package com.yizhaoqi.smartpai.eval;

import com.yizhaoqi.smartpai.client.DeepSeekClient;
import com.yizhaoqi.smartpai.eval.model.GroundTruthFact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 财务指标计算器。优先从结构化表格数据中确定性计算，LLM 仅作为兜底。
 */
public class IndicatorComputer {

    private static final Logger log = LoggerFactory.getLogger(IndicatorComputer.class);
    private static final int MAX_CANDIDATES = 25;
    private static final int DECIMAL_SCALE = 6;
    private static final Pattern YEAR_PATTERN = Pattern.compile("(?<!\\d)(20\\d{2})(?!\\d)");

    private final DeepSeekClient deepSeekClient;

    public IndicatorComputer(DeepSeekClient deepSeekClient) {
        this.deepSeekClient = deepSeekClient;
    }

    // ─────────────────────────────────────────────
    // 公开入口
    // ─────────────────────────────────────────────

    /**
     * 优先从 tableContext 确定性计算；解析失败或数据不足时降级到 LLM。
     */
    public List<GroundTruthFact> compute(String question, List<String> candidates, String tableContext) {
        if (tableContext != null && !tableContext.isBlank()) {
            List<GroundTruthFact> structured = computeFromTable(question, tableContext);
            if (!structured.isEmpty()) {
                log.info("Indicator 确定性计算成功: {} 个指标", structured.size());
                return structured;
            }
        }
        return computeWithLlm(question, candidates);
    }

    /** 仅 LLM 路径（兼容旧调用方）。 */
    public List<GroundTruthFact> compute(String question, List<String> candidates) {
        return computeWithLlm(question, candidates);
    }

    // ─────────────────────────────────────────────
    // 确定性计算（从 Markdown 表格）
    // ─────────────────────────────────────────────

    static List<GroundTruthFact> computeFromTable(String question, String tableContext) {
        Map<String, Map<String, BigDecimal>> facts = parseTableToFacts(tableContext);
        if (facts.isEmpty()) {
            log.debug("parseTableToFacts 返回空，tableContext 前200字符: {}",
                    tableContext.substring(0, Math.min(200, tableContext.length())));
            return List.of();
        }

        String cleanedQuestion = cleanQuestion(question);
        List<String> indicatorNames = findIndicatorNames(cleanedQuestion);
        if (indicatorNames.isEmpty()) {
            log.debug("findIndicatorNames 返回空，cleanedQuestion: {}", cleanedQuestion);
            return List.of();
        }

        String targetYear = extractTargetYear(cleanedQuestion);
        if (targetYear == null) {
            log.debug("extractTargetYear 返回 null，cleanedQuestion: {}", cleanedQuestion);
            return List.of();
        }

        List<GroundTruthFact> results = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (String name : indicatorNames) {
            FormulaDef def = FORMULA_DEFS.get(name);
            if (def == null) {
                failed.add(name + "(无公式)");
                continue;
            }

            BigDecimal value = computeDeterministic(def, facts, targetYear);
            if (value == null) {
                failed.add(name + "(计算失败:" + String.join(",", def.inputs()) + ")");
                continue;
            }

            Map<String, String> yearValues = new LinkedHashMap<>();
            yearValues.put(targetYear, value.stripTrailingZeros().toPlainString());
            results.add(new GroundTruthFact(name, yearValues));
        }
        if (!results.isEmpty() && !failed.isEmpty()) {
            log.debug("Indicator 部分成功: {}/{} succeeded, failed: {}",
                    results.size(), indicatorNames.size(), failed);
        }
        if (!results.isEmpty()) {
            log.info("Indicator 确定性计算成功: {} 个指标 (tableFacts={} keys, foundIndicators={}, year={})",
                    results.size(), facts.size(), indicatorNames.size(), targetYear);
        }
        return results;
    }

    // ─────────────────────────────────────────────
    // 表格解析：Markdown → Map<指标名, Map<年份, BigDecimal>>
    // ─────────────────────────────────────────────

    static Map<String, Map<String, BigDecimal>> parseTableToFacts(String markdown) {
        Map<String, Map<String, BigDecimal>> facts = new LinkedHashMap<>();
        String[] lines = markdown.split("\\R");
        List<String> headers = null;
        List<String> yearKeys = null;

        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                headers = null;
                yearKeys = null;
                continue;
            }
            if (!t.startsWith("|")) continue;

            String[] cells = splitPipe(t);
            if (cells.length == 0 || isSeparatorRow(cells)) continue;

            if (headers == null) {
                headers = new ArrayList<>();
                yearKeys = new ArrayList<>();
                for (int i = 1; i < cells.length; i++) {
                    headers.add(cells[i]);
                    yearKeys.add(extractYear(cells[i]));
                }
                continue;
            }

            String metricName = cells[0];
            if (metricName.isBlank()) continue;

            String normalizedName = normalizeMetricName(metricName);
            Map<String, BigDecimal> yearValues = new LinkedHashMap<>();
            for (int i = 1; i < cells.length && i - 1 < yearKeys.size(); i++) {
                String yearKey = yearKeys.get(i - 1);
                if (yearKey == null) continue;
                String raw = normalizeNumber(cells[i]);
                if (!raw.isBlank()) {
                    try {
                        yearValues.put(yearKey, new BigDecimal(raw));
                    } catch (NumberFormatException ignored) {
                        // skip unparseable values
                    }
                }
            }
            if (!yearValues.isEmpty()) {
                facts.put(normalizedName, yearValues);
            }
        }
        return facts;
    }

    // ─────────────────────────────────────────────
    // 公式定义与计算
    // ─────────────────────────────────────────────

    /** RATIO = period-end denominator; AVG_RATIO = average-of-two-years denominator (flow/stock ratios) */
    enum FormulaType { RATIO, AVG_RATIO, GROWTH, TURNOVER_DAYS, AVG_TURNOVER_DAYS, QUICK_RATIO, GROSS_MARGIN, PERIOD_EXPENSE, OPERATING_CYCLE }

    record FormulaDef(FormulaType type, String[] inputs) {}

    /** 35 个 FinAR-Bench 中文指标 → 公式定义。inputs 顺序与 FormulaType 对应。 */
    private static final Map<String, FormulaDef> FORMULA_DEFS = new LinkedHashMap<>();
    static {
        // RATIO: numerator / denominator
        fd("资产负债率",                             FormulaType.RATIO,          "负债合计", "资产总计");
        fd("流动比率",                              FormulaType.RATIO,          "流动资产合计", "流动负债合计");
        fd("产权比率",                              FormulaType.RATIO,          "负债合计", "归属于母公司所有者权益合计");
        fd("权益乘数",                              FormulaType.RATIO,          "资产总计", "股东权益合计");
        fd("总资产收益率",                           FormulaType.AVG_RATIO,      "净利润", "资产总计");
        fd("净资产收益率",                           FormulaType.AVG_RATIO,      "归属于母公司所有者的净利润", "归属于母公司所有者权益合计");
        fd("总资产周转率",                           FormulaType.AVG_RATIO,      "营业收入", "资产总计");
        fd("流动资产周转率",                         FormulaType.AVG_RATIO,      "营业收入", "流动资产合计");
        fd("应收账款周转率",                         FormulaType.AVG_RATIO,      "营业收入", "应收帐款");
        fd("应收帐款周转率",                         FormulaType.AVG_RATIO,      "营业收入", "应收帐款");
        fd("应付账款周转率",                         FormulaType.AVG_RATIO,      "营业成本", "应付帐款");
        fd("应付帐款周转率",                         FormulaType.AVG_RATIO,      "营业成本", "应付帐款");
        fd("管理费用与营业收入的比例",                FormulaType.RATIO,          "管理费用", "营业收入");
        fd("销售费用与营业收入的比例",                FormulaType.RATIO,          "销售费用", "营业收入");
        fd("财务费用与营业收入的比例",                FormulaType.RATIO,          "财务费用", "营业收入");
        fd("非流动资产合计与资产总计的比例",          FormulaType.RATIO,          "非流动资产合计", "资产总计");
        fd("流动资产合计与资产总计的比例",            FormulaType.RATIO,          "流动资产合计", "资产总计");
        fd("固定资产净额与资产总计的比例",            FormulaType.RATIO,          "固定资产净额", "资产总计");
        fd("应收帐款与资产总计的比例",                FormulaType.RATIO,          "应收帐款", "资产总计");
        fd("应收账款与资产总计的比例",                FormulaType.RATIO,          "应收帐款", "资产总计");
        fd("存货与资产总计的比例",                    FormulaType.RATIO,          "存货", "资产总计");
        fd("应付帐款与负债合计的比例",                FormulaType.RATIO,          "应付帐款", "负债合计");
        fd("应付账款与负债合计的比例",                FormulaType.RATIO,          "应付帐款", "负债合计");
        fd("流动负债合计与负债合计的比例",            FormulaType.RATIO,          "流动负债合计", "负债合计");
        fd("长期负债合计与负债合计的比例",            FormulaType.RATIO,          "长期负债合计", "负债合计");
        fd("商誉与资产总计的比例",                    FormulaType.RATIO,          "商誉", "资产总计");
        fd("销售商品提供劳务收到的现金与营业收入的比例", FormulaType.RATIO,       "销售商品提供劳务收到的现金", "营业收入");
        fd("经营活动现金流量净额与净利润的比例",      FormulaType.RATIO,          "经营活动现金流量净额", "净利润");

        // 常见别名/变体
        fd("销售净利率",                             FormulaType.RATIO,          "净利润", "营业收入");
        fd("净利率",                                FormulaType.RATIO,          "净利润", "营业收入");
        fd("毛利率",                                FormulaType.GROSS_MARGIN,   "营业收入", "营业成本");
        fd("存货周转率",                             FormulaType.AVG_RATIO,      "营业成本", "存货");
        fd("固定资产周转率",                         FormulaType.AVG_RATIO,      "营业收入", "固定资产净额");
        fd("流动比",                                FormulaType.RATIO,          "流动资产合计", "流动负债合计");
        fd("速动比",                                FormulaType.QUICK_RATIO,    "流动资产合计", "存货", "流动负债合计");

        // GROWTH: (current - previous) / |previous|
        fd("净利润增长率",                           FormulaType.GROWTH,         "净利润");
        fd("营业收入增长率",                         FormulaType.GROWTH,         "营业收入");
        fd("经营活动现金流量净额增长率",              FormulaType.GROWTH,         "经营活动现金流量净额");
        fd("应收帐款增长率",                         FormulaType.GROWTH,         "应收帐款");
        fd("应收账款增长率",                         FormulaType.GROWTH,         "应收帐款");

        // TURNOVER_DAYS: 360 * numerator / denominator (period-end); AVG variant uses average numerator
        fd("存货周转天数",                           FormulaType.AVG_TURNOVER_DAYS, "存货", "营业成本");
        fd("应收账款周转天数",                       FormulaType.AVG_TURNOVER_DAYS, "应收帐款", "营业收入");
        fd("应收帐款周转天数",                       FormulaType.AVG_TURNOVER_DAYS, "应收帐款", "营业收入");
        fd("应付账款周转天数",                       FormulaType.AVG_TURNOVER_DAYS, "应付帐款", "营业成本");
        fd("应付帐款周转天数",                       FormulaType.AVG_TURNOVER_DAYS, "应付帐款", "营业成本");

        // 三项公式
        fd("速动比率",                              FormulaType.QUICK_RATIO,    "流动资产合计", "存货", "流动负债合计");
        fd("销售毛利率",                             FormulaType.GROSS_MARGIN,   "营业收入", "营业成本");
        fd("期间费用率",                             FormulaType.PERIOD_EXPENSE, "销售费用", "管理费用", "财务费用", "营业收入");

        // 复合指标
        fd("营业周期",                              FormulaType.OPERATING_CYCLE,"存货", "营业成本", "应收帐款", "营业收入");
    }

    private static void fd(String name, FormulaType type, String... inputs) {
        FORMULA_DEFS.put(name, new FormulaDef(type, inputs));
    }

    /** 扫描 question 中出现的已知指标名（按长度降序，避免"资产负债率"被"负债率"误匹配）。 */
    static List<String> findIndicatorNames(String question) {
        String normalized = normalizeMetricName(question);
        List<String> found = new ArrayList<>();
        // 按名称长度降序排列，优先匹配长名称
        List<String> sortedNames = new ArrayList<>(FORMULA_DEFS.keySet());
        sortedNames.sort(Comparator.comparingInt(String::length).reversed());

        String remaining = normalized;
        for (String name : sortedNames) {
            String nn = normalizeMetricName(name);
            if (remaining.contains(nn)) {
                found.add(name);
                remaining = remaining.replace(nn, " ");
            }
        }
        return found;
    }

    static BigDecimal computeDeterministic(FormulaDef def, Map<String, Map<String, BigDecimal>> facts, String targetYear) {
        try {
            return switch (def.type()) {
                case RATIO -> {
                    BigDecimal a = getFact(facts, def.inputs()[0], targetYear);
                    BigDecimal b = getFact(facts, def.inputs()[1], targetYear);
                    if (a == null || b == null || b.compareTo(BigDecimal.ZERO) == 0) yield null;
                    yield divide(a, b);
                }
                case AVG_RATIO -> {
                    BigDecimal a = getFact(facts, def.inputs()[0], targetYear);
                    BigDecimal b = getAvgFact(facts, def.inputs()[1], targetYear);
                    if (a == null || b == null || b.compareTo(BigDecimal.ZERO) == 0) yield null;
                    yield divide(a, b);
                }
                case GROWTH -> {
                    BigDecimal cur = getFact(facts, def.inputs()[0], targetYear);
                    String prevYear = previousYear(targetYear);
                    BigDecimal prev = getFact(facts, def.inputs()[0], prevYear);
                    if (cur == null || prev == null || prev.compareTo(BigDecimal.ZERO) == 0) yield null;
                    yield divide(cur.subtract(prev), prev);
                }
                case TURNOVER_DAYS -> {
                    BigDecimal a = getFact(facts, def.inputs()[0], targetYear);
                    BigDecimal b = getFact(facts, def.inputs()[1], targetYear);
                    if (a == null || b == null || b.compareTo(BigDecimal.ZERO) == 0) yield null;
                    yield divide(a, b).multiply(new BigDecimal("360"));
                }
                case AVG_TURNOVER_DAYS -> {
                    BigDecimal a = getAvgFact(facts, def.inputs()[0], targetYear);
                    BigDecimal b = getFact(facts, def.inputs()[1], targetYear);
                    if (a == null || b == null || b.compareTo(BigDecimal.ZERO) == 0) yield null;
                    yield divide(a, b).multiply(new BigDecimal("360"));
                }
                case QUICK_RATIO -> {
                    BigDecimal ca = getFact(facts, def.inputs()[0], targetYear);
                    BigDecimal inv = getFact(facts, def.inputs()[1], targetYear);
                    BigDecimal cl = getFact(facts, def.inputs()[2], targetYear);
                    if (ca == null || inv == null || cl == null || cl.compareTo(BigDecimal.ZERO) == 0) yield null;
                    // 速动资产 = 流动资产 - 存货 - 预付款项 - 一年内到期非流动资产 - 其他流动资产
                    BigDecimal quick = ca.subtract(inv);
                    BigDecimal prepay = getFact(facts, "预付款项", targetYear);
                    if (prepay == null) prepay = getFact(facts, "预付账款", targetYear);
                    if (prepay == null) prepay = getFact(facts, "预付帐款", targetYear);
                    if (prepay != null) quick = quick.subtract(prepay);
                    BigDecimal yrNonCur = getFact(facts, "一年内到期的非流动资产", targetYear);
                    if (yrNonCur != null) quick = quick.subtract(yrNonCur);
                    BigDecimal otherCa = getFact(facts, "其他流动资产", targetYear);
                    if (otherCa != null) quick = quick.subtract(otherCa);
                    yield divide(quick, cl);
                }
                case GROSS_MARGIN -> {
                    BigDecimal rev = getFact(facts, def.inputs()[0], targetYear);
                    BigDecimal cost = getFact(facts, def.inputs()[1], targetYear);
                    if (rev == null || cost == null || rev.compareTo(BigDecimal.ZERO) == 0) yield null;
                    yield divide(rev.subtract(cost), rev);
                }
                case PERIOD_EXPENSE -> {
                    BigDecimal se = getFact(facts, def.inputs()[0], targetYear);
                    BigDecimal me = getFact(facts, def.inputs()[1], targetYear);
                    BigDecimal fe = getFact(facts, def.inputs()[2], targetYear);
                    BigDecimal rev = getFact(facts, def.inputs()[3], targetYear);
                    if (se == null || me == null || fe == null || rev == null || rev.compareTo(BigDecimal.ZERO) == 0) yield null;
                    yield divide(se.add(me).add(fe), rev);
                }
                case OPERATING_CYCLE -> {
                    BigDecimal inv = getAvgFact(facts, def.inputs()[0], targetYear);
                    BigDecimal cost = getFact(facts, def.inputs()[1], targetYear);
                    BigDecimal ar = getAvgFact(facts, def.inputs()[2], targetYear);
                    BigDecimal rev = getFact(facts, def.inputs()[3], targetYear);
                    if (inv == null || cost == null || ar == null || rev == null
                            || cost.compareTo(BigDecimal.ZERO) == 0 || rev.compareTo(BigDecimal.ZERO) == 0) yield null;
                    BigDecimal invDays = divide(inv, cost).multiply(new BigDecimal("360"));
                    BigDecimal arDays = divide(ar, rev).multiply(new BigDecimal("360"));
                    yield invDays.add(arDays);
                }
            };
        } catch (ArithmeticException e) {
            return null;
        }
    }

    // ─────────────────────────────────────────────
    // 数值工具
    // ─────────────────────────────────────────────

    private static BigDecimal divide(BigDecimal a, BigDecimal b) {
        return a.divide(b, DECIMAL_SCALE, RoundingMode.HALF_UP);
    }

    /** 常见别名字典：当精确查找"股东权益合计"时，也尝试"所有者权益合计"和"归属于母公司所有者权益合计"。 */
    private static final Map<String, String[]> METRIC_ALIASES = Map.of(
            "股东权益合计", new String[]{"所有者权益合计", "归属于母公司所有者权益合计"},
            "应收帐款", new String[]{"应收账款"},
            "应付帐款", new String[]{"应付账款"},
            "归属于母公司所有者的净利润", new String[]{"归属于母公司股东的净利润", "归母净利润"}
    );

    private static Map<String, BigDecimal> resolveWithAliases(Map<String, Map<String, BigDecimal>> facts, String key, String year) {
        Map<String, BigDecimal> result = facts.get(key);
        if (result != null) return result;

        // 尝试预定义的别名链
        String[] aliases = METRIC_ALIASES.get(key);
        if (aliases != null) {
            for (String alias : aliases) {
                result = facts.get(normalizeMetricName(alias));
                if (result != null) return result;
            }
        }

        // 反向：其他 key 的别名是否指向当前 key
        for (var aliasEntry : METRIC_ALIASES.entrySet()) {
            for (String alias : aliasEntry.getValue()) {
                if (normalizeMetricName(alias).equals(key)) {
                    result = facts.get(aliasEntry.getKey());
                    if (result != null) return result;
                }
            }
        }

        return null;
    }

    static BigDecimal getFact(Map<String, Map<String, BigDecimal>> facts, String metricName, String year) {
        String key = normalizeMetricName(metricName);

        // 0. 别名链：依次尝试原 key 及所有别名
        Map<String, BigDecimal> yearValues = resolveWithAliases(facts, key, year);
        if (yearValues != null) return yearValues.get(year);

        // 1. 包含匹配（双向）
        for (var entry : facts.entrySet()) {
            if (entry.getKey().contains(key) || key.contains(entry.getKey())) {
                return entry.getValue().get(year);
            }
        }

        // 3. 去后缀匹配（如"非流动资产合计" vs "非流动资产"）
        String keyNoSuffix = key.replaceAll("(合计|净额|净值|总额)$", "");
        if (!keyNoSuffix.equals(key)) {
            for (var entry : facts.entrySet()) {
                String entryNoSuffix = entry.getKey().replaceAll("(合计|净额|净值|总额)$", "");
                if (entryNoSuffix.equals(keyNoSuffix) || entryNoSuffix.contains(keyNoSuffix) || keyNoSuffix.contains(entryNoSuffix)) {
                    return entry.getValue().get(year);
                }
            }
        }

        return null;
    }

    /** 取某指标当年与上一年的平均值，用于流动/存量的比率（如ROA、周转率）。 */
    static BigDecimal getAvgFact(Map<String, Map<String, BigDecimal>> facts, String metricName, String year) {
        BigDecimal cur = getFact(facts, metricName, year);
        BigDecimal prev = getFact(facts, metricName, previousYear(year));
        if (cur == null || prev == null) return null;
        return cur.add(prev).divide(new BigDecimal("2"), DECIMAL_SCALE, RoundingMode.HALF_UP);
    }

    static String previousYear(String year) {
        try {
            return String.valueOf(Integer.parseInt(year) - 1);
        } catch (NumberFormatException e) {
            return year;
        }
    }

    // ─────────────────────────────────────────────
    // 文本解析工具
    // ─────────────────────────────────────────────

    /** 从 question 中提取目标年份（取最晚出现的）。 */
    static String extractTargetYear(String question) {
        Matcher m = YEAR_PATTERN.matcher(question);
        String latest = null;
        int maxYear = 0;
        while (m.find()) {
            int y = Integer.parseInt(m.group(1));
            if (y > maxYear) { maxYear = y; latest = String.valueOf(y); }
        }
        return latest;
    }

    /** 从表头如 "2023年12月31日" 提取年份 "2023"。 */
    private static String extractYear(String header) {
        Matcher m = YEAR_PATTERN.matcher(header);
        return m.find() ? m.group(1) : null;
    }

    static String normalizeMetricName(String name) {
        if (name == null) return "";
        return name.replace('（', '(').replace('）', ')')
                .replace('帐', '账')
                .replaceAll("[\\s:：()（）\\-—_、，,。.]", "")
                .trim().toLowerCase();
    }

    /** 移除 FinAR-Bench 问题中的格式指令。 */
    static String cleanQuestion(String question) {
        if (question == null) return "";
        return question
                .replaceAll("输出一个markdown格式的表格[，,。]?\\s*列名为[^。]*[。.]?", "")
                .replaceAll("输出一个markdown格式的表格[。.]?", "")
                .replaceAll("结果表示为小数[，,]保留4位小数[。.]?", "")
                .trim();
    }

    static String normalizeNumber(String raw) {
        if (raw == null) return "";
        return raw.replace(",", "").replace("，", "").trim();
    }

    static String[] splitPipe(String line) {
        String s = line.strip();
        if (s.startsWith("|")) s = s.substring(1);
        if (s.endsWith("|")) s = s.substring(0, s.length() - 1);
        String[] parts = s.split("\\|");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        return parts;
    }

    static boolean isSeparatorRow(String[] cells) {
        for (String cell : cells) {
            if (!cell.matches("^[-: ]+$")) return false;
        }
        return true;
    }

    // ─────────────────────────────────────────────
    // LLM 兜底
    // ─────────────────────────────────────────────

    private List<GroundTruthFact> computeWithLlm(String question, List<String> candidates) {
        String context = buildContext(candidates);
        String cleanedQuestion = cleanQuestion(question);
        String prompt = PROMPT_TEMPLATE.formatted(context, cleanedQuestion);

        try {
            log.info("Indicator LLM 计算(兜底): question={}, candidates={}",
                    cleanedQuestion.substring(0, Math.min(60, cleanedQuestion.length())), candidates.size());
            String response = deepSeekClient.completeResponse(prompt, "", List.of());
            log.info("LLM response (first 400): {}",
                    response.substring(0, Math.min(400, response.length())));
            List<GroundTruthFact> facts = FinArBenchLoader.parseGroundTruth(response);
            log.info("Indicator LLM 计算结果: {} 个指标", facts.size());
            return facts;
        } catch (Exception e) {
            log.warn("Indicator LLM 计算失败: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildContext(List<String> candidates) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(candidates.size(), MAX_CANDIDATES);
        for (int i = 0; i < limit; i++) {
            sb.append("[").append(i + 1).append("] ").append(candidates.get(i)).append("\n");
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────
    // Prompt 模板
    // ─────────────────────────────────────────────

    private static final String FORMULA_LIBRARY = """
            # 公式库（必须严格使用，不得自行推导）

            资产负债率 = 负债合计 / 资产总计
            流动比率 = 流动资产合计 / 流动负债合计
            速动比率 = (流动资产合计 - 存货) / 流动负债合计
            产权比率 = 负债合计 / 归属于母公司所有者权益合计
            权益乘数 = 资产总计 / 归属于母公司所有者权益合计
            总资产收益率 = 净利润 / 资产总计
            净资产收益率 = 净利润 / 归属于母公司所有者权益合计
            销售毛利率 = (营业收入 - 营业成本) / 营业收入
            期间费用率 = (销售费用 + 管理费用 + 财务费用) / 营业收入
            总资产周转率 = 营业收入 / 资产总计
            流动资产周转率 = 营业收入 / 流动资产合计
            应收账款周转率 = 营业收入 / 应收帐款
            应付账款周转率 = 营业成本 / 应付帐款
            存货周转天数 = 360 * 存货 / 营业成本
            应收账款周转天数 = 360 / 应收账款周转率
            应付账款周转天数 = 360 / 应付账款周转率
            营业周期 = 存货周转天数 + 应收账款周转天数
            净利润增长率 = (2023年净利润 - 2022年净利润) / |2022年净利润|
            营业收入增长率 = (2023年营业收入 - 2022年营业收入) / |2022年营业收入|
            经营活动现金流量净额增长率 = (2023年值 - 2022年值) / |2022年值|
            应收帐款增长率 = (2023年应收帐款 - 2022年应收帐款) / |2022年应收帐款|
            管理费用与营业收入的比例 = 管理费用 / 营业收入
            销售费用与营业收入的比例 = 销售费用 / 营业收入
            财务费用与营业收入的比例 = 财务费用 / 营业收入
            非流动资产合计与资产总计的比例 = 非流动资产合计 / 资产总计
            流动资产合计与资产总计的比例 = 流动资产合计 / 资产总计
            固定资产净额与资产总计的比例 = 固定资产净额 / 资产总计
            应收帐款与资产总计的比例 = 应收帐款 / 资产总计
            存货与资产总计的比例 = 存货 / 资产总计
            应付帐款与负债合计的比例 = 应付帐款 / 负债合计
            流动负债合计与负债合计的比例 = 流动负债合计 / 负债合计
            长期负债合计与负债合计的比例 = 长期负债合计 / 负债合计
            商誉与资产总计的比例 = 商誉 / 资产总计
            销售商品提供劳务收到的现金与营业收入的比例 = 销售商品提供劳务收到的现金 / 营业收入
            经营活动现金流量净额与净利润的比例 = 经营活动现金流量净额 / 净利润""";

    private static final String PROMPT_TEMPLATE = """
            你是财务指标计算引擎。只使用下方财务数据，严格按公式库计算。
            无法找到所需数据时，跳过该指标不输出。结果保留4位小数（小数形式，如0.4224，不是百分比）。

            """ + FORMULA_LIBRARY + """

            # 执行规则
            1. 对任务中要求的每个指标：
               a. 在公式库中找到对应公式
               b. 在财务数据中定位公式所需的原始字段（数据格式为"[报表] 字段名; 2023年 数值; 2022年 数值"，去掉数字中的千分位逗号）
               c. 代入公式计算，保留4位小数
               d. 检查结果是否合理：比率0~5、周转率0~50、天数0~2000、增长率-50~50、费用率-1~2，超出范围说明取错了字段
            2. 增长率类指标的分母必须取绝对值
            3. 字段名允许相近匹配（如"股东权益合计"可匹配"归属于母公司所有者权益合计"）

            # 输出格式
            严格按以下 Markdown 表格格式输出，不要输出任何其他内容：

            | 项目 | 2023 |
            | 指标名 | 计算值 |

            示例：
            任务：计算2023的管理费用与营业收入的比例,资产负债率
            输出：
            | 项目 | 2023 |
            | 管理费用与营业收入的比例 | 0.0560 |
            | 资产负债率 | 0.4224 |

            <财务数据>
            %s
            </财务数据>

            任务：%s""";
}

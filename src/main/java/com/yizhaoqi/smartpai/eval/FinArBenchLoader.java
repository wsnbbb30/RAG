package com.yizhaoqi.smartpai.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.eval.model.EvaluationCase;
import com.yizhaoqi.smartpai.eval.model.GroundTruthFact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 FinAR-Bench dev.txt JSONL 文件为 {@link EvaluationCase} 列表。
 *
 * <p>ground truth 的 Markdown 表格年份列名由表头动态决定，不做硬编码假设。</p>
 */
public class FinArBenchLoader {

    private static final Pattern STOCK_CODE = Pattern.compile("(\\d{6})\\..*");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(20\\d{2})(?!\\d)");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public List<EvaluationCase> load(Path devFile) throws IOException {
        List<String> lines = Files.readAllLines(devFile);
        List<EvaluationCase> cases = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank()) continue;
            JsonNode root = MAPPER.readTree(line);
            String tableContext = root.path("table").asText();
            String stockCode = extractStockCode(root);
            JsonNode instances = root.path("instances");
            for (JsonNode inst : instances) {
                String task = inst.path("task").asText();
                String groundTruth = inst.path("ground_truth").asText();
                String taskType = inst.path("task_type").asText();
                String company = inst.path("company").asText();
                String companyCode = inst.path("company_code").asText();
                String taskId = inst.path("task_id").asText();
                Integer fiscalYear = extractLatestYear(task);
                List<GroundTruthFact> expectedFacts = parseGroundTruth(groundTruth);
                cases.add(new EvaluationCase(taskId, task, groundTruth, taskType,
                        company, companyCode, stockCode, fiscalYear, tableContext, expectedFacts));
            }
        }
        return cases;
    }

    /** "603421.SH" → "603421" */
    String extractStockCode(JsonNode root) {
        JsonNode firstInst = root.path("instances").path(0);
        String code = firstInst.path("company_code").asText();
        Matcher m = STOCK_CODE.matcher(code);
        return m.find() ? m.group(1) : code;
    }

    /** 从 task 文本中提取最晚出现的年份，无年份时返回 null。 */
    Integer extractLatestYear(String text) {
        Matcher m = YEAR.matcher(text == null ? "" : text);
        int latest = 0;
        while (m.find()) {
            int y = Integer.parseInt(m.group(1));
            if (y > latest) latest = y;
        }
        return latest > 0 ? latest : null;
    }

    /**
     * 解析 ground truth Markdown 表格为结构化事实列表。
     *
     * <p>输入示例：
     * <pre>
     * | 项目 | 2022 | 2023 |
     * | 归属于母公司所有者的净利润 | 118,680,630.51 | 131,220,189.16 |
     * </pre>
     */
    /** 解析 ground truth 或 LLM 返回的 Markdown 表格为结构化事实列表。 */
    public static List<GroundTruthFact> parseGroundTruth(String markdown) {
        if (markdown == null || markdown.isBlank()) return List.of();
        String[] lines = markdown.strip().split("\\R");
        List<String> headers = null;
        List<GroundTruthFact> facts = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.startsWith("|")) continue;
            String[] cells = splitPipe(trimmed);
            if (cells.length == 0) continue;

            if (isSeparatorRow(cells)) continue;

            if (headers == null) {
                headers = extractHeaders(cells);
            } else {
                GroundTruthFact fact = toFact(cells, headers);
                if (fact != null) facts.add(fact);
            }
        }
        return facts;
    }

    private static String[] splitPipe(String line) {
        String stripped = line.strip();
        if (stripped.startsWith("|")) stripped = stripped.substring(1);
        if (stripped.endsWith("|")) stripped = stripped.substring(0, stripped.length() - 1);
        String[] parts = stripped.split("\\|");
        String[] result = new String[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = parts[i].trim();
        }
        return result;
    }

    /** 检测分隔行，如 "----|------|------" */
    private static boolean isSeparatorRow(String[] cells) {
        for (String cell : cells) {
            String s = cell.replaceAll("[-: ]", "");
            if (!s.isEmpty()) return false;
        }
        return true;
    }

    /** 提取表头，跳过第一列（通常是"项目"）。 */
    private static List<String> extractHeaders(String[] cells) {
        List<String> headers = new ArrayList<>();
        for (int i = 1; i < cells.length; i++) {
            headers.add(cells[i]);
        }
        return headers;
    }

    /** 将一行数据与表头映射为 GroundTruthFact。 */
    private static GroundTruthFact toFact(String[] cells, List<String> headers) {
        if (cells.length < 2) return null;
        String metricName = cells[0];
        if (metricName.isBlank()) return null;
        LinkedHashMap<String, String> yearValues = new LinkedHashMap<>();
        for (int i = 1; i < cells.length && i - 1 < headers.size(); i++) {
            String value = normalizeNumber(cells[i]);
            if (!value.isBlank()) {
                yearValues.put(headers.get(i - 1), value);
            }
        }
        if (yearValues.isEmpty()) return null;
        return GroundTruthFact.of(metricName, yearValues);
    }

    /** 去除千分位逗号，保留数字、小数点和负号。 */
    static String normalizeNumber(String raw) {
        if (raw == null) return "";
        return raw.replace(",", "").replace("，", "").trim();
    }
}

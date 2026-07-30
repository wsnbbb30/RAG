package com.yizhaoqi.smartpai.retrieval;

import com.yizhaoqi.smartpai.model.FinancialMetricAlias;
import com.yizhaoqi.smartpai.repository.FinancialMetricAliasRepository;
import com.yizhaoqi.smartpai.service.MetricDictionary;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 无模型、可解释的问题过滤条件提取器。
 *
 * <p>仅识别明确出现的 6 位股票代码、20xx 年和报告类型。公司简称可能有歧义，
 * 在接入公司目录消歧与置信度前不强制作为 filter，宁可多召回而不误杀证据。</p>
 */
@Component
public class QueryFilterExtractor {
    private static final Pattern STOCK_CODE = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(20\\d{2})(?:年|年度)?");

    private final FinancialMetricAliasRepository aliasRepository;

    public QueryFilterExtractor(FinancialMetricAliasRepository aliasRepository) {
        this.aliasRepository = aliasRepository;
    }

    public QueryFilter extract(String query) {
        String stockCode = first(STOCK_CODE, query);
        String yearText = first(YEAR, query);
        Integer year = yearText == null ? null : Integer.valueOf(yearText);
        List<String> metricCodes = extractMetrics(query);
        return new QueryFilter(stockCode, year, extractReportType(query), metricCodes);
    }

    private String first(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractReportType(String query) {
        String normalized = query == null ? "" : query.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("年报") || normalized.contains("年度报告") || normalized.contains("annual report")) return "ANNUAL_REPORT";
        if (normalized.contains("半年报") || normalized.contains("半年度") || normalized.contains("semi-annual")
                || normalized.contains("half-year")) return "SEMI_ANNUAL_REPORT";
        if (normalized.contains("季报") || normalized.matches(".*q[1-4].*")) return "QUARTERLY_REPORT";
        return null;
    }

    /**
     * 从 query 文本中扫描已知指标别名，返回匹配到的标准 metricCode 列表。
     *
     * <p>使用与 {@link MetricDictionary} 相同的归一化规则，确保别名匹配一致。
     * 匹配不上时不强行猜测，由 BM25/Vector 路兜底。</p>
     */
    List<String> extractMetrics(String query) {
        if (query == null || query.isBlank()) return List.of();
        String normalizedQuery = MetricDictionary.normalize(query);
        if (normalizedQuery.isBlank()) return List.of();

        List<FinancialMetricAlias> aliases = aliasRepository.findAll();
        if (aliases.isEmpty()) return List.of();

        // 按别名长度降序排列，优先匹配更长的别名（如"归属于母公司所有者的净利润"优先于"净利润"）
        List<FinancialMetricAlias> sorted = new ArrayList<>(aliases);
        sorted.sort(Comparator.comparingInt((FinancialMetricAlias a) -> a.getNormalizedAlias().length()).reversed());

        Set<String> matchedCodes = new LinkedHashSet<>();
        String remaining = normalizedQuery;
        for (FinancialMetricAlias alias : sorted) {
            String na = alias.getNormalizedAlias();
            if (na.length() < 2) continue; // 跳过过短的别名，避免误匹配
            if (remaining.contains(na)) {
                matchedCodes.add(alias.getMetricCode());
                // 将该别名从搜索空间中移除，避免"营业"匹配到多个指标
                remaining = remaining.replace(na, " ");
            }
        }
        return List.copyOf(matchedCodes);
    }
}

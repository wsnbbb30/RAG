package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.finance.NumericClaim;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从“指标附近的数字”中提取可规则校验的主张。
 * 无法绑定到指标的普通年份、页码和编号不作为金额 claim，避免产生大量误报。
 */
@Component
public class NumericClaimExtractor {
    private static final Pattern NUMBER = Pattern.compile("[（(]?[-－]?\\d[\\d,，]*(?:\\.\\d+)?%?[)）]?");
    private final MetricDictionary metricDictionary;
    private final UnitNormalizer unitNormalizer;
    public NumericClaimExtractor(MetricDictionary metricDictionary, UnitNormalizer unitNormalizer) {
        this.metricDictionary = metricDictionary; this.unitNormalizer = unitNormalizer;
    }

    public List<NumericClaim> extract(String answer) {
        List<NumericClaim> claims = new ArrayList<>();
        if (answer == null || answer.isBlank()) return claims;
        for (String sentence : answer.split("[。；;\\n]")) {
            String metricCode = resolveMetric(sentence);
            if (metricCode == null) continue;
            Matcher matcher = NUMBER.matcher(sentence);
            while (matcher.find()) {
                String raw = matcher.group();
                // 年份不是财务金额；期间由事实字段单独核验。
                if (raw.matches("20\\d{2}")) continue;
                unitNormalizer.normalize(raw, raw.endsWith("%") ? "%" : "", "CNY")
                        .ifPresent(value -> claims.add(new NumericClaim(metricCode, value.value(), raw,
                                raw.endsWith("%"), sentence.trim())));
            }
        }
        return claims;
    }

    private String resolveMetric(String sentence) {
        // 对初始字典中的别名逐词尝试不现实，因此仅从常用候选片段查字典；后续可用分词器扩展。
        String[] candidates = {"营业收入", "营业总收入", "净利润", "归母净利润", "资产总额", "资产合计", "负债合计", "经营活动产生的现金流量净额"};
        for (String candidate : candidates) {
            if (sentence.contains(candidate)) {
                var metric = metricDictionary.resolve(candidate);
                if (metric.isPresent()) return metric.get().getMetricCode();
            }
        }
        return null;
    }
}

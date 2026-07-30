package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.FinancialMetric;
import com.yizhaoqi.smartpai.repository.FinancialMetricAliasRepository;
import com.yizhaoqi.smartpai.repository.FinancialMetricRepository;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * 财务指标字典访问门面。
 *
 * <p>匹配仅允许规范化后的精确别名，避免“净利润”“归母净利润”等相近指标被模糊匹配混淆。
 * 新别名应以数据迁移或审核流程写入字典，而不是在提取器里临时硬编码。</p>
 */
@Component
public class MetricDictionary {
    private final FinancialMetricAliasRepository aliasRepository;
    private final FinancialMetricRepository metricRepository;

    public MetricDictionary(FinancialMetricAliasRepository aliasRepository, FinancialMetricRepository metricRepository) {
        this.aliasRepository = aliasRepository;
        this.metricRepository = metricRepository;
    }

    public Optional<FinancialMetric> resolve(String sourceLabel) {
        String normalized = normalize(sourceLabel);
        if (normalized.isBlank()) return Optional.empty();
        return aliasRepository.findByNormalizedAlias(normalized)
                .flatMap(alias -> metricRepository.findByMetricCodeAndEnabledTrue(alias.getMetricCode()));
    }

    /** 统一全半角括号、空格和常见分隔符，作为唯一可持久化的字典匹配键。 */
    public static String normalize(String value) {
        if (value == null) return "";
        return value.replace('（', '(').replace('）', ')')
                .replace('帐', '账')
                .replaceAll("[\\s:：()（）\\-—_、，,。.]", "")
                .trim().toLowerCase(Locale.ROOT);
    }
}

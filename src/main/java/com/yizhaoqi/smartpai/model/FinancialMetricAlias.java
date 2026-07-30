package com.yizhaoqi.smartpai.model;

import jakarta.persistence.*;
import lombok.Data;

/** 指标别名采用显式字典管理，避免在提取代码中散落大量公司特例。 */
@Data
@Entity
@Table(name = "financial_metric_alias", uniqueConstraints = @UniqueConstraint(name = "uk_metric_alias", columnNames = "normalized_alias"))
public class FinancialMetricAlias {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "metric_code", nullable = false, length = 64) private String metricCode;
    @Column(name = "alias_text", nullable = false, length = 128) private String aliasText;
    /** 去除空格、全半角符号后的匹配键，写入时由 MetricDictionary 统一生成。 */
    @Column(name = "normalized_alias", nullable = false, length = 128) private String normalizedAlias;
}

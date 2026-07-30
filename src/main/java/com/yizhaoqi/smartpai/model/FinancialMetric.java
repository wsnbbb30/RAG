package com.yizhaoqi.smartpai.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 跨公司、跨报告稳定的财务指标字典。
 * 指标编码是业务主键，报告中的不同叫法只能作为别名，不能直接充当事实主键。
 */
@Data
@Entity
@Table(name = "financial_metric")
public class FinancialMetric {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "metric_code", nullable = false, unique = true, length = 64) private String metricCode;
    @Column(name = "canonical_name", nullable = false, length = 128) private String canonicalName;
    @Enumerated(EnumType.STRING) @Column(name = "statement_type", nullable = false, length = 32)
    private StatementType statementType;
    @Enumerated(EnumType.STRING) @Column(name = "unit_type", nullable = false, length = 32)
    private UnitType unitType;
    /** S3-03 使用；原始指标没有公式时为空。 */
    @Column(name = "formula_expression", length = 512) private String formulaExpression;
    @Column(nullable = false) private boolean enabled = true;

    public enum StatementType { BALANCE_SHEET, INCOME_STATEMENT, CASH_FLOW, OTHER }
    public enum UnitType { CURRENCY, PERCENT, PER_SHARE, COUNT, OTHER }
}

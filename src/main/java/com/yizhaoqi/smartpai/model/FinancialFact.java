package com.yizhaoqi.smartpai.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 从报告表格提取出的、可定位且不丢失原始口径的财务事实。
 *
 * <p>value 为按 scale 换算后的标准值，rawValue/rawUnit 保留报告展示值；全程使用 BigDecimal，
 * 禁止 double，以保证后续确定性计算和审计复算一致。</p>
 */
@Data
@Entity
@Table(name = "financial_fact", uniqueConstraints = @UniqueConstraint(
        name = "uk_fact_source", columnNames = {"version_id", "metric_code", "period", "scope", "source_cell_id"}))
public class FinancialFact {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "version_id", nullable = false) private Long versionId;
    @Column(name = "metric_code", nullable = false, length = 64) private String metricCode;
    @Column(nullable = false, length = 32) private String period;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private FinancialReportMetadata.ReportScope scope;
    @Column(nullable = false, precision = 30, scale = 8) private BigDecimal value;
    @Column(name = "raw_value", nullable = false, precision = 30, scale = 8) private BigDecimal rawValue;
    @Column(name = "raw_unit", length = 128) private String rawUnit;
    @Column(nullable = false, length = 16) private String currency;
    @Column(nullable = false, precision = 20, scale = 8) private BigDecimal scale;
    @Column(name = "table_id", nullable = false) private Long tableId;
    @Column(name = "row_no", nullable = false) private Integer rowNo;
    @Column(name = "column_no", nullable = false) private Integer columnNo;
    @Column(name = "source_cell_id", nullable = false) private Long sourceCellId;
    @Column(name = "page_no", nullable = false) private Integer pageNo;
    @Column(precision = 10, scale = 2) private BigDecimal x0;
    @Column(precision = 10, scale = 2) private BigDecimal y0;
    @Column(precision = 10, scale = 2) private BigDecimal x1;
    @Column(precision = 10, scale = 2) private BigDecimal y1;
    @Lob @Column(name = "evidence_text", columnDefinition = "LONGTEXT") private String evidenceText;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Confidence confidence;
    @Enumerated(EnumType.STRING) @Column(name = "review_status", nullable = false, length = 16) private ReviewStatus reviewStatus;
    @Column(name = "extractor_version", nullable = false, length = 64) private String extractorVersion;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at") private LocalDateTime updatedAt;

    public enum Confidence { HIGH, MEDIUM, LOW }
    public enum ReviewStatus { PENDING, APPROVED, REJECTED }
}

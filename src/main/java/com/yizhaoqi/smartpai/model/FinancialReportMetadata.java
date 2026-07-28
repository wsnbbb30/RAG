package com.yizhaoqi.smartpai.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 年报元数据实体。
 * 存储从年报中提取的结构化财务信息，是后续FinancialFact抽取和财务计算的上下文基础
 *
 * 与DocumentVersion的关系：1:1（每个版本一份元数据）
 *
 * 核心设计原则：
 * -确定性优先：每条字段都记录置信度和提取来源
 * -不确定不猜：低置信度字段标记confidence=LOW，记录extraction_note说明原因
 * -可人工修正：提供MANUAL置信度级别，修正不改变原始提取记录
 *
 */
@Data
@Entity
@Table(name = "financial_report_metadata")
public class FinancialReportMetadata {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的文档版本 ID */
    @Column(name = "version_id", nullable = false, unique = true)
    private Long versionId;

    // ========== 确定性信息 ==========

    @Column(name = "company_name", length = 255, nullable = false)
    private String companyName;

    @Column(name = "stock_code", length = 10, nullable = false)
    private String stockCode;

    @Column(name = "report_type", length = 32, nullable = false)
    @Enumerated(EnumType.STRING)
    private Document.ReportType reportType;

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    /**
     * 会计期间: Q1/Q2/Q3/Q4/FY（全年）。
     * 年报默认为 FY（Full Year）。
     */
    @Column(length = 8, nullable = false)
    private String period = "FY";

    /**
     * 合并口径。
     * CONSOLIDATED: 合并报表（含子公司）
     * PARENT_COMPANY: 母公司报表（单体）
     * 默认 CONSOLIDATED，因为年报通常为合并报表。
     */
    @Column(length = 32, nullable = false)
    @Enumerated(EnumType.STRING)
    private ReportScope scope = ReportScope.CONSOLIDATED;

    /** 币种: CNY/USD/HKD */
    @Column(length = 8, nullable = false)
    private String currency = "CNY";

    // ========== 审计信息 ==========

    /**
     * 审计意见类型。
     * STANDARD_UNQUALIFIED: 标准无保留意见（最常见的健康信号）
     * QUALIFIED: 保留意见（有瑕疵但不是全面性问题）
     * ADVERSE: 否定意见（重大错报）
     * DISCLAIMER: 无法表示意见（审计范围受限）
     */
    @Column(name = "audit_opinion", length = 64)
    @Enumerated(EnumType.STRING)
    private AuditOpinion auditOpinion;

    /** 审计机构名称，如 "普华永道中天会计师事务所" */
    @Column(length = 128)
    private String auditor;

    // ========== 提取过程元数据 ==========

    /**
     * 置信度等级。
     * HIGH:   多个来源交叉验证一致（如文件名+封面都确认）
     * MEDIUM: 单一来源直接匹配
     * LOW:    模糊匹配或猜测（必须标记 extraction_note）
     * MANUAL: 人工确认/修正
     */
    @Column(length = 16, nullable = false)
    @Enumerated(EnumType.STRING)
    private Confidence confidence = Confidence.MEDIUM;

    /**
     * 提取来源: FILENAME / COVER_PAGE / AUDIT_PAGE / MANUAL
     */
    @Column(name = "extracted_from", length = 32, nullable = false)
    private String extractedFrom = "FILENAME";

    /**
     * 提取备注，低置信度时解释原因，人工修正时记录操作人
     */
    @Column(name = "extraction_note", columnDefinition = "TEXT")
    private String extractionNote;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ========== 枚举定义 ==========

    public enum ReportScope {
        CONSOLIDATED,
        PARENT_COMPANY
    }

    public enum AuditOpinion {
        STANDARD_UNQUALIFIED,
        QUALIFIED,
        ADVERSE,
        DISCLAIMER
    }

    public enum Confidence {
        HIGH,
        MEDIUM,
        LOW,
        MANUAL
    }
}

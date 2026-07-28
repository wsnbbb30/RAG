package com.yizhaoqi.smartpai.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 逻辑文档实体。
 * 代表"一份年报"的抽象概念，不与具体PDF文件绑定
 * 同一份报告即使多次修订（更换文件），也归属于同一个Document
 *
 * 自然键 documentId 格式：{STOCK_CODE}-{FISCAL_YEAR}-{REPORT_TYPE}-{LANG}
 * 示例：000002-2023-ANNUAL_REPORT-CN 表示万科 2023年度报告中文版
 */
@Data
@Entity
@Table(name = "document")
public class Document {
    //id
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //文档自然键，全局唯一
    @Column(name = "document_id", length = 64, nullable = false, unique = true)
    private String documentId;

    //公司全称
    @Column(name = "company_name", length = 255, nullable = false)
    private String companyName;

    //股票代码
    @Column(name = "stock_code", length = 10, nullable = false)
    private String stockCode;

    //报告类型枚举
    @Column(name = "report_type", length = 32, nullable = false)
    @Enumerated(EnumType.STRING)
    private ReportType reportType;

    //财年，如2023
    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    //语言：CN
    @Column(length = 8, nullable = false)
    private String language = "CN";

    //版本总数（同一逻辑文档的修订次数）
    @Column(name = "total_versions", nullable = false)
    private Integer totalVersions = 1;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


    //报告类型枚举
    public enum ReportType {
        ANNUAL_REPORT,      //年度报告
        SEMI_ANNUAL_REPORT, //半年度报告
        QUARTERLY_REPORT    //季度报告
    }
}

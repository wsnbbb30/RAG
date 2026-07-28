package com.yizhaoqi.smartpai.model.dto;

import lombok.Data;

/**
 * 人工审核/修正年报元数据的请求体。
 * 仅发送需要修改的字段，不需要修改的字段留空。
 */
@Data
public class ReportMetadataReviewRequest {

    /** 要修改的版本 ID（必填） */
    private Long versionId;

    /** 修正后的公司全称 */
    private String companyName;

    /** 修正后的股票代码 */
    private String stockCode;

    /** 修正后的报告类型: ANNUAL_REPORT / SEMI_ANNUAL_REPORT / QUARTERLY_REPORT */
    private String reportType;

    /** 修正后的财年 */
    private Integer fiscalYear;

    /** 修正后的会计期间: Q1/Q2/Q3/Q4/FY */
    private String period;

    /** 修正后的合并口径: CONSOLIDATED / PARENT_COMPANY */
    private String scope;

    /** 修正后的币种 */
    private String currency;

    /** 修正后的审计意见 */
    private String auditOpinion;

    /** 修正后的审计机构 */
    private String auditor;

    /** 审核备注 */
    private String reviewNote;
}

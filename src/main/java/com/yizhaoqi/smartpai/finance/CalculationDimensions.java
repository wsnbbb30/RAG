package com.yizhaoqi.smartpai.finance;

import com.yizhaoqi.smartpai.model.FinancialReportMetadata;

/** 一次计算的严格事实筛选维度。 */
public record CalculationDimensions(Long versionId, String period, FinancialReportMetadata.ReportScope scope) { }

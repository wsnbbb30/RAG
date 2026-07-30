package com.yizhaoqi.smartpai.model.dto;

import java.math.BigDecimal;

/**
 * 人工复核只允许修正标准化结果和状态；原始单元格证据、bbox 和抽取版本不可被覆盖。
 */
public record FinancialFactReviewRequest(BigDecimal value, String currency, String reviewStatus) { }

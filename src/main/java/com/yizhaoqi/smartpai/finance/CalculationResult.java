package com.yizhaoqi.smartpai.finance;

import java.math.BigDecimal;

/** 确定性计算结果：value 仅在 CALCULATED 时存在。 */
public record CalculationResult(String metricCode, CalculationStatus status, BigDecimal value,
                                String unit, String reason, CalculationTrace trace) { }

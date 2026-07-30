package com.yizhaoqi.smartpai.finance;

import java.math.BigDecimal;
import java.util.List;

/** 前端和面试演示均可展示的可解释计算轨迹。 */
public record CalculationTrace(String formulaVersion, String expression, List<Input> inputs) {
    public record Input(String metricCode, BigDecimal value, Long factId, Long sourceCellId) { }
}

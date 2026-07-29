package com.yizhaoqi.smartpai.eval.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** 从 FinAR-Bench ground truth Markdown 表格中解析出的预期事实。年份列名由表头动态决定。 */
public record GroundTruthFact(String metricName, Map<String, String> yearValues) {
    public GroundTruthFact {
        yearValues = Map.copyOf(yearValues);
    }

    public static GroundTruthFact of(String metricName, LinkedHashMap<String, String> yearValues) {
        return new GroundTruthFact(metricName, yearValues);
    }
}

package com.yizhaoqi.smartpai.retrieval;

/**
 * 从问题中高置信度提取的结构化过滤条件。
 * null 表示”未确定”，必须不加该条件，避免把模糊问题错误缩小到某家公司或年度。
 */
public record QueryFilter(String stockCode, Integer fiscalYear, String reportType,
                          java.util.List<String> metricCodes) {
    public QueryFilter(String stockCode, Integer fiscalYear, String reportType) {
        this(stockCode, fiscalYear, reportType, java.util.List.of());
    }
    public static QueryFilter empty() { return new QueryFilter(null, null, null, java.util.List.of()); }
    public boolean isEmpty() { return stockCode == null && fiscalYear == null && reportType == null && metricCodes.isEmpty(); }
}

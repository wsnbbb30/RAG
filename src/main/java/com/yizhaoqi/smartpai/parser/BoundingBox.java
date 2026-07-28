package com.yizhaoqi.smartpai.parser;

import java.math.BigDecimal;

/** PDF 坐标系边界框；坐标统一使用左上/右下，单位为 PDF point。 */
public record BoundingBox(BigDecimal x0, BigDecimal y0, BigDecimal x1, BigDecimal y1) {
    public static BoundingBox empty() { return new BoundingBox(null, null, null, null); }
}
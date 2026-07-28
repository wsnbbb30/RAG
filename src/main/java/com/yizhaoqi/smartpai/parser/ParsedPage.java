package com.yizhaoqi.smartpai.parser;

import java.math.BigDecimal;
import java.util.List;

/** 一页解析结果；页面元素的阅读顺序必须已经稳定。 */
public record ParsedPage(
        int pageNo,
        BigDecimal width,
        BigDecimal height,
        int rotation,
        int textCharCount,
        boolean ocrRecommended,
        List<ParsedElement> elements) {
}
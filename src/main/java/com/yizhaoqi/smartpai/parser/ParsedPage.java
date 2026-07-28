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
        List<ParsedElement> elements,
        List<ParsedTable> tables) {
    /**
     * 保持 S1-02 调用方兼容：未启用表格识别的解析器可继续只返回页面元素。
     */
    public ParsedPage(int pageNo, BigDecimal width, BigDecimal height, int rotation, int textCharCount,
                      boolean ocrRecommended, List<ParsedElement> elements) {
        this(pageNo, width, height, rotation, textCharCount, ocrRecommended, elements, List.of());
    }
}

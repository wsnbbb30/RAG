package com.yizhaoqi.smartpai.parser;

/** 解析器返回的纯数据对象，不含数据库 ID。 */
public record ParsedElement(
        ElementType elementType,
        String textContent,
        int orderNo,
        BoundingBox boundingBox,
        Integer headingLevel,
        double confidence) {
}
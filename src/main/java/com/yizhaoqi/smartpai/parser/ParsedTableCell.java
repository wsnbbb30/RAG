package com.yizhaoqi.smartpai.parser;

/**
 * 表格单元格的原始结构化输出。
 * row/column 从 0 开始，span 使用 1 表示未合并；这样可避免数据库层的隐式坐标约定。
 */
public record ParsedTableCell(
        int pageNo,
        int rowNo,
        int columnNo,
        int rowSpan,
        int columnSpan,
        String textContent,
        BoundingBox boundingBox,
        double confidence) {
}

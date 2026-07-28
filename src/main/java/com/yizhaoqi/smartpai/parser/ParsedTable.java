package com.yizhaoqi.smartpai.parser;

import java.util.List;

/**
 * 解析层的表格快照，不携带 JPA ID。
 *
 * <p>表格与普通段落同时保留：段落用于兼容现有全文检索，表格快照用于后续的
 * FinancialFact 数值抽取，二者都可以通过页码和 bbox 回溯到原始 PDF。</p>
 */
public record ParsedTable(
        int pageStart,
        int pageEnd,
        String title,
        String unitText,
        BoundingBox boundingBox,
        double confidence,
        List<ParsedTableCell> cells) {
    public ParsedTable {
        cells = List.copyOf(cells);
    }
}

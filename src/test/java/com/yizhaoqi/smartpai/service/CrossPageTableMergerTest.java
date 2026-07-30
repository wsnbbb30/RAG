package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.parser.BoundingBox;
import com.yizhaoqi.smartpai.parser.ParsedTable;
import com.yizhaoqi.smartpai.parser.ParsedTableCell;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证续表合并不会重复表头，也不会误合并无依据的相邻表。 */
class CrossPageTableMergerTest {
    private final CrossPageTableMerger merger = new CrossPageTableMerger();

    @Test
    void shouldMergeConsecutiveTablesAndRemoveRepeatedHeader() {
        List<ParsedTable> result = merger.merge(List.of(table(1, "合并利润表", "项目", "金额"), table(2, "合并利润表", "项目", "金额")));

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).pageStart());
        assertEquals(2, result.get(0).pageEnd());
        // 第一页两行四格 + 第二页去掉重复表头后的一行两格。
        assertEquals(6, result.get(0).cells().size());
        assertEquals(2, result.get(0).cells().get(4).rowNo());
    }

    @Test
    void shouldNotMergeTablesWithoutMatchingTitleOrHeader() {
        List<ParsedTable> result = merger.merge(List.of(table(1, null, "资产", "金额"), table(2, null, "负债", "金额")));
        assertEquals(2, result.size());
    }

    private ParsedTable table(int page, String title, String headerLeft, String headerRight) {
        BoundingBox box = new BoundingBox(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN);
        return new ParsedTable(page, page, title, "单位：元", box, 0.8D, List.of(
                new ParsedTableCell(page, 0, 0, 1, 1, headerLeft, box, 0.8D),
                new ParsedTableCell(page, 0, 1, 1, 1, headerRight, box, 0.8D),
                new ParsedTableCell(page, 1, 0, 1, 1, "营业收入", box, 0.8D),
                new ParsedTableCell(page, 1, 1, 1, 1, "100", box, 0.8D)));
    }
}

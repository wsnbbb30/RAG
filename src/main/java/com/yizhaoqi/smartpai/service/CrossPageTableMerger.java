package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.parser.BoundingBox;
import com.yizhaoqi.smartpai.parser.ParsedTable;
import com.yizhaoqi.smartpai.parser.ParsedTableCell;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 合并相邻页面的续表。
 *
 * <p>只在页码连续、列数一致，且标题或表头一致时合并，宁可少合并也不能把两张独立
 * 财务表拼在一起。续页重复表头会被移除，后续数据行的 rowNo 会整体平移。</p>
 */
@Component
public class CrossPageTableMerger {
    public List<ParsedTable> merge(List<ParsedTable> source) {
        List<ParsedTable> sorted = source.stream()
                .sorted(Comparator.comparingInt(ParsedTable::pageStart)).toList();
        List<ParsedTable> merged = new ArrayList<>();
        for (ParsedTable current : sorted) {
            if (!merged.isEmpty() && shouldMerge(merged.get(merged.size() - 1), current)) {
                ParsedTable previous = merged.remove(merged.size() - 1);
                merged.add(mergeTwo(previous, current));
            } else {
                merged.add(current);
            }
        }
        return List.copyOf(merged);
    }

    private boolean shouldMerge(ParsedTable previous, ParsedTable current) {
        String previousTitle = normalize(previous.title());
        String currentTitle = normalize(current.title());
        boolean sameNonBlankTitle = !previousTitle.isBlank() && previousTitle.equals(currentTitle);
        String previousHeader = header(previous);
        String currentHeader = header(current);
        return previous.pageEnd() + 1 == current.pageStart()
                && columnCount(previous) == columnCount(current)
                && (sameNonBlankTitle || (!previousHeader.isBlank() && previousHeader.equals(currentHeader)));
    }

    private ParsedTable mergeTwo(ParsedTable first, ParsedTable second) {
        int rowOffset = first.cells().stream().mapToInt(ParsedTableCell::rowNo).max().orElse(-1) + 1;
        boolean repeatedHeader = header(first).equals(header(second));
        List<ParsedTableCell> cells = new ArrayList<>(first.cells());
        second.cells().stream()
                .filter(cell -> !(repeatedHeader && cell.rowNo() == 0))
                .map(cell -> new ParsedTableCell(cell.pageNo(), cell.rowNo() + rowOffset - (repeatedHeader ? 1 : 0),
                        cell.columnNo(), cell.rowSpan(), cell.columnSpan(), cell.textContent(), cell.boundingBox(), cell.confidence()))
                .forEach(cells::add);
        return new ParsedTable(first.pageStart(), second.pageEnd(), first.title(), first.unitText(),
                union(first.boundingBox(), second.boundingBox()), Math.min(first.confidence(), second.confidence()), cells);
    }

    private int columnCount(ParsedTable table) {
        return table.cells().stream().mapToInt(ParsedTableCell::columnNo).max().orElse(-1) + 1;
    }
    private String header(ParsedTable table) {
        return table.cells().stream().filter(cell -> cell.rowNo() == 0).sorted(Comparator.comparingInt(ParsedTableCell::columnNo))
                .map(cell -> normalize(cell.textContent())).reduce((a, b) -> a + "|" + b).orElse("");
    }
    private String normalize(String value) { return value == null ? "" : value.replaceAll("\\s+", "").trim().toLowerCase(Locale.ROOT); }
    private BoundingBox union(BoundingBox left, BoundingBox right) {
        if (left == null || left.x0() == null) return right;
        if (right == null || right.x0() == null) return left;
        return new BoundingBox(left.x0().min(right.x0()), left.y0().min(right.y0()), left.x1().max(right.x1()), left.y1().max(right.y1()));
    }
}

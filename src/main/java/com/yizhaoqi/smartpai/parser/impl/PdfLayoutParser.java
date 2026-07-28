package com.yizhaoqi.smartpai.parser.impl;

import com.yizhaoqi.smartpai.config.DocumentParserProperties;
import com.yizhaoqi.smartpai.parser.BoundingBox;
import com.yizhaoqi.smartpai.parser.DocumentParser;
import com.yizhaoqi.smartpai.parser.ElementType;
import com.yizhaoqi.smartpai.parser.ParseRequest;
import com.yizhaoqi.smartpai.parser.ParseResult;
import com.yizhaoqi.smartpai.parser.ParsedElement;
import com.yizhaoqi.smartpai.parser.ParsedPage;
import com.yizhaoqi.smartpai.parser.ParserType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 文字型 PDF 的页级布局解析器。
 *
 * <p>与只读取 {@code PDFTextStripper#getText()} 的实现不同，本类在 PDFBox 回调中保留
 * {@link TextPosition}。每个输出元素都由实际字符坐标合并得到 bbox，并按“文本行 + 垂直间距”
 * 聚合为段落。因此一页不再退化成一个大文本元素。</p>
 *
 * <p>范围：数字 PDF 文本层。扫描件仍只会被标记为 OCR 推荐，OCR、表格网格和图片抽取由后续
 * 迭代完成，不能将本类的 PARAGRAPH 误认为已识别出的 TABLE。</p>
 */
@Component
public class PdfLayoutParser implements DocumentParser {

    /** 两行基线距离超过该倍数时，视为新的段落。 */
    private static final float PARAGRAPH_GAP_MULTIPLIER = 1.45F;
    /** 很短且不含典型句末标点的行可作为标题候选。 */
    private static final int TITLE_MAX_LENGTH = 60;

    private final DocumentParserProperties properties;

    public PdfLayoutParser(DocumentParserProperties properties) {
        this.properties = properties;
    }

    @Override
    public ParserType type() {
        return ParserType.PDF_LAYOUT;
    }

    @Override
    public boolean supports(String fileName, String contentType) {
        return (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf"))
                || "application/pdf".equalsIgnoreCase(contentType);
    }

    @Override
    public ParseResult parse(ParseRequest request) throws Exception {
        try (InputStream input = request.inputStream();
             PDDocument document = Loader.loadPDF(input.readAllBytes())) {
            List<ParsedPage> pages = new ArrayList<>();
            for (int index = 0; index < document.getNumberOfPages(); index++) {
                PDPage page = document.getPage(index);
                List<LayoutLine> lines = extractPageLines(document, index + 1);
                List<ParsedElement> elements = toParagraphElements(lines);
                int textCharCount = lines.stream().mapToInt(line -> line.text().length()).sum();
                pages.add(new ParsedPage(index + 1,
                        BigDecimal.valueOf(page.getMediaBox().getWidth()),
                        BigDecimal.valueOf(page.getMediaBox().getHeight()),
                        page.getRotation(), textCharCount,
                        textCharCount < properties.getOcrTextThreshold(), elements));
            }
            return new ParseResult(type(), properties.getPdfLayoutVersion(), pages);
        }
    }

    /**
     * 以 PDFBox 的“文本写出单元”为基础提取行。启用按坐标排序后，PDFBox 会以可读顺序
     * 调用 writeString；每次回调包含同一视觉行的 TextPosition 列表。
     */
    private List<LayoutLine> extractPageLines(PDDocument document, int pageNo) throws IOException {
        PositionAwareTextStripper stripper = new PositionAwareTextStripper();
        stripper.setSortByPosition(true);
        stripper.setStartPage(pageNo);
        stripper.setEndPage(pageNo);
        stripper.getText(document);
        return stripper.lines().stream()
                .sorted(Comparator.comparing(LayoutLine::y0).thenComparing(LayoutLine::x0))
                .toList();
    }

    /** 把相邻行合并为段落，同时为每个段落保留字符坐标的并集。 */
    private List<ParsedElement> toParagraphElements(List<LayoutLine> lines) {
        List<ParsedElement> result = new ArrayList<>();
        List<LayoutLine> paragraph = new ArrayList<>();
        int orderNo = 1;
        for (LayoutLine line : lines) {
            if (looksLikeTitle(line)) {
                orderNo = appendParagraph(result, paragraph, orderNo);
                paragraph.clear();
                result.add(toElement(List.of(line), ElementType.TITLE, orderNo++, 1));
                continue;
            }
            if (!paragraph.isEmpty() && startsNewParagraph(paragraph.get(paragraph.size() - 1), line)) {
                orderNo = appendParagraph(result, paragraph, orderNo);
                paragraph.clear();
            }
            paragraph.add(line);
        }
        appendParagraph(result, paragraph, orderNo);
        return result;
    }

    private int appendParagraph(List<ParsedElement> output, List<LayoutLine> lines, int orderNo) {
        if (lines.isEmpty()) {
            return orderNo;
        }
        output.add(toElement(lines, ElementType.PARAGRAPH, orderNo, null));
        return orderNo + 1;
    }

    private boolean startsNewParagraph(LayoutLine previous, LayoutLine current) {
        float verticalGap = current.y0() - previous.y1();
        float baselineGap = current.y0() - previous.y0();
        float expectedLineHeight = Math.max(previous.height(), current.height());
        return verticalGap > expectedLineHeight * 0.45F
                || baselineGap > expectedLineHeight * PARAGRAPH_GAP_MULTIPLIER;
    }

    private boolean looksLikeTitle(LayoutLine line) {
        String text = line.text();
        return text.length() <= TITLE_MAX_LENGTH
                && !text.matches(".*[。；，、,.!?！？:].*")
                && line.height() >= 9.0F;
    }

    private ParsedElement toElement(List<LayoutLine> lines, ElementType type, int orderNo, Integer headingLevel) {
        String content = lines.stream().map(LayoutLine::text).reduce((left, right) -> left + "\n" + right).orElse("");
        float x0 = lines.stream().map(LayoutLine::x0).min(Float::compare).orElse(0F);
        float y0 = lines.stream().map(LayoutLine::y0).min(Float::compare).orElse(0F);
        float x1 = lines.stream().map(LayoutLine::x1).max(Float::compare).orElse(0F);
        float y1 = lines.stream().map(LayoutLine::y1).max(Float::compare).orElse(0F);
        return new ParsedElement(type, content, orderNo,
                new BoundingBox(decimal(x0), decimal(y0), decimal(x1), decimal(y1)),
                headingLevel, 1.0D);
    }

    private BigDecimal decimal(float value) {
        return BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** PDFBox 回调适配器：不丢弃 TextPosition，计算每行的真实坐标范围。 */
    private static final class PositionAwareTextStripper extends PDFTextStripper {
        private final List<LayoutLine> lines = new ArrayList<>();

        private PositionAwareTextStripper() throws IOException {
            super();
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            String normalized = text == null ? "" : text.replaceAll("[\\t ]+", " ").trim();
            if (normalized.isBlank() || positions == null || positions.isEmpty()) {
                return;
            }
            List<TextPosition> visible = positions.stream()
                    .filter(position -> !position.getUnicode().isBlank())
                    .toList();
            if (visible.isEmpty()) {
                return;
            }
            float x0 = visible.stream().map(TextPosition::getXDirAdj).min(Float::compare).orElse(0F);
            float y0 = visible.stream().map(TextPosition::getYDirAdj).min(Float::compare).orElse(0F);
            float x1 = visible.stream().map(position -> position.getXDirAdj() + position.getWidthDirAdj())
                    .max(Float::compare).orElse(x0);
            float y1 = visible.stream().map(position -> position.getYDirAdj() + position.getHeightDir())
                    .max(Float::compare).orElse(y0);
            float height = visible.stream().map(TextPosition::getHeightDir).max(Float::compare).orElse(0F);
            lines.add(new LayoutLine(normalized, x0, y0, x1, y1, height));
        }

        private List<LayoutLine> lines() {
            return List.copyOf(lines);
        }
    }

    /** 页内视觉行；坐标来自 PDFBox 的方向校正坐标系，单位为 point。 */
    private record LayoutLine(String text, float x0, float y0, float x1, float y1, float height) { }
}

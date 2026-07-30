package com.yizhaoqi.smartpai.parser.impl;

import com.yizhaoqi.smartpai.config.DocumentParserProperties;
import com.yizhaoqi.smartpai.parser.ParseRequest;
import com.yizhaoqi.smartpai.parser.ParseResult;
import com.yizhaoqi.smartpai.parser.ParserType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用巨潮资讯公开年报验证 PDF 布局解析器。
 * 只校验解析契约，不把特定页数、标题文字等容易随公告修订变化的内容写死。
 */
class PdfLayoutParserTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "pdf/600519_2023_annual_report.pdf",
            "pdf/000002_2023_annual_report_summary.pdf"
    })
    void shouldParseRealCninfoAnnualReportByPage(String resourcePath) throws Exception {
        DocumentParserProperties properties = new DocumentParserProperties();
        properties.setPdfLayoutVersion("pdf-layout-test-v1");
        properties.setOcrTextThreshold(30);
        PdfLayoutParser parser = new PdfLayoutParser(properties);

        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(input, "测试 PDF 不存在: " + resourcePath);
            ParseResult result = parser.parse(new ParseRequest(1L, resourcePath, "application/pdf", input));

            assertEquals(ParserType.PDF_LAYOUT, result.parserType());
            assertEquals("pdf-layout-test-v1", result.parserVersion());
            assertFalse(result.pages().isEmpty(), "真实年报至少应包含一页");
            assertTrue(result.pages().stream().allMatch(page -> page.pageNo() > 0));
            assertTrue(result.pages().stream().allMatch(page -> page.width().signum() > 0 && page.height().signum() > 0));

            long elementCount = result.pages().stream().mapToLong(page -> page.elements().size()).sum();
            assertTrue(elementCount > result.pages().size(), "文本型 PDF 不应退化为每页一个大文本元素");

            result.pages().forEach(page -> {
                for (int index = 0; index < page.elements().size(); index++) {
                    assertEquals(index + 1, page.elements().get(index).orderNo(), "页内阅读顺序必须连续");
                    assertFalse(page.elements().get(index).textContent().isBlank(), "元素文本不能为空");
                    assertNotNull(page.elements().get(index).boundingBox().x0(), "文字型 PDF 元素必须有 bbox x0");
                    assertNotNull(page.elements().get(index).boundingBox().y0(), "文字型 PDF 元素必须有 bbox y0");
                    assertTrue(page.elements().get(index).boundingBox().x1()
                            .compareTo(page.elements().get(index).boundingBox().x0()) > 0, "bbox 宽度必须为正");
                    assertTrue(page.elements().get(index).boundingBox().y1()
                            .compareTo(page.elements().get(index).boundingBox().y0()) > 0, "bbox 高度必须为正");
                }
            });
        }
    }
}

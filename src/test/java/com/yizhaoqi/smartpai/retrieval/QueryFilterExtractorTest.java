package com.yizhaoqi.smartpai.retrieval;

import com.yizhaoqi.smartpai.model.FinancialMetricAlias;
import com.yizhaoqi.smartpai.repository.FinancialMetricAliasRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 过滤器只能从明确表达中取值；没有元数据条件的问题必须保持空过滤。 */
class QueryFilterExtractorTest {
    private QueryFilterExtractor extractor;

    @BeforeEach
    void setUp() {
        FinancialMetricAliasRepository aliasRepo = mock(FinancialMetricAliasRepository.class);
        when(aliasRepo.findAll()).thenReturn(List.of(
                alias("OPERATING_REVENUE", "营业收入", "营业收入"),
                alias("NET_PROFIT", "净利润", "净利润"),
                alias("NET_PROFIT", "归属于母公司所有者的净利润", "归属于母公司所有者的净利润"),
                alias("TOTAL_ASSETS", "总资产", "总资产")
        ));
        extractor = new QueryFilterExtractor(aliasRepo);
    }

    private static FinancialMetricAlias alias(String metricCode, String text, String normalized) {
        FinancialMetricAlias a = new FinancialMetricAlias();
        a.setMetricCode(metricCode);
        a.setAliasText(text);
        a.setNormalizedAlias(normalized);
        return a;
    }

    @Test
    void extractsExplicitStockYearAndReportType() {
        QueryFilter filter = extractor.extract("请找 600519 2023 年年度报告中的营业收入");
        assertEquals("600519", filter.stockCode());
        assertEquals(2023, filter.fiscalYear());
        assertEquals("ANNUAL_REPORT", filter.reportType());
        assertTrue(filter.metricCodes().contains("OPERATING_REVENUE"));
    }

    @Test
    void extractsMultipleMetrics() {
        QueryFilter filter = extractor.extract("600036 2023年度营业收入和净利润分别是多少");
        assertEquals("600036", filter.stockCode());
        assertEquals(2023, filter.fiscalYear());
        assertEquals(2, filter.metricCodes().size());
        assertTrue(filter.metricCodes().contains("OPERATING_REVENUE"));
        assertTrue(filter.metricCodes().contains("NET_PROFIT"));
    }

    @Test
    void keepsAmbiguousQuestionUnfiltered() {
        QueryFilter filter = extractor.extract("公司的偿债风险有哪些？");
        assertNull(filter.stockCode());
        assertNull(filter.fiscalYear());
        assertNull(filter.reportType());
        assertTrue(filter.metricCodes().isEmpty());
    }

    @Test
    void prefersLongerAliasMatch() {
        // "归属于母公司所有者的净利润" 比 "净利润" 更长，应优先匹配长的
        QueryFilter filter = extractor.extract("归属于母公司所有者的净利润是多少");
        assertTrue(filter.metricCodes().contains("NET_PROFIT"));
    }

    @Test
    void stockCodeNotPrefixedByDigit() {
        // 7 位数字不应被当作股票代码
        QueryFilter filter = extractor.extract("1234567 年的数据");
        assertNull(filter.stockCode());
    }
}

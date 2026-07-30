package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.*;
import com.yizhaoqi.smartpai.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证“指标行 + 年份列 + 万元单位”会生成可回溯且已换算的事实。 */
class FactExtractorTest {
    @Test
    void shouldExtractTraceableFactWithPeriodAndUnitNormalization() {
        TableModelRepository tableRepository = mock(TableModelRepository.class);
        TableCellRepository cellRepository = mock(TableCellRepository.class);
        FinancialReportMetadataRepository metadataRepository = mock(FinancialReportMetadataRepository.class);
        FinancialFactRepository factRepository = mock(FinancialFactRepository.class);
        DocumentPageRepository pageRepository = mock(DocumentPageRepository.class);
        MetricDictionary dictionary = mock(MetricDictionary.class);

        FinancialReportMetadata metadata = new FinancialReportMetadata();
        metadata.setFiscalYear(2023); metadata.setCurrency("CNY");
        metadata.setScope(FinancialReportMetadata.ReportScope.CONSOLIDATED);
        when(metadataRepository.findByVersionId(1L)).thenReturn(Optional.of(metadata));
        DocumentPage page = new DocumentPage(); page.setId(10L); page.setPageNo(8);
        when(pageRepository.findByVersionIdOrderByPageNoAsc(1L)).thenReturn(List.of(page));
        TableModel table = new TableModel(); table.setId(20L); table.setUnitText("单位：万元");
        table.setTitleText("合并利润表"); table.setPageStart(8); table.setConfidence(new BigDecimal("0.90"));
        when(tableRepository.findByVersionIdOrderByPageStartAscIdAsc(1L)).thenReturn(List.of(table));
        when(cellRepository.findByTableIdOrderByRowNoAscColumnNoAsc(20L)).thenReturn(List.of(
                cell(1L, 10L, 0, 0, "项目"), cell(2L, 10L, 0, 1, "2023年"),
                cell(3L, 10L, 1, 0, "营业收入"), cell(4L, 10L, 1, 1, "1,234.50")));
        FinancialMetric metric = new FinancialMetric(); metric.setMetricCode("OPERATING_REVENUE");
        when(dictionary.resolve("营业收入")).thenReturn(Optional.of(metric));
        when(dictionary.resolve(argThat(value -> !"营业收入".equals(value)))).thenReturn(Optional.empty());
        when(factRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        FactExtractor extractor = new FactExtractor(tableRepository, cellRepository, metadataRepository, factRepository,
                pageRepository, dictionary, new UnitNormalizer());
        List<FinancialFact> facts = extractor.replaceFacts(1L);

        assertEquals(1, facts.size());
        FinancialFact fact = facts.get(0);
        assertEquals("OPERATING_REVENUE", fact.getMetricCode());
        assertEquals("FY2023", fact.getPeriod());
        assertEquals(new BigDecimal("12345000.00000000"), fact.getValue());
        assertEquals(8, fact.getPageNo());
        assertEquals(4L, fact.getSourceCellId());
        assertTrue(fact.getEvidenceText().contains("营业收入"));
    }

    private TableCell cell(Long id, Long pageId, int row, int column, String text) {
        TableCell cell = new TableCell();
        cell.setId(id); cell.setPageId(pageId); cell.setRowNo(row); cell.setColumnNo(column); cell.setTextContent(text);
        cell.setConfidence(new BigDecimal("0.90"));
        return cell;
    }
}

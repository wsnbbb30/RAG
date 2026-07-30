package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.finance.*;
import com.yizhaoqi.smartpai.model.FinancialFact;
import com.yizhaoqi.smartpai.model.FinancialReportMetadata;
import com.yizhaoqi.smartpai.repository.FinancialFactRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 覆盖利润率、除零拒绝与冲突事实，保证计算不会被 LLM 或 double 精度影响。 */
class FinancialCalculatorTest {
    @Test
    void shouldCalculateNetProfitMarginWithTrace() {
        FinancialFactRepository repository = mock(FinancialFactRepository.class);
        FinancialReportMetadata.ReportScope scope = FinancialReportMetadata.ReportScope.CONSOLIDATED;
        when(repository.findByVersionIdAndMetricCodeAndPeriodAndScope(1L, "NET_PROFIT", "FY2023", scope)).thenReturn(List.of(fact(1L, "NET_PROFIT", "100")));
        when(repository.findByVersionIdAndMetricCodeAndPeriodAndScope(1L, "OPERATING_REVENUE", "FY2023", scope)).thenReturn(List.of(fact(2L, "OPERATING_REVENUE", "1000")));

        CalculationResult result = new FinancialCalculator(repository, new FormulaRegistry())
                .calculate("NET_PROFIT_MARGIN", new CalculationDimensions(1L, "FY2023", scope));

        assertEquals(CalculationStatus.CALCULATED, result.status());
        assertEquals(new BigDecimal("10.000000"), result.value());
        assertEquals(2, result.trace().inputs().size());
    }

    @Test
    void shouldRefuseWhenDenominatorIsZero() {
        FinancialFactRepository repository = mock(FinancialFactRepository.class);
        FinancialReportMetadata.ReportScope scope = FinancialReportMetadata.ReportScope.CONSOLIDATED;
        when(repository.findByVersionIdAndMetricCodeAndPeriodAndScope(anyLong(), eq("NET_PROFIT"), anyString(), eq(scope))).thenReturn(List.of(fact(1L, "NET_PROFIT", "100")));
        when(repository.findByVersionIdAndMetricCodeAndPeriodAndScope(anyLong(), eq("OPERATING_REVENUE"), anyString(), eq(scope))).thenReturn(List.of(fact(2L, "OPERATING_REVENUE", "0")));

        CalculationResult result = new FinancialCalculator(repository, new FormulaRegistry())
                .calculate("NET_PROFIT_MARGIN", new CalculationDimensions(1L, "FY2023", scope));
        assertEquals(CalculationStatus.INSUFFICIENT, result.status());
    }

    private FinancialFact fact(Long id, String metric, String value) {
        FinancialFact fact = new FinancialFact(); fact.setId(id); fact.setMetricCode(metric); fact.setValue(new BigDecimal(value));
        fact.setSourceCellId(id + 100); fact.setReviewStatus(FinancialFact.ReviewStatus.APPROVED); return fact;
    }
}

package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.finance.*;
import com.yizhaoqi.smartpai.model.FinancialFact;
import com.yizhaoqi.smartpai.model.FinancialMetric;
import com.yizhaoqi.smartpai.rag.CitationVerifier;
import com.yizhaoqi.smartpai.rag.Evidence;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 故意篡改金额和引用编号时，核验器必须产生 HIGH 问题。 */
class FinancialAnswerVerifierTest {
    @Test
    void shouldDetectWrongFinancialNumberAndFakeCitation() {
        MetricDictionary dictionary = mock(MetricDictionary.class);
        FinancialMetric metric = new FinancialMetric(); metric.setMetricCode("OPERATING_REVENUE");
        when(dictionary.resolve("营业收入")).thenReturn(Optional.of(metric));
        FinancialAnswerVerifier verifier = new FinancialAnswerVerifier(new NumericClaimExtractor(dictionary, new UnitNormalizer()), new CitationVerifier());
        FinancialFact fact = new FinancialFact(); fact.setMetricCode("OPERATING_REVENUE"); fact.setValue(new BigDecimal("100"));
        fact.setPeriod("FY2023"); fact.setReviewStatus(FinancialFact.ReviewStatus.APPROVED);
        Evidence evidence = new Evidence("E1", 1L, 1L, 1, 1, "doc", "营业收入 100");

        VerificationReport report = verifier.verify(new DraftAnswer("2023年营业收入为 120。[E99]"), List.of(fact), List.of(evidence));

        assertTrue(report.hasBlockingIssue());
        assertTrue(report.issues().stream().anyMatch(issue -> "NUMERIC_MISMATCH".equals(issue.code())));
        assertTrue(report.issues().stream().anyMatch(issue -> "INVALID_CITATION".equals(issue.code())));
    }
}

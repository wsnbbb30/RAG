package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.finance.*;
import com.yizhaoqi.smartpai.model.FinancialFact;
import com.yizhaoqi.smartpai.rag.CitationVerification;
import com.yizhaoqi.smartpai.rag.CitationVerifier;
import com.yizhaoqi.smartpai.rag.Evidence;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * S3-04 数字、年份和引用核验器。
 *
 * <p>仅将本轮 Evidence 所属版本的 FinancialFact 作为真相源。找不到对应事实时不把
 * 模型数值判错；但引用伪造、已知指标数值不一致、或答案与事实年份冲突会产生 HIGH 问题。</p>
 */
@Service
public class FinancialAnswerVerifier {
    private static final BigDecimal TOLERANCE = new BigDecimal("0.000001");
    private final NumericClaimExtractor claimExtractor;
    private final CitationVerifier citationVerifier;

    public FinancialAnswerVerifier(NumericClaimExtractor claimExtractor, CitationVerifier citationVerifier) {
        this.claimExtractor = claimExtractor; this.citationVerifier = citationVerifier;
    }

    public VerificationReport verify(DraftAnswer answer, List<FinancialFact> facts, List<Evidence> evidence) {
        List<VerificationIssue> issues = new ArrayList<>();
        CitationVerification citations = citationVerifier.verify(answer.content(), evidence);
        if (!citations.valid()) issues.add(new VerificationIssue(VerificationIssue.Severity.HIGH, "INVALID_CITATION", citations.reason(), answer.content()));
        for (NumericClaim claim : claimExtractor.extract(answer.content())) {
            List<FinancialFact> candidates = facts.stream().filter(fact -> fact.getMetricCode().equals(claim.metricCode()))
                    .filter(fact -> fact.getReviewStatus() != FinancialFact.ReviewStatus.REJECTED).toList();
            if (candidates.isEmpty()) continue;
            boolean matches = candidates.stream().anyMatch(fact -> close(fact.getValue(), claim.normalizedValue()));
            if (!matches) {
                issues.add(new VerificationIssue(VerificationIssue.Severity.HIGH, "NUMERIC_MISMATCH",
                        "答案中的 " + claim.metricCode() + " 数值与已授权财务事实不一致", claim.rawText()));
            }
            checkYear(claim, candidates, issues);
        }
        return new VerificationReport(issues);
    }

    private void checkYear(NumericClaim claim, List<FinancialFact> facts, List<VerificationIssue> issues) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(20\\d{2})年").matcher(claim.sentence());
        if (!matcher.find()) return;
        String expectedPeriod = "FY" + matcher.group(1);
        if (facts.stream().noneMatch(fact -> expectedPeriod.equals(fact.getPeriod()))) {
            issues.add(new VerificationIssue(VerificationIssue.Severity.HIGH, "PERIOD_MISMATCH",
                    "答案年份与该指标已授权事实期间不一致", claim.sentence()));
        }
    }
    private boolean close(BigDecimal left, BigDecimal right) { return left.subtract(right).abs().compareTo(TOLERANCE) <= 0; }
}

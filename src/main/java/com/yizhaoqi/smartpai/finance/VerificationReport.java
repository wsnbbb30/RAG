package com.yizhaoqi.smartpai.finance;

import java.util.List;

/** 数字与引用核验报告，供 API、评测和 Bad Case 工作流统一消费。 */
public record VerificationReport(List<VerificationIssue> issues) {
    public VerificationReport { issues = List.copyOf(issues); }
    public boolean hasBlockingIssue() { return issues.stream().anyMatch(issue -> issue.severity() == VerificationIssue.Severity.HIGH); }
}

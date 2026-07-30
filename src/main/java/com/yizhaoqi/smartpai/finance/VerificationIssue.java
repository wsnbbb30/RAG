package com.yizhaoqi.smartpai.finance;

/** HIGH 问题会阻断 VERIFIED/SUPPORTED 输出并将回答降级。 */
public record VerificationIssue(Severity severity, String code, String message, String claim) {
    public enum Severity { HIGH, MEDIUM, LOW }
}

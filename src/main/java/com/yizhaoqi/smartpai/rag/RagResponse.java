package com.yizhaoqi.smartpai.rag;

import java.util.List;
import com.yizhaoqi.smartpai.finance.VerificationReport;

/** S2-04 对外结构化回答协议：答案、证据状态、引用和检索追踪必须同时返回。 */
public record RagResponse(String answer, AnswerStatus status, List<Evidence> evidence,
                          CitationVerification citationVerification, String traceId, boolean degraded,
                          VerificationReport financialVerification) {
    public RagResponse { evidence = List.copyOf(evidence); }
    /** 保持 S2-04 既有构造调用兼容。 */
    public RagResponse(String answer, AnswerStatus status, List<Evidence> evidence,
                       CitationVerification citationVerification, String traceId, boolean degraded) {
        this(answer, status, evidence, citationVerification, traceId, degraded, new VerificationReport(List.of()));
    }
}

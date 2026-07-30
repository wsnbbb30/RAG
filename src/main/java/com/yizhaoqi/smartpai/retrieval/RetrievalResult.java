package com.yizhaoqi.smartpai.retrieval;

import java.util.List;

/** 单路召回结果，连同耗时、可用性和降级原因一起返回，供接口和后续评测记录。 */
public record RetrievalResult(String source, List<RetrievalCandidate> candidates,
                              long latencyMs, boolean degraded, String diagnostic) {
    public RetrievalResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        diagnostic = diagnostic == null ? "" : diagnostic;
    }

    public static RetrievalResult disabled(String source, String reason) {
        return new RetrievalResult(source, List.of(), 0L, false, reason);
    }

    public static RetrievalResult degraded(String source, long latencyMs, String reason) {
        return new RetrievalResult(source, List.of(), latencyMs, true, reason);
    }
}

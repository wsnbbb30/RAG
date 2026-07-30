package com.yizhaoqi.smartpai.rerank;

import java.util.List;

/** 精排结果及运行元数据；API 失败时 candidates 必须仍包含原始 RRF 候选。 */
public record RerankResult(List<RerankedCandidate> candidates, String model, long latencyMs,
                           boolean applied, boolean degraded, String diagnostic) {
    public RerankResult {
        candidates = List.copyOf(candidates);
        diagnostic = diagnostic == null ? "" : diagnostic;
    }
}

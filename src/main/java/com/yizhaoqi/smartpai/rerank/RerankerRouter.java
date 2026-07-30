package com.yizhaoqi.smartpai.rerank;

import com.yizhaoqi.smartpai.config.RerankProperties;
import com.yizhaoqi.smartpai.retrieval.FusedCandidate;
import org.springframework.stereotype.Component;

import java.util.List;

/** 根据 feature flag 选择 API 或 Noop 精排，避免业务编排层绑定某一家模型供应商。 */
@Component
public class RerankerRouter implements Reranker {
    private final RerankProperties properties;
    private final NoopReranker noopReranker;
    private final ApiReranker apiReranker;

    public RerankerRouter(RerankProperties properties, NoopReranker noopReranker, ApiReranker apiReranker) {
        this.properties = properties; this.noopReranker = noopReranker; this.apiReranker = apiReranker;
    }

    @Override
    public RerankResult rerank(String query, List<FusedCandidate> candidates, int topN) {
        return properties.isEnabled() ? apiReranker.rerank(query, candidates, topN)
                : noopReranker.rerank(query, candidates, topN);
    }
}

package com.yizhaoqi.smartpai.rerank;

import com.yizhaoqi.smartpai.retrieval.FusedCandidate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

/** 默认精排实现：保持 RRF 顺序，作为关闭开关、熔断和外部错误的安全回退。 */
@Component
public class NoopReranker implements Reranker {
    @Override
    public RerankResult rerank(String query, List<FusedCandidate> candidates, int topN) {
        return new RerankResult(IntStream.range(0, Math.min(topN, candidates.size()))
                .mapToObj(index -> new RerankedCandidate(candidates.get(index), index + 1, null)).toList(),
                "noop", 0L, false, false, "未启用 API 精排，保留 RRF 顺序");
    }
}

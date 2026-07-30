package com.yizhaoqi.smartpai.rerank;

import com.yizhaoqi.smartpai.retrieval.FusedCandidate;

import java.util.List;

/** 精排端口。输入只能来自已完成 ACL 过滤的 RRF 候选，禁止重新检索或扩展候选集合。 */
public interface Reranker {
    RerankResult rerank(String query, List<FusedCandidate> candidates, int topN);
}

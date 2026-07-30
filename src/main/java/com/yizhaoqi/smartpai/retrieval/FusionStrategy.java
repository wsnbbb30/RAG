package com.yizhaoqi.smartpai.retrieval;

import java.util.List;

/** 多路召回融合端口；S2-02 默认 RRF，未来可替换为学习排序且不改 Retriever。 */
public interface FusionStrategy {
    List<FusedCandidate> fuse(List<RetrievalResult> routes, int topK);
}

package com.yizhaoqi.smartpai.rerank;

import com.yizhaoqi.smartpai.retrieval.FusedCandidate;

/** 精排后的候选；rerankScore 为 null 代表使用 RRF 降级顺序。 */
public record RerankedCandidate(FusedCandidate candidate, int rerankRank, Double rerankScore) { }

package com.yizhaoqi.smartpai.rerank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.config.RerankProperties;
import com.yizhaoqi.smartpai.model.DocumentChunk;
import com.yizhaoqi.smartpai.retrieval.FusedCandidate;
import com.yizhaoqi.smartpai.retrieval.RetrievalCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 精排不可用时必须完整保留 RRF 排序，不能返回空结果或扩大候选集合。 */
class RerankerFallbackTest {
    @Test
    void noopKeepsOriginalRrfOrder() {
        List<FusedCandidate> input = List.of(candidate(1L), candidate(2L));
        RerankResult result = new NoopReranker().rerank("营业收入", input, 2);

        assertFalse(result.applied());
        assertFalse(result.degraded());
        assertEquals(1L, result.candidates().get(0).candidate().candidate().chunkId());
        assertEquals(2L, result.candidates().get(1).candidate().candidate().chunkId());
    }

    @Test
    void apiWithoutCompleteConfigurationFallsBackSafely() {
        RerankProperties properties = new RerankProperties();
        properties.setEnabled(true);
        // url/key 缺失模拟部署误配；不能尝试网络调用，也不能丢弃 RRF 证据。
        RerankResult result = new ApiReranker(properties, new ObjectMapper())
                .rerank("营业收入", List.of(candidate(1L)), 1);

        assertTrue(result.degraded());
        assertFalse(result.applied());
        assertEquals(1L, result.candidates().get(0).candidate().candidate().chunkId());
    }

    private FusedCandidate candidate(Long chunkId) {
        RetrievalCandidate candidate = new RetrievalCandidate("bm25", 1, 1D, "v1-c" + chunkId, 1L,
                "600519-2023-ANNUAL_REPORT-CN", chunkId, DocumentChunk.ChunkType.TEXT, null,
                "营业收入", "hash" + chunkId, 1, 1, "1", "default", false,
                "600519", 2023, "ANNUAL_REPORT");
        return new FusedCandidate(candidate, 0.1D, List.of());
    }
}

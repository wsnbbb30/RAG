package com.yizhaoqi.smartpai.retrieval;

import com.yizhaoqi.smartpai.config.RetrievalProperties;
import com.yizhaoqi.smartpai.model.DocumentChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 RRF 不比较异构 rawScore，且同一版本分块跨路命中后只保留一个证据位置。 */
class RrfFusionStrategyTest {
    @Test
    void fusesDuplicateEvidenceAndRetainsRouteContributions() {
        RrfFusionStrategy strategy = new RrfFusionStrategy(new RetrievalProperties(), new CandidateDeduplicator());
        RetrievalCandidate sameBm25 = candidate("bm25", 1, 11L, 99D);
        RetrievalCandidate sameVector = candidate("vector", 2, 11L, 0.72D);
        RetrievalCandidate other = candidate("bm25", 2, 12L, 1000D);

        List<FusedCandidate> result = strategy.fuse(List.of(
                new RetrievalResult("bm25", List.of(sameBm25, other), 4, false, ""),
                new RetrievalResult("vector", List.of(sameVector), 6, false, "")), 10);

        assertEquals(2, result.size());
        assertEquals(11L, result.get(0).candidate().chunkId());
        assertEquals(2, result.get(0).contributions().size());
        assertTrue(result.get(0).fusedScore() > result.get(1).fusedScore());
    }

    private RetrievalCandidate candidate(String source, int rank, Long chunkId, double rawScore) {
        return new RetrievalCandidate(source, rank, rawScore, "v1-c" + chunkId, 1L, "600519-2023-ANNUAL_REPORT-CN",
                chunkId, DocumentChunk.ChunkType.TEXT, null, "营业收入", "same-hash-" + chunkId, 1, 1,
                "42", "finance", false, "600519", 2023, "ANNUAL_REPORT");
    }
}

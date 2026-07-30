package com.yizhaoqi.smartpai.retrieval;

import com.yizhaoqi.smartpai.model.DocumentChunk;

/**
 * 一条召回候选的稳定证据描述。
 * rawScore 只可在同一路内比较；S2-02 会使用 rank 做 RRF，禁止直接跨路比较分值。
 */
public record RetrievalCandidate(
        String source,
        int rank,
        double rawScore,
        String indexId,
        Long versionId,
        String documentId,
        Long chunkId,
        DocumentChunk.ChunkType chunkType,
        Long parentChunkId,
        String content,
        String contentHash,
        int pageStart,
        int pageEnd,
        String ownerUserId,
        String orgTag,
        boolean isPublic,
        String stockCode,
        Integer fiscalYear,
        String reportType) {
}

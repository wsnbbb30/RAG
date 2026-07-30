package com.yizhaoqi.smartpai.index;

import com.yizhaoqi.smartpai.model.DocumentChunk;

import java.util.List;

/**
 * 写入 Elasticsearch 的稳定文档契约。
 * 不复用旧 EsDocument，防止历史 fileMd5/chunkId schema 与版本化 Chunk schema 混淆。
 */
public record IndexDocument(
        String id,
        Long versionId,
        String documentId,
        Long chunkId,
        int chunkNo,
        DocumentChunk.ChunkType chunkType,
        Long parentChunkId,
        String content,
        String contentHash,
        int tokenCount,
        int pageStart,
        int pageEnd,
        List<Long> elementIds,
        String parserVersion,
        String chunkerVersion,
        EmbeddingMetadata embedding,
        float[] vector,
        String ownerUserId,
        String orgTag,
        boolean isPublic,
        String stockCode,
        Integer fiscalYear,
        String reportType) {

    /** 稳定 ID 使重试/upsert 不会制造重复 ES 文档。 */
    public static String stableId(Long versionId, Long chunkId) {
        return "v" + versionId + "-c" + chunkId;
    }
}

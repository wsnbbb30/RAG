package com.yizhaoqi.smartpai.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.model.DocumentChunk;
import com.yizhaoqi.smartpai.model.DocumentVersion;
import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.model.FinancialReportMetadata;
import org.springframework.stereotype.Component;

import java.util.List;

/** 将 MySQL 事实模型映射为 ES 索引模型；所有字段在此集中定义，避免业务层散写 ES DTO。 */
@Component
public class IndexDocumentMapper {
    private final ObjectMapper objectMapper;

    public IndexDocumentMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public IndexDocument map(DocumentChunk chunk, DocumentVersion version, FileUpload upload,
                             FinancialReportMetadata reportMetadata, EmbeddingMetadata embedding, float[] vector) {
        if (chunk.getId() == null || chunk.getVersionId() == null || !chunk.getVersionId().equals(version.getId())) {
            throw new IllegalArgumentException("Chunk 与 DocumentVersion 不匹配");
        }
        if (vector == null || vector.length != embedding.dimension()) {
            throw new IllegalArgumentException("Embedding 向量维度与元数据不一致");
        }
        return new IndexDocument(IndexDocument.stableId(version.getId(), chunk.getId()), version.getId(),
                version.getDocumentId(), chunk.getId(), chunk.getChunkNo(), chunk.getChunkType(),
                chunk.getParentChunkId(), chunk.getContent(), chunk.getContentHash(), chunk.getTokenCount(),
                chunk.getPageStart(), chunk.getPageEnd(), parseElementIds(chunk.getElementIdsJson()),
                version.getParserVersion(), chunk.getChunkerVersion(), embedding, vector,
                upload.getUserId(), upload.getOrgTag(), upload.isPublic(),
                reportMetadata == null ? null : reportMetadata.getStockCode(),
                reportMetadata == null ? null : reportMetadata.getFiscalYear(),
                reportMetadata == null || reportMetadata.getReportType() == null ? null : reportMetadata.getReportType().name());
    }

    private List<Long> parseElementIds(String elementIdsJson) {
        try {
            return objectMapper.readValue(elementIdsJson, new TypeReference<List<Long>>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取 Chunk 来源元素 ID", exception);
        }
    }
}

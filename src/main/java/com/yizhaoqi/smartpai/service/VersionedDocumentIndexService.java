package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.chunk.TokenCounter;
import com.yizhaoqi.smartpai.config.VersionedIndexProperties;
import com.yizhaoqi.smartpai.index.EmbeddingMetadata;
import com.yizhaoqi.smartpai.index.IndexDocument;
import com.yizhaoqi.smartpai.index.IndexDocumentMapper;
import com.yizhaoqi.smartpai.index.IndexWriter;
import com.yizhaoqi.smartpai.client.EmbeddingClient;
import com.yizhaoqi.smartpai.model.DocumentChunk;
import com.yizhaoqi.smartpai.model.DocumentVersion;
import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.model.FinancialReportMetadata;
import com.yizhaoqi.smartpai.repository.DocumentChunkRepository;
import com.yizhaoqi.smartpai.repository.DocumentVersionRepository;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.repository.FinancialReportMetadataRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 将 S1-03 的结构化 Chunk 嵌入并写入版本化索引。
 * 该服务只索引 TEXT/TABLE 子块；PARENT 仅用于检索命中后的上下文回填，避免大父块稀释向量语义。
 */
@Service
public class VersionedDocumentIndexService {
    private final DocumentVersionRepository versionRepository;
    private final DocumentChunkRepository chunkRepository;
    private final FileUploadRepository fileUploadRepository;
    private final FinancialReportMetadataRepository metadataRepository;
    private final EmbeddingClient embeddingClient;
    private final TokenCounter tokenCounter;
    private final IndexDocumentMapper mapper;
    private final IndexWriter indexWriter;
    private final VersionedIndexProperties indexProperties;
    private final String embeddingModel;

    public VersionedDocumentIndexService(DocumentVersionRepository versionRepository,
                                         DocumentChunkRepository chunkRepository,
                                         FileUploadRepository fileUploadRepository,
                                         FinancialReportMetadataRepository metadataRepository,
                                         EmbeddingClient embeddingClient,
                                         TokenCounter tokenCounter,
                                         IndexDocumentMapper mapper,
                                         IndexWriter indexWriter,
                                         VersionedIndexProperties indexProperties,
                                         @Value("${embedding.api.model}") String embeddingModel) {
        this.versionRepository = versionRepository;
        this.chunkRepository = chunkRepository;
        this.fileUploadRepository = fileUploadRepository;
        this.metadataRepository = metadataRepository;
        this.embeddingClient = embeddingClient;
        this.tokenCounter = tokenCounter;
        this.mapper = mapper;
        this.indexWriter = indexWriter;
        this.indexProperties = indexProperties;
        this.embeddingModel = embeddingModel;
    }

    /** 可重复调用：稳定 ES ID 将同版本同 Chunk 的重试变为 upsert。 */
    @Transactional
    public void index(Long versionId) {
        DocumentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("文档版本不存在: " + versionId));
        if (version.getStatus() != DocumentVersion.ProcessingStatus.CHUNKED
                && version.getStatus() != DocumentVersion.ProcessingStatus.INDEXED) {
            throw new IllegalStateException("仅 CHUNKED/INDEXED 版本可索引，当前状态: " + version.getStatus());
        }
        FileUpload upload = fileUploadRepository.findFirstByVersionIdOrderByIdDesc(versionId)
                .orElseThrow(() -> new IllegalStateException("缺少版本 ACL 快照，拒绝索引 versionId=" + versionId));
        List<DocumentChunk> chunks = chunkRepository.findByVersionIdOrderByChunkNoAsc(versionId).stream()
                .filter(chunk -> chunk.getChunkType() == DocumentChunk.ChunkType.TEXT
                        || chunk.getChunkType() == DocumentChunk.ChunkType.TABLE)
                .toList();
        if (chunks.isEmpty()) {
            throw new IllegalStateException("版本没有可索引的子块: " + versionId);
        }

        version.setStatus(DocumentVersion.ProcessingStatus.EMBEDDING);
        versionRepository.save(version);
        List<float[]> vectors = embeddingClient.embed(chunks.stream().map(DocumentChunk::getContent).toList());
        validateVectors(chunks, vectors);

        FinancialReportMetadata metadata = metadataRepository.findByVersionId(versionId).orElse(null);
        EmbeddingMetadata embedding = new EmbeddingMetadata(embeddingModel, indexProperties.getVectorDimension(),
                tokenCounter.tokenizerId());
        List<IndexDocument> documents = java.util.stream.IntStream.range(0, chunks.size())
                .mapToObj(index -> mapper.map(chunks.get(index), version, upload, metadata, embedding, vectors.get(index)))
                .toList();
        indexWriter.upsert(documents);

        version.setEmbeddingModel(embeddingModel);
        version.setStatus(DocumentVersion.ProcessingStatus.INDEXED);
        version.setErrorMessage(null);
        versionRepository.save(version);
    }

    private void validateVectors(List<DocumentChunk> chunks, List<float[]> vectors) {
        if (vectors == null || vectors.size() != chunks.size()) {
            throw new IllegalStateException("Embedding 响应数量错误：请求=" + chunks.size()
                    + "，响应=" + (vectors == null ? 0 : vectors.size()));
        }
        for (int index = 0; index < vectors.size(); index++) {
            if (vectors.get(index) == null || vectors.get(index).length != indexProperties.getVectorDimension()) {
                throw new IllegalStateException("Embedding 维度错误：chunkId=" + chunks.get(index).getId()
                        + "，期望=" + indexProperties.getVectorDimension()
                        + "，实际=" + (vectors.get(index) == null ? 0 : vectors.get(index).length));
            }
        }
    }
}

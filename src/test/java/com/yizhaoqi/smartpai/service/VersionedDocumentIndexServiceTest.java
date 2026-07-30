package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.chunk.CharacterTokenCounter;
import com.yizhaoqi.smartpai.client.EmbeddingClient;
import com.yizhaoqi.smartpai.config.VersionedIndexProperties;
import com.yizhaoqi.smartpai.index.IndexDocument;
import com.yizhaoqi.smartpai.index.IndexDocumentMapper;
import com.yizhaoqi.smartpai.index.IndexWriter;
import com.yizhaoqi.smartpai.model.*;
import com.yizhaoqi.smartpai.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** 验证 S1-04 的索引状态机、稳定 ID 和向量响应 fail-fast 契约。 */
class VersionedDocumentIndexServiceTest {
    private DocumentVersionRepository versionRepository;
    private DocumentChunkRepository chunkRepository;
    private FileUploadRepository fileUploadRepository;
    private FinancialReportMetadataRepository metadataRepository;
    private EmbeddingClient embeddingClient;
    private IndexWriter indexWriter;
    private VersionedDocumentIndexService service;

    @BeforeEach
    void setUp() {
        versionRepository = mock(DocumentVersionRepository.class);
        chunkRepository = mock(DocumentChunkRepository.class);
        fileUploadRepository = mock(FileUploadRepository.class);
        metadataRepository = mock(FinancialReportMetadataRepository.class);
        embeddingClient = mock(EmbeddingClient.class);
        indexWriter = mock(IndexWriter.class);
        VersionedIndexProperties properties = new VersionedIndexProperties();
        properties.setVectorDimension(4);
        service = new VersionedDocumentIndexService(versionRepository, chunkRepository, fileUploadRepository,
                metadataRepository, embeddingClient, new CharacterTokenCounter(),
                new IndexDocumentMapper(new ObjectMapper()), indexWriter, properties, "embedding-test-v1");
    }

    @Test
    void indexesOnlyChildrenWithStableIdAndMarksVersionIndexed() {
        DocumentVersion version = version(DocumentVersion.ProcessingStatus.CHUNKED);
        DocumentChunk parent = chunk(100L, DocumentChunk.ChunkType.PARENT, "标题", "[]");
        DocumentChunk text = chunk(101L, DocumentChunk.ChunkType.TEXT, "正文内容", "[11]");
        FileUpload upload = upload();
        FinancialReportMetadata metadata = new FinancialReportMetadata();
        metadata.setStockCode("600519"); metadata.setFiscalYear(2023); metadata.setReportType(Document.ReportType.ANNUAL_REPORT);
        when(versionRepository.findById(7L)).thenReturn(Optional.of(version));
        when(fileUploadRepository.findFirstByVersionIdOrderByIdDesc(7L)).thenReturn(Optional.of(upload));
        when(chunkRepository.findByVersionIdOrderByChunkNoAsc(7L)).thenReturn(List.of(parent, text));
        when(metadataRepository.findByVersionId(7L)).thenReturn(Optional.of(metadata));
        when(embeddingClient.embed(List.of("正文内容"))).thenReturn(List.of(new float[]{1, 2, 3, 4}));

        service.index(7L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IndexDocument>> documents = ArgumentCaptor.forClass(List.class);
        verify(indexWriter).upsert(documents.capture());
        assertEquals(1, documents.getValue().size(), "父块不得直接向量化");
        IndexDocument document = documents.getValue().get(0);
        assertEquals("v7-c101", document.id());
        assertEquals(7L, document.versionId());
        assertEquals(101L, document.chunkId());
        assertEquals("600519", document.stockCode());
        assertEquals(DocumentVersion.ProcessingStatus.INDEXED, version.getStatus());
        assertEquals("embedding-test-v1", version.getEmbeddingModel());
    }

    @Test
    void failsBeforeWritingWhenEmbeddingResponseCountIsWrong() {
        DocumentVersion version = version(DocumentVersion.ProcessingStatus.CHUNKED);
        DocumentChunk text = chunk(101L, DocumentChunk.ChunkType.TEXT, "正文内容", "[11]");
        when(versionRepository.findById(7L)).thenReturn(Optional.of(version));
        when(fileUploadRepository.findFirstByVersionIdOrderByIdDesc(7L)).thenReturn(Optional.of(upload()));
        when(chunkRepository.findByVersionIdOrderByChunkNoAsc(7L)).thenReturn(List.of(text));
        when(embeddingClient.embed(List.of("正文内容"))).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> service.index(7L));
        verify(indexWriter, never()).upsert(anyList());
    }

    private DocumentVersion version(DocumentVersion.ProcessingStatus status) {
        DocumentVersion version = new DocumentVersion();
        version.setId(7L); version.setDocumentId("600519-2023-ANNUAL_REPORT-CN"); version.setStatus(status);
        version.setParserVersion("pdf-layout-v2"); version.setChunkerVersion("structure-aware-v1");
        return version;
    }

    private DocumentChunk chunk(Long id, DocumentChunk.ChunkType type, String content, String elementIdsJson) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(id); chunk.setVersionId(7L); chunk.setChunkNo(id.intValue()); chunk.setChunkType(type);
        chunk.setContent(content); chunk.setContentHash("a".repeat(64)); chunk.setTokenCount(4);
        chunk.setPageStart(1); chunk.setPageEnd(1); chunk.setElementIdsJson(elementIdsJson);
        chunk.setChunkerVersion("structure-aware-v1");
        return chunk;
    }

    private FileUpload upload() {
        FileUpload upload = new FileUpload();
        upload.setVersionId(7L); upload.setUserId("user-1"); upload.setOrgTag("finance"); upload.setPublic(false);
        return upload;
    }
}

package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.chunk.*;
import com.yizhaoqi.smartpai.config.ChunkingProperties;
import com.yizhaoqi.smartpai.model.*;
import com.yizhaoqi.smartpai.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 验证应用服务的状态机、重建清理顺序和草稿 ID 到真实 ID 的映射。 */
class VersionedDocumentChunkServiceTest {
    private DocumentVersionRepository versionRepository;
    private DocumentPageRepository pageRepository;
    private DocumentElementRepository elementRepository;
    private DocumentChunkRepository chunkRepository;
    private ChunkRelationRepository relationRepository;
    private VersionedDocumentChunkService service;

    @BeforeEach
    void setUp() {
        versionRepository = mock(DocumentVersionRepository.class);
        pageRepository = mock(DocumentPageRepository.class);
        elementRepository = mock(DocumentElementRepository.class);
        chunkRepository = mock(DocumentChunkRepository.class);
        relationRepository = mock(ChunkRelationRepository.class);
        Chunker deterministicChunker = (elements, policy) -> new Chunker.ChunkingResult(List.of(
                new ChunkDraft(DocumentChunk.ChunkType.PARENT, "章节", 2, 1, 1, List.of(11L), null),
                new ChunkDraft(DocumentChunk.ChunkType.TEXT, "正文", 2, 1, 1, List.of(12L), 0)), List.of(
                new ChunkRelationDraft(1, 0, ChunkRelation.RelationType.PARENT),
                new ChunkRelationDraft(0, 1, ChunkRelation.RelationType.CHILD)));
        ChunkingProperties properties = new ChunkingProperties();
        properties.setChunkerVersion("test-v1");
        service = new VersionedDocumentChunkService(versionRepository, pageRepository, elementRepository,
                chunkRepository, relationRepository, deterministicChunker, properties, new ObjectMapper());
    }

    @Test
    void rebuildsOldChunksAndPersistsRealParentChildIds() {
        DocumentVersion version = version(DocumentVersion.ProcessingStatus.PARSED);
        DocumentPage page = new DocumentPage(); page.setId(1L); page.setPageNo(1);
        DocumentElement element = new DocumentElement(); element.setId(12L); element.setOrderNo(1);
        element.setElementType(DocumentElement.ElementType.PARAGRAPH); element.setTextContent("正文");
        DocumentChunk oldChunk = new DocumentChunk(); oldChunk.setId(99L);
        AtomicLong ids = new AtomicLong(100L);

        when(versionRepository.findById(7L)).thenReturn(Optional.of(version));
        when(pageRepository.findByVersionIdOrderByPageNoAsc(7L)).thenReturn(List.of(page));
        when(elementRepository.findByPageIdOrderByOrderNoAsc(1L)).thenReturn(List.of(element));
        when(chunkRepository.findByVersionIdOrderByChunkNoAsc(7L)).thenReturn(List.of(oldChunk));
        when(chunkRepository.save(any(DocumentChunk.class))).thenAnswer(invocation -> {
            DocumentChunk saved = invocation.getArgument(0);
            saved.setId(ids.getAndIncrement());
            return saved;
        });

        service.chunk(7L);

        verify(relationRepository).deleteBySourceChunkIdInOrTargetChunkIdIn(List.of(99L), List.of(99L));
        verify(chunkRepository).deleteByVersionId(7L);
        verify(chunkRepository, times(2)).save(any(DocumentChunk.class));
        verify(relationRepository, times(2)).save(any(ChunkRelation.class));
        assertEquals(DocumentVersion.ProcessingStatus.CHUNKED, version.getStatus());
        assertEquals("test-v1", version.getChunkerVersion());
    }

    @Test
    void rejectsVersionThatHasNotFinishedParsing() {
        DocumentVersion version = version(DocumentVersion.ProcessingStatus.UPLOADED);
        when(versionRepository.findById(7L)).thenReturn(Optional.of(version));

        assertThrows(IllegalStateException.class, () -> service.chunk(7L));
        verifyNoInteractions(pageRepository, elementRepository, chunkRepository, relationRepository);
    }

    @Test
    void skipsChunkingWhenKafkaRetriesAfterChunksWerePersisted() {
        DocumentVersion version = version(DocumentVersion.ProcessingStatus.CHUNKED);
        when(versionRepository.findById(7L)).thenReturn(Optional.of(version));

        service.chunk(7L);

        // 已落库的 Chunk 是版本快照；索引重试不能重写它。
        verifyNoInteractions(pageRepository, elementRepository, chunkRepository, relationRepository);
        verify(versionRepository, never()).save(any());
    }

    private DocumentVersion version(DocumentVersion.ProcessingStatus status) {
        DocumentVersion version = new DocumentVersion();
        version.setId(7L);
        version.setStatus(status);
        return version;
    }
}

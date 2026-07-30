package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.DocumentVersion;
import com.yizhaoqi.smartpai.parser.*;
import com.yizhaoqi.smartpai.repository.DocumentElementRepository;
import com.yizhaoqi.smartpai.repository.DocumentPageRepository;
import com.yizhaoqi.smartpai.repository.DocumentVersionRepository;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证解析编排的状态更新、持久化与重试清理，不依赖真实 PDF、MinIO 或数据库。 */
class VersionedDocumentParseServiceTest {
    @Test
    void shouldSkipParsingWhenKafkaRetriesAfterChunking() throws Exception {
        DocumentVersionRepository versionRepository = mock(DocumentVersionRepository.class);
        DocumentPageRepository pageRepository = mock(DocumentPageRepository.class);
        DocumentElementRepository elementRepository = mock(DocumentElementRepository.class);
        ParserRegistry registry = mock(ParserRegistry.class);
        ParseArtifactStorage storage = mock(ParseArtifactStorage.class);
        DocumentVersion version = new DocumentVersion();
        version.setId(1L);
        version.setStatus(DocumentVersion.ProcessingStatus.CHUNKED);
        when(versionRepository.findById(1L)).thenReturn(Optional.of(version));

        new VersionedDocumentParseService(versionRepository, pageRepository, elementRepository, registry, storage)
                .parse(1L, "report.pdf", "application/pdf", new ByteArrayInputStream(new byte[0]));

        // 后续索引阶段失败并重投 Kafka 时，解析快照必须保持不变，避免重复插入页面。
        verifyNoInteractions(pageRepository, elementRepository, registry, storage);
        verify(versionRepository, never()).save(any());
    }

    @Test
    void shouldPersistPagesElementsAndManifest() throws Exception {
        DocumentVersionRepository versionRepository = mock(DocumentVersionRepository.class);
        DocumentPageRepository pageRepository = mock(DocumentPageRepository.class);
        DocumentElementRepository elementRepository = mock(DocumentElementRepository.class);
        ParserRegistry registry = mock(ParserRegistry.class);
        ParseArtifactStorage storage = mock(ParseArtifactStorage.class);
        DocumentVersion version = new DocumentVersion();
        version.setId(1L); version.setStatus(DocumentVersion.ProcessingStatus.UPLOADED);
        when(versionRepository.findById(1L)).thenReturn(Optional.of(version));
        when(pageRepository.findByVersionIdOrderByPageNoAsc(1L)).thenReturn(List.of());
        when(pageRepository.save(any())).thenAnswer(invocation -> {
            var page = invocation.getArgument(0, com.yizhaoqi.smartpai.model.DocumentPage.class);
            page.setId(10L); return page;
        });
        DocumentParser parser = mock(DocumentParser.class);
        when(registry.requireParser("report.pdf", "application/pdf")).thenReturn(parser);
        when(parser.parse(any())).thenReturn(new ParseResult(ParserType.PDF_LAYOUT, "pdf-layout-v1", List.of(
                new ParsedPage(1, BigDecimal.valueOf(595), BigDecimal.valueOf(842), 0, 10, false, List.of(
                        new ParsedElement(ElementType.PARAGRAPH, "测试文本", 1, BoundingBox.empty(), null, 1.0))))));

        new VersionedDocumentParseService(versionRepository, pageRepository, elementRepository, registry, storage)
                .parse(1L, "report.pdf", "application/pdf", new ByteArrayInputStream(new byte[0]));

        verify(elementRepository).saveAll(anyList());
        verify(storage).saveManifest(eq(1L), any(ParseResult.class));
        verify(versionRepository, atLeast(2)).save(version);
        org.junit.jupiter.api.Assertions.assertEquals(DocumentVersion.ProcessingStatus.PARSED, version.getStatus());
    }
}

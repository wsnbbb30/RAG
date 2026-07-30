package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.chunk.*;
import com.yizhaoqi.smartpai.config.ChunkingProperties;
import com.yizhaoqi.smartpai.model.ChunkRelation;
import com.yizhaoqi.smartpai.model.DocumentChunk;
import com.yizhaoqi.smartpai.model.DocumentElement;
import com.yizhaoqi.smartpai.model.DocumentPage;
import com.yizhaoqi.smartpai.model.DocumentVersion;
import com.yizhaoqi.smartpai.repository.ChunkRelationRepository;
import com.yizhaoqi.smartpai.repository.DocumentChunkRepository;
import com.yizhaoqi.smartpai.repository.DocumentElementRepository;
import com.yizhaoqi.smartpai.repository.DocumentPageRepository;
import com.yizhaoqi.smartpai.repository.DocumentVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档版本的结构化切块应用服务。
 *
 * <p>该类仅负责编排：读取页级元素、状态流转、清理旧产物、草稿持久化以及关系 ID 映射；
 * 不包含任何标题识别或 token 边界算法，保证切块策略可独立替换和测试。</p>
 */
@Service
public class VersionedDocumentChunkService {
    private final DocumentVersionRepository versionRepository;
    private final DocumentPageRepository pageRepository;
    private final DocumentElementRepository elementRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ChunkRelationRepository relationRepository;
    private final Chunker chunker;
    private final ChunkingProperties properties;
    private final ObjectMapper objectMapper;

    public VersionedDocumentChunkService(DocumentVersionRepository versionRepository,
                                         DocumentPageRepository pageRepository,
                                         DocumentElementRepository elementRepository,
                                         DocumentChunkRepository chunkRepository,
                                         ChunkRelationRepository relationRepository,
                                         Chunker chunker,
                                         ChunkingProperties properties,
                                         ObjectMapper objectMapper) {
        this.versionRepository = versionRepository;
        this.pageRepository = pageRepository;
        this.elementRepository = elementRepository;
        this.chunkRepository = chunkRepository;
        this.relationRepository = relationRepository;
        this.chunker = chunker;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 对一个已完成页级解析的版本生成 Chunk。
     * 重复调用会删除该版本旧关系和旧 Chunk 后完整重建，因而不会产生重复数据。
     */
    @Transactional
    public void chunk(Long versionId) {
        if (!properties.isEnabled()) {
            return;
        }
        DocumentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("文档版本不存在: " + versionId));
        // Kafka 可能因索引阶段失败重投整条任务。CHUNKED 表示页面快照和分块快照都已持久化，
        // 此时应将控制权交给后续索引阶段，而非删除后重建；否则同一事务内的删除/插入可能
        // 与数据库唯一键 (version_id, chunk_no) 发生冲突，导致重试无法收敛。
        if (version.getStatus() == DocumentVersion.ProcessingStatus.CHUNKED
                || version.getStatus() == DocumentVersion.ProcessingStatus.INDEXED) {
            return;
        }
        validateStatus(version);
        version.setStatus(DocumentVersion.ProcessingStatus.CHUNKING);
        versionRepository.save(version);

        deletePreviousChunks(versionId);
        List<ChunkSourceElement> elements = loadElementsInReadingOrder(versionId);
        ChunkingPolicy policy = new ChunkingPolicy(properties.getChunkerVersion(), properties.getMinTokens(),
                properties.getMaxTokens(), properties.getOverlapTokens(), properties.getOversizedElementTokens());
        Chunker.ChunkingResult result = chunker.chunk(elements, policy);
        Map<Integer, DocumentChunk> persistedChunks = persistDrafts(versionId, result.chunks(), policy.version());
        persistRelations(result.relations(), persistedChunks);

        version.setChunkerVersion(policy.version());
        version.setStatus(DocumentVersion.ProcessingStatus.CHUNKED);
        version.setErrorMessage(null);
        versionRepository.save(version);
    }

    private void validateStatus(DocumentVersion version) {
        if (version.getStatus() != DocumentVersion.ProcessingStatus.PARSED) {
            throw new IllegalStateException("仅 PARSED 版本可切块，当前状态: " + version.getStatus());
        }
    }

    /** 先删除关系再删除 Chunk，满足关系表外键约束。 */
    private void deletePreviousChunks(Long versionId) {
        List<Long> oldChunkIds = chunkRepository.findByVersionIdOrderByChunkNoAsc(versionId).stream()
                .map(DocumentChunk::getId)
                .toList();
        if (!oldChunkIds.isEmpty()) {
            relationRepository.deleteBySourceChunkIdInOrTargetChunkIdIn(oldChunkIds, oldChunkIds);
        }
        chunkRepository.deleteByVersionId(versionId);
    }

    /**
     * 通过页表构造 pageId -> pageNo 映射，再批量按页读取元素。
     * 算法层接收 ChunkSourceElement，避免把 JPA 实体泄漏到切块器。
     */
    private List<ChunkSourceElement> loadElementsInReadingOrder(Long versionId) {
        List<ChunkSourceElement> result = new ArrayList<>();
        for (DocumentPage page : pageRepository.findByVersionIdOrderByPageNoAsc(versionId)) {
            for (DocumentElement element : elementRepository.findByPageIdOrderByOrderNoAsc(page.getId())) {
                result.add(new ChunkSourceElement(element.getId(), page.getPageNo(), element.getOrderNo(),
                        element.getElementType(), element.getTextContent()));
            }
        }
        return result;
    }

    /** 草稿按固定顺序保存，后续草稿可安全引用已经生成 ID 的父草稿。 */
    private Map<Integer, DocumentChunk> persistDrafts(Long versionId, List<ChunkDraft> drafts, String chunkerVersion) {
        Map<Integer, DocumentChunk> persisted = new HashMap<>();
        for (int index = 0; index < drafts.size(); index++) {
            ChunkDraft draft = drafts.get(index);
            DocumentChunk entity = new DocumentChunk();
            entity.setVersionId(versionId);
            entity.setChunkNo(index + 1);
            entity.setChunkType(draft.chunkType());
            entity.setContent(draft.content());
            entity.setTokenCount(draft.tokenCount());
            entity.setContentHash(sha256(draft.content()));
            entity.setPageStart(draft.pageStart());
            entity.setPageEnd(draft.pageEnd());
            entity.setElementIdsJson(writeElementIds(draft.sourceElementIds()));
            entity.setChunkerVersion(chunkerVersion);
            if (draft.parentDraftNo() != null) {
                DocumentChunk parent = persisted.get(draft.parentDraftNo());
                if (parent == null || parent.getId() == null) {
                    throw new IllegalStateException("父块必须在子块之前持久化");
                }
                entity.setParentChunkId(parent.getId());
            }
            persisted.put(index, chunkRepository.save(entity));
        }
        return persisted;
    }

    private void persistRelations(List<ChunkRelationDraft> drafts, Map<Integer, DocumentChunk> chunks) {
        for (ChunkRelationDraft draft : drafts) {
            DocumentChunk source = requirePersistedChunk(chunks, draft.sourceDraftNo());
            DocumentChunk target = requirePersistedChunk(chunks, draft.targetDraftNo());
            ChunkRelation relation = new ChunkRelation();
            relation.setSourceChunkId(source.getId());
            relation.setTargetChunkId(target.getId());
            relation.setRelationType(draft.relationType());
            relation.setWeight(BigDecimal.ONE);
            relationRepository.save(relation);
        }
    }

    private DocumentChunk requirePersistedChunk(Map<Integer, DocumentChunk> chunks, int draftNo) {
        DocumentChunk chunk = chunks.get(draftNo);
        if (chunk == null || chunk.getId() == null) {
            throw new IllegalStateException("关系引用了不存在的 Chunk 草稿: " + draftNo);
        }
        return chunk;
    }

    private String writeElementIds(List<Long> elementIds) {
        try {
            return objectMapper.writeValueAsString(elementIds);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化来源元素 ID", exception);
        }
    }

    private String sha256(String content) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder();
            for (byte item : bytes) {
                value.append(String.format("%02x", item));
            }
            return value.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算 Chunk 内容哈希", exception);
        }
    }
}

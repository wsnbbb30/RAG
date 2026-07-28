package com.yizhaoqi.smartpai.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 可检索、可追溯的文档切块。
 * 此实体不关联 ES，确保删除、重建和证据定位都以 MySQL 的版本化事实为准。
 */
@Data
@Entity
@Table(name = "document_chunk")
public class DocumentChunk {
    public enum ChunkType { PARENT, TEXT, TABLE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "chunk_no", nullable = false)
    private Integer chunkNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "chunk_type", nullable = false, length = 32)
    private ChunkType chunkType;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "token_count", nullable = false)
    private Integer tokenCount;

    // 与 V4 migration 中的 CHAR(64) 保持完全一致：SHA-256 的十六进制摘要固定为 64 个字符。
    // 仅设置 length 会被 Hibernate 推断为 VARCHAR(64)，从而在 ddl-auto=validate 时启动失败。
    @Column(name = "content_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String contentHash;

    @Column(name = "page_start", nullable = false)
    private Integer pageStart;

    @Column(name = "page_end", nullable = false)
    private Integer pageEnd;

    /** JSON 数组，按阅读顺序记录来源 DocumentElement ID。 */
    @Lob
    @Column(name = "element_ids_json", nullable = false, columnDefinition = "JSON")
    private String elementIdsJson;

    @Column(name = "parent_chunk_id")
    private Long parentChunkId;

    @Column(name = "chunker_version", nullable = false, length = 64)
    private String chunkerVersion;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

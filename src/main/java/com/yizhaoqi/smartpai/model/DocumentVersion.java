package com.yizhaoqi.smartpai.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 文档版本实体
 * 代表一份PDF文件的具体版本，是后续解析/切块/索引的锚点
 *
 * 与Document的关系：N:1（一个逻辑文档可以有多个修订版本）
 * 与FileUpload的关系：1:1（每个已完成的文件上传对应一个版本）
 *
 * 生命周期状态机：
 * UPLOADED -> PARSING -> PARSED -> CHUNKING -> CHUNKED -> EMBEDDING -> INDEXED
 * 任意阶段出错 -> FAILED
 *
 */
@Data
@Entity
@Table(name = "document_version")
public class DocumentVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的逻辑文档 ID */
    @Column(name = "document_id", length = 64, nullable = false)
    private String documentId;

    /** 版本号，同一 documentId 下从 1 递增 */
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    /** 文件 MD5 指纹，用于去重和完整性校验 */
    @Column(name = "file_md5", length = 32, nullable = false)
    private String fileMd5;

    /** 文件大小（字节），用于性能评估和容量规划 */
    @Column(name = "file_size", nullable = false)
    private Long fileSize = 0L;

    /** PDF 总页数（解析后回填） */
    @Column(name = "page_count")
    private Integer pageCount;

    /** 解析器版本号（阶段 1 解析完成后回填） */
    @Column(name = "parser_version", length = 32)
    private String parserVersion;

    /** 切块器版本号（阶段 1 切块完成后回填） */
    @Column(name = "chunker_version", length = 32)
    private String chunkerVersion;

    /** 向量模型标识（阶段 1 嵌入完成后回填，如 "text-embedding-v4"） */
    @Column(name = "embedding_model", length = 64)
    private String embeddingModel;

    /** 处理状态 */
    @Column(length = 32, nullable = false)
    @Enumerated(EnumType.STRING)
    private ProcessingStatus status = ProcessingStatus.UPLOADED;

    /** 失败原因（仅在 status = FAILED 时有值） */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** 上传用户 ID */
    @Column(name = "created_by", length = 64, nullable = false)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 处理状态枚举 */
    public enum ProcessingStatus {
        /** 已上传，等待处理 */
        UPLOADED,
        /** 解析中 */
        PARSING,
        /** 解析完成 */
        PARSED,
        /** 切块中 */
        CHUNKING,
        /** 切块完成 */
        CHUNKED,
        /** 向量化中 */
        EMBEDDING,
        /** 索引完成（终态） */
        INDEXED,
        /** 处理失败（终态） */
        FAILED
    }
}

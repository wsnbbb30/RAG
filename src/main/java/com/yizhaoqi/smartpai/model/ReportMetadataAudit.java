package com.yizhaoqi.smartpai.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** 年报元数据提取、封面补全和人工复核的不可变审计记录。 */
@Data
@Entity
@Table(name = "report_metadata_audit")
public class ReportMetadataAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "event_type", length = 32, nullable = false)
    @Enumerated(EnumType.STRING)
    private EventType eventType;

    @Column(name = "operator_id", length = 64)
    private String operatorId;

    @Lob
    // 与 V2 migration 的 TEXT 对齐，保存 JSON 格式的元数据快照。
    @Column(name = "before_snapshot", columnDefinition = "TEXT")
    private String beforeSnapshot;

    @Lob
    // 与 V2 migration 的 TEXT 对齐，保存 JSON 格式的元数据快照。
    @Column(name = "after_snapshot", nullable = false, columnDefinition = "TEXT")
    private String afterSnapshot;

    @Lob
    // 与 V2 migration 的 TEXT 对齐，避免 @Lob 在 MySQL 下被推断为 TINYTEXT。
    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum EventType {
        EXTRACTION,
        COVER_ENRICHMENT,
        MANUAL_REVIEW
    }
}

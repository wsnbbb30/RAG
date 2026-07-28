package com.yizhaoqi.smartpai.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 显式记录 Chunk 之间的父子、相邻和表格语义关系，禁止在检索阶段临时猜测。 */
@Data
@Entity
@Table(name = "chunk_relation")
public class ChunkRelation {
    public enum RelationType { PARENT, CHILD, PREV, NEXT, SAME_TABLE, CAPTION_OF }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_chunk_id", nullable = false)
    private Long sourceChunkId;

    @Column(name = "target_chunk_id", nullable = false)
    private Long targetChunkId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 32)
    private RelationType relationType;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal weight = BigDecimal.ONE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

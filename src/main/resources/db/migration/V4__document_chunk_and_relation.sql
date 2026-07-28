-- S1-03：结构感知切块及其显式关系。
-- MySQL 是 Chunk、页码和来源元素的事实源；ES 仅在 S1-04 作为可重建索引。
CREATE TABLE document_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    version_id BIGINT NOT NULL COMMENT '关联 document_version.id',
    chunk_no INT NOT NULL COMMENT '版本内稳定递增序号',
    chunk_type VARCHAR(32) NOT NULL COMMENT 'PARENT/TEXT/TABLE',
    content LONGTEXT NOT NULL COMMENT '可用于 embedding 的文本内容',
    token_count INT NOT NULL COMMENT '使用 tokenizer 计算的近似或精确 token 数',
    content_hash CHAR(64) NOT NULL COMMENT '文本 SHA-256，用于版本内幂等和变更检测',
    page_start INT NOT NULL COMMENT '来源起始页码，从 1 开始',
    page_end INT NOT NULL COMMENT '来源结束页码',
    element_ids_json JSON NOT NULL COMMENT '来源 document_element.id 的有序数组',
    parent_chunk_id BIGINT DEFAULT NULL COMMENT '子块关联的父块；父块为空',
    chunker_version VARCHAR(64) NOT NULL COMMENT '切块策略版本',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_chunk_version_no (version_id, chunk_no),
    -- 相同文本可出现在不同页/不同章节；hash 只用于变更检测，不能作为版本内唯一约束。
    INDEX idx_chunk_version_hash (version_id, content_hash),
    INDEX idx_chunk_version_parent (version_id, parent_chunk_id),
    INDEX idx_chunk_version_page (version_id, page_start, page_end),
    CONSTRAINT fk_chunk_version FOREIGN KEY (version_id)
        REFERENCES document_version(id) ON DELETE CASCADE,
    CONSTRAINT fk_chunk_parent FOREIGN KEY (parent_chunk_id)
        REFERENCES document_chunk(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档结构感知切块';

CREATE TABLE chunk_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    source_chunk_id BIGINT NOT NULL COMMENT '关系起点 document_chunk.id',
    target_chunk_id BIGINT NOT NULL COMMENT '关系终点 document_chunk.id',
    relation_type VARCHAR(32) NOT NULL COMMENT 'PARENT/CHILD/PREV/NEXT/SAME_TABLE/CAPTION_OF',
    weight DECIMAL(6,4) NOT NULL DEFAULT 1.0000 COMMENT '关系强度',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_chunk_relation (source_chunk_id, target_chunk_id, relation_type),
    INDEX idx_relation_target_type (target_chunk_id, relation_type),
    CONSTRAINT fk_relation_source FOREIGN KEY (source_chunk_id)
        REFERENCES document_chunk(id) ON DELETE CASCADE,
    CONSTRAINT fk_relation_target FOREIGN KEY (target_chunk_id)
        REFERENCES document_chunk(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='切块显式关系';

CREATE TABLE document_page (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, version_id BIGINT NOT NULL, page_no INT NOT NULL,
    width DECIMAL(10,2) NOT NULL, height DECIMAL(10,2) NOT NULL, rotation INT NOT NULL DEFAULT 0,
    image_object_key VARCHAR(512) DEFAULT NULL, text_char_count INT NOT NULL DEFAULT 0,
    ocr_status VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED', parser_version VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_document_page (version_id, page_no), INDEX idx_page_version (version_id),
    CONSTRAINT fk_page_version FOREIGN KEY (version_id) REFERENCES document_version(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档页级解析结果';

CREATE TABLE document_element (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, page_id BIGINT NOT NULL, element_type VARCHAR(32) NOT NULL,
    text_content LONGTEXT DEFAULT NULL, order_no INT NOT NULL,
    x0 DECIMAL(10,2) DEFAULT NULL, y0 DECIMAL(10,2) DEFAULT NULL,
    x1 DECIMAL(10,2) DEFAULT NULL, y1 DECIMAL(10,2) DEFAULT NULL,
    heading_level TINYINT DEFAULT NULL, table_ref VARCHAR(64) DEFAULT NULL,
    confidence DECIMAL(5,4) NOT NULL DEFAULT 1.0000, source_text_hash CHAR(64) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_page_order (page_id, order_no), INDEX idx_element_page_type (page_id, element_type),
    CONSTRAINT fk_element_page FOREIGN KEY (page_id) REFERENCES document_page(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档布局元素';

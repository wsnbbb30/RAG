CREATE TABLE document_table (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    table_ref VARCHAR(64) NOT NULL,
    title_text VARCHAR(1024) DEFAULT NULL,
    unit_text VARCHAR(128) DEFAULT NULL,
    page_start INT NOT NULL,
    page_end INT NOT NULL,
    x0 DECIMAL(10,2) DEFAULT NULL, y0 DECIMAL(10,2) DEFAULT NULL,
    x1 DECIMAL(10,2) DEFAULT NULL, y1 DECIMAL(10,2) DEFAULT NULL,
    confidence DECIMAL(5,4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_table_version_ref (version_id, table_ref),
    INDEX idx_table_version_page (version_id, page_start),
    CONSTRAINT fk_table_version FOREIGN KEY (version_id) REFERENCES document_version(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='年报表格结构与跨页范围';

CREATE TABLE document_table_cell (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_id BIGINT NOT NULL,
    page_id BIGINT NOT NULL,
    row_no INT NOT NULL,
    column_no INT NOT NULL,
    row_span INT NOT NULL DEFAULT 1,
    column_span INT NOT NULL DEFAULT 1,
    text_content LONGTEXT DEFAULT NULL,
    x0 DECIMAL(10,2) DEFAULT NULL, y0 DECIMAL(10,2) DEFAULT NULL,
    x1 DECIMAL(10,2) DEFAULT NULL, y1 DECIMAL(10,2) DEFAULT NULL,
    confidence DECIMAL(5,4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_table_cell_position (table_id, page_id, row_no, column_no),
    INDEX idx_cell_table_row (table_id, row_no, column_no),
    CONSTRAINT fk_cell_table FOREIGN KEY (table_id) REFERENCES document_table(id) ON DELETE CASCADE,
    CONSTRAINT fk_cell_page FOREIGN KEY (page_id) REFERENCES document_page(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='年报表格单元格与源坐标';

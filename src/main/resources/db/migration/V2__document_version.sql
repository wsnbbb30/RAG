-- ============================================================
-- V2: 文档版本与金融元数据
-- 为每份年报建立逻辑文档 → 版本 → 元数据的三层模型
-- ============================================================

-- 1. 逻辑文档表：代表"一份年报"的抽象概念（不绑定具体文件）
CREATE TABLE document (
                          id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
                          document_id VARCHAR(64) NOT NULL COMMENT '文档自然键，格式: STOCK_CODE-FISCAL_YEAR-REPORT_TYPE-LANG',
                          company_name VARCHAR(255) NOT NULL COMMENT '公司全称',
                          stock_code VARCHAR(10) NOT NULL COMMENT '股票代码，如 000002',
                          report_type VARCHAR(32) NOT NULL COMMENT '报告类型: ANNUAL_REPORT/SEMI_ANNUAL_REPORT/QUARTERLY_REPORT',
                          fiscal_year INT NOT NULL COMMENT '财年，如 2023',
                          language VARCHAR(8) NOT NULL DEFAULT 'CN' COMMENT '语言: CN/EN',
                          total_versions INT NOT NULL DEFAULT 1 COMMENT '版本总数（修订计数）',
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          UNIQUE KEY uk_document_id (document_id),
                          INDEX idx_company_year (company_name, fiscal_year),
                          INDEX idx_stock_code (stock_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='逻辑文档表';

-- 2. 文档版本表：代表一份 PDF 文件的具体版本（同一报告可能修订）
CREATE TABLE document_version (
                                  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
                                  document_id VARCHAR(64) NOT NULL COMMENT '关联逻辑文档',
                                  version_no INT NOT NULL COMMENT '版本号，从 1 开始递增',
                                  file_md5 VARCHAR(32) NOT NULL COMMENT '文件 MD5 指纹',
                                  file_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
                                  page_count INT DEFAULT NULL COMMENT 'PDF 页数',
                                  parser_version VARCHAR(32) DEFAULT NULL COMMENT '解析器版本号',
                                  chunker_version VARCHAR(32) DEFAULT NULL COMMENT '切块器版本号',
                                  embedding_model VARCHAR(64) DEFAULT NULL COMMENT '向量模型标识',
                                  status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED' COMMENT '处理状态: UPLOADED/PARSING/PARSED/CHUNKING/CHUNKED/EMBEDDING/INDEXED/FAILED',
                                  error_message TEXT DEFAULT NULL COMMENT '失败原因（仅 status=FAILED 时有值）',
                                  created_by VARCHAR(64) NOT NULL COMMENT '上传用户 ID',
                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  UNIQUE KEY uk_doc_version (document_id, version_no),
                                  UNIQUE KEY uk_file_md5_version (file_md5, document_id),
                                  INDEX idx_status (status),
                                  INDEX idx_created_by (created_by),
                                  CONSTRAINT fk_version_document FOREIGN KEY (document_id) REFERENCES document(document_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档版本表';

-- 3. 年报元数据表：存储从年报中提取的结构化财务信息
CREATE TABLE financial_report_metadata (
                                           id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
                                           version_id BIGINT NOT NULL COMMENT '关联 document_version.id',
    -- 确定性信息（从文件名/封面提取）
                                           company_name VARCHAR(255) NOT NULL COMMENT '公司全称',
                                           stock_code VARCHAR(10) NOT NULL COMMENT '股票代码',
                                           report_type VARCHAR(32) NOT NULL COMMENT '报告类型',
                                           fiscal_year INT NOT NULL COMMENT '财年',
                                           period VARCHAR(8) NOT NULL DEFAULT 'FY' COMMENT '会计期间: Q1/Q2/Q3/Q4/FY（全年）',
                                           scope VARCHAR(32) NOT NULL DEFAULT 'CONSOLIDATED' COMMENT '合并口径: CONSOLIDATED(合并)/PARENT_COMPANY(母公司)',
                                           currency VARCHAR(8) NOT NULL DEFAULT 'CNY' COMMENT '币种: CNY/USD/HKD',
    -- 审计信息（从审计报告页提取）
                                           audit_opinion VARCHAR(64) DEFAULT NULL COMMENT '审计意见: STANDARD_UNQUALIFIED/QUALIFIED/ADVERSE/DISCLAIMER',
                                           auditor VARCHAR(128) DEFAULT NULL COMMENT '审计机构名称',
    -- 提取过程元数据
                                           confidence VARCHAR(16) NOT NULL DEFAULT 'MEDIUM' COMMENT '置信度: HIGH/MEDIUM/LOW/MANUAL（人工确认）',
                                           extracted_from VARCHAR(32) NOT NULL DEFAULT 'FILENAME' COMMENT '提取来源: FILENAME/COVER_PAGE/AUDIT_PAGE/MANUAL',
                                           extraction_note TEXT DEFAULT NULL COMMENT '提取备注（置信度为 LOW 时记录原因）',
                                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                           updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                           UNIQUE KEY uk_version_metadata (version_id),
                                           INDEX idx_company_year (company_name, fiscal_year),
                                           INDEX idx_confidence (confidence),
                                           CONSTRAINT fk_metadata_version FOREIGN KEY (version_id) REFERENCES document_version(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='年报元数据表';

-- 4. 给 file_upload 表增添 version_id 列（建立上传记录到版本的关联）
ALTER TABLE file_upload
    ADD COLUMN version_id BIGINT DEFAULT NULL COMMENT '关联 document_version.id' AFTER file_md5,
    ADD INDEX idx_version_id (version_id),
    ADD CONSTRAINT fk_file_upload_version
        FOREIGN KEY (version_id) REFERENCES document_version(id) ON DELETE SET NULL;

-- 5. 元数据提取与人工复核审计：保留原始提取快照，避免人工修正覆盖证据来源
CREATE TABLE report_metadata_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    version_id BIGINT NOT NULL COMMENT '关联 document_version.id',
    event_type VARCHAR(32) NOT NULL COMMENT 'EXTRACTION/COVER_ENRICHMENT/MANUAL_REVIEW',
    operator_id VARCHAR(64) DEFAULT NULL COMMENT '操作人；系统提取为空',
    before_snapshot TEXT DEFAULT NULL COMMENT '变更前元数据快照(JSON)',
    after_snapshot TEXT NOT NULL COMMENT '变更后元数据快照(JSON)',
    review_note TEXT DEFAULT NULL COMMENT '提取或复核说明',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_metadata_audit_version_created (version_id, created_at),
    CONSTRAINT fk_metadata_audit_version
        FOREIGN KEY (version_id) REFERENCES document_version(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='年报元数据审计记录';

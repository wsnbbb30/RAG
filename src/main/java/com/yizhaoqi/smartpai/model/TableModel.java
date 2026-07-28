package com.yizhaoqi.smartpai.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一张可回溯的年报表格。
 *
 * <p>表格本体属于 MySQL 真相源，MinIO 中的 JSON/Markdown 只是可重建的展示产物。
 * pageStart/pageEnd 支持跨页合并；低置信度表格不会被覆盖，而是保留以供人工复核。</p>
 */
@Data
@Entity
@Table(name = "document_table", uniqueConstraints = @UniqueConstraint(name = "uk_table_version_ref", columnNames = {"version_id", "table_ref"}))
public class TableModel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    /** 同一文档版本内稳定的表格编号，例如 table-12-3。 */
    @Column(name = "table_ref", nullable = false, length = 64)
    private String tableRef;

    @Column(name = "title_text", length = 1024)
    private String titleText;

    @Column(name = "unit_text", length = 128)
    private String unitText;

    @Column(name = "page_start", nullable = false)
    private Integer pageStart;
    @Column(name = "page_end", nullable = false)
    private Integer pageEnd;

    @Column(precision = 10, scale = 2) private BigDecimal x0;
    @Column(precision = 10, scale = 2) private BigDecimal y0;
    @Column(precision = 10, scale = 2) private BigDecimal x1;
    @Column(precision = 10, scale = 2) private BigDecimal y1;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

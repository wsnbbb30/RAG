package com.yizhaoqi.smartpai.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 可精确定位的表格单元格；原始文本永远保留，数值标准化由 S3-02 单独完成。
 */
@Data
@Entity
@Table(name = "document_table_cell", uniqueConstraints = @UniqueConstraint(
        name = "uk_table_cell_position", columnNames = {"table_id", "page_id", "row_no", "column_no"}))
public class TableCell {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "table_id", nullable = false) private Long tableId;
    @Column(name = "page_id", nullable = false) private Long pageId;
    @Column(name = "row_no", nullable = false) private Integer rowNo;
    @Column(name = "column_no", nullable = false) private Integer columnNo;
    @Column(name = "row_span", nullable = false) private Integer rowSpan = 1;
    @Column(name = "column_span", nullable = false) private Integer columnSpan = 1;
    @Lob @Column(name = "text_content", columnDefinition = "LONGTEXT") private String textContent;
    @Column(precision = 10, scale = 2) private BigDecimal x0;
    @Column(precision = 10, scale = 2) private BigDecimal y0;
    @Column(precision = 10, scale = 2) private BigDecimal x1;
    @Column(precision = 10, scale = 2) private BigDecimal y1;
    @Column(nullable = false, precision = 5, scale = 4) private BigDecimal confidence;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
}

package com.yizhaoqi.smartpai.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 页内最小可追溯内容单元。S1-02 先落标题、段落、图片占位等；
 * S3-01 会在同一模型上补表格和单元格结构，避免另建脱节的数据链路。
 */
@Data
@Entity
@Table(name = "document_element")
public class DocumentElement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_id", nullable = false)
    private Long pageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "element_type", length = 32, nullable = false)
    private ElementType elementType;

    //文本内容，可存储长文本
    @Lob
    @Column(name = "text_content")
    private String textContent;

    //阅读顺序，即在同一页内的序号
    @Column(name = "order_no", nullable = false)
    private Integer orderNo;

    //位置信息
    @Column(precision = 10, scale = 2) private BigDecimal x0;
    @Column(precision = 10, scale = 2) private BigDecimal y0;
    @Column(precision = 10, scale = 2) private BigDecimal x1;
    @Column(precision = 10, scale = 2) private BigDecimal y1;

    //标题级别
    @Column(name = "heading_level")
    private Integer headingLevel;

    //表格引用标识
    @Column(name = "table_ref", length = 64)
    private String tableRef;

    //置信度
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence = BigDecimal.ONE;

    @Column(name = "source_text_hash", length = 64)
    private String sourceTextHash;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    //元素类型，包括标题、段落、表格、图片、标题/图注、页眉、页脚
    public enum ElementType { TITLE, PARAGRAPH, TABLE, IMAGE, CAPTION, HEADER, FOOTER }
}
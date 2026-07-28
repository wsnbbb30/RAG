package com.yizhaoqi.smartpai.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 一个 DocumentVersion 对应多页 DocumentPage。
 * 不使用 JPA 级联对象图，避免大批量解析时因持久化上下文过大导致内存增长。
 */
@Data
@Entity
@Table(name = "document_page")
public class DocumentPage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "page_no", nullable = false)
    private Integer pageNo;

    //页面宽度
    @Column(nullable = false, precision = 10, scale = 2)
    private java.math.BigDecimal width;

    //页面高度
    @Column(nullable = false, precision = 10, scale = 2)
    private java.math.BigDecimal height;

    //旋转角度
    @Column(nullable = false)
    private Integer rotation = 0;

    //页面图片存储位置，即对象存储key，如MinIO
    @Column(name = "image_object_key", length = 512)
    private String imageObjectKey;

    //该页的字符数
    @Column(name = "text_char_count", nullable = false)
    private Integer textCharCount = 0;

    //OCR处理状态
    @Enumerated(EnumType.STRING)
    @Column(name = "ocr_status", length = 32, nullable = false)
    private OcrStatus ocrStatus = OcrStatus.NOT_REQUIRED;

    //解析器版本号
    @Column(name = "parser_version", length = 64, nullable = false)
    private String parserVersion;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum OcrStatus {
        //不需要OCR
        NOT_REQUIRED,
        //等待ing
        PENDING,
        SUCCEEDED,
        FAILED }
}
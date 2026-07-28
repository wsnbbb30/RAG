package com.yizhaoqi.smartpai.model.dto;

import com.yizhaoqi.smartpai.model.DocumentVersion;
import com.yizhaoqi.smartpai.model.FinancialReportMetadata;
import lombok.Data;

/**
 * 年报元数据响应 DTO。
 * 将 DocumentVersion + FinancialReportMetadata 组装为前端可展示的结构。
 */
@Data
public class ReportMetadataDTO {

    // 来自 DocumentVersion
    private Long versionId;
    private String documentId;
    private Integer versionNo;
    private String fileMd5;
    private Long fileSize;
    private Integer pageCount;
    private String status;

    // 来自 FinancialReportMetadata
    private String companyName;
    private String stockCode;
    private String reportType;
    private Integer fiscalYear;
    private String period;
    private String scope;
    private String currency;
    private String auditOpinion;
    private String auditor;

    // 提取元数据
    private String confidence;
    private String extractedFrom;
    private String extractionNote;

    /**
     * 从实体组装 DTO。
     */
    public static ReportMetadataDTO from(DocumentVersion version, FinancialReportMetadata metadata) {
        ReportMetadataDTO dto = new ReportMetadataDTO();
        dto.setVersionId(version.getId());
        dto.setDocumentId(version.getDocumentId());
        dto.setVersionNo(version.getVersionNo());
        dto.setFileMd5(version.getFileMd5());
        dto.setFileSize(version.getFileSize());
        dto.setPageCount(version.getPageCount());
        dto.setStatus(version.getStatus().name());

        if (metadata != null) {
            dto.setCompanyName(metadata.getCompanyName());
            dto.setStockCode(metadata.getStockCode());
            dto.setReportType(metadata.getReportType().name());
            dto.setFiscalYear(metadata.getFiscalYear());
            dto.setPeriod(metadata.getPeriod());
            dto.setScope(metadata.getScope().name());
            dto.setCurrency(metadata.getCurrency());
            dto.setAuditOpinion(metadata.getAuditOpinion() != null ? metadata.getAuditOpinion().name() : null);
            dto.setAuditor(metadata.getAuditor());
            dto.setConfidence(metadata.getConfidence().name());
            dto.setExtractedFrom(metadata.getExtractedFrom());
            dto.setExtractionNote(metadata.getExtractionNote());
        }

        return dto;
    }
}
package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.Document;
import com.yizhaoqi.smartpai.model.DocumentVersion;
import com.yizhaoqi.smartpai.model.FinancialReportMetadata;
import com.yizhaoqi.smartpai.model.ReportMetadataAudit;
import com.yizhaoqi.smartpai.model.dto.ReportMetadataDTO;
import com.yizhaoqi.smartpai.model.dto.ReportMetadataReviewRequest;
import com.yizhaoqi.smartpai.repository.DocumentRepository;
import com.yizhaoqi.smartpai.repository.DocumentVersionRepository;
import com.yizhaoqi.smartpai.repository.FinancialReportMetadataRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

/** 处理低置信年报元数据的人工确认，并保留修改前后的审计快照。 */
@Service
public class ReportMetadataReviewService {
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final FinancialReportMetadataRepository metadataRepository;
    private final ReportMetadataExtractor extractor;

    public ReportMetadataReviewService(DocumentRepository documentRepository, DocumentVersionRepository versionRepository,
                                       FinancialReportMetadataRepository metadataRepository, ReportMetadataExtractor extractor) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.metadataRepository = metadataRepository;
        this.extractor = extractor;
    }

    @Transactional(readOnly = true)
    public ReportMetadataDTO get(Long versionId) {
        DocumentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new CustomException("文档版本不存在", HttpStatus.NOT_FOUND));
        return ReportMetadataDTO.from(version, metadataRepository.findByVersionId(versionId).orElse(null));
    }

    @Transactional
    public ReportMetadataDTO review(ReportMetadataReviewRequest request, String reviewerId) {
        if (request == null || request.getVersionId() == null) {
            throw new CustomException("versionId 为必填项", HttpStatus.BAD_REQUEST);
        }
        DocumentVersion version = versionRepository.findById(request.getVersionId())
                .orElseThrow(() -> new CustomException("文档版本不存在", HttpStatus.NOT_FOUND));
        FinancialReportMetadata metadata = metadataRepository.findByVersionId(version.getId())
                .orElseThrow(() -> new CustomException("文档版本缺少元数据", HttpStatus.CONFLICT));

        FinancialReportMetadata before = copy(metadata);
        apply(request, metadata);
        validate(metadata);
        moveVersionIfNeeded(version, metadata);
        metadata.setConfidence(FinancialReportMetadata.Confidence.MANUAL);
        metadata.setExtractedFrom("MANUAL");
        metadata.setExtractionNote(request.getReviewNote());
        metadataRepository.save(metadata);
        extractor.recordAudit(version.getId(), ReportMetadataAudit.EventType.MANUAL_REVIEW, reviewerId, before, metadata,
                request.getReviewNote());
        return ReportMetadataDTO.from(version, metadata);
    }

    private void moveVersionIfNeeded(DocumentVersion version, FinancialReportMetadata metadata) {
        String targetId = "%s-%d-%s-CN".formatted(metadata.getStockCode(), metadata.getFiscalYear(), metadata.getReportType().name());
        if (targetId.equals(version.getDocumentId())) return;
        if (versionRepository.findByDocumentIdAndFileMd5(targetId, version.getFileMd5()).isPresent()) {
            throw new CustomException("目标逻辑文档已存在相同文件内容，请先处理重复版本", HttpStatus.CONFLICT);
        }
        Document oldDocument = documentRepository.findByDocumentIdForUpdate(version.getDocumentId()).orElse(null);
        Document target = documentRepository.findByDocumentIdForUpdate(targetId).orElseGet(() -> createDocument(targetId, metadata));
        int nextNo = versionRepository.findTopByDocumentIdOrderByVersionNoDesc(targetId)
                .map(item -> item.getVersionNo() + 1).orElse(1);
        version.setDocumentId(targetId);
        version.setVersionNo(nextNo);
        versionRepository.save(version);
        target.setTotalVersions(nextNo);
        documentRepository.save(target);
        if (oldDocument != null) {
            int left = Math.max(0, oldDocument.getTotalVersions() - 1);
            if (left == 0) documentRepository.delete(oldDocument);
            else { oldDocument.setTotalVersions(left); documentRepository.save(oldDocument); }
        }
    }

    private Document createDocument(String id, FinancialReportMetadata metadata) {
        Document document = new Document();
        document.setDocumentId(id); document.setCompanyName(metadata.getCompanyName());
        document.setStockCode(metadata.getStockCode()); document.setReportType(metadata.getReportType());
        document.setFiscalYear(metadata.getFiscalYear()); document.setLanguage("CN"); document.setTotalVersions(0);
        return documentRepository.saveAndFlush(document);
    }

    private void apply(ReportMetadataReviewRequest request, FinancialReportMetadata metadata) {
        if (hasText(request.getCompanyName())) metadata.setCompanyName(request.getCompanyName().trim());
        if (hasText(request.getStockCode())) metadata.setStockCode(request.getStockCode().trim());
        if (hasText(request.getReportType())) metadata.setReportType(parse(Document.ReportType.class, request.getReportType(), "reportType"));
        if (request.getFiscalYear() != null) metadata.setFiscalYear(request.getFiscalYear());
        if (hasText(request.getPeriod())) metadata.setPeriod(request.getPeriod().trim());
        if (hasText(request.getScope())) metadata.setScope(parse(FinancialReportMetadata.ReportScope.class, request.getScope(), "scope"));
        if (hasText(request.getCurrency())) metadata.setCurrency(request.getCurrency().trim());
        if (hasText(request.getAuditOpinion())) metadata.setAuditOpinion(parse(FinancialReportMetadata.AuditOpinion.class, request.getAuditOpinion(), "auditOpinion"));
        if (hasText(request.getAuditor())) metadata.setAuditor(request.getAuditor().trim());
    }

    private void validate(FinancialReportMetadata metadata) {
        if (!metadata.getStockCode().matches("\\d{6}") || !hasText(metadata.getCompanyName()))
            throw new CustomException("人工确认后必须提供有效的六位股票代码和公司全称", HttpStatus.BAD_REQUEST);
        if (metadata.getFiscalYear() < 1990 || metadata.getFiscalYear() > Year.now().getValue() + 1)
            throw new CustomException("财年超出允许范围", HttpStatus.BAD_REQUEST);
        if (!metadata.getPeriod().matches("Q[1-4]|FY"))
            throw new CustomException("period 仅支持 Q1/Q2/Q3/Q4/FY", HttpStatus.BAD_REQUEST);
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        try { return Enum.valueOf(type, value.trim()); }
        catch (IllegalArgumentException exception) { throw new CustomException(field + " 枚举值非法", HttpStatus.BAD_REQUEST); }
    }
    private FinancialReportMetadata copy(FinancialReportMetadata source) {
        FinancialReportMetadata target = new FinancialReportMetadata();
        target.setCompanyName(source.getCompanyName()); target.setStockCode(source.getStockCode()); target.setReportType(source.getReportType());
        target.setFiscalYear(source.getFiscalYear()); target.setPeriod(source.getPeriod()); target.setScope(source.getScope());
        target.setCurrency(source.getCurrency()); target.setAuditOpinion(source.getAuditOpinion()); target.setAuditor(source.getAuditor());
        target.setConfidence(source.getConfidence()); target.setExtractedFrom(source.getExtractedFrom()); target.setExtractionNote(source.getExtractionNote());
        return target;
    }
}

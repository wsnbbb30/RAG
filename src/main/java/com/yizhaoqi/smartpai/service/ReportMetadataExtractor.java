package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.Document;
import com.yizhaoqi.smartpai.model.DocumentVersion;
import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.model.FinancialReportMetadata;
import com.yizhaoqi.smartpai.model.ReportMetadataAudit;
import com.yizhaoqi.smartpai.repository.DocumentRepository;
import com.yizhaoqi.smartpai.repository.DocumentVersionRepository;
import com.yizhaoqi.smartpai.repository.FinancialReportMetadataRepository;
import com.yizhaoqi.smartpai.repository.ReportMetadataAuditRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 财报元数据提取服务
 *
 * 功能：当文件上传合并完成后，创建 Document -> DocumentVersion -> FinancialReportMetadata 聚合根。
 *
 * 设计原则：
 * 1. 文件名提取只产生"候选结果"，未解析的文件会被隔离并显式标记为 LOW 置信度
 * 2. 不会将未解析的文件静默关联到任意报告，避免数据错误
 */
@Service
public class ReportMetadataExtractor {

    private static final Logger log = LoggerFactory.getLogger(ReportMetadataExtractor.class);
    private static final String UNRESOLVED_COMPANY = "待人工确认";
    private static final String UNRESOLVED_STOCK_CODE = "UNRESOLVED";

    /**
     * 规范化后的文件名格式：主体 年份 报告类型 [附加说明]。
     * 匹配前会将中英文标点、下划线、连字符、括号等统一为空格，
     * 因此可兼容 600519_2023_annual_report.pdf、000002：2023 年度报告.pdf 等命名。
     */
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile(
            "^(?<subject>.+?)\\s+(?<year>20\\d{2})\\s*(?:年\\s*)?"
                    + "(?<type>年度报告|年报|中期报告|半年度报告|半年报|"
                    + "第一季度报告|一季报|第二季度报告|二季报|第三季度报告|三季报|"
                    + "第四季度报告|四季报|季度报告|季报|"
                    + "annual(?:\\s+report)?|semi[\\s-]*annual(?:\\s+report)?|"
                    + "half[\\s-]*year(?:\\s+report)?|interim(?:\\s+report)?|"
                    + "q[1-4](?:\\s+report)?|quarter(?:ly)?(?:\\s+report)?)"
                    + "(?:\\s+.*)?$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("^\\d{6}$");
    private static final Pattern YEAR_PATTERN = Pattern.compile("(20\\d{2})");

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final FinancialReportMetadataRepository metadataRepository;
    private final ReportMetadataAuditRepository auditRepository;
    private final CompanyIdentityResolver companyIdentityResolver;
    private final ObjectMapper objectMapper;

    public ReportMetadataExtractor(DocumentRepository documentRepository,
                                   DocumentVersionRepository versionRepository,
                                   FinancialReportMetadataRepository metadataRepository,
                                   ReportMetadataAuditRepository auditRepository,
                                   CompanyIdentityResolver companyIdentityResolver,
                                   ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.metadataRepository = metadataRepository;
        this.auditRepository = auditRepository;
        this.companyIdentityResolver = companyIdentityResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * 对上传记录及同一逻辑文档中的相同文件内容保持幂等。
     * 调用方必须在 MinIO 合并成功后、发布异步处理任务前调用。
     */
    @Transactional
    public DocumentVersion extractAndCreate(FileUpload fileUpload) {
        validateUpload(fileUpload);

        if (fileUpload.getVersionId() != null) {
            return versionRepository.findById(fileUpload.getVersionId())
                    .orElseThrow(() -> new IllegalStateException(
                            "file_upload.version_id points to a missing document version: " + fileUpload.getVersionId()));
        }

        ExtractionResult result = parseFileName(fileUpload.getFileName(), fileUpload.getFileMd5());
        String documentId = buildDocumentId(result, fileUpload.getFileMd5());

        Optional<DocumentVersion> existing = versionRepository.findByDocumentIdAndFileMd5(documentId, fileUpload.getFileMd5());
        if (existing.isPresent()) {
            fileUpload.setVersionId(existing.get().getId());
            log.info("Reuse document version for identical upload: documentId={}, versionId={}",
                    documentId, existing.get().getId());
            return existing.get();
        }

        Document document = documentRepository.findByDocumentIdForUpdate(documentId)
                .orElseGet(() -> createDocument(documentId, result));
        // 已存在的逻辑文档已加锁；首次并发创建由唯一键兜底，调用方可重试一次。
        int versionNo = versionRepository.findTopByDocumentIdOrderByVersionNoDesc(documentId)
                .map(version -> version.getVersionNo() + 1)
                .orElse(1);

        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(documentId);
        version.setVersionNo(versionNo);
        version.setFileMd5(fileUpload.getFileMd5());
        version.setFileSize(fileUpload.getTotalSize());
        version.setStatus(DocumentVersion.ProcessingStatus.UPLOADED);
        version.setCreatedBy(fileUpload.getUserId());
        version = versionRepository.save(version);

        FinancialReportMetadata metadata = new FinancialReportMetadata();
        metadata.setVersionId(version.getId());
        metadata.setCompanyName(result.companyName());
        metadata.setStockCode(result.stockCode());
        metadata.setReportType(result.reportType());
        metadata.setFiscalYear(result.fiscalYear());
        metadata.setConfidence(result.confidence());
        metadata.setExtractedFrom("FILENAME");
        metadata.setExtractionNote(result.extractionNote());
        metadataRepository.save(metadata);
        recordAudit(version.getId(), ReportMetadataAudit.EventType.EXTRACTION, null, null, metadata,
                "文件名候选提取");

        document.setTotalVersions(versionNo);
        documentRepository.save(document);
        fileUpload.setVersionId(version.getId());

        log.info("Created report aggregate: documentId={}, versionNo={}, versionId={}, confidence={}",
                documentId, versionNo, version.getId(), result.confidence());
        return version;
    }

    ExtractionResult parseFileName(String fileName, String fileMd5) {
        String normalizedName = normalizeFileName(fileName);
        Matcher matcher = FILE_NAME_PATTERN.matcher(normalizedName);
        if (!matcher.matches()) {
            return unresolved(fileMd5, normalizedName, "文件名未匹配年报命名规则，需要人工确认");
        }

        int fiscalYear = Integer.parseInt(matcher.group("year"));
        String subject = matcher.group("subject");
        Document.ReportType reportType = toReportType(matcher.group("type"));
        if (reportType == null) {
            return unresolved(fileMd5, normalizedName, "未识别报告类型，需要人工确认");
        }

        if (STOCK_CODE_PATTERN.matcher(subject).matches()) {
            return companyIdentityResolver.resolveByStockCode(subject)
                    .map(identity -> new ExtractionResult(identity.companyName(), identity.stockCode(), reportType,
                            fiscalYear, FinancialReportMetadata.Confidence.MEDIUM, null))
                    .orElseGet(() -> new ExtractionResult(UNRESOLVED_COMPANY, subject, reportType, fiscalYear,
                            FinancialReportMetadata.Confidence.LOW,
                            "股票代码 " + subject + " 未在本地公司字典中命中，需要人工确认公司名称"));
        }

        return companyIdentityResolver.resolveByCompanyName(subject)
                .map(identity -> new ExtractionResult(identity.companyName(), identity.stockCode(), reportType,
                        fiscalYear, FinancialReportMetadata.Confidence.MEDIUM, null))
                .orElseGet(() -> new ExtractionResult(subject, UNRESOLVED_STOCK_CODE, reportType, fiscalYear,
                        FinancialReportMetadata.Confidence.LOW,
                        "公司名称 \"" + subject + "\" 未匹配股票代码，需要人工确认"));
    }

    private ExtractionResult unresolved(String fileMd5, String fileName, String reason) {
        Matcher yearMatcher = YEAR_PATTERN.matcher(fileName);
        int fiscalYear = yearMatcher.find() ? Integer.parseInt(yearMatcher.group(1)) : 0;
        return new ExtractionResult(UNRESOLVED_COMPANY, UNRESOLVED_STOCK_CODE,
                Document.ReportType.ANNUAL_REPORT, fiscalYear, FinancialReportMetadata.Confidence.LOW,
                reason + "；原始文件名：" + fileName + "；fileMd5=" + fileMd5);
    }

    private String buildDocumentId(ExtractionResult result, String fileMd5) {
        if (UNRESOLVED_STOCK_CODE.equals(result.stockCode()) || result.fiscalYear() == 0) {
            return "UNRESOLVED-" + fileMd5.toLowerCase(Locale.ROOT);
        }
        return "%s-%d-%s-CN".formatted(result.stockCode(), result.fiscalYear(), result.reportType().name());
    }

    private Document createDocument(String documentId, ExtractionResult result) {
        Document document = new Document();
        document.setDocumentId(documentId);
        document.setCompanyName(result.companyName());
        document.setStockCode(result.stockCode());
        document.setReportType(result.reportType());
        document.setFiscalYear(result.fiscalYear());
        document.setLanguage("CN");
        document.setTotalVersions(0);
        return documentRepository.saveAndFlush(document);
    }

    private Document.ReportType toReportType(String keyword) {
        return switch (keyword.trim().toLowerCase(Locale.ROOT)) {
            case "年度报告", "年报" -> Document.ReportType.ANNUAL_REPORT;
            case "annual", "annual report" -> Document.ReportType.ANNUAL_REPORT;
            case "中期报告", "半年度报告", "半年报", "semi annual", "semi-annual", "semi annual report",
                    "semi-annual report", "half year", "half-year", "half year report", "half-year report",
                    "interim", "interim report" -> Document.ReportType.SEMI_ANNUAL_REPORT;
            case "第一季度报告", "一季报", "第二季度报告", "二季报", "第三季度报告", "三季报",
                    "第四季度报告", "四季报", "季度报告", "季报", "q1", "q1 report", "q2", "q2 report",
                    "q3", "q3 report", "q4", "q4 report", "quarter", "quarter report", "quarterly",
                    "quarterly report" -> Document.ReportType.QUARTERLY_REPORT;
            default -> null;
        };
    }

    /**
     * 统一文件名中的 Unicode 形式和分隔符，使解析规则不依赖上传者的命名习惯。
     * 不在这里猜测公司身份；无法匹配时仍按 LOW 置信度进入人工复核。
     */
    private String normalizeFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        String value = Normalizer.normalize(fileName.trim(), Normalizer.Form.NFKC)
                .replaceFirst("(?i)\\.pdf\\s*$", "")
                .replaceAll("[\\p{P}\\p{Z}]+", " ");
        // 将 2023年、2023Annual 这类无分隔符写法拆开，统一为“主体 2023 报告类型”。
        return value.replaceAll("(?<!\\d)(20\\d{2})(?!\\d)", " $1 ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void validateUpload(FileUpload fileUpload) {
        if (fileUpload == null || fileUpload.getFileMd5() == null || !fileUpload.getFileMd5().matches("[a-fA-F0-9]{32}")) {
            throw new IllegalArgumentException("A valid 32-character file MD5 is required");
        }
        if (fileUpload.getFileName() == null || fileUpload.getFileName().isBlank()) {
            throw new IllegalArgumentException("File name is required for report metadata extraction");
        }
        if (fileUpload.getUserId() == null || fileUpload.getUserId().isBlank()) {
            throw new IllegalArgumentException("Upload userId is required for document version creation");
        }
        if (fileUpload.getTotalSize() < 0) {
            throw new IllegalArgumentException("File size cannot be negative");
        }
    }

    record ExtractionResult(String companyName, String stockCode, Document.ReportType reportType,
                            int fiscalYear, FinancialReportMetadata.Confidence confidence, String extractionNote) {
    }

    /** 记录不可变审计快照，序列化失败时阻断写入，避免产生无法追溯的修改。 */
    void recordAudit(Long versionId, ReportMetadataAudit.EventType eventType, String operatorId,
                     FinancialReportMetadata before, FinancialReportMetadata after, String note) {
        ReportMetadataAudit audit = new ReportMetadataAudit();
        audit.setVersionId(versionId);
        audit.setEventType(eventType);
        audit.setOperatorId(operatorId);
        audit.setBeforeSnapshot(before == null ? null : toSnapshot(before));
        audit.setAfterSnapshot(toSnapshot(after));
        audit.setReviewNote(note);
        auditRepository.save(audit);
    }

    private String toSnapshot(FinancialReportMetadata metadata) {
        try {
            java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
            snapshot.put("companyName", metadata.getCompanyName());
            snapshot.put("stockCode", metadata.getStockCode());
            snapshot.put("reportType", metadata.getReportType().name());
            snapshot.put("fiscalYear", metadata.getFiscalYear());
            snapshot.put("period", metadata.getPeriod());
            snapshot.put("scope", metadata.getScope().name());
            snapshot.put("currency", metadata.getCurrency());
            snapshot.put("auditOpinion", metadata.getAuditOpinion() == null ? "" : metadata.getAuditOpinion().name());
            snapshot.put("auditor", metadata.getAuditor() == null ? "" : metadata.getAuditor());
            snapshot.put("confidence", metadata.getConfidence().name());
            snapshot.put("extractedFrom", metadata.getExtractedFrom());
            snapshot.put("extractionNote", metadata.getExtractionNote() == null ? "" : metadata.getExtractionNote());
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化年报元数据审计快照", exception);
        }
    }
}

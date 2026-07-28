package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.Document;
import com.yizhaoqi.smartpai.model.DocumentVersion;
import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.model.FinancialReportMetadata;
import com.yizhaoqi.smartpai.repository.DocumentRepository;
import com.yizhaoqi.smartpai.repository.DocumentVersionRepository;
import com.yizhaoqi.smartpai.repository.FinancialReportMetadataRepository;
import com.yizhaoqi.smartpai.repository.ReportMetadataAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportMetadataExtractorTest {

    private DocumentRepository documentRepository;
    private DocumentVersionRepository versionRepository;
    private FinancialReportMetadataRepository metadataRepository;
    private ReportMetadataAuditRepository auditRepository;
    private ReportMetadataExtractor extractor;

    @BeforeEach
    void setUp() {
        documentRepository = mock(DocumentRepository.class);
        versionRepository = mock(DocumentVersionRepository.class);
        metadataRepository = mock(FinancialReportMetadataRepository.class);
        auditRepository = mock(ReportMetadataAuditRepository.class);
        extractor = new ReportMetadataExtractor(documentRepository, versionRepository, metadataRepository, auditRepository,
                new LocalCompanyIdentityResolver(), new ObjectMapper());
    }

    @Test
    void createsVersionAndMarksKnownCompanyAsMediumConfidence() {
        FileUpload upload = upload("000002_2023_年度报告_CN.pdf", "0123456789abcdef0123456789abcdef");
        DocumentVersion savedVersion = new DocumentVersion();
        savedVersion.setId(101L);

        when(versionRepository.findByDocumentIdAndFileMd5("000002-2023-ANNUAL_REPORT-CN", upload.getFileMd5()))
                .thenReturn(Optional.empty());
        when(documentRepository.findByDocumentIdForUpdate("000002-2023-ANNUAL_REPORT-CN"))
                .thenReturn(Optional.empty());
        when(documentRepository.saveAndFlush(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(versionRepository.findTopByDocumentIdOrderByVersionNoDesc("000002-2023-ANNUAL_REPORT-CN"))
                .thenReturn(Optional.empty());
        when(versionRepository.save(any(DocumentVersion.class))).thenReturn(savedVersion);

        DocumentVersion result = extractor.extractAndCreate(upload);

        assertSame(savedVersion, result);
        assertEquals(101L, upload.getVersionId());
        verify(metadataRepository).save(org.mockito.ArgumentMatchers.argThat(metadata ->
                metadata.getVersionId().equals(101L)
                        && "000002".equals(metadata.getStockCode())
                        && "万科企业股份有限公司".equals(metadata.getCompanyName())
                        && metadata.getConfidence() == FinancialReportMetadata.Confidence.MEDIUM));
    }

    @Test
    void reusesVersionWhenUploadAlreadyHasVersionId() {
        FileUpload upload = upload("任意.pdf", "0123456789abcdef0123456789abcdef");
        upload.setVersionId(9L);
        DocumentVersion existing = new DocumentVersion();
        existing.setId(9L);
        when(versionRepository.findById(9L)).thenReturn(Optional.of(existing));

        assertSame(existing, extractor.extractAndCreate(upload));
        verify(versionRepository).findById(9L);
    }

    @Test
    void isolatesFilenameThatCannotBeParsedForManualReview() {
        ReportMetadataExtractor.ExtractionResult result = extractor.parseFileName(
                "upload-archive.pdf", "fedcba9876543210fedcba9876543210");

        assertEquals("待人工确认", result.companyName());
        assertEquals("UNRESOLVED", result.stockCode());
        assertEquals(0, result.fiscalYear());
        assertEquals(FinancialReportMetadata.Confidence.LOW, result.confidence());
        assertNotNull(result.extractionNote());
    }

    @Test
    void keepsUnknownStockCodeVisibleInsteadOfInventingCompanyName() {
        ReportMetadataExtractor.ExtractionResult result = extractor.parseFileName(
                "123456_2023_年度报告.pdf", "fedcba9876543210fedcba9876543210");

        assertEquals("123456", result.stockCode());
        assertEquals("待人工确认", result.companyName());
        assertEquals(FinancialReportMetadata.Confidence.LOW, result.confidence());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("supportedReportFileNames")
    void parsesEnglishReportNamesAndPunctuation(String fileName, String expectedStockCode,
                                                Document.ReportType expectedReportType, int expectedYear,
                                                FinancialReportMetadata.Confidence expectedConfidence) {
        ReportMetadataExtractor.ExtractionResult result = extractor.parseFileName(
                fileName, "fedcba9876543210fedcba9876543210");

        assertEquals(expectedStockCode, result.stockCode());
        assertEquals(expectedReportType, result.reportType());
        assertEquals(expectedYear, result.fiscalYear());
        assertEquals(expectedConfidence, result.confidence());
    }

    private static Stream<Arguments> supportedReportFileNames() {
        return Stream.of(
                Arguments.of("600519_2023_annual_report.pdf", "600519", Document.ReportType.ANNUAL_REPORT,
                        2023, FinancialReportMetadata.Confidence.MEDIUM),
                Arguments.of("000002-2023-Annual Report Summary.PDF", "000002", Document.ReportType.ANNUAL_REPORT,
                        2023, FinancialReportMetadata.Confidence.MEDIUM),
                Arguments.of("600519（2023）Annual-Report.pdf", "600519", Document.ReportType.ANNUAL_REPORT,
                        2023, FinancialReportMetadata.Confidence.MEDIUM),
                Arguments.of("000002：2023 年度报告.pdf", "000002", Document.ReportType.ANNUAL_REPORT,
                        2023, FinancialReportMetadata.Confidence.MEDIUM),
                Arguments.of("东方精工：2026年半年度报告.pdf", "UNRESOLVED", Document.ReportType.SEMI_ANNUAL_REPORT,
                        2026, FinancialReportMetadata.Confidence.LOW)
        );
    }

    private FileUpload upload(String fileName, String md5) {
        FileUpload upload = new FileUpload();
        upload.setFileName(fileName);
        upload.setFileMd5(md5);
        upload.setUserId("user-1");
        upload.setTotalSize(1024L);
        return upload;
    }
}

package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.*;
import com.yizhaoqi.smartpai.parser.*;
import com.yizhaoqi.smartpai.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析应用服务：负责状态、幂等、实体映射和持久化；不包含 PDF 解析细节。
 *
 * 职责定位：
 * 1.管理解析状态流转（IPLOADED -> PARSING -> PARSED）
 * 2.保证幂等性，即重复调用不会产生脏数据
 * 3.将解析器输出的纯数据对象（ParsedPage/ParsedElement）映射为数据库实体
 * 4.保存解析快照到MinIO
 *
 * 调用方：
 * - Kafka消费者，异步解析
 * - 定时任务，批量重试
 * - 管理后台，手动触发解析
 */
@Service
public class VersionedDocumentParseService {
    private final DocumentVersionRepository versionRepository;
    private final DocumentPageRepository pageRepository;
    private final DocumentElementRepository elementRepository;
    private final ParserRegistry parserRegistry;
    private final ParseArtifactStorage artifactStorage;

    public VersionedDocumentParseService(DocumentVersionRepository versionRepository,
                                         DocumentPageRepository pageRepository, DocumentElementRepository elementRepository,
                                         ParserRegistry parserRegistry, ParseArtifactStorage artifactStorage) {
        this.versionRepository = versionRepository; this.pageRepository = pageRepository;
        this.elementRepository = elementRepository; this.parserRegistry = parserRegistry;
        this.artifactStorage = artifactStorage;
    }

    /**
     * 执行文档解析，核心编排方法
     *
     * 完整流程：
     * 1.检查版本是否存在
     * 2.幂等检查：已解析或已索引则跳过
     * 3.状态流转：UPLOADED -> PARSING
     * 4.选择解析器：通过ParserRegistry根据文件类型匹配
     * 5.执行解析：调用DocumentParser.parse()
     * 6.清理旧数据：删除该版本原有的页面和元素，保证幂等
     * 7.保存新数据：遍历每一页，保存DocumentPage和DocumentElement
     * 8.保存快照：上传manifest.json到MinIO
     * 9.更新版本信息：页数、解析器版本
     * 10.状态流转：PARSING -> PARSED
     * @param versionId
     * @param fileName
     * @param contentType
     * @param inputStream
     * @throws Exception
     */
    @Transactional
    public void parse(Long versionId, String fileName, String contentType, InputStream inputStream) throws Exception {
        DocumentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("文档版本不存在: " + versionId));
        if (version.getStatus() == DocumentVersion.ProcessingStatus.PARSED
                || version.getStatus() == DocumentVersion.ProcessingStatus.INDEXED) return;

        version.setStatus(DocumentVersion.ProcessingStatus.PARSING);
        versionRepository.save(version);
        DocumentParser parser = parserRegistry.requireParser(fileName, contentType);
        ParseResult result = parser.parse(new ParseRequest(versionId, fileName, contentType, inputStream));

        // 解析重试前先删除旧页；数据库外的 manifest 使用固定对象键覆盖，二者保持幂等。
        List<Long> oldPageIds = pageRepository.findByVersionIdOrderByPageNoAsc(versionId).stream()
                .map(DocumentPage::getId).toList();
        if (!oldPageIds.isEmpty()) elementRepository.deleteByPageIdIn(oldPageIds);
        pageRepository.deleteByVersionId(versionId);

        for (ParsedPage parsedPage : result.pages()) {
            DocumentPage page = new DocumentPage();
            page.setVersionId(versionId); page.setPageNo(parsedPage.pageNo());
            page.setWidth(parsedPage.width()); page.setHeight(parsedPage.height());
            page.setRotation(parsedPage.rotation()); page.setTextCharCount(parsedPage.textCharCount());
            page.setOcrStatus(parsedPage.ocrRecommended() ? DocumentPage.OcrStatus.PENDING : DocumentPage.OcrStatus.NOT_REQUIRED);
            page.setParserVersion(result.parserVersion());
            page = pageRepository.save(page);
            List<DocumentElement> elements = new ArrayList<>();
            for (ParsedElement item : parsedPage.elements()) elements.add(toEntity(page.getId(), item));
            elementRepository.saveAll(elements);
        }
        artifactStorage.saveManifest(versionId, result);
        version.setPageCount(result.pages().size());
        version.setParserVersion(result.parserVersion());
        version.setStatus(DocumentVersion.ProcessingStatus.PARSED);
        version.setErrorMessage(null);
        versionRepository.save(version);
    }

    private DocumentElement toEntity(Long pageId, ParsedElement source) {
        DocumentElement target = new DocumentElement();
        target.setPageId(pageId); target.setElementType(DocumentElement.ElementType.valueOf(source.elementType().name()));
        target.setTextContent(source.textContent()); target.setOrderNo(source.orderNo());
        target.setHeadingLevel(source.headingLevel()); target.setConfidence(BigDecimal.valueOf(source.confidence()));
        target.setX0(source.boundingBox().x0()); target.setY0(source.boundingBox().y0());
        target.setX1(source.boundingBox().x1()); target.setY1(source.boundingBox().y1());
        target.setSourceTextHash(sha256(source.textContent()));
        return target;
    }

    /**
     * 计算文本的SHA-256哈希值
     *
     * 用途：
     * 1.内容去重，相同内容的段落只存储一份
     * 2.变更检测：对比不同版本的同业是否修改
     * 3.内容溯源：快速定位重复内容
     *
     * 算法：SHA-256（安全哈希算法，输出64位十六进制字符串）
     *
     * @param text
     * @return
     */
    private String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(); for (byte item : hash) value.append(String.format("%02x", item));
            return value.toString();
        } catch (Exception exception) { throw new IllegalStateException("计算文本哈希失败", exception); }
    }
}
package com.yizhaoqi.smartpai.retrieval;

import com.yizhaoqi.smartpai.config.RetrievalProperties;
import com.yizhaoqi.smartpai.model.FinancialFact;
import com.yizhaoqi.smartpai.model.FinancialMetric;
import com.yizhaoqi.smartpai.model.FinancialReportMetadata;
import com.yizhaoqi.smartpai.repository.FinancialFactRepository;
import com.yizhaoqi.smartpai.repository.FinancialMetricRepository;
import com.yizhaoqi.smartpai.repository.FinancialReportMetadataRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * S3 财务事实结构化召回。
 *
 * <p>不走 ES 全文检索，而是利用 MySQL 中已持久化的 {@link FinancialFact} 做精确字段过滤。
 * 仅在 QueryFilter 包含 stockCode + fiscalYear 且至少匹配到一个指标别名时才生效；
 * 条件不充分时返回空列表，由 BM25 / Vector 路兜底。</p>
 */
@Component
public class FinancialFactRetriever implements Retriever {

    private final FinancialFactRepository factRepository;
    private final FinancialReportMetadataRepository metadataRepository;
    private final FinancialMetricRepository metricRepository;
    private final RetrievalProperties properties;

    public FinancialFactRetriever(FinancialFactRepository factRepository,
                                  FinancialReportMetadataRepository metadataRepository,
                                  FinancialMetricRepository metricRepository,
                                  RetrievalProperties properties) {
        this.factRepository = factRepository;
        this.metadataRepository = metadataRepository;
        this.metricRepository = metricRepository;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "fact";
    }

    @Override
    public RetrievalResult retrieve(RetrievalContext context) {
        if (!properties.isFactEnabled()) {
            return RetrievalResult.disabled(name(), "fact 已由配置关闭");
        }

        long startedAt = System.nanoTime();
        try {
            QueryFilter filter = context.filters();
            if (filter == null || filter.isEmpty()) {
                return RetrievalResult.disabled(name(), "未提取到结构化过滤条件");
            }

            String stockCode = filter.stockCode();
            Integer fiscalYear = filter.fiscalYear();
            List<String> metricCodes = filter.metricCodes();

            // FinancialFact 需要 stockCode + fiscalYear 锚定版本，指标名由 QueryFilterExtractor 提取。
            if (stockCode == null || fiscalYear == null || metricCodes.isEmpty()) {
                return RetrievalResult.disabled(name(),
                        stockCode == null ? "缺少股票代码" : fiscalYear == null ? "缺少财年" : "未匹配到指标别名");
            }

            List<FinancialReportMetadata> metadatas = metadataRepository.findByStockCodeAndFiscalYear(stockCode, fiscalYear);
            if (metadatas.isEmpty()) {
                return RetrievalResult.disabled(name(),
                        "未找到 stockCode=" + stockCode + " fiscalYear=" + fiscalYear + " 的报告元数据");
            }

            List<RetrievalCandidate> candidates = new ArrayList<>();
            for (FinancialReportMetadata metadata : metadatas) {
                for (String metricCode : metricCodes) {
                    List<FinancialFact> facts = factRepository.findByVersionIdAndMetricCodeAndPeriodAndScope(
                            metadata.getVersionId(), metricCode, metadata.getPeriod(),
                            metadata.getScope() != null ? metadata.getScope() : FinancialReportMetadata.ReportScope.CONSOLIDATED);
                    for (FinancialFact fact : facts) {
                        if (fact.getReviewStatus() == FinancialFact.ReviewStatus.REJECTED) continue;
                        candidates.add(toCandidate(fact, metadata, metricCode));
                    }
                }
            }
            // 按 metricCode 排序保证 rank 顺序稳定；RRF 使用 rank 而非 rawScore。
            candidates.sort(java.util.Comparator.comparing(
                    c -> c.content() != null ? c.content() : ""));
            List<RetrievalCandidate> ranked = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                RetrievalCandidate c = candidates.get(i);
                ranked.add(withRank(c, i + 1));
            }
            return new RetrievalResult(name(), ranked, elapsedMs(startedAt), false, "");
        } catch (Exception exception) {
            return RetrievalResult.degraded(name(), elapsedMs(startedAt),
                    "FinancialFact 查询失败: " + exception.getClass().getSimpleName());
        }
    }

    private static RetrievalCandidate withRank(RetrievalCandidate c, int rank) {
        return new RetrievalCandidate(c.source(), rank, c.rawScore(), c.indexId(), c.versionId(), c.documentId(),
                c.chunkId(), c.chunkType(), c.parentChunkId(), c.content(), c.contentHash(),
                c.pageStart(), c.pageEnd(), c.ownerUserId(), c.orgTag(), c.isPublic(),
                c.stockCode(), c.fiscalYear(), c.reportType());
    }

    private RetrievalCandidate toCandidate(FinancialFact fact, FinancialReportMetadata metadata, String metricCode) {
        String content = buildContent(fact, metricCode);
        return new RetrievalCandidate(
                "fact",
                0, // 临时 rank，调用方会在排序后用 withRank 重新赋值
                1.0, // rawScore: 结构化精确命中，不需要非结构化相似度
                "fact-" + fact.getId(),
                fact.getVersionId(),
                metadata.getVersionId() != null ? String.valueOf(metadata.getVersionId()) : null,
                null, // chunkId — fact 不是 chunk
                null, // chunkType
                null, // parentChunkId
                content,
                null, // contentHash
                fact.getPageNo(),
                fact.getPageNo(),
                null, // ownerUserId — 年报公开，无个人属主
                null, // orgTag
                true, // isPublic — 年报是公开披露文件
                metadata.getStockCode(),
                metadata.getFiscalYear(),
                metadata.getReportType() != null ? metadata.getReportType().name() : null);
    }

    private String buildContent(FinancialFact fact, String metricCode) {
        String metricName = metricRepository.findByMetricCodeAndEnabledTrue(metricCode)
                .map(FinancialMetric::getCanonicalName)
                .orElse(metricCode);
        StringBuilder sb = new StringBuilder();
        sb.append(metricName).append(": ").append(fact.getValue().stripTrailingZeros().toPlainString());
        if (fact.getRawUnit() != null && !fact.getRawUnit().isBlank()) {
            sb.append(" ").append(fact.getRawUnit());
        }
        sb.append(" (第").append(fact.getPageNo()).append("页");
        if (fact.getRowNo() != null && fact.getColumnNo() != null) {
            sb.append(", ").append(fact.getRowNo()).append("行").append(fact.getColumnNo()).append("列");
        }
        sb.append(")");
        return sb.toString();
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}

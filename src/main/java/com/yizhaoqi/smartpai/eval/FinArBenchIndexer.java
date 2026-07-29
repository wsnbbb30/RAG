package com.yizhaoqi.smartpai.eval;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import com.yizhaoqi.smartpai.client.EmbeddingClient;
import com.yizhaoqi.smartpai.eval.model.EvaluationCase;
import com.yizhaoqi.smartpai.index.IndexDocument;
import com.yizhaoqi.smartpai.model.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 FinAR-Bench Markdown 表格按行拆分为 {@link IndexDocument} 并写入 ES。
 *
 * <p>写入目标索引由 {@link com.yizhaoqi.smartpai.config.VersionedIndexProperties} 决定，
 * 与 BM25/Vector retriever 使用同一索引，确保检索评测走真实路径。</p>
 */
@Component
public class FinArBenchIndexer {

    private static final Logger log = LoggerFactory.getLogger(FinArBenchIndexer.class);
    private static final Pattern YEAR_IN_HEADER = Pattern.compile("(20\\d{2})年");

    private final ElasticsearchClient esClient;
    private final EmbeddingClient embeddingClient;
    private final String indexName;

    public FinArBenchIndexer(ElasticsearchClient esClient,
                             EmbeddingClient embeddingClient,
                             com.yizhaoqi.smartpai.config.VersionedIndexProperties indexProperties) {
        this.esClient = esClient;
        this.embeddingClient = embeddingClient;
        this.indexName = indexProperties.getName();
    }

    /**
     * 将所有评测公司的表格数据索引到 ES。
     * @return 写入的 chunk 数量
     */
    public int indexAll(List<EvaluationCase> cases) {
        java.util.Map<String, List<EvaluationCase>> byCompany = new java.util.LinkedHashMap<>();
        for (EvaluationCase c : cases) {
            byCompany.computeIfAbsent(c.stockCode(), k -> new ArrayList<>()).add(c);
        }

        // 1. 生成所有 chunk（不设 vector）
        List<IndexDocument> allChunks = new ArrayList<>();
        int versionId = -1;
        for (var entry : byCompany.entrySet()) {
            EvaluationCase representative = entry.getValue().get(0);
            allChunks.addAll(chunkTable(versionId, representative));
            versionId--;
        }

        // 2. 批量生成向量
        if (!allChunks.isEmpty()) {
            log.info("开始为 {} 个 chunk 生成向量...", allChunks.size());
            List<String> contents = allChunks.stream().map(IndexDocument::content).toList();
            List<float[]> vectors = embeddingClient.embed(contents);
            for (int i = 0; i < allChunks.size(); i++) {
                IndexDocument doc = allChunks.get(i);
                allChunks.set(i, new IndexDocument(
                        doc.id(), doc.versionId(), doc.documentId(), doc.chunkId(), doc.chunkNo(),
                        doc.chunkType(), doc.parentChunkId(), doc.content(), doc.contentHash(),
                        doc.tokenCount(), doc.pageStart(), doc.pageEnd(), doc.elementIds(),
                        doc.parserVersion(), doc.chunkerVersion(), doc.embedding(),
                        vectors.get(i),
                        doc.ownerUserId(), doc.orgTag(), doc.isPublic(), doc.stockCode(),
                        doc.fiscalYear(), doc.reportType()));
            }
            log.info("向量生成完成: {} 个", vectors.size());

            // 3. 批量写入 ES
            bulkIndex(allChunks);
        }

        log.info("FinAR-Bench 表格索引完成: {} 家公司, {} 个 chunk", byCompany.size(), allChunks.size());
        return allChunks.size();
    }

    /** 将一家公司的 Markdown 报表拆分为行级 chunk。 */
    List<IndexDocument> chunkTable(int virtualVersionId, EvaluationCase sample) {
        String tableMarkdown = sample.tableContext();
        if (tableMarkdown == null || tableMarkdown.isBlank()) return List.of();

        List<IndexDocument> chunks = new ArrayList<>();
        long chunkId = 1;
        String currentSection = "";

        String[] lines = tableMarkdown.split("\\R");
        String[] headers = null;

        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;

            if (t.startsWith("# ")) {
                currentSection = t.substring(2).trim();
                headers = null;
                continue;
            }

            if (!t.startsWith("|")) continue;

            String[] cells = splitPipe(t);
            if (cells.length == 0) continue;

            if (isSeparatorRow(cells)) continue;
            if (headers == null) {
                headers = cells;
                continue;
            }

            if (cells.length >= 2 && !cells[0].isBlank()) {
                String content = buildChunkContent(currentSection, headers, cells);
                String stableId = IndexDocument.stableId((long) virtualVersionId, chunkId);
                IndexDocument doc = new IndexDocument(
                        stableId,
                        (long) virtualVersionId,
                        "finarbench:" + sample.stockCode(),
                        chunkId,
                        (int) chunkId,
                        DocumentChunk.ChunkType.TABLE,
                        null,
                        content,
                        null,
                        estimateTokenCount(content),
                        0, 0,
                        List.of(),
                        "finarbench-loader",
                        "table-row-v1",
                        null,
                        null,
                        null, null,
                        true,
                        sample.stockCode(),
                        null,
                        "ANNUAL_REPORT");
                chunks.add(doc);
                chunkId++;
            }
        }
        return chunks;
    }

    /** "[利润表] 营业收入: 2023年 3,632,703,199.78; 2022年 3,114,981,021.66" */
    private String buildChunkContent(String section, String[] headers, String[] cells) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(section).append("] ");
        sb.append(cells[0]);
        for (int i = 1; i < cells.length && i < headers.length; i++) {
            if (cells[i].isBlank() || cells[i].equals("-") || cells[i].equals("—")) continue;
            sb.append("; ");
            sb.append(extractYearLabel(headers[i])).append(" ").append(cells[i].replace(",", "").replace("，", ""));
        }
        return sb.toString();
    }

    private String extractYearLabel(String header) {
        Matcher m = YEAR_IN_HEADER.matcher(header);
        if (m.find()) return m.group(1) + "年";
        return header;
    }

    private int estimateTokenCount(String text) {
        return (int) Math.ceil(text.length() / 2.5);
    }

    private String[] splitPipe(String line) {
        String s = line.strip();
        if (s.startsWith("|")) s = s.substring(1);
        if (s.endsWith("|")) s = s.substring(0, s.length() - 1);
        String[] parts = s.split("\\|");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        return parts;
    }

    private boolean isSeparatorRow(String[] cells) {
        for (String cell : cells) {
            if (!cell.matches("^[-: ]+$")) return false;
        }
        return true;
    }

    private void bulkIndex(List<IndexDocument> docs) {
        try {
            List<BulkOperation> ops = docs.stream()
                    .map(doc -> BulkOperation.of(op -> op.index(idx -> idx
                            .index(indexName).id(doc.id()).document(doc))))
                    .toList();
            BulkResponse response = esClient.bulk(BulkRequest.of(b -> b.operations(ops)));
            if (response.errors()) {
                log.error("FinAR-Bench ES 批量索引存在错误");
                response.items().forEach(item -> {
                    if (item.error() != null) log.error("  {}: {}", item.id(), item.error().reason());
                });
            }
        } catch (Exception e) {
            log.error("FinAR-Bench ES 批量索引失败", e);
            throw new RuntimeException("FinAR-Bench ES 索引失败", e);
        }
    }

    /** 清理所有 eval 数据。 */
    public void cleanup() {
        try {
            DeleteByQueryRequest request = DeleteByQueryRequest.of(d -> d
                    .index(indexName)
                    .query(q -> q.term(t -> t.field("parserVersion").value("finarbench-loader"))));
            esClient.deleteByQuery(request);
            log.info("FinAR-Bench ES 数据已清理");
        } catch (Exception e) {
            log.warn("FinAR-Bench ES 数据清理失败", e);
        }
    }
}

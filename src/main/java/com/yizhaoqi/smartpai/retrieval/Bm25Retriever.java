package com.yizhaoqi.smartpai.retrieval;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.yizhaoqi.smartpai.config.RetrievalProperties;
import com.yizhaoqi.smartpai.config.VersionedIndexProperties;
import com.yizhaoqi.smartpai.index.IndexDocument;
import org.springframework.stereotype.Component;

import java.util.List;

/** 基于 IK/BM25 的词法召回；不依赖 Embedding 服务，因此是向量服务故障时的可靠降级路径。 */
@Component
public class Bm25Retriever implements Retriever {
    private final ElasticsearchClient client;
    private final VersionedIndexProperties indexProperties;
    private final RetrievalProperties properties;
    private final ElasticsearchAclFilter aclFilter;

    public Bm25Retriever(ElasticsearchClient client, VersionedIndexProperties indexProperties,
                         RetrievalProperties properties, ElasticsearchAclFilter aclFilter) {
        this.client = client;
        this.indexProperties = indexProperties;
        this.properties = properties;
        this.aclFilter = aclFilter;
    }

    @Override public String name() { return "bm25"; }

    @Override
    public RetrievalResult retrieve(RetrievalContext context) {
        if (!properties.isBm25Enabled()) return RetrievalResult.disabled(name(), "bm25 已由配置关闭");
        long startedAt = System.nanoTime();
        try {
            int recallK = Math.max(context.topK(), context.topK() * properties.getRecallMultiplier());
            SearchResponse<IndexDocument> response = client.search(search -> search
                    .index(indexProperties.getName())
                    .query(query -> query.bool(bool -> bool
                            .must(must -> must.match(match -> match.field("content").query(context.query())))
                            .filter(aclFilter.authorizedChunks(context))))
                    .size(recallK), IndexDocument.class);
            return new RetrievalResult(name(), toCandidates(response.hits().hits()), elapsedMs(startedAt), false, "");
        } catch (Exception exception) {
            // BM25 故障不抛到控制器，调用方仍可使用其他独立召回路并记录失败原因。
            return RetrievalResult.degraded(name(), elapsedMs(startedAt), "BM25 查询失败: " + exception.getClass().getSimpleName());
        }
    }

    private List<RetrievalCandidate> toCandidates(List<Hit<IndexDocument>> hits) {
        java.util.ArrayList<RetrievalCandidate> candidates = new java.util.ArrayList<>();
        for (int index = 0; index < hits.size(); index++) {
            Hit<IndexDocument> hit = hits.get(index);
            if (hit.source() != null) candidates.add(candidate(hit.source(), hit.id(), index + 1, hit.score()));
        }
        return candidates;
    }

    static RetrievalCandidate candidate(IndexDocument doc, String indexId, int rank, Double score) {
        return new RetrievalCandidate("bm25", rank, score == null ? 0D : score, indexId, doc.versionId(),
                doc.documentId(), doc.chunkId(), doc.chunkType(), doc.parentChunkId(), doc.content(), doc.contentHash(),
                doc.pageStart(), doc.pageEnd(), doc.ownerUserId(), doc.orgTag(), doc.isPublic(), doc.stockCode(), doc.fiscalYear(), doc.reportType());
    }

    private long elapsedMs(long startedAt) { return (System.nanoTime() - startedAt) / 1_000_000; }
}

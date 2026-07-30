package com.yizhaoqi.smartpai.retrieval;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.yizhaoqi.smartpai.client.EmbeddingClient;
import com.yizhaoqi.smartpai.config.RetrievalProperties;
import com.yizhaoqi.smartpai.config.VersionedIndexProperties;
import com.yizhaoqi.smartpai.index.IndexDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * KNN 语义召回。Embedding 或 ES 调用失败时返回 degraded 结果，绝不影响 BM25 路。
 */
@Component
public class VectorRetriever implements Retriever {
    private final ElasticsearchClient client;
    private final EmbeddingClient embeddingClient;
    private final VersionedIndexProperties indexProperties;
    private final RetrievalProperties properties;
    private final ElasticsearchAclFilter aclFilter;

    public VectorRetriever(ElasticsearchClient client, EmbeddingClient embeddingClient,
                           VersionedIndexProperties indexProperties, RetrievalProperties properties,
                           ElasticsearchAclFilter aclFilter) {
        this.client = client; this.embeddingClient = embeddingClient; this.indexProperties = indexProperties;
        this.properties = properties; this.aclFilter = aclFilter;
    }

    @Override public String name() { return "vector"; }

    @Override
    public RetrievalResult retrieve(RetrievalContext context) {
        if (!properties.isVectorEnabled()) return RetrievalResult.disabled(name(), "vector 已由配置关闭");
        long startedAt = System.nanoTime();
        try {
            List<float[]> vectors = embeddingClient.embed(List.of(context.query()));
            if (vectors.size() != 1 || vectors.get(0).length != indexProperties.getVectorDimension()) {
                return RetrievalResult.degraded(name(), elapsedMs(startedAt), "Embedding 响应为空或维度不匹配");
            }
            List<Float> queryVector = new ArrayList<>(vectors.get(0).length);
            for (float value : vectors.get(0)) queryVector.add(value);
            int recallK = Math.max(context.topK(), context.topK() * properties.getRecallMultiplier());
            SearchResponse<IndexDocument> response = client.search(search -> search.index(indexProperties.getName())
                    // ACL 必须置于 KNN filter 内，使无权文档不参与最近邻候选竞争。
                    .knn(knn -> knn.field("vector").queryVector(queryVector).k(recallK)
                            .numCandidates(Math.max(recallK * 3, 50)).filter(aclFilter.authorizedChunks(context)))
                    .size(recallK), IndexDocument.class);
            return new RetrievalResult(name(), toCandidates(response.hits().hits()), elapsedMs(startedAt), false, "");
        } catch (Exception exception) {
            return RetrievalResult.degraded(name(), elapsedMs(startedAt), "Vector 召回降级: " + exception.getClass().getSimpleName());
        }
    }

    private List<RetrievalCandidate> toCandidates(List<Hit<IndexDocument>> hits) {
        List<RetrievalCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < hits.size(); index++) {
            Hit<IndexDocument> hit = hits.get(index);
            IndexDocument doc = hit.source();
            if (doc != null) candidates.add(new RetrievalCandidate(name(), index + 1, hit.score() == null ? 0D : hit.score(),
                    hit.id(), doc.versionId(), doc.documentId(), doc.chunkId(), doc.chunkType(), doc.parentChunkId(),
                    doc.content(), doc.contentHash(), doc.pageStart(), doc.pageEnd(), doc.ownerUserId(), doc.orgTag(), doc.isPublic(),
                    doc.stockCode(), doc.fiscalYear(), doc.reportType()));
        }
        return candidates;
    }

    private long elapsedMs(long startedAt) { return (System.nanoTime() - startedAt) / 1_000_000; }
}

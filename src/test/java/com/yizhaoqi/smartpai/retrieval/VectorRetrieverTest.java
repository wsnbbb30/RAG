package com.yizhaoqi.smartpai.retrieval;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.yizhaoqi.smartpai.client.EmbeddingClient;
import com.yizhaoqi.smartpai.config.RetrievalProperties;
import com.yizhaoqi.smartpai.config.VersionedIndexProperties;
import com.yizhaoqi.smartpai.security.AccessScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证外部 Embedding 不可用时仅降级 Vector 路，不把异常扩散为整条检索失败。 */
class VectorRetrieverTest {
    @Test
    void degradesWhenEmbeddingProviderFails() {
        ElasticsearchClient client = mock(ElasticsearchClient.class);
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        when(embeddingClient.embed(org.mockito.ArgumentMatchers.anyList()))
                .thenThrow(new RuntimeException("provider unavailable"));

        RetrievalProperties properties = new RetrievalProperties();
        VectorRetriever retriever = new VectorRetriever(client, embeddingClient, new VersionedIndexProperties(),
                properties, new ElasticsearchAclFilter());

        RetrievalResult result = retriever.retrieve(new RetrievalContext("贵州茅台营业收入", AccessScope.anonymous(), 5, "test"));

        assertTrue(result.degraded());
        assertTrue(result.candidates().isEmpty());
    }
}

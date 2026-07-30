package com.yizhaoqi.smartpai.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * 初始化 S1-04 的新物理索引；绝不修改或复用旧 knowledge_base，避免历史 schema 污染。
 */
@Component
@ConditionalOnProperty(name = "elasticsearch.versioned-index.auto-init", havingValue = "true", matchIfMissing = true)
public class VersionedEsIndexInitializer implements CommandLineRunner {
    private final ElasticsearchClient client;
    private final VersionedIndexProperties properties;

    public VersionedEsIndexInitializer(ElasticsearchClient client, VersionedIndexProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void run(String... args) throws Exception {
        BooleanResponse exists = client.indices().exists(ExistsRequest.of(request -> request.index(properties.getName())));
        if (exists.value()) return;

        // 映射中的 dense_vector 维度必须与配置一致；配置变更应创建新索引版本，而不是原地修改。
        if (properties.getVectorDimension() != 2048) {
            throw new IllegalStateException("rag_document_chunks_v1 的 mapping 固定为 2048 维，请创建新的索引版本");
        }
        try (Reader reader = new InputStreamReader(new ClassPathResource("es-mappings/rag_document_chunks_v1.json")
                .getInputStream(), StandardCharsets.UTF_8)) {
            client.indices().create(CreateIndexRequest.of(request -> request.index(properties.getName()).withJson(reader)));
        }
    }
}

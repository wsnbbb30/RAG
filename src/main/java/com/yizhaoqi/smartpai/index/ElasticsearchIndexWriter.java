package com.yizhaoqi.smartpai.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.yizhaoqi.smartpai.config.VersionedIndexProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/** Elasticsearch 对 IndexWriter 的实现，严格检查 Bulk 每一项错误，不允许“部分成功”被当成完成。 */
@Component
public class ElasticsearchIndexWriter implements IndexWriter {
    private final ElasticsearchClient client;
    private final VersionedIndexProperties properties;

    public ElasticsearchIndexWriter(ElasticsearchClient client, VersionedIndexProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void upsert(List<IndexDocument> documents) {
        if (documents.isEmpty()) return;
        List<BulkOperation> operations = documents.stream().map(document -> BulkOperation.of(operation ->
                operation.index(index -> index.index(properties.getName()).id(document.id()).document(document)))).toList();
        try {
            BulkResponse response = client.bulk(BulkRequest.of(request -> request.operations(operations)));
            if (response.errors()) {
                String details = response.items().stream().filter(item -> item.error() != null)
                        .map(this::formatError).reduce((left, right) -> left + "; " + right).orElse("未知 Bulk 错误");
                throw new IllegalStateException("Elasticsearch Bulk 写入部分失败: " + details);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Elasticsearch 索引写入失败", exception);
        }
    }

    @Override
    public void deleteByVersionId(Long versionId) {
        try {
            client.deleteByQuery(DeleteByQueryRequest.of(request -> request.index(properties.getName())
                    .query(query -> query.term(term -> term.field("versionId").value(versionId)))));
        } catch (Exception exception) {
            throw new IllegalStateException("删除版本索引失败，versionId=" + versionId, exception);
        }
    }

    private String formatError(BulkResponseItem item) {
        return "id=" + item.id() + ", reason=" + item.error().reason();
    }
}

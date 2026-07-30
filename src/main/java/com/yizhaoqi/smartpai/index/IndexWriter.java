package com.yizhaoqi.smartpai.index;

import java.util.List;

/** 索引写入端口。业务服务不直接依赖 ElasticsearchClient，便于单测与未来迁移索引存储。 */
public interface IndexWriter {
    void upsert(List<IndexDocument> documents);
    void deleteByVersionId(Long versionId);
}

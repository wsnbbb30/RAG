package com.yizhaoqi.smartpai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** S1-04 版本化 Elasticsearch 索引配置。物理索引升级时只新增名称，不覆盖旧索引。 */
@Data
@Component
@ConfigurationProperties(prefix = "elasticsearch.versioned-index")
public class VersionedIndexProperties {
    /** 新结构化 Chunk 的物理索引名。 */
    private String name = "rag_document_chunks_v1";
    /** 是否在应用启动时创建索引。 */
    private boolean autoInit = true;
    /** dense_vector 维度，必须与 embedding 服务配置一致。 */
    private int vectorDimension = 2048;
}

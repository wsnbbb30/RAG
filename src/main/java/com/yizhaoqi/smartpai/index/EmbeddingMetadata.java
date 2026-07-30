package com.yizhaoqi.smartpai.index;

/**
 * 每条索引文档携带的向量可追溯信息。
 * 模型、维度或 tokenizer 改变后，S4 可基于此字段选择重建范围。
 */
public record EmbeddingMetadata(String model, int dimension, String tokenizerId) {
    public EmbeddingMetadata {
        if (model == null || model.isBlank() || dimension <= 0 || tokenizerId == null || tokenizerId.isBlank()) {
            throw new IllegalArgumentException("Embedding 元数据不完整");
        }
    }
}

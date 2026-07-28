package com.yizhaoqi.smartpai.chunk;

/**
 * Token 计数 SPI。
 * S1-03 使用本地近似实现，S5-01 接入不同 Embedding/LLM Provider 后可替换为各模型的 tokenizer。
 */
public interface TokenCounter {
    /** 返回计数器版本，便于后续评测中追溯 token 统计口径。 */
    String tokenizerId();

    /** 返回文本 token 数。 */
    int count(String text);

    /** 返回不超过 maxTokens 的末尾文本，用于相邻正文块的语义重叠。 */
    String tail(String text, int maxTokens);
}

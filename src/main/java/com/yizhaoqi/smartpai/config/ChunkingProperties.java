package com.yizhaoqi.smartpai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * S1-03 切块策略的集中配置。
 * 修改任何影响边界的参数时，必须同时升级 chunkerVersion，避免新旧 Chunk 混用。
 */
@Data
@Component
@ConfigurationProperties(prefix = "document.chunking")
public class ChunkingProperties {
    /** 便于在异常时关闭新切块链路。 */
    private boolean enabled = true;
    /** 策略版本，会写入 DocumentVersion 和每条 DocumentChunk。 */
    private String chunkerVersion = "structure-aware-v1";
    /** 普通正文块的推荐下限；允许末尾块低于此值。 */
    private int minTokens = 350;
    /** 普通正文块的硬上限。 */
    private int maxTokens = 600;
    /** 相邻正文块的尾部重叠 token 数。 */
    private int overlapTokens = 80;
    /** 单一元素超过该值时，后续可按句子进一步拆分；当前保留原子性。 */
    private int oversizedElementTokens = 800;
}

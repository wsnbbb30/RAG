package com.yizhaoqi.smartpai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** S2-03 精排 Provider 的可回滚配置；默认关闭，只有评测证明收益后再开启。 */
@Data
@Component
@ConfigurationProperties(prefix = "rerank")
public class RerankProperties {
    private boolean enabled = false;
    /** OpenAI 风格 rerank 服务地址，例如 https://provider.example/v1/rerank。 */
    private String url;
    private String apiKey;
    private String model = "rerank-v1";
    /** RRF 后进入精排的最大候选数。 */
    private int candidateSize = 50;
    private long timeoutMs = 2500L;
    /** 连续失败达到该阈值后打开熔断器。 */
    private int failureThreshold = 3;
    private long circuitOpenMs = 60000L;
}

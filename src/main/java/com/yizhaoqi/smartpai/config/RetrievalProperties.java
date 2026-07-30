package com.yizhaoqi.smartpai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * S2-01 检索阶段的运行参数。
 *
 * <p>开关与超时按召回路独立配置：某一条外部依赖异常时，只降级该路，
 * 不允许为了“有结果”而绕过 ACL 或悄悄切换到旧索引。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "retrieval")
public class RetrievalProperties {
    /** BM25 词法召回开关。 */
    private boolean bm25Enabled = true;
    /** 向量召回开关。 */
    private boolean vectorEnabled = true;
    /** S3 财务事实入库前保持关闭；接口仍保留以稳定调用方。 */
    private boolean factEnabled = false;
    /** 每一路的候选池大小；S2-02 再由 RRF 融合。 */
    private int recallMultiplier = 3;
    /** 单路最大等待时间；超时只降级该路，避免外部 Embedding 拖慢 BM25。 */
    private long routeTimeoutMs = 3000L;
    /** RRF 常量；仅影响 rank 融合的衰减速度，不与任何模型分数耦合。 */
    private int rrfK = 60;
}

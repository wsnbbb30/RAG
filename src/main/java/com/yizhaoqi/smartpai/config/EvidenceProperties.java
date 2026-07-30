package com.yizhaoqi.smartpai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** S2-04 证据上下文预算；所有阈值可由开发集标定，禁止在业务代码中写死。 */
@Data
@Component
@ConfigurationProperties(prefix = "rag.evidence")
public class EvidenceProperties {
    /** 给检索证据分配的最大近似 token 数，不包含模型输出预算。 */
    private int maxContextTokens = 3200;
    /** 至少需要多少条有效证据才允许调用生成模型。 */
    private int minEvidenceCount = 1;
    /** 过短文本通常是页眉/页脚或解析噪声，不作为可回答证据。 */
    private int minEvidenceCharacters = 20;
}

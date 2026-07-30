package com.yizhaoqi.smartpai.rag;

/** 回答证据状态；由服务规则判定，模型不能自行把无证据内容标记为“已验证”。 */
public enum AnswerStatus {
    VERIFIED,
    SUPPORTED,
    PARTIAL,
    INSUFFICIENT_EVIDENCE,
    CONFLICTING_EVIDENCE
}

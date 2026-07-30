package com.yizhaoqi.smartpai.rag;

/** 冻结给模型的最小证据单元；页面与版本信息使引用可回到原始年报。 */
public record Evidence(String citationId, Long versionId, Long chunkId, int pageStart, int pageEnd,
                       String documentId, String content) { }

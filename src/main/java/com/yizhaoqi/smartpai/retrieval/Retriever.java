package com.yizhaoqi.smartpai.retrieval;

/** 独立召回路端口；实现不得接受裸 userId，以强制统一 ACL 语义。 */
public interface Retriever {
    String name();
    RetrievalResult retrieve(RetrievalContext context);
}

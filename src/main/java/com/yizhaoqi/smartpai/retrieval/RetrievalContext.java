package com.yizhaoqi.smartpai.retrieval;

import com.yizhaoqi.smartpai.security.AccessScope;

import java.util.Objects;
import java.util.UUID;

/**
 * 单次检索的不可变输入。
 *
 * <p>AccessScope 不能为 null：匿名请求也必须传入 AccessScope.anonymous()，
 * 以保证所有 Retriever 都 fail-closed，而不是依赖调用方“记得加 ACL”。</p>
 */
public record RetrievalContext(String query, AccessScope accessScope, int topK, String traceId, QueryFilter filters) {
    /** 保持 S2-01 调用方兼容；没有显式过滤条件时使用空过滤器。 */
    public RetrievalContext(String query, AccessScope accessScope, int topK, String traceId) {
        this(query, accessScope, topK, traceId, QueryFilter.empty());
    }
    public RetrievalContext {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("检索问题不能为空");
        }
        Objects.requireNonNull(accessScope, "accessScope 不能为空；匿名用户请使用 AccessScope.anonymous()");
        if (topK < 1 || topK > 100) {
            throw new IllegalArgumentException("topK 必须在 1 到 100 之间");
        }
        traceId = (traceId == null || traceId.isBlank()) ? UUID.randomUUID().toString() : traceId;
        filters = filters == null ? QueryFilter.empty() : filters;
    }
}

package com.yizhaoqi.smartpai.retrieval;

import org.springframework.stereotype.Component;

/**
 * 证据去重键策略。
 *
 * <p>优先 versionId + chunkId（同一版本中的稳定证据主键），其次 contentHash，最后 ES indexId。
 * 同一文本在不同页可能具有不同定位证据，因此不能只按正文内容做去重。</p>
 * 
 * 去重策略：
 * 1.如果有versionId + chunkId，则按文档版本中的块去重
 * 2.否则如果有contentHash，则按内容哈希去重
 * 3.否则按indexId兜底，按索引位置去重
 */
@Component
public class CandidateDeduplicator {
    public String keyOf(RetrievalCandidate candidate) {
        if (candidate.versionId() != null && candidate.chunkId() != null) {
            return "chunk:" + candidate.versionId() + ":" + candidate.chunkId();
        }
        if (candidate.contentHash() != null && !candidate.contentHash().isBlank()) return "hash:" + candidate.contentHash();
        return "index:" + candidate.indexId();
    }
}

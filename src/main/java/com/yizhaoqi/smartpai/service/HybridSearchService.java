package com.yizhaoqi.smartpai.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.yizhaoqi.smartpai.client.EmbeddingClient;
import com.yizhaoqi.smartpai.entity.EsDocument;
import com.yizhaoqi.smartpai.entity.SearchResult;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.model.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 混合搜索服务，结合文本匹配和向量相似度搜索。
 * 所有搜索路径必须经过统一的 ACL 过滤。
 */
@Service
public class HybridSearchService {

    private static final Logger logger = LoggerFactory.getLogger(HybridSearchService.class);

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrgTagCacheService orgTagCacheService;

    @Autowired
    private FileUploadRepository fileUploadRepository;

    /**
     * 带权限的混合搜索。userId 为 null 时仅返回公开文档。
     */
    public List<SearchResult> searchWithPermission(String query, String userId, int topK) {
        logger.debug("带权限搜索, query: {}, userId: {}", query, userId);

        try {
            String userDbId = resolveUserDbId(userId);
            List<String> effectiveTags = resolveEffectiveTags(userId);

            final List<Float> queryVector = embedToVectorList(query);

            if (queryVector == null) {
                logger.warn("向量生成失败，降级为纯文本搜索");
                return textOnlySearch(query, userDbId, effectiveTags, topK);
            }

            int recallK = topK * 30;

            SearchResponse<EsDocument> response = esClient.search(s -> {
                s.index("knowledge_base");

                // KNN 召回，内嵌 ACL filter（防止无权限文档参与向量召回竞争）
                s.knn(kn -> kn
                        .field("vector")
                        .queryVector(queryVector)
                        .k(recallK)
                        .numCandidates(recallK)
                        .filter(f -> f.bool(b -> buildAclBoolQuery(b, userDbId, effectiveTags)))
                );

                // 文本匹配 + ACL 后置过滤
                s.query(q -> q.bool(b -> b
                        .must(mst -> mst.match(m -> m.field("textContent").query(query)))
                        .filter(f -> f.bool(bf -> buildAclBoolQuery(bf, userDbId, effectiveTags)))
                ));

                s.rescore(r -> r
                        .windowSize(recallK)
                        .query(rq -> rq
                                .queryWeight(0.2d)
                                .rescoreQueryWeight(1.0d)
                                .query(rqq -> rqq.match(m -> m
                                        .field("textContent")
                                        .query(query)
                                        .operator(Operator.And)
                                ))
                        )
                );
                s.size(topK);
                return s;
            }, EsDocument.class);

            List<SearchResult> results = mapResults(response);
            attachFileNames(results);
            return results;
        } catch (Exception e) {
            logger.error("搜索失败", e);
            try {
                return textOnlySearch(query, resolveUserDbId(userId), resolveEffectiveTags(userId), topK);
            } catch (Exception fallbackError) {
                logger.error("后备搜索也失败", fallbackError);
                return Collections.emptyList();
            }
        }
    }

    /**
     * ACL bool 查询构建（所有检索路径共用）：
     * userId 匹配 OR isPublic=true OR orgTag 匹配，至少满足一项
     */
    private co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder buildAclBoolQuery(
            co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder b,
            String userDbId, List<String> effectiveTags) {
        if (userDbId != null) {
            b.should(s1 -> s1.term(t -> t.field("userId").value(userDbId)));
        }
        b.should(s2 -> s2.term(t -> t.field("isPublic").value(true)));
        if (effectiveTags != null && !effectiveTags.isEmpty()) {
            if (effectiveTags.size() == 1) {
                b.should(s3 -> s3.term(t -> t.field("orgTag").value(effectiveTags.get(0))));
            } else {
                b.should(s3 -> s3.bool(inner -> {
                    effectiveTags.forEach(tag -> inner.should(sh -> sh.term(t -> t.field("orgTag").value(tag))));
                    return inner;
                }));
            }
        }
        b.minimumShouldMatch("1");
        return b;
    }

    private List<SearchResult> textOnlySearch(String query, String userDbId,
                                               List<String> userEffectiveTags, int topK) {
        try {
            SearchResponse<EsDocument> response = esClient.search(s -> s
                    .index("knowledge_base")
                    .query(q -> q.bool(b -> b
                            .must(m -> m.match(ma -> ma.field("textContent").query(query)))
                            .filter(f -> f.bool(bf -> buildAclBoolQuery(bf, userDbId, userEffectiveTags)))
                    ))
                    .minScore(0.3d)
                    .size(topK),
                    EsDocument.class
            );

            List<SearchResult> results = mapResults(response);
            attachFileNames(results);
            return results;
        } catch (Exception e) {
            logger.error("纯文本搜索失败", e);
            return new ArrayList<>();
        }
    }

    private List<String> resolveEffectiveTags(String userId) {
        if (userId == null) return Collections.emptyList();
        try {
            User user = findUser(userId);
            return orgTagCacheService.getUserEffectiveOrgTags(user.getUsername());
        } catch (Exception e) {
            logger.error("获取用户有效组织标签失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String resolveUserDbId(String userId) {
        if (userId == null) return null;
        try {
            return findUser(userId).getId().toString();
        } catch (Exception e) {
            logger.error("获取用户数据库ID失败: {}", e.getMessage());
            return null;
        }
    }

    private User findUser(String userId) {
        try {
            Long userIdLong = Long.parseLong(userId);
            return userRepository.findById(userIdLong)
                    .orElseThrow(() -> new CustomException("User not found: " + userId, HttpStatus.NOT_FOUND));
        } catch (NumberFormatException e) {
            return userRepository.findByUsername(userId)
                    .orElseThrow(() -> new CustomException("User not found: " + userId, HttpStatus.NOT_FOUND));
        }
    }

    private List<Float> embedToVectorList(String text) {
        try {
            List<float[]> vecs = embeddingClient.embed(List.of(text));
            if (vecs == null || vecs.isEmpty()) return null;
            float[] raw = vecs.get(0);
            List<Float> list = new ArrayList<>(raw.length);
            for (float v : raw) list.add(v);
            return list;
        } catch (Exception e) {
            logger.error("生成向量失败", e);
            return null;
        }
    }

    private List<SearchResult> mapResults(SearchResponse<EsDocument> response) {
        return response.hits().hits().stream()
                .map(hit -> {
                    assert hit.source() != null;
                    return new SearchResult(
                            hit.source().getFileMd5(),
                            hit.source().getChunkId(),
                            hit.source().getTextContent(),
                            hit.score(),
                            hit.source().getUserId(),
                            hit.source().getOrgTag(),
                            hit.source().isPublic()
                    );
                })
                .toList();
    }

    private void attachFileNames(List<SearchResult> results) {
        if (results == null || results.isEmpty()) return;
        try {
            Set<String> md5Set = results.stream()
                    .map(SearchResult::getFileMd5)
                    .collect(Collectors.toSet());
            List<FileUpload> uploads = fileUploadRepository.findByFileMd5In(new ArrayList<>(md5Set));
            Map<String, String> md5ToName = uploads.stream()
                    .collect(Collectors.toMap(FileUpload::getFileMd5, FileUpload::getFileName));
            results.forEach(r -> r.setFileName(md5ToName.get(r.getFileMd5())));
        } catch (Exception e) {
            logger.error("补充文件名失败", e);
        }
    }
}

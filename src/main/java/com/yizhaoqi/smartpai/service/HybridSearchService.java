package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.entity.SearchResult;
import com.yizhaoqi.smartpai.config.RetrievalProperties;
import com.yizhaoqi.smartpai.config.RerankProperties;
import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.retrieval.FinancialFactRetriever;
import com.yizhaoqi.smartpai.retrieval.RetrievalContext;
import com.yizhaoqi.smartpai.retrieval.RetrievalResult;
import com.yizhaoqi.smartpai.retrieval.FusedCandidate;
import com.yizhaoqi.smartpai.retrieval.FusionStrategy;
import com.yizhaoqi.smartpai.retrieval.QueryFilter;
import com.yizhaoqi.smartpai.retrieval.QueryFilterExtractor;
import com.yizhaoqi.smartpai.retrieval.Bm25Retriever;
import com.yizhaoqi.smartpai.retrieval.VectorRetriever;
import com.yizhaoqi.smartpai.rerank.RerankResult;
import com.yizhaoqi.smartpai.rerank.RerankedCandidate;
import com.yizhaoqi.smartpai.rerank.RerankerRouter;
import com.yizhaoqi.smartpai.security.AccessScope;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * S2-01 检索编排门面。
 *
 * <p>该类不再构造任何 ES DSL，也不再把 BM25 与 KNN 混写在一个 ES 请求中；
 * 每条召回路有独立实现、独立耗时与独立降级信息。S2-02 会在本类之后加入 RRF，
 * 当前阶段仅合并输出以保持旧接口可用，严禁把不同召回路的 rawScore 当作同一量纲排序。</p>
 */
@Service
public class HybridSearchService {
    private final Bm25Retriever bm25Retriever;
    private final VectorRetriever vectorRetriever;
    private final FinancialFactRetriever factRetriever;
    private final UserRepository userRepository;
    private final OrgTagCacheService orgTagCacheService;
    private final RetrievalProperties retrievalProperties;
    private final Executor retrievalExecutor;
    private final QueryFilterExtractor queryFilterExtractor;
    private final FusionStrategy fusionStrategy;
    private final RerankerRouter rerankerRouter;
    private final RerankProperties rerankProperties;

    public HybridSearchService(Bm25Retriever bm25Retriever, VectorRetriever vectorRetriever,
                               FinancialFactRetriever factRetriever, UserRepository userRepository,
                               OrgTagCacheService orgTagCacheService, RetrievalProperties retrievalProperties,
                               @Qualifier("retrievalExecutor") Executor retrievalExecutor,
                               QueryFilterExtractor queryFilterExtractor, FusionStrategy fusionStrategy,
                               RerankerRouter rerankerRouter, RerankProperties rerankProperties) {
        this.bm25Retriever = bm25Retriever;
        this.vectorRetriever = vectorRetriever;
        this.factRetriever = factRetriever;
        this.userRepository = userRepository;
        this.orgTagCacheService = orgTagCacheService;
        this.retrievalProperties = retrievalProperties;
        this.retrievalExecutor = retrievalExecutor;
        this.queryFilterExtractor = queryFilterExtractor;
        this.fusionStrategy = fusionStrategy;
        this.rerankerRouter = rerankerRouter;
        this.rerankProperties = rerankProperties;
    }

    /** 供 REST 接口使用的详细结果，包含每一路是否降级与耗时。 */
    public RetrievalResponse retrieveWithPermission(String query, String userId, int topK) {
        AccessScope scope = resolveAccessScope(userId);
        QueryFilter filters = queryFilterExtractor.extract(query);
        // Retriever 先召回足够候选，最终响应数仍由 rerank 的 topK 控制。
        RetrievalContext context = new RetrievalContext(query, scope, Math.max(topK, rerankProperties.getCandidateSize()), null, filters);
        // 先同时提交三路，再按固定顺序收集；CompletableFuture 的超时结果由 routeFuture 显式标注。
        CompletableFuture<RetrievalResult> bm25Future = routeFuture(bm25Retriever, context);
        CompletableFuture<RetrievalResult> vectorFuture = routeFuture(vectorRetriever, context);
        CompletableFuture<RetrievalResult> factFuture = routeFuture(factRetriever, context);
        List<RetrievalResult> routes = List.of(bm25Future.join(), vectorFuture.join(), factFuture.join());

        // 精排候选池与最终 TopK 分离，避免只给模型极少候选而失去精排价值。
        List<FusedCandidate> fused = fusionStrategy.fuse(routes, Math.max(topK, rerankProperties.getCandidateSize()));
        RerankResult rerank = rerankerRouter.rerank(query, fused, topK);
        return new RetrievalResponse(context.traceId(), filters, rerank.candidates(), routes, rerank);
    }

    private CompletableFuture<RetrievalResult> routeFuture(com.yizhaoqi.smartpai.retrieval.Retriever retriever,
                                                             RetrievalContext context) {
        return CompletableFuture.supplyAsync(() -> retriever.retrieve(context), retrievalExecutor)
                .completeOnTimeout(RetrievalResult.degraded(retriever.name(), retrievalProperties.getRouteTimeoutMs(),
                        retriever.name() + " 召回超时"), retrievalProperties.getRouteTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .exceptionally(exception -> RetrievalResult.degraded(retriever.name(), 0L,
                        retriever.name() + " 召回异常: " + exception.getClass().getSimpleName()));
    }

    /**
     * 兼容既有 ChatHandler 的轻量结果接口。
     * 新索引不再以 fileMd5 作为检索主键，因此旧字段暂填 documentId；S2-04 会切换为证据 DTO。
     */
    public List<SearchResult> searchWithPermission(String query, String userId, int topK) {
        return retrieveWithPermission(query, userId, topK).candidates().stream()
                .map(reranked -> reranked.candidate().candidate())
                .map(candidate -> new SearchResult(candidate.documentId(), Math.toIntExact(candidate.chunkId()),
                        candidate.content(), candidate.rawScore(), candidate.ownerUserId(), candidate.orgTag(), candidate.isPublic()))
                .toList();
    }

    private AccessScope resolveAccessScope(String userId) {
        if (userId == null || userId.isBlank()) return AccessScope.anonymous();
        User user = findUser(userId);
        List<String> tags = orgTagCacheService.getUserEffectiveOrgTags(user.getUsername());
        return AccessScope.authenticated(user.getId().toString(), tags == null ? List.of() : tags);
    }

    private User findUser(String userId) {
        try {
            return userRepository.findById(Long.parseLong(userId))
                    .orElseThrow(() -> new CustomException("用户不存在: " + userId, HttpStatus.NOT_FOUND));
        } catch (NumberFormatException ignored) {
            return userRepository.findByUsername(userId)
                    .orElseThrow(() -> new CustomException("用户不存在: " + userId, HttpStatus.NOT_FOUND));
        }
    }

    /** REST 层直接返回的可观测、可解释检索协议。 */
    public record RetrievalResponse(String traceId, QueryFilter filters, List<RerankedCandidate> candidates,
                                    List<RetrievalResult> routes, RerankResult rerank) {
        public RetrievalResponse {
            candidates = List.copyOf(candidates);
            routes = List.copyOf(routes);
        }
        public boolean degraded() { return routes.stream().anyMatch(RetrievalResult::degraded) || rerank.degraded(); }
    }
}

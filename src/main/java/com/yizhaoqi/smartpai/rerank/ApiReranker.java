package com.yizhaoqi.smartpai.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.config.RerankProperties;
import com.yizhaoqi.smartpai.retrieval.FusedCandidate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP 精排 Provider。
 *
 * <p>采用常见 JSON 契约：请求为 {model, query, documents}，响应 data 中每项包含
 * {index, relevance_score}。Provider 异常、429、超时、空响应、非法 index 都会回退 RRF；
 * 不允许因精排失败而丢弃已授权证据。</p>
 */
@Component
public class ApiReranker implements Reranker {
    private final RerankProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong circuitOpenUntilMs = new AtomicLong(0L);

    public ApiReranker(RerankProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().build();
    }

    /**
     * 对检索候选集进行精排重排序
     *
     * @param query      用户查询
     * @param candidates 融合后的候选列表（已合并多路召回结果）
     * @param topN       需要返回的最大数量
     * @return 精排结果（包含排序后的候选 + 降级标记 + 耗时等元信息）
     */
    @Override
    public RerankResult rerank(String query, List<FusedCandidate> candidates, int topN) {
        long startedAt = System.nanoTime();
        if (!isReady()) return fallback(candidates, topN, elapsedMs(startedAt), "rerank 服务未配置或熔断中");
        try {
            List<FusedCandidate> batch = candidates.subList(0, Math.min(candidates.size(), properties.getCandidateSize()));
            //调用外部精排API
            String response = webClient.post().uri(properties.getUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request(query, batch))
                    .retrieve().bodyToMono(String.class)
                    .block(Duration.ofMillis(properties.getTimeoutMs()));
            List<ScoredIndex> scores = parse(response, batch.size());
            if (scores.isEmpty()) throw new IllegalStateException("精排响应为空");
            consecutiveFailures.set(0);
            List<RerankedCandidate> reranked = scores.stream()
                    .sorted(Comparator.comparingDouble(ScoredIndex::score).reversed().thenComparingInt(ScoredIndex::index))
                    .limit(topN)
                    .map(item -> new RerankedCandidate(batch.get(item.index()), 0, item.score()))
                    .toList();
            List<RerankedCandidate> ranked = new ArrayList<>();
            for (int index = 0; index < reranked.size(); index++) {
                RerankedCandidate item = reranked.get(index);
                ranked.add(new RerankedCandidate(item.candidate(), index + 1, item.rerankScore()));
            }
            return new RerankResult(ranked, properties.getModel(), elapsedMs(startedAt), true, false, "");
        } catch (Exception exception) {
            recordFailure();
            return fallback(candidates, topN, elapsedMs(startedAt), "API 精排降级: " + exception.getClass().getSimpleName());
        }
    }

    private boolean isReady() {
        return properties.isEnabled() && properties.getUrl() != null && !properties.getUrl().isBlank()
                && properties.getApiKey() != null && !properties.getApiKey().isBlank()
                && System.currentTimeMillis() >= circuitOpenUntilMs.get();
    }

    private Map<String, Object> request(String query, List<FusedCandidate> candidates) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", properties.getModel());
        body.put("query", query);
        // 只传正文；版本、页码与 ACL 保留在服务端，避免外部 Provider 接触不必要的权限元数据。
        body.put("documents", candidates.stream().map(item -> item.candidate().content()).toList());
        return body;
    }

    private List<ScoredIndex> parse(String response, int documentCount) throws Exception {
        JsonNode data = objectMapper.readTree(response).path("data");
        if (!data.isArray()) throw new IllegalStateException("精排响应缺少 data 数组");
        List<ScoredIndex> scores = new ArrayList<>();
        for (JsonNode item : data) {
            int index = item.path("index").asInt(-1);
            JsonNode score = item.path("relevance_score");
            if (index < 0 || index >= documentCount || !score.isNumber()) {
                throw new IllegalStateException("精排响应含非法 index 或 score");
            }
            scores.add(new ScoredIndex(index, score.asDouble()));
        }
        return scores;
    }

    private void recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= properties.getFailureThreshold()) {
            circuitOpenUntilMs.set(System.currentTimeMillis() + properties.getCircuitOpenMs());
            consecutiveFailures.set(0);
        }
    }

    private RerankResult fallback(List<FusedCandidate> candidates, int topN, long latencyMs, String reason) {
        List<RerankedCandidate> fallback = new ArrayList<>();
        for (int index = 0; index < Math.min(topN, candidates.size()); index++) {
            fallback.add(new RerankedCandidate(candidates.get(index), index + 1, null));
        }
        return new RerankResult(fallback, properties.getModel(), latencyMs, false, true, reason);
    }

    private long elapsedMs(long startedAt) { return (System.nanoTime() - startedAt) / 1_000_000; }
    private record ScoredIndex(int index, double score) { }
}

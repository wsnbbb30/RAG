package com.yizhaoqi.smartpai.retrieval;

import com.yizhaoqi.smartpai.config.RetrievalProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion 实现。
 *
 * <p>不同路的 BM25、余弦相似度、事实匹配分数不可直接比较，RRF 只使用每路 rank：
 * score(d) = Σ 1 / (k + rank_r(d))。相同证据跨路命中时累积贡献，但最终只占一个位置。</p>
 */
@Component
public class RrfFusionStrategy implements FusionStrategy {
    private final RetrievalProperties properties;
    private final CandidateDeduplicator deduplicator;

    public RrfFusionStrategy(RetrievalProperties properties, CandidateDeduplicator deduplicator) {
        this.properties = properties;
        this.deduplicator = deduplicator;
    }

    @Override
    public List<FusedCandidate> fuse(List<RetrievalResult> routes, int topK) {
        Map<String, Aggregate> aggregates = new LinkedHashMap<>();
        for (RetrievalResult route : routes) {
            // 降级/关闭路的空候选不影响其他路，也不参与分母或补分。
            for (RetrievalCandidate candidate : route.candidates()) {
                String key = deduplicator.keyOf(candidate);
                Aggregate aggregate = aggregates.computeIfAbsent(key, ignored -> new Aggregate(candidate));
                double contribution = 1D / (properties.getRrfK() + candidate.rank());
                aggregate.add(candidate, contribution);
            }
        }
        return aggregates.values().stream()
                .map(Aggregate::toResult)
                // 分数相同也按稳定证据键排序，保证离线评测可复现。
                .sorted(Comparator.comparingDouble(FusedCandidate::fusedScore).reversed()
                        .thenComparing(item -> deduplicator.keyOf(item.candidate())))
                .limit(topK)
                .toList();
    }

    private static final class Aggregate {
        private RetrievalCandidate representative;
        private double score;
        private final List<FusedCandidate.RouteContribution> contributions = new ArrayList<>();

        private Aggregate(RetrievalCandidate candidate) { this.representative = candidate; }

        private void add(RetrievalCandidate candidate, double contribution) {
            // 选择 rank 更靠前的版本作为正文/页码代表，其他路信息仍完整保留在 contributions。
            if (candidate.rank() < representative.rank()) representative = candidate;
            score += contribution;
            contributions.add(new FusedCandidate.RouteContribution(candidate.source(), candidate.rank(),
                    candidate.rawScore(), contribution));
        }

        private FusedCandidate toResult() { return new FusedCandidate(representative, score, contributions); }
    }
}

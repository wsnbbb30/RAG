package com.yizhaoqi.smartpai.eval;

import com.yizhaoqi.smartpai.client.DeepSeekClient;
import com.yizhaoqi.smartpai.eval.model.EvalRetrievalResult;
import com.yizhaoqi.smartpai.eval.model.EvalRunReport;
import com.yizhaoqi.smartpai.eval.model.EvalRunReport.BadCase;
import com.yizhaoqi.smartpai.eval.model.EvalRunReport.PassedCase;
import com.yizhaoqi.smartpai.eval.model.EvalRunReport.TypeMetrics;
import com.yizhaoqi.smartpai.eval.model.EvaluationCase;
import com.yizhaoqi.smartpai.retrieval.Bm25Retriever;
import com.yizhaoqi.smartpai.retrieval.QueryFilter;
import com.yizhaoqi.smartpai.retrieval.QueryFilterExtractor;
import com.yizhaoqi.smartpai.retrieval.RetrievalCandidate;
import com.yizhaoqi.smartpai.retrieval.RetrievalContext;
import com.yizhaoqi.smartpai.retrieval.RetrievalResult;
import com.yizhaoqi.smartpai.retrieval.VectorRetriever;
import com.yizhaoqi.smartpai.security.AccessScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FinAR-Bench 检索评测编排器。
 *
 * <p>遍历所有 EvaluationCase，调用 BM25 + Vector 双路检索（FinAR-Bench 数据跨多年，
 * 清除财年过滤；stockCode 从 EvaluationCase 中注入），
 * 将候选与 ground truth 比对，产出汇总报告。</p>
 */
@Component
public class EvaluationRunner {

    private static final Logger log = LoggerFactory.getLogger(EvaluationRunner.class);
    private static final boolean DEBUG_EVAL = true;

    private final Bm25Retriever bm25Retriever;
    private final VectorRetriever vectorRetriever;
    private final QueryFilterExtractor filterExtractor;
    private final RetrievalEvaluator evaluator;

    public EvaluationRunner(Bm25Retriever bm25Retriever, VectorRetriever vectorRetriever,
                            QueryFilterExtractor filterExtractor, DeepSeekClient deepSeekClient) {
        this.bm25Retriever = bm25Retriever;
        this.vectorRetriever = vectorRetriever;
        this.filterExtractor = filterExtractor;
        this.evaluator = new RetrievalEvaluator(new IndicatorComputer(deepSeekClient));
    }

    /**
     * 执行评测。
     *
     * @param cases 评测用例列表
     * @param topK  最终返回的候选数（会传入检索上下文）
     * @return 汇总报告
     */
    public EvalRunReport run(List<EvaluationCase> cases, int topK) {
        String runId = UUID.randomUUID().toString().substring(0, 8);
        Instant timestamp = Instant.now();
        List<EvalRetrievalResult> results = new ArrayList<>();
        List<BadCase> badCases = new ArrayList<>();
        List<PassedCase> passedCaseList = new ArrayList<>();

        for (int i = 0; i < cases.size(); i++) {
            EvaluationCase evalCase = cases.get(i);
            try {
                QueryFilter rawFilter = filterExtractor.extract(evalCase.task());
                // stockCode 从用例预解析字段注入（FinAR-Bench 文本不含6位股票代码）；
                // fiscalYear 置 null，因 FinAR-Bench 表格跨多年
                QueryFilter filter = new QueryFilter(evalCase.stockCode(), null,
                        rawFilter.reportType(), rawFilter.metricCodes());
                RetrievalContext ctx = new RetrievalContext(evalCase.task(), AccessScope.anonymous(),
                        Math.max(topK, 30), null, filter);

                // BM25 + Vector 双路并行
                RetrievalResult bm25Result = bm25Retriever.retrieve(ctx);
                RetrievalResult vectorResult = vectorRetriever.retrieve(ctx);

                // 合并：BM25 在前，向量结果去重追加
                List<String> contents = mergeCandidates(bm25Result, vectorResult);
                if (DEBUG_EVAL && i < 3) {
                    log.info("--- DEBUG case[{}] taskId={} type={} ---", i, evalCase.taskId(), evalCase.taskType());
                    log.info("  query: {}", evalCase.task().substring(0, Math.min(80, evalCase.task().length())));
                    log.info("  filter: stock={} year={} metrics={}", filter.stockCode(), filter.fiscalYear(), filter.metricCodes());
                    log.info("  bm25={}, vector={}, merged={}",
                            bm25Result.candidates().size(),
                            vectorResult.candidates().size(), contents.size());
                    log.info("  candidates({}):", contents.size());
                    for (int j = 0; j < Math.min(3, contents.size()); j++) {
                        String c = contents.get(j);
                        log.info("    [{}] {}", j, c != null ? c.substring(0, Math.min(120, c.length())) : "null");
                    }
                    log.info("  expected facts({}):", evalCase.expectedFacts().size());
                    for (var f : evalCase.expectedFacts()) {
                        log.info("    {} yearValues={}", f.metricName(), f.yearValues());
                    }
                }
                EvalRetrievalResult result = evaluator.evaluate(evalCase, contents, filter);
                results.add(result);
                if (result.passed()) {
                    passedCaseList.add(toPassedCase(result));
                } else if (result.failureReason() != null) {
                    badCases.add(toBadCase(result));
                }
            } catch (Exception e) {
                log.warn("评测 case {} 失败: {}", evalCase.taskId(), e.getMessage());
                EvalRetrievalResult failed = EvalRetrievalResult.skipped(
                        evalCase.taskId(), evalCase.taskType(), evalCase.task(),
                        "执行异常: " + e.getClass().getSimpleName());
                results.add(failed);
            }

            if ((i + 1) % 20 == 0) {
                log.info("评测进度: {}/{}", i + 1, cases.size());
            }
        }

        return buildReport(runId, timestamp, results, badCases, passedCaseList);
    }

    /** BM25 结果在前，向量结果去重追加，保持 rank 顺序。 */
    private List<String> mergeCandidates(RetrievalResult bm25, RetrievalResult vector) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> merged = new ArrayList<>();
        for (RetrievalCandidate c : bm25.candidates()) {
            String content = c.content();
            if (content != null && seen.add(content)) {
                merged.add(content);
            }
        }
        for (RetrievalCandidate c : vector.candidates()) {
            String content = c.content();
            if (content != null && seen.add(content)) {
                merged.add(content);
            }
        }
        return merged;
    }

    private EvalRunReport buildReport(String runId, Instant timestamp,
                                       List<EvalRetrievalResult> results, List<BadCase> badCases,
                                       List<PassedCase> passedCaseList) {
        long total = results.size();
        long passed = results.stream().filter(EvalRetrievalResult::passed).count();
        long skipped = results.stream().filter(r -> r.failureReason() != null && r.failureReason().contains("暂不自动评测")).count();
        long evalable = total - skipped;

        double avgRecall5 = evalable == 0 ? 0 : results.stream()
                .filter(r -> r.failureReason() == null || !r.failureReason().contains("暂不自动评测"))
                .mapToDouble(EvalRetrievalResult::recallAt5).average().orElse(0);
        double avgRecall10 = evalable == 0 ? 0 : results.stream()
                .filter(r -> r.failureReason() == null || !r.failureReason().contains("暂不自动评测"))
                .mapToDouble(EvalRetrievalResult::recallAt10).average().orElse(0);
        double avgMrr = evalable == 0 ? 0 : results.stream()
                .filter(r -> r.failureReason() == null || !r.failureReason().contains("暂不自动评测"))
                .mapToDouble(EvalRetrievalResult::mrr).average().orElse(0);

        long stockCorrect = results.stream().filter(r -> r.queryFilterStockCodeMatch() == 1).count();
        long yearCorrect = results.stream().filter(r -> r.queryFilterYearMatch() == 1).count();

        Map<String, TypeMetrics> byType = new LinkedHashMap<>();
        results.stream().map(EvalRetrievalResult::taskType).distinct().sorted().forEach(type -> {
            List<EvalRetrievalResult> group = results.stream()
                    .filter(r -> r.taskType().equals(type)).toList();
            long t = group.size();
            long p = group.stream().filter(EvalRetrievalResult::passed).count();
            long s = group.stream().filter(r -> r.failureReason() != null
                    && r.failureReason().contains("暂不自动评测")).count();
            long e = t - s;
            double r = e == 0 ? 0 : group.stream()
                    .filter(r2 -> r2.failureReason() == null || !r2.failureReason().contains("暂不自动评测"))
                    .mapToDouble(EvalRetrievalResult::recallAt10).average().orElse(0);
            double m = e == 0 ? 0 : group.stream()
                    .filter(r2 -> r2.failureReason() == null || !r2.failureReason().contains("暂不自动评测"))
                    .mapToDouble(EvalRetrievalResult::mrr).average().orElse(0);
            byType.put(type, new TypeMetrics(t, p, s, r, m));
        });

        return new EvalRunReport(runId, timestamp, (int) total, (int) passed, (int) skipped,
                avgRecall5, avgRecall10, avgMrr,
                total == 0 ? 0 : (double) stockCorrect / total,
                total == 0 ? 0 : (double) yearCorrect / total,
                byType, badCases, passedCaseList);
    }

    private BadCase toBadCase(EvalRetrievalResult result) {
        String expectedSummary = result.totalExpectedFacts() + " facts expected";
        String retrievedSummary = "found " + result.foundFacts();
        return new BadCase(result.taskId(), result.taskType(), result.question(),
                expectedSummary, retrievedSummary,
                result.failureReason() != null ? result.failureReason() : "");
    }

    private PassedCase toPassedCase(EvalRetrievalResult result) {
        return new PassedCase(result.taskId(), result.taskType(), result.question(),
                result.recallAt10(), result.mrr(), result.totalExpectedFacts(), result.foundFacts());
    }
}

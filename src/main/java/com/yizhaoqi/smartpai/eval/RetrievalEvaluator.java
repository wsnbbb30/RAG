package com.yizhaoqi.smartpai.eval;

import com.yizhaoqi.smartpai.eval.model.EvalRetrievalResult;
import com.yizhaoqi.smartpai.eval.model.EvaluationCase;
import com.yizhaoqi.smartpai.eval.model.GroundTruthFact;
import com.yizhaoqi.smartpai.retrieval.QueryFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 检索结果与 ground truth 比对器。
 *
 * <p>fact 类型：检查 ground truth 中的每个数值是否出现在检索结果中。
 * indicator 类型：调用 LLM 从检索结果中计算指标值，再与 ground truth 比对。
 * reasoning 类型：暂不自动判断，标记为 skipped。</p>
 */
public class RetrievalEvaluator {

    private static final double INDICATOR_TOLERANCE = 0.01; // 1% relative tolerance

    private final IndicatorComputer indicatorComputer;

    public RetrievalEvaluator(IndicatorComputer indicatorComputer) {
        this.indicatorComputer = indicatorComputer;
    }

    /**
     * 评测单条 case 的检索质量。
     */
    public EvalRetrievalResult evaluate(EvaluationCase evalCase,
                                         List<String> candidateContents,
                                         QueryFilter extractedFilter) {
        String taskType = evalCase.taskType();
        if ("reasoning".equals(taskType)) {
            return EvalRetrievalResult.skipped(evalCase.taskId(), taskType, evalCase.task(),
                    "reasoning 类型暂不自动评测");
        }

        List<GroundTruthFact> expected = evalCase.expectedFacts();
        if (expected.isEmpty()) {
            return EvalRetrievalResult.skipped(evalCase.taskId(), taskType, evalCase.task(),
                    "ground truth 解析为空");
        }

        if ("indicator".equals(taskType)) {
            return evaluateIndicator(evalCase, candidateContents, extractedFilter);
        }

        return evaluateFact(evalCase, candidateContents, extractedFilter);
    }

    private EvalRetrievalResult evaluateFact(EvaluationCase evalCase,
                                              List<String> candidateContents,
                                              QueryFilter extractedFilter) {
        List<GroundTruthFact> expected = evalCase.expectedFacts();
        int totalFacts = expected.size();
        int foundFacts = 0;
        double firstHitRank = Double.MAX_VALUE;
        List<Integer> hitRanks = new ArrayList<>();

        for (GroundTruthFact fact : expected) {
            Integer rank = findFactInCandidates(fact, candidateContents);
            if (rank != null) {
                foundFacts++;
                hitRanks.add(rank);
                if (rank < firstHitRank) firstHitRank = rank;
            }
        }

        double recallAt5 = totalFacts == 0 ? 0 : (double) countHitsUpToRank(hitRanks, 5) / totalFacts;
        double recallAt10 = totalFacts == 0 ? 0 : (double) countHitsUpToRank(hitRanks, 10) / totalFacts;
        double mrr = firstHitRank == Double.MAX_VALUE ? 0 : 1.0 / firstHitRank;
        boolean passed = foundFacts >= totalFacts;

        int stockMatch = evalFilterMatch(evalCase.stockCode(), extractedFilter.stockCode());
        int yearMatch = evalFilterMatch(evalCase.fiscalYear(), extractedFilter.fiscalYear());

        String failureReason = passed ? null :
                "found " + foundFacts + "/" + totalFacts + " facts in candidates";

        return new EvalRetrievalResult(evalCase.taskId(), evalCase.taskType(), evalCase.task(),
                passed, recallAt5, recallAt10, mrr, totalFacts, foundFacts,
                stockMatch, yearMatch, candidateContents, failureReason);
    }

    /** indicator 任务：LLM 计算 → 数值比对。 */
    private EvalRetrievalResult evaluateIndicator(EvaluationCase evalCase,
                                                    List<String> candidateContents,
                                                    QueryFilter extractedFilter) {
        List<GroundTruthFact> expected = evalCase.expectedFacts();
        int totalFacts = expected.size();

        List<GroundTruthFact> computed = indicatorComputer.compute(evalCase.task(), candidateContents);
        if (computed.isEmpty()) {
            return new EvalRetrievalResult(evalCase.taskId(), evalCase.taskType(), evalCase.task(),
                    false, 0.0, 0.0, 0.0, totalFacts, 0,
                    evalFilterMatch(evalCase.stockCode(), extractedFilter.stockCode()),
                    evalFilterMatch(evalCase.fiscalYear(), extractedFilter.fiscalYear()),
                    candidateContents, "LLM 未返回计算结果");
        }

        int foundFacts = 0;
        for (GroundTruthFact expectedFact : expected) {
            if (findIndicatorMatch(expectedFact, computed)) {
                foundFacts++;
            }
        }

        double recall = totalFacts == 0 ? 0 : (double) foundFacts / totalFacts;
        double mrr = foundFacts > 0 ? 1.0 : 0.0;
        boolean passed = foundFacts >= totalFacts;

        String failureReason = passed ? null :
                "found " + foundFacts + "/" + totalFacts + " indicators computed correctly";

        return new EvalRetrievalResult(evalCase.taskId(), evalCase.taskType(), evalCase.task(),
                passed, recall, recall, mrr, totalFacts, foundFacts,
                evalFilterMatch(evalCase.stockCode(), extractedFilter.stockCode()),
                evalFilterMatch(evalCase.fiscalYear(), extractedFilter.fiscalYear()),
                candidateContents, failureReason);
    }

    /** 在 LLM 计算结果中查找匹配的指标，使用数值容差比对。 */
    private boolean findIndicatorMatch(GroundTruthFact expectedFact, List<GroundTruthFact> computed) {
        String expectedMetric = expectedFact.metricName();
        Map<String, String> expectedValues = expectedFact.yearValues();

        for (GroundTruthFact computedFact : computed) {
            if (!computedFact.metricName().contains(expectedMetric)
                    && !expectedMetric.contains(computedFact.metricName())) {
                continue;
            }

            boolean allMatch = true;
            for (Map.Entry<String, String> entry : expectedValues.entrySet()) {
                String year = entry.getKey();
                String expectedVal = entry.getValue();
                String computedVal = computedFact.yearValues().get(year);
                if (computedVal == null) {
                    allMatch = false;
                    break;
                }
                if (!valuesClose(expectedVal, computedVal)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) return true;
        }
        return false;
    }

    /** 数值近似比对，允许 1% 相对误差。 */
    static boolean valuesClose(String expected, String computed) {
        try {
            double exp = Double.parseDouble(expected);
            double comp = Double.parseDouble(computed);
            if (Math.abs(exp) < 1e-10) return Math.abs(comp) < 1e-10;
            return Math.abs(comp - exp) / Math.abs(exp) <= INDICATOR_TOLERANCE;
        } catch (NumberFormatException e) {
            return expected.equals(computed);
        }
    }

    private static final boolean DEBUG_FACT = false;

    private Integer findFactInCandidates(GroundTruthFact fact, List<String> candidateContents) {
        String metricName = fact.metricName();
        Map<String, String> yearValues = fact.yearValues();

        for (int i = 0; i < candidateContents.size(); i++) {
            String content = candidateContents.get(i);
            if (content == null) continue;
            String normalizedContent = FinArBenchLoader.normalizeNumber(content);

            if (!normalizedContent.contains(metricName)) continue;

            boolean allValuesPresent = true;
            for (String expectedValue : yearValues.values()) {
                String normalizedValue = FinArBenchLoader.normalizeNumber(expectedValue);
                if (!normalizedContent.contains(normalizedValue)) {
                    allValuesPresent = false;
                    break;
                }
            }
            if (allValuesPresent) {
                return i + 1;
            }
        }
        return null;
    }

    private int countHitsUpToRank(List<Integer> hitRanks, int k) {
        int count = 0;
        for (int rank : hitRanks) {
            if (rank <= k) count++;
        }
        return count;
    }

    private int evalFilterMatch(Object expected, Object actual) {
        if (expected == null) return 0;
        if (actual == null) return 0;
        return expected.equals(actual) ? 1 : -1;
    }
}

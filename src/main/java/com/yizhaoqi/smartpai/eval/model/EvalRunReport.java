package com.yizhaoqi.smartpai.eval.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 一次评测运行的汇总报告。 */
public record EvalRunReport(
        String runId,
        Instant timestamp,
        int totalCases,
        int passedCases,
        int skippedCases,
        double avgRecallAt5,
        double avgRecallAt10,
        double avgMrr,
        double stockCodeExtractionAccuracy,
        double yearExtractionAccuracy,
        Map<String, TypeMetrics> metricsByType,
        List<BadCase> badCases,
        List<PassedCase> passedCaseList) {

    public record TypeMetrics(long total, long passed, long skipped, double avgRecall, double avgMrr) {}

    public record BadCase(String taskId, String taskType, String question,
                          String expectedSummary, String retrievedSummary, String reason) {}

    public record PassedCase(String taskId, String taskType, String question,
                             double recallAt10, double mrr, int totalExpected, int foundFacts) {}
}

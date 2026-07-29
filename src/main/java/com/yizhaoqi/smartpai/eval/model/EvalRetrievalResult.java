package com.yizhaoqi.smartpai.eval.model;

import java.util.List;

/** 单条评测用例的检索评测结果。 */
public record EvalRetrievalResult(
        String taskId,
        String taskType,
        String question,
        boolean passed,
        double recallAt5,
        double recallAt10,
        double mrr,
        int totalExpectedFacts,
        int foundFacts,
        int queryFilterStockCodeMatch,   // 0=未提取, 1=正确, -1=错误
        int queryFilterYearMatch,        // 0=未提取, 1=正确, -1=错误
        List<String> retrievedContents,
        String failureReason) {

    public static EvalRetrievalResult skipped(String taskId, String taskType, String question, String reason) {
        return new EvalRetrievalResult(taskId, taskType, question, false, 0, 0, 0,
                0, 0, 0, 0, List.of(), reason);
    }
}

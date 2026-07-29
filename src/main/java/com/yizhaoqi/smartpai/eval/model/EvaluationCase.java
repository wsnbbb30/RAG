package com.yizhaoqi.smartpai.eval.model;

import java.util.List;

/** FinAR-Bench 中的单条 QA 评测用例。tableContext 为该公司完整 Markdown 财务报表，同公司的 case 共享同一份。 */
public record EvaluationCase(
        String taskId,
        String task,
        String groundTruth,
        String taskType,         // "fact" / "indicator" / "reasoning"
        String company,
        String companyCode,       // "603421.SH"
        String stockCode,         // "603421"
        Integer fiscalYear,
        String tableContext,      // 该公司完整 Markdown 报表，用于索引
        List<GroundTruthFact> expectedFacts) {
}

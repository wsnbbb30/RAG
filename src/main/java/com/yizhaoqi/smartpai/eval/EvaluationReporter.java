package com.yizhaoqi.smartpai.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yizhaoqi.smartpai.eval.model.EvalRunReport;
import com.yizhaoqi.smartpai.eval.model.EvalRunReport.BadCase;
import com.yizhaoqi.smartpai.eval.model.EvalRunReport.PassedCase;
import com.yizhaoqi.smartpai.eval.model.EvalRunReport.TypeMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 评测报告输出。 */
public class EvaluationReporter {

    private static final Logger log = LoggerFactory.getLogger(EvaluationReporter.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** 在控制台打印汇总报告。 */
    public void printConsoleReport(EvalRunReport report) {
        var sb = new StringBuilder();
        sb.append("\n");
        sb.append("═══════════════════════════════════════════════════════\n");
        sb.append("  FinAR-Bench 检索评测报告\n");
        sb.append("  Run:  ").append(report.runId()).append("\n");
        sb.append("  Time: ").append(FMT.format(report.timestamp())).append("\n");
        sb.append("═══════════════════════════════════════════════════════\n");
        sb.append(String.format("  Cases:    %3d total, %3d passed, %3d skipped\n",
                report.totalCases(), report.passedCases(), report.skippedCases()));
        sb.append("───────────────────────────────────────────────────────\n");
        sb.append(String.format("  Recall@5:  %.2f\n", report.avgRecallAt5()));
        sb.append(String.format("  Recall@10: %.2f\n", report.avgRecallAt10()));
        sb.append(String.format("  MRR:       %.2f\n", report.avgMrr()));
        sb.append(String.format("  StockCode Extraction Accuracy: %.2f\n", report.stockCodeExtractionAccuracy()));
        sb.append(String.format("  Year Extraction Accuracy:      %.2f\n", report.yearExtractionAccuracy()));
        sb.append("───────────────────────────────────────────────────────\n");
        sb.append("  By Task Type:\n");
        for (var entry : report.metricsByType().entrySet()) {
            TypeMetrics m = entry.getValue();
            sb.append(String.format("    %-12s  %2d/%2d passed  Recall@10: %.2f  MRR: %.2f\n",
                    entry.getKey(), m.passed(), m.total() - m.skipped(), m.avgRecall(), m.avgMrr()));
        }
        sb.append("───────────────────────────────────────────────────────\n");
        if (!report.passedCaseList().isEmpty()) {
            sb.append("  Passed Cases (").append(report.passedCaseList().size()).append("):\n");
            for (PassedCase pc : report.passedCaseList()) {
                String q = pc.question().length() > 70 ? pc.question().substring(0, 67) + "..." : pc.question();
                sb.append(String.format("    [%s] %s\n       facts=%d/%d Recall@10=%.2f MRR=%.2f\n",
                        pc.taskType(), q, pc.foundFacts(), pc.totalExpected(), pc.recallAt10(), pc.mrr()));
            }
        }
        sb.append("\n");
        if (!report.badCases().isEmpty()) {
            int limit = Math.min(report.badCases().size(), 10);
            sb.append("  Top ").append(limit).append(" Bad Cases:\n");
            for (int i = 0; i < limit; i++) {
                BadCase bc = report.badCases().get(i);
                String q = bc.question().length() > 60 ? bc.question().substring(0, 57) + "..." : bc.question();
                sb.append(String.format("    %d. [%s] %s\n       expected=%s retrieved=%s reason=%s\n",
                        i + 1, bc.taskType(), q, bc.expectedSummary(), bc.retrievedSummary(), bc.reason()));
            }
        }
        sb.append("═══════════════════════════════════════════════════════\n");
        log.info(sb.toString());
        System.out.println(sb);
    }

    /** 导出 JSON 报告到文件。 */
    public Path exportJson(EvalRunReport report, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path file = outputDir.resolve("finarbench-eval-" + report.runId() + ".json");
        JSON.writeValue(file.toFile(), report);
        log.info("评测报告已导出: {}", file);
        return file;
    }
}

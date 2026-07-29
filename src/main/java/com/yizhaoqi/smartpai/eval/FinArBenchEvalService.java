package com.yizhaoqi.smartpai.eval;

import com.yizhaoqi.smartpai.eval.model.EvalRunReport;
import com.yizhaoqi.smartpai.eval.model.EvaluationCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * FinAR-Bench 评测全流程编排：加载 → 索引 → 检索评测 → 报告 → 清理。
 *
 * <p>调用方只需传入 dev.txt 路径和 topK，其余由本类协调。</p>
 */
@Service
public class FinArBenchEvalService {

    private static final Logger log = LoggerFactory.getLogger(FinArBenchEvalService.class);
    private static final Path DEFAULT_DATA_PATH = Path.of("data/FinAR-Bench/dev.txt");

    private final FinArBenchIndexer indexer;
    private final EvaluationRunner runner;
    private final EvaluationReporter reporter;
    private final FinArBenchLoader loader;

    public FinArBenchEvalService(FinArBenchIndexer indexer, EvaluationRunner runner) {
        this.indexer = indexer;
        this.runner = runner;
        this.reporter = new EvaluationReporter();
        this.loader = new FinArBenchLoader();
    }

    /**
     * 执行完整评测流程。
     *
     * @param dataPath dev.txt 路径，null 则使用默认路径
     * @param topK     检索返回数量
     * @param cleanup  评测结束后是否清理 ES 中的 eval 数据
     * @return 评测报告
     */
    public EvalRunReport evaluate(Path dataPath, int topK, boolean cleanup) throws IOException {
        Path path = dataPath != null ? dataPath : DEFAULT_DATA_PATH;
        log.info("加载 FinAR-Bench 数据: {}", path);
        List<EvaluationCase> cases = loader.load(path);
        log.info("加载完成: {} 条用例", cases.size());

        log.info("开始索引到 ES...");
        int chunkCount = indexer.indexAll(cases);
        log.info("索引完成: {} 个 chunk", chunkCount);

        // 给 ES 一点时间完成 refresh
        try { Thread.sleep(500); } catch (InterruptedException ignored) { }

        log.info("开始检索评测...");
        EvalRunReport report = runner.run(cases, topK);
        log.info("评测完成: {}/{} passed", report.passedCases(), report.totalCases());

        reporter.printConsoleReport(report);
        reporter.exportJson(report, Path.of("data/eval-results"));

        if (cleanup) {
            indexer.cleanup();
            log.info("ES eval 数据已清理");
        }

        return report;
    }
}

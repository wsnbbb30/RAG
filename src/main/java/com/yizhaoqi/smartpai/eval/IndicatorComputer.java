package com.yizhaoqi.smartpai.eval;

import com.yizhaoqi.smartpai.client.DeepSeekClient;
import com.yizhaoqi.smartpai.eval.model.GroundTruthFact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 使用 DeepSeek LLM 从检索到的原始财务数据中计算指标值。
 *
 * <p>FinAR-Bench 的 indicator 类问题要求计算比率/周转率等指标，
 * 这些值不存在于原始表格中，必须通过 LLM 提取原始值并计算。</p>
 */
public class IndicatorComputer {

    private static final Logger log = LoggerFactory.getLogger(IndicatorComputer.class);
    private static final int MAX_CANDIDATES = 25;

    private final DeepSeekClient deepSeekClient;

    public IndicatorComputer(DeepSeekClient deepSeekClient) {
        this.deepSeekClient = deepSeekClient;
    }

    /**
     * 调用 LLM 计算财务指标。
     *
     * @param question  原始问题
     * @param candidates 检索到的候选内容
     * @return 计算结果，格式与 ground truth 一致
     */
    public List<GroundTruthFact> compute(String question, List<String> candidates) {
        String context = buildContext(candidates);
        String prompt = buildPrompt(question, context);

        try {
            log.info("Indicator LLM 计算: question={}, candidates={}",
                    question.substring(0, Math.min(60, question.length())), candidates.size());
            String response = deepSeekClient.completeResponse(prompt, "", List.of());
            log.debug("LLM response: {}", response.substring(0, Math.min(200, response.length())));
            return FinArBenchLoader.parseGroundTruth(response);
        } catch (Exception e) {
            log.warn("Indicator LLM 计算失败: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildContext(List<String> candidates) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(candidates.size(), MAX_CANDIDATES);
        for (int i = 0; i < limit; i++) {
            sb.append("[").append(i + 1).append("] ").append(candidates.get(i)).append("\n");
        }
        return sb.toString();
    }

    /** 构建 prompt，要求 LLM 以 Markdown 表格输出计算结果。 */
    private String buildPrompt(String question, String context) {
        return """
                你是财务分析师。根据下方财务数据计算要求的指标，输出 Markdown 表格。

                <财务数据>
                %s
                </财务数据>

                任务：%s

                要求：
                1. 从财务数据中找到相关原始值，准确计算每个指标
                2. 输出与以下格式完全一致的 Markdown 表格：
                | 项目 | 2023 |
                | 指标名1 | 计算值1 |
                | 指标名2 | 计算值2 |
                3. 结果保留4位小数
                4. 数据不足的指标不要输出
                5. 只输出表格，不输出其他内容
                """.formatted(context, question);
    }
}

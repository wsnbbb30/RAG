package com.yizhaoqi.smartpai.rag;

import com.yizhaoqi.smartpai.config.EvidenceProperties;
import com.yizhaoqi.smartpai.rerank.RerankedCandidate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 将精排结果冻结为可引用上下文。
 *
 * <p>按精排顺序贪心装入 token budget，绝不截断单条证据的中间内容；放不下的证据直接跳过，
 * 避免模型引用到不完整句子或表格。每条证据都有稳定 citationId，例如 [E1]。</p>
 */
@Component
public class EvidenceAssembler {
    private final EvidenceProperties properties;
    private final TokenBudgetPolicy tokenBudgetPolicy;

    public EvidenceAssembler(EvidenceProperties properties, TokenBudgetPolicy tokenBudgetPolicy) {
        this.properties = properties;
        this.tokenBudgetPolicy = tokenBudgetPolicy;
    }

    public EvidenceAssembly assemble(List<RerankedCandidate> candidates) {
        List<Evidence> evidence = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        int usedTokens = 0;
        for (RerankedCandidate reranked : candidates) {
            var candidate = reranked.candidate().candidate();
            String content = candidate.content() == null ? "" : candidate.content().trim();
            if (content.length() < properties.getMinEvidenceCharacters()) continue;
            String citationId = "E" + (evidence.size() + 1);
            String block = "[" + citationId + "] version=" + candidate.versionId() + ", page="
                    + candidate.pageStart() + "-" + candidate.pageEnd() + "\n" + content + "\n";
            int blockTokens = tokenBudgetPolicy.estimateTokens(block);
            if (usedTokens + blockTokens > properties.getMaxContextTokens()) continue;
            evidence.add(new Evidence(citationId, candidate.versionId(), candidate.chunkId(), candidate.pageStart(),
                    candidate.pageEnd(), candidate.documentId(), content));
            context.append(block).append('\n');
            usedTokens += blockTokens;
        }
        if (evidence.size() < properties.getMinEvidenceCount()) {
            return new EvidenceAssembly(evidence, context.toString(), usedTokens, AnswerStatus.INSUFFICIENT_EVIDENCE,
                    "没有足够的授权证据，已拒绝调用生成模型");
        }
        return new EvidenceAssembly(evidence, context.toString(), usedTokens, AnswerStatus.SUPPORTED, "");
    }
}

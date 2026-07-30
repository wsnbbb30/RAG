package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.client.DeepSeekClient;
import com.yizhaoqi.smartpai.rag.AnswerStatus;
import com.yizhaoqi.smartpai.rag.CitationVerifier;
import com.yizhaoqi.smartpai.rag.EvidenceAssembler;
import com.yizhaoqi.smartpai.rag.EvidenceAssembly;
import com.yizhaoqi.smartpai.rag.RagResponse;
import com.yizhaoqi.smartpai.finance.DraftAnswer;
import com.yizhaoqi.smartpai.repository.FinancialFactRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 服务端 RAG 问答编排：检索 → 冻结证据 → 预算控制 → 生成 → 引用校验。
 * 生成模型只拿到 evidence context，无法访问未授权的原文或自行扩展检索范围。
 */
@Service
public class RagAnswerService {
    private final HybridSearchService searchService;
    private final EvidenceAssembler evidenceAssembler;
    private final CitationVerifier citationVerifier;
    private final DeepSeekClient deepSeekClient;
    private final FinancialAnswerVerifier financialAnswerVerifier;
    private final FinancialFactRepository factRepository;

    public RagAnswerService(HybridSearchService searchService, EvidenceAssembler evidenceAssembler,
                            CitationVerifier citationVerifier, DeepSeekClient deepSeekClient,
                            FinancialAnswerVerifier financialAnswerVerifier, FinancialFactRepository factRepository) {
        this.searchService = searchService; this.evidenceAssembler = evidenceAssembler;
        this.citationVerifier = citationVerifier; this.deepSeekClient = deepSeekClient;
        this.financialAnswerVerifier = financialAnswerVerifier; this.factRepository = factRepository;
    }

    public RagResponse answer(String question, String userId, int topK) {
        HybridSearchService.RetrievalResponse retrieval = searchService.retrieveWithPermission(question, userId, topK);
        EvidenceAssembly assembly = evidenceAssembler.assemble(retrieval.candidates());
        if (!assembly.sufficient()) {
            return new RagResponse("暂无足够的授权证据，无法可靠回答该问题。", AnswerStatus.INSUFFICIENT_EVIDENCE,
                    assembly.evidence(), citationVerifier.verify("", assembly.evidence()), retrieval.traceId(), retrieval.degraded());
        }
        try {
            String answer = deepSeekClient.completeResponse(question, assembly.context(), List.of());
            var verification = citationVerifier.verify(answer, assembly.evidence());
            var financialVerification = financialAnswerVerifier.verify(new DraftAnswer(answer),
                    factRepository.findByVersionIdInOrderByVersionIdAscMetricCodeAscPeriodAsc(
                            assembly.evidence().stream().map(com.yizhaoqi.smartpai.rag.Evidence::versionId).distinct().toList()),
                    assembly.evidence());
            AnswerStatus status = verification.valid() && !financialVerification.hasBlockingIssue()
                    ? AnswerStatus.SUPPORTED : AnswerStatus.PARTIAL;
            return new RagResponse(answer, status, assembly.evidence(), verification, retrieval.traceId(), retrieval.degraded(), financialVerification);
        } catch (Exception exception) {
            // 模型不可用不是“无证据”，仍返回可定位的证据，前端可提示稍后重试。
            return new RagResponse("已找到相关证据，但生成服务暂时不可用。", AnswerStatus.PARTIAL,
                    assembly.evidence(), citationVerifier.verify("", assembly.evidence()), retrieval.traceId(), true);
        }
    }
}

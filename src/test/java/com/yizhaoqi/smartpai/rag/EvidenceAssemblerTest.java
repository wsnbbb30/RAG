package com.yizhaoqi.smartpai.rag;

import com.yizhaoqi.smartpai.config.EvidenceProperties;
import com.yizhaoqi.smartpai.model.DocumentChunk;
import com.yizhaoqi.smartpai.rerank.RerankedCandidate;
import com.yizhaoqi.smartpai.retrieval.FusedCandidate;
import com.yizhaoqi.smartpai.retrieval.RetrievalCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证证据冻结、预算控制和无证据拒答的核心边界。 */
class EvidenceAssemblerTest {

    @Test
    void shouldRefuseWhenNoUsableEvidenceExists() {
        EvidenceAssembler assembler = assembler(100, 20);

        EvidenceAssembly result = assembler.assemble(List.of(candidate("太短")));

        assertFalse(result.sufficient());
        assertEquals(AnswerStatus.INSUFFICIENT_EVIDENCE, result.status());
        assertTrue(result.evidence().isEmpty());
    }

    @Test
    void shouldKeepWholeEvidenceBlockAndAssignStableCitation() {
        EvidenceAssembler assembler = assembler(100, 5);

        EvidenceAssembly result = assembler.assemble(List.of(candidate("本年度营业收入增长，现金流保持稳定。")));

        assertTrue(result.sufficient());
        assertEquals("E1", result.evidence().get(0).citationId());
        assertTrue(result.context().contains("[E1] version=101, page=3-4"));
        assertTrue(result.context().contains("本年度营业收入增长"));
    }

    @Test
    void shouldSkipInsteadOfTruncatingEvidenceThatExceedsBudget() {
        EvidenceAssembler assembler = assembler(3, 1);

        EvidenceAssembly result = assembler.assemble(List.of(candidate("这是一段超过很小 token 预算的完整证据文本。")));

        assertFalse(result.sufficient());
        assertTrue(result.context().isEmpty());
    }

    private EvidenceAssembler assembler(int maxTokens, int minCharacters) {
        EvidenceProperties properties = new EvidenceProperties();
        properties.setMaxContextTokens(maxTokens);
        properties.setMinEvidenceCharacters(minCharacters);
        properties.setMinEvidenceCount(1);
        return new EvidenceAssembler(properties, new TokenBudgetPolicy());
    }

    private RerankedCandidate candidate(String content) {
        RetrievalCandidate candidate = new RetrievalCandidate("bm25", 1, 1.0, "index-1", 101L,
                "document-1", 201L, DocumentChunk.ChunkType.TEXT, null, content, "hash", 3, 4,
                "user-1", "org-1", false, "600000", 2024, "ANNUAL_REPORT");
        FusedCandidate fused = new FusedCandidate(candidate, 0.1, List.of());
        return new RerankedCandidate(fused, 1, 0.9);
    }
}

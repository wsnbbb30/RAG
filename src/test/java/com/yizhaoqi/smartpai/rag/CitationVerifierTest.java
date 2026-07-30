package com.yizhaoqi.smartpai.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证模型输出不能伪造证据编号，也不能省略证据引用。 */
class CitationVerifierTest {
    private final CitationVerifier verifier = new CitationVerifier();
    private final List<Evidence> evidence = List.of(
            new Evidence("E1", 101L, 201L, 3, 3, "document-1", "营业收入为 100 万元"));

    @Test
    void shouldAcceptCitationBelongingToFrozenEvidence() {
        CitationVerification result = verifier.verify("营业收入为 100 万元。[E1]", evidence);
        assertTrue(result.valid());
    }

    @Test
    void shouldRejectFabricatedCitation() {
        CitationVerification result = verifier.verify("营业收入为 100 万元。[E99]", evidence);
        assertFalse(result.valid());
        assertTrue(result.invalidIds().contains("E99"));
    }

    @Test
    void shouldRejectAnswerWithoutCitationWhenEvidenceExists() {
        CitationVerification result = verifier.verify("营业收入为 100 万元。", evidence);
        assertFalse(result.valid());
    }
}

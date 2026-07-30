package com.yizhaoqi.smartpai.rag;

import java.util.List;

/** EvidenceAssembler 的不可变输出；context 只能由 evidence 构成，防止检索后注入额外“事实”。 */
public record EvidenceAssembly(List<Evidence> evidence, String context, int tokenCount, AnswerStatus status, String reason) {
    public EvidenceAssembly { evidence = List.copyOf(evidence); }
    public boolean sufficient() { return status != AnswerStatus.INSUFFICIENT_EVIDENCE; }
}

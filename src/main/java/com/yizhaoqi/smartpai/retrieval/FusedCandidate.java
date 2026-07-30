package com.yizhaoqi.smartpai.retrieval;

import java.util.List;

/** RRF 融合后的证据，保留每一路贡献以支持评测、调参和面试追问中的可解释性。 */
public record FusedCandidate(RetrievalCandidate candidate, double fusedScore,
                             List<RouteContribution> contributions) {
    public FusedCandidate {
        contributions = List.copyOf(contributions);
    }

    /** 单路贡献 = 1 / (rrfK + rank)，rawScore 只作为审计信息，不参与跨路计算。 */
    public record RouteContribution(String source, int rank, double rawScore, double rrfContribution) { }
}

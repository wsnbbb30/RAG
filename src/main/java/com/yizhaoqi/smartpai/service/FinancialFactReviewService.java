package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.FinancialFact;
import com.yizhaoqi.smartpai.model.dto.FinancialFactReviewRequest;
import com.yizhaoqi.smartpai.repository.FinancialFactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 低置信度事实的最小人工复核能力，保证修正不会损失原始证据链。 */
@Service
public class FinancialFactReviewService {
    private final FinancialFactRepository factRepository;
    public FinancialFactReviewService(FinancialFactRepository factRepository) { this.factRepository = factRepository; }

    public List<FinancialFact> pending() {
        return factRepository.findByReviewStatusOrderByCreatedAtAsc(FinancialFact.ReviewStatus.PENDING);
    }

    @Transactional
    public FinancialFact review(Long factId, FinancialFactReviewRequest request) {
        FinancialFact fact = factRepository.findById(factId)
                .orElseThrow(() -> new IllegalArgumentException("财务事实不存在: " + factId));
        FinancialFact.ReviewStatus status;
        try {
            status = FinancialFact.ReviewStatus.valueOf(request.reviewStatus());
        } catch (Exception exception) {
            throw new IllegalArgumentException("reviewStatus 必须为 APPROVED 或 REJECTED");
        }
        if (status == FinancialFact.ReviewStatus.PENDING) {
            throw new IllegalArgumentException("人工复核不能将状态改回 PENDING");
        }
        if (request.value() != null) fact.setValue(request.value());
        if (request.currency() != null && !request.currency().isBlank()) fact.setCurrency(request.currency().trim().toUpperCase());
        fact.setReviewStatus(status);
        return factRepository.save(fact);
    }
}

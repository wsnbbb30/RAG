package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.FinancialFact;
import com.yizhaoqi.smartpai.model.FinancialReportMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinancialFactRepository extends JpaRepository<FinancialFact, Long> {
    List<FinancialFact> findByVersionIdOrderByMetricCodeAscPeriodAsc(Long versionId);
    List<FinancialFact> findByReviewStatusOrderByCreatedAtAsc(FinancialFact.ReviewStatus reviewStatus);
    List<FinancialFact> findByVersionIdAndMetricCodeAndPeriodAndScope(Long versionId, String metricCode,
                                                                        String period, FinancialReportMetadata.ReportScope scope);
    List<FinancialFact> findByVersionIdInOrderByVersionIdAscMetricCodeAscPeriodAsc(List<Long> versionIds);
    void deleteByVersionId(Long versionId);
}

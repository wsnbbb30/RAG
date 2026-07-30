package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.FinancialMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinancialMetricRepository extends JpaRepository<FinancialMetric, Long> {
    Optional<FinancialMetric> findByMetricCodeAndEnabledTrue(String metricCode);
}

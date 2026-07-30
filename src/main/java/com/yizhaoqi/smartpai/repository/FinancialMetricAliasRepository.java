package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.FinancialMetricAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinancialMetricAliasRepository extends JpaRepository<FinancialMetricAlias, Long> {
    Optional<FinancialMetricAlias> findByNormalizedAlias(String normalizedAlias);
}

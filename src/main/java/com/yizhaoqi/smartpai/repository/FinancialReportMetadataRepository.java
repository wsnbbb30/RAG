package com.yizhaoqi.smartpai.repository;


import com.yizhaoqi.smartpai.model.FinancialReportMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * FinancialReportMetadata 数据访问层。
 */
@Repository
public interface FinancialReportMetadataRepository extends JpaRepository<FinancialReportMetadata, Long> {

    /** 按版本 ID 查找 */
    Optional<FinancialReportMetadata> findByVersionId(Long versionId);

    /** 查找某公司某年的所有报告元数据 */
    List<FinancialReportMetadata> findByCompanyNameAndFiscalYear(String companyName, Integer fiscalYear);

    /** 按股票代码和财年查找，供 FinancialFactRetriever 精确过滤。 */
    List<FinancialReportMetadata> findByStockCodeAndFiscalYear(String stockCode, Integer fiscalYear);

    /** 查找待人工审核的元数据（置信度为 LOW） */
    List<FinancialReportMetadata> findByConfidence(FinancialReportMetadata.Confidence confidence);
}

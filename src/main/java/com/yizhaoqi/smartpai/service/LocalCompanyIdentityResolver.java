package com.yizhaoqi.smartpai.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * MVP 阶段的确定性本地字典。它与提取器解耦，未来替换为带缓存的巨潮数据源时，
 * 不会改变文档/版本创建语义。
 */
@Component("localCompanyIdentityResolver")
public class LocalCompanyIdentityResolver implements CompanyIdentityResolver {

    private static final Map<String, String> COMPANIES = Map.of(
            "000002", "万科企业股份有限公司",
            "600519", "贵州茅台酒股份有限公司",
            "000651", "格力电器股份有限公司",
            "601318", "中国平安保险（集团）股份有限公司",
            "600036", "招商银行股份有限公司"
    );

    @Override
    public Optional<CompanyIdentity> resolveByStockCode(String stockCode) {
        String companyName = COMPANIES.get(stockCode);
        return companyName == null ? Optional.empty() : Optional.of(new CompanyIdentity(stockCode, companyName));
    }

    @Override
    public Optional<CompanyIdentity> resolveByCompanyName(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return Optional.empty();
        }
        return COMPANIES.entrySet().stream()
                .filter(entry -> entry.getValue().contains(companyName) || companyName.contains(entry.getValue()))
                .findFirst()
                .map(entry -> new CompanyIdentity(entry.getKey(), entry.getValue()));
    }
}

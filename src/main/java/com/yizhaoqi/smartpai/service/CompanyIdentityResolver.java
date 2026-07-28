package com.yizhaoqi.smartpai.service;

import java.util.Optional;

/**
 * 上市公司身份解析接口，避免上传主链路绑定某一家外部数据供应商。
 * 后续可接入带缓存的巨潮实现；解析失败不能阻断文件上传。
 */
public interface CompanyIdentityResolver {

    Optional<CompanyIdentity> resolveByStockCode(String stockCode);

    Optional<CompanyIdentity> resolveByCompanyName(String companyName);

    record CompanyIdentity(String stockCode, String companyName) {
    }
}

package com.yizhaoqi.smartpai.service;

import java.util.List;

/**
 * 上市公司证券目录的数据来源抽象。
 * 将巨潮 HTTP 协议与身份解析逻辑隔离，后续可替换为持久化缓存、付费数据源或离线快照。
 */
public interface CompanyDirectorySource {

    List<CompanyIdentityResolver.CompanyIdentity> loadDirectory();
}

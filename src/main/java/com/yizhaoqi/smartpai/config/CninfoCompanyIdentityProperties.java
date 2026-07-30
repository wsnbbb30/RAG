package com.yizhaoqi.smartpai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 巨潮证券目录的数据源与缓存配置。 */
@Data
@Component
@ConfigurationProperties(prefix = "company-identity.cninfo")
public class CninfoCompanyIdentityProperties {

    /** 巨潮资讯网页面使用的公开证券目录资源。 */
    private String stockListUrl = "https://www.cninfo.com.cn/new/data/szse_stock.json";

    /** 单次目录拉取的最大耗时；超时后立即走本地兜底，不影响上传。 */
    private Duration requestTimeout = Duration.ofSeconds(3);

    /** 成功拉取后的内存缓存时长，避免每次上传都请求外部站点。 */
    private Duration cacheTtl = Duration.ofHours(24);

    /** 外部目录刷新失败后的重试间隔，防止每次上传都等待网络超时。 */
    private Duration failureRetryInterval = Duration.ofMinutes(1);
}

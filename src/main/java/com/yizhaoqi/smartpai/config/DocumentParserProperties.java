package com.yizhaoqi.smartpai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 页级解析配置，集中管理以避免解析器中散落硬编码参数。 */
@Data
@Component
@ConfigurationProperties(prefix = "document.parser")
public class DocumentParserProperties {
    private boolean enabled = true;
    private int ocrTextThreshold = 30;
    private String pdfLayoutVersion = "pdf-layout-v2";
}

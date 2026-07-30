package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.config.CninfoCompanyIdentityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/** 从巨潮资讯网公开证券目录读取“股票代码 - 证券简称”映射。 */
@Component
public class CninfoStockListCompanyDirectorySource implements CompanyDirectorySource {

    private static final Logger log = LoggerFactory.getLogger(CninfoStockListCompanyDirectorySource.class);
    private static final String USER_AGENT = "PaiSmart-RAG/1.0 (company-identity-resolver)";

    private final CninfoCompanyIdentityProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    // 类中保留了测试专用的三参构造器，因此需要显式标识生产环境使用的注入构造器。
    @Autowired
    public CninfoStockListCompanyDirectorySource(CninfoCompanyIdentityProperties properties,
                                                  ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(properties.getRequestTimeout())
                .build());
    }

    CninfoStockListCompanyDirectorySource(CninfoCompanyIdentityProperties properties,
                                          ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public List<CompanyIdentityResolver.CompanyIdentity> loadDirectory() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getStockListUrl()))
                    .timeout(properties.getRequestTimeout())
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://www.cninfo.com.cn/")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("CNInfo stock list returned HTTP " + response.statusCode());
            }
            return parse(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CNInfo stock list request was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load CNInfo stock list", exception);
        }
    }

    private List<CompanyIdentityResolver.CompanyIdentity> parse(String responseBody) throws Exception {
        JsonNode stockList = objectMapper.readTree(responseBody).path("stockList");
        if (!stockList.isArray()) {
            throw new IllegalStateException("CNInfo stock list response does not contain stockList array");
        }
        List<CompanyIdentityResolver.CompanyIdentity> identities = new ArrayList<>();
        for (JsonNode item : stockList) {
            String stockCode = item.path("code").asText().trim();
            String companyName = item.path("zwjc").asText().trim();
            if (stockCode.matches("\\d{6}") && !companyName.isBlank()) {
                identities.add(new CompanyIdentityResolver.CompanyIdentity(stockCode, companyName));
            }
        }
        if (identities.isEmpty()) {
            throw new IllegalStateException("CNInfo stock list is empty");
        }
        log.info("Loaded {} company identities from CNInfo", identities.size());
        return List.copyOf(identities);
    }
}

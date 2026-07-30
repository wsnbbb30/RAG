package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.CninfoCompanyIdentityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 基于巨潮公开证券目录的公司身份解析器。
 * 目录按 TTL 缓存在内存中；网络不可用时复用旧缓存，并最终降级到少量本地字典。
 */
@Primary
@Component
public class CninfoCompanyIdentityResolver implements CompanyIdentityResolver {

    private static final Logger log = LoggerFactory.getLogger(CninfoCompanyIdentityResolver.class);

    private final CompanyDirectorySource directorySource;
    private final CompanyIdentityResolver localFallback;
    private final CninfoCompanyIdentityProperties properties;
    private final Object refreshLock = new Object();
    private volatile DirectorySnapshot snapshot = DirectorySnapshot.empty();
    private volatile Instant nextRefreshAllowedAt = Instant.EPOCH;

    public CninfoCompanyIdentityResolver(CompanyDirectorySource directorySource,
                                         @Qualifier("localCompanyIdentityResolver") CompanyIdentityResolver localFallback,
                                         CninfoCompanyIdentityProperties properties) {
        this.directorySource = directorySource;
        this.localFallback = localFallback;
        this.properties = properties;
    }

    @Override
    public Optional<CompanyIdentity> resolveByStockCode(String stockCode) {
        if (stockCode == null || !stockCode.matches("\\d{6}")) {
            return Optional.empty();
        }
        CompanyIdentity identity = currentDirectory().byStockCode().get(stockCode);
        Optional<CompanyIdentity> result = Optional.ofNullable(identity);
        return result.isPresent() ? result : localFallback.resolveByStockCode(stockCode);
    }

    @Override
    public Optional<CompanyIdentity> resolveByCompanyName(String companyName) {
        String normalizedName = normalizeCompanyName(companyName);
        if (normalizedName.isBlank()) {
            return Optional.empty();
        }
        DirectorySnapshot directory = currentDirectory();
        Optional<CompanyIdentity> exact = Optional.ofNullable(directory.byNormalizedName().get(normalizedName));
        if (exact.isPresent()) {
            return exact;
        }
        // 巨潮目录提供证券简称；允许“贵州茅台酒股份有限公司”与“贵州茅台”互相匹配。
        Optional<CompanyIdentity> containsMatch = directory.byStockCode().values().stream()
                .filter(identity -> {
                    String candidate = normalizeCompanyName(identity.companyName());
                    return candidate.length() >= 2 && (normalizedName.contains(candidate) || candidate.contains(normalizedName));
                })
                .findFirst();
        return containsMatch.isPresent() ? containsMatch : localFallback.resolveByCompanyName(companyName);
    }

    private DirectorySnapshot currentDirectory() {
        DirectorySnapshot current = snapshot;
        if (!current.expired(properties)) {
            return current;
        }
        if (Instant.now().isBefore(nextRefreshAllowedAt)) {
            return current;
        }
        synchronized (refreshLock) {
            current = snapshot;
            if (!current.expired(properties) || Instant.now().isBefore(nextRefreshAllowedAt)) {
                return current;
            }
            try {
                Map<String, CompanyIdentity> byCode = new HashMap<>();
                Map<String, CompanyIdentity> byName = new HashMap<>();
                for (CompanyIdentity identity : directorySource.loadDirectory()) {
                    byCode.putIfAbsent(identity.stockCode(), identity);
                    byName.putIfAbsent(normalizeCompanyName(identity.companyName()), identity);
                }
                snapshot = new DirectorySnapshot(Map.copyOf(byCode), Map.copyOf(byName), Instant.now());
                nextRefreshAllowedAt = Instant.EPOCH;
            } catch (RuntimeException exception) {
                nextRefreshAllowedAt = Instant.now().plus(properties.getFailureRetryInterval());
                log.warn("CNInfo company directory refresh failed; using cached/local fallback: {}", exception.getMessage());
            }
            return snapshot;
        }
    }

    private String normalizeCompanyName(String companyName) {
        if (companyName == null) {
            return "";
        }
        return companyName.replaceAll("[\\s()（）【】\\[\\]、，,:：.-]", "")
                .replace("股份有限公司", "")
                .replace("有限责任公司", "")
                .replace("有限公司", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private record DirectorySnapshot(Map<String, CompanyIdentity> byStockCode,
                                     Map<String, CompanyIdentity> byNormalizedName,
                                     Instant loadedAt) {
        static DirectorySnapshot empty() {
            return new DirectorySnapshot(Map.of(), Map.of(), Instant.EPOCH);
        }

        boolean expired(CninfoCompanyIdentityProperties properties) {
            return loadedAt.plus(properties.getCacheTtl()).isBefore(Instant.now());
        }
    }
}

package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.CninfoCompanyIdentityProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CninfoCompanyIdentityResolverTest {

    @Test
    void resolvesByCodeAndCompanyNameUsingCachedCninfoDirectory() {
        AtomicInteger loadCount = new AtomicInteger();
        CompanyDirectorySource source = () -> {
            loadCount.incrementAndGet();
            return List.of(
                    new CompanyIdentityResolver.CompanyIdentity("600519", "贵州茅台"),
                    new CompanyIdentityResolver.CompanyIdentity("002611", "东方精工"));
        };
        CompanyIdentityResolver fallback = new EmptyCompanyIdentityResolver();
        CninfoCompanyIdentityResolver resolver = new CninfoCompanyIdentityResolver(
                source, fallback, new CninfoCompanyIdentityProperties());

        assertEquals("贵州茅台", resolver.resolveByStockCode("600519").orElseThrow().companyName());
        assertEquals("002611", resolver.resolveByCompanyName("东方精工科技股份有限公司")
                .orElseThrow().stockCode());
        assertEquals(1, loadCount.get(), "同一 TTL 内应复用目录缓存");
    }

    @Test
    void fallsBackToLocalDirectoryWhenCninfoIsUnavailable() {
        CompanyDirectorySource unavailableSource = () -> {
            throw new IllegalStateException("network unavailable");
        };
        CninfoCompanyIdentityResolver resolver = new CninfoCompanyIdentityResolver(
                unavailableSource, new LocalCompanyIdentityResolver(), new CninfoCompanyIdentityProperties());

        Optional<CompanyIdentityResolver.CompanyIdentity> identity = resolver.resolveByStockCode("600519");
        assertTrue(identity.isPresent());
        assertEquals("600519", identity.orElseThrow().stockCode());
    }

    private static class EmptyCompanyIdentityResolver implements CompanyIdentityResolver {
        @Override
        public Optional<CompanyIdentity> resolveByStockCode(String stockCode) {
            return Optional.empty();
        }

        @Override
        public Optional<CompanyIdentity> resolveByCompanyName(String companyName) {
            return Optional.empty();
        }
    }
}

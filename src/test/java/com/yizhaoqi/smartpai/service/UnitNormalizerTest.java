package com.yizhaoqi.smartpai.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** 覆盖年报中最常见的括号负数、单位换算、百分比和空值语义。 */
class UnitNormalizerTest {
    private final UnitNormalizer normalizer = new UnitNormalizer();

    @Test
    void shouldNormalizeParenthesizedNegativeAndTenThousandYuan() {
        var value = normalizer.normalize("（1,234.50）", "单位：万元", "CNY").orElseThrow();
        assertEquals(new BigDecimal("-12345000.00000000"), value.value());
        assertEquals(new BigDecimal("-1234.50"), value.rawValue());
        assertEquals(new BigDecimal("10000"), value.scale());
    }

    @Test
    void shouldNormalizePercentAndNeverTreatDashAsZero() {
        var percent = normalizer.normalize("12.5%", "", "CNY").orElseThrow();
        assertEquals(new BigDecimal("0.12500000"), percent.value());
        assertTrue(normalizer.normalize("—", "单位：元", "CNY").isEmpty());
    }
}

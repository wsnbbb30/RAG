package com.yizhaoqi.smartpai.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;

/**
 * 数字、负号、单位和币种的确定性标准化工具。
 *
 * <p>空白、破折号、N/A 不是零，返回 empty；括号负数、中文逗号和百分号则按财务报告惯例处理。
 * value = rawValue × scale，原始值和原始单位同时写入 FinancialFact。</p>
 */
@Component
public class UnitNormalizer {
    public Optional<NormalizedNumber> normalize(String rawText, String unitHint, String currencyHint) {
        if (rawText == null) return Optional.empty();
        String text = rawText.trim();
        if (text.isBlank() || text.matches("^(--|-|—|－|N/?A|不适用)$")) return Optional.empty();

        boolean parenthesizedNegative = text.startsWith("(") || text.startsWith("（");
        String cleaned = text.replace("(", "").replace(")", "").replace("（", "").replace("）", "")
                .replace(",", "").replace("，", "").replace(" ", "");
        boolean percent = cleaned.endsWith("%");
        cleaned = cleaned.replace("%", "");
        try {
            BigDecimal raw = new BigDecimal(cleaned);
            if (parenthesizedNegative) raw = raw.negate();
            UnitInfo unit = unit(unitHint, currencyHint, percent);
            return Optional.of(new NormalizedNumber(raw.multiply(unit.scale()).setScale(8, RoundingMode.HALF_UP), raw,
                    unit.rawUnit(), unit.currency(), unit.scale()));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private UnitInfo unit(String unitHint, String currencyHint, boolean percent) {
        String hint = unitHint == null ? "" : unitHint.toUpperCase(Locale.ROOT);
        if (percent) return new UnitInfo("%", "PERCENT", new BigDecimal("0.01"));
        BigDecimal scale = hint.contains("亿元") ? new BigDecimal("100000000")
                : hint.contains("百万元") ? new BigDecimal("1000000")
                : hint.contains("万元") ? new BigDecimal("10000")
                : hint.contains("千元") ? new BigDecimal("1000") : BigDecimal.ONE;
        String currency = hint.contains("美元") || hint.contains("USD") ? "USD"
                : hint.contains("港元") || hint.contains("HKD") ? "HKD"
                : currencyHint == null || currencyHint.isBlank() ? "CNY" : currencyHint;
        return new UnitInfo(unitHint == null || unitHint.isBlank() ? currency : unitHint, currency, scale);
    }

    public record NormalizedNumber(BigDecimal value, BigDecimal rawValue, String rawUnit, String currency, BigDecimal scale) { }
    private record UnitInfo(String rawUnit, String currency, BigDecimal scale) { }
}

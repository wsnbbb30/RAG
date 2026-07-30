package com.yizhaoqi.smartpai.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 统一财务计算精度，防止各公式各自舍入导致结果无法复算。 */
public final class DecimalPolicy {
    public static final int INTERNAL_SCALE = 12;
    public static final int RESULT_SCALE = 6;
    private DecimalPolicy() { }
    public static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, INTERNAL_SCALE, RoundingMode.HALF_UP);
    }
    public static BigDecimal result(BigDecimal value) { return value.setScale(RESULT_SCALE, RoundingMode.HALF_UP); }
}

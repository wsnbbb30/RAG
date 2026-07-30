package com.yizhaoqi.smartpai.finance;

import java.math.BigDecimal;

/** 从答案文本识别出的数值主张，保留原文用于核验失败的精确诊断。 */
public record NumericClaim(String metricCode, BigDecimal normalizedValue, String rawText, boolean percent, String sentence) { }

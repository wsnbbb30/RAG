package com.yizhaoqi.smartpai.finance;

/** 计算失败也必须有明确原因，禁止向调用方返回伪造的 0。 */
public enum CalculationStatus { CALCULATED, NOT_APPLICABLE, INSUFFICIENT, CONFLICT }

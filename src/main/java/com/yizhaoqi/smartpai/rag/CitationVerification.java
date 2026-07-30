package com.yizhaoqi.smartpai.rag;

import java.util.List;

/** 引用校验结果；非法引用必须暴露给调用方而不是静默删除。 */
public record CitationVerification(boolean valid, List<String> citedIds, List<String> invalidIds, String reason) {
    public CitationVerification { citedIds = List.copyOf(citedIds); invalidIds = List.copyOf(invalidIds); }
}

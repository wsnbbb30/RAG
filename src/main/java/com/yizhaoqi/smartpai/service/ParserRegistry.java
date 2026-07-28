package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.parser.DocumentParser;
import org.springframework.stereotype.Component;

import java.util.List;

/** 根据文件类型选择解析器，找不到实现时明确失败，禁止悄悄返回空结果。 */
@Component
public class ParserRegistry {
    private final List<DocumentParser> parsers;
    public ParserRegistry(List<DocumentParser> parsers) { this.parsers = parsers; }
    public DocumentParser requireParser(String fileName, String contentType) {
        return parsers.stream().filter(p -> p.supports(fileName, contentType)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的文件类型: " + fileName));
    }
}
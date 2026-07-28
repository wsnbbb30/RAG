package com.yizhaoqi.smartpai.parser;

import java.util.List;

/** 一次完整解析的不可变输出。 */
public record ParseResult(ParserType parserType, String parserVersion, List<ParsedPage> pages) {
}
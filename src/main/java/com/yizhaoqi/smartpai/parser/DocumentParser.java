package com.yizhaoqi.smartpai.parser;

/**
 * 文档解析 SPI。
 * 新格式只需要新增实现类，不需要修改编排服务或 Kafka 消费者。
 */
public interface DocumentParser {
    ParserType type();
    boolean supports(String fileName, String contentType);
    ParseResult parse(ParseRequest request) throws Exception;
}
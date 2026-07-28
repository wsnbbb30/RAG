package com.yizhaoqi.smartpai.parser;

import java.io.InputStream;

/**
 * 解析请求；InputStream 的关闭责任属于调用方。
 * parser 不能缓存 InputStream，也不能调用 Repository。
 */
public record ParseRequest(Long versionId, String fileName, String contentType, InputStream inputStream) {
}
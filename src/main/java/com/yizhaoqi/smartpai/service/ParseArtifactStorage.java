package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.parser.ParseResult;

/** 大文件产物存储抽象；S1-02 先保存 manifest，S3-01 再增加表格 JSON 和页图。 */
public interface ParseArtifactStorage {
    String saveManifest(Long versionId, ParseResult result);
}
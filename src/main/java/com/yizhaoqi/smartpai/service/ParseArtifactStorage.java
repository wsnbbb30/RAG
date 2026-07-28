package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.parser.ParseResult;
import com.yizhaoqi.smartpai.parser.ParsedTable;

import java.util.List;

/** 大文件产物存储抽象；S1-02 先保存 manifest，S3-01 再增加表格 JSON 和页图。 */
public interface ParseArtifactStorage {
    String saveManifest(Long versionId, ParseResult result);

    /**
     * 表格 JSON/Markdown 是便于人工复核和离线评测的衍生产物；MySQL 表和单元格仍是真相源。
     * 默认实现避免旧存储适配器在升级时失效。
     */
    default String saveTables(Long versionId, List<ParsedTable> tables) { return null; }
}

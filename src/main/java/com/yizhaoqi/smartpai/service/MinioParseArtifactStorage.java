package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.parser.ParseResult;
import com.yizhaoqi.smartpai.parser.ParsedTable;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 使用 versionId 组织对象键，禁止使用原始文件名以避免同名覆盖。 */
@Service
public class MinioParseArtifactStorage implements ParseArtifactStorage {
    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;
    public MinioParseArtifactStorage(MinioClient minioClient, ObjectMapper objectMapper) {
        this.minioClient = minioClient; this.objectMapper = objectMapper;
    }
    @Override
    public String saveManifest(Long versionId, ParseResult result) {
        try {
            String objectKey = "documents/" + versionId + "/parse/manifest.json";
            byte[] payload = objectMapper.writeValueAsBytes(result);
            minioClient.putObject(PutObjectArgs.builder().bucket("uploads").object(objectKey)
                    .stream(new ByteArrayInputStream(payload), payload.length, -1)
                    .contentType("application/json").build());
            return objectKey;
        } catch (Exception exception) {
            throw new IllegalStateException("保存解析 manifest 失败", exception);
        }
    }

    @Override
    public String saveTables(Long versionId, List<ParsedTable> tables) {
        try {
            String objectKey = "documents/" + versionId + "/parse/tables.json";
            byte[] payload = objectMapper.writeValueAsBytes(tables);
            minioClient.putObject(PutObjectArgs.builder().bucket("uploads").object(objectKey)
                    .stream(new ByteArrayInputStream(payload), payload.length, -1)
                    .contentType("application/json").build());
            return objectKey;
        } catch (Exception exception) {
            throw new IllegalStateException("保存表格结构化产物失败", exception);
        }
    }
}

package com.yizhaoqi.smartpai.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件处理任务类，用于Kafka消息传递
 */
@Data
@NoArgsConstructor
public class FileProcessingTask {
    private String fileMd5; // 文件的 MD5 校验值
    private String filePath; // 文件存储路径
    private String fileName; // 文件名
    private String userId;   // 上传用户ID
    private String orgTag;   // 文件所属组织标签
    private boolean isPublic; // 文件是否公开
    /** 文档版本锚点；兼容历史 Kafka 消息时允许为空。 */
    private Long versionId;

    /** 兼容尚未携带 versionId 的历史生产者。 */
    public FileProcessingTask(String fileMd5, String filePath, String fileName,
                              String userId, String orgTag, boolean isPublic) {
        this(fileMd5, filePath, fileName, userId, orgTag, isPublic, null);
    }

    public FileProcessingTask(String fileMd5, String filePath, String fileName,
                              String userId, String orgTag, boolean isPublic, Long versionId) {
        this.fileMd5 = fileMd5;
        this.filePath = filePath;
        this.fileName = fileName;
        this.userId = userId;
        this.orgTag = orgTag;
        this.isPublic = isPublic;
        this.versionId = versionId;
    }
    
    /**
     * 向后兼容的构造函数
     */
    public FileProcessingTask(String fileMd5, String filePath, String fileName) {
        this.fileMd5 = fileMd5;
        this.filePath = filePath;
        this.fileName = fileName;
        this.userId = null;
        this.orgTag = "DEFAULT";
        this.isPublic = false;
        this.versionId = null;
    }
}

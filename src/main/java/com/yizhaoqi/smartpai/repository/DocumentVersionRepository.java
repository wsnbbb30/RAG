package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
    //按照文档ID + 唯一版本号查找
    Optional<DocumentVersion> findByDocumentIdAndVersionNo(String documentId, Integer versionNo);

    //查找某个逻辑文档的所有版本（按照版本号降序排列，最新在前）
    List<DocumentVersion> findByDocumentIdOrderByVersionNoDesc(String documentId);

    // 同一文件哈希可能属于不同逻辑文档，数据库只保证(file_md5, document_id)唯一，不能使用Optional。
    List<DocumentVersion> findAllByFileMd5(String fileMd5);

    /** 同一逻辑文档内的相同文件内容幂等查询。 */
    Optional<DocumentVersion> findByDocumentIdAndFileMd5(String documentId, String fileMd5);

    Optional<DocumentVersion> findTopByDocumentIdOrderByVersionNoDesc(String documentId);

    // 按状态查找（用于处理调度）
    List<DocumentVersion> findByStatus(DocumentVersion.ProcessingStatus status);

    // 统计某个逻辑文档的版本数
    long countByDocumentId(String documentId);
}

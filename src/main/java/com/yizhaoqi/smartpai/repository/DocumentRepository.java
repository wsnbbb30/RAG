package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.Document;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Document数据访问层
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    //按照自然键查找
    Optional<Document> findByDocumentId(String documentId);

    /**
     * 对同一逻辑文档加悲观锁，串行分配版本号，避免并发合并请求生成相同版本号。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.documentId = :documentId")
    Optional<Document> findByDocumentIdForUpdate(@Param("documentId") String documentId);

    //判断自然键是否已存在，用于去重
    boolean existsByDocumentId(String documentId);

    //按照公司名 + 财务年查找，用于展示某公司历史报告列表
    List<Document> findByCompanyNameAndFiscalYear(String companyName, Integer fiscalYear);

}

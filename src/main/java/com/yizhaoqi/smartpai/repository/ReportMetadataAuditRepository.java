package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.ReportMetadataAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 年报元数据审计记录访问层。 */
@Repository
public interface ReportMetadataAuditRepository extends JpaRepository<ReportMetadataAudit, Long> {

    List<ReportMetadataAudit> findByVersionIdOrderByCreatedAtAsc(Long versionId);
}

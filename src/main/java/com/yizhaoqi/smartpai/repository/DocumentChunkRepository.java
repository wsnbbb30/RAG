package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** DocumentChunk 数据访问层，所有查询必须携带 versionId，防止跨版本混用。 */
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
    List<DocumentChunk> findByVersionIdOrderByChunkNoAsc(Long versionId);
    void deleteByVersionId(Long versionId);
}

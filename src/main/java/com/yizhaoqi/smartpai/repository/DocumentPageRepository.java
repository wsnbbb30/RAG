package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.DocumentPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentPageRepository extends JpaRepository<DocumentPage, Long> {
    //根据文档ID找到所有DocumentPage，并升序排列
    List<DocumentPage> findByVersionIdOrderByPageNoAsc(Long versionId);
    void deleteByVersionId(Long versionId);
}

package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.TableModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 表格按版本查询，保证重试时可以先整体清理再写入。 */
public interface TableModelRepository extends JpaRepository<TableModel, Long> {
    List<TableModel> findByVersionIdOrderByPageStartAscIdAsc(Long versionId);
    void deleteByVersionId(Long versionId);
}

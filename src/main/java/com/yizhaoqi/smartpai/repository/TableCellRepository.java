package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.TableCell;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TableCellRepository extends JpaRepository<TableCell, Long> {
    List<TableCell> findByTableIdOrderByRowNoAscColumnNoAsc(Long tableId);
    void deleteByTableIdIn(List<Long> tableIds);
}

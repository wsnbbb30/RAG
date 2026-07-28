package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.DocumentElement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentElementRepository extends JpaRepository<DocumentElement, Long> {
    //根据页面id，升序返回该页面内的所有元素
    List<DocumentElement> findByPageIdOrderByOrderNoAsc(Long pageId);
    void deleteByPageIdIn(List<Long> pageIds);
}

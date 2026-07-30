package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.ChunkRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

/** 删除切块前先清理关系，避免外键与孤儿关系问题。 */
public interface ChunkRelationRepository extends JpaRepository<ChunkRelation, Long> {
    void deleteBySourceChunkIdInOrTargetChunkIdIn(Collection<Long> sourceChunkIds, Collection<Long> targetChunkIds);
}

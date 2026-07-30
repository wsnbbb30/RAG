package com.yizhaoqi.smartpai.chunk;

import com.yizhaoqi.smartpai.model.ChunkRelation;

/** 使用草稿编号表示关系；应用服务写库后映射为真实 DocumentChunk ID。 */
public record ChunkRelationDraft(int sourceDraftNo, int targetDraftNo,
                                 ChunkRelation.RelationType relationType) { }

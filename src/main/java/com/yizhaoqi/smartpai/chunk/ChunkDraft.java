package com.yizhaoqi.smartpai.chunk;

import com.yizhaoqi.smartpai.model.DocumentChunk;

import java.util.List;

/** 尚未写入数据库的切块草稿，parentDraftNo 指向本次运行中的草稿序号。 */
public record ChunkDraft(DocumentChunk.ChunkType chunkType, String content, int tokenCount,
                         int pageStart, int pageEnd, List<Long> sourceElementIds,
                         Integer parentDraftNo) { }

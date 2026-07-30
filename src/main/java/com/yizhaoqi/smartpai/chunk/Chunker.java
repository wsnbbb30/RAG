package com.yizhaoqi.smartpai.chunk;

import java.util.List;

/** 结构化切块算法 SPI，便于后续 A/B 对照旧字符切块器与不同策略。 */
public interface Chunker {
    ChunkingResult chunk(List<ChunkSourceElement> orderedElements, ChunkingPolicy policy);

    record ChunkingResult(List<ChunkDraft> chunks, List<ChunkRelationDraft> relations) { }
}

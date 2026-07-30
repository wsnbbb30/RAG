package com.yizhaoqi.smartpai.chunk;

import com.yizhaoqi.smartpai.model.ChunkRelation;
import com.yizhaoqi.smartpai.model.DocumentChunk;
import com.yizhaoqi.smartpai.model.DocumentElement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证纯算法契约；不加载 Spring，也不依赖数据库或 PDF 文件。 */
class StructureAwareChunkerTest {

    private final StructureAwareChunker chunker = new StructureAwareChunker(new CharacterTokenCounter());
    private final ChunkingPolicy policy = new ChunkingPolicy("test-v1", 4, 30, 6, 50);

    @Test
    void keepsTitleParagraphAndTableBoundariesAndBuildsRelations() {
        List<ChunkSourceElement> elements = List.of(
                element(1L, 1, 1, DocumentElement.ElementType.TITLE, "一、经营情况讨论"),
                element(2L, 1, 2, DocumentElement.ElementType.PARAGRAPH, "公司主营业务保持稳定增长，经营现金流持续改善。"),
                element(3L, 1, 3, DocumentElement.ElementType.PARAGRAPH, "报告期内收入和利润同比增加，毛利率稳步提升。"),
                element(4L, 2, 1, DocumentElement.ElementType.TABLE, "项目 2023年 2022年\n营业收入 100 90"));

        Chunker.ChunkingResult result = chunker.chunk(elements, policy);

        assertEquals(4, result.chunks().size(), "应生成一个父块、两个正文子块和一个表格子块");
        assertEquals(DocumentChunk.ChunkType.PARENT, result.chunks().get(0).chunkType());
        assertEquals(DocumentChunk.ChunkType.TEXT, result.chunks().get(1).chunkType());
        assertEquals(DocumentChunk.ChunkType.TEXT, result.chunks().get(2).chunkType());
        assertEquals(DocumentChunk.ChunkType.TABLE, result.chunks().get(3).chunkType());
        assertEquals(0, result.chunks().get(1).parentDraftNo());
        assertEquals(0, result.chunks().get(3).parentDraftNo());
        assertTrue(result.chunks().get(2).content().contains("持续改善"), "第二正文块应包含前一块的 overlap");
        assertTrue(result.relations().stream().anyMatch(relation -> relation.relationType() == ChunkRelation.RelationType.PARENT));
        assertTrue(result.relations().stream().anyMatch(relation -> relation.relationType() == ChunkRelation.RelationType.CHILD));
        assertTrue(result.relations().stream().anyMatch(relation -> relation.relationType() == ChunkRelation.RelationType.PREV));
        assertTrue(result.relations().stream().anyMatch(relation -> relation.relationType() == ChunkRelation.RelationType.NEXT));
    }

    @Test
    void createsParentForContentBeforeFirstTitle() {
        Chunker.ChunkingResult result = chunker.chunk(List.of(
                element(10L, 1, 1, DocumentElement.ElementType.PARAGRAPH, "这是封面之后、正文标题之前的说明文字。")), policy);

        assertEquals(2, result.chunks().size());
        assertEquals(DocumentChunk.ChunkType.PARENT, result.chunks().get(0).chunkType());
        assertEquals("未命名章节", result.chunks().get(0).content().split("\n")[0]);
    }

    @Test
    void splitsOversizedParagraphWithoutExceedingTokenLimit() {
        ChunkingPolicy smallPolicy = new ChunkingPolicy("test-v1", 4, 12, 3, 15);
        String longParagraph = "这是一个非常长的段落内容。它需要按照句子和令牌上限拆分。拆分后不能出现超长子块。";
        Chunker.ChunkingResult result = chunker.chunk(List.of(
                element(20L, 1, 1, DocumentElement.ElementType.TITLE, "长段落"),
                element(21L, 1, 2, DocumentElement.ElementType.PARAGRAPH, longParagraph)), smallPolicy);

        List<ChunkDraft> textChunks = result.chunks().stream()
                .filter(chunk -> chunk.chunkType() == DocumentChunk.ChunkType.TEXT).toList();
        assertTrue(textChunks.size() > 1, "超长段落必须被拆成多个正文块");
        assertTrue(textChunks.stream().allMatch(chunk -> chunk.tokenCount() <= smallPolicy.maxTokens()),
                "含 overlap 的最终正文块也不能超过 maxTokens");
    }

    private ChunkSourceElement element(Long id, int pageNo, int orderNo,
                                       DocumentElement.ElementType type, String text) {
        return new ChunkSourceElement(id, pageNo, orderNo, type, text);
    }
}

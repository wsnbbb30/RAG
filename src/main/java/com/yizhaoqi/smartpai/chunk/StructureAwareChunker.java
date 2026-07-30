package com.yizhaoqi.smartpai.chunk;

import com.yizhaoqi.smartpai.model.ChunkRelation;
import com.yizhaoqi.smartpai.model.DocumentChunk;
import com.yizhaoqi.smartpai.model.DocumentElement;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 标题、段落、表格感知切块器。
 *
 * <p>标题开启新章节；每个章节先创建一个 PARENT 块，再创建 TEXT/TABLE 子块。
 * TABLE 永远独立，普通正文仅在超过 maxTokens 时才于元素边界换块。换块后把前一块末尾
 * 文本前置到下一块，保留可配置 overlap，从而降低跨块语义丢失。</p>
 */
@Component
public class StructureAwareChunker implements Chunker {
    private final TokenCounter tokenCounter;

    public StructureAwareChunker(TokenCounter tokenCounter) {
        this.tokenCounter = tokenCounter;
    }

    @Override
    public ChunkingResult chunk(List<ChunkSourceElement> orderedElements, ChunkingPolicy policy) {
        List<ChunkDraft> drafts = new ArrayList<>();
        List<ChunkRelationDraft> relations = new ArrayList<>();
        List<ChunkSourceElement> sectionElements = new ArrayList<>();
        ChunkSourceElement currentTitle = null;

        for (ChunkSourceElement element : orderedElements) {
            if (!element.hasText()) {
                continue;
            }
            if (element.elementType() == DocumentElement.ElementType.TITLE) {
                flushSection(currentTitle, sectionElements, policy, drafts, relations);
                sectionElements.clear();
                currentTitle = element;
            } else {
                sectionElements.add(element);
            }
        }
        flushSection(currentTitle, sectionElements, policy, drafts, relations);
        linkAdjacentChildren(drafts, relations);
        return new ChunkingResult(List.copyOf(drafts), List.copyOf(relations));
    }

    private void flushSection(ChunkSourceElement title, List<ChunkSourceElement> elements, ChunkingPolicy policy,
                              List<ChunkDraft> drafts, List<ChunkRelationDraft> relations) {
        if (title == null && elements.isEmpty()) {
            return;
        }
        int parentDraftNo = drafts.size();
        String parentText = buildParentText(title, elements);
        int pageStart = title != null ? title.pageNo() : elements.get(0).pageNo();
        int pageEnd = elements.isEmpty() ? pageStart : elements.get(elements.size() - 1).pageNo();
        List<Long> parentElementIds = new ArrayList<>();
        if (title != null && title.id() != null) parentElementIds.add(title.id());
        elements.stream().map(ChunkSourceElement::id).filter(id -> id != null).forEach(parentElementIds::add);
        drafts.add(new ChunkDraft(DocumentChunk.ChunkType.PARENT, parentText, tokenCounter.count(parentText),
                pageStart, pageEnd, List.copyOf(parentElementIds), null));

        List<ChunkSourceElement> textBuffer = new ArrayList<>();
        String previousTextChunk = "";
        for (ChunkSourceElement element : elements) {
            if (element.elementType() == DocumentElement.ElementType.TABLE) {
                previousTextChunk = flushTextBuffer(textBuffer, previousTextChunk, parentDraftNo,
                        policy, drafts, relations);
                addTableChunk(element, parentDraftNo, drafts, relations);
                // 表格是强语义边界，后续正文不应携带表格前段落的 overlap。
                previousTextChunk = "";
                continue;
            }
            int elementTokens = tokenCounter.count(element.textContent());
            int bodyTokenLimit = previousTextChunk.isBlank()
                    ? policy.maxTokens() : policy.maxTokens() - policy.overlapTokens();
            if (elementTokens > policy.oversizedElementTokens() || elementTokens > bodyTokenLimit) {
                previousTextChunk = flushTextBuffer(textBuffer, previousTextChunk, parentDraftNo,
                        policy, drafts, relations);
                previousTextChunk = addOversizedElementChunks(element, previousTextChunk, parentDraftNo,
                        policy, drafts, relations);
                continue;
            }
            if (!textBuffer.isEmpty() && tokenCounter.count(join(textBuffer, element)) > bodyTokenLimit) {
                previousTextChunk = flushTextBuffer(textBuffer, previousTextChunk, parentDraftNo,
                        policy, drafts, relations);
                // 换块后会增加 overlap，重新校验单个元素能否放入新的正文预算。
                if (elementTokens > policy.maxTokens() - policy.overlapTokens()) {
                    previousTextChunk = addOversizedElementChunks(element, previousTextChunk, parentDraftNo,
                            policy, drafts, relations);
                    continue;
                }
            }
            textBuffer.add(element);
        }
        flushTextBuffer(textBuffer, previousTextChunk, parentDraftNo, policy, drafts, relations);
    }

    /** 写入一个正文草稿，并返回本块正文以便下一个块构造 overlap。 */
    private String flushTextBuffer(List<ChunkSourceElement> buffer, String previousTextChunk, int parentDraftNo,
                                   ChunkingPolicy policy, List<ChunkDraft> drafts,
                                   List<ChunkRelationDraft> relations) {
        if (buffer.isEmpty()) {
            return previousTextChunk;
        }
        String body = join(buffer, null);
        String overlap = tokenCounter.tail(previousTextChunk, policy.overlapTokens());
        String content = overlap.isBlank() ? body : overlap + "\n" + body;
        int draftNo = drafts.size();
        List<Long> elementIds = buffer.stream().map(ChunkSourceElement::id).filter(id -> id != null).toList();
        drafts.add(new ChunkDraft(DocumentChunk.ChunkType.TEXT, content, tokenCounter.count(content),
                buffer.get(0).pageNo(), buffer.get(buffer.size() - 1).pageNo(), elementIds, parentDraftNo));
        addParentChildRelations(draftNo, parentDraftNo, relations);
        buffer.clear();
        return body;
    }

    /**
     * 单个段落超过阈值时按句子拆分；没有句号或单句仍过长时再按 token 上限硬切。
     * 每个子块仍记录同一个来源 elementId，因此证据链不会断裂。
     */
    private String addOversizedElementChunks(ChunkSourceElement element, String previousTextChunk, int parentDraftNo,
                                             ChunkingPolicy policy, List<ChunkDraft> drafts,
                                             List<ChunkRelationDraft> relations) {
        String previousBody = previousTextChunk;
        // 预留 overlap token，保证“overlap + 正文”整体仍不超过 maxTokens。
        int bodyLimit = policy.maxTokens() - policy.overlapTokens();
        for (String body : splitIntoTokenBoundedParts(element.textContent(), bodyLimit)) {
            String overlap = tokenCounter.tail(previousBody, policy.overlapTokens());
            String content = overlap.isBlank() ? body : overlap + "\n" + body;
            int draftNo = drafts.size();
            List<Long> ids = element.id() == null ? List.of() : List.of(element.id());
            drafts.add(new ChunkDraft(DocumentChunk.ChunkType.TEXT, content, tokenCounter.count(content),
                    element.pageNo(), element.pageNo(), ids, parentDraftNo));
            addParentChildRelations(draftNo, parentDraftNo, relations);
            previousBody = body;
        }
        return previousBody;
    }

    private List<String> splitIntoTokenBoundedParts(String text, int maxTokens) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : text.split("(?<=[。！？；.!?;])")) {
            String normalized = sentence.trim();
            if (normalized.isBlank()) continue;
            if (tokenCounter.count(normalized) > maxTokens) {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                result.addAll(hardSplit(normalized, maxTokens));
            } else if (!current.isEmpty() && tokenCounter.count(current + "\n" + normalized) > maxTokens) {
                result.add(current.toString());
                current.setLength(0);
                current.append(normalized);
            } else {
                if (!current.isEmpty()) current.append('\n');
                current.append(normalized);
            }
        }
        if (!current.isEmpty()) result.add(current.toString());
        return result;
    }

    /** 使用二分查找求不超 token 上限的最大字符前缀，兼容未来替换的精确 tokenizer。 */
    private List<String> hardSplit(String text, int maxTokens) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int low = start + 1;
            int high = text.length();
            int best = low;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                if (tokenCounter.count(text.substring(start, middle)) <= maxTokens) {
                    best = middle;
                    low = middle + 1;
                } else {
                    high = middle - 1;
                }
            }
            result.add(text.substring(start, best));
            start = best;
        }
        return result;
    }

    private void addTableChunk(ChunkSourceElement element, int parentDraftNo,
                               List<ChunkDraft> drafts, List<ChunkRelationDraft> relations) {
        int draftNo = drafts.size();
        List<Long> ids = element.id() == null ? List.of() : List.of(element.id());
        drafts.add(new ChunkDraft(DocumentChunk.ChunkType.TABLE, element.textContent(),
                tokenCounter.count(element.textContent()), element.pageNo(), element.pageNo(), ids, parentDraftNo));
        addParentChildRelations(draftNo, parentDraftNo, relations);
    }

    private void addParentChildRelations(int childDraftNo, int parentDraftNo, List<ChunkRelationDraft> relations) {
        relations.add(new ChunkRelationDraft(childDraftNo, parentDraftNo, ChunkRelation.RelationType.PARENT));
        relations.add(new ChunkRelationDraft(parentDraftNo, childDraftNo, ChunkRelation.RelationType.CHILD));
    }

    /** 相邻的所有非父块建立 PREV/NEXT，检索命中后可扩展局部上下文。 */
    private void linkAdjacentChildren(List<ChunkDraft> drafts, List<ChunkRelationDraft> relations) {
        Integer previousChild = null;
        for (int current = 0; current < drafts.size(); current++) {
            if (drafts.get(current).chunkType() == DocumentChunk.ChunkType.PARENT) continue;
            if (previousChild != null) {
                relations.add(new ChunkRelationDraft(previousChild, current, ChunkRelation.RelationType.NEXT));
                relations.add(new ChunkRelationDraft(current, previousChild, ChunkRelation.RelationType.PREV));
            }
            previousChild = current;
        }
    }

    private String buildParentText(ChunkSourceElement title, List<ChunkSourceElement> elements) {
        String heading = title == null ? "未命名章节" : title.textContent();
        String body = join(elements, null);
        return body.isBlank() ? heading : heading + "\n" + body;
    }

    private String join(List<ChunkSourceElement> elements, ChunkSourceElement append) {
        List<ChunkSourceElement> all = new ArrayList<>(elements);
        if (append != null) all.add(append);
        return all.stream().map(ChunkSourceElement::textContent).filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n" + right).orElse("");
    }
}

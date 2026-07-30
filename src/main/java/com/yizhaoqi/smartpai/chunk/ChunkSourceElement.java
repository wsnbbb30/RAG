package com.yizhaoqi.smartpai.chunk;

import com.yizhaoqi.smartpai.model.DocumentElement;

/**
 * 切块器的输入投影，不暴露 JPA 实体和数据库关联。
 * 这样算法层无需关心 pageId、持久化上下文或可能产生的 N+1 查询。
 */
public record ChunkSourceElement(Long id, int pageNo, int orderNo,
                                 DocumentElement.ElementType elementType, String textContent) {
    public boolean hasText() {
        return textContent != null && !textContent.isBlank();
    }
}

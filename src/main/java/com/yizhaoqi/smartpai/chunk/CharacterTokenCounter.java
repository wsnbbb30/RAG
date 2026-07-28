package com.yizhaoqi.smartpai.chunk;

import org.springframework.stereotype.Component;

/**
 * tokenizer 不可用时的降级实现：中文字符逐个计数，连续英文/数字按一个词计数。
 * 仅服务于切块边界，不得用于模型账单、成本统计或不同模型间的精确比较。
 */
@Component
public class CharacterTokenCounter implements TokenCounter {
    @Override
    public String tokenizerId() {
        return "character-fallback-v1";
    }

    @Override
    public int count(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int count = 0;
        boolean inLatinWord = false;
        for (char character : text.trim().toCharArray()) {
            if (Character.isWhitespace(character)) {
                inLatinWord = false;
            } else if (character <= 127 && Character.isLetterOrDigit(character)) {
                if (!inLatinWord) {
                    count++;
                    inLatinWord = true;
                }
            } else {
                count++;
                inLatinWord = false;
            }
        }
        return count;
    }

    @Override
    public String tail(String text, int maxTokens) {
        if (text == null || text.isBlank() || maxTokens <= 0) {
            return "";
        }
        // 逐步缩小字符串起点。该实现是降级近似，模型 tokenizer 接入后会覆盖此方法。
        String normalized = text.trim();
        int start = normalized.length();
        int tokenCount = 0;
        boolean inLatinWord = false;
        for (int index = normalized.length() - 1; index >= 0; index--) {
            char character = normalized.charAt(index);
            if (Character.isWhitespace(character)) {
                inLatinWord = false;
                start = index;
                continue;
            }
            boolean latin = character <= 127 && Character.isLetterOrDigit(character);
            boolean startsToken = latin ? !inLatinWord : true;
            if (startsToken && tokenCount >= maxTokens) {
                break;
            }
            if (startsToken) {
                tokenCount++;
            }
            inLatinWord = latin;
            start = index;
        }
        return normalized.substring(start).trim();
    }
}

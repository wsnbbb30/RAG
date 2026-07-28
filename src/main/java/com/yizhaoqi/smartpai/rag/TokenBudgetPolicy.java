package com.yizhaoqi.smartpai.rag;

import org.springframework.stereotype.Component;

/**
 * 近似 token 计数器。中文按约 0.6 token/字符、ASCII 单词按约 0.3 token/字符估计，
 * 用于上下文预算保护；后续接入 Provider tokenizer 时只替换本策略，不影响 EvidenceAssembler。
 */
@Component
public class TokenBudgetPolicy {
    public int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        int chinese = 0;
        int other = 0;
        for (char character : text.toCharArray()) {
            if (Character.UnicodeScript.of(character) == Character.UnicodeScript.HAN) chinese++;
            else other++;
        }
        return (int) Math.ceil(chinese * 0.6D + other * 0.3D);
    }
}

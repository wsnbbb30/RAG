package com.yizhaoqi.smartpai.chunk;

/**
 * 不可变切块配置值对象。
 * 脱离 Spring 配置后仍可被单测、离线评测和批任务稳定复放。
 */
public record ChunkingPolicy(String version, int minTokens, int maxTokens,
                             int overlapTokens, int oversizedElementTokens) {
    public ChunkingPolicy {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("chunkerVersion 不能为空");
        }
        if (minTokens <= 0 || maxTokens < minTokens || overlapTokens < 0 || overlapTokens >= maxTokens) {
            throw new IllegalArgumentException("切块策略参数非法");
        }
    }
}

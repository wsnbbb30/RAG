package com.yizhaoqi.smartpai.finance;

/** 待发送给用户的草稿答案；核验器只校验该不可变快照。 */
public record DraftAnswer(String content) { }

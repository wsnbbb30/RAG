package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.service.RagAnswerService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * S2-04 的证据优先问答接口。
 *
 * <p>与兼容历史前端的流式会话接口分离：该接口的回答、证据、引用校验结果和降级状态
 * 一起返回，方便调用方明确展示“答案来自哪里”。</p>
 */
@RestController
@RequestMapping("/api/v1/rag")
public class RagController {
    private static final int DEFAULT_TOP_K = 10;
    private static final int MAX_TOP_K = 20;

    private final RagAnswerService ragAnswerService;

    public RagController(RagAnswerService ragAnswerService) {
        this.ragAnswerService = ragAnswerService;
    }

    @PostMapping("/answer")
    public Map<String, Object> answer(@RequestBody RagQuestionRequest request,
                                      @RequestAttribute(value = "userId", required = false) String userId) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            return Map.of("code", HttpStatus.BAD_REQUEST.value(), "message", "question 不能为空", "data", List.of());
        }
        int topK = request.topK() == null ? DEFAULT_TOP_K : request.topK();
        if (topK < 1 || topK > MAX_TOP_K) {
            return Map.of("code", HttpStatus.BAD_REQUEST.value(), "message", "topK 必须在 1 到 20 之间", "data", List.of());
        }
        return Map.of("code", HttpStatus.OK.value(), "message", "success",
                "data", ragAnswerService.answer(request.question().trim(), userId, topK));
    }

    /** 仅承载接口输入，避免 Controller 与检索/证据领域对象耦合。 */
    public record RagQuestionRequest(String question, Integer topK) { }
}

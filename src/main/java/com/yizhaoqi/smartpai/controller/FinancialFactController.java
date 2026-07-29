package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.model.dto.FinancialFactReviewRequest;
import com.yizhaoqi.smartpai.service.FinancialFactReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** S3-02 复核接口；生产环境应由现有 RBAC 配置限制为数据审核角色。 */
@RestController
@RequestMapping("/api/v1/financial-facts")
public class FinancialFactController {
    private final FinancialFactReviewService reviewService;
    public FinancialFactController(FinancialFactReviewService reviewService) { this.reviewService = reviewService; }

    @GetMapping("/pending")
    public Map<String, Object> pending() {
        return Map.of("code", HttpStatus.OK.value(), "message", "success", "data", reviewService.pending());
    }

    @PostMapping("/{factId}/review")
    public Map<String, Object> review(@PathVariable Long factId, @RequestBody FinancialFactReviewRequest request) {
        try {
            return Map.of("code", HttpStatus.OK.value(), "message", "success", "data", reviewService.review(factId, request));
        } catch (IllegalArgumentException exception) {
            return Map.of("code", HttpStatus.BAD_REQUEST.value(), "message", exception.getMessage(), "data", List.of());
        }
    }
}

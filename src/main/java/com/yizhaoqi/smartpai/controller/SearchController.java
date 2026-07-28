package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.service.HybridSearchService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** S2-01 独立召回接口；名称保留 hybrid 以兼容已有调用方，RRF 融合将在 S2-02 接入。 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {
    private final HybridSearchService searchService;

    public SearchController(HybridSearchService searchService) { this.searchService = searchService; }

    @GetMapping("/hybrid")
    public Map<String, Object> hybridSearch(@RequestParam String query,
                                             @RequestParam(defaultValue = "10") int topK,
                                             @RequestAttribute(value = "userId", required = false) String userId) {
        try {
            HybridSearchService.RetrievalResponse response = searchService.retrieveWithPermission(query, userId, topK);
            return Map.of("code", HttpStatus.OK.value(), "message", "success", "data", response.candidates(),
                    "traceId", response.traceId(), "filters", response.filters(), "routes", response.routes(),
                    "rerank", response.rerank(), "degraded", response.degraded());
        } catch (IllegalArgumentException exception) {
            return Map.of("code", HttpStatus.BAD_REQUEST.value(), "message", exception.getMessage(), "data", java.util.List.of());
        }
    }
}

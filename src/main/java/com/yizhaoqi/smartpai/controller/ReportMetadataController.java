package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.model.dto.ReportMetadataDTO;
import com.yizhaoqi.smartpai.model.dto.ReportMetadataReviewRequest;
import com.yizhaoqi.smartpai.service.ReportMetadataReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 年报元数据查询与人工复核接口。 */
@RestController
@RequestMapping("/api/v1/reports/metadata")
public class ReportMetadataController {
    private final ReportMetadataReviewService reviewService;
    public ReportMetadataController(ReportMetadataReviewService reviewService) { this.reviewService = reviewService; }
    @GetMapping("/{versionId}")
    public ReportMetadataDTO get(@PathVariable Long versionId) { return reviewService.get(versionId); }
    @PutMapping("/review")
    public ResponseEntity<ReportMetadataDTO> review(@RequestBody ReportMetadataReviewRequest request,
                                                    @RequestAttribute("userId") String userId) {
        return ResponseEntity.ok(reviewService.review(request, userId));
    }
}

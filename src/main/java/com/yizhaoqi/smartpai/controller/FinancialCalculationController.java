package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.finance.CalculationDimensions;
import com.yizhaoqi.smartpai.model.FinancialReportMetadata;
import com.yizhaoqi.smartpai.service.FinancialCalculator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 暴露确定性计算结果及其 trace；前端不得自行对事实金额重复计算。
 */
@RestController
@RequestMapping("/api/v1/financial-calculations")
public class FinancialCalculationController {
    private final FinancialCalculator calculator;
    public FinancialCalculationController(FinancialCalculator calculator) { this.calculator = calculator; }

    @GetMapping
    public Map<String, Object> calculate(@RequestParam String metricCode, @RequestParam Long versionId,
                                         @RequestParam String period, @RequestParam(defaultValue = "CONSOLIDATED") String scope) {
        try {
            FinancialReportMetadata.ReportScope reportScope = FinancialReportMetadata.ReportScope.valueOf(scope);
            return Map.of("code", HttpStatus.OK.value(), "message", "success", "data",
                    calculator.calculate(metricCode, new CalculationDimensions(versionId, period, reportScope)));
        } catch (IllegalArgumentException exception) {
            return Map.of("code", HttpStatus.BAD_REQUEST.value(), "message", "scope 必须为 CONSOLIDATED 或 PARENT_COMPANY", "data", Map.of());
        }
    }
}

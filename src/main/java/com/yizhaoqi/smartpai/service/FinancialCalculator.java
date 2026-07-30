package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.finance.*;
import com.yizhaoqi.smartpai.model.FinancialFact;
import com.yizhaoqi.smartpai.repository.FinancialFactRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * S3-03 确定性财务计算引擎。
 * 大模型只能解释本类输出，不参与四则运算或单位换算。
 */
@Service
public class FinancialCalculator {
    private final FinancialFactRepository factRepository;
    private final FormulaRegistry formulaRegistry;
    public FinancialCalculator(FinancialFactRepository factRepository, FormulaRegistry formulaRegistry) {
        this.factRepository = factRepository; this.formulaRegistry = formulaRegistry;
    }

    public CalculationResult calculate(String metricCode, CalculationDimensions dimensions) {
        FormulaRegistry.Formula formula = formulaRegistry.find(metricCode).orElse(null);
        if (formula == null) return new CalculationResult(metricCode, CalculationStatus.NOT_APPLICABLE, null, null,
                "未登记该派生指标的计算公式", null);
        if (dimensions == null || dimensions.versionId() == null || dimensions.period() == null || dimensions.scope() == null) {
            return new CalculationResult(metricCode, CalculationStatus.INSUFFICIENT, null, formula.unit(), "缺少版本、期间或口径", null);
        }
        return switch (metricCode) {
            // ── 增长率类 ──
            case "REVENUE_GROWTH_RATE" -> growthMetric(metricCode, formula, dimensions, "OPERATING_REVENUE");
            case "NET_PROFIT_GROWTH" -> growthMetric(metricCode, formula, dimensions, "NET_PROFIT");
            case "CASH_FLOW_GROWTH" -> growthMetric(metricCode, formula, dimensions, "OPERATING_CASH_FLOW");
            case "RECEIVABLES_GROWTH" -> growthMetric(metricCode, formula, dimensions, "ACCOUNTS_RECEIVABLE");

            // ── 利润率类 ──
            case "NET_PROFIT_MARGIN" -> ratio(metricCode, formula, dimensions, "NET_PROFIT", dimensions.period(), "OPERATING_REVENUE", dimensions.period());
            case "GROSS_PROFIT_MARGIN" -> grossProfitMargin(metricCode, formula, dimensions);
            case "OPERATING_PROFIT_MARGIN" -> ratio(metricCode, formula, dimensions, "OPERATING_PROFIT", dimensions.period(), "OPERATING_REVENUE", dimensions.period());
            case "TOTAL_PROFIT_MARGIN" -> ratio(metricCode, formula, dimensions, "TOTAL_PROFIT", dimensions.period(), "OPERATING_REVENUE", dimensions.period());
            case "OPERATING_COST_MARGIN" -> ratio(metricCode, formula, dimensions, "OPERATING_COST", dimensions.period(), "OPERATING_REVENUE", dimensions.period());

            // ── 费用率类 ──
            case "R_AND_D_EXPENSE_RATIO" -> ratio(metricCode, formula, dimensions, "R_AND_D_EXPENSE", dimensions.period(), "OPERATING_REVENUE", dimensions.period());
            case "SELLING_EXPENSE_RATIO" -> ratio(metricCode, formula, dimensions, "SELLING_EXPENSE", dimensions.period(), "OPERATING_REVENUE", dimensions.period());
            case "MANAGEMENT_EXPENSE_RATIO" -> ratio(metricCode, formula, dimensions, "MANAGEMENT_EXPENSE", dimensions.period(), "OPERATING_REVENUE", dimensions.period());
            case "FINANCIAL_EXPENSE_RATIO" -> ratio(metricCode, formula, dimensions, "FINANCIAL_EXPENSE", dimensions.period(), "OPERATING_REVENUE", dimensions.period());
            case "PERIOD_EXPENSE_RATIO" -> periodExpenseRatio(metricCode, formula, dimensions);

            // ── 偿债能力类 ──
            case "DEBT_TO_ASSET_RATIO" -> ratio(metricCode, formula, dimensions, "TOTAL_LIABILITIES", dimensions.period(), "TOTAL_ASSETS", dimensions.period());
            case "CURRENT_RATIO" -> multiple(metricCode, formula, dimensions, "CURRENT_ASSETS", "CURRENT_LIABILITIES");
            case "QUICK_RATIO" -> quickRatio(metricCode, formula, dimensions);
            case "DEBT_TO_EQUITY_RATIO" -> ratio(metricCode, formula, dimensions, "TOTAL_LIABILITIES", dimensions.period(), "PARENT_EQUITY", dimensions.period());
            case "EQUITY_MULTIPLIER" -> multiple(metricCode, formula, dimensions, "TOTAL_ASSETS", "PARENT_EQUITY");

            // ── 营运能力类 ──
            case "ASSET_TURNOVER" -> multiple(metricCode, formula, dimensions, "OPERATING_REVENUE", "TOTAL_ASSETS");
            case "CURRENT_ASSET_TURNOVER" -> multiple(metricCode, formula, dimensions, "OPERATING_REVENUE", "CURRENT_ASSETS");
            case "RECEIVABLES_TURNOVER" -> multiple(metricCode, formula, dimensions, "OPERATING_REVENUE", "ACCOUNTS_RECEIVABLE");
            case "PAYABLES_TURNOVER" -> multiple(metricCode, formula, dimensions, "OPERATING_COST", "ACCOUNTS_PAYABLE");
            case "INVENTORY_TURNOVER_DAYS" -> turnoverDays(metricCode, formula, dimensions, "INVENTORY", "OPERATING_COST");
            case "RECEIVABLES_TURNOVER_DAYS" -> turnoverDays(metricCode, formula, dimensions, "ACCOUNTS_RECEIVABLE", "OPERATING_REVENUE");
            case "PAYABLES_TURNOVER_DAYS" -> turnoverDays(metricCode, formula, dimensions, "ACCOUNTS_PAYABLE", "OPERATING_COST");
            case "OPERATING_CYCLE" -> operatingCycle(metricCode, formula, dimensions);

            // ── 收益率类 ──
            case "RETURN_ON_ASSETS" -> ratio(metricCode, formula, dimensions, "NET_PROFIT", dimensions.period(), "TOTAL_ASSETS", dimensions.period());
            case "RETURN_ON_EQUITY" -> ratio(metricCode, formula, dimensions, "NET_PROFIT", dimensions.period(), "PARENT_EQUITY", dimensions.period());

            // ── 现金流比率类 ──
            case "OPERATING_CASH_FLOW_TO_REVENUE" -> ratio(metricCode, formula, dimensions, "OPERATING_CASH_FLOW", dimensions.period(), "OPERATING_REVENUE", dimensions.period());
            case "OPERATING_CASH_FLOW_TO_NET_PROFIT" -> multiple(metricCode, formula, dimensions, "OPERATING_CASH_FLOW", "NET_PROFIT");
            case "CASH_RECEIVED_TO_REVENUE" -> ratio(metricCode, formula, dimensions, "CASH_RECEIVED_FROM_SALES", dimensions.period(), "OPERATING_REVENUE", dimensions.period());

            // ── 结构比率类 ──
            case "NON_CURRENT_ASSET_RATIO" -> ratio(metricCode, formula, dimensions, "NON_CURRENT_ASSETS", dimensions.period(), "TOTAL_ASSETS", dimensions.period());
            case "CURRENT_ASSET_RATIO" -> ratio(metricCode, formula, dimensions, "CURRENT_ASSETS", dimensions.period(), "TOTAL_ASSETS", dimensions.period());
            case "FIXED_ASSET_RATIO" -> ratio(metricCode, formula, dimensions, "FIXED_ASSETS", dimensions.period(), "TOTAL_ASSETS", dimensions.period());
            case "RECEIVABLES_TO_ASSET" -> ratio(metricCode, formula, dimensions, "ACCOUNTS_RECEIVABLE", dimensions.period(), "TOTAL_ASSETS", dimensions.period());
            case "INVENTORY_TO_ASSET" -> ratio(metricCode, formula, dimensions, "INVENTORY", dimensions.period(), "TOTAL_ASSETS", dimensions.period());
            case "GOODWILL_TO_ASSET" -> ratio(metricCode, formula, dimensions, "GOODWILL", dimensions.period(), "TOTAL_ASSETS", dimensions.period());
            case "PAYABLES_TO_LIABILITY" -> ratio(metricCode, formula, dimensions, "ACCOUNTS_PAYABLE", dimensions.period(), "TOTAL_LIABILITIES", dimensions.period());
            case "CURRENT_LIABILITY_RATIO" -> ratio(metricCode, formula, dimensions, "CURRENT_LIABILITIES", dimensions.period(), "TOTAL_LIABILITIES", dimensions.period());
            case "LONG_TERM_LIABILITY_RATIO" -> ratio(metricCode, formula, dimensions, "LONG_TERM_LIABILITIES", dimensions.period(), "TOTAL_LIABILITIES", dimensions.period());

            default -> new CalculationResult(metricCode, CalculationStatus.NOT_APPLICABLE, null, formula.unit(), "公式尚未实现", null);
        };
    }

    /** 倍数型指标不乘 100，例如流动比率和资产周转率。 */
    private CalculationResult multiple(String code, FormulaRegistry.Formula formula, CalculationDimensions dimensions,
                                       String numeratorCode, String denominatorCode) {
        Lookup numerator = one(dimensions, numeratorCode, dimensions.period());
        Lookup denominator = one(dimensions, denominatorCode, dimensions.period());
        if (numerator.conflict || denominator.conflict) return conflict(code, formula, "同一指标存在多个互相冲突的事实");
        if (numerator.fact == null || denominator.fact == null) return insufficient(code, formula, "缺少公式输入事实");
        if (denominator.fact.getValue().compareTo(BigDecimal.ZERO) == 0) return insufficient(code, formula, "分母为零，拒绝计算");
        return calculated(code, formula, DecimalPolicy.result(DecimalPolicy.divide(numerator.fact.getValue(), denominator.fact.getValue())), numerator.fact, denominator.fact);
    }

    private CalculationResult grossProfitMargin(String code, FormulaRegistry.Formula formula, CalculationDimensions dimensions) {
        Lookup revenue = one(dimensions, "OPERATING_REVENUE", dimensions.period());
        Lookup cost = one(dimensions, "OPERATING_COST", dimensions.period());
        if (revenue.conflict || cost.conflict) return conflict(code, formula, "同一指标存在多个互相冲突的事实");
        if (revenue.fact == null || cost.fact == null) return insufficient(code, formula, "缺少营业收入或营业成本");
        if (revenue.fact.getValue().compareTo(BigDecimal.ZERO) == 0) return insufficient(code, formula, "营业收入为零，拒绝计算");
        return calculated(code, formula, DecimalPolicy.result(DecimalPolicy.divide(revenue.fact.getValue().subtract(cost.fact.getValue()), revenue.fact.getValue()).multiply(new BigDecimal("100"))), revenue.fact, cost.fact);
    }

    private CalculationResult ratio(String code, FormulaRegistry.Formula formula, CalculationDimensions dimensions,
                                    String numeratorCode, String numeratorPeriod, String denominatorCode, String denominatorPeriod) {
        Lookup numerator = one(dimensions, numeratorCode, numeratorPeriod);
        Lookup denominator = one(dimensions, denominatorCode, denominatorPeriod);
        if (numerator.conflict || denominator.conflict) return conflict(code, formula, "同一指标存在多个互相冲突的事实");
        if (numerator.fact == null || denominator.fact == null) return insufficient(code, formula, "缺少公式输入事实");
        if (denominator.fact.getValue().compareTo(BigDecimal.ZERO) == 0) return insufficient(code, formula, "分母为零，拒绝计算");
        BigDecimal value = DecimalPolicy.result(DecimalPolicy.divide(numerator.fact.getValue(), denominator.fact.getValue()).multiply(new BigDecimal("100")));
        return calculated(code, formula, value, numerator.fact, denominator.fact);
    }

    /** 通用增长率：(current - previous) / |previous| * 100。 */
    private CalculationResult growthMetric(String code, FormulaRegistry.Formula formula, CalculationDimensions dimensions, String metricCode) {
        Lookup current = one(dimensions, metricCode, dimensions.period());
        Lookup previous = one(dimensions, metricCode, previousPeriod(dimensions.period()));
        if (current.conflict || previous.conflict) return conflict(code, formula, "同一期间存在冲突事实");
        if (current.fact == null || previous.fact == null) return insufficient(code, formula, "缺少本期或上期数据");
        if (previous.fact.getValue().compareTo(BigDecimal.ZERO) == 0) return insufficient(code, formula, "上期值为零，拒绝计算");
        BigDecimal value = DecimalPolicy.result(DecimalPolicy.divide(current.fact.getValue().subtract(previous.fact.getValue()), previous.fact.getValue().abs()).multiply(new BigDecimal("100")));
        return calculated(code, formula, value, current.fact, previous.fact);
    }

    /** 速动比率：(流动资产 - 存货) / 流动负债。 */
    private CalculationResult quickRatio(String code, FormulaRegistry.Formula formula, CalculationDimensions dimensions) {
        Lookup ca = one(dimensions, "CURRENT_ASSETS", dimensions.period());
        Lookup inv = one(dimensions, "INVENTORY", dimensions.period());
        Lookup cl = one(dimensions, "CURRENT_LIABILITIES", dimensions.period());
        if (ca.conflict || inv.conflict || cl.conflict) return conflict(code, formula, "同一指标存在多个互相冲突的事实");
        if (ca.fact == null || inv.fact == null || cl.fact == null) return insufficient(code, formula, "缺少公式输入事实");
        if (cl.fact.getValue().compareTo(BigDecimal.ZERO) == 0) return insufficient(code, formula, "分母为零，拒绝计算");
        BigDecimal value = DecimalPolicy.result(DecimalPolicy.divide(ca.fact.getValue().subtract(inv.fact.getValue()), cl.fact.getValue()));
        return calculated(code, formula, value, ca.fact, inv.fact, cl.fact);
    }

    /** 周转天数：365 * numerator / denominator。 */
    private CalculationResult turnoverDays(String code, FormulaRegistry.Formula formula, CalculationDimensions dimensions,
                                           String numeratorCode, String denominatorCode) {
        Lookup num = one(dimensions, numeratorCode, dimensions.period());
        Lookup den = one(dimensions, denominatorCode, dimensions.period());
        if (num.conflict || den.conflict) return conflict(code, formula, "同一指标存在多个互相冲突的事实");
        if (num.fact == null || den.fact == null) return insufficient(code, formula, "缺少公式输入事实");
        if (den.fact.getValue().compareTo(BigDecimal.ZERO) == 0) return insufficient(code, formula, "分母为零，拒绝计算");
        BigDecimal value = DecimalPolicy.result(DecimalPolicy.divide(num.fact.getValue(), den.fact.getValue()).multiply(new BigDecimal("365")));
        return calculated(code, formula, value, num.fact, den.fact);
    }

    /** 期间费用率：(销售费用 + 管理费用 + 财务费用) / 营业收入。 */
    private CalculationResult periodExpenseRatio(String code, FormulaRegistry.Formula formula, CalculationDimensions dimensions) {
        Lookup se = one(dimensions, "SELLING_EXPENSE", dimensions.period());
        Lookup me = one(dimensions, "MANAGEMENT_EXPENSE", dimensions.period());
        Lookup fe = one(dimensions, "FINANCIAL_EXPENSE", dimensions.period());
        Lookup rev = one(dimensions, "OPERATING_REVENUE", dimensions.period());
        if (se.conflict || me.conflict || fe.conflict || rev.conflict) return conflict(code, formula, "同一指标存在多个互相冲突的事实");
        if (se.fact == null || me.fact == null || fe.fact == null || rev.fact == null) return insufficient(code, formula, "缺少公式输入事实");
        if (rev.fact.getValue().compareTo(BigDecimal.ZERO) == 0) return insufficient(code, formula, "分母为零，拒绝计算");
        BigDecimal numerator = se.fact.getValue().add(me.fact.getValue()).add(fe.fact.getValue());
        BigDecimal value = DecimalPolicy.result(DecimalPolicy.divide(numerator, rev.fact.getValue()));
        return calculated(code, formula, value, se.fact, me.fact, fe.fact, rev.fact);
    }

    /** 营业周期：存货周转天数 + 应收账款周转天数。直接计算避免链式依赖。 */
    private CalculationResult operatingCycle(String code, FormulaRegistry.Formula formula, CalculationDimensions dimensions) {
        Lookup inventory = one(dimensions, "INVENTORY", dimensions.period());
        Lookup cost = one(dimensions, "OPERATING_COST", dimensions.period());
        Lookup ar = one(dimensions, "ACCOUNTS_RECEIVABLE", dimensions.period());
        Lookup revenue = one(dimensions, "OPERATING_REVENUE", dimensions.period());
        if (inventory.conflict || cost.conflict || ar.conflict || revenue.conflict) return conflict(code, formula, "同一指标存在多个互相冲突的事实");
        if (inventory.fact == null || cost.fact == null || ar.fact == null || revenue.fact == null) return insufficient(code, formula, "缺少公式输入事实");
        if (cost.fact.getValue().compareTo(BigDecimal.ZERO) == 0 || revenue.fact.getValue().compareTo(BigDecimal.ZERO) == 0) return insufficient(code, formula, "分母为零，拒绝计算");
        BigDecimal inventoryDays = DecimalPolicy.divide(inventory.fact.getValue(), cost.fact.getValue()).multiply(new BigDecimal("365"));
        BigDecimal receivablesDays = DecimalPolicy.divide(ar.fact.getValue(), revenue.fact.getValue()).multiply(new BigDecimal("365"));
        BigDecimal value = DecimalPolicy.result(inventoryDays.add(receivablesDays));
        return calculated(code, formula, value, inventory.fact, cost.fact, ar.fact, revenue.fact);
    }

    private Lookup one(CalculationDimensions dimensions, String metric, String period) {
        List<FinancialFact> facts = factRepository.findByVersionIdAndMetricCodeAndPeriodAndScope(
                dimensions.versionId(), metric, period, dimensions.scope()).stream()
                .filter(fact -> fact.getReviewStatus() != FinancialFact.ReviewStatus.REJECTED).toList();
        if (facts.isEmpty()) return new Lookup(null, false);
        // 同指标同期间多条事实允许数值完全一致；数值不同必须人工处理，不能随意选第一条。
        boolean conflict = facts.stream().map(FinancialFact::getValue).distinct().count() > 1;
        return new Lookup(facts.get(0), conflict);
    }
    private String previousPeriod(String period) {
        if (period.matches("FY20\\d{2}")) return "FY" + (Integer.parseInt(period.substring(2)) - 1);
        return period + "_PREVIOUS";
    }
    private CalculationResult calculated(String code, FormulaRegistry.Formula formula, BigDecimal value, FinancialFact... facts) {
        List<CalculationTrace.Input> inputs = Arrays.stream(facts).map(fact -> new CalculationTrace.Input(
                fact.getMetricCode(), fact.getValue(), fact.getId(), fact.getSourceCellId())).toList();
        return new CalculationResult(code, CalculationStatus.CALCULATED, value, formula.unit(), "",
                new CalculationTrace(FormulaRegistry.VERSION, formula.expression(), inputs));
    }
    private CalculationResult insufficient(String code, FormulaRegistry.Formula formula, String reason) { return new CalculationResult(code, CalculationStatus.INSUFFICIENT, null, formula.unit(), reason, null); }
    private CalculationResult conflict(String code, FormulaRegistry.Formula formula, String reason) { return new CalculationResult(code, CalculationStatus.CONFLICT, null, formula.unit(), reason, null); }
    private record Lookup(FinancialFact fact, boolean conflict) { }
}

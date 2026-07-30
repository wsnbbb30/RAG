package com.yizhaoqi.smartpai.finance;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 公式字典版本化。只登记受支持的派生指标，避免将自然语言任意解释为计算请求。
 */
@Component
public class FormulaRegistry {
    public static final String VERSION = "finance-formula-v2";

    private final Map<String, Formula> formulas = Map.ofEntries(
            // ── 增长率类 ──
            entry("REVENUE_GROWTH_RATE", "(revenue_current - revenue_previous) / revenue_previous", "%", "OPERATING_REVENUE", "OPERATING_REVENUE_PREVIOUS"),
            entry("NET_PROFIT_GROWTH", "(net_profit_current - net_profit_previous) / abs(net_profit_previous)", "%", "NET_PROFIT", "NET_PROFIT_PREVIOUS"),
            entry("CASH_FLOW_GROWTH", "(ocf_current - ocf_previous) / abs(ocf_previous)", "%", "OPERATING_CASH_FLOW", "OPERATING_CASH_FLOW_PREVIOUS"),
            entry("RECEIVABLES_GROWTH", "(ar_current - ar_previous) / abs(ar_previous)", "%", "ACCOUNTS_RECEIVABLE", "ACCOUNTS_RECEIVABLE_PREVIOUS"),

            // ── 利润率类 ──
            entry("NET_PROFIT_MARGIN", "net_profit / revenue", "%", "NET_PROFIT", "OPERATING_REVENUE"),
            entry("GROSS_PROFIT_MARGIN", "(revenue - operating_cost) / revenue", "%", "OPERATING_REVENUE", "OPERATING_COST"),
            entry("OPERATING_PROFIT_MARGIN", "operating_profit / revenue", "%", "OPERATING_PROFIT", "OPERATING_REVENUE"),
            entry("TOTAL_PROFIT_MARGIN", "total_profit / revenue", "%", "TOTAL_PROFIT", "OPERATING_REVENUE"),
            entry("OPERATING_COST_MARGIN", "operating_cost / revenue", "%", "OPERATING_COST", "OPERATING_REVENUE"),

            // ── 费用率类 ──
            entry("R_AND_D_EXPENSE_RATIO", "r_and_d_expense / revenue", "%", "R_AND_D_EXPENSE", "OPERATING_REVENUE"),
            entry("SELLING_EXPENSE_RATIO", "selling_expense / revenue", "%", "SELLING_EXPENSE", "OPERATING_REVENUE"),
            entry("MANAGEMENT_EXPENSE_RATIO", "management_expense / revenue", "%", "MANAGEMENT_EXPENSE", "OPERATING_REVENUE"),
            entry("FINANCIAL_EXPENSE_RATIO", "financial_expense / revenue", "%", "FINANCIAL_EXPENSE", "OPERATING_REVENUE"),
            entry("PERIOD_EXPENSE_RATIO", "(selling + management + financial) / revenue", "%", "SELLING_EXPENSE", "MANAGEMENT_EXPENSE", "FINANCIAL_EXPENSE", "OPERATING_REVENUE"),

            // ── 偿债能力类 ──
            entry("DEBT_TO_ASSET_RATIO", "total_liabilities / total_assets", "%", "TOTAL_LIABILITIES", "TOTAL_ASSETS"),
            entry("CURRENT_RATIO", "current_assets / current_liabilities", "times", "CURRENT_ASSETS", "CURRENT_LIABILITIES"),
            entry("QUICK_RATIO", "(current_assets - inventory) / current_liabilities", "times", "CURRENT_ASSETS", "INVENTORY", "CURRENT_LIABILITIES"),
            entry("DEBT_TO_EQUITY_RATIO", "total_liabilities / parent_equity", "%", "TOTAL_LIABILITIES", "PARENT_EQUITY"),
            entry("EQUITY_MULTIPLIER", "total_assets / parent_equity", "times", "TOTAL_ASSETS", "PARENT_EQUITY"),

            // ── 营运能力类 ──
            entry("ASSET_TURNOVER", "revenue / total_assets", "times", "OPERATING_REVENUE", "TOTAL_ASSETS"),
            entry("CURRENT_ASSET_TURNOVER", "revenue / current_assets", "times", "OPERATING_REVENUE", "CURRENT_ASSETS"),
            entry("RECEIVABLES_TURNOVER", "revenue / accounts_receivable", "times", "OPERATING_REVENUE", "ACCOUNTS_RECEIVABLE"),
            entry("PAYABLES_TURNOVER", "operating_cost / accounts_payable", "times", "OPERATING_COST", "ACCOUNTS_PAYABLE"),
            entry("INVENTORY_TURNOVER_DAYS", "365 * inventory / operating_cost", "days", "INVENTORY", "OPERATING_COST"),
            entry("RECEIVABLES_TURNOVER_DAYS", "365 * accounts_receivable / revenue", "days", "ACCOUNTS_RECEIVABLE", "OPERATING_REVENUE"),
            entry("PAYABLES_TURNOVER_DAYS", "365 * accounts_payable / operating_cost", "days", "ACCOUNTS_PAYABLE", "OPERATING_COST"),
            entry("OPERATING_CYCLE", "inventory_turnover_days + receivables_turnover_days", "days", "INVENTORY", "OPERATING_COST", "ACCOUNTS_RECEIVABLE", "OPERATING_REVENUE"),

            // ── 收益率类 ──
            entry("RETURN_ON_ASSETS", "net_profit / total_assets", "%", "NET_PROFIT", "TOTAL_ASSETS"),
            entry("RETURN_ON_EQUITY", "net_profit / parent_equity", "%", "NET_PROFIT", "PARENT_EQUITY"),

            // ── 现金流比率类 ──
            entry("OPERATING_CASH_FLOW_TO_REVENUE", "operating_cash_flow / revenue", "%", "OPERATING_CASH_FLOW", "OPERATING_REVENUE"),
            entry("OPERATING_CASH_FLOW_TO_NET_PROFIT", "operating_cash_flow / net_profit", "times", "OPERATING_CASH_FLOW", "NET_PROFIT"),
            entry("CASH_RECEIVED_TO_REVENUE", "cash_received_from_sales / revenue", "%", "CASH_RECEIVED_FROM_SALES", "OPERATING_REVENUE"),

            // ── 结构比率类 ──
            entry("NON_CURRENT_ASSET_RATIO", "non_current_assets / total_assets", "%", "NON_CURRENT_ASSETS", "TOTAL_ASSETS"),
            entry("CURRENT_ASSET_RATIO", "current_assets / total_assets", "%", "CURRENT_ASSETS", "TOTAL_ASSETS"),
            entry("FIXED_ASSET_RATIO", "fixed_assets / total_assets", "%", "FIXED_ASSETS", "TOTAL_ASSETS"),
            entry("RECEIVABLES_TO_ASSET", "accounts_receivable / total_assets", "%", "ACCOUNTS_RECEIVABLE", "TOTAL_ASSETS"),
            entry("INVENTORY_TO_ASSET", "inventory / total_assets", "%", "INVENTORY", "TOTAL_ASSETS"),
            entry("GOODWILL_TO_ASSET", "goodwill / total_assets", "%", "GOODWILL", "TOTAL_ASSETS"),
            entry("PAYABLES_TO_LIABILITY", "accounts_payable / total_liabilities", "%", "ACCOUNTS_PAYABLE", "TOTAL_LIABILITIES"),
            entry("CURRENT_LIABILITY_RATIO", "current_liabilities / total_liabilities", "%", "CURRENT_LIABILITIES", "TOTAL_LIABILITIES"),
            entry("LONG_TERM_LIABILITY_RATIO", "long_term_liabilities / total_liabilities", "%", "LONG_TERM_LIABILITIES", "TOTAL_LIABILITIES")
    );

    private static Map.Entry<String, Formula> entry(String code, String expression, String unit, String... inputs) {
        return Map.entry(code, new Formula(code, expression, unit, List.of(inputs)));
    }

    public Optional<Formula> find(String metricCode) { return Optional.ofNullable(formulas.get(metricCode)); }
    public record Formula(String metricCode, String expression, String unit, List<String> inputs) { }
}

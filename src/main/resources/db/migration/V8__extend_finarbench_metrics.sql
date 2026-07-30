-- Phase 2: 补齐 FinAR-Bench 所需的原始指标和中文别名
-- 新增 9 个原始指标，为所有 21 个新派生指标注册中文别名

INSERT INTO financial_metric(metric_code, canonical_name, statement_type, unit_type) VALUES
('INVENTORY', '存货', 'BALANCE_SHEET', 'CURRENCY'),
('PARENT_EQUITY', '归属于母公司所有者权益合计', 'BALANCE_SHEET', 'CURRENCY'),
('ACCOUNTS_RECEIVABLE', '应收帐款', 'BALANCE_SHEET', 'CURRENCY'),
('ACCOUNTS_PAYABLE', '应付帐款', 'BALANCE_SHEET', 'CURRENCY'),
('NON_CURRENT_ASSETS', '非流动资产合计', 'BALANCE_SHEET', 'CURRENCY'),
('FIXED_ASSETS', '固定资产净额', 'BALANCE_SHEET', 'CURRENCY'),
('LONG_TERM_LIABILITIES', '长期负债合计', 'BALANCE_SHEET', 'CURRENCY'),
('GOODWILL', '商誉', 'BALANCE_SHEET', 'CURRENCY'),
('CASH_RECEIVED_FROM_SALES', '销售商品提供劳务收到的现金', 'CASH_FLOW', 'CURRENCY')
ON DUPLICATE KEY UPDATE canonical_name = VALUES(canonical_name), enabled = TRUE;

-- 补齐现有指标的别名（帐/账变体、简称等）
INSERT INTO financial_metric_alias(metric_code, alias_text, normalized_alias) VALUES
-- 现有指标补充别名
('TOTAL_ASSETS', '资产总计', '资产总计'),
('TOTAL_ASSETS', '总资产', '总资产'),
('TOTAL_LIABILITIES', '总负债', '总负债'),
('NET_PROFIT', '净利润', '净利润'),
('NET_PROFIT', '净利', '净利'),
('OPERATING_REVENUE', '营收', '营收'),
('OPERATING_REVENUE', '主营业务收入', '主营业务收入'),
('OPERATING_COST', '主营业务成本', '主营业务成本'),
('CURRENT_ASSETS', '流动资产', '流动资产'),
('CURRENT_LIABILITIES', '流动负债', '流动负债'),
('OPERATING_CASH_FLOW', '经营活动现金流量净额', '经营活动现金流量净额'),
('OPERATING_CASH_FLOW', '经营活动产生的现金流量净额', '经营活动产生的现金流量净额'),
('R_AND_D_EXPENSE', '研发支出', '研发支出'),
('SELLING_EXPENSE', '销售费用', '销售费用'),
('SELLING_EXPENSE', '营业费用', '营业费用'),
('MANAGEMENT_EXPENSE', '管理费用', '管理费用'),
('MANAGEMENT_EXPENSE', '管理费', '管理费'),
('FINANCIAL_EXPENSE', '财务费用', '财务费用'),
('FINANCIAL_EXPENSE', '财务费', '财务费'),

-- 新增原始指标别名
('INVENTORY', '存货', '存货'),
('INVENTORY', '存货净值', '存货净值'),
('INVENTORY', '库存', '库存'),
('PARENT_EQUITY', '归属于母公司所有者权益合计', '归属于母公司所有者权益合计'),
('PARENT_EQUITY', '归属于母公司股东权益合计', '归属于母公司股东权益合计'),
('PARENT_EQUITY', '归母权益', '归母权益'),
('PARENT_EQUITY', '归属母公司所有者权益', '归属母公司所有者权益'),
('PARENT_EQUITY', '归属母公司股东权益', '归属母公司股东权益'),
('ACCOUNTS_RECEIVABLE', '应收帐款', '应收帐款'),
('ACCOUNTS_RECEIVABLE', '应收账款', '应收账款'),
('ACCOUNTS_RECEIVABLE', '应收款项', '应收款项'),
('ACCOUNTS_PAYABLE', '应付帐款', '应付帐款'),
('ACCOUNTS_PAYABLE', '应付账款', '应付账款'),
('ACCOUNTS_PAYABLE', '应付款项', '应付款项'),
('NON_CURRENT_ASSETS', '非流动资产合计', '非流动资产合计'),
('NON_CURRENT_ASSETS', '非流动资产', '非流动资产'),
('FIXED_ASSETS', '固定资产净额', '固定资产净额'),
('FIXED_ASSETS', '固定资产净值', '固定资产净值'),
('FIXED_ASSETS', '固定资产', '固定资产'),
('FIXED_ASSETS', '固定资', '固定资'),
('LONG_TERM_LIABILITIES', '长期负债合计', '长期负债合计'),
('LONG_TERM_LIABILITIES', '长期负债', '长期负债'),
('LONG_TERM_LIABILITIES', '非流动负债合计', '非流动负债合计'),
('LONG_TERM_LIABILITIES', '非流动负债', '非流动负债'),
('GOODWILL', '商誉', '商誉'),
('CASH_RECEIVED_FROM_SALES', '销售商品提供劳务收到的现金', '销售商品提供劳务收到的现金'),
('CASH_RECEIVED_FROM_SALES', '销售商品、提供劳务收到的现金', '销售商品、提供劳务收到的现金'),
('CASH_RECEIVED_FROM_SALES', '销售商品收到的现金', '销售商品收到的现金')
ON DUPLICATE KEY UPDATE metric_code = VALUES(metric_code), alias_text = VALUES(alias_text);

-- 派生指标别名（FormulaRegistry 中已注册的 41 个指标 → 中文映射）
INSERT INTO financial_metric_alias(metric_code, alias_text, normalized_alias) VALUES
-- 增长率类
('REVENUE_GROWTH_RATE', '营业收入增长率', '营业收入增长率'),
('REVENUE_GROWTH_RATE', '营收增长率', '营收增长率'),
('NET_PROFIT_GROWTH', '净利润增长率', '净利润增长率'),
('CASH_FLOW_GROWTH', '经营活动现金流量净额增长率', '经营活动现金流量净额增长率'),
('CASH_FLOW_GROWTH', '经营现金流增长率', '经营现金流增长率'),
('RECEIVABLES_GROWTH', '应收帐款增长率', '应收帐款增长率'),
('RECEIVABLES_GROWTH', '应收账款增长率', '应收账款增长率'),

-- 利润率类
('NET_PROFIT_MARGIN', '销售净利率', '销售净利率'),
('NET_PROFIT_MARGIN', '净利润率', '净利润率'),
('NET_PROFIT_MARGIN', '净利率', '净利率'),
('GROSS_PROFIT_MARGIN', '销售毛利率', '销售毛利率'),
('GROSS_PROFIT_MARGIN', '毛利率', '毛利率'),
('OPERATING_PROFIT_MARGIN', '营业利润率', '营业利润率'),
('TOTAL_PROFIT_MARGIN', '利润总额率', '利润总额率'),
('OPERATING_COST_MARGIN', '营业成本率', '营业成本率'),

-- 费用率类
('R_AND_D_EXPENSE_RATIO', '研发费用率', '研发费用率'),
('R_AND_D_EXPENSE_RATIO', '研发费用与营业收入的比例', '研发费用与营业收入的比例'),
('SELLING_EXPENSE_RATIO', '销售费用率', '销售费用率'),
('SELLING_EXPENSE_RATIO', '销售费用与营业收入的比例', '销售费用与营业收入的比例'),
('MANAGEMENT_EXPENSE_RATIO', '管理费用率', '管理费用率'),
('MANAGEMENT_EXPENSE_RATIO', '管理费用与营业收入的比例', '管理费用与营业收入的比例'),
('FINANCIAL_EXPENSE_RATIO', '财务费用率', '财务费用率'),
('FINANCIAL_EXPENSE_RATIO', '财务费用与营业收入的比例', '财务费用与营业收入的比例'),
('PERIOD_EXPENSE_RATIO', '期间费用率', '期间费用率'),

-- 偿债能力类
('DEBT_TO_ASSET_RATIO', '资产负债率', '资产负债率'),
('DEBT_TO_ASSET_RATIO', '负债率', '负债率'),
('CURRENT_RATIO', '流动比率', '流动比率'),
('CURRENT_RATIO', '流动比', '流动比'),
('QUICK_RATIO', '速动比率', '速动比率'),
('QUICK_RATIO', '速动比', '速动比'),
('DEBT_TO_EQUITY_RATIO', '产权比率', '产权比率'),
('DEBT_TO_EQUITY_RATIO', '负债权益比率', '负债权益比率'),
('EQUITY_MULTIPLIER', '权益乘数', '权益乘数'),

-- 营运能力类
('ASSET_TURNOVER', '总资产周转率', '总资产周转率'),
('ASSET_TURNOVER', '资产周转率', '资产周转率'),
('CURRENT_ASSET_TURNOVER', '流动资产周转率', '流动资产周转率'),
('RECEIVABLES_TURNOVER', '应收账款周转率', '应收账款周转率'),
('RECEIVABLES_TURNOVER', '应收帐款周转率', '应收帐款周转率'),
('RECEIVABLES_TURNOVER', '应收周转率', '应收周转率'),
('PAYABLES_TURNOVER', '应付账款周转率', '应付账款周转率'),
('PAYABLES_TURNOVER', '应付帐款周转率', '应付帐款周转率'),
('PAYABLES_TURNOVER', '应付周转率', '应付周转率'),
('INVENTORY_TURNOVER_DAYS', '存货周转天数', '存货周转天数'),
('RECEIVABLES_TURNOVER_DAYS', '应收账款周转天数', '应收账款周转天数'),
('RECEIVABLES_TURNOVER_DAYS', '应收帐款周转天数', '应收帐款周转天数'),
('RECEIVABLES_TURNOVER_DAYS', '应收周转天数', '应收周转天数'),
('PAYABLES_TURNOVER_DAYS', '应付账款周转天数', '应付账款周转天数'),
('PAYABLES_TURNOVER_DAYS', '应付帐款周转天数', '应付帐款周转天数'),
('PAYABLES_TURNOVER_DAYS', '应付周转天数', '应付周转天数'),
('OPERATING_CYCLE', '营业周期', '营业周期'),

-- 收益率类
('RETURN_ON_ASSETS', '总资产收益率', '总资产收益率'),
('RETURN_ON_ASSETS', '资产收益率', '资产收益率'),
('RETURN_ON_ASSETS', 'ROA', 'ROA'),
('RETURN_ON_EQUITY', '净资产收益率', '净资产收益率'),
('RETURN_ON_EQUITY', 'ROE', 'ROE'),
('RETURN_ON_EQUITY', '净资产回报率', '净资产回报率'),

-- 现金流比率类
('OPERATING_CASH_FLOW_TO_REVENUE', '经营活动现金流量净额与营业收入的比例', '经营活动现金流量净额与营业收入的比例'),
('OPERATING_CASH_FLOW_TO_REVENUE', '经营现金流与营收比', '经营现金流与营收比'),
('OPERATING_CASH_FLOW_TO_NET_PROFIT', '经营活动现金流量净额与净利润的比例', '经营活动现金流量净额与净利润的比例'),
('OPERATING_CASH_FLOW_TO_NET_PROFIT', '经营现金流与净利润比', '经营现金流与净利润比'),
('CASH_RECEIVED_TO_REVENUE', '销售商品提供劳务收到的现金与营业收入的比例', '销售商品提供劳务收到的现金与营业收入的比例'),
('CASH_RECEIVED_TO_REVENUE', '销售收现比', '销售收现比'),

-- 结构比率类
('NON_CURRENT_ASSET_RATIO', '非流动资产合计与资产总计的比例', '非流动资产合计与资产总计的比例'),
('NON_CURRENT_ASSET_RATIO', '非流动资产占比', '非流动资产占比'),
('CURRENT_ASSET_RATIO', '流动资产合计与资产总计的比例', '流动资产合计与资产总计的比例'),
('CURRENT_ASSET_RATIO', '流动资产占比', '流动资产占比'),
('FIXED_ASSET_RATIO', '固定资产净额与资产总计的比例', '固定资产净额与资产总计的比例'),
('FIXED_ASSET_RATIO', '固定资产占比', '固定资产占比'),
('RECEIVABLES_TO_ASSET', '应收帐款与资产总计的比例', '应收帐款与资产总计的比例'),
('RECEIVABLES_TO_ASSET', '应收账款与资产总计的比例', '应收账款与资产总计的比例'),
('RECEIVABLES_TO_ASSET', '应收占比', '应收占比'),
('INVENTORY_TO_ASSET', '存货与资产总计的比例', '存货与资产总计的比例'),
('INVENTORY_TO_ASSET', '存货占比', '存货占比'),
('GOODWILL_TO_ASSET', '商誉与资产总计的比例', '商誉与资产总计的比例'),
('GOODWILL_TO_ASSET', '商誉占比', '商誉占比'),
('PAYABLES_TO_LIABILITY', '应付帐款与负债合计的比例', '应付帐款与负债合计的比例'),
('PAYABLES_TO_LIABILITY', '应付账款与负债合计的比例', '应付账款与负债合计的比例'),
('CURRENT_LIABILITY_RATIO', '流动负债合计与负债合计的比例', '流动负债合计与负债合计的比例'),
('CURRENT_LIABILITY_RATIO', '流动负债占比', '流动负债占比'),
('LONG_TERM_LIABILITY_RATIO', '长期负债合计与负债合计的比例', '长期负债合计与负债合计的比例'),
('LONG_TERM_LIABILITY_RATIO', '非流动负债占比', '非流动负债占比')
ON DUPLICATE KEY UPDATE metric_code = VALUES(metric_code), alias_text = VALUES(alias_text);

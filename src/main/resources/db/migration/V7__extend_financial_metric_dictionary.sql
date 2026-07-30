INSERT INTO financial_metric(metric_code, canonical_name, statement_type, unit_type) VALUES
('CURRENT_ASSETS', '流动资产合计', 'BALANCE_SHEET', 'CURRENCY'),
('CURRENT_LIABILITIES', '流动负债合计', 'BALANCE_SHEET', 'CURRENCY')
ON DUPLICATE KEY UPDATE canonical_name = VALUES(canonical_name), enabled = TRUE;

INSERT INTO financial_metric_alias(metric_code, alias_text, normalized_alias) VALUES
('CURRENT_ASSETS', '流动资产合计', '流动资产合计'),
('CURRENT_ASSETS', '流动资产', '流动资产'),
('CURRENT_LIABILITIES', '流动负债合计', '流动负债合计'),
('CURRENT_LIABILITIES', '流动负债', '流动负债')
ON DUPLICATE KEY UPDATE metric_code = VALUES(metric_code), alias_text = VALUES(alias_text);

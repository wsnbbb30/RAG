import json, re, math
from decimal import Decimal, getcontext
from collections import defaultdict

getcontext().prec = 50

# ---- Replicate Java IndicatorComputer logic in Python ----

def normalize_metric_name(name):
    if not name:
        return ''
    return (name.replace('（','(').replace('）',')')
                .replace('帐','账')
                .replace(' ', '').replace('\t', '').replace('\n', '')
                .replace(':','').replace('：','')
                .replace('(','').replace(')','').replace('（','').replace('）','')
                .replace('-','').replace('—','').replace('_','')
                .replace('、','').replace('，', '').replace(',', '')
                .replace('。','').replace('.','')
                .strip().lower())

def normalize_number(raw):
    if not raw:
        return ''
    return raw.replace(',', '').replace('，', '').strip()

def split_pipe(line):
    s = line.strip()
    if s.startswith('|'): s = s[1:]
    if s.endswith('|'): s = s[:-1]
    return [p.strip() for p in s.split('|')]

def is_separator(cells):
    for c in cells:
        if not re.match(r'^[-: ]+$', c):
            return False
    return True

def extract_year(header):
    m = re.search(r'(?<!\d)(20\d{2})(?!\d)', header)
    return m.group(1) if m else None

YEAR_PATTERN = re.compile(r'(?<!\d)(20\d{2})(?!\d)')

def parse_table_to_facts(markdown):
    facts = {}
    lines = markdown.split('\n')
    headers = None
    year_keys = None
    for line in lines:
        t = line.strip()
        if not t or t.startswith('#'):
            headers = None; year_keys = None
            continue
        if not t.startswith('|'): continue
        cells = split_pipe(t)
        if not cells or is_separator(cells): continue
        if headers is None:
            headers = [cells[i] for i in range(1, len(cells))]
            year_keys = [extract_year(cells[i]) for i in range(1, len(cells))]
            continue
        metric_name = cells[0]
        if not metric_name: continue
        nn = normalize_metric_name(metric_name)
        year_values = {}
        for i in range(1, min(len(cells), len(year_keys) + 1)):
            yk = year_keys[i-1]
            if yk is None: continue
            raw = normalize_number(cells[i])
            if raw:
                try:
                    year_values[yk] = Decimal(raw)
                except:
                    pass
        if year_values:
            facts[nn] = year_values
    return facts

def get_fact(facts, metric_name, year):
    key = normalize_metric_name(metric_name)
    # 1. exact match
    yv = facts.get(key)
    if yv is not None:
        return yv.get(year)
    # 2. contains match
    for k, v in facts.items():
        if key in k or k in key:
            return v.get(year)
    # 3. suffix-stripped match
    key_no_suffix = re.sub(r'(合计|净额|净值|总额)$', '', key)
    if key_no_suffix != key:
        for k, v in facts.items():
            k_no_suffix = re.sub(r'(合计|净额|净值|总额)$', '', k)
            if k_no_suffix == key_no_suffix or key_no_suffix in k_no_suffix or k_no_suffix in key_no_suffix:
                return v.get(year)
    return None

def divide(a, b):
    return a / b

def compute_deterministic(formula_type, inputs, facts, year):
    try:
        if formula_type == 'RATIO':
            a = get_fact(facts, inputs[0], year)
            b = get_fact(facts, inputs[1], year)
            if a is None or b is None or b == 0: return None
            return divide(a, b)
        elif formula_type == 'GROWTH':
            cur = get_fact(facts, inputs[0], year)
            prev_year = str(int(year) - 1)
            prev = get_fact(facts, inputs[0], prev_year)
            if cur is None or prev is None or prev == 0: return None
            return divide(cur - prev, abs(prev))
        elif formula_type == 'TURNOVER_DAYS':
            a = get_fact(facts, inputs[0], year)
            b = get_fact(facts, inputs[1], year)
            if a is None or b is None or b == 0: return None
            return divide(a, b) * Decimal('365')
        elif formula_type == 'QUICK_RATIO':
            ca = get_fact(facts, inputs[0], year)
            inv = get_fact(facts, inputs[1], year)
            cl = get_fact(facts, inputs[2], year)
            if ca is None or inv is None or cl is None or cl == 0: return None
            return divide(ca - inv, cl)
        elif formula_type == 'GROSS_MARGIN':
            rev = get_fact(facts, inputs[0], year)
            cost = get_fact(facts, inputs[1], year)
            if rev is None or cost is None or rev == 0: return None
            return divide(rev - cost, rev)
        elif formula_type == 'PERIOD_EXPENSE':
            se = get_fact(facts, inputs[0], year)
            me = get_fact(facts, inputs[1], year)
            fe = get_fact(facts, inputs[2], year)
            rev = get_fact(facts, inputs[3], year)
            if se is None or me is None or fe is None or rev is None or rev == 0: return None
            return divide(se + me + fe, rev)
        elif formula_type == 'OPERATING_CYCLE':
            inv = get_fact(facts, inputs[0], year)
            cost = get_fact(facts, inputs[1], year)
            ar = get_fact(facts, inputs[2], year)
            rev = get_fact(facts, inputs[3], year)
            if inv is None or cost is None or ar is None or rev is None or cost == 0 or rev == 0: return None
            inv_days = divide(inv, cost) * Decimal('365')
            ar_days = divide(ar, rev) * Decimal('365')
            return inv_days + ar_days
    except:
        return None

FORMULA_DEFS = {
    '资产负债率': ('RATIO', ['负债合计', '资产总计']),
    '流动比率': ('RATIO', ['流动资产合计', '流动负债合计']),
    '产权比率': ('RATIO', ['负债合计', '归属于母公司所有者权益合计']),
    '权益乘数': ('RATIO', ['资产总计', '归属于母公司所有者权益合计']),
    '总资产收益率': ('RATIO', ['净利润', '资产总计']),
    '净资产收益率': ('RATIO', ['净利润', '归属于母公司所有者权益合计']),
    '总资产周转率': ('RATIO', ['营业收入', '资产总计']),
    '流动资产周转率': ('RATIO', ['营业收入', '流动资产合计']),
    '应收账款周转率': ('RATIO', ['营业收入', '应收帐款']),
    '应收帐款周转率': ('RATIO', ['营业收入', '应收帐款']),
    '应付账款周转率': ('RATIO', ['营业成本', '应付帐款']),
    '应付帐款周转率': ('RATIO', ['营业成本', '应付帐款']),
    '管理费用与营业收入的比例': ('RATIO', ['管理费用', '营业收入']),
    '销售费用与营业收入的比例': ('RATIO', ['销售费用', '营业收入']),
    '财务费用与营业收入的比例': ('RATIO', ['财务费用', '营业收入']),
    '非流动资产合计与资产总计的比例': ('RATIO', ['非流动资产合计', '资产总计']),
    '流动资产合计与资产总计的比例': ('RATIO', ['流动资产合计', '资产总计']),
    '固定资产净额与资产总计的比例': ('RATIO', ['固定资产净额', '资产总计']),
    '应收帐款与资产总计的比例': ('RATIO', ['应收帐款', '资产总计']),
    '应收账款与资产总计的比例': ('RATIO', ['应收帐款', '资产总计']),
    '存货与资产总计的比例': ('RATIO', ['存货', '资产总计']),
    '应付帐款与负债合计的比例': ('RATIO', ['应付帐款', '负债合计']),
    '应付账款与负债合计的比例': ('RATIO', ['应付帐款', '负债合计']),
    '流动负债合计与负债合计的比例': ('RATIO', ['流动负债合计', '负债合计']),
    '长期负债合计与负债合计的比例': ('RATIO', ['长期负债合计', '负债合计']),
    '商誉与资产总计的比例': ('RATIO', ['商誉', '资产总计']),
    '销售商品提供劳务收到的现金与营业收入的比例': ('RATIO', ['销售商品提供劳务收到的现金', '营业收入']),
    '经营活动现金流量净额与净利润的比例': ('RATIO', ['经营活动现金流量净额', '净利润']),
    '销售净利率': ('RATIO', ['净利润', '营业收入']),
    '净利率': ('RATIO', ['净利润', '营业收入']),
    '毛利率': ('GROSS_MARGIN', ['营业收入', '营业成本']),
    '存货周转率': ('RATIO', ['营业成本', '存货']),
    '固定资产周转率': ('RATIO', ['营业收入', '固定资产净额']),
    '流动比': ('RATIO', ['流动资产合计', '流动负债合计']),
    '速动比': ('QUICK_RATIO', ['流动资产合计', '存货', '流动负债合计']),
    '净利润增长率': ('GROWTH', ['净利润']),
    '营业收入增长率': ('GROWTH', ['营业收入']),
    '经营活动现金流量净额增长率': ('GROWTH', ['经营活动现金流量净额']),
    '应收帐款增长率': ('GROWTH', ['应收帐款']),
    '应收账款增长率': ('GROWTH', ['应收帐款']),
    '存货周转天数': ('TURNOVER_DAYS', ['存货', '营业成本']),
    '应收账款周转天数': ('TURNOVER_DAYS', ['应收帐款', '营业收入']),
    '应收帐款周转天数': ('TURNOVER_DAYS', ['应收帐款', '营业收入']),
    '应付账款周转天数': ('TURNOVER_DAYS', ['应付帐款', '营业成本']),
    '应付帐款周转天数': ('TURNOVER_DAYS', ['应付帐款', '营业成本']),
    '速动比率': ('QUICK_RATIO', ['流动资产合计', '存货', '流动负债合计']),
    '销售毛利率': ('GROSS_MARGIN', ['营业收入', '营业成本']),
    '期间费用率': ('PERIOD_EXPENSE', ['销售费用', '管理费用', '财务费用', '营业收入']),
    '营业周期': ('OPERATING_CYCLE', ['存货', '营业成本', '应收帐款', '营业收入']),
}

def clean_question(q):
    if not q: return ''
    q = re.sub(r'输出一个markdown格式的表格[，,。]?\s*列名为[^。]*[。.]?', '', q)
    q = re.sub(r'输出一个markdown格式的表格[。.]?', '', q)
    q = re.sub(r'结果表示为小数[，,]保留4位小数[。.]?', '', q)
    return q.strip()

def find_indicator_names(question):
    normalized = normalize_metric_name(question)
    found = []
    sorted_names = sorted(FORMULA_DEFS.keys(), key=len, reverse=True)
    remaining = normalized
    for name in sorted_names:
        nn = normalize_metric_name(name)
        if nn in remaining:
            found.append(name)
            remaining = remaining.replace(nn, ' ')
    return found

def extract_target_year(question):
    matches = YEAR_PATTERN.findall(question)
    return str(max(int(y) for y in matches)) if matches else None

def parse_ground_truth(markdown):
    if not markdown or not markdown.strip():
        return []
    lines = markdown.strip().split('\n')
    headers = None
    facts = []
    for line in lines:
        trimmed = line.strip()
        if not trimmed or not trimmed.startswith('|'): continue
        cells = split_pipe(trimmed)
        if not cells: continue
        if is_separator(cells): continue
        if headers is None:
            headers = [cells[i] for i in range(1, len(cells))]
        else:
            if len(cells) < 2: continue
            name = cells[0]
            if not name: continue
            yv = {}
            for i in range(1, min(len(cells), len(headers) + 1)):
                val = normalize_number(cells[i])
                if val:
                    yv[headers[i-1]] = val
            if yv:
                facts.append((name, yv))
    return facts

def values_close(expected, computed, tolerance=0.01):
    try:
        exp = float(expected)
        comp = float(computed)
        if abs(exp) < 1e-10:
            return abs(comp) < 1e-10
        return abs(comp - exp) / abs(exp) <= tolerance
    except:
        return expected == computed

# ---- Main analysis ----
print('='*120)
print('DETAILED INDICATOR-LEVEL DEBUG')
print('='*120)

total_indicators = 0
match_count = 0
mismatch_details = []
not_found_details = []
no_formula_details = []
compute_failed_details = []

with open('data/FinAR-Bench/dev.txt', 'r', encoding='utf-8') as f:
    for line in f:
        line = line.strip()
        if not line: continue
        data = json.loads(line)
        table_context = data['table']
        facts = parse_table_to_facts(table_context)

        for inst in data['instances']:
            if inst['task_type'] != 'indicator': continue

            question = inst['task']
            gt = inst['ground_truth']

            # Parse ground truth
            gt_facts = parse_ground_truth(gt)

            # Compute indicators from table
            cq = clean_question(question)
            indicator_names = find_indicator_names(cq)
            target_year = extract_target_year(cq)

            if not indicator_names:
                continue

            for gt_name, gt_yv in gt_facts:
                total_indicators += 1
                gt_norm = normalize_metric_name(gt_name)
                gt_year = list(gt_yv.keys())[0] if gt_yv else None
                gt_val = list(gt_yv.values())[0] if gt_yv else None

                # Find matching formula
                formula_def = FORMULA_DEFS.get(gt_name)
                if not formula_def:
                    for k, v in FORMULA_DEFS.items():
                        if normalize_metric_name(k) == gt_norm:
                            formula_def = v
                            break

                if not formula_def:
                    no_formula_details.append(f'  [{inst["task_id"][:8]}] {gt_name} - NO FORMULA DEFINED')
                    continue

                ft, inputs = formula_def
                year = target_year
                if not year:
                    year = gt_year

                if not year:
                    compute_failed_details.append(f'  [{inst["task_id"][:8]}] {gt_name} - NO YEAR')
                    continue

                computed = compute_deterministic(ft, inputs, facts, year)

                if computed is None:
                    missing = []
                    for inp in inputs:
                        v = get_fact(facts, inp, year)
                        if v is None:
                            if ft == 'GROWTH' and inp == inputs[0]:
                                prev = get_fact(facts, inp, str(int(year)-1))
                                if prev is None:
                                    missing.append(f'{inp}({year}|{int(year)-1})')
                            else:
                                missing.append(f'{inp}({year})')
                    compute_failed_details.append(f'  [{inst["task_id"][:8]}] {gt_name} = {ft}({",".join(inputs)}) - MISSING: {missing}')
                    continue

                computed_str = str(computed.quantize(Decimal('0.0001'))) if computed is not None else 'None'

                if gt_val and values_close(gt_val, computed_str):
                    match_count += 1
                elif gt_val:
                    mismatch_details.append(f'  [{inst["task_id"][:8]}] {gt_name}: expected={gt_val}, computed={computed_str}, formula={ft}({",".join(inputs)})')

print(f'\nTOTAL indicators evaluated: {total_indicators}')
print(f'MATCHED: {match_count} ({100*match_count/total_indicators:.1f}%)')
print(f'VALUE MISMATCH: {len(mismatch_details)} ({100*len(mismatch_details)/total_indicators:.1f}%)')
print(f'COMPUTE FAILED (missing raw data): {len(compute_failed_details)}')
print(f'NO FORMULA: {len(no_formula_details)}')

print(f'\n--- VALUE MISMATCHES ({len(mismatch_details)}) ---')
for d in mismatch_details[:30]:
    print(d)

print(f'\n--- COMPUTE FAILED ({len(compute_failed_details)}) ---')
for d in compute_failed_details[:30]:
    print(d)

print(f'\n--- NO FORMULA ({len(no_formula_details)}) ---')
for d in no_formula_details[:20]:
    print(d)

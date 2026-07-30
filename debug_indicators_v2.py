import json, re
from decimal import Decimal, getcontext
getcontext().prec = 50

def nm(name):
    if not name: return ''
    return (name.replace('（','(').replace('）',')').replace('帐','账')
                .replace(' ','').replace('\t','').replace('\n','')
                .replace(':','').replace('：','').replace('(','').replace(')','').replace('（','').replace('）','')
                .replace('-','').replace('—','').replace('_','').replace('、','').replace('，','').replace(',','')
                .replace('。','').replace('.','').strip().lower())

def parse_table(md):
    facts = {}
    lines = md.split('\n')
    headers = None; year_keys = None
    for line in lines:
        t = line.strip()
        if not t or t.startswith('#'): headers = None; year_keys = None; continue
        if not t.startswith('|'): continue
        cells = [p.strip() for p in t.strip().strip('|').split('|')]
        if not cells or all(re.match(r'^[-: ]+$', c) for c in cells): continue
        if headers is None:
            headers = [cells[i] for i in range(1, len(cells))]
            year_keys = [(re.search(r'(?<!\d)(20\d{2})(?!\d)', cells[i]).group(1) if re.search(r'(?<!\d)(20\d{2})(?!\d)', cells[i]) else None) for i in range(1, len(cells))]
            continue
        mn = cells[0]
        if not mn: continue
        nn = nm(mn)
        yv = {}
        for i in range(1, min(len(cells), len(year_keys) + 1)):
            yk = year_keys[i-1]
            if yk is None: continue
            raw = cells[i].replace(',','').replace('，','').strip()
            if raw:
                try: yv[yk] = Decimal(raw)
                except: pass
        if yv: facts[nn] = yv
    return facts

METRIC_ALIASES = {
    '股东权益合计': ['所有者权益合计', '归属于母公司所有者权益合计'],
    '应收帐款': ['应收账款'],
    '应付帐款': ['应付账款'],
    '归属于母公司所有者的净利润': ['归属于母公司股东的净利润', '归母净利润'],
}

def resolve_aliases(facts, key):
    """Try original key and aliases."""
    if key in facts:
        return facts[key]
    aliases = METRIC_ALIASES.get(key, [])
    for alias in aliases:
        ak = nm(alias)
        if ak in facts:
            return facts[ak]
    # Reverse: other keys point to our key as alias
    for orig, alist in METRIC_ALIASES.items():
        for a in alist:
            if nm(a) == key and orig in facts:
                return facts[orig]
    return None

def gf(facts, metric_name, year):
    key = nm(metric_name)
    yv = resolve_aliases(facts, key)
    if yv is not None: return yv.get(year)
    # 2. contains match
    for k, v in facts.items():
        if key in k or k in key:
            return v.get(year)
    # 3. suffix-stripped
    key_no = re.sub(r'(合计|净额|净值|总额)$', '', key)
    if key_no != key:
        for k, v in facts.items():
            k_no = re.sub(r'(合计|净额|净值|总额)$', '', k)
            if k_no == key_no or key_no in k_no or k_no in key_no:
                return v.get(year)
    return None

def avg_fact(facts, metric_name, year):
    cur = gf(facts, metric_name, year)
    prev = gf(facts, metric_name, str(int(year)-1))
    if cur is None or prev is None: return None
    return (cur + prev) / Decimal('2')

def div(a, b): return a / b

def compute(ft, inputs, facts, year):
    try:
        if ft == 'RATIO':
            a = gf(facts, inputs[0], year); b = gf(facts, inputs[1], year)
            if not all([a, b]) or b == 0: return None
            return div(a, b)
        elif ft == 'AVG_RATIO':
            a = gf(facts, inputs[0], year); b = avg_fact(facts, inputs[1], year)
            if not all([a, b]) or b == 0: return None
            return div(a, b)
        elif ft == 'GROWTH':
            cur = gf(facts, inputs[0], year)
            prev = gf(facts, inputs[0], str(int(year)-1))
            if not all([cur, prev]) or prev == 0: return None
            return div(cur - prev, prev)  # NO abs()
        elif ft == 'TURNOVER_DAYS':
            a = gf(facts, inputs[0], year); b = gf(facts, inputs[1], year)
            if not all([a, b]) or b == 0: return None
            return div(a, b) * Decimal('360')
        elif ft == 'AVG_TURNOVER_DAYS':
            a = avg_fact(facts, inputs[0], year); b = gf(facts, inputs[1], year)
            if not all([a, b]) or b == 0: return None
            return div(a, b) * Decimal('360')
        elif ft == 'QUICK_RATIO':
            ca = gf(facts, inputs[0], year); inv = gf(facts, inputs[1], year); cl = gf(facts, inputs[2], year)
            if not all([ca, inv, cl]) or cl == 0: return None
            quick = ca - inv
            prepay = gf(facts, '预付款项', year) or gf(facts, '预付账款', year) or gf(facts, '预付帐款', year)
            if prepay: quick -= prepay
            yr = gf(facts, '一年内到期的非流动资产', year)
            if yr: quick -= yr
            other = gf(facts, '其他流动资产', year)
            if other: quick -= other
            return div(quick, cl)
        elif ft == 'GROSS_MARGIN':
            rev = gf(facts, inputs[0], year); cost = gf(facts, inputs[1], year)
            if not all([rev, cost]) or rev == 0: return None
            return div(rev - cost, rev)
        elif ft == 'PERIOD_EXPENSE':
            se = gf(facts, inputs[0], year); me = gf(facts, inputs[1], year)
            fe = gf(facts, inputs[2], year); rev = gf(facts, inputs[3], year)
            if not all([se, me, fe, rev]) or rev == 0: return None
            return div(se + me + fe, rev)
        elif ft == 'OPERATING_CYCLE':
            inv = avg_fact(facts, inputs[0], year); cost = gf(facts, inputs[1], year)
            ar = avg_fact(facts, inputs[2], year); rev = gf(facts, inputs[3], year)
            if not all([inv, cost, ar, rev]) or cost == 0 or rev == 0: return None
            return div(inv, cost) * Decimal('360') + div(ar, rev) * Decimal('360')
    except: return None

FORMULA_DEFS = {
    '资产负债率': ('RATIO', ['负债合计', '资产总计']),
    '流动比率': ('RATIO', ['流动资产合计', '流动负债合计']),
    '产权比率': ('RATIO', ['负债合计', '归属于母公司所有者权益合计']),
    '权益乘数': ('RATIO', ['资产总计', '股东权益合计']),
    '总资产收益率': ('AVG_RATIO', ['净利润', '资产总计']),
    '净资产收益率': ('AVG_RATIO', ['归属于母公司所有者的净利润', '归属于母公司所有者权益合计']),
    '总资产周转率': ('AVG_RATIO', ['营业收入', '资产总计']),
    '流动资产周转率': ('AVG_RATIO', ['营业收入', '流动资产合计']),
    '应收账款周转率': ('AVG_RATIO', ['营业收入', '应收帐款']),
    '应收帐款周转率': ('AVG_RATIO', ['营业收入', '应收帐款']),
    '应付账款周转率': ('AVG_RATIO', ['营业成本', '应付帐款']),
    '应付帐款周转率': ('AVG_RATIO', ['营业成本', '应付帐款']),
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
    '存货周转率': ('AVG_RATIO', ['营业成本', '存货']),
    '固定资产周转率': ('AVG_RATIO', ['营业收入', '固定资产净额']),
    '流动比': ('RATIO', ['流动资产合计', '流动负债合计']),
    '速动比': ('QUICK_RATIO', ['流动资产合计', '存货', '流动负债合计']),
    '净利润增长率': ('GROWTH', ['净利润']),
    '营业收入增长率': ('GROWTH', ['营业收入']),
    '经营活动现金流量净额增长率': ('GROWTH', ['经营活动现金流量净额']),
    '应收帐款增长率': ('GROWTH', ['应收帐款']),
    '应收账款增长率': ('GROWTH', ['应收帐款']),
    '存货周转天数': ('AVG_TURNOVER_DAYS', ['存货', '营业成本']),
    '应收账款周转天数': ('AVG_TURNOVER_DAYS', ['应收帐款', '营业收入']),
    '应收帐款周转天数': ('AVG_TURNOVER_DAYS', ['应收帐款', '营业收入']),
    '应付账款周转天数': ('AVG_TURNOVER_DAYS', ['应付帐款', '营业成本']),
    '应付帐款周转天数': ('AVG_TURNOVER_DAYS', ['应付帐款', '营业成本']),
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

def find_names(question):
    normed = nm(question)
    found = []
    sorted_names = sorted(FORMULA_DEFS.keys(), key=len, reverse=True)
    remaining = normed
    for name in sorted_names:
        nn = nm(name)
        if nn in remaining:
            found.append(name)
            remaining = remaining.replace(nn, ' ')
    return found

YEAR_PATTERN = re.compile(r'(?<!\d)(20\d{2})(?!\d)')

def extract_year(q):
    matches = YEAR_PATTERN.findall(q)
    return str(max(int(y) for y in matches)) if matches else None

def parse_gt(md):
    if not md or not md.strip(): return []
    lines = md.strip().split('\n')
    headers = None; facts = []
    for line in lines:
        trimmed = line.strip()
        if not trimmed or not trimmed.startswith('|'): continue
        cells = [p.strip() for p in trimmed.strip().strip('|').split('|')]
        if not cells or all(re.match(r'^[-: ]+$', c) for c in cells): continue
        if headers is None:
            headers = [cells[i] for i in range(1, len(cells))]
        elif len(cells) >= 2 and cells[0]:
            yv = {}
            for i in range(1, min(len(cells), len(headers)+1)):
                val = cells[i].replace(',','').strip()
                if val: yv[headers[i-1]] = val
            if yv: facts.append((cells[0], yv))
    return facts

def values_close(e, c):
    try:
        ep = float(e); cp = float(c)
        if abs(ep) < 1e-10: return abs(cp) < 1e-10
        return abs(cp - ep) / abs(ep) <= 0.01
    except: return e == c

# Main
total = 0; matched = 0; mismatch = 0; failed = 0; no_formula = 0
mismatch_list = []
failed_list = []
no_formula_list = []

# Case-level tracking
case_total = 0; case_passed = 0

with open('data/FinAR-Bench/dev.txt', 'r', encoding='utf-8') as f:
    for line in f:
        line = line.strip()
        if not line: continue
        data = json.loads(line)
        facts = parse_table(data['table'])
        for inst in data['instances']:
            if inst['task_type'] != 'indicator': continue
            cq = clean_question(inst['task'])
            year = extract_year(cq)
            if not year: continue
            gt_facts = parse_gt(inst['ground_truth'])
            if not gt_facts: continue
            case_total += 1
            case_all_match = True
            for gt_name, gt_yv in gt_facts:
                total += 1
                gt_year = list(gt_yv.keys())[0]
                gt_val = list(gt_yv.values())[0]
                fd = FORMULA_DEFS.get(gt_name)
                if not fd:
                    for k, v in FORMULA_DEFS.items():
                        if nm(k) == nm(gt_name): fd = v; break
                if not fd:
                    no_formula += 1
                    no_formula_list.append(f'{gt_name}')
                    case_all_match = False
                    continue
                ft, inputs = fd
                y = year if year else gt_year
                computed = compute(ft, inputs, facts, y)
                if computed is None:
                    failed += 1
                    failed_list.append(f'{gt_name} = {ft}({",".join(inputs)})')
                    case_all_match = False
                    continue
                cs = str(computed.quantize(Decimal('0.0001')))
                if gt_val and values_close(gt_val, cs):
                    matched += 1
                else:
                    mismatch += 1
                    mismatch_list.append(f'{gt_name}: expected={gt_val}, computed={cs}, formula={ft}({",".join(inputs)})')
                    case_all_match = False
            if case_all_match:
                case_passed += 1

print(f'INDIVIDUAL: TOTAL={total} | MATCH={matched} ({100*matched/total:.1f}%) | MISMATCH={mismatch} ({100*mismatch/total:.1f}%) | FAILED={failed} | NO_FORMULA={no_formula}')
print(f'CASE-LEVEL: TOTAL={case_total} | PASSED={case_passed} ({100*case_passed/case_total:.1f}%)')
print(f'Individual improvement: +{matched - 396} matches from before (was 62.9%)')
print(f'Case-level was 10/60=16.7%, now {case_passed}/{case_total}={100*case_passed/case_total:.1f}%')
print()
print('--- REMAINING MISMATCHES ---')
# Group by indicator name
from collections import Counter
mc = Counter()
for m in mismatch_list:
    name = m.split(':')[0]
    mc[name] += 1
for name, count in mc.most_common():
    print(f'  {name}: {count}')
print()
print('--- REMAINING FAILURES ---')
fc = Counter()
for f in failed_list:
    fc[f] += 1
for name, count in fc.most_common():
    print(f'  {name}: {count}')
print()
print('--- NO FORMULA ---')
for nf in set(no_formula_list):
    print(f'  {nf}')

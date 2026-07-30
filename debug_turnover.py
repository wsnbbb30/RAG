import json, re
from decimal import Decimal, getcontext
from collections import defaultdict

getcontext().prec = 50

def nm(n):
    if not n: return ''
    return (n.replace('（','(').replace('）',')').replace('帐','账').replace(' ','').replace('\t','')
            .replace(':','').replace('：','').replace('(','').replace(')','').replace('（','').replace('）','')
            .replace('-','').replace('—','').replace('_','').replace('、','').replace('，','').replace(',','')
            .replace('。','').replace('.','').strip().lower())

def parse_table(md):
    facts = {}; lines = md.split('\n'); headers = None; year_keys = None
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
        nn = nm(mn); yv = {}
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
}

def resolve_aliases(facts, key):
    if key in facts: return facts[key]
    for orig, alist in METRIC_ALIASES.items():
        if nm(orig) == key:
            for a in alist:
                ak = nm(a)
                if ak in facts: return facts[ak]
    for orig, alist in METRIC_ALIASES.items():
        for a in alist:
            if nm(a) == key and orig in facts: return facts[orig]
    return None

def gf(facts, mn, year):
    key = nm(mn)
    yv = resolve_aliases(facts, key)
    if yv is not None: return yv.get(year)
    for k, v in facts.items():
        if key in k or k in key: return v.get(year)
    key_no = re.sub(r'(合计|净额|净值|总额)$', '', key)
    if key_no != key:
        for k, v in facts.items():
            k_no = re.sub(r'(合计|净额|净值|总额)$', '', k)
            if k_no == key_no or key_no in k_no or k_no in key_no:
                return v.get(year)
    return None

def avg_fact(facts, mn, year):
    cur = gf(facts, mn, year); prev = gf(facts, mn, str(int(year)-1))
    if cur is None or prev is None: return None
    return (cur + prev) / Decimal('2')

Y = re.compile(r'(?<!\d)(20\d{2})(?!\d)')
target_metrics = ['存货周转天数', '应收账款周转天数', '应收帐款周转天数',
                  '应付账款周转天数', '应付帐款周转天数', '营业周期']

mismatches = []

with open('data/FinAR-Bench/dev.txt', 'r', encoding='utf-8') as f:
    for line in f:
        data = json.loads(line)
        facts = parse_table(data['table'])
        for inst in data['instances']:
            if inst['task_type'] != 'indicator': continue
            gt = inst['ground_truth']
            ym = re.search(r'(?<!\d)(20\d{2})(?!\d)', inst['task'])
            year = ym.group(1) if ym else '2023'
            gt_lines = gt.strip().split('\n'); headers = None; gt_map = {}
            for gl in gt_lines:
                gl = gl.strip()
                if not gl or not gl.startswith('|'): continue
                cells = [c.strip() for c in gl.strip('|').split('|')]
                if all(re.match(r'^[-: ]+$', c) for c in cells): continue
                if headers is None: headers = cells[1:]
                elif len(cells) >= 2 and cells[0]:
                    gt_map[cells[0]] = cells[1].replace(',','').strip()

            for metric_name in target_metrics:
                gt_val = gt_map.get(metric_name)
                if not gt_val: continue

                computed = None
                formula_name = ''
                raw_info = ''
                if '存货周转天数' == metric_name:
                    inv = avg_fact(facts, '存货', year)
                    cost = gf(facts, '营业成本', year)
                    if inv and cost and cost != 0:
                        computed = (Decimal('365') * inv / cost).quantize(Decimal('0.0001'))
                        formula_name = '365*avg(存货)/营业成本'
                        inv_end = gf(facts, '存货', year)
                        cost_total = gf(facts, '营业总成本', year)
                        inv_turnover = (cost / inv).quantize(Decimal('0.0001'))
                        raw_info = f'avgInv={inv.quantize(Decimal("0.01"))}, cost={cost.quantize(Decimal("0.01"))}, invTurnover={inv_turnover}'
                elif '应收账款周转天数' == metric_name or '应收帐款周转天数' == metric_name:
                    ar = avg_fact(facts, '应收帐款', year)
                    rev = gf(facts, '营业收入', year)
                    if ar and rev and rev != 0:
                        computed = (Decimal('365') * ar / rev).quantize(Decimal('0.0001'))
                        formula_name = '365*avg(应收帐款)/营业收入'
                        ar_turnover = (rev / ar).quantize(Decimal('0.0001'))
                        raw_info = f'avgAR={ar.quantize(Decimal("0.01"))}, rev={rev.quantize(Decimal("0.01"))}, arTurnover={ar_turnover}'
                elif '应付账款周转天数' == metric_name or '应付帐款周转天数' == metric_name:
                    ap = avg_fact(facts, '应付帐款', year)
                    cost = gf(facts, '营业成本', year)
                    if ap and cost and cost != 0:
                        computed = (Decimal('365') * ap / cost).quantize(Decimal('0.0001'))
                        formula_name = '365*avg(应付帐款)/营业成本'
                        ap_turnover = (cost / ap).quantize(Decimal('0.0001'))
                        raw_info = f'avgAP={ap.quantize(Decimal("0.01"))}, cost={cost.quantize(Decimal("0.01"))}, apTurnover={ap_turnover}'
                elif '营业周期' == metric_name:
                    inv = avg_fact(facts, '存货', year)
                    ar = avg_fact(facts, '应收帐款', year)
                    cost = gf(facts, '营业成本', year)
                    rev = gf(facts, '营业收入', year)
                    if all([inv, ar, cost, rev]) and cost != 0 and rev != 0:
                        inv_days = Decimal('365') * inv / cost
                        ar_days = Decimal('365') * ar / rev
                        computed = (inv_days + ar_days).quantize(Decimal('0.0001'))
                        formula_name = 'invDays+arDays'
                        raw_info = f'invDays={inv_days.quantize(Decimal("0.01"))}, arDays={ar_days.quantize(Decimal("0.01"))}'

                if computed is None: continue
                try:
                    exp_f = float(gt_val); comp_f = float(str(computed))
                    if abs(exp_f) < 1e-10:
                        pct_diff = 0.0
                    else:
                        pct_diff = (comp_f - exp_f) / abs(exp_f) * 100
                except:
                    pct_diff = 0

                if abs(pct_diff) > 1.0:
                    mismatches.append({
                        'company': inst['company_code'],
                        'task_id': inst['task_id'][:8],
                        'metric': metric_name,
                        'gt': gt_val,
                        'computed': str(computed),
                        'pct_diff': round(pct_diff, 2),
                        'formula': formula_name,
                        'raw': raw_info,
                    })

# Print summary
print('='*120)
print('TURNOVER METRICS MISMATCH ANALYSIS')
print('='*120)
print(f'Total turnover mismatches: {len(mismatches)}')

by_metric = defaultdict(list)
for m in mismatches:
    by_metric[m['metric']].append(m)

for metric, items in sorted(by_metric.items()):
    pcts = [i['pct_diff'] for i in items]
    avg_pct = sum(pcts)/len(pcts)
    print(f'\n{metric} ({len(items)} cases):')
    print(f'  Avg deviation: {avg_pct:+.2f}%')
    print(f'  Range: [{min(pcts):+.2f}%, {max(pcts):+.2f}%]')
    positive = sum(1 for p in pcts if p > 0)
    negative = sum(1 for p in pcts if p < 0)
    print(f'  Direction: {positive} over-estimated, {negative} under-estimated')

# Group by company
by_company = defaultdict(list)
for m in mismatches:
    by_company[m['company']].append(m)
print('\n--- BY COMPANY ---')
for company, items in sorted(by_company.items()):
    pcts = [i['pct_diff'] for i in items]
    avg_pct = sum(pcts)/len(pcts)
    print(f'  {company}: {len(items)} mismatches, avg deviation {avg_pct:+.2f}%')

# Show detailed examples
print('\n' + '='*120)
print('DETAILED EXAMPLES (first 3 per metric)')
print('='*120)
for metric, items in sorted(by_metric.items()):
    print(f'\n--- {metric} ---')
    for m in items[:3]:
        print(f'  [{m["task_id"]}] {m["company"]}: computed={m["computed"]}, GT={m["gt"]}, diff={m["pct_diff"]:+.2f}%')
        print(f'    formula: {m["formula"]}')
        print(f'    raw: {m["raw"]}')

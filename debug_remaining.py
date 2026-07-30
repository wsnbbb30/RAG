import json, re
from decimal import Decimal, getcontext
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
DAYS = Decimal('360')

def values_close(e, c):
    try:
        ep = float(e); cp = float(c)
        if abs(ep) < 1e-10: return abs(cp) < 1e-10
        return abs(cp - ep) / abs(ep) <= 0.01
    except: return e == c

# Check remaining AP turnover days
print('=== REMAINING 应付账款周转天数 MISMATCHES ===')
with open('data/FinAR-Bench/dev.txt', 'r', encoding='utf-8') as f:
    for line in f:
        data = json.loads(line)
        facts = parse_table(data['table'])
        for inst in data['instances']:
            if inst['task_type'] != 'indicator': continue
            gt = inst['ground_truth']
            target = ['应付账款周转天数', '应付帐款周转天数']
            if not any(t in gt for t in target): continue
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
            for mn in target:
                gt_val = gt_map.get(mn)
                if not gt_val: continue
                ap = avg_fact(facts, '应付帐款', year)
                cost = gf(facts, '营业成本', year)
                notes_avg = avg_fact(facts, '应付票据', year)
                if ap and cost and cost != 0:
                    cs = str((DAYS * ap / cost).quantize(Decimal('0.0001')))
                    if not values_close(gt_val, cs):
                        cc = inst['company_code']; tid = inst['task_id'][:8]
                        print(f'[{tid}] {cc}: computed={cs}, GT={gt_val}')
                        print(f'  avgAP={ap.quantize(Decimal("0.01"))}, cost={cost.quantize(Decimal("0.01"))}')
                        print(f'  avg应付票据={notes_avg}')
                        if notes_avg:
                            ap_total = ap + notes_avg
                            cs2 = str((DAYS * ap_total / cost).quantize(Decimal('0.0001')))
                            print(f'  with notes={cs2} (match={values_close(gt_val, cs2)})')
                        print()

print('=== REMAINING 应收账款周转率 MISMATCHES ===')
with open('data/FinAR-Bench/dev.txt', 'r', encoding='utf-8') as f:
    for line in f:
        data = json.loads(line)
        facts = parse_table(data['table'])
        for inst in data['instances']:
            if inst['task_type'] != 'indicator': continue
            gt = inst['ground_truth']
            target = ['应收账款周转率', '应收帐款周转率']
            if not any(t in gt for t in target): continue
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
            for mn in target:
                gt_val = gt_map.get(mn)
                if not gt_val: continue
                rev = gf(facts, '营业收入', year)
                ar = avg_fact(facts, '应收帐款', year)
                if rev and ar and ar != 0:
                    cs = str((rev / ar).quantize(Decimal('0.0001')))
                    if not values_close(gt_val, cs):
                        cc = inst['company_code']; tid = inst['task_id'][:8]
                        notes_avg = avg_fact(facts, '应收票据', year)
                        print(f'[{tid}] {cc}: computed={cs}, GT={gt_val}')
                        print(f'  rev={rev.quantize(Decimal("0.01"))}, avgAR={ar.quantize(Decimal("0.01"))}')
                        print(f'  avg应收票据={notes_avg}')
                        if notes_avg:
                            ar_total = ar + notes_avg
                            cs2 = str((rev / ar_total).quantize(Decimal('0.0001')))
                            print(f'  with notes={cs2} (match={values_close(gt_val, cs2)})')
                        print()

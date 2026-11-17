# -*- coding: utf-8 -*-
"""
V9.18 村务系统枚举整理 + 关联字段下拉补全
三项整改：
1) 枚举：清理重复 enumMap 注入；按完整值域词库重写 enumMap；
   dictText('sys_status')（字典表为空导致裸数字）统一改走 enumText。
2) 关联字段：表格列/详情显示 refText 名称；缺 ref 机制的页面补全；
   vvLoadRefs 映射按表外键补全；表单缺失下拉的外键补 el-select。
3) 顺带清点：输出每类改动数量，便于验收。
"""
import json, re
from pathlib import Path

ROOT = Path(r'D:/workspace/xyh-devbranch/codegen-demo-target/village_affairs-web/src/views')
TPL = Path(r'D:/workspace/xyh-devbranch/codegen-server/src/main/resources/industry-templates/village_affairs.json')

def camel(n):
    p = n.split('_')
    return p[0] + ''.join(x.capitalize() for x in p[1:])

# 完整值域枚举词库（与演示数据实际值分布对齐：status 0-4、priority 1-5、level 1-5、rank 1-5）
ENUM_LIB = {
    'status':   [(1, '正常'), (2, '待审核'), (3, '进行中'), (4, '已归档'), (0, '停用')],
    'priority': [(1, '紧急'), (2, '高'), (3, '中'), (4, '低'), (5, '较低')],
    'level':    [(1, '一级'), (2, '二级'), (3, '三级'), (4, '四级'), (5, '五级')],
    'rank':     [(1, '优秀'), (2, '良好'), (3, '合格'), (4, '基本合格'), (5, '不合格')],
}
ENUM_COLS = {'status', 'priority', 'level', 'rank'}

REF_MAP = {
    'villageId': ('village_area', 'area_name'),
    'householdId': ('village_household', 'head_name'),
    'gridId': ('village_grid', 'grid_name'),
    'residentId': ('village_resident', 'resident_name'),
    'parcelId': ('village_land_parcel', 'parcel_name'),
    'assetId': ('village_asset', 'asset_name'),
    'ownerId': ('sys_user', 'username'),
    'userId': ('sys_user', 'username'),
    'handlerId': ('sys_user', 'username'),
    'planId': ('village_assess_plan', 'plan_name'),
    'contractId': ('village_contract', 'contract_name'),
    'accountId': ('village_fund_account', 'account_name'),
    'disputeId': ('village_dispute', 'case_no'),
    'archiveId': ('village_archive', 'title'),
    'budgetId': ('village_fund_budget', 'budget_name'),
    'houseId': ('village_house', 'house_no'),
    'memberId': ('village_grid_member', 'member_name'),
    'tagId': ('tag', 'tag_name'),
}

stat = {k: 0 for k in ('enum_dup_removed', 'enum_map_rewrite', 'dict_col', 'dict_detail',
                       'dict_form', 'ref_new_page', 'ref_map_add', 'ref_col', 'ref_detail',
                       'enum_detail', 'form_select', 'query_select')}

tpl = json.load(open(TPL, encoding='utf-8'))

def build_enum_block(fields):
    parts = []
    for f in fields:
        entries = ', '.join("{ value: %d, label: '%s' }" % (v, l) for v, l in ENUM_LIB[f])
        parts.append(f'{f}: [{entries}]')
    return 'enumMap: {\n        ' + ',\n        '.join(parts) + '\n      },'

def ref_methods_js(ref_fields):
    all_map = ', '.join(f"{f}: ['{REF_MAP[f][0]}', '{REF_MAP[f][1]}']" for f in ref_fields)
    return f'''async vvSearchRef(field, refTable, label, keyword) {{
      this.vvRefLoading[field] = true
      try {{
        const res = await request({{ url: '/api/demo/' + this.$options.name.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase() + '/options', method: 'get', params: {{ refTable, label, keyword: keyword || '' }} }})
        this.vvRefOptions[field] = (res && res.data || [])
      }} catch (e) {{
        this.vvRefOptions[field] = []
      }} finally {{
        this.vvRefLoading[field] = false
      }}
    }},
    refText(field, value) {{
      if (value === undefined || value === null || value === '') return ''
      const hit = (this.vvRefOptions[field] || []).find(o => String(o.id) === String(value))
      return hit ? hit.label : String(value)
    }},
    vvLoadRefs() {{
      const all = {{{all_map}}}
      Object.keys(all).forEach(f => this.vvSearchRef(f, all[f][0], all[f][1], ''))
    }},'''

REF_METHOD_SIG = 'async vvSearchRef(field, refTable, label, keyword) {'

for m in tpl['modules']:
    for t in m['tables']:
        tn = t['tableName']
        vd = ROOT / tn / 'index.vue'
        if not vd.exists():
            continue
        src = vd.read_text(encoding='utf-8')
        orig = src

        # ---- 该表元数据 ----
        enum_fields = [camel(c['name']) for c in t['columns'] if c['name'] in ENUM_COLS and 'int' in c['type']]
        fk_fields = []
        for c in t['columns']:
            jf = camel(c['name'])
            if jf in REF_MAP and jf not in fk_fields:
                fk_fields.append(jf)

        # ---- 1a. 清理重复 enumMap（连排的注入块只留最后一处位置，整体重写一次）----
        dup_pat = re.compile(r'// <village-enums-v9\.18>\s*enumMap: \{.*?\},\s*(?=// <village-enums-v9\.18>|      rules:)', re.S)
        dupes = dup_pat.findall(src)
        if dupes:
            stat['enum_dup_removed'] += 1
        # 先统一删掉全部旧 enumMap 定义（含 gen:begin 内的原生块与注入块）
        src = re.sub(r'// <village-enums-v9\.18>\s*', '', src)
        src = re.sub(r'enumMap: \{.*?\n      \},\n', '', src, count=1, flags=re.S) if 'enumMap: {' in src else src
        while re.search(r'// <village-enums-v9\.18>\s*enumMap: \{.*?\},\n', src, re.S):
            src = re.sub(r'// <village-enums-v9\.18>\s*enumMap: \{.*?\},\n', '', src, count=1, flags=re.S)
        # 再插入统一版（放在 rules: 前）
        if enum_fields:
            block = build_enum_block(enum_fields) + '\n'
            src = src.replace('      rules: {', '      ' + block + '      rules: {', 1)
            stat['enum_map_rewrite'] += 1

        # ---- 1b. dictText('sys_status') 改 enumText ----
        n1 = len(re.findall(r"dictText\('sys_status',\s*(row\.\w+|r\.\w+|detail\.\w+|scope\.row\.\w+)\)", src))
        if n1:
            src = re.sub(r"dictText\('sys_status',\s*(row\.\w+|r\.\w+|detail\.\w+|scope\.row\.\w+)\)",
                         lambda mm: "enumText('status', %s)" % mm.group(1), src)
            stat['dict_col'] += n1

        # ---- 1c. 表单 dicts.sys_status → enumMap.status ----
        n2 = src.count('dicts.sys_status || []')
        if n2:
            src = src.replace('dicts.sys_status || []', 'enumMap.status || []')
            stat['dict_form'] += n2

        # ---- 2a. 缺 ref 机制的页面注入 ----
        has_ref = REF_METHOD_SIG in src
        if not has_ref and fk_fields:
            data_inject = '      vvRefOptions: {},\n      vvRefLoading: {},\n'
            src = src.replace('      dicts: { sys_status: [] },\n',
                              '      dicts: { sys_status: [] },\n' + data_inject, 1)
            mm = re.search(r'    async loadDicts\(\) \{.*?\n    \},\n', src, re.S)
            if mm:
                src = src.replace(mm.group(0), mm.group(0) + '    ' + ref_methods_js(fk_fields) + '\n', 1)
            src = src.replace('    this.loadDicts()\n', '    if (this.vvLoadRefs) this.vvLoadRefs()\n    this.loadDicts()\n', 1)
            stat['ref_new_page'] += 1

        # ---- 2b. vvLoadRefs 映射补全 ----
        if has_ref or fk_fields:
            mm = re.search(r'const all = \{([^}]*)\}', src)
            if mm:
                have = set(re.findall(r'(\w+):', mm.group(1)))
                need = [f for f in fk_fields if f not in have]
                if need:
                    add = ', '.join(f"{f}: ['{REF_MAP[f][0]}', '{REF_MAP[f][1]}']" for f in need)
                    src = src.replace(mm.group(0), 'const all = {' + mm.group(1).rstrip().rstrip(',') + ', ' + add + '}')
                    stat['ref_map_add'] += len(need)

        # ---- 2c. 表格列外键加 refText formatter ----
        for f in fk_fields:
            mm = re.search(rf'(<el-table-column[^>]*prop="{f}"[^>]*?)(\s*/>)', src)
            if mm and 'refText' not in mm.group(0):
                src = src[:mm.start()] + mm.group(1) + f' :formatter="(r)=>refText(\'{f}\', r.{f})"' + mm.group(2) + src[mm.end():]
                stat['ref_col'] += 1

        # ---- 2d. 详情外键 → refText ----
        for f in fk_fields:
            pat = r'\{\{ detail\.' + f + r' \}\}'
            n = len(re.findall(pat, src))
            if n:
                src = re.sub(pat, lambda _m: "{{ refText('%s', detail.%s) }}" % (f, f), src)
                stat['ref_detail'] += n

        # ---- 2e. 详情裸枚举 → enumText ----
        for f in enum_fields:
            pat = r'\{\{ detail\.' + f + r' \}\}'
            n = len(re.findall(pat, src))
            if n:
                src = re.sub(pat, lambda _m: "{{ enumText('%s', detail.%s) }}" % (f, f), src)
                stat['enum_detail'] += n

        # ---- 2f. 表单缺失下拉的外键补 el-select ----
        for f in fk_fields:
            fm = re.search(rf'(<el-form-item label="[^"]*" prop="{f}">\s*)<el-input[^>]*/>(\s*</el-form-item>)', src)
            if fm and f"vvSearchRef('{f}'" not in src.split('el-form-item')[-1]:
                ref_tbl, ref_label = REF_MAP[f]
                sel = (f"<el-select v-model=\"form.{f}\" clearable filterable remote style=\"width: 100%\" "
                       f"placeholder=\"输入关键字搜索\" :remote-method=\"kw => vvSearchRef('{f}', '{ref_tbl}', '{ref_label}', kw)\" "
                       f":loading=\"vvRefLoading['{f}']\"><el-option v-for=\"o in vvRefOptions['{f}'] || []\" :key=\"o.id\" "
                       f":label=\"o.label\" :value=\"o.id\" /></el-select>")
                src = src[:fm.start()] + fm.group(1) + sel + fm.group(2) + src[fm.end():]
                stat['form_select'] += 1

        # ---- 2g. 查询区外键输入 → 下拉 ----
        for f in fk_fields:
            qm = re.search(rf'(<el-form-item[^>]*prop="{f}"[^>]*>\s*)(<el-input[^>]*v-model="query\.{f}"[^>]*?)(\s*/>)', src)
            if qm:
                ref_tbl, ref_label = REF_MAP[f]
                sel = (f"<el-select v-model=\"query.{f}\" clearable filterable remote style=\"width: 160px\" "
                       f"placeholder=\"请选择\" :remote-method=\"kw => vvSearchRef('{f}', '{ref_tbl}', '{ref_label}', kw)\" "
                       f":loading=\"vvRefLoading['{f}']\"><el-option v-for=\"o in vvRefOptions['{f}'] || []\" :key=\"o.id\" "
                       f":label=\"o.label\" :value=\"o.id\" /></el-select>")
                src = src[:qm.start()] + qm.group(1) + sel + qm.group(3) + src[qm.end():]
                stat['query_select'] += 1

        if src != orig:
            vd.write_text(src, encoding='utf-8')

print(json.dumps(stat, ensure_ascii=False, indent=1))

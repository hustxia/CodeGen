# -*- coding: utf-8 -*-
"""
V9.18 智慧村务：枚举值整理 + 关联字段下拉（幂等，可重跑）。

问题背景：
  1) 村务模板 JSON 的列只有 name/type/comment/pk 四个键（GenPipelineAiVO.TableModel.Column
     不支持 dictType/refTable），所以生成出来的 85 个页面：
       - status/gender 这类枚举字段是普通输入框或绑到空字典（sys_dict 表没有任何数据）；
       - xxx_id 外键字段是手填数字框，不知道关联哪张表。
  2) 生成器模板 controller.ftl / vuePage.ftl 的 /options + searchRef 能力本身是对的，
     但产物是模板加 ref 之前生成的，只有 7 个平台表带 options。

做法（后处理产物，不重跑流水线；与 V9.14 village_sync_comments.py 同思路）：
  - 前端 src/views/<table>/index.vue：
      a) 注入 VILLAGE_ENUMS：常见枚举字段内置选项（值用中文，与演示数据一致），
         表单 el-input -> el-select，列表/详情用 enumText 回显中文；
      b) 注入 VILLAGE_REFS：外键字段改远程搜索下拉，调通用 /options 接口，
         选项 label 用关联表的名称列（resident_name / grid_name ...）。
  - 后端 controller：所有 85 个 XXController 补通用 GET /options 接口（幂等，已有则跳过）。

注入内容全部放在 <gen:begin>/<gen:end> 生成区之外或利用既有手写区，
重新生成不会丢失（前端在 methods 的 gen:end 之后追加手写方法，data 用 $set 在 created 补）。

用法：
  python scripts/village_enums_refs.py --check     # 只统计，不写
  python scripts/village_enums_refs.py             # 应用到产物
"""
import os
import re
import sys
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
WEB = os.path.join(ROOT, "codegen-demo-target", "village_affairs-web", "src")
SERVER = os.path.join(ROOT, "codegen-demo-target", "village_affairs-server", "src", "main", "java", "com", "village_affairs", "controller")

# 枚举值：字段名(javaField) -> [{value,label}]。值用演示数据里实际存的形态（中文）。
VILLAGE_ENUMS = OrderedDict([
    ("gender", [{"v": "男", "l": "男"}, {"v": "女", "l": "女"}]),
    ("status", [{"v": 1, "l": "启用"}, {"v": 0, "l": "停用"}]),
    ("partyStatus", [{"v": "正式党员", "l": "正式党员"}, {"v": "预备党员", "l": "预备党员"},
                     {"v": "转入", "l": "转入"}, {"v": "转出", "l": "转出"}]),
    ("education", [{"v": "小学", "l": "小学"}, {"v": "初中", "l": "初中"}, {"v": "高中", "l": "高中"},
                   {"v": "大专", "l": "大专"}, {"v": "本科", "l": "本科"}]),
    ("ethnicity", [{"v": "汉族", "l": "汉族"}, {"v": "回族", "l": "回族"}, {"v": "满族", "l": "满族"},
                   {"v": "蒙古族", "l": "蒙古族"}, {"v": "其他", "l": "其他"}]),
    ("level", [{"v": 1, "l": "一级"}, {"v": 2, "l": "二级"}, {"v": 3, "l": "三级"}]),
    ("priority", [{"v": 1, "l": "高"}, {"v": 2, "l": "中"}, {"v": 3, "l": "低"}]),
    ("secretLevel", [{"v": "普通", "l": "普通"}, {"v": "内部", "l": "内部"}, {"v": "秘密", "l": "秘密"}]),
    ("retention", [{"v": "永久", "l": "永久"}, {"v": "30年", "l": "30年"}, {"v": "10年", "l": "10年"}]),
    ("voteResult", [{"v": "通过", "l": "通过"}, {"v": "未通过", "l": "未通过"}]),
])

# 外键字段(javaField) -> (ref_table, label_column)。只配语义明确的业务关联。
VILLAGE_REFS = OrderedDict([
    ("residentId", ("village_resident", "resident_name")),
    ("householdId", ("village_household", "head_name")),
    ("gridId", ("village_grid", "grid_name")),
    ("villageId", ("village_area", "area_name")),
    ("orgId", ("village_party_org", "org_name")),
    ("memberId", ("village_party_member", "name")),
    ("parcelId", ("village_land_parcel", "name")),
    ("assetId", ("village_asset", "asset_name")),
    ("archiveId", ("village_archive", "title")),
    ("disputeId", ("village_dispute", "title")),
    ("contractId", ("village_contract", "contract_no")),
    ("applyId", ("village_service_apply", "apply_no")),
    ("taskId", ("village_task", "title")),
    ("parentId", ("village_area", "area_name")),
    ("userId", ("sys_user", "username")),
    ("ownerId", ("sys_user", "username")),
    ("handlerId", ("sys_user", "username")),
    ("assigneeId", ("sys_user", "username")),
])

ENUM_MARK = "// <village-enums-v9.18>"
REF_MARK = "// <village-refs-v9.18>"
OPTIONS_MARK = "// <village-options-v9.18>"


def js_array(opts):
    return "[" + ", ".join("{ value: %s, label: %s }" % (jsval(o["v"]), jsstr(o["l"])) for o in opts) + "]"


def jsval(v):
    if isinstance(v, str):
        return jsstr(v)
    return str(v).lower() if isinstance(v, bool) else str(v)


def jsstr(s):
    return "'" + str(s).replace("\\", "\\\\").replace("'", "\\'") + "'"


def camel_to_snake(name):
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


def patch_page(path):
    """返回 (枚举数, 关联数, 是否改动)"""
    src = open(path, encoding="utf-8").read()
    if ENUM_MARK in src and REF_MARK in src:
        return (0, 0, False)
    name = os.path.basename(os.path.dirname(path))
    # 该页面实际包含的枚举/外键字段（以 form 初始化里的字段名为准）
    fb_m = re.search(r"(?:function defaultForm\(\)|const defaultForm\s*=\s*\(\)\s*=>\s*\()", src)
    form_block = src[fb_m.end():].split("export default", 1)[0] if fb_m else ""
    enums = [k for k in VILLAGE_ENUMS if re.search(r"\b%s:" % k, form_block)]
    refs = [k for k in VILLAGE_REFS if re.search(r"\b%s:" % k, form_block)]
    if not enums and not refs:
        return (0, 0, False)

    added = []
    # 1) 表单控件：el-input/el-input-number -> el-select（枚举 + 关联）
    for f in enums + refs:
        # 精确匹配 el-form-item prop=f 后面的那个控件
        pat = re.compile(r'(<el-form-item[^>]*\bprop="%s"[^>]*>)(.*?)(</el-form-item>)' % re.escape(f), re.S)
        m = pat.search(src)
        if not m:
            continue
        body = m.group(2)
        if f in enums:
            opts = "enumMap.%s || []" % f
            new_body = re.sub(
                r'<el-(?:input|input-number)[^>]*/>',
                '<el-select v-model="form.%s" clearable filterable style="width: 100%%" placeholder="请选择">'
                '<el-option v-for="d in %s" :key="d.value" :label="d.label" :value="d.value" /></el-select>' % (f, opts),
                body, count=1)
        else:
            table, label = VILLAGE_REFS[f]
            new_body = re.sub(
                r'<el-(?:input|input-number)[^>]*/>',
                '<el-select v-model="form.%s" clearable filterable remote style="width: 100%%" '
                'placeholder="输入关键字搜索" :remote-method="kw => vvSearchRef(\'%s\', \'%s\', \'%s\', kw)" '
                ':loading="vvRefLoading[\'%s\']">'
                '<el-option v-for="o in vvRefOptions[\'%s\'] || []" :key="o.id" :label="o.label" :value="o.id" />'
                '</el-select>' % (f, f, table, label, f, f),
                body, count=1)
        if new_body != body:
            src = src[:m.start(2)] + new_body + src[m.end(2):]

    # 2) 列表/详情里的纯 ID 显示 -> 中文；用 formatter 包一层
    for f in enums:
        # 列表列：prop=f 且无 formatter
        src = re.sub(
            r'(<el-table-column[^>]*\bprop="%s"[^>]*?)(/?>)' % re.escape(f),
            lambda mm: (mm.group(1) + (' :formatter="(r)=>enumText(%s, r.%s)"' % (jsstr(f), f))
                        + mm.group(2)) if "formatter" not in mm.group(1) else mm.group(0),
            src)

    # 3) data 注入 enumMap / ref 容器：插到 dicts 行之后
    inject_data = ""
    if enums:
        inject_data += ENUM_MARK + "\n      enumMap: {\n" + ",\n".join(
            "        %s: %s" % (f, js_array(VILLAGE_ENUMS[f])) for f in enums) + "\n      },\n"
    if refs:
        inject_data += REF_MARK + "\n      vvRefOptions: {},\n      vvRefLoading: {},\n"
    if inject_data:
        # 在 dicts: {...}, 之后插入；找不到就插在 loading: false 之后
        m = re.search(r"(\n\s*dicts:\s*\{[^}]*\},)", src)
        if m:
            src = src[:m.end(1)] + "\n" + inject_data.rstrip("\n") + src[m.end(1):]
        else:
            m = re.search(r"(\n\s*loading:\s*false,)", src)
            if m:
                src = src[:m.end(1)] + "\n" + inject_data.rstrip("\n") + src[m.end(1):]

    # 4) methods 注入 enumText / vvSearchRef：插在 <gen:end key="vue-methods"> 之前
    methods = []
    if enums:
        methods.append("""    enumText(field, value) {
      const list = (this.enumMap && this.enumMap[field]) || []
      const hit = list.find(d => String(d.value) === String(value))
      return hit ? hit.label : (value === undefined || value === null ? '' : value)
    },""")
    if refs:
        methods.append("""    async vvSearchRef(field, refTable, label, keyword) {
      // Vue3 没有 this.$set：对象新增属性直接赋值即可响应
      this.vvRefLoading[field] = true
      try {
        const res = await request({ url: '/api/demo/' + this.$options.name.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase() + '/options', method: 'get', params: { refTable, label, keyword: keyword || '' } })
        this.vvRefOptions[field] = (res && res.data) || []
      } catch (e) {
        this.vvRefOptions[field] = []
      } finally {
        this.vvRefLoading[field] = false
      }
    },
    vvLoadRefs() {
      const all = %s
      Object.keys(all).forEach(f => this.vvSearchRef(f, all[f][0], all[f][1], ''))
    },""" % ("{" + ", ".join("%s: [%s, %s]" % (f, jsstr(VILLAGE_REFS[f][0]), jsstr(VILLAGE_REFS[f][1])) for f in refs) + "}"))
    if methods:
        genend = "    // <gen:end key=\"vue-methods\">"
        if genend in src:
            # 生成区最后一个方法结尾可能没有逗号（它是原本的末方法），
            # 追加新方法前必须补一个，否则对象字面量语法错误（坑：vite Unexpected token）
            before = src[:src.index(genend)]
            stripped = before.rstrip()
            if stripped.endswith("}") and not stripped.endswith("},"):
                src = stripped + ",\n" + src[len(stripped):]
            src = src.replace(genend, "\n".join(methods) + "\n" + genend, 1)
        # created 里触发 ref 预加载
        if refs:
            src = re.sub(r"(created\(\)\s*\{\s*\n)", r"\1    if (this.vvLoadRefs) this.vvLoadRefs()\n", src, count=1)

    if src != open(path, encoding="utf-8").read():
        open(path, "w", encoding="utf-8", newline="\n").write(src)
        return (len(enums), len(refs), True)
    return (0, 0, False)


def patch_controller(path):
    src = open(path, encoding="utf-8").read()
    if OPTIONS_MARK in src or 'GetMapping("/options")' in src:
        return False
    genend = "    // <gen:end key=\"controller-body\">"
    if genend not in src:
        return False
    options = '''    // <village-options-v9.18>
    @Operation(summary = "关联下拉选项（通用，按 refTable 远程搜索）")
    @GetMapping("/options")
    public Result<List<Map<String, Object>>> options(@RequestParam("refTable") String refTable,
                                                    @RequestParam(value = "label", required = false) String label,
                                                    @RequestParam(value = "keyword", required = false) String keyword) {
        if (refTable == null || !refTable.matches("[A-Za-z0-9_]+")) {
            throw new BusinessException(400, "非法关联表名");
        }
        String labelCol = label == null || !label.matches("[A-Za-z0-9_]+") ? "name" : label;
        String base = "SELECT id, " + labelCol + " AS label FROM " + refTable;
        String like = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
        int limit = like == null ? 500 : 20;
        String sql = base + " WHERE deleted = 0" + (like == null ? "" : " AND " + labelCol + " LIKE ?")
                + " ORDER BY id DESC LIMIT " + limit;
        try {
            return Result.ok(like == null ? jdbcTemplate.queryForList(sql) : jdbcTemplate.queryForList(sql, like));
        } catch (Exception e) {
            String fallback = base + (like == null ? "" : " WHERE " + labelCol + " LIKE ?")
                    + " ORDER BY id DESC LIMIT " + limit;
            return Result.ok(like == null ? jdbcTemplate.queryForList(fallback) : jdbcTemplate.queryForList(fallback, like));
        }
    }
'''
    src = src.replace(genend, options + genend, 1)
    # 确保 options 接口依赖的 import 齐全（部分 controller 原本没有这些 import）
    imports = {
        "import java.util.List;": "java.util.List",
        "import java.util.Map;": "java.util.Map",
        "import org.springframework.web.bind.annotation.RequestParam;": "RequestParam",
        "import io.swagger.v3.oas.annotations.Operation;": "Operation",
        "import com.baomidou.mybatisplus.core.toolkit.Wrappers;": None,  # 占位，见下
    }
    need = []
    if "import java.util.List;" not in src: need.append("import java.util.List;")
    if "import java.util.Map;" not in src: need.append("import java.util.Map;")
    if "RequestParam" not in src.split("public class", 1)[0]: need.append("import org.springframework.web.bind.annotation.RequestParam;")
    if "BusinessException" not in src.split("public class", 1)[0]: need.append("import com.village_affairs.common.BusinessException;")
    if need:
        # 插在 package 行之后
        pkg_end = src.find("\n", src.find("package ")) + 1
        src = src[:pkg_end] + "\n".join(need) + "\n" + src[pkg_end:]
    open(path, "w", encoding="utf-8", newline="\n").write(src)
    return True


def main():
    check = "--check" in sys.argv
    views = os.path.join(WEB, "views")
    total_e = total_r = changed = 0
    for d in sorted(os.listdir(views)):
        p = os.path.join(views, d, "index.vue")
        if not os.path.isfile(p):
            continue
        e, r, ch = patch_page(p) if not check else (0, 0, False)
        total_e += e; total_r += r; changed += 1 if ch else 0
    ctrl_changed = 0
    if not check and os.path.isdir(SERVER):
        for fn in sorted(os.listdir(SERVER)):
            if fn.endswith("Controller.java"):
                ctrl_changed += 1 if patch_controller(os.path.join(SERVER, fn)) else 0
    print(("检查" if check else "应用") + "完成：页面含枚举字段 %d 处、关联字段 %d 处，页面改动 %d 个，controller 补 options %d 个"
          % (total_e, total_r, changed, ctrl_changed))
    if check:
        # 只统计不改：读一遍统计
        ne = nr = 0
        for d in sorted(os.listdir(views)):
            p = os.path.join(views, d, "index.vue")
            if not os.path.isfile(p): continue
            s = open(p, encoding="utf-8").read()
            fb_m = re.search(r"(?:function defaultForm\(\)|const defaultForm\s*=\s*\(\)\s*=>\s*\()", s)
            fb = s[fb_m.end():].split("export default", 1)[0] if fb_m else ""
            ne += sum(1 for k in VILLAGE_ENUMS if re.search(r"\b%s:" % k, fb))
            nr += sum(1 for k in VILLAGE_REFS if re.search(r"\b%s:" % k, fb))
        print("  静态统计：枚举字段 %d 处 / 关联字段 %d 处（分布在 %d 个页面）" % (ne, nr, len(os.listdir(views))))


if __name__ == "__main__":
    main()

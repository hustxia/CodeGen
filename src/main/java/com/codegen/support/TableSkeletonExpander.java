package com.codegen.support;

import com.codegen.vo.GenPipelineAiVO;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * V9.7 两段式骨架生成的第二段：把「表名 + 字段名清单」按命名语义规则补全成完整表结构。
 *
 * <p>背景：AI 完整建模时每列要输出 name/type/comment/pk 四个字段，其中类型与 pk
 * 占了输出量的大头，且小模型经常在这里出错（varchar 长度乱给、pk 漏标）。
 * 骨架模式让模型只出字段名+注释（输出量降 60%+，本地 4B 从 3 分钟/模块降到 1 分钟内），
 * 类型、长度、主键由本类按命名语义推导——规则生成又快又稳定。
 *
 * <p>推导口径（全部小写匹配列名）：
 * <ul>
 *   <li>id / *_id → bigint；骨架无 pk 时补 id 主键，已有 id 列直接升为 pk</li>
 *   <li>*_no / *_code / code / no → varchar(64)（单号、编码）</li>
 *   <li>name / title 及 *_name / *_title → varchar(128)</li>
 *   <li>*_remark / *_description / *_content / *_detail / *_summary 等 → text（长文本）</li>
 *   <li>email → varchar(128)；phone/mobile → varchar(20)；url/avatar/image 等 → varchar(512)</li>
 *   <li>*_amount / *_price / *_fee / *_money / *_balance → decimal(12,2)；rate/percent → decimal(5,2)</li>
 *   <li>lat/lng → decimal(10,6)</li>
 *   <li>*_time / *_at → datetime；birthday / *_date → date</li>
 *   <li>status / type / sex / is_* / *_flag / *_enabled 等 → tinyint</li>
 *   <li>数量类（*_num / *_count / *_qty / quantity / stock / score / sort / level / priority / version）→ int</li>
 *   <li>兜底 → varchar(255)</li>
 * </ul>
 *
 * <p>审计列（create_time/update_time/create_by/update_by/deleted）不在本类职责内，
 * 建库阶段由 {@code GenAuditColumns} 统一补齐——与 AI 完整模式同一约定。
 */
public final class TableSkeletonExpander {

    private TableSkeletonExpander() {
    }

    /**
     * 补全骨架表：逐列推类型、保证唯一主键、表名/列名归一为小写 snake_case。
     * 输入的列若已带 type（模型不听话多给了）则信任模型值，只补缺失部分。
     */
    public static List<GenPipelineAiVO.TableModel> expand(List<GenPipelineAiVO.TableModel> skeletons) {
        List<GenPipelineAiVO.TableModel> out = new ArrayList<>();
        if (skeletons == null) {
            return out;
        }
        for (GenPipelineAiVO.TableModel t : skeletons) {
            if (t == null || blank(t.getTableName())) {
                continue;
            }
            GenPipelineAiVO.TableModel full = new GenPipelineAiVO.TableModel();
            full.setTableName(t.getTableName().trim().toLowerCase(Locale.ROOT));
            full.setTableComment(t.getTableComment() == null ? "" : t.getTableComment().trim());

            Set<String> seen = new LinkedHashSet<>();
            List<GenPipelineAiVO.Column> cols = new ArrayList<>();
            boolean hasPk = false;
            if (t.getColumns() != null) {
                for (GenPipelineAiVO.Column c : t.getColumns()) {
                    if (c == null || blank(c.getName())) {
                        continue;
                    }
                    String name = normalizeName(c.getName());
                    if (!seen.add(name) || "id".equals(name)) {
                        continue; // 重名列丢弃；模型自己给的 id 由统一主键接管
                    }
                    GenPipelineAiVO.Column col = new GenPipelineAiVO.Column();
                    col.setName(name);
                    col.setComment(c.getComment() == null ? "" : c.getComment().trim());
                    col.setType(hasText(c.getType()) ? c.getType().trim() : inferType(name));
                    cols.add(col);
                }
            }
            // 统一主键：骨架模式模型不输出 pk，这里补 id 放在业务列之前
            GenPipelineAiVO.Column id = new GenPipelineAiVO.Column();
            id.setName("id");
            id.setType("bigint");
            id.setComment("主键");
            id.setPk(true);
            cols.add(0, id);
            full.setColumns(cols);
            out.add(full);
        }
        return out;
    }

    /** 按列名语义推断 MySQL 类型（见类注释口径表） */
    static String inferType(String rawName) {
        String name = normalizeName(rawName);
        if (name.isEmpty()) {
            return "varchar(255)";
        }
        if ("id".equals(name) || name.endsWith("_id")) {
            return "bigint";
        }
        if ("email".equals(name)) {
            return "varchar(128)";
        }
        if (name.endsWith("account")) {
            return "varchar(64)"; // account 结尾会被下面的 count 规则误判成 int，先拦
        }
        if ("phone".equals(name) || "mobile".equals(name) || "tel".equals(name)
                || name.endsWith("_phone") || name.endsWith("_mobile")) {
            return "varchar(20)";
        }
        if (endsWithAny(name, "no", "code")) {
            return "varchar(64)";
        }
        if ("name".equals(name) || name.endsWith("_name")
                || "title".equals(name) || name.endsWith("_title")) {
            return "varchar(128)";
        }
        if (endsWithAny(name, "remark", "description", "content", "detail", "summary",
                "intro", "notice", "answer", "reply", "reason", "address")) {
            return "text";
        }
        if (endsWithAny(name, "url", "link", "avatar", "image", "icon", "logo",
                "photo", "pic", "img", "attachment", "file")) {
            return "varchar(512)";
        }
        if (endsWithAny(name, "amount", "price", "fee", "money", "balance", "cost", "total")) {
            return "decimal(12,2)";
        }
        if (endsWithAny(name, "rate", "percent", "ratio", "discount", "weight")) {
            return "decimal(5,2)";
        }
        if ("lat".equals(name) || "lng".equals(name)
                || "latitude".equals(name) || "longitude".equals(name)) {
            return "decimal(10,6)";
        }
        if (endsWithAny(name, "time") || name.endsWith("_at")) {
            return "datetime";
        }
        if ("birthday".equals(name) || name.endsWith("_date") || "date".equals(name)) {
            return "date";
        }
        if (name.startsWith("is_")
                || endsWithAny(name, "status", "state", "type", "flag", "enabled", "disabled",
                "locked", "sex", "gender", "level_type")) {
            return "tinyint";
        }
        if (endsWithAny(name, "num", "count", "qty", "quantity", "stock", "score", "points",
                "sort", "order_sort", "priority", "level", "version", "age", "days", "minutes")) {
            return "int";
        }
        return "varchar(255)";
    }

    /** 列名归一：小写、空白转下划线、压缩连续下划线、去首尾下划线 */
    static String normalizeName(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\-]+", "_")
                .replaceAll("_+", "_");
        while (s.startsWith("_")) {
            s = s.substring(1);
        }
        while (s.endsWith("_")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static boolean endsWithAny(String name, String... suffixes) {
        for (String suffix : suffixes) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }
}

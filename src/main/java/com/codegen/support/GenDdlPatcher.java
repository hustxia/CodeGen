package com.codegen.support;

import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.ResultCode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 手动 DDL 改写补齐器（V5.1 P0-1）：对用户粘贴的 CREATE TABLE 文本<strong>只增不改</strong>——
 * 在列定义段末尾插入缺失的审计列（定义与 AI 模式共用 {@link GenAuditColumns}），
 * 缺 id 时补 id 列 + PRIMARY KEY。
 *
 * <p>边界（V5.1 规划风险 1）：
 * <ul>
 *   <li>用户已有列/类型/注释/业务列一概不动；已含某审计列的表跳过</li>
 *   <li>只处理 CREATE TABLE ... ENGINE 语句；出现了 CREATE TABLE 但正则匹配不上的
 *       （如缺 ENGINE、怪异书写），整体抛异常降级阻断，绝不模糊匹配改错 SQL</li>
 * </ul>
 */
public final class GenDdlPatcher {

    /** 与原 lintDdlText 同一口径的匹配边界：括号体 + ENGINE 结尾 */
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?is)CREATE\\s+TABLE\\s+(IF\\s+NOT\\s+EXISTS\\s+)?`?(\\w+)`?\\s*\\((.*?)\\)\\s*ENGINE",
            Pattern.DOTALL);
    /** 声明数计数用：宽松匹配所有 CREATE TABLE 出现处 */
    private static final Pattern CREATE_TABLE_DECLARED = Pattern.compile("(?i)CREATE\\s+TABLE");

    /**
     * DDL 是否显式建库（CREATE DATABASE / CREATE SCHEMA）。
     * 坑 38：不能用 contains("use ") 这种子串判断——"mdm_warehouse (" 恰好含子串
     * "use "（wareh<b>ouse (</b>），会把「该补 CREATE DATABASE」误判成「用户自己会切库」，
     * 目标库没建，后续 USE 静默回落把 43 张表全建进了平台库。必须按语句级匹配。
     */
    private static final Pattern CREATE_DATABASE_STMT = Pattern.compile("(?i)\\bCREATE\\s+(DATABASE|SCHEMA)\\b");

    /** 行首的 USE 语句（脚本切库的标准写法）；注释行 "-- use x" 不算 */
    private static final Pattern USE_STMT = Pattern.compile("(?im)^\\s*USE\\s+[^;\\s]+");

    private GenDdlPatcher() {
    }

    /** DDL 里是否包含 CREATE DATABASE / CREATE SCHEMA 语句（语句级，防子串误判） */
    public static boolean createsDatabase(String ddl) {
        return ddl != null && CREATE_DATABASE_STMT.matcher(ddl).find();
    }

    /** DDL 里是否有行首 USE 语句（用户自己切库，平台不必代建库） */
    public static boolean hasUseStatement(String ddl) {
        return ddl != null && USE_STMT.matcher(ddl).find();
    }

    /** 单表补齐明细：patchedCols 为空 = 六列全齐 SKIPPED */
    public static class PatchItem {
        private final String table;
        private final List<String> patchedCols;

        public PatchItem(String table, List<String> patchedCols) {
            this.table = table;
            this.patchedCols = patchedCols;
        }

        public String getTable() {
            return table;
        }

        public List<String> getPatchedCols() {
            return patchedCols;
        }
    }

    public static class PatchResult {
        private final String ddl;
        private final List<PatchItem> items;

        PatchResult(String ddl, List<PatchItem> items) {
            this.ddl = ddl;
            this.items = items;
        }

        public String getDdl() {
            return ddl;
        }

        public List<PatchItem> getItems() {
            return items;
        }
    }

    /**
     * 改写 DDL：逐表补缺审计列。无可补列时返回原文（逐字节一致，AC2）。
     *
     * @throws BusinessException 存在解析不了的 CREATE TABLE 时（降级阻断，维持原保守行为）
     */
    public static PatchResult patch(String ddlText) {
        Matcher counter = CREATE_TABLE_DECLARED.matcher(ddlText);
        int declared = 0;
        while (counter.find()) {
            declared++;
        }

        List<PatchItem> items = new ArrayList<>();
        StringBuilder out = new StringBuilder();
        int last = 0;
        int matched = 0;
        Matcher m = CREATE_TABLE.matcher(ddlText);
        while (m.find()) {
            matched++;
            String table = m.group(2).toLowerCase();
            String body = m.group(3).toLowerCase();
            List<String> missing = new ArrayList<>();
            // 判定口径与原 lintDdlText 完全一致：反引号包裹或裸词+空格
            for (String need : GenAuditColumns.COLS) {
                if (!body.contains("`" + need + "`") && !body.contains(need + " ")) {
                    missing.add(need);
                }
            }
            items.add(new PatchItem(table, missing));

            if (missing.isEmpty()) {
                continue; // 本段原文照抄
            }
            boolean hasId = !missing.contains("id");
            // id 注入策略（BaseEntity 的 id 是 @TableId(ASSIGN_ID)：应用层雪花生成，不依赖数据库自增）：
            // - 表已有自增列 = 有自己的自增业务主键（如 wc_id AUTO_INCREMENT）。MySQL 一表只允许
            //   一个 AUTO_INCREMENT（1075）→ 注入「非自增可空 id」，业务主键原样保留
            // - 表已有显式 PRIMARY KEY（非自增，如 PRIMARY KEY(code)）→ 同样注入非自增可空 id，
            //   不追加 PK 子句（MySQL 不允许两个 PRIMARY KEY，1068）
            // - 表既无自增列也无主键 → 注入自增 id + PRIMARY KEY（原行为）
            boolean hasAutoInc = body.contains("auto_increment");
            boolean hasExplicitPk = body.contains("primary key");
            boolean injectAutoId = !hasId && !hasAutoInc && !hasExplicitPk;

            // 1) 尾部插入：非 id 缺列 + （可注入自增 id 时的）PRIMARY KEY 子句，紧贴最后一个非空白字符之后
            //    （body 捕获组含 `)` 前的缩进空白，直接用 end(3) 会插成 ")\n  ,"）
            List<String> tailDefs = new ArrayList<>();
            for (String col : missing) {
                if (!"id".equals(col)) {
                    tailDefs.add(GenAuditColumns.columnDef(col));
                }
            }
            if (injectAutoId) {
                tailDefs.add("PRIMARY KEY (`id`)");
            }
            String tailBlock = "";
            int tailAt = m.end(3);
            if (!tailDefs.isEmpty()) {
                int lastNonWs = lastNonWsIndex(ddlText, m.start(3), m.end(3));
                char lastChar = lastNonWs >= 0 ? ddlText.charAt(lastNonWs) : '\0';
                tailBlock = (lastChar == ',' ? "\n  " : ",\n  ") + String.join(",\n  ", tailDefs);
                tailAt = lastNonWs >= 0 ? lastNonWs + 1 : tailAt;
            }

            // 2) 头部插入：缺 id 时把 id 列定义插到第一个列定义前（已有自增列/主键时用非自增定义）
            String headBlock = hasId ? "" : (injectAutoId ? GenAuditColumns.columnDef("id") : GenAuditColumns.idDefPlain()) + ",\n  ";
            int headAt = hasId ? -1 : firstNonWsIndex(ddlText, m.start(3), m.end(3));

            // 组装：原文 [last, headAt) + headBlock + 原文 [headAt, tailAt) + tailBlock（尾部原缩进留给后续原文）
            if (headAt >= 0) {
                out.append(ddlText, last, headAt).append(headBlock).append(ddlText, headAt, tailAt);
            } else {
                out.append(ddlText, last, tailAt);
            }
            out.append(tailBlock);
            last = tailAt;
        }
        if (declared != matched) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "有 " + (declared - matched) + " 条 CREATE TABLE 无法解析（需以 ENGINE 结尾的标准书写），"
                            + "为避免改错 SQL 已阻断：请修正书写后重试，或改用 AI 模式");
        }
        out.append(ddlText.substring(last));
        return new PatchResult(out.toString(), items);
    }

    private static int lastNonWsIndex(String s, int from, int to) {
        for (int i = to - 1; i >= from; i--) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int firstNonWsIndex(String s, int from, int to) {
        for (int i = from; i < to; i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}

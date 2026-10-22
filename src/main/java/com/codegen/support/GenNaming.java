package com.codegen.support;

import java.util.Locale;

/**
 * 跨系统命名规则：权限点前缀、字典编码。
 *
 * <p>为什么单独一个类：这两条规则各出现过 3 份实现，而且已经漂过一次——
 * 菜单服务做了 {@code toLowerCase}，模板模型没做，自检服务又做了一次。
 * 系统编码都是小写时看不出问题，一旦有人手工往库里写了 {@code MKT2}，
 * 生成的页面权限码是 {@code MKT2_x:add}，菜单里写的却是 {@code mkt2_x:add}，
 * 结果就是**按钮全部点了没反应**，而且四份代码看上去都「对」。
 *
 * <p>现在三处调用点（GenMenuServiceImpl / GenTemplateModel / GenSelfCheckServiceImpl）
 * 都只调这里，规则只有一份，改一次就全改。
 */
public final class GenNaming {

    private GenNaming() {
    }

    /**
     * 权限点前缀：{@code 系统编码_表名}，未绑定系统时就是表名。
     *
     * <p>不加前缀会怎样：两个系统都有 {@code sys_user} 时，
     * 权限点 {@code sys_user:add} 重名。角色授权时勾了 A 系统的用户新增，
     * B 系统的按钮也会亮——权限串了但系统不报错。
     */
    public static String permsPrefix(String projectCode, String tableName) {
        String table = tableName == null ? "" : tableName.trim();
        String scope = normalize(projectCode);
        return scope.isEmpty() ? table : scope + "_" + table;
    }

    /**
     * 字典编码：{@code 系统编码_表名_列名}。
     *
     * <p>同样的理由：{@code sys_user.status} 这种组合几乎必然重复，
     * 不带前缀时两个系统的 status 落到同一条字典上，
     * 后导入的会把前一个的选项覆盖掉，且不报错——只是下拉框里冒出别的系统的选项。
     */
    public static String dictCode(String projectCode, String tableName, String columnName) {
        String t = tableName == null ? "" : tableName.trim().toLowerCase(Locale.ROOT);
        String c = columnName == null ? "" : columnName.trim().toLowerCase(Locale.ROOT);
        String body = (t + "_" + c).replaceAll("[^a-z0-9_]", "_");
        String scope = normalize(projectCode);
        return scope.isEmpty() ? body : scope + "_" + body;
    }

    /**
     * 系统编码归一：去空白、转小写、非 [a-z0-9_] 一律换成下划线。
     *
     * <p>不做合法性校验——那是 SysProjectService 的职责。
     * 这里只保证「无论如何都能拼出一个合法标识符」，因为这段字符串会进
     * 权限码、字典编码、SQL 与前端 JS，拼出空格或中文就是运行期炸。
     */
    private static String normalize(String projectCode) {
        if (projectCode == null) {
            return "";
        }
        return projectCode.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }
}

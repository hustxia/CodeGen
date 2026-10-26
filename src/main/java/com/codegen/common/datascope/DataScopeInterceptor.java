package com.codegen.common.datascope;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import com.codegen.common.context.UserContext;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据权限拦截器：给带了数据权限标记的表，在 SELECT/UPDATE/DELETE 时自动追加过滤条件。
 *
 * <p>规则来自生成期配置（字段的 dataScope）：
 * <ul>
 *   <li>{@code self} —— 该列存的是「归属人 ID」，只允许看自己的数据</li>
 *   <li>{@code dept} —— 该列存的是「归属部门 ID」，只允许看本部门的数据</li>
 * </ul>
 *
 * <p>为什么在拦截器里做而不是生成 Mapper SQL 片段：条件必须依赖登录态，
 * 生成期拿不到用户，只能在运行期拼。规则用静态注册表而不是查库，
 * 是为了避免「拦截器里查库又触发拦截器」的死循环。
 */
public class DataScopeInterceptor implements InnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DataScopeInterceptor.class);

    /**
     * tableName -> 配置。key 用不带库名的表名，避免 schema 变化导致失配。
     */
    private static final Map<String, DataScope> SCOPES = new ConcurrentHashMap<>();

    public record DataScope(String column, String scope) {
    }

    /**
     * 注册某张表的数据权限列。同表重复注册以最后一次为准。
     */
    public static void register(String tableName, String column, String scope) {
        if (tableName == null || column == null || scope == null) {
            return;
        }
        SCOPES.put(tableName.toLowerCase(), new DataScope(column, scope.toLowerCase()));
    }

    public static void clearRegistry() {
        SCOPES.clear();
    }

    @Override
    public void beforeQuery(org.apache.ibatis.executor.Executor executor,
                            org.apache.ibatis.mapping.MappedStatement ms,
                            Object parameter, org.apache.ibatis.session.RowBounds rowBounds,
                            org.apache.ibatis.session.ResultHandler resultHandler,
                            org.apache.ibatis.mapping.BoundSql boundSql) {
        rewrite(boundSql);
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        rewrite(sh.getBoundSql());
    }

    private void rewrite(org.apache.ibatis.mapping.BoundSql boundSql) {
        if (SCOPES.isEmpty()) {
            return;
        }
        String scope = UserContext.getDataScope();
        if ("all".equals(scope)) {
            return;
        }
        Long userId = UserContext.getUserId();
        if (userId == null) {
            // 无登录态（如定时任务）不做过滤，避免静默查空
            return;
        }
        String sql = boundSql.getSql();
        if (sql == null || sql.isBlank()) {
            return;
        }
        try {
            net.sf.jsqlparser.statement.Statement stmt = CCJSqlParserUtil.parse(sql);
            boolean changed = false;
            if (stmt instanceof Select select && select.getSelectBody() instanceof PlainSelect ps) {
                changed = appendCondition(ps, ps.getFromItem() instanceof Table t ? t : null, ps);
            } else if (stmt instanceof net.sf.jsqlparser.statement.update.Update update) {
                changed = appendConditionForUpdate(update);
            } else if (stmt instanceof net.sf.jsqlparser.statement.delete.Delete delete) {
                changed = appendConditionForDelete(delete);
            }
            if (changed) {
                PluginUtils.mpBoundSql(boundSql).sql(stmt.toString());
            }
        } catch (Exception e) {
            // 解析失败不能吞掉业务：原样放行并告警，由 DBA 从日志发现规则未生效
            log.warn("数据权限改写失败，已放行原 SQL：{}", e.getMessage());
        }
    }

    private boolean appendConditionForUpdate(net.sf.jsqlparser.statement.update.Update update) {
        Table table = update.getTable();
        DataScope ds = match(table);
        if (ds == null) {
            return false;
        }
        Expression cond = buildCondition(table, ds);
        update.setWhere(cond == null ? update.getWhere() : and(update.getWhere(), cond));
        return cond != null;
    }

    private boolean appendConditionForDelete(net.sf.jsqlparser.statement.delete.Delete delete) {
        Table table = delete.getTable();
        DataScope ds = match(table);
        if (ds == null) {
            return false;
        }
        Expression cond = buildCondition(table, ds);
        delete.setWhere(cond == null ? delete.getWhere() : and(delete.getWhere(), cond));
        return cond != null;
    }

    private boolean appendCondition(PlainSelect ps, Table table, PlainSelect select) {
        if (table == null) {
            return false;
        }
        DataScope ds = match(table);
        if (ds == null) {
            return false;
        }
        Expression cond = buildCondition(table, ds);
        if (cond == null) {
            return false;
        }
        select.setWhere(and(select.getWhere(), cond));
        return true;
    }

    private DataScope match(Table table) {
        if (table == null || table.getName() == null) {
            return null;
        }
        String name = table.getName().replace("`", "").toLowerCase();
        DataScope ds = SCOPES.get(name);
        // 多表 join 时只处理主表，避免给别名表加错列
        return ds;
    }

    private Expression buildCondition(Table table, DataScope ds) {
        String valueColumn;
        Long value;
        if ("dept".equals(ds.scope())) {
            value = UserContext.getDeptId();
            valueColumn = ds.column();
            if (value == null) {
                // 没有部门信息时按「本人」降级，宁严不松
                return selfCondition(table, ds);
            }
        } else if ("self".equals(ds.scope())) {
            return selfCondition(table, ds);
        } else {
            return null;
        }
        EqualsTo eq = new EqualsTo();
        eq.setLeftExpression(new Column(table, valueColumn));
        eq.setRightExpression(new LongValue(value));
        return eq;
    }

    private Expression selfCondition(Table table, DataScope ds) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return null;
        }
        EqualsTo eq = new EqualsTo();
        eq.setLeftExpression(new Column(table, ds.column()));
        eq.setRightExpression(new LongValue(userId));
        return eq;
    }

    private Expression and(Expression where, Expression cond) {
        return where == null ? cond : new AndExpression(where, cond);
    }
}

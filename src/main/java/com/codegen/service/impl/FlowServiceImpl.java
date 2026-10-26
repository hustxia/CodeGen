package com.codegen.service.impl;

import com.codegen.common.context.UserContext;
import com.codegen.common.exception.BusinessException;
import com.codegen.service.FlowService;
import com.codegen.common.flow.FlowRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 审批流实现。
 *
 * <p>安全要点：{@code table_name} / {@code status_col} 来自流程定义（管理员配置），
 * 但仍然必须走白名单正则 —— 否则流程定义被改成 {@code x; DROP TABLE ...} 就是一条 SQL 注入。
 */
@Service
public class FlowServiceImpl implements FlowService {

    private static final Logger log = LoggerFactory.getLogger(FlowServiceImpl.class);

    /** 表名/列名白名单：只允许标识符，绝不接受括号、分号等 */
    private static final Pattern IDENT = Pattern.compile("^[A-Za-z0-9_]{1,64}$");

    /** 草稿态：提交前的默认状态 */
    private static final String DRAFT = "0";

    /** 待审批 */
    private static final String PENDING = "1";

    private static final String NODE_KEY = "nodes";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(String flowCode, Long bizId, String comment) {
        Def def = loadDef(flowCode);
        String current = currentStatus(def, bizId);
        if (!DRAFT.equals(current)) {
            throw new BusinessException(400, "只有草稿状态可以提交，当前状态：" + current);
        }
        String to = resolveSubmitTarget(def);
        move(def, bizId, current, to, "SUBMIT", comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(String flowCode, Long bizId, String comment) {
        Def def = loadDef(flowCode);
        String current = currentStatus(def, bizId);
        Map<String, Object> node = findNode(def, current);
        if (node == null) {
            throw new BusinessException(400, "当前状态没有配置审批节点：" + current);
        }
        checkRole(node, "approve");
        String to = str(node.get("to"));
        if (!StringUtils.hasText(to)) {
            throw new BusinessException(400, "流程节点缺少目标状态，请检查流程定义");
        }
        move(def, bizId, current, to, "APPROVE", comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(String flowCode, Long bizId, String comment) {
        Def def = loadDef(flowCode);
        String current = currentStatus(def, bizId);
        Map<String, Object> node = findNode(def, current);
        if (node == null) {
            throw new BusinessException(400, "当前状态没有配置审批节点：" + current);
        }
        checkRole(node, "reject");
        // 驳回目标：节点里显式写了 rejectTo 就用它，否则回草稿
        String to = str(node.get("rejectTo"));
        if (!StringUtils.hasText(to)) {
            to = DRAFT;
        }
        move(def, bizId, current, to, "REJECT", comment);
    }

    @Override
    public List<FlowRecord> records(String flowCode, Long bizId) {
        if (!StringUtils.hasText(flowCode) || bizId == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                "SELECT id, flow_code, biz_table, biz_id, from_status, to_status, action, comment, "
                        + "oper_user_id, oper_name, oper_time FROM sys_flow_task "
                        + "WHERE flow_code = ? AND biz_id = ? ORDER BY oper_time, id",
                (rs, i) -> {
                    FlowRecord vo = new FlowRecord();
                    vo.setId(rs.getLong("id"));
                    vo.setFlowCode(rs.getString("flow_code"));
                    vo.setBizTable(rs.getString("biz_table"));
                    vo.setBizId(rs.getLong("biz_id"));
                    vo.setFromStatus(rs.getString("from_status"));
                    vo.setToStatus(rs.getString("to_status"));
                    vo.setAction(rs.getString("action"));
                    vo.setComment(rs.getString("comment"));
                    long uid = rs.getLong("oper_user_id");
                    vo.setOperUserId(rs.wasNull() ? null : uid);
                    vo.setOperName(rs.getString("oper_name"));
                    if (rs.getTimestamp("oper_time") != null) {
                        vo.setOperTime(rs.getTimestamp("oper_time").toLocalDateTime());
                    }
                    return vo;
                }, flowCode, bizId);
    }

    @Override
    public Map<String, Object> state(String flowCode, Long bizId) {
        Def def = loadDef(flowCode);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("flowCode", def.code);
        data.put("flowName", def.name);
        String current = bizId == null ? DRAFT : currentStatus(def, bizId);
        data.put("status", current);
        data.put("nodes", def.nodes);
        List<String> actions = new ArrayList<>();
        if (DRAFT.equals(current)) {
            actions.add("SUBMIT");
        } else {
            Map<String, Object> node = findNode(def, current);
            if (node != null && StringUtils.hasText(str(node.get("to")))) {
                // 角色不匹配就不给按钮，避免用户点了才被拒
                if (roleMatched(node)) {
                    actions.add("APPROVE");
                    actions.add("REJECT");
                }
            }
        }
        data.put("actions", actions);
        return data;
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 执行状态流转：先更新业务表，再写流水。两者同一事务。
     */
    private void move(Def def, Long bizId, String from, String to, String action, String comment) {
        String sql = "UPDATE " + def.tableName + " SET " + def.statusCol + " = ? WHERE id = ?";
        int n = jdbcTemplate.update(sql, to, bizId);
        if (n == 0) {
            throw new BusinessException(404, "单据不存在：" + bizId);
        }
        String operName = UserContext.getUsername();
        jdbcTemplate.update(
                "INSERT INTO sys_flow_task(flow_code, biz_table, biz_id, from_status, to_status, action, "
                        + "comment, oper_user_id, oper_name) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)",
                def.code, def.tableName, bizId, from, to, action, comment,
                UserContext.getUserId(), StringUtils.hasText(operName) ? operName : "system");
        log.info("审批流转 {}({}) {} → {} by {}", def.code, bizId, from, to, operName);
    }

    /**
     * 提交目标状态：取「from=草稿」节点的 to，没配就用 1（待审批）
     */
    private String resolveSubmitTarget(Def def) {
        Map<String, Object> node = findNode(def, DRAFT);
        if (node != null && StringUtils.hasText(str(node.get("to")))) {
            return str(node.get("to"));
        }
        return PENDING;
    }

    /**
     * 在当前业务流程上找到 from = 当前状态 的节点
     */
    private Map<String, Object> findNode(Def def, String from) {
        for (Map<String, Object> node : def.nodes) {
            if (from.equals(str(node.get("from")))) {
                return node;
            }
        }
        return null;
    }

    /**
     * 角色校验：节点未配 roleCode 表示不限制；
     * 超管永远放行，否则要求当前用户角色里含 roleCode。
     */
    private void checkRole(Map<String, Object> node, String action) {
        String need = str(node.get("roleCode"));
        if (!StringUtils.hasText(need)) {
            return;
        }
        if (UserContext.isSuper()) {
            return;
        }
        List<String> roles = UserContext.getRoles();
        for (String r : roles) {
            if (need.equalsIgnoreCase(r)) {
                return;
            }
        }
        throw new BusinessException(403, "无权执行该操作，需要角色：" + need);
    }

    private boolean roleMatched(Map<String, Object> node) {
        String need = str(node.get("roleCode"));
        if (!StringUtils.hasText(need) || UserContext.isSuper()) {
            return true;
        }
        for (String r : UserContext.getRoles()) {
            if (need.equalsIgnoreCase(r)) {
                return true;
            }
        }
        return false;
    }

    private String currentStatus(Def def, Long bizId) {
        List<String> list = jdbcTemplate.queryForList(
                "SELECT " + def.statusCol + " FROM " + def.tableName + " WHERE id = ?", String.class, bizId);
        if (list.isEmpty()) {
            throw new BusinessException(404, "单据不存在：" + bizId);
        }
        return list.get(0) == null ? DRAFT : list.get(0);
    }

    /**
     * 读流程定义，并校验表名/列名合法
     */
    private Def loadDef(String flowCode) {
        if (!StringUtils.hasText(flowCode)) {
            throw new BusinessException(400, "流程编码不能为空");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT flow_code, flow_name, table_name, status_col, nodes, enabled "
                        + "FROM sys_flow_def WHERE flow_code = ? AND deleted = 0", flowCode.trim());
        if (rows.isEmpty()) {
            throw new BusinessException(404, "流程不存在：" + flowCode);
        }
        Map<String, Object> r = rows.get(0);
        if (r.get("enabled") != null && ((Number) r.get("enabled")).intValue() != 1) {
            throw new BusinessException(400, "流程已停用：" + flowCode);
        }
        Def def = new Def();
        def.code = str(r.get("flow_code"));
        def.name = str(r.get("flow_name"));
        def.tableName = str(r.get("table_name"));
        def.statusCol = str(r.get("status_col"));
        if (!IDENT.matcher(def.tableName).matches()) {
            throw new BusinessException(400, "流程绑定的表名非法：" + def.tableName);
        }
        if (!IDENT.matcher(def.statusCol).matches()) {
            throw new BusinessException(400, "流程绑定的状态列非法：" + def.statusCol);
        }
        def.nodes = parseNodes(str(r.get("nodes")));
        return def;
    }

    private List<Map<String, Object>> parseNodes(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<>() {
            });
            return list == null ? List.of() : list;
        } catch (Exception e) {
            throw new BusinessException(400, "流程节点 JSON 解析失败：" + e.getMessage());
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    /**
     * 流程定义的内存视图
     */
    private static final class Def {
        String code;
        String name;
        String tableName;
        String statusCol;
        List<Map<String, Object>> nodes = List.of();
    }
}

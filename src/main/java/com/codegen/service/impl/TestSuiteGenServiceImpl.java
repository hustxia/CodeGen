package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codegen.common.exception.BusinessException;
import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import com.codegen.entity.TestCase;
import com.codegen.entity.TestSuite;
import com.codegen.mapper.GenTableColumnMapper;
import com.codegen.mapper.GenTableMapper;
import com.codegen.mapper.TestCaseMapper;
import com.codegen.mapper.TestSuiteMapper;
import com.codegen.service.TestSuiteGenService;
import com.codegen.support.TestJson;
import com.codegen.vo.TestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V9.2 T3 按表生成冒烟用例。
 *
 * <p>生成依据全部来自 {@code gen_table} / {@code gen_table_column}：
 * 接口路径由「模块名 + 开关」推导，请求体由「表单列 + Java 类型」推导，断言固定为
 * 「HTTP 200 + code=200（+ 分页 records 存在）」。
 *
 * <p>三条刻意的取舍：
 * <ol>
 *   <li><b>不生成登录用例</b>：鉴权属于环境级配置，放「环境公共请求头」，
 *       否则每个套件都要复制一份账号密码。</li>
 *   <li><b>必填校验只断言 code != 200</b>：@Valid 失败可能返回 HTTP 400 或 200，
 *       锁死状态码会让生成的用例在两种全局异常配置下必有一条是错的。</li>
 *   <li><b>主键来自分页结果</b>：新增接口返回 Result&lt;Void&gt; 拿不到 id，
 *       所以顺序固定为「新增 → 分页（提取首行 id）→ 详情 → 修改 → 删除」。</li>
 * </ol>
 */
@Service
public class TestSuiteGenServiceImpl implements TestSuiteGenService {

    /** 审计列不参与请求体构造 */
    private static final Set<String> AUDIT_COLUMNS =
            Set.of("create_time", "update_time", "create_by", "update_by", "deleted");

    private static final String ASSERTS_OK =
            "[{\"type\":\"status\",\"expect\":200},{\"type\":\"jsonpath\",\"path\":\"$.code\",\"op\":\"eq\",\"expect\":200}]";
    private static final String ASSERTS_PAGE =
            "[{\"type\":\"status\",\"expect\":200},{\"type\":\"jsonpath\",\"path\":\"$.code\",\"op\":\"eq\",\"expect\":200},"
                    + "{\"type\":\"jsonpath\",\"path\":\"$.data.records\",\"op\":\"exists\",\"expect\":true}]";
    private static final String ASSERTS_REQUIRED =
            "[{\"type\":\"jsonpath\",\"path\":\"$.code\",\"op\":\"ne\",\"expect\":200}]";

    @Autowired
    private GenTableMapper genTableMapper;

    @Autowired
    private GenTableColumnMapper columnMapper;

    @Autowired
    private TestSuiteMapper suiteMapper;

    @Autowired
    private TestCaseMapper caseMapper;

    /** 一条待落库的用例定义 */
    private record Spec(String name, String method, String path, String bodyJson,
                        String extractJson, String assertsJson, int orderNo) {
    }

    @Override
    public TestVO.GenResult generate(Long tableId) {
        GenTable table = tableId == null ? null : genTableMapper.selectById(tableId);
        if (table == null) {
            throw new BusinessException(400, "表配置不存在");
        }
        List<GenTableColumn> columns = columnMapper.selectList(new LambdaQueryWrapper<GenTableColumn>()
                .eq(GenTableColumn::getTableId, tableId)
                .orderByAsc(GenTableColumn::getSort));

        TestSuite suite = findOrCreateSuite(table);
        List<String> messages = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int skipped = 0;

        for (Spec spec : buildSpecs(table, columns)) {
            TestCase existing = caseMapper.selectOne(new LambdaQueryWrapper<TestCase>()
                    .eq(TestCase::getSuiteId, suite.getId())
                    .eq(TestCase::getName, spec.name())
                    .last("limit 1"));
            String fp = fingerprint(spec.method(), spec.path(), spec.bodyJson(), spec.assertsJson());
            if (existing == null) {
                TestCase entity = new TestCase();
                entity.setSuiteId(suite.getId());
                entity.setName(spec.name());
                entity.setSource("generated");
                entity.setMethod(spec.method());
                entity.setPath(spec.path());
                entity.setBodyJson(spec.bodyJson());
                entity.setExtractJson(spec.extractJson());
                entity.setAssertsJson(spec.assertsJson());
                entity.setOrderNo(spec.orderNo());
                entity.setEnabled(1);
                entity.setFingerprint(fp);
                caseMapper.insert(entity);
                created++;
                continue;
            }
            if (!"generated".equals(existing.getSource())) {
                skipped++;
                messages.add(spec.name() + "：已存在同名手工用例，保留不动");
                continue;
            }
            String current = fingerprint(existing.getMethod(), existing.getPath(),
                    existing.getBodyJson(), existing.getAssertsJson());
            if (!current.equals(existing.getFingerprint())) {
                skipped++;
                messages.add(spec.name() + "：已被手工修改，保留不动");
                continue;
            }
            TestCase patch = new TestCase();
            patch.setId(existing.getId());
            patch.setMethod(spec.method());
            patch.setPath(spec.path());
            patch.setBodyJson(spec.bodyJson());
            patch.setExtractJson(spec.extractJson());
            patch.setAssertsJson(spec.assertsJson());
            patch.setOrderNo(spec.orderNo());
            patch.setFingerprint(fp);
            caseMapper.updateById(patch);
            updated++;
        }

        if (created == 0 && updated == 0 && skipped == 0) {
            messages.add("该表未开启任何 CRUD 开关，未生成用例");
        }
        messages.add("鉴权请在环境公共请求头配置 Authorization，用例本身不带 token");
        return new TestVO.GenResult(suite.getId(), created, updated, skipped, messages);
    }

    private TestSuite findOrCreateSuite(GenTable table) {
        TestSuite suite = suiteMapper.selectOne(new LambdaQueryWrapper<TestSuite>()
                .eq(TestSuite::getTableId, table.getId())
                .eq(TestSuite::getSource, "generated")
                .last("limit 1"));
        if (suite != null) {
            return suite;
        }
        suite = new TestSuite();
        suite.setProjectId(table.getProjectId());
        suite.setTableId(table.getId());
        suite.setName(StringUtils.hasText(table.getTableComment()) ? table.getTableComment() : table.getTableName());
        suite.setType("api");
        suite.setSource("generated");
        suite.setModuleName(table.getModuleName());
        suite.setEnabled(1);
        suiteMapper.insert(suite);
        return suite;
    }

    private static List<Spec> buildSpecs(GenTable table, List<GenTableColumn> columns) {
        List<Spec> specs = new ArrayList<>();
        String module = StringUtils.hasText(table.getModuleName()) ? table.getModuleName() : table.getTableName();
        String base = "/" + module;
        Map<String, Object> addBody = buildAddBody(columns);
        int order = 1;

        if (on(table.getGenAddApi())) {
            specs.add(new Spec("新增", "POST", base, TestJson.write(addBody), null, ASSERTS_OK, order++));
        }
        if (on(table.getGenPageApi())) {
            specs.add(new Spec("分页查询", "POST", base + "/page",
                    "{\"pageNum\":1,\"pageSize\":10}",
                    "{\"id\":\"$.data.records[0].id\"}", ASSERTS_PAGE, order++));
        }
        if (on(table.getGenDetailApi()) && on(table.getGenPageApi())) {
            specs.add(new Spec("详情", "GET", base + "/${case.id}", null, null, ASSERTS_OK, order++));
        }
        if (on(table.getGenEditApi()) && on(table.getGenPageApi())) {
            Map<String, Object> editBody = new LinkedHashMap<>(addBody);
            editBody.put("id", "${case.id}");
            specs.add(new Spec("修改", "PUT", base, TestJson.write(editBody), null, ASSERTS_OK, order++));
        }
        if (on(table.getGenDeleteApi()) && on(table.getGenPageApi())) {
            specs.add(new Spec("删除", "DELETE", base + "/${case.id}", null, null, ASSERTS_OK, order++));
        }
        GenTableColumn required = firstRequiredFormColumn(columns);
        if (required != null && on(table.getGenAddApi())) {
            Map<String, Object> badBody = new LinkedHashMap<>(addBody);
            badBody.remove(required.getJavaField());
            specs.add(new Spec("必填校验-" + required.getColumnName(), "POST", base,
                    TestJson.write(badBody), null, ASSERTS_REQUIRED, order++));
        }
        if (StringUtils.hasText(table.getTreeParentColumn())) {
            specs.add(new Spec("树查询", "GET", base + "/tree", null, null, ASSERTS_OK, order++));
        }
        return specs;
    }

    /** 请求体：只取表单列，按 Java 类型造合法样本 */
    private static Map<String, Object> buildAddBody(List<GenTableColumn> columns) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (GenTableColumn c : columns) {
            if (Integer.valueOf(1).equals(c.getIsPk())) {
                continue;
            }
            if (AUDIT_COLUMNS.contains(c.getColumnName())) {
                continue;
            }
            if (!Integer.valueOf(1).equals(c.getIsForm())) {
                continue;
            }
            body.put(c.getJavaField(), sample(c));
        }
        return body;
    }

    private static Object sample(GenTableColumn c) {
        String type = c.getJavaType() == null ? "String" : c.getJavaType();
        return switch (type) {
            case "Integer", "Long", "Short", "Byte" -> 1;
            case "BigDecimal", "Double", "Float" -> 1.5;
            case "Boolean" -> true;
            case "LocalDate" -> "2026-01-01";
            case "LocalDateTime" -> "2026-01-01 00:00:00";
            default -> {
                if (StringUtils.hasText(c.getDictType())) {
                    yield "1";
                }
                int max = maxLen(c.getValidateRule());
                yield max > 0 && max < 16 ? "测试" : "smoke";
            }
        };
    }

    private static int maxLen(String rule) {
        if (!StringUtils.hasText(rule)) {
            return 0;
        }
        for (String part : rule.split(",")) {
            String p = part.trim();
            if (p.startsWith("maxlen=")) {
                try {
                    return Integer.parseInt(p.substring("maxlen=".length()).trim());
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static GenTableColumn firstRequiredFormColumn(List<GenTableColumn> columns) {
        for (GenTableColumn c : columns) {
            if (Integer.valueOf(1).equals(c.getIsRequired())
                    && Integer.valueOf(1).equals(c.getIsForm())
                    && !Integer.valueOf(1).equals(c.getIsPk())
                    && !AUDIT_COLUMNS.contains(c.getColumnName())) {
                return c;
            }
        }
        return null;
    }

    private static boolean on(Integer flag) {
        return flag == null || Integer.valueOf(1).equals(flag);
    }

    private static String fingerprint(String method, String path, String body, String asserts) {
        String raw = method + "|" + path + "|" + body + "|" + asserts;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }
}

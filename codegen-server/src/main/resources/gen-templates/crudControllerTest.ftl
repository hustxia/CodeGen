package ${pkg};

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ${comment} CRUD 集成测试（代码生成器 V6.7 自动生成，重新生成会覆盖本文件）。
 *
 * <p>H2 内存库 + 真实鉴权链（基类直插 sys_session 造登录态），只覆盖 generated 端点：
 * page → add → detail → update → delete → 未登录 401。业务规则断言请另建测试类。
 */
class ${className}ControllerTest extends LoginStateTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 新增一条最小合法记录，返回自增 id（add 接口不回 id，从库里取最后一条） */
    private Long addRow() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/${moduleName}<#if hasChild>/save-with-children</#if>")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM ${tableName}", Long.class);
    }

    /** 业务字段 JSON（平台按列模型生成；唯一列值运行时随机，避免测试互撞唯一键） */
    private String fieldsJson() {
        return ${testFieldsJsonExpr};
    }

    private String saveBody() {
        return fieldsJson();
    }

    /** 修改体 = id + 同一业务字段集（{@code {"id":1,...}}：fieldsJson 去掉左花括号接 id） */
    private String updateBody(long id) {
        return "{\"id\":" + id + "," + fieldsJson().substring(1);
    }

    private String suffix() {
        return "it" + System.nanoTime();
    }

    @Test
    void pageReturnsRecords() throws Exception {
        addRow();
        mockMvc.perform(MockMvcRequestBuilders.post("/${moduleName}/page")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNum\":1,\"pageSize\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    <#if ctrlDetailApi>
    @Test
    void addThenDetailReturnsSameId() throws Exception {
        Long id = addRow();
        // VO 的 Long id 走 LongIdJacksonConfig 序列化为字符串（JS 精度保护，坑 57 同源）
        mockMvc.perform(MockMvcRequestBuilders.get("/${moduleName}/" + id)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(String.valueOf(id)));
    }
    </#if>

    <#if ctrlEditApi || hasChild>
    @Test
    void updateReturnsOk() throws Exception {
        Long id = addRow();
        mockMvc.perform(MockMvcRequestBuilders.<#if hasChild>post("/${moduleName}/save-with-children")<#else>put("/${moduleName}")</#if>
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
    </#if>

    <#if ctrlDeleteApi>
    @Test
    void deleteThenDetailIsEmpty() throws Exception {
        Long id = addRow();
        mockMvc.perform(MockMvcRequestBuilders.delete("/${moduleName}/" + id)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        // 逻辑删后详情返回 200 + data=null（Result.ok(null)），而非 404
        mockMvc.perform(MockMvcRequestBuilders.get("/${moduleName}/" + id)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
    </#if>

    @Test
    void anonymousRequestIsRejected() throws Exception {
        // AuthContextFilter 未命中 token 时直接 SC_UNAUTHORIZED（HTTP 401，非 200+code）
        mockMvc.perform(MockMvcRequestBuilders.post("/${moduleName}/page")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNum\":1,\"pageSize\":10}"))
                .andExpect(status().isUnauthorized());
    }
}

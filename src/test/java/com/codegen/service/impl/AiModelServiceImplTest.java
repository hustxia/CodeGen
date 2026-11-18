package com.codegen.service.impl;

import com.codegen.common.exception.BusinessException;
import com.codegen.vo.GenPipelineAiModulesVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiModelServiceImplTest {

    private final AiModelServiceImpl service = new AiModelServiceImpl();

    @Test
    void parseModules_readsFunctions() {
        String json = """
                {"modules":[{"name":"用户管理","desc":"账号、角色与权限","functions":["账号管理","角色管理","权限管理"]}]}
                """;

        GenPipelineAiModulesVO result = parse(json);

        assertEquals("用户管理", result.getModules().get(0).getName());
        assertEquals(List.of("账号管理", "角色管理", "权限管理"), functions(result));
    }

    @Test
    void parseModules_defaultsMissingFunctionsToEmptyList() {
        String json = """
                {"modules":[{"name":"用户管理","desc":"账号、角色与权限"}]}
                """;

        assertEquals(List.of(), functions(parse(json)));
    }

    @Test
    void parseModules_ignoresNonArrayFunctions() {
        String json = """
                {"modules":[{"name":"用户管理","functions":"账号管理"}]}
                """;

        assertEquals(List.of(), functions(parse(json)));
    }

    @Test
    void parseModules_filtersBlankAndNonTextualFunctions() {
        String json = """
                {"modules":[{"name":"用户管理","functions":[" 账号管理 ","",null,123,{"k":"v"},"角色管理"]}]}
                """;

        assertEquals(List.of("账号管理", "角色管理"), functions(parse(json)));
    }

    @Test
    void parseModules_rejectsBrokenJson() {
        assertThrows(BusinessException.class, () -> parse("not a json"));
    }

    @Test
    void parseModules_rejectsEmptyModuleList() {
        assertThrows(BusinessException.class, () -> parse("{\"modules\":[]}"));
    }

    // ============ V9.4：推荐模块与输入系统不相关 ============

    /** 旧 prompt 把「用户管理/账号管理/角色管理/权限管理」写成具体示例，小模型会照抄 */
    @Test
    void modulesPrompt_hasNoConcreteDomainExampleAndAnchorsToDomain() throws Exception {
        Field field = AiModelServiceImpl.class.getDeclaredField("MODULES_SYSTEM_PROMPT");
        field.setAccessible(true);
        String prompt = (String) field.get(null);

        assertFalse(prompt.contains("\"name\":\"用户管理\""), "JSON 示例不应带可直接照抄的具体领域词");
        assertFalse(prompt.contains("\"账号管理\",\"角色管理\",\"权限管理\""));
        assertTrue(prompt.contains("<业务对象>管理"), "示例值必须是占位符");
        assertTrue(prompt.contains("该系统自己的业务名词"));
        assertTrue(prompt.contains("严禁输出与本系统无关的通用后台模块"));
        assertTrue(prompt.contains("统计报表"), "通用支撑域应作为反例列出而非必选项");
        assertFalse(prompt.contains("8-12 个"), "不应再强制 8-12 个模块撑数");
    }

    @Test
    void buildModulesUserContent_carriesNameAndDescription() {
        String content = AiModelServiceImpl.buildModulesUserContent(
                "图书馆借阅系统", "管理图书入库、读者借阅、归还与逾期处理");

        assertTrue(content.contains("图书馆借阅系统"));
        assertTrue(content.contains("管理图书入库、读者借阅、归还与逾期处理"));
        assertTrue(countOccurrences(content, "图书馆借阅系统") >= 2,
                "系统名需在开头与结尾重复注入，避免被长 prompt 稀释");
        assertTrue(content.contains("与该系统无关的模块一律不要输出"));
    }

    @Test
    void buildModulesUserContent_emptyDescriptionAsksToInferFromName() {
        String content = AiModelServiceImpl.buildModulesUserContent("宠物领养系统", "");

        assertTrue(content.contains("仅根据系统名称推断该系统的业务域"));
        assertFalse(content.contains("系统描述：（空）"));
    }

    @Test
    void assessRelevance_domainSpecificModulesPassWithoutWarning() {
        String json = """
                {"modules":[
                  {"name":"图书档案","desc":"图书入库与编目","functions":["图书登记","编目入藏","馆藏查询"]},
                  {"name":"读者证管理","desc":"读者证办理与借阅额度","functions":["读者注册","借阅额度","证件挂失"]},
                  {"name":"借阅归还","desc":"借书还书与逾期","functions":["借书登记","归还确认","逾期处理"]}
                ]}
                """;

        GenPipelineAiModulesVO vo = parse(json);
        assess("图书馆借阅系统", "管理图书入库、读者借阅、归还与逾期处理", vo);

        assertFalse(vo.isRelevanceWarning());
        assertEquals("", vo.getRelevanceTip());
    }

    /** 完全套通用后台模板且与输入零关联 → 拦截，不把无关清单静默展示给用户 */
    @Test
    void assessRelevance_blocksPureGenericAdminTemplate() {
        String json = """
                {"modules":[
                  {"name":"用户管理","desc":"账号与角色","functions":["账号管理","角色管理","权限管理"]},
                  {"name":"系统配置","desc":"参数与字典","functions":["参数配置","数据字典","消息通知"]},
                  {"name":"操作日志","desc":"记录操作","functions":["登录日志","操作日志","统计报表"]}
                ]}
                """;

        GenPipelineAiModulesVO vo = parse(json);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> assess("图书馆借阅系统", "图书借阅与归还", vo));
        assertTrue(ex.getMessage().contains("不相关"));
        assertTrue(ex.getMessage().contains("图书馆借阅系统"));
    }

    /** 部分领域相关但比例偏低 → 只告警不阻断，用户仍可编辑或重新生成 */
    @Test
    void assessRelevance_lowOverlapWarnsButDoesNotBlock() {
        String json = """
                {"modules":[
                  {"name":"图书档案","desc":"图书入库","functions":["图书登记"]},
                  {"name":"库房管理","desc":"货架维护","functions":["货架编号"]},
                  {"name":"排班值守","desc":"值班安排","functions":["班次编排"]},
                  {"name":"耗材申领","desc":"办公用品","functions":["耗材登记"]}
                ]}
                """;

        GenPipelineAiModulesVO vo = parse(json);
        assess("图书馆借阅系统", "图书借阅与归还", vo);

        assertTrue(vo.isRelevanceWarning());
        assertTrue(vo.getRelevanceTip().contains("图书馆借阅系统"));
        assertEquals(4, vo.getModules().size());
    }

    @Test
    void domainTokens_stripsGenericSuffixAndKeepsBusinessWords() {
        @SuppressWarnings("unchecked")
        List<String> tokens = (List<String>) ReflectionTestUtils.invokeMethod(
                service, "domainTokens", "仓库管理系统", "管理入库、出库、库存盘点");

        assertTrue(tokens.contains("仓库"));
        assertTrue(tokens.contains("入库"));
        assertTrue(tokens.contains("库存盘点"));
        assertFalse(tokens.contains("系统"));
        assertFalse(tokens.contains("管理系统"));
    }

    private GenPipelineAiModulesVO parse(String json) {
        return ReflectionTestUtils.invokeMethod(service, "parseModules", json);
    }

    private void assess(String systemName, String desc, GenPipelineAiModulesVO vo) {
        ReflectionTestUtils.invokeMethod(service, "assessRelevance", systemName, desc, vo);
    }

    private List<String> functions(GenPipelineAiModulesVO result) {
        return result.getModules().get(0).getFunctions();
    }

    private static int countOccurrences(String text, String part) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(part, from)) >= 0) {
            count++;
            from += part.length();
        }
        return count;
    }
}

package com.codegen.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codegen.entity.GenTemplate;
import com.codegen.mapper.GenTemplateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 模板种子器：启动时把 classpath:gen-templates/*.ftl 的全部全局模板落库。
 * 幂等：对应 tpl_code 已有启用行则跳过（保护用户在线编辑）；
 * 插入前物理清理逻辑删除残留行，避免占用 uk_code_table 唯一键。
 */
@Component
public class GenTemplateSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GenTemplateSeeder.class);

    /**
     * 模板元数据：编码 / 名称 / 产物分组 / 路径模式
     *
     * <p>同步策略：remark 仍是系统种子标记（未被用户接管）的全局模板，
     * 只要 classpath 里的内容与库中不同就用新内容覆盖；用户改过的模板
     * 在保存时会被标记为「用户自定义」，这里一律不动。
     */
    private record SeedMeta(String code, String name, String group, String pathPattern) {
    }

    private static final String SEED_REMARK = "全局种子模板";

    private static final java.util.regex.Pattern SEED_REMARK_PATTERN =
            java.util.regex.Pattern.compile("^全局种子模板(?:\\s*v(\\d+))?$");

    private static final List<SeedMeta> SEEDS = List.of(
            new SeedMeta("entity", "实体类", "backend", "${backendBase}/entity/${className}.java"),
            new SeedMeta("mapper", "Mapper 接口", "backend", "${backendBase}/mapper/${className}Mapper.java"),
            new SeedMeta("service", "Service 接口", "backend", "${backendBase}/service/${className}Service.java"),
            // V4.4 模板分道：serviceImpl 按表形态拆三个（派发判据在 GenTemplateModel.serviceImplTplCode）
            new SeedMeta("serviceImplSimple", "Service 实现（单表）", "backend", "${backendBase}/service/impl/${className}ServiceImpl.java"),
            new SeedMeta("serviceImplTree", "Service 实现（树形）", "backend", "${backendBase}/service/impl/${className}ServiceImpl.java"),
            new SeedMeta("serviceImplMasterdetail", "Service 实现（主子表）", "backend", "${backendBase}/service/impl/${className}ServiceImpl.java"),
            new SeedMeta("pageDto", "分页查询 DTO", "backend", "${backendBase}/dto/${className}PageDTO.java"),
            new SeedMeta("saveDto", "保存 DTO", "backend", "${backendBase}/dto/${className}SaveDTO.java"),
            new SeedMeta("vo", "列表 VO", "backend", "${backendBase}/vo/${className}VO.java"),
            new SeedMeta("controller", "Controller", "backend", "${backendBase}/controller/${className}Controller.java"),
            new SeedMeta("apiJs", "前端 api.js", "frontend", "src/api/${moduleName}.js"),
            new SeedMeta("vuePage", "前端页面", "frontend", "src/views/${moduleName}/index.vue"),
            // V4.5 A4 打印单据：开关 gen_print 开启才渲染，独立于 index.vue 的单据版式页
            new SeedMeta("printPage", "打印单据页", "frontend", "src/views/${moduleName}/print.vue"),
            // V6.1 审批流：批次内任一表绑定 flow_code 时由 renderFlowShared 注入（待办中心 + 全局 flow api）
            new SeedMeta("flowApi", "审批流前端接口", "frontend", "src/api/flow.js"),
            new SeedMeta("flowTodo", "待办中心页", "frontend", "src/views/flow/todo.vue"),
            // V6.3 支付能力包：系统 pay_enabled 开启时由 renderPayShared 注入（收银台 + 支付记录 + 全局 pay api）
            new SeedMeta("payApi", "支付前端接口", "frontend", "src/api/pay.js"),
            new SeedMeta("payCashier", "收银台页", "frontend", "src/views/pay/cashier.vue"),
            new SeedMeta("payOrderPage", "支付记录页", "frontend", "src/views/pay/order.vue"),
            new SeedMeta("routerSnippet", "路由片段", "shared", "router-snippet.txt"),
            new SeedMeta("readme", "使用说明", "shared", "README-使用说明.txt"),
            // V6.7 产物测试出厂：test_enabled 开启时随业务表出集成测试与 H2 建表脚本。
            // 路径锚定 testJavaBase/testResBase（与 backendBase 同根推导），multi 模块下自然落 bootstrap 所在工程
            new SeedMeta("crudControllerTest", "集成测试（CRUD）", "test", "${testJavaBase}/${className}ControllerTest.java"),
            new SeedMeta("testSchemaTable", "测试库建表（H2）", "test", "${testResBase}/schema/table-${tableName}.sql"));

    @Autowired
    private GenTemplateMapper genTemplateMapper;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        int inserted = 0;
        int upgraded = 0;
        for (SeedMeta meta : SEEDS) {
            LambdaQueryWrapper<GenTemplate> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(GenTemplate::getTplCode, meta.code());
            wrapper.eq(GenTemplate::getTableId, 0L);
            GenTemplate existing = genTemplateMapper.selectOne(wrapper.last("LIMIT 1"));
            if (existing != null && !isManagedSeed(existing.getRemark())) {
                // 用户自定义过的模板不覆盖
                continue;
            }
            ClassPathResource resource = new ClassPathResource("gen-templates/" + meta.code() + ".ftl");
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (existing != null) {
                boolean same = content.equals(existing.getContent())
                        && meta.pathPattern().equals(existing.getPathPattern())
                        && SEED_REMARK.equals(existing.getRemark());
                if (same) {
                    continue;
                }
                existing.setContent(content);
                existing.setPathPattern(meta.pathPattern());
                existing.setRemark(SEED_REMARK);
                genTemplateMapper.updateById(existing);
                upgraded++;
                continue;
            }
            genTemplateMapper.purgeDeletedResidual(meta.code(), 0L);
            GenTemplate tpl = new GenTemplate();
            tpl.setTplCode(meta.code());
            tpl.setTplName(meta.name());
            tpl.setFileGroup(meta.group());
            tpl.setContent(content);
            tpl.setPathPattern(meta.pathPattern());
            tpl.setTableId(0L);
            tpl.setStatus(1);
            tpl.setRemark(SEED_REMARK);
            genTemplateMapper.insert(tpl);
            inserted++;
        }
        if (inserted > 0 || upgraded > 0) {
            log.info("GenTemplateSeeder: 回补 {} 个、同步 {} 个全局模板", inserted, upgraded);
        }
    }

    /**
     * remark 仍是系统种子标记（未被用户接管）；兼容历史的「全局种子模板 vN」写法
     */
    private boolean isManagedSeed(String remark) {
        return remark != null && SEED_REMARK_PATTERN.matcher(remark.trim()).matches();
    }
}

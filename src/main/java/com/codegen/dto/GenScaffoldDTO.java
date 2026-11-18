package com.codegen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * 一键建工程入参。
 *
 * <p>只描述「建一个什么样的空工程」，具体目录由 {@code gen.output} 与这里的路径覆盖项共同决定：
 * 绝对路径优先，其次相对 {@code gen.output.backend-root} / {@code frontend-root}。
 */
@Data
@Schema(description = "一键建工程入参")
public class GenScaffoldDTO {

    @Schema(description = "工程名，同时作为后端 artifactId 与前端 package name", example = "order-center")
    @NotBlank(message = "工程名不能为空")
    private String projectName;

    @Schema(description = "基座根包名，生成物按 {rootPackage}.common.* 引用", example = "com.order")
    @NotBlank(message = "包名不能为空")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_.]*$", message = "根包名必须是合法 Java 包名")
    private String rootPackage;

    @Schema(description = "后端模块目录名（相对后端输出根），默认 {projectName}-server", example = "order-server")
    @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "模块目录名只能含字母、数字、下划线、点与横线")
    private String backendModule;

    @Schema(description = "前端模块目录名（相对前端输出根），默认 {projectName}-web", example = "order-web")
    @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "模块目录名只能含字母、数字、下划线、点与横线")
    private String frontendModule;

    @Schema(description = "后端输出根目录（绝对路径），留空用 gen.output.backend-root")
    private String backendRoot;

    @Schema(description = "前端输出根目录（绝对路径），留空用 gen.output.frontend-root")
    private String frontendRoot;

    @Schema(description = "服务端口，默认 8080")
    private Integer serverPort;

    @Schema(description = "前端 dev 端口，默认 serverPort + 1000")
    private Integer webPort;

    @Schema(description = "数据库名，写入 application.yml", example = "order_center")
    private String dbName;

    @Schema(description = "数据库地址，默认 127.0.0.1:3306")
    private String dbHost;

    @Schema(description = "数据库用户名，默认 root")
    private String dbUsername;

    @Schema(description = "数据库密码，默认 root")
    private String dbPassword;

    @Schema(description = "是否生成前端工程，默认 true")
    private Boolean withFrontend = Boolean.TRUE;

    @Schema(description = "是否生成登录与鉴权基座（AuthContextFilter + 登录接口），默认 true")
    private Boolean withAuth = Boolean.TRUE;

    @Schema(description = "工程结构：null=单模块，multi=Maven 多模块（parent + common/domain/service/bootstrap），"
            + "micro=微服务设施（多模块基础上追加 gateway 模块 + Nacos/OpenFeign 设施，默认关闭两栖运行），"
            + "登记系统时请把 sys_project.project_structure 设为同值")
    private String projectStructure;

    @Schema(description = "V6.1 产物流程引擎：null/builtin=内置简易流，activiti=Activiti 8（产物按值装配 FlowService 实现，含引擎依赖与 ACT_ 建表）")
    private String flowEngine;

    @Schema(description = "V6.2 是否生成部署物（Dockerfile/compose/CI 模板），默认 false；系统配置请把 sys_project.default_gen_deploy 设为同值")
    private Boolean genDeploy;

    @Schema(description = "V6.3 是否生成支付能力包（pay_order 表 + PaymentService 三渠道 + 收银台），默认 false；系统配置请把 sys_project.pay_enabled 设为同值")
    private Boolean payEnabled;

    @Schema(description = "V6.5 是否生成移动端工程（uni-app 单源 H5+微信小程序，落 {frontendModule}-mobile），默认 false；系统配置请把 sys_project.mobile_enabled 设为同值")
    private Boolean mobileEnabled;

    @Schema(description = "V6.6 是否生成文件能力包（sys_file 表 + /file 上传下载端点 + ImageUpload/FileUpload 组件），默认 false；系统配置请把 sys_project.file_enabled 设为同值")
    private Boolean fileEnabled;

    @Schema(description = "V6.7 是否生成测试骨架（src/test 集成测试 + H2 内存库，打开即 mvn test 全绿），默认 true；multi/micro 工程暂不支持（P1）")
    private Boolean testEnabled;

    @Schema(description = "V6.8 应用显示名（title/侧边栏/登录页统一取它）；空=回落到项目名")
    private String appName;

    @Schema(description = "V6.8 Logo 图片 URL；空=使用内置 logo")
    private String appLogo;

    @Schema(description = "V6.8 主题主色（#rgb / #rrggbb）；空或非法=回落 #e1251b")
    private String appThemeColor;

    /** V7.5 项目语言配置：默认语言 BCP47 标签（空 = zh-CN，存量零 diff） */
    private String defaultLanguage;

    /** V8.0：所属系统 ID，用于读取该系统的用户译文覆盖（直接调 scaffold 时可空） */
    private Long projectId;

    /** V7.5 项目语言配置：启用语言码，逗号分隔（空 = 默认语言 + 内置精译） */
    private String supportedLanguages;

    /** V8.0：产物国际化开关（NULL/false=关，true=开） */
    private Boolean i18nEnabled;

    @Schema(description = "V6.1 需下发的流程定义（平台不直连产物库，由调用方查询组装）；空=只写流程表 DDL")
    private java.util.List<FlowDef> flowDefs;

    /** V6.1 流程定义下发载体：nodes 为 sys_flow_def.nodes 原样 JSON 结构，bpmnXml 由平台 GenBpmnBuilder 预先翻译 */
    public static class FlowDef {
        private String flowCode;
        private String flowName;
        private String tableName;
        private String statusCol;
        private java.util.List<java.util.Map<String, Object>> nodes;
        private String bpmnXml;

        public String getFlowCode() { return flowCode; }
        public void setFlowCode(String v) { this.flowCode = v; }
        public String getFlowName() { return flowName; }
        public void setFlowName(String v) { this.flowName = v; }
        public String getTableName() { return tableName; }
        public void setTableName(String v) { this.tableName = v; }
        public String getStatusCol() { return statusCol; }
        public void setStatusCol(String v) { this.statusCol = v; }
        public java.util.List<java.util.Map<String, Object>> getNodes() { return nodes; }
        public void setNodes(java.util.List<java.util.Map<String, Object>> v) { this.nodes = v; }
        public String getBpmnXml() { return bpmnXml; }
        public void setBpmnXml(String v) { this.bpmnXml = v; }
    }

    @Schema(description = "是否覆盖已存在的文件，默认 false（存在即跳过，保证可重复执行）")
    private Boolean force = Boolean.FALSE;

    @Schema(description = "额外 pom 依赖坐标，形如 com.foo:bar:1.0.0")
    private List<String> extraDependencies;

    @Schema(description = "网关前缀：api.js 路径 /api/{gatewayPrefix}，为空默认 demo；前端代理会剥掉它直连后端")
    private String gatewayPrefix;
}

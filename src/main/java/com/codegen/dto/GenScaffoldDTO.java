package com.codegen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
    private String rootPackage;

    @Schema(description = "后端模块目录名（相对后端输出根），默认 {projectName}-server", example = "order-server")
    private String backendModule;

    @Schema(description = "前端模块目录名（相对前端输出根），默认 {projectName}-web", example = "order-web")
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

    @Schema(description = "V5.5 工程结构：null=单模块，multi=Maven 多模块（parent + common/domain/service/bootstrap），登记系统时请把 sys_project.project_structure 设为同值")
    private String projectStructure;

    @Schema(description = "是否覆盖已存在的文件，默认 false（存在即跳过，保证可重复执行）")
    private Boolean force = Boolean.FALSE;

    @Schema(description = "额外 pom 依赖坐标，形如 com.foo:bar:1.0.0")
    private List<String> extraDependencies;
}

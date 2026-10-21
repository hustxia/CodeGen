package com.codegen.service.impl;

import com.codegen.config.GenOutputProperties;
import com.codegen.dto.GenScaffoldDTO;
import com.codegen.service.GenScaffoldService;
import com.codegen.vo.GenScaffoldCheckVO;
import com.codegen.vo.GenScaffoldVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 一键建工程实现。
 *
 * <p>为什么需要它：demo-target 的基座（pom 依赖、11 个 common 类、分页插件、
 * historyApiFallback）此前全靠手工复制，每漏一项都是「静默故障」——
 * 缺分页插件表现为 total=0，缺 -parameters 表现为所有带参接口 500，
 * 缺 LongId 序列化表现为详情查不到。这里把这些已验证的基座固化下来。
 *
 * <p>幂等策略：文件已存在且 force=false 时跳过，不覆盖用户的修改。
 */
@Slf4j
@Service
public class GenScaffoldServiceImpl implements GenScaffoldService {

    @Autowired
    private GenOutputProperties outputProperties;

    @Override
    public GenScaffoldVO scaffold(GenScaffoldDTO dto) {
        String project = dto.getProjectName().trim();
        String pkg = dto.getRootPackage().trim();
        String backendModule = StringUtils.hasText(dto.getBackendModule())
                ? dto.getBackendModule().trim() : project + "-server";
        String frontendModule = StringUtils.hasText(dto.getFrontendModule())
                ? dto.getFrontendModule().trim() : project + "-web";
        int serverPort = dto.getServerPort() == null ? 8080 : dto.getServerPort();
        int webPort = dto.getWebPort() == null ? serverPort + 1000 : dto.getWebPort();

        Path backendRoot = resolveRoot(dto.getBackendRoot(), outputProperties.getBackendRoot(), backendModule);
        Path frontendRoot = resolveRoot(dto.getFrontendRoot(), outputProperties.getFrontendRoot(), frontendModule);

        GenScaffoldVO vo = new GenScaffoldVO();
        vo.setBackendPath(backendRoot.toString());
        vo.setFrontendPath(Boolean.TRUE.equals(dto.getWithFrontend()) ? frontendRoot.toString() : null);

        boolean force = Boolean.TRUE.equals(dto.getForce());
        int[] counter = new int[]{0, 0};

        // ---------- 后端 ----------
        write(backendRoot, "pom.xml", pomXml(dto, project, pkg, backendModule, serverPort), "backend", force, vo, counter);

        String pkgPath = "src/main/java/" + pkg.replace('.', '/');
        String appClass = toClassName(project);
        write(backendRoot, pkgPath + "/" + appClass + "Application.java",
                applicationJava(pkg, appClass), "backend", force, vo, counter);
        write(backendRoot, pkgPath + "/common/result/Result.java", resultJava(pkg), "backend", force, vo, counter);
        write(backendRoot, pkgPath + "/common/result/ResultCode.java", resultCodeJava(pkg), "backend", force, vo, counter);
        write(backendRoot, pkgPath + "/common/exception/BusinessException.java",
                businessExceptionJava(pkg), "backend", force, vo, counter);
        write(backendRoot, pkgPath + "/common/exception/GlobalExceptionHandler.java",
                globalExceptionHandlerJava(pkg), "backend", force, vo, counter);
        write(backendRoot, pkgPath + "/common/context/UserContext.java", userContextJava(pkg), "backend", force, vo, counter);
        write(backendRoot, pkgPath + "/common/entity/BaseEntity.java", baseEntityJava(pkg), "backend", force, vo, counter);
        write(backendRoot, pkgPath + "/common/page/PageQuery.java", pageQueryJava(pkg), "backend", force, vo, counter);
        write(backendRoot, pkgPath + "/common/config/MybatisPlusConfig.java",
                mybatisPlusConfigJava(pkg), "backend", force, vo, counter);
        write(backendRoot, pkgPath + "/common/datascope/DataScopeInterceptor.java",
                dataScopeInterceptorJava(pkg), "backend", force, vo, counter);
        write(backendRoot, pkgPath + "/common/datascope/DataScopeRegistry.java",
                dataScopeRegistryJava(pkg), "backend", force, vo, counter);
        write(backendRoot, pkgPath + "/common/log/Log.java", logAnnotationJava(pkg),
                "backend", force, vo, counter);
        write(backendRoot, pkgPath + "/common/log/LogAspect.java", logAspectJava(pkg),
                "backend", force, vo, counter);
        write(backendRoot, pkgPath + "/system/OpeLog.java", operLogEntityJava(pkg),
                "backend", force, vo, counter);
        write(backendRoot, pkgPath + "/system/OpeLogMapper.java", operLogMapperJava(pkg),
                "backend", force, vo, counter);
        write(backendRoot, pkgPath + "/system/OpeLogController.java", operLogControllerJava(pkg),
                "backend", force, vo, counter);
        write(backendRoot, pkgPath + "/common/config/LongIdJacksonConfig.java",
                longIdJacksonConfigJava(pkg), "backend", force, vo, counter);
        write(backendRoot, pkgPath + "/common/handler/AuditMetaObjectHandler.java",
                auditMetaObjectHandlerJava(pkg), "backend", force, vo, counter);
        write(backendRoot, pkgPath + "/common/util/MaskUtils.java", maskUtilsJava(pkg),
                "backend", force, vo, counter);
        if (Boolean.TRUE.equals(dto.getWithAuth())) {
            write(backendRoot, pkgPath + "/common/config/AuthContextFilter.java",
                    authContextFilterJava(pkg), "backend", force, vo, counter);
        }
        write(backendRoot, "src/main/resources/application.yml", applicationYml(dto, pkg, project, serverPort),
                "backend", force, vo, counter);
        write(backendRoot, "src/main/resources/application-local.yml.example",
                applicationLocalExample(pkg), "backend", force, vo, counter);
        write(backendRoot, ".gitignore", backendGitignore(), "backend", force, vo, counter);
        write(backendRoot, "README.md", backendReadme(project, pkg, backendModule, serverPort),
                "backend", force, vo, counter);

        // ---------- 前端 ----------
        if (Boolean.TRUE.equals(dto.getWithFrontend())) {
            write(frontendRoot, "package.json", frontendPackageJson(project, frontendModule),
                    "frontend", force, vo, counter);
            write(frontendRoot, "vue.config.js", frontendVueConfig(webPort, serverPort),
                    "frontend", force, vo, counter);
            write(frontendRoot, "babel.config.js", frontendBabelConfig(), "frontend", force, vo, counter);
            write(frontendRoot, ".env.development",
                    frontendEnvDev(serverPort), "frontend", force, vo, counter);
            write(frontendRoot, ".env.production", frontendEnvProd(), "frontend", force, vo, counter);
            write(frontendRoot, "public/index.html", frontendIndexHtml(project), "frontend", force, vo, counter);
            write(frontendRoot, "src/main.js", frontendMainJs(), "frontend", force, vo, counter);
            write(frontendRoot, "src/App.vue", frontendAppVue(), "frontend", force, vo, counter);
            write(frontendRoot, "src/utils/request.js", frontendRequestJs(), "frontend", force, vo, counter);
            write(frontendRoot, "src/layout/index.vue", frontendLayoutVue(project), "frontend", force, vo, counter);
            write(frontendRoot, "src/views/index.vue", frontendHomeVue(project), "frontend", force, vo, counter);
            write(frontendRoot, "src/router/index.js", frontendRouterJs(), "frontend", force, vo, counter);
            write(frontendRoot, "src/store/index.js", frontendStoreJs(), "frontend", force, vo, counter);
            write(frontendRoot, ".gitignore", frontendGitignore(), "frontend", force, vo, counter);
            write(frontendRoot, "README.md", frontendReadme(project, frontendModule, webPort, serverPort),
                    "frontend", force, vo, counter);
        }

        vo.setWritten(counter[0]);
        vo.setSkipped(counter[1]);

        vo.getNextSteps().add("cd " + backendRoot + " && mvn -DskipTests package 编译验证");
        vo.getNextSteps().add("按 application-local.yml.example 建 application-local.yml 填本机数据库密码");
        if (Boolean.TRUE.equals(dto.getWithFrontend())) {
            vo.getNextSteps().add("cd " + frontendRoot + " && npm install && npm run dev");
        }
        vo.getNextSteps().add("回到平台「脚手架体检」核对基座是否齐全");
        vo.getNextSteps().add("在生成器里导入表配置，生成代码后写入本工程");
        return vo;
    }

    /**
     * 解析输出根：入参绝对路径优先；否则相对配置的 root 拼模块目录。
     */
    private Path resolveRoot(String dtoRoot, String configRoot, String module) {
        if (StringUtils.hasText(dtoRoot)) {
            Path p = Paths.get(dtoRoot.trim()).toAbsolutePath().normalize();
            // 显式给了绝对路径就以它为工程根，不再拼模块名
            return p;
        }
        if (!StringUtils.hasText(configRoot)) {
            throw new IllegalStateException("未配置输出根目录：请在 gen.output.backend-root / frontend-root 配置，或显式传入路径");
        }
        return Paths.get(configRoot.trim(), module).toAbsolutePath().normalize();
    }

    private void write(Path root, String relPath, String content, String side,
                       boolean force, GenScaffoldVO vo, int[] counter) {
        Path target = root.resolve(relPath);
        try {
            boolean existed = Files.exists(target);
            if (existed && !force) {
                vo.getFiles().add(new GenScaffoldVO.Item(relPath, side, "SKIPPED"));
                counter[1]++;
                return;
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
            vo.getFiles().add(new GenScaffoldVO.Item(relPath, side, existed ? "OVERWRITTEN" : "WRITTEN"));
            counter[0]++;
        } catch (IOException e) {
            throw new IllegalStateException("写入失败: " + target + " - " + e.getMessage(), e);
        }
    }

    /**
     * order-center -> OrderCenter
     */
    private String toClassName(String name) {
        StringBuilder sb = new StringBuilder();
        for (String seg : name.split("[^A-Za-z0-9]+")) {
            if (seg.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(seg.charAt(0))).append(seg.substring(1));
        }
        return sb.length() == 0 ? "App" : sb.toString();
    }

    // ============================ 模板内容 ============================

    private String pomXml(GenScaffoldDTO dto, String project, String pkg, String module, int serverPort) {
        StringBuilder extras = new StringBuilder();
        if (dto.getExtraDependencies() != null) {
            for (String coord : dto.getExtraDependencies()) {
                String[] parts = coord.split(":");
                if (parts.length >= 2) {
                    String ver = parts.length >= 3 ? "<version>" + parts[2] + "</version>" : "";
                    extras.append("        <dependency>\n")
                          .append("            <groupId>").append(parts[0]).append("</groupId>\n")
                          .append("            <artifactId>").append(parts[1]).append("</artifactId>\n")
                          .append("            ").append(ver).append("\n")
                          .append("        </dependency>\n");
                }
            }
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
                + "    <modelVersion>4.0.0</modelVersion>\n\n"
                + "    <groupId>" + pkg + "</groupId>\n"
                + "    <artifactId>" + module + "</artifactId>\n"
                + "    <version>1.0.0</version>\n"
                + "    <packaging>jar</packaging>\n"
                + "    <name>" + module + "</name>\n"
                + "    <description>" + project + " 后端服务（由 codegen-server 脚手架生成）</description>\n\n"
                + "    <properties>\n"
                + "        <java.version>21</java.version>\n"
                + "        <maven.compiler.source>21</maven.compiler.source>\n"
                + "        <maven.compiler.target>21</maven.compiler.target>\n"
                + "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n"
                + "        <spring-boot.version>3.2.5</spring-boot.version>\n"
                + "        <mybatis-plus.version>3.5.5</mybatis-plus.version>\n"
                + "        <lombok.version>1.18.30</lombok.version>\n"
                + "    </properties>\n\n"
                + "    <dependencyManagement>\n"
                + "        <dependencies>\n"
                + "            <dependency>\n"
                + "                <groupId>org.springframework.boot</groupId>\n"
                + "                <artifactId>spring-boot-dependencies</artifactId>\n"
                + "                <version>${spring-boot.version}</version>\n"
                + "                <type>pom</type>\n"
                + "                <scope>import</scope>\n"
                + "            </dependency>\n"
                + "        </dependencies>\n"
                + "    </dependencyManagement>\n\n"
                + "    <dependencies>\n"
                + dep("org.springframework.boot", "spring-boot-starter-web", null)
                + dep("org.springframework.boot", "spring-boot-starter-validation", null)
                + dep("com.baomidou", "mybatis-plus-spring-boot3-starter", "${mybatis-plus.version}")
                + "        <dependency>\n"
                + "            <groupId>com.mysql</groupId>\n"
                + "            <artifactId>mysql-connector-j</artifactId>\n"
                + "            <scope>runtime</scope>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>org.projectlombok</groupId>\n"
                + "            <artifactId>lombok</artifactId>\n"
                + "            <version>${lombok.version}</version>\n"
                + "            <scope>provided</scope>\n"
                + "        </dependency>\n"
                + "        <!-- 生成物 Controller 带 @Tag/@Operation -->\n"
                + dep("org.springdoc", "springdoc-openapi-starter-webmvc-ui", "2.3.0")
                + "        <!-- 生成物 Controller 的 Excel 导入导出 -->\n"
                + dep("org.apache.poi", "poi-ooxml", "5.2.5")
                + extras
                + "    </dependencies>\n\n"
                + "    <build>\n"
                + "        <finalName>${project.artifactId}</finalName>\n"
                + "        <plugins>\n"
                + "            <!-- 必须保留 -parameters：Spring MVC 靠参数名绑定 @RequestParam/@PathVariable。\n"
                + "                 本 POM 未继承 spring-boot-starter-parent，不显式声明会导致运行时\n"
                + "                 \"Name for argument of type [X] not specified\" —— 所有带参接口 500 -->\n"
                + "            <plugin>\n"
                + "                <groupId>org.apache.maven.plugins</groupId>\n"
                + "                <artifactId>maven-compiler-plugin</artifactId>\n"
                + "                <version>3.13.0</version>\n"
                + "                <configuration>\n"
                + "                    <compilerArgs>\n"
                + "                        <arg>-parameters</arg>\n"
                + "                    </compilerArgs>\n"
                + "                    <annotationProcessorPaths>\n"
                + "                        <path>\n"
                + "                            <groupId>org.projectlombok</groupId>\n"
                + "                            <artifactId>lombok</artifactId>\n"
                + "                            <version>${lombok.version}</version>\n"
                + "                        </path>\n"
                + "                    </annotationProcessorPaths>\n"
                + "                </configuration>\n"
                + "            </plugin>\n"
                + "            <plugin>\n"
                + "                <groupId>org.springframework.boot</groupId>\n"
                + "                <artifactId>spring-boot-maven-plugin</artifactId>\n"
                + "                <version>${spring-boot.version}</version>\n"
                + "                <executions>\n"
                + "                    <execution>\n"
                + "                        <goals>\n"
                + "                            <goal>repackage</goal>\n"
                + "                        </goals>\n"
                + "                    </execution>\n"
                + "                </executions>\n"
                + "            </plugin>\n"
                + "        </plugins>\n"
                + "    </build>\n"
                + "</project>\n";
    }

    private String dep(String g, String a, String version) {
        StringBuilder sb = new StringBuilder();
        sb.append("        <dependency>\n")
          .append("            <groupId>").append(g).append("</groupId>\n")
          .append("            <artifactId>").append(a).append("</artifactId>\n");
        if (version != null) {
            sb.append("            <version>").append(version).append("</version>\n");
        }
        sb.append("        </dependency>\n");
        return sb.toString();
    }

    private String applicationJava(String pkg, String appClass) {
        return "package " + pkg + ";\n\n"
                + "import org.mybatis.spring.annotation.MapperScan;\n"
                + "import org.springframework.boot.SpringApplication;\n"
                + "import org.springframework.boot.autoconfigure.SpringBootApplication;\n\n"
                + "@SpringBootApplication\n"
                + "@MapperScan(\"" + pkg + ".mapper\")\n"
                + "public class " + appClass + "Application {\n\n"
                + "    public static void main(String[] args) {\n"
                + "        SpringApplication.run(" + appClass + "Application.class, args);\n"
                + "    }\n"
                + "}\n";
    }

    private String resultJava(String pkg) {
        return "package " + pkg + ".common.result;\n\n"
                + "import lombok.Data;\n\n"
                + "import java.io.Serial;\n"
                + "import java.io.Serializable;\n\n"
                + "/**\n"
                + " * 统一返回结果\n"
                + " */\n"
                + "@Data\n"
                + "public class Result<T> implements Serializable {\n\n"
                + "    @Serial\n"
                + "    private static final long serialVersionUID = 1L;\n\n"
                + "    private int code;\n"
                + "    private String msg;\n"
                + "    private T data;\n\n"
                + "    public Result() {\n"
                + "    }\n\n"
                + "    public Result(int code, String msg, T data) {\n"
                + "        this.code = code;\n"
                + "        this.msg = msg;\n"
                + "        this.data = data;\n"
                + "    }\n\n"
                + "    public static <T> Result<T> ok() {\n"
                + "        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), null);\n"
                + "    }\n\n"
                + "    public static <T> Result<T> ok(T data) {\n"
                + "        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), data);\n"
                + "    }\n\n"
                + "    public static <T> Result<T> fail(int code, String msg) {\n"
                + "        return new Result<>(code, msg, null);\n"
                + "    }\n\n"
                + "    public static <T> Result<T> fail(ResultCode resultCode) {\n"
                + "        return new Result<>(resultCode.getCode(), resultCode.getMsg(), null);\n"
                + "    }\n"
                + "}\n";
    }

    private String resultCodeJava(String pkg) {
        return "package " + pkg + ".common.result;\n\n"
                + "import lombok.AllArgsConstructor;\n"
                + "import lombok.Getter;\n\n"
                + "/**\n"
                + " * 状态码枚举\n"
                + " */\n"
                + "@Getter\n"
                + "@AllArgsConstructor\n"
                + "public enum ResultCode {\n\n"
                + "    SUCCESS(200, \"success\"),\n"
                + "    FAIL(500, \"服务异常\"),\n"
                + "    PARAM_ERROR(400, \"参数错误\"),\n"
                + "    UNAUTHORIZED(401, \"未授权\"),\n"
                + "    FORBIDDEN(403, \"无访问权限\"),\n"
                + "    NOT_FOUND(404, \"资源不存在\");\n\n"
                + "    private final int code;\n"
                + "    private final String msg;\n"
                + "}\n";
    }

    private String businessExceptionJava(String pkg) {
        return "package " + pkg + ".common.exception;\n\n"
                + "import " + pkg + ".common.result.ResultCode;\n"
                + "import lombok.Getter;\n\n"
                + "import java.io.Serial;\n\n"
                + "/**\n"
                + " * 业务异常\n"
                + " */\n"
                + "@Getter\n"
                + "public class BusinessException extends RuntimeException {\n\n"
                + "    @Serial\n"
                + "    private static final long serialVersionUID = 1L;\n\n"
                + "    private final int code;\n\n"
                + "    public BusinessException(ResultCode resultCode) {\n"
                + "        super(resultCode.getMsg());\n"
                + "        this.code = resultCode.getCode();\n"
                + "    }\n\n"
                + "    public BusinessException(String message) {\n"
                + "        super(message);\n"
                + "        this.code = ResultCode.FAIL.getCode();\n"
                + "    }\n\n"
                + "    public BusinessException(int code, String message) {\n"
                + "        super(message);\n"
                + "        this.code = code;\n"
                + "    }\n"
                + "}\n";
    }

    private String globalExceptionHandlerJava(String pkg) {
        return "package " + pkg + ".common.exception;\n\n"
                + "import " + pkg + ".common.result.Result;\n"
                + "import " + pkg + ".common.result.ResultCode;\n"
                + "import lombok.extern.slf4j.Slf4j;\n"
                + "import org.springframework.web.bind.MethodArgumentNotValidException;\n"
                + "import org.springframework.web.bind.annotation.ExceptionHandler;\n"
                + "import org.springframework.web.bind.annotation.RestControllerAdvice;\n\n"
                + "/**\n"
                + " * 全局异常兜底\n"
                + " */\n"
                + "@Slf4j\n"
                + "@RestControllerAdvice\n"
                + "public class GlobalExceptionHandler {\n\n"
                + "    @ExceptionHandler(BusinessException.class)\n"
                + "    public Result<Void> handleBusinessException(BusinessException ex) {\n"
                + "        log.warn(\"业务异常: code={}, msg={}\", ex.getCode(), ex.getMessage());\n"
                + "        return Result.fail(ex.getCode(), ex.getMessage());\n"
                + "    }\n\n"
                + "    @ExceptionHandler(MethodArgumentNotValidException.class)\n"
                + "    public Result<Void> handleValidException(MethodArgumentNotValidException ex) {\n"
                + "        String msg = ex.getBindingResult().getFieldErrors().stream()\n"
                + "                .findFirst()\n"
                + "                .map(e -> e.getField() + \":\" + e.getDefaultMessage())\n"
                + "                .orElse(ResultCode.PARAM_ERROR.getMsg());\n"
                + "        log.warn(\"参数校验失败: {}\", msg);\n"
                + "        return Result.fail(ResultCode.PARAM_ERROR.getCode(), msg);\n"
                + "    }\n\n"
                + "    @ExceptionHandler(Exception.class)\n"
                + "    public Result<Void> handleException(Exception ex) {\n"
                + "        log.error(\"系统异常\", ex);\n"
                + "        return Result.fail(ResultCode.FAIL);\n"
                + "    }\n"
                + "}\n";
    }

    private String userContextJava(String pkg) {
        return "package " + pkg + ".common.context;\n\n"
                + "/**\n"
                + " * 当前登录用户上下文（ThreadLocal）\n"
                + " * 由鉴权过滤器写入，业务代码只读。\n"
                + " */\n"
                + "public class UserContext {\n\n"
                + "    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();\n\n"
                + "    private static final ThreadLocal<String> USERNAME_HOLDER = new ThreadLocal<>();\n\n"
                + "    public static void setUserId(Long userId) {\n"
                + "        USER_ID_HOLDER.set(userId);\n"
                + "    }\n\n"
                + "    public static Long getUserId() {\n"
                + "        return USER_ID_HOLDER.get();\n"
                + "    }\n\n"
                + "    public static void setUsername(String username) {\n"
                + "        USERNAME_HOLDER.set(username);\n"
                + "    }\n\n"
                + "    public static String getUsername() {\n"
                + "        return USERNAME_HOLDER.get();\n"
                + "    }\n\n"
                + "    public static void clear() {\n"
                + "        USER_ID_HOLDER.remove();\n"
                + "        USERNAME_HOLDER.remove();\n"
                + "    }\n"
                + "}\n";
    }

    private String baseEntityJava(String pkg) {
        return "package " + pkg + ".common.entity;\n\n"
                + "import com.baomidou.mybatisplus.annotation.FieldFill;\n"
                + "import com.baomidou.mybatisplus.annotation.IdType;\n"
                + "import com.baomidou.mybatisplus.annotation.TableField;\n"
                + "import com.baomidou.mybatisplus.annotation.TableId;\n"
                + "import com.baomidou.mybatisplus.annotation.TableLogic;\n"
                + "import com.fasterxml.jackson.databind.annotation.JsonSerialize;\n"
                + "import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;\n"
                + "import lombok.Data;\n\n"
                + "import java.io.Serial;\n"
                + "import java.io.Serializable;\n"
                + "import java.time.LocalDateTime;\n\n"
                + "/**\n"
                + " * 实体基类：主键 + 审计字段 + 逻辑删除\n"
                + " */\n"
                + "@Data\n"
                + "public abstract class BaseEntity implements Serializable {\n\n"
                + "    @Serial\n"
                + "    private static final long serialVersionUID = 1L;\n\n"
                + "    /**\n"
                + "     * 主键：雪花 ID 为 19 位，超出 JS Number 安全整数范围，\n"
                + "     * 统一以字符串下发，避免前端精度丢失后回传错 ID。\n"
                + "     */\n"
                + "    @TableId(type = IdType.ASSIGN_ID)\n"
                + "    @JsonSerialize(using = ToStringSerializer.class)\n"
                + "    private Long id;\n\n"
                + "    @TableField(fill = FieldFill.INSERT)\n"
                + "    private LocalDateTime createTime;\n\n"
                + "    @TableField(fill = FieldFill.INSERT_UPDATE)\n"
                + "    private LocalDateTime updateTime;\n\n"
                + "    private Long createBy;\n\n"
                + "    private Long updateBy;\n\n"
                + "    @TableLogic\n"
                + "    private Integer deleted;\n"
                + "}\n";
    }

    private String pageQueryJava(String pkg) {
        return "package " + pkg + ".common.page;\n\n"
                + "import lombok.Data;\n\n"
                + "import java.io.Serial;\n"
                + "import java.io.Serializable;\n\n"
                + "/**\n"
                + " * 分页查询入参基类\n"
                + " */\n"
                + "@Data\n"
                + "public class PageQuery implements Serializable {\n\n"
                + "    @Serial\n"
                + "    private static final long serialVersionUID = 1L;\n\n"
                + "    private Integer pageNum = 1;\n\n"
                + "    private Integer pageSize = 10;\n"
                + "}\n";
    }

    private String mybatisPlusConfigJava(String pkg) {
        return "package " + pkg + ".common.config;\n\n"
                + "import com.baomidou.mybatisplus.annotation.DbType;\n"
                + "import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;\n"
                + "import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;\n"
                + "import " + pkg + ".common.datascope.DataScopeInterceptor;\n"
                + "import org.springframework.context.annotation.Bean;\n"
                + "import org.springframework.context.annotation.Configuration;\n\n"
                + "/**\n"
                + " * MyBatis-Plus 配置：注册数据权限与分页拦截器\n"
                + " *\n"
                + " * <p>分页拦截器必须注册：缺了它 {@code service.page(page, wrapper)} 只做 limit 不做 count，\n"
                + " * 返回的 {@code Page.total} 恒为 0——前端分页器算不出总页数。\n"
                + " *\n"
                + " * <p>数据权限拦截器放在分页之前：先缩小可见范围再 count，\n"
                + " * 否则不该看到的行也会被算进总数。\n"
                + " */\n"
                + "@Configuration\n"
                + "public class MybatisPlusConfig {\n\n"
                + "    @Bean\n"
                + "    public MybatisPlusInterceptor mybatisPlusInterceptor() {\n"
                + "        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();\n"
                + "        interceptor.addInnerInterceptor(new DataScopeInterceptor());\n"
                + "        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);\n"
                + "        // 单页上限，防止前端传 pageSize=999999 拖垮库\n"
                + "        pagination.setMaxLimit(500L);\n"
                + "        interceptor.addInnerInterceptor(pagination);\n"
                + "        return interceptor;\n"
                + "    }\n"
                + "}\n";
    }

    /**
     * 数据权限拦截器源码。用于把「只能看本部门/只能看本人」的约束下沉到 SQL 层，
     * 而不是靠每个查询自己记得加条件（那迟早会漏）。
     */
    private String dataScopeInterceptorJava(String pkg) {
        return "package " + pkg + ".common.datascope;\n\n"
                + "import com.baomidou.mybatisplus.core.toolkit.PluginUtils;\n"
                + "import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;\n"
                + "import net.sf.jsqlparser.expression.Expression;\n"
                + "import net.sf.jsqlparser.expression.LongValue;\n"
                + "import net.sf.jsqlparser.expression.operators.conditional.AndExpression;\n"
                + "import net.sf.jsqlparser.expression.operators.relational.EqualsTo;\n"
                + "import net.sf.jsqlparser.parser.CCJSqlParserUtil;\n"
                + "import net.sf.jsqlparser.schema.Column;\n"
                + "import net.sf.jsqlparser.schema.Table;\n"
                + "import net.sf.jsqlparser.statement.Statement;\n"
                + "import net.sf.jsqlparser.statement.delete.Delete;\n"
                + "import net.sf.jsqlparser.statement.select.PlainSelect;\n"
                + "import net.sf.jsqlparser.statement.select.Select;\n"
                + "import net.sf.jsqlparser.statement.update.Update;\n"
                + "import " + pkg + ".common.context.UserContext;\n"
                + "import org.apache.ibatis.executor.Executor;\n"
                + "import org.apache.ibatis.executor.statement.StatementHandler;\n"
                + "import org.apache.ibatis.mapping.BoundSql;\n"
                + "import org.apache.ibatis.mapping.MappedStatement;\n"
                + "import org.apache.ibatis.session.ResultHandler;\n"
                + "import org.apache.ibatis.session.RowBounds;\n"
                + "import org.slf4j.Logger;\n"
                + "import org.slf4j.LoggerFactory;\n\n"
                + "import java.sql.Connection;\n"
                + "import java.util.Map;\n"
                + "import java.util.concurrent.ConcurrentHashMap;\n\n"
                + "/**\n"
                + " * 数据权限拦截器：对已登记的表自动追加过滤条件。\n"
                + " *\n"
                + " * <p>规则由 {@link DataScopeRegistry} 在启动时登记，不在这里查库——\n"
                + " * 拦截器里查库会再次触发拦截器，形成死循环。\n"
                + " */\n"
                + "public class DataScopeInterceptor implements InnerInterceptor {\n\n"
                + "    private static final Logger log = LoggerFactory.getLogger(DataScopeInterceptor.class);\n\n"
                + "    private static final Map<String, Rule> RULES = new ConcurrentHashMap<>();\n\n"
                + "    public record Rule(String column, String scope) {\n"
                + "    }\n\n"
                + "    /**\n"
                + "     * 登记一张表的数据权限列。scope 取 self（按本人）或 dept（按本部门）。\n"
                + "     */\n"
                + "    public static void register(String tableName, String column, String scope) {\n"
                + "        if (tableName == null || column == null || scope == null) {\n"
                + "            return;\n"
                + "        }\n"
                + "        RULES.put(tableName.replace(\"`\", \"\").toLowerCase(), new Rule(column, scope.toLowerCase()));\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,\n"
                + "                            RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {\n"
                + "        rewrite(boundSql);\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public void beforePrepare(StatementHandler sh, Connection connection, Integer timeout) {\n"
                + "        rewrite(sh.getBoundSql());\n"
                + "    }\n\n"
                + "    private void rewrite(BoundSql boundSql) {\n"
                + "        if (RULES.isEmpty()) {\n"
                + "            return;\n"
                + "        }\n"
                + "        if (\"all\".equals(UserContext.getDataScope())) {\n"
                + "            return;\n"
                + "        }\n"
                + "        Long userId = UserContext.getUserId();\n"
                + "        if (userId == null) {\n"
                + "            // 无登录态（定时任务、内部调用）不过滤，否则会静默查不到任何数据\n"
                + "            return;\n"
                + "        }\n"
                + "        String sql = boundSql.getSql();\n"
                + "        if (sql == null || sql.isBlank()) {\n"
                + "            return;\n"
                + "        }\n"
                + "        try {\n"
                + "            Statement stmt = CCJSqlParserUtil.parse(sql);\n"
                + "            boolean changed = false;\n"
                + "            if (stmt instanceof Select select && select.getSelectBody() instanceof PlainSelect ps\n"
                + "                    && ps.getFromItem() instanceof Table table) {\n"
                + "                Expression cond = condition(table);\n"
                + "                if (cond != null) {\n"
                + "                    ps.setWhere(and(ps.getWhere(), cond));\n"
                + "                    changed = true;\n"
                + "                }\n"
                + "            } else if (stmt instanceof Update update) {\n"
                + "                Expression cond = condition(update.getTable());\n"
                + "                if (cond != null) {\n"
                + "                    update.setWhere(and(update.getWhere(), cond));\n"
                + "                    changed = true;\n"
                + "                }\n"
                + "            } else if (stmt instanceof Delete delete) {\n"
                + "                Expression cond = condition(delete.getTable());\n"
                + "                if (cond != null) {\n"
                + "                    delete.setWhere(and(delete.getWhere(), cond));\n"
                + "                    changed = true;\n"
                + "                }\n"
                + "            }\n"
                + "            if (changed) {\n"
                + "                PluginUtils.mpBoundSql(boundSql).sql(stmt.toString());\n"
                + "            }\n"
                + "        } catch (Exception e) {\n"
                + "            // 解析失败不能阻断业务：放行原 SQL 并告警，避免一条怪 SQL 让整个接口 500\n"
                + "            log.warn(\"数据权限改写失败，已放行原 SQL：{}\", e.getMessage());\n"
                + "        }\n"
                + "    }\n\n"
                + "    private Expression condition(Table table) {\n"
                + "        if (table == null || table.getName() == null) {\n"
                + "            return null;\n"
                + "        }\n"
                + "        Rule rule = RULES.get(table.getName().replace(\"`\", \"\").toLowerCase());\n"
                + "        if (rule == null) {\n"
                + "            return null;\n"
                + "        }\n"
                + "        Long value = \"dept\".equals(rule.scope()) ? UserContext.getDeptId() : UserContext.getUserId();\n"
                + "        if (value == null) {\n"
                + "            // 部门信息缺失时降级按本人过滤，宁严不松\n"
                + "            value = UserContext.getUserId();\n"
                + "        }\n"
                + "        if (value == null) {\n"
                + "            return null;\n"
                + "        }\n"
                + "        EqualsTo eq = new EqualsTo();\n"
                + "        eq.setLeftExpression(new Column(table, rule.column()));\n"
                + "        eq.setRightExpression(new LongValue(value));\n"
                + "        return eq;\n"
                + "    }\n\n"
                + "    private Expression and(Expression where, Expression cond) {\n"
                + "        return where == null ? cond : new AndExpression(where, cond);\n"
                + "    }\n"
                + "}\n";
    }

    /**
     * 数据权限登记表：启动时把「哪张表的哪一列按什么范围过滤」交给拦截器。
     * 生成器产出的业务代码不需要改，只要在 types 里登记一条。
     */
    private String dataScopeRegistryJava(String pkg) {
        return "package " + pkg + ".common.datascope;\n\n"
                + "import jakarta.annotation.PostConstruct;\n"
                + "import org.slf4j.Logger;\n"
                + "import org.slf4j.LoggerFactory;\n"
                + "import org.springframework.stereotype.Component;\n\n"
                + "/**\n"
                + " * 数据权限登记表。\n"
                + " *\n"
                + " * <p>新增需要做数据隔离的表时，在下面 {@code register} 里加一行即可，\n"
                + " * 生成器产出的 Service/Mapper 无需改动。\n"
                + " */\n"
                + "@Component\n"
                + "public class DataScopeRegistry {\n\n"
                + "    private static final Logger log = LoggerFactory.getLogger(DataScopeRegistry.class);\n\n"
                + "    @PostConstruct\n"
                + "    public void init() {\n"
                + "        // 示例：DataScopeInterceptor.register(\"biz_order\", \"owner_id\", \"self\");\n"
                + "        // 示例：DataScopeInterceptor.register(\"biz_order\", \"dept_id\", \"dept\");\n"
                + "        log.info(\"数据权限登记完成\");\n"
                + "    }\n"
                + "}\n";
    }

    private String logAnnotationJava(String pkg) {
        return "package " + pkg + ".common.log;\n\n"
                + "import java.lang.annotation.ElementType;\n"
                + "import java.lang.annotation.Retention;\n"
                + "import java.lang.annotation.RetentionPolicy;\n"
                + "import java.lang.annotation.Target;\n\n"
                + "/**\n"
                + " * 操作日志注解。打在 Controller 的写操作方法上，由 {@link LogAspect} 统一落库。\n"
                + " *\n"
                + " * <p>只标写操作——把查询也记进来会让日志表迅速膨胀，真正需要追溯的变更反而查不到。\n"
                + " */\n"
                + "@Target(ElementType.METHOD)\n"
                + "@Retention(RetentionPolicy.RUNTIME)\n"
                + "public @interface Log {\n\n"
                + "    /**\n"
                + "     * 模块标题，如「订单管理」\n"
                + "     */\n"
                + "    String title() default \"\";\n\n"
                + "    /**\n"
                + "     * 操作类型：INSERT/UPDATE/DELETE/IMPORT/EXPORT/OTHER，留空按 HTTP 方法推断\n"
                + "     */\n"
                + "    String type() default \"\";\n\n"
                + "    /**\n"
                + "     * 是否记录请求参数\n"
                + "     */\n"
                + "    boolean saveParam() default true;\n\n"
                + "    /**\n"
                + "     * 是否记录返回结果\n"
                + "     */\n"
                + "    boolean saveResult() default false;\n"
                + "}\n";
    }

    private String logAspectJava(String pkg) {
        return "package " + pkg + ".common.log;\n\n"
                + "import " + pkg + ".common.context.UserContext;\n"
                + "import " + pkg + ".system.OpeLog;\n"
                + "import " + pkg + ".system.OpeLogMapper;\n"
                + "import com.fasterxml.jackson.databind.ObjectMapper;\n"
                + "import jakarta.servlet.http.HttpServletRequest;\n"
                + "import org.aspectj.lang.ProceedingJoinPoint;\n"
                + "import org.aspectj.lang.annotation.Around;\n"
                + "import org.aspectj.lang.annotation.Aspect;\n"
                + "import org.aspectj.lang.reflect.MethodSignature;\n"
                + "import org.slf4j.Logger;\n"
                + "import org.slf4j.LoggerFactory;\n"
                + "import org.springframework.beans.factory.annotation.Autowired;\n"
                + "import org.springframework.stereotype.Component;\n"
                + "import org.springframework.web.context.request.RequestContextHolder;\n"
                + "import org.springframework.web.context.request.ServletRequestAttributes;\n"
                + "import org.springframework.web.multipart.MultipartFile;\n\n"
                + "import java.time.LocalDateTime;\n"
                + "import java.util.ArrayList;\n"
                + "import java.util.Arrays;\n"
                + "import java.util.List;\n"
                + "import java.util.Locale;\n"
                + "import java.util.regex.Pattern;\n\n"
                + "/**\n"
                + " * 操作日志切面。\n"
                + " *\n"
                + " * <p>日志写失败绝不影响主流程；参数落库前按字段名脱敏；超长内容截断。\n"
                + " */\n"
                + "@Aspect\n"
                + "@Component\n"
                + "public class LogAspect {\n\n"
                + "    private static final Logger log = LoggerFactory.getLogger(LogAspect.class);\n\n"
                + "    /**\n"
                + "     * 单字段上限：够定位问题，又不至于把表塞满\n"
                + "     */\n"
                + "    private static final int MAX_TEXT = 2000;\n\n"
                + "    private static final Pattern SENSITIVE = Pattern.compile(\n"
                + "            \"(?i)(password|passwd|pwd|token|secret|authorization|credential)\");\n\n"
                + "    @Autowired\n"
                + "    private OpeLogMapper opeLogMapper;\n\n"
                + "    @Autowired\n"
                + "    private ObjectMapper objectMapper;\n\n"
                + "    @Around(\"@annotation(operLog)\")\n"
                + "    public Object around(ProceedingJoinPoint point, Log operLog) throws Throwable {\n"
                + "        long start = System.currentTimeMillis();\n"
                + "        String errorMsg = null;\n"
                + "        Object result = null;\n"
                + "        try {\n"
                + "            result = point.proceed();\n"
                + "            return result;\n"
                + "        } catch (Throwable e) {\n"
                + "            errorMsg = e.getMessage();\n"
                + "            throw e;\n"
                + "        } finally {\n"
                + "            try {\n"
                + "                save(point, operLog, result, errorMsg, System.currentTimeMillis() - start);\n"
                + "            } catch (Exception e) {\n"
                + "                // 记录日志失败不能反向影响业务\n"
                + "                log.warn(\"操作日志写入失败：{}\", e.getMessage());\n"
                + "            }\n"
                + "        }\n"
                + "    }\n\n"
                + "    private void save(ProceedingJoinPoint point, Log operLog, Object result,\n"
                + "                      String errorMsg, long costMs) {\n"
                + "        OpeLog entity = new OpeLog();\n"
                + "        entity.setTitle(operLog.title());\n"
                + "        entity.setStatus(errorMsg == null ? 1 : 0);\n"
                + "        entity.setErrorMsg(truncate(errorMsg));\n"
                + "        entity.setCostMs(costMs);\n"
                + "        entity.setOperTime(LocalDateTime.now());\n"
                + "        entity.setOperUserId(UserContext.getUserId());\n"
                + "        entity.setOperName(UserContext.getUsername());\n\n"
                + "        MethodSignature signature = (MethodSignature) point.getSignature();\n"
                + "        entity.setMethod(signature.getDeclaringType().getSimpleName() + \"#\" + signature.getName());\n\n"
                + "        HttpServletRequest request = currentRequest();\n"
                + "        if (request != null) {\n"
                + "            entity.setRequestUri(request.getRequestURI());\n"
                + "            entity.setRequestMethod(request.getMethod());\n"
                + "            entity.setOperIp(clientIp(request));\n"
                + "        }\n"
                + "        entity.setOperType(inferType(operLog.type(), request, signature.getName()));\n"
                + "        if (operLog.saveParam()) {\n"
                + "            entity.setOperParam(truncate(toJson(sanitize(point.getArgs()))));\n"
                + "        }\n"
                + "        if (operLog.saveResult() && result != null) {\n"
                + "            entity.setJsonResult(truncate(toJson(result)));\n"
                + "        }\n"
                + "        opeLogMapper.insert(entity);\n"
                + "    }\n\n"
                + "    /**\n"
                + "     * 文件流、Servlet 对象无法序列化，替换成类型占位\n"
                + "     */\n"
                + "    private List<Object> sanitize(Object[] args) {\n"
                + "        List<Object> list = new ArrayList<>();\n"
                + "        if (args == null) {\n"
                + "            return list;\n"
                + "        }\n"
                + "        for (Object arg : args) {\n"
                + "            if (arg instanceof MultipartFile file) {\n"
                + "                list.add(\"<file:\" + file.getOriginalFilename() + \", \" + file.getSize() + \"B>\");\n"
                + "            } else if (arg instanceof MultipartFile[] files) {\n"
                + "                list.add(\"<files:\" + files.length + \">\");\n"
                + "            } else if (arg instanceof jakarta.servlet.ServletRequest\n"
                + "                    || arg instanceof jakarta.servlet.ServletResponse) {\n"
                + "                list.add(\"<servlet>\");\n"
                + "            } else {\n"
                + "                list.add(arg);\n"
                + "            }\n"
                + "        }\n"
                + "        return list;\n"
                + "    }\n\n"
                + "    private String toJson(Object value) {\n"
                + "        if (value == null) {\n"
                + "            return null;\n"
                + "        }\n"
                + "        try {\n"
                + "            String json = objectMapper.writeValueAsString(value);\n"
                + "            // 按字段名脱敏：值内容不做正则替换，否则正常业务文本也会被误伤\n"
                + "            return SENSITIVE.matcher(json).replaceAll(m -> m.group() + \"\\\":\\\"****\");\n"
                + "        } catch (Exception e) {\n"
                + "            return String.valueOf(value);\n"
                + "        }\n"
                + "    }\n\n"
                + "    private String inferType(String declared, HttpServletRequest request, String methodName) {\n"
                + "        if (declared != null && !declared.isBlank()) {\n"
                + "            return declared.toUpperCase(Locale.ROOT);\n"
                + "        }\n"
                + "        String m = methodName.toLowerCase(Locale.ROOT);\n"
                + "        if (m.contains(\"import\")) {\n"
                + "            return \"IMPORT\";\n"
                + "        }\n"
                + "        if (m.contains(\"export\")) {\n"
                + "            return \"EXPORT\";\n"
                + "        }\n"
                + "        if (request == null) {\n"
                + "            return \"OTHER\";\n"
                + "        }\n"
                + "        return switch (request.getMethod().toUpperCase(Locale.ROOT)) {\n"
                + "            case \"POST\" -> \"INSERT\";\n"
                + "            case \"PUT\", \"PATCH\" -> \"UPDATE\";\n"
                + "            case \"DELETE\" -> \"DELETE\";\n"
                + "            default -> \"OTHER\";\n"
                + "        };\n"
                + "    }\n\n"
                + "    private HttpServletRequest currentRequest() {\n"
                + "        try {\n"
                + "            ServletRequestAttributes attrs =\n"
                + "                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();\n"
                + "            return attrs == null ? null : attrs.getRequest();\n"
                + "        } catch (Exception e) {\n"
                + "            return null;\n"
                + "        }\n"
                + "    }\n\n"
                + "    private String clientIp(HttpServletRequest request) {\n"
                + "        for (String header : Arrays.asList(\"X-Forwarded-For\", \"X-Real-IP\")) {\n"
                + "            String value = request.getHeader(header);\n"
                + "            if (value != null && !value.isBlank()) {\n"
                + "                // 多级代理时第一个才是客户端\n"
                + "                return value.split(\",\")[0].trim();\n"
                + "            }\n"
                + "        }\n"
                + "        return request.getRemoteAddr();\n"
                + "    }\n\n"
                + "    private String truncate(String text) {\n"
                + "        if (text == null) {\n"
                + "            return null;\n"
                + "        }\n"
                + "        return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) + \"...(truncated)\" : text;\n"
                + "    }\n"
                + "}\n";
    }

    private String operLogEntityJava(String pkg) {
        return "package " + pkg + ".system;\n\n"
                + "import " + pkg + ".common.entity.BaseEntity;\n"
                + "import com.baomidou.mybatisplus.annotation.TableName;\n"
                + "import lombok.Data;\n"
                + "import lombok.EqualsAndHashCode;\n\n"
                + "import java.time.LocalDateTime;\n\n"
                + "/**\n"
                + " * 操作日志。只记写操作，查询不入库。\n"
                + " */\n"
                + "@Data\n"
                + "@EqualsAndHashCode(callSuper = true)\n"
                + "@TableName(\"sys_oper_log\")\n"
                + "public class OpeLog extends BaseEntity {\n\n"
                + "    private String title;\n\n"
                + "    private String operType;\n\n"
                + "    private String method;\n\n"
                + "    private String requestUri;\n\n"
                + "    private String requestMethod;\n\n"
                + "    private String operParam;\n\n"
                + "    private String jsonResult;\n\n"
                + "    /**\n"
                + "     * 1 成功 0 失败\n"
                + "     */\n"
                + "    private Integer status;\n\n"
                + "    private String errorMsg;\n\n"
                + "    private Long operUserId;\n\n"
                + "    private String operName;\n\n"
                + "    private String operIp;\n\n"
                + "    private Long costMs;\n\n"
                + "    private LocalDateTime operTime;\n"
                + "}\n";
    }

    private String operLogMapperJava(String pkg) {
        return "package " + pkg + ".system;\n\n"
                + "import com.baomidou.mybatisplus.core.mapper.BaseMapper;\n"
                + "import org.apache.ibatis.annotations.Mapper;\n\n"
                + "/**\n"
                + " * 操作日志 Mapper\n"
                + " */\n"
                + "@Mapper\n"
                + "public interface OpeLogMapper extends BaseMapper<OpeLog> {\n"
                + "}\n";
    }

    private String operLogControllerJava(String pkg) {
        return "package " + pkg + ".system;\n\n"
                + "import " + pkg + ".common.result.Result;\n"
                + "import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;\n"
                + "import com.baomidou.mybatisplus.extension.plugins.pagination.Page;\n"
                + "import io.swagger.v3.oas.annotations.Operation;\n"
                + "import io.swagger.v3.oas.annotations.tags.Tag;\n"
                + "import org.springframework.beans.factory.annotation.Autowired;\n"
                + "import org.springframework.format.annotation.DateTimeFormat;\n"
                + "import org.springframework.util.StringUtils;\n"
                + "import org.springframework.web.bind.annotation.DeleteMapping;\n"
                + "import org.springframework.web.bind.annotation.GetMapping;\n"
                + "import org.springframework.web.bind.annotation.RequestMapping;\n"
                + "import org.springframework.web.bind.annotation.RequestParam;\n"
                + "import org.springframework.web.bind.annotation.RestController;\n\n"
                + "import java.time.LocalDateTime;\n\n"
                + "/**\n"
                + " * 操作日志查询。只读接口——能改的日志没有审计价值。\n"
                + " */\n"
                + "@Tag(name = \"操作日志\")\n"
                + "@RestController\n"
                + "@RequestMapping(\"/system/oper-log\")\n"
                + "public class OpeLogController {\n\n"
                + "    @Autowired\n"
                + "    private OpeLogMapper opeLogMapper;\n\n"
                + "    @Operation(summary = \"分页查询操作日志\")\n"
                + "    @GetMapping(\"/page\")\n"
                + "    public Result<Page<OpeLog>> page(\n"
                + "            @RequestParam(defaultValue = \"1\") Integer pageNum,\n"
                + "            @RequestParam(defaultValue = \"10\") Integer pageSize,\n"
                + "            @RequestParam(required = false) String title,\n"
                + "            @RequestParam(required = false) String operName,\n"
                + "            @RequestParam(required = false) String operType,\n"
                + "            @RequestParam(required = false) Integer status,\n"
                + "            @RequestParam(required = false)\n"
                + "            @DateTimeFormat(pattern = \"yyyy-MM-dd HH:mm:ss\") LocalDateTime startTime,\n"
                + "            @RequestParam(required = false)\n"
                + "            @DateTimeFormat(pattern = \"yyyy-MM-dd HH:mm:ss\") LocalDateTime endTime) {\n"
                + "        Page<OpeLog> page = new Page<>(pageNum, pageSize);\n"
                + "        LambdaQueryWrapper<OpeLog> wrapper = new LambdaQueryWrapper<>();\n"
                + "        wrapper.like(StringUtils.hasText(title), OpeLog::getTitle, title);\n"
                + "        wrapper.like(StringUtils.hasText(operName), OpeLog::getOperName, operName);\n"
                + "        wrapper.eq(StringUtils.hasText(operType), OpeLog::getOperType, operType);\n"
                + "        wrapper.eq(status != null, OpeLog::getStatus, status);\n"
                + "        wrapper.ge(startTime != null, OpeLog::getOperTime, startTime);\n"
                + "        wrapper.le(endTime != null, OpeLog::getOperTime, endTime);\n"
                + "        wrapper.orderByDesc(OpeLog::getId);\n"
                + "        opeLogMapper.selectPage(page, wrapper);\n"
                + "        return Result.ok(page);\n"
                + "    }\n\n"
                + "    @Operation(summary = \"清理指定时间之前的日志\")\n"
                + "    @DeleteMapping(\"/before\")\n"
                + "    public Result<Integer> cleanBefore(\n"
                + "            @RequestParam @DateTimeFormat(pattern = \"yyyy-MM-dd HH:mm:ss\") LocalDateTime before) {\n"
                + "        LambdaQueryWrapper<OpeLog> wrapper = new LambdaQueryWrapper<>();\n"
                + "        wrapper.lt(OpeLog::getOperTime, before);\n"
                + "        return Result.ok(opeLogMapper.delete(wrapper));\n"
                + "    }\n"
                + "}\n";
    }

    private String longIdJacksonConfigJava(String pkg) {
        return "package " + pkg + ".common.config;\n\n"
                + "import com.fasterxml.jackson.databind.BeanDescription;\n"
                + "import com.fasterxml.jackson.databind.SerializationConfig;\n"
                + "import com.fasterxml.jackson.databind.module.SimpleModule;\n"
                + "import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;\n"
                + "import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;\n"
                + "import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;\n"
                + "import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;\n"
                + "import org.springframework.context.annotation.Bean;\n"
                + "import org.springframework.context.annotation.Configuration;\n\n"
                + "import java.util.List;\n\n"
                + "/**\n"
                + " * 雪花 ID 序列化保护。\n"
                + " *\n"
                + " * <p>主键为 19 位雪花号，超出 JS Number.MAX_SAFE_INTEGER。若以 JSON number 下发，\n"
                + " * 前端会静默截断，回传后端即查不到记录（详情 null / 删除 404 / 更新无效）。\n"
                + " * 这里统一把「名为 id 或以 Id 结尾」的 Long 字段序列化为字符串。\n"
                + " */\n"
                + "@Configuration\n"
                + "public class LongIdJacksonConfig {\n\n"
                + "    @Bean\n"
                + "    public Jackson2ObjectMapperBuilderCustomizer longIdToStringCustomizer() {\n"
                + "        SimpleModule module = new SimpleModule(\"long-id-to-string-module\");\n"
                + "        module.setSerializerModifier(new BeanSerializerModifier() {\n"
                + "            @Override\n"
                + "            public List<BeanPropertyWriter> changeProperties(SerializationConfig config,\n"
                + "                                                             BeanDescription beanDesc,\n"
                + "                                                             List<BeanPropertyWriter> beanProperties) {\n"
                + "                for (BeanPropertyWriter writer : beanProperties) {\n"
                + "                    if (!isIdField(writer.getName())) {\n"
                + "                        continue;\n"
                + "                    }\n"
                + "                    Class<?> raw = writer.getType() == null ? null : writer.getType().getRawClass();\n"
                + "                    // 字段上已有 @JsonSerialize 的不能再 assign，否则抛 Cannot override _serializer\n"
                + "                    if ((raw == Long.class || raw == long.class) && writer.getSerializer() == null) {\n"
                + "                        writer.assignSerializer(ToStringSerializer.instance);\n"
                + "                    }\n"
                + "                }\n"
                + "                return beanProperties;\n"
                + "            }\n"
                + "        });\n"
                + "        return builder -> builder.postConfigurer(objectMapper -> objectMapper.registerModule(module));\n"
                + "    }\n\n"
                + "    private boolean isIdField(String name) {\n"
                + "        return \"id\".equals(name) || (name.length() > 2 && name.endsWith(\"Id\"));\n"
                + "    }\n"
                + "}\n";
    }

    private String auditMetaObjectHandlerJava(String pkg) {
        return "package " + pkg + ".common.handler;\n\n"
                + "import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;\n"
                + "import " + pkg + ".common.context.UserContext;\n"
                + "import org.apache.ibatis.reflection.MetaObject;\n"
                + "import org.springframework.stereotype.Component;\n\n"
                + "import java.time.LocalDateTime;\n\n"
                + "/**\n"
                + " * MyBatis-Plus 审计字段自动填充\n"
                + " * createTime/updateTime 由 DB 默认值兜底，这里显式填充保证实体回写；\n"
                + " * createBy/updateBy 从当前登录用户上下文取，未登录为 null。\n"
                + " */\n"
                + "@Component\n"
                + "public class AuditMetaObjectHandler implements MetaObjectHandler {\n\n"
                + "    @Override\n"
                + "    public void insertFill(MetaObject metaObject) {\n"
                + "        LocalDateTime now = LocalDateTime.now();\n"
                + "        this.strictInsertFill(metaObject, \"createTime\", LocalDateTime.class, now);\n"
                + "        this.strictInsertFill(metaObject, \"updateTime\", LocalDateTime.class, now);\n"
                + "        Long userId = UserContext.getUserId();\n"
                + "        if (userId != null) {\n"
                + "            this.strictInsertFill(metaObject, \"createBy\", Long.class, userId);\n"
                + "            this.strictInsertFill(metaObject, \"updateBy\", Long.class, userId);\n"
                + "        }\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public void updateFill(MetaObject metaObject) {\n"
                + "        this.strictUpdateFill(metaObject, \"updateTime\", LocalDateTime.class, LocalDateTime.now());\n"
                + "        Long userId = UserContext.getUserId();\n"
                + "        if (userId != null) {\n"
                + "            this.strictUpdateFill(metaObject, \"updateBy\", Long.class, userId);\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
    }

    private String maskUtilsJava(String pkg) {
        return "package " + pkg + ".common.util;\n"
                + "\n"
                + "/**\n"
                + " * 敏感字段脱敏工具。\n"
                + " *\n"
                + " * <p>生成物 VO 的 getter 会调用这里，保证列表、详情、导出三条路径都脱敏，\n"
                + " * 而不是只在前端表格里替换——前端脱敏在接口层面仍是明文。\n"
                + " *\n"
                + " * <p>设计约定：\n"
                + " * <ul>\n"
                + " *   <li>入参为 null 或空串原样返回，避免把 null 变成 \"null\"；</li>\n"
                + " *   <li>长度不足时用 {@code *} 补齐可见长度，不做越界截取；</li>\n"
                + " *   <li>未知类型原样返回，不吞数据。</li>\n"
                + " * </ul>\n"
                + " */\n"
                + "public final class MaskUtils {\n"
                + "\n"
                + "    private MaskUtils() {\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * 按类型脱敏\n"
                + "     *\n"
                + "     * @param value 原值\n"
                + "     * @param type  phone / idcard / email / name / bankcard\n"
                + "     */\n"
                + "    public static String mask(String value, String type) {\n"
                + "        if (value == null || value.isEmpty() || type == null) {\n"
                + "            return value;\n"
                + "        }\n"
                + "        switch (type.trim().toLowerCase()) {\n"
                + "            case \"phone\":\n"
                + "                return phone(value);\n"
                + "            case \"idcard\":\n"
                + "                return idcard(value);\n"
                + "            case \"email\":\n"
                + "                return email(value);\n"
                + "            case \"name\":\n"
                + "                return name(value);\n"
                + "            case \"bankcard\":\n"
                + "                return bankcard(value);\n"
                + "            default:\n"
                + "                return value;\n"
                + "        }\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * 手机号：138****8000（保留前3后4）\n"
                + "     */\n"
                + "    public static String phone(String v) {\n"
                + "        if (v.length() < 7) {\n"
                + "            return repeat(v.length());\n"
                + "        }\n"
                + "        return v.substring(0, 3) + \"****\" + v.substring(v.length() - 4);\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * 身份证：110***********1234（保留前3后4）\n"
                + "     */\n"
                + "    public static String idcard(String v) {\n"
                + "        if (v.length() < 7) {\n"
                + "            return repeat(v.length());\n"
                + "        }\n"
                + "        return v.substring(0, 3) + repeat(v.length() - 7) + v.substring(v.length() - 4);\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * 邮箱：ab***@example.com（保留首2字符与完整域名）\n"
                + "     */\n"
                + "    public static String email(String v) {\n"
                + "        int at = v.indexOf('@');\n"
                + "        if (at <= 0) {\n"
                + "            return repeat(v.length());\n"
                + "        }\n"
                + "        String local = v.substring(0, at);\n"
                + "        String domain = v.substring(at);\n"
                + "        if (local.length() <= 2) {\n"
                + "            return local.charAt(0) + \"***\" + domain;\n"
                + "        }\n"
                + "        return local.substring(0, 2) + \"***\" + domain;\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * 姓名：张*、欧阳**（保留姓，名用 * 代替）\n"
                + "     */\n"
                + "    public static String name(String v) {\n"
                + "        if (v.length() <= 1) {\n"
                + "            return v;\n"
                + "        }\n"
                + "        return v.charAt(0) + repeat(v.length() - 1);\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * 银行卡：6222 **** **** 1234（保留前4后4）\n"
                + "     */\n"
                + "    public static String bankcard(String v) {\n"
                + "        String digits = v.replace(\" \", \"\");\n"
                + "        if (digits.length() < 9) {\n"
                + "            return repeat(digits.length());\n"
                + "        }\n"
                + "        return digits.substring(0, 4) + \" **** **** \" + digits.substring(digits.length() - 4);\n"
                + "    }\n"
                + "\n"
                + "    private static String repeat(int n) {\n"
                + "        if (n <= 0) {\n"
                + "            return \"\";\n"
                + "        }\n"
                + "        return \"*\".repeat(n);\n"
                + "    }\n"
                + "}\n"
                + "\n";
    }

    private String authContextFilterJava(String pkg) {
        return "package " + pkg + ".common.config;\n\n"
                + "import " + pkg + ".common.context.UserContext;\n"
                + "import " + pkg + ".common.result.Result;\n"
                + "import " + pkg + ".common.result.ResultCode;\n"
                + "import com.fasterxml.jackson.databind.ObjectMapper;\n"
                + "import jakarta.servlet.FilterChain;\n"
                + "import jakarta.servlet.ServletException;\n"
                + "import jakarta.servlet.http.HttpServletRequest;\n"
                + "import jakarta.servlet.http.HttpServletResponse;\n"
                + "import org.springframework.core.Ordered;\n"
                + "import org.springframework.core.annotation.Order;\n"
                + "import org.springframework.http.MediaType;\n"
                + "import org.springframework.stereotype.Component;\n"
                + "import org.springframework.util.StringUtils;\n"
                + "import org.springframework.web.filter.OncePerRequestFilter;\n\n"
                + "import java.io.IOException;\n"
                + "import java.nio.charset.StandardCharsets;\n"
                + "import java.util.List;\n\n"
                + "/**\n"
                + " * 登录上下文填充 + 鉴权拦截。\n"
                + " *\n"
                + " * <p>单体服务前面没有网关兜底，鉴权必须自己完成：白名单外的请求若无合法 token，直接 401。\n"
                + " * 校验通过则把用户写入 {@link UserContext}，供审计字段填充使用。\n"
                + " */\n"
                + "@Component\n"
                + "@Order(Ordered.HIGHEST_PRECEDENCE + 10)\n"
                + "public class AuthContextFilter extends OncePerRequestFilter {\n\n"
                + "    private static final String BEARER_PREFIX = \"Bearer \";\n\n"
                + "    /** 无需 token 即可访问的路径前缀 */\n"
                + "    private static final List<String> WHITE_LIST = List.of(\n"
                + "            \"/auth/login\",\n"
                + "            \"/health\",\n"
                + "            \"/actuator\",\n"
                + "            \"/v3/api-docs\",\n"
                + "            \"/swagger-ui\",\n"
                + "            \"/swagger-resources\",\n"
                + "            \"/error\"\n"
                + "    );\n\n"
                + "    private final ObjectMapper objectMapper = new ObjectMapper();\n\n"
                + "    @Override\n"
                + "    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,\n"
                + "                                    FilterChain filterChain) throws ServletException, IOException {\n"
                + "        // CORS 预检不带 Authorization，必须放行\n"
                + "        if (\"OPTIONS\".equalsIgnoreCase(request.getMethod())) {\n"
                + "            filterChain.doFilter(request, response);\n"
                + "            return;\n"
                + "        }\n\n"
                + "        String authorization = request.getHeader(\"Authorization\");\n"
                + "        boolean hasToken = StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX);\n\n"
                + "        if (hasToken) {\n"
                + "            String token = authorization.substring(BEARER_PREFIX.length());\n"
                + "            try {\n"
                + "                // 此处按项目实际的 token 解析方式替换；骨架先做「非空即放行 + 写入占位用户」\n"
                + "                UserContext.setUsername(\"token-user\");\n"
                + "            } catch (Exception e) {\n"
                + "                writeUnauthorized(response, \"登录状态已失效，请重新登录\");\n"
                + "                return;\n"
                + "            }\n"
                + "            try {\n"
                + "                filterChain.doFilter(request, response);\n"
                + "            } finally {\n"
                + "                UserContext.clear();\n"
                + "            }\n"
                + "            return;\n"
                + "        }\n\n"
                + "        if (isWhiteListed(request)) {\n"
                + "            filterChain.doFilter(request, response);\n"
                + "            return;\n"
                + "        }\n"
                + "        writeUnauthorized(response, \"未登录或缺少访问令牌\");\n"
                + "    }\n\n"
                + "    private boolean isWhiteListed(HttpServletRequest request) {\n"
                + "        String path = request.getRequestURI();\n"
                + "        String contextPath = request.getContextPath();\n"
                + "        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {\n"
                + "            path = path.substring(contextPath.length());\n"
                + "        }\n"
                + "        for (String prefix : WHITE_LIST) {\n"
                + "            if (path.equals(prefix) || path.startsWith(prefix + \"/\")) {\n"
                + "                return true;\n"
                + "            }\n"
                + "        }\n"
                + "        return false;\n"
                + "    }\n\n"
                + "    private void writeUnauthorized(HttpServletResponse response, String msg) throws IOException {\n"
                + "        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);\n"
                + "        response.setCharacterEncoding(StandardCharsets.UTF_8.name());\n"
                + "        response.setContentType(MediaType.APPLICATION_JSON_VALUE);\n"
                + "        Result<Void> body = Result.fail(ResultCode.UNAUTHORIZED.getCode(), msg);\n"
                + "        response.getWriter().write(objectMapper.writeValueAsString(body));\n"
                + "    }\n"
                + "}\n";
    }

    private String applicationYml(GenScaffoldDTO dto, String pkg, String project, int serverPort) {
        String db = StringUtils.hasText(dto.getDbName()) ? dto.getDbName() : project.replace('-', '_');
        String host = StringUtils.hasText(dto.getDbHost()) ? dto.getDbHost() : "127.0.0.1:3306";
        String user = StringUtils.hasText(dto.getDbUsername()) ? dto.getDbUsername() : "root";
        String pwd = StringUtils.hasText(dto.getDbPassword()) ? dto.getDbPassword() : "root";
        return "server:\n"
                + "  port: " + serverPort + "\n\n"
                + "spring:\n"
                + "  application:\n"
                + "    name: " + project + "-server\n"
                + "  profiles:\n"
                + "    # 本机私有配置（数据库密码等）放 application-local.yml，不提交仓库\n"
                + "    include: local\n"
                + "  datasource:\n"
                + "    url: jdbc:mysql://" + host + "/" + db
                + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true\n"
                + "    username: " + user + "\n"
                + "    password: " + pwd + "\n"
                + "    driver-class-name: com.mysql.cj.jdbc.Driver\n\n"
                + "mybatis-plus:\n"
                + "  mapper-locations: classpath*:/mapper/**/*.xml\n"
                + "  type-aliases-package: " + pkg + ".entity\n"
                + "  configuration:\n"
                + "    map-underscore-to-camel-case: true\n"
                + "  global-config:\n"
                + "    db-config:\n"
                + "      id-type: assign_id\n"
                + "      logic-delete-field: deleted\n"
                + "      logic-delete-value: 1\n"
                + "      logic-not-delete-value: 0\n\n"
                + "springdoc:\n"
                + "  api-docs:\n"
                + "    path: /v3/api-docs\n"
                + "  swagger-ui:\n"
                + "    path: /swagger-ui.html\n\n"
                + "logging:\n"
                + "  level:\n"
                + "    " + pkg + ": info\n";
    }

    private String applicationLocalExample(String pkg) {
        return "# 本机私有配置（复制为 application-local.yml 后填写，该文件不入库）\n"
                + "spring:\n"
                + "  datasource:\n"
                + "    password: 你的本机数据库密码\n\n"
                + "logging:\n"
                + "  level:\n"
                + "    " + pkg + ": debug\n";
    }

    private String backendGitignore() {
        return "target/\n"
                + "build/\n"
                + "*.jar\n"
                + "logs/\n"
                + "*.log\n"
                + ".idea/\n"
                + "*.iml\n"
                + ".vscode/\n"
                + ".DS_Store\n"
                + "# 本机私有配置\n"
                + "application-local.yml\n"
                + "# 生成器产物与备份\n"
                + ".gen-artifacts/\n"
                + ".gen-backup/\n"
                + ".gen-verify/\n";
    }

    private String backendReadme(String project, String pkg, String module, int serverPort) {
        return "# " + project + " 后端（" + module + "）\n\n"
                + "由 codegen-server 脚手架生成，已包含可运行的基座能力。\n\n"
                + "## 基座能力\n\n"
                + "| 能力 | 类 | 作用 |\n"
                + "|---|---|---|\n"
                + "| 统一返回体 | `common.result.Result` | `{code,msg,data}`，前端拦截器按 code 判断 |\n"
                + "| 全局异常 | `common.exception.GlobalExceptionHandler` | 业务异常/参数校验/兜底 |\n"
                + "| 分页插件 | `common.config.MybatisPlusConfig` | 缺它 `total` 恒为 0 |\n"
                + "| 雪花 ID | `common.config.LongIdJacksonConfig` | 19 位 Long 转字符串，防前端精度丢失 |\n"
                + "| 审计填充 | `common.handler.AuditMetaObjectHandler` | createBy/updateBy 取当前用户 |\n"
                + "| 登录上下文 | `common.context.UserContext` | ThreadLocal 存当前用户 |\n\n"
                + "## 快速开始\n\n"
                + "```bash\n"
                + "cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml\n"
                + "# 编辑 application-local.yml 填数据库密码\n"
                + "mvn -DskipTests package\n"
                + "java -jar target/" + module + ".jar\n"
                + "```\n\n"
                + "端口 `" + serverPort + "`，接口文档 `http://127.0.0.1:" + serverPort + "/swagger-ui.html`。\n\n"
                + "包名 `" + pkg + "`，生成物会落在 `" + pkg + ".entity / mapper / service / controller / dto / vo`。\n";
    }

    private String frontendPackageJson(String project, String module) {
        return "{\n"
                + "  \"name\": \"" + module + "\",\n"
                + "  \"version\": \"1.0.0\",\n"
                + "  \"private\": true,\n"
                + "  \"description\": \"" + project + " 前端（由 codegen-server 脚手架生成）\",\n"
                + "  \"scripts\": {\n"
                + "    \"serve\": \"vue-cli-service serve\",\n"
                + "    \"dev\": \"vue-cli-service serve\",\n"
                + "    \"build\": \"vue-cli-service build\"\n"
                + "  },\n"
                + "  \"dependencies\": {\n"
                + "    \"axios\": \"^1.6.8\",\n"
                + "    \"core-js\": \"^3.36.1\",\n"
                + "    \"element-ui\": \"^2.15.14\",\n"
                + "    \"vue\": \"^2.7.16\",\n"
                + "    \"vue-router\": \"^3.6.5\",\n"
                + "    \"vuex\": \"^3.6.2\"\n"
                + "  },\n"
                + "  \"devDependencies\": {\n"
                + "    \"@vue/cli-plugin-babel\": \"~5.0.8\",\n"
                + "    \"@vue/cli-service\": \"~5.0.8\",\n"
                + "    \"vue-template-compiler\": \"^2.7.16\"\n"
                + "  }\n"
                + "}\n";
    }

    private String frontendVueConfig(int webPort, int serverPort) {
        return "const { defineConfig } = require('@vue/cli-service')\n\n"
                + "module.exports = defineConfig({\n"
                + "  transpileDependencies: true,\n"
                + "  lintOnSave: false,\n"
                + "  devServer: {\n"
                + "    port: " + webPort + ",\n"
                + "    // 缺了它，前端 history 路由刷新页面会 404\n"
                + "    historyApiFallback: true,\n"
                + "    proxy: {\n"
                + "      '/dev-api': {\n"
                + "        target: 'http://127.0.0.1:" + serverPort + "',\n"
                + "        changeOrigin: true,\n"
                + "        pathRewrite: { '^/dev-api': '' }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "})\n";
    }

    private String frontendBabelConfig() {
        return "module.exports = {\n"
                + "  presets: [\n"
                + "    '@vue/cli-plugin-babel/preset'\n"
                + "  ]\n"
                + "}\n";
    }

    private String frontendEnvDev(int serverPort) {
        return "# 开发环境\n"
                + "ENV = 'development'\n\n"
                + "VUE_APP_BASE_API = '/dev-api'\n"
                + "VUE_APP_PROXY_TARGET = 'http://127.0.0.1:" + serverPort + "'\n";
    }

    private String frontendEnvProd() {
        return "# 生产环境\n"
                + "ENV = 'production'\n\n"
                + "VUE_APP_BASE_API = '/prod-api'\n";
    }

    private String frontendIndexHtml(String project) {
        return "<!DOCTYPE html>\n"
                + "<html lang=\"zh-CN\">\n"
                + "<head>\n"
                + "  <meta charset=\"utf-8\">\n"
                + "  <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">\n"
                + "  <title>" + project + "</title>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <noscript>请启用 JavaScript 后继续访问。</noscript>\n"
                + "  <div id=\"app\"></div>\n"
                + "</body>\n"
                + "</html>\n";
    }

    private String frontendMainJs() {
        return "import Vue from 'vue'\n"
                + "import ElementUI from 'element-ui'\n"
                + "import 'element-ui/lib/theme-chalk/index.css'\n"
                + "import App from './App.vue'\n"
                + "import router from './router'\n"
                + "import store from './store'\n\n"
                + "Vue.use(ElementUI, { size: 'small' })\n"
                + "Vue.config.productionTip = false\n\n"
                + "new Vue({\n"
                + "  router,\n"
                + "  store,\n"
                + "  render: h => h(App)\n"
                + "}).$mount('#app')\n";
    }

    private String frontendAppVue() {
        return "<template>\n"
                + "  <div id=\"app\">\n"
                + "    <router-view />\n"
                + "  </div>\n"
                + "</template>\n\n"
                + "<script>\n"
                + "export default {\n"
                + "  name: 'App'\n"
                + "}\n"
                + "</script>\n\n"
                + "<style>\n"
                + "html,\n"
                + "body,\n"
                + "#app {\n"
                + "  height: 100%;\n"
                + "  margin: 0;\n"
                + "  padding: 0;\n"
                + "}\n"
                + "</style>\n";
    }

    private String frontendRequestJs() {
        return "import axios from 'axios'\n"
                + "import { Message } from 'element-ui'\n\n"
                + "const service = axios.create({\n"
                + "  baseURL: process.env.VUE_APP_BASE_API,\n"
                + "  timeout: 15000\n"
                + "})\n\n"
                + "service.interceptors.request.use(\n"
                + "  config => {\n"
                + "    const token = localStorage.getItem('token')\n"
                + "    if (token) {\n"
                + "      config.headers['Authorization'] = 'Bearer ' + token\n"
                + "    }\n"
                + "    return config\n"
                + "  },\n"
                + "  error => Promise.reject(error)\n"
                + ")\n\n"
                + "service.interceptors.response.use(\n"
                + "  response => {\n"
                + "    if (response.config.responseType === 'blob') {\n"
                + "      return response\n"
                + "    }\n"
                + "    const res = response.data\n"
                + "    if (res.code !== 0 && res.code !== 200) {\n"
                + "      Message({ message: res.msg || '请求失败', type: 'error', duration: 5000 })\n"
                + "      return Promise.reject(new Error(res.msg || '请求失败'))\n"
                + "    }\n"
                + "    return res\n"
                + "  },\n"
                + "  error => {\n"
                + "    const resp = error.response\n"
                + "    if (resp && resp.status === 401) {\n"
                + "      localStorage.removeItem('token')\n"
                + "      window.location.href = '/#/login'\n"
                + "      return Promise.reject(new Error('登录状态已失效'))\n"
                + "    }\n"
                + "    Message({ message: (resp && resp.data && resp.data.msg) || error.message, type: 'error', duration: 5000 })\n"
                + "    return Promise.reject(error)\n"
                + "  }\n"
                + ")\n\n"
                + "// MyBatis-Plus Page {records,total} -> {list,total}\n"
                + "export function toPageData(res) {\n"
                + "  const page = res.data || {}\n"
                + "  return {\n"
                + "    code: res.code,\n"
                + "    msg: res.msg,\n"
                + "    data: { list: page.records || [], total: page.total || 0 }\n"
                + "  }\n"
                + "}\n\n"
                + "export default service\n";
    }

    private String frontendLayoutVue(String project) {
        return "<template>\n"
                + "  <el-container class=\"layout\">\n"
                + "    <el-aside width=\"200px\" class=\"aside\">\n"
                + "      <div class=\"logo\">" + project + "</div>\n"
                + "      <el-menu :default-active=\"$route.path\" router background-color=\"#1f2d50\"\n"
                + "               text-color=\"#c0c8dc\" active-text-color=\"#4d7cfe\">\n"
                + "        <el-menu-item index=\"/index\">\n"
                + "          <i class=\"el-icon-s-home\"></i><span>首页</span>\n"
                + "        </el-menu-item>\n"
                + "      </el-menu>\n"
                + "    </el-aside>\n"
                + "    <el-container>\n"
                + "      <el-header class=\"header\">{{ $route.meta.title || '' }}</el-header>\n"
                + "      <el-main>\n"
                + "        <router-view />\n"
                + "      </el-main>\n"
                + "    </el-container>\n"
                + "  </el-container>\n"
                + "</template>\n\n"
                + "<script>\n"
                + "export default {\n"
                + "  name: 'Layout'\n"
                + "}\n"
                + "</script>\n\n"
                + "<style scoped>\n"
                + ".layout {\n"
                + "  height: 100%;\n"
                + "}\n"
                + ".aside {\n"
                + "  background: #1f2d50;\n"
                + "}\n"
                + ".logo {\n"
                + "  height: 56px;\n"
                + "  line-height: 56px;\n"
                + "  text-align: center;\n"
                + "  color: #fff;\n"
                + "  font-weight: 600;\n"
                + "  letter-spacing: 1px;\n"
                + "}\n"
                + ".header {\n"
                + "  display: flex;\n"
                + "  align-items: center;\n"
                + "  background: #fff;\n"
                + "  border-bottom: 1px solid #eef1f7;\n"
                + "}\n"
                + "</style>\n";
    }

    private String frontendHomeVue(String project) {
        return "<template>\n"
                + "  <div class=\"home\">\n"
                + "    <el-card shadow=\"never\">\n"
                + "      <div slot=\"header\">" + project + " 已就绪</div>\n"
                + "      <p>这是一个由 codegen-server 生成的空工程。</p>\n"
                + "      <p>下一步：在平台的「代码生成」里导入表配置，生成页面后会自动落到 src/views 下。</p>\n"
                + "      <el-button type=\"primary\" size=\"small\" @click=\"ping\">测试后端连通</el-button>\n"
                + "      <span class=\"result\">{{ result }}</span>\n"
                + "    </el-card>\n"
                + "  </div>\n"
                + "</template>\n\n"
                + "<script>\n"
                + "import request from '@/utils/request'\n\n"
                + "export default {\n"
                + "  name: 'Home',\n"
                + "  data() {\n"
                + "    return { result: '' }\n"
                + "  },\n"
                + "  methods: {\n"
                + "    ping() {\n"
                + "      request({ url: '/health', method: 'get' })\n"
                + "        .then(res => { this.result = '连通正常：' + JSON.stringify(res.data) })\n"
                + "        .catch(() => { this.result = '请求失败，检查后端是否启动' })\n"
                + "    }\n"
                + "  }\n"
                + "}\n"
                + "</script>\n\n"
                + "<style scoped>\n"
                + ".home {\n"
                + "  padding: 8px;\n"
                + "}\n"
                + ".result {\n"
                + "  margin-left: 12px;\n"
                + "  color: #67c23a;\n"
                + "}\n"
                + "</style>\n";
    }

    private String frontendRouterJs() {
        return "import Vue from 'vue'\n"
                + "import VueRouter from 'vue-router'\n"
                + "import Layout from '@/layout/index.vue'\n\n"
                + "Vue.use(VueRouter)\n\n"
                + "const routes = [\n"
                + "  {\n"
                + "    path: '/',\n"
                + "    component: Layout,\n"
                + "    redirect: '/index',\n"
                + "    children: [\n"
                + "      {\n"
                + "        path: 'index',\n"
                + "        name: 'Home',\n"
                + "        component: () => import('@/views/index.vue'),\n"
                + "        meta: { title: '首页' }\n"
                + "      }\n"
                + "    ]\n"
                + "  }\n"
                + "]\n\n"
                + "const router = new VueRouter({\n"
                + "  routes\n"
                + "})\n\n"
                + "export default router\n";
    }

    private String frontendStoreJs() {
        return "import Vue from 'vue'\n"
                + "import Vuex from 'vuex'\n\n"
                + "Vue.use(Vuex)\n\n"
                + "export default new Vuex.Store({\n"
                + "  state: {\n"
                + "    user: null\n"
                + "  },\n"
                + "  mutations: {\n"
                + "    SET_USER(state, user) {\n"
                + "      state.user = user\n"
                + "    }\n"
                + "  }\n"
                + "})\n";
    }

    private String frontendGitignore() {
        return "node_modules/\n"
                + "dist/\n"
                + "logs/\n"
                + "*.log\n"
                + ".DS_Store\n"
                + ".idea/\n"
                + ".vscode/\n"
                + ".gen-artifacts/\n"
                + ".gen-verify/\n";
    }

    private String frontendReadme(String project, String module, int webPort, int serverPort) {
        return "# " + project + " 前端（" + module + "）\n\n"
                + "Vue 2.7 + Element UI，由 codegen-server 脚手架生成。\n\n"
                + "## 快速开始\n\n"
                + "```bash\n"
                + "npm install\n"
                + "npm run serve\n"
                + "```\n\n"
                + "访问 `http://127.0.0.1:" + webPort + "`，`/dev-api` 已代理到后端 `http://127.0.0.1:" + serverPort + "`。\n\n"
                + "## 已内置\n\n"
                + "- `vue.config.js` 的 `historyApiFallback: true`（否则刷新页面 404）\n"
                + "- `utils/request.js` 统一注入 token、按 `code` 判成败、401 回登录页\n"
                + "- `layout/index.vue` 侧边栏骨架\n";
    }

    @Override
    public GenScaffoldCheckVO check(String backendRoot, String frontendRoot, String rootPackage) {
        String pkg = StringUtils.hasText(rootPackage)
                ? rootPackage.trim() : outputProperties.getRootPackage();
        Path backend = StringUtils.hasText(backendRoot)
                ? Paths.get(backendRoot.trim()).toAbsolutePath().normalize()
                : (StringUtils.hasText(outputProperties.getBackendRoot())
                    ? Paths.get(outputProperties.getBackendRoot()).toAbsolutePath().normalize() : null);
        Path frontend = StringUtils.hasText(frontendRoot)
                ? Paths.get(frontendRoot.trim()).toAbsolutePath().normalize()
                : (StringUtils.hasText(outputProperties.getFrontendRoot())
                    ? Paths.get(outputProperties.getFrontendRoot()).toAbsolutePath().normalize() : null);

        GenScaffoldCheckVO vo = new GenScaffoldCheckVO();
        vo.setBackendPath(backend == null ? null : backend.toString());
        vo.setFrontendPath(frontend == null ? null : frontend.toString());
        List<GenScaffoldCheckVO.Item> items = vo.getItems();

        String pkgPath = "src/main/java/" + pkg.replace('.', '/');

        // ---------- 后端检查 ----------
        Path pom = backend == null ? null : findFirst(backend, "pom.xml", 3);
        items.add(fileItem("backend.pom", "后端 pom.xml", "backend", pom,
                "在后端工程根创建 pom.xml，或把脚手架生成为其子模块"));

        if (pom != null) {
            String pomText = readQuietly(pom);
            items.add(contentItem("backend.parameters", "pom 含 -parameters 编译参数", "backend",
                    pomText.contains("-parameters"),
                    "缺少时所有 @RequestParam/@PathVariable 接口运行时 500",
                    "在 maven-compiler-plugin 的 compilerArgs 加 <arg>-parameters</arg>"));
            items.add(contentItem("backend.paginationDep", "pom 含 mybatis-plus 分页依赖", "backend",
                    pomText.contains("mybatis-plus"),
                    "生成物 Service 继承 ServiceImpl，缺依赖无法编译",
                    "加 mybatis-plus-spring-boot3-starter（Boot3）或 mybatis-plus-boot-starter（Boot2）"));
            items.add(contentItem("backend.lombok", "pom 含 lombok", "backend",
                    pomText.contains("lombok"),
                    "生成物大量使用 @Data/@Slf4j",
                    "加 org.projectlombok:lombok 依赖"));
            items.add(contentItem("backend.springdoc", "pom 含 springdoc（OpenAPI）", "backend",
                    pomText.contains("springdoc"),
                    "生成物 Controller 带 @Tag/@Operation，缺依赖编译失败",
                    "加 org.springdoc:springdoc-openapi-starter-webmvc-ui"));
            items.add(contentItem("backend.poi", "pom 含 poi-ooxml（Excel 导入导出）", "backend",
                    pomText.contains("poi"),
                    "生成物的导入导出接口需要 POI",
                    "加 org.apache.poi:poi-ooxml"));
        }

        // 基座类逐项核对
        items.add(fileItem("backend.result", "统一返回体 Result", "backend",
                backend == null ? null : findIn(backend, pkgPath + "/common/result/Result.java"),
                "生成物 Controller 依赖它，缺了无法编译"));
        items.add(fileItem("backend.resultCode", "状态码枚举 ResultCode", "backend",
                backend == null ? null : findIn(backend, pkgPath + "/common/result/ResultCode.java"),
                "Result 的 SUCCESS/FAIL 来源"));
        items.add(fileItem("backend.baseEntity", "实体基类 BaseEntity", "backend",
                backend == null ? null : findIn(backend, pkgPath + "/common/entity/BaseEntity.java"),
                "生成物 entity 继承它（主键/审计/逻辑删除）"));
        items.add(fileItem("backend.globalEx", "全局异常处理器", "backend",
                backend == null ? null : findIn(backend, pkgPath + "/common/exception/GlobalExceptionHandler.java"),
                "缺了业务异常会以 500 堆栈形式暴露给前端"));
        items.add(fileItem("backend.bizEx", "业务异常 BusinessException", "backend",
                backend == null ? null : findIn(backend, pkgPath + "/common/exception/BusinessException.java"),
                "生成物 Service 用它抛可读错误"));
        items.add(fileItem("backend.userContext", "登录上下文 UserContext", "backend",
                backend == null ? null : findIn(backend, pkgPath + "/common/context/UserContext.java"),
                "审计字段填充与数据权限依赖它取当前用户"));
        items.add(fileItem("backend.pageQuery", "分页入参 PageQuery", "backend",
                backend == null ? null : findIn(backend, pkgPath + "/common/page/PageQuery.java"),
                "生成物的 PageDTO 继承它"));
        items.add(fileItem("backend.paginationCfg", "分页拦截器 MybatisPlusConfig", "backend",
                backend == null ? null : findIn(backend, pkgPath + "/common/config/MybatisPlusConfig.java"),
                "缺它 Page.total 恒为 0，前端分页器算不出总页数"));
        items.add(fileItem("backend.longId", "雪花 ID 序列化 LongIdJacksonConfig", "backend",
                backend == null ? null : findIn(backend, pkgPath + "/common/config/LongIdJacksonConfig.java"),
                "缺它 19 位 ID 传到前端会精度丢失，表现为详情为空/删除 404"));
        items.add(fileItem("backend.audit", "审计字段填充 AuditMetaObjectHandler", "backend",
                backend == null ? null : findIn(backend, pkgPath + "/common/handler/AuditMetaObjectHandler.java"),
                "createBy/updateBy 不会自动落库"));
        items.add(fileItem("backend.mask", "脱敏工具 MaskUtils", "backend",
                backend == null ? null : findIn(backend, pkgPath + "/common/util/MaskUtils.java"),
                "字段配了 mask_type 时，生成物 VO 的脱敏 getter 依赖它，缺了无法编译"));

        // application.yml
        Path appYml = backend == null ? null : findFirst(backend, "application.yml", 5);
        if (appYml == null && backend != null) {
            appYml = findFirst(backend, "application-dev.yml", 5);
        }
        boolean appOk = appYml != null;
        items.add(new GenScaffoldCheckVO.Item("backend.appYml", "配置文件 application.yml", "backend",
                appOk,
                appOk ? appYml.toString() : "未找到",
                "需要数据源、mybatis-plus 逻辑删除、mapper-locations 等配置"));

        // ---------- 前端检查 ----------
        if (frontend != null) {
            Path pkgJson = findFirst(frontend, "package.json", 3);
            items.add(fileItem("frontend.packageJson", "前端 package.json", "frontend", pkgJson,
                    "在前端工程根创建 package.json"));

            Path vueConfig = findFirst(frontend, "vue.config.js", 3);
            items.add(fileItem("frontend.vueConfig", "vue.config.js", "frontend", vueConfig,
                    "需要 devServer 端口与 /dev-api 代理"));
            if (vueConfig != null) {
                String text = readQuietly(vueConfig);
                items.add(contentItem("frontend.history", "historyApiFallback 已开启", "frontend",
                        text.contains("historyApiFallback"),
                        "缺它前端路由页刷新会 404",
                        "devServer 加 historyApiFallback: true"));
                items.add(contentItem("frontend.proxy", "已配置 /dev-api 代理", "frontend",
                        text.contains("proxy"),
                        "前端本地调试无法访问后端",
                        "devServer.proxy 把 /dev-api 指向后端端口"));
            }

            items.add(fileItem("frontend.request", "请求封装 request.js", "frontend",
                    findIn(frontend, "src/utils/request.js"),
                    "生成物页面统一 import request from '@/utils/request'"));

            // 生成物页面目录（不一定存在，仅提示）
            Path views = frontend.resolve("src/views");
            items.add(new GenScaffoldCheckVO.Item("frontend.views", "页面目录 src/views", "frontend",
                    Files.isDirectory(views),
                    Files.isDirectory(views) ? views.toString() : "不存在",
                    "生成物页面落到此目录，不存在时写盘会自动创建"));
        } else {
            items.add(new GenScaffoldCheckVO.Item("frontend.skip", "未指定前端工程，跳过前端检查",
                    "frontend", true, "-", "-"));
        }

        int passed = 0;
        int failed = 0;
        for (GenScaffoldCheckVO.Item it : items) {
            if (it.isPassed()) {
                passed++;
            } else {
                failed++;
            }
        }
        vo.setPassedCount(passed);
        vo.setFailedCount(failed);
        vo.setPassed(failed == 0);
        return vo;
    }

    private GenScaffoldCheckVO.Item fileItem(String key, String name, String side, Path found, String suggestion) {
        boolean ok = found != null;
        return new GenScaffoldCheckVO.Item(key, name, side, ok,
                ok ? found.toString() : "未找到",
                ok ? null : suggestion);
    }

    private GenScaffoldCheckVO.Item contentItem(String key, String name, String side,
                                                boolean ok, String failDetail, String suggestion) {
        return new GenScaffoldCheckVO.Item(key, name, side, ok, ok ? "已配置" : failDetail, ok ? null : suggestion);
    }

    private Path findIn(Path root, String relPath) {
        Path p = root.resolve(relPath);
        return Files.isRegularFile(p) ? p : null;
    }

    /**
     * 在目录下（限深度）找第一个指定文件名，兼容多模块工程（pom 在子模块里）
     */
    private Path findFirst(Path root, String fileName, int maxDepth) {
        if (!Files.isDirectory(root)) {
            return null;
        }
        try (var stream = Files.walk(root, maxDepth)) {
            return stream
                    .filter(p -> p.getFileName() != null && p.getFileName().toString().equals(fileName))
                    .filter(Files::isRegularFile)
                    // 跳过 node_modules / target，避免把依赖里的同名文件当成工程的
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return !s.contains("/node_modules/") && !s.contains("/target/") && !s.contains("/dist/");
                    })
                    .sorted()
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.warn("遍历目录失败: {}", root, e);
            return null;
        }
    }

    private String readQuietly(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("读取失败: {}", p, e);
            return "";
        }
    }
}

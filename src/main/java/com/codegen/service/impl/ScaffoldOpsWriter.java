package com.codegen.service.impl;

import com.codegen.common.exception.BusinessException;
import com.codegen.common.log.Ip2RegionSearcher;
import com.codegen.config.GenOutputProperties;
import com.codegen.config.I18nCatalog;
import com.codegen.config.LanguageConfig;
import com.codegen.dto.GenScaffoldDTO;
import com.codegen.service.GenScaffoldService;
import com.codegen.service.I18nMessageService;
import com.codegen.service.I18nTextRewriter;
import com.codegen.support.GenNaming;
import com.codegen.support.GenPathResolver;
import com.codegen.vo.GenScaffoldCheckVO;
import com.codegen.vo.GenScaffoldVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import static com.codegen.service.impl.ScaffoldTextSupport.loadScaffoldResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * V9.1 拆分自 {@link GenScaffoldServiceImpl}：Docker/CI/移动端/支付/文件上传等运维与外设产物字符串工厂。
 * 纯函数集合，无实例状态；产物内容与拆分前逐字节一致。
 */
final class ScaffoldOpsWriter {

    static String dockerfileText() {
        return "# V6.2 多阶段构建：maven 编译 + JRE 运行；基础镜像走 ARG 便于内网替换\n"
                + "ARG BUILD_IMAGE=maven:3.9-eclipse-temurin-21\n"
                + "ARG RUN_IMAGE=eclipse-temurin:21-jre\n\n"
                + "FROM ${BUILD_IMAGE} AS build\n"
                + "WORKDIR /src\n"
                + "COPY pom.xml .\n"
                + "COPY src ./src\n"
                + "RUN mvn -q package -DskipTests\n\n"
                + "FROM ${RUN_IMAGE}\n"
                + "WORKDIR /app\n"
                + "# 非 root 运行\n"
                + "RUN useradd -r -u 1001 appuser\n"
                + "USER appuser\n"
                + "COPY --from=build /src/target/*.jar app.jar\n"
                + "ENV JAVA_OPTS=\"\"\n"
                + "EXPOSE 8080\n"
                + "ENTRYPOINT [\"sh\", \"-c\", \"java $JAVA_OPTS -jar app.jar --server.port=${SERVER_PORT:-8080}\"]\n";
    }

    static String dockerignoreText() {
        return "target/\nlogs/\ndb/\ndeploy/\n*.log\n.idea/\n*.iml\n";
    }

    static String dockerComposeYml(String project, int serverPort, boolean micro) {
        String svc = "services:\n"
                + "  mysql:\n"
                + "    image: mysql:8.0\n"
                + "    environment:\n"
                + "      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD:-root}\n"
                + "      MYSQL_DATABASE: " + project.replace('-', '_') + "\n"
                + "    volumes:\n"
                + "      - mysql-data:/var/lib/mysql\n"
                + "    healthcheck:\n"
                + "      test: [\"CMD\", \"mysqladmin\", \"ping\", \"-h\", \"localhost\"]\n"
                + "      interval: 5s\n"
                + "      retries: 20\n\n"
                + "  app:\n"
                + "    build: .\n"
                + "    ports:\n"
                + "      - \"" + serverPort + ":" + serverPort + "\"\n"
                + "    environment:\n"
                + "      SERVER_PORT: \"" + serverPort + "\"\n"
                + "      DB_URL: jdbc:mysql://mysql:3306/" + project.replace('-', '_')
                + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true\n"
                + "      DB_USERNAME: root\n"
                + "      DB_PASSWORD: ${DB_PASSWORD:-root}\n"
                + "    depends_on:\n"
                + "      mysql:\n"
                + "        condition: service_healthy\n";
        String microSvc = micro
                ? "\n# V6.0 micro：启用网关注释本段（gateway 端口=服务端口+1，路由见其 application.yml）\n"
                + "#  gateway:\n"
                + "#    build: ../gateway 相对路径按实际模块调整\n"
                + "#    ports:\n"
                + "#      - \"" + (serverPort + 1) + ":" + (serverPort + 1) + "\"\n"
                + "#    environment:\n"
                + "#      SPRING_CLOUD_NACOS_DISCOVERY_ENABLED: \"true\"\n"
                + "#  nacos:\n"
                + "#    image: nacos/nacos-server:v2.3.2\n"
                + "#    environment:\n"
                + "#      MODE: standalone\n" : "";
        return svc + microSvc + "\nvolumes:\n  mysql-data:\n";
    }

    static String ciGithubActionsText() {
        return "# 改名为 ci-github-actions.yml 放 .github/workflows/ 即激活（V6.2 部署物）\n"
                + "name: build\n"
                + "on: [push]\n"
                + "jobs:\n"
                + "  backend:\n"
                + "    runs-on: ubuntu-latest\n"
                + "    steps:\n"
                + "      - uses: actions/checkout@v4\n"
                + "      - uses: actions/setup-java@v4\n"
                + "        with: { distribution: temurin, java-version: '21' }\n"
                + "      - run: mvn -B package -DskipTests\n";
    }

    static String jenkinsfileText() {
        return "// 改名为 Jenkinsfile 放仓库根即激活（V6.2 部署物）\n"
                + "pipeline {\n"
                + "  agent any\n"
                + "  stages {\n"
                + "    stage('Build') { steps { sh 'mvn -B package -DskipTests' } }\n"
                + "  }\n"
                + "}\n";
    }

    /**
     * V6.3 支付模块产物源码：读 classpath 资源 scaffold-pay/<file>，__PKG__ 占位替换为根包名。
     * 支付模块约 800 行，走转义字符串块不可维护（V6.1 flow 基座规模尚可，V6.3 起改资源文件模式）。
     */

    /**
     * V6.3 支付模块产物源码：读 classpath 资源 scaffold-pay/<file>，__PKG__ 占位替换为根包名。
     * 支付模块约 800 行，走转义字符串块不可维护（V6.1 flow 基座规模尚可，V6.3 起改资源文件模式）。
     */
    static String payResource(String file, String pkg) {
        try {
            org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource("scaffold-pay/" + file);
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return content.replace("__PKG__", pkg);
        } catch (java.io.IOException e) {
            throw new com.codegen.common.exception.BusinessException(500, "支付模块源码资源缺失：scaffold-pay/" + file);
        }
    }

    /** V6.6 文件能力包：源码资源读取（V6.3 payResource 同款模式，__PKG__ 占位替换） */

    /** V6.6 文件能力包：源码资源读取（V6.3 payResource 同款模式，__PKG__ 占位替换） */
    static String fileResource(String file, String pkg) {
        try {
            org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource("scaffold-file/" + file);
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return content.replace("__PKG__", pkg);
        } catch (java.io.IOException e) {
            throw new com.codegen.common.exception.BusinessException(500, "文件模块源码资源缺失：scaffold-file/" + file);
        }
    }

    /**
     * V6.7 产物测试出厂：读 scaffold-test/ 资源（fileResource 同款；__PKG__ 占位替换）。
     * 每次改 scaffold-test/ 后必须重打包平台 jar 再 scaffold（V6.6 实测坑：平台读的是 jar 内资源）。
     */

    /**
     * V6.7 产物测试出厂：读 scaffold-test/ 资源（fileResource 同款；__PKG__ 占位替换）。
     * 每次改 scaffold-test/ 后必须重打包平台 jar 再 scaffold（V6.6 实测坑：平台读的是 jar 内资源）。
     */
    static String testResource(String file, String pkg) {
        try {
            org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource("scaffold-test/" + file);
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return content.replace("__PKG__", pkg);
        } catch (java.io.IOException e) {
            throw new com.codegen.common.exception.BusinessException(500, "测试骨架资源缺失：scaffold-test/" + file);
        }
    }

    /**
     * V6.4：文件基座端点映射——上传/下载登录即可；文件管理（列表/删除）收紧为权限点。
     *
     * <p>page/delete 若也放「登录即可」，任意登录用户就能翻全量文件元数据、删他人文件记录（IDOR）。
     * 权限点 file:list / file:delete 默认不在任何菜单下 → 只有超管（*:*:*）直通，
     * 需要开放给普通用户时在 sys_menu 挂按钮权限授予角色即可（与业务表 ApiPerms 同一套路）。
     */

    /**
     * V6.4：文件基座端点映射——上传/下载登录即可；文件管理（列表/删除）收紧为权限点。
     *
     * <p>page/delete 若也放「登录即可」，任意登录用户就能翻全量文件元数据、删他人文件记录（IDOR）。
     * 权限点 file:list / file:delete 默认不在任何菜单下 → 只有超管（*:*:*）直通，
     * 需要开放给普通用户时在 sys_menu 挂按钮权限授予角色即可（与业务表 ApiPerms 同一套路）。
     */
    static String fileApiPermsJava(String pkg) {
        return "package " + pkg + ".common.auth;\n\n"
                + "import org.springframework.stereotype.Component;\n\n"
                + "import java.util.HashMap;\n"
                + "import java.util.Map;\n\n"
                + "/**\n"
                + " * 文件基座端点映射：上传/下载是平台内置能力，登录即可操作；\n"
                + " * 列表、删除是管理动作，收为权限点 file:list / file:delete（未授权即 403，超管直通）。\n"
                + " */\n"
                + "@Component\n"
                + "public class FileApiPerms implements ApiPermContributor {\n\n"
                + "    @Override\n"
                + "    public Map<String, String> endpointPerms() {\n"
                + "        Map<String, String> m = new HashMap<>();\n"
                + "        m.put(\"POST /file/upload\", \"\");\n"
                + "        m.put(\"GET /file/download/{id}\", \"\");\n"
                + "        m.put(\"POST /file/page\", \"file:list\");\n"
                + "        m.put(\"DELETE /file/{id}\", \"file:delete\");\n"
                + "        return m;\n"
                + "    }\n"
                + "}\n";
    }

    // ============================ V6.5 移动端工程物料 ============================

    /** mobile 工程骨架文件清单：显式枚举（classpath 资源无法可靠列目录，V6.3 scaffold-pay 同款取舍）。
     * 注意 .gitignore 不走资源：dotfile 会被 jar 打包漏掉（S8 实测），内容内联在 mobileGitignore() */

    /** mobile 工程骨架文件清单：显式枚举（classpath 资源无法可靠列目录，V6.3 scaffold-pay 同款取舍）。
     * 注意 .gitignore 不走资源：dotfile 会被 jar 打包漏掉（S8 实测），内容内联在 mobileGitignore() */
    static final List<String> MOBILE_FILES = List.of(
            "package.json", "vite.config.js", "index.html", "README.md",
            "src/main.js", "src/App.vue", "src/manifest.json", "src/pages.json", "src/uni.scss",
            "src/utils/request.js", "src/utils/tables.js", "src/store/user.js",
            "src/pages/login/login.vue", "src/pages/index/index.vue",
            "src/pages/dynamic/list.vue", "src/pages/dynamic/detail.vue", "src/pages/dynamic/form.vue",
            "src/pages/todo/todo.vue", "src/pages/mine/mine.vue");

    /**
     * 兼容 V7.4 及之前的三参签名（含通过反射调用该方法的基线测试）。
     * <p>V7.5 增加了语言参数，但旧入口必须保留：无语言配置时回落 {@code zh-CN/ltr}，
     * 保证存量移动端产物与改造前逐字节一致。
     */

    /**
     * 兼容 V7.4 及之前的三参签名（含通过反射调用该方法的基线测试）。
     * <p>V7.5 增加了语言参数，但旧入口必须保留：无语言配置时回落 {@code zh-CN/ltr}，
     * 保证存量移动端产物与改造前逐字节一致。
     */
    static Map<String, String> mobileResources(String project, int apiPort, String gwPrefix) {
        return mobileResources(project, apiPort, gwPrefix,
                com.codegen.config.LanguageConfig.DEFAULT_LANGUAGE, "ltr");
    }

    static Map<String, String> mobileResources(String project, int apiPort, String gwPrefix,
                                                String htmlLang, String htmlDir) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (String f : MOBILE_FILES) {
            out.put(f, mobileResource(f, project, apiPort, gwPrefix, htmlLang, htmlDir));
        }
        out.put(".gitignore", mobileGitignore());
        return out;
    }

    static String mobileGitignore() {
        return "node_modules/\ndist/\n.hbuilderx/\n.DS_Store\n*.local\n";
    }

    /** 读 classpath scaffold-mobile/&lt;file&gt;，__PROJECT__/__API_PORT__/__GW__ 占位替换 */

    /** 读 classpath scaffold-mobile/&lt;file&gt;，__PROJECT__/__API_PORT__/__GW__ 占位替换 */
    static String mobileResource(String file, String project, int apiPort, String gwPrefix,
                                  String htmlLang, String htmlDir) {
        try {
            org.springframework.core.io.ClassPathResource resource =
                    new org.springframework.core.io.ClassPathResource("scaffold-mobile/" + file);
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return content.replace("__PROJECT__", project)
                    .replace("__API_PORT__", String.valueOf(apiPort))
                    .replace("__GW__", gwPrefix == null ? "" : gwPrefix)
                    .replace("__HTML_LANG__", htmlLang)
                    .replace("__HTML_DIR_ATTR__", "rtl".equals(htmlDir) ? " dir=\"rtl\"" : "");
        } catch (java.io.IOException e) {
            throw new com.codegen.common.exception.BusinessException(500, "移动端工程资源缺失：scaffold-mobile/" + file);
        }
    }

    // ============================ 模板内容 ============================
}

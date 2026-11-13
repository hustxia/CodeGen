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
 * V9.1 拆分自 {@link GenScaffoldServiceImpl}：pom 依赖、Spring Boot 启动类、application*.yml 与后端 README 产物字符串工厂。
 * 纯函数集合，无实例状态；产物内容与拆分前逐字节一致。
 */
final class ScaffoldPomWriter {

    static String pomXml(GenScaffoldDTO dto, String appName, String project, String pkg, String module, int serverPort,
                          boolean activiti, boolean payEnabled, boolean testEnabled) {
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
        // V6.1 Activiti 8 制品不在 Maven 中央仓库，产物 pom 自带 Alfresco 仓库声明
        String activitiRepos = activiti
                ? "    <repositories>\n"
                + "        <repository>\n"
                + "            <id>alfresco</id>\n"
                + "            <url>https://artifacts.alfresco.com/nexus/content/repositories/public/</url>\n"
                + "            <releases><enabled>true</enabled></releases>\n"
                + "            <snapshots><enabled>false</enabled></snapshots>\n"
                + "        </repository>\n"
                + "    </repositories>\n\n" : "";
        String activitiDep = activiti
                ? dep("org.activiti", "activiti-spring-boot-starter", "8.7.0") : "";
        // V6.3 支付 SDK：两渠道均中央仓库直供（无 V6.1 坑 43b 的私仓问题），版本直接内联
        String payDeps = payEnabled
                ? dep("com.github.wechatpay-apiv3", "wechatpay-java", "0.2.14")
                + dep("com.alipay.sdk", "alipay-sdk-java", "4.38.200.ALL") : "";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
                + "    <modelVersion>4.0.0</modelVersion>\n\n"
                + activitiRepos
                + "    <groupId>" + pkg + "</groupId>\n"
                + "    <artifactId>" + module + "</artifactId>\n"
                + "    <version>1.0.0</version>\n"
                + "    <packaging>jar</packaging>\n"
                + "    <name>" + module + "</name>\n"
                + "    <description>" + appName + " 后端服务（由 codegen-server 脚手架生成）</description>\n\n"
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
                + activitiDep
                + payDeps
                + dep("org.springframework.boot", "spring-boot-starter-web", null)
                + dep("org.springframework.boot", "spring-boot-starter-validation", null)
                + "        <!-- 基座 LogAspect（操作日志切面）依赖 AspectJ，缺了编译直接失败 -->\n"
                + dep("org.springframework.boot", "spring-boot-starter-aop", null)
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
                + "        <!-- V6.4 密码 BCrypt：仅 crypto 单模块，不引 Spring Security 全家桶 -->\n"
                + dep("org.springframework.security", "spring-security-crypto", "6.2.4")
                + "        <!-- 生成物 Controller 的 Excel 导入导出 -->\n"
                + dep("org.apache.poi", "poi-ooxml", "5.2.5")
                + extras
                // V6.7 测试骨架：均 test scope，不进产物运行时 classpath
                + (testEnabled
                    ? dep("org.springframework.boot", "spring-boot-starter-test", null)
                        + dep("com.h2database", "h2", null)
                    : "")
                + "    </dependencies>\n\n"
                + "    <build>\n"
                + "        <finalName>${project.artifactId}</finalName>\n"
                + "        <plugins>\n"
                // V6.7 spike 实测：本 POM 不继承 starter-parent，surefire 落到 Maven 默认 2.12.4
                // 不识别 JUnit 5，mvn test 静默跑 0 个用例——显式钉到 3.1.2（testEnabled 时才需要）
                + (testEnabled
                    ? "            <plugin>\n"
                    + "                <groupId>org.apache.maven.plugins</groupId>\n"
                    + "                <artifactId>maven-surefire-plugin</artifactId>\n"
                    + "                <version>3.1.2</version>\n"
                    + "            </plugin>\n"
                    : "")
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

    static String dep(String g, String a, String version) {
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

    /**
     * V5.5 多模块：内部模块依赖坐标（{@code <rootPackage-stem>} groupId + {@code <stem>-<module>}）。
     * 版本由聚合 pom 的 dependencyManagement 统一管，子模块不写 version。
     */

    /**
     * V5.5 多模块：内部模块依赖坐标（{@code <rootPackage-stem>} groupId + {@code <stem>-<module>}）。
     * 版本由聚合 pom 的 dependencyManagement 统一管，子模块不写 version。
     */
    static String internalDep(String pkg, String stem, String module) {
        return dep(pkg, stem + "-" + module, null);
    }

    static String extraDepsXml(GenScaffoldDTO dto) {
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
        return extras.toString();
    }

    /**
     * V5.5 多模块聚合 pom：packaging=pom + modules + 版本管理（spring-boot BOM + 内部模块 + 编译插件管理）。
     *
     * <p>-parameters 编译参数放 pluginManagement：子模块 build 引用即继承，
     * 单模块 pom 那段「缺 -parameters 全部带参接口 500」的教训在多模块同样成立。
     *
     * <p>V6.0 micro=true：追加 spring-cloud / spring-cloud-alibaba BOM（版本矩阵复用 shop 实测：
     * Boot 3.2.5 + cloud 2023.0.1 + alibaba 2023.0.1.0）与 gateway 模块条目。
     */

    /**
     * V5.5 多模块聚合 pom：packaging=pom + modules + 版本管理（spring-boot BOM + 内部模块 + 编译插件管理）。
     *
     * <p>-parameters 编译参数放 pluginManagement：子模块 build 引用即继承，
     * 单模块 pom 那段「缺 -parameters 全部带参接口 500」的教训在多模块同样成立。
     *
     * <p>V6.0 micro=true：追加 spring-cloud / spring-cloud-alibaba BOM（版本矩阵复用 shop 实测：
     * Boot 3.2.5 + cloud 2023.0.1 + alibaba 2023.0.1.0）与 gateway 模块条目。
     */
    static String pomMultiParent(String appName, String project, String pkg, String stem, String backendModule, boolean micro, boolean activiti, boolean payEnabled) {
        String gatewayModule = micro
                ? "        <module>" + stem + "-gateway</module>\n" : "";
        // V6.1 Activiti 8 制品不在 Maven 中央仓库（7.1.x 起），产物 pom 必须自带 Alfresco 仓库声明
        String activitiProps = activiti
                ? "        <activiti.version>8.7.0</activiti.version>\n" : "";
        String activitiRepos = activiti
                ? "\n    <repositories>\n"
                + "        <repository>\n"
                + "            <id>alfresco</id>\n"
                + "            <url>https://artifacts.alfresco.com/nexus/content/repositories/public/</url>\n"
                + "            <releases><enabled>true</enabled></releases>\n"
                + "            <snapshots><enabled>false</enabled></snapshots>\n"
                + "        </repository>\n"
                + "    </repositories>\n" : "";
        String cloudProps = micro
                ? "        <spring-cloud.version>2023.0.1</spring-cloud.version>\n"
                + "        <spring-cloud-alibaba.version>2023.0.1.0</spring-cloud-alibaba.version>\n" : "";
        // V6.3 支付 SDK 版本属性（BOM 统一管理；两 SDK 均中央仓库直供）
        String payProps = payEnabled
                ? "        <wechatpay.version>0.2.14</wechatpay.version>\n"
                + "        <alipay.version>4.38.200.ALL</alipay.version>\n" : "";
        String cloudBoms = micro
                ? "            <dependency>\n"
                + "                <groupId>org.springframework.cloud</groupId>\n"
                + "                <artifactId>spring-cloud-dependencies</artifactId>\n"
                + "                <version>${spring-cloud.version}</version>\n"
                + "                <type>pom</type>\n"
                + "                <scope>import</scope>\n"
                + "            </dependency>\n"
                + "            <dependency>\n"
                + "                <groupId>com.alibaba.cloud</groupId>\n"
                + "                <artifactId>spring-cloud-alibaba-dependencies</artifactId>\n"
                + "                <version>${spring-cloud-alibaba.version}</version>\n"
                + "                <type>pom</type>\n"
                + "                <scope>import</scope>\n"
                + "            </dependency>\n" : "";
        String description = micro
                ? appName + " 后端服务聚合工程（由 codegen-server 脚手架生成，微服务设施：Nacos/Gateway/Feign 默认关闭）"
                : appName + " 后端服务聚合工程（由 codegen-server 脚手架生成，微服务就绪单体）";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
                + "    <modelVersion>4.0.0</modelVersion>\n\n"
                + activitiRepos
                + "    <groupId>" + pkg + "</groupId>\n"
                + "    <artifactId>" + stem + "-parent</artifactId>\n"
                + "    <version>1.0.0</version>\n"
                + "    <packaging>pom</packaging>\n"
                + "    <name>" + backendModule + "-parent</name>\n"
                + "    <description>" + description + "</description>\n\n"
                + "    <modules>\n"
                + "        <module>" + stem + "-common</module>\n"
                + "        <module>" + stem + "-domain</module>\n"
                + "        <module>" + stem + "-service</module>\n"
                + "        <module>" + stem + "-bootstrap</module>\n"
                + gatewayModule
                + "    </modules>\n\n"
                + "    <properties>\n"
                + "        <java.version>21</java.version>\n"
                + "        <maven.compiler.source>21</maven.compiler.source>\n"
                + "        <maven.compiler.target>21</maven.compiler.target>\n"
                + "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n"
                + "        <spring-boot.version>3.2.5</spring-boot.version>\n"
                + "        <mybatis-plus.version>3.5.5</mybatis-plus.version>\n"
                + "        <lombok.version>1.18.30</lombok.version>\n"
                + cloudProps
                + payProps
                + activitiProps
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
                + cloudBoms
                + "            <dependency>\n"
                + "                <groupId>" + pkg + "</groupId>\n"
                + "                <artifactId>" + stem + "-common</artifactId>\n"
                + "                <version>${project.version}</version>\n"
                + "            </dependency>\n"
                + "            <dependency>\n"
                + "                <groupId>" + pkg + "</groupId>\n"
                + "                <artifactId>" + stem + "-domain</artifactId>\n"
                + "                <version>${project.version}</version>\n"
                + "            </dependency>\n"
                + "            <dependency>\n"
                + "                <groupId>" + pkg + "</groupId>\n"
                + "                <artifactId>" + stem + "-service</artifactId>\n"
                + "                <version>${project.version}</version>\n"
                + "            </dependency>\n"
                + "        </dependencies>\n"
                + "    </dependencyManagement>\n\n"
                + "    <build>\n"
                + "        <pluginManagement>\n"
                + "            <plugins>\n"
                + "                <!-- 必须保留 -parameters：Spring MVC 靠参数名绑定 @RequestParam/@PathVariable。\n"
                + "                     缺了会运行时 \"Name for argument of type [X] not specified\" —— 所有带参接口 500 -->\n"
                + "                <plugin>\n"
                + "                    <groupId>org.apache.maven.plugins</groupId>\n"
                + "                    <artifactId>maven-compiler-plugin</artifactId>\n"
                + "                    <version>3.13.0</version>\n"
                + "                    <configuration>\n"
                + "                        <compilerArgs>\n"
                + "                            <arg>-parameters</arg>\n"
                + "                        </compilerArgs>\n"
                + "                        <annotationProcessorPaths>\n"
                + "                            <path>\n"
                + "                                <groupId>org.projectlombok</groupId>\n"
                + "                                <artifactId>lombok</artifactId>\n"
                + "                                <version>${lombok.version}</version>\n"
                + "                            </path>\n"
                + "                        </annotationProcessorPaths>\n"
                + "                    </configuration>\n"
                + "                </plugin>\n"
                + "            </plugins>\n"
                + "        </pluginManagement>\n"
                + "    </build>\n"
                + "</project>\n";
    }

    /**
     * V5.5 多模块子模块 pom：parent 指聚合（parentArtifactId 必须与聚合 pom artifactId 一致）；
     * 依赖由调用方按模块职责传入；isBootApp=true 时挂 spring-boot-maven-plugin（只有 bootstrap 可执行打包）。
     */

    /**
     * V5.5 多模块子模块 pom：parent 指聚合（parentArtifactId 必须与聚合 pom artifactId 一致）；
     * 依赖由调用方按模块职责传入；isBootApp=true 时挂 spring-boot-maven-plugin（只有 bootstrap 可执行打包）。
     */
    static String pomMultiModule(String parentGroupId, String parentArtifactId, String stem, String module,
                                  String dependenciesXml, boolean isBootApp) {
        String bootPlugin = isBootApp
                ? "            <plugin>\n"
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
                : "";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
                + "    <modelVersion>4.0.0</modelVersion>\n\n"
                + "    <parent>\n"
                + "        <groupId>" + parentGroupId + "</groupId>\n"
                + "        <artifactId>" + parentArtifactId + "</artifactId>\n"
                + "        <version>1.0.0</version>\n"
                + "    </parent>\n"
                + "    <artifactId>" + stem + "-" + module + "</artifactId>\n"
                + "    <packaging>jar</packaging>\n"
                + "    <name>" + stem + "-" + module + "</name>\n\n"
                + "    <dependencies>\n"
                + dependenciesXml
                + "    </dependencies>\n\n"
                + "    <build>\n"
                + "        <finalName>${project.artifactId}</finalName>\n"
                + "        <plugins>\n"
                + "            <plugin>\n"
                + "                <groupId>org.apache.maven.plugins</groupId>\n"
                + "                <artifactId>maven-compiler-plugin</artifactId>\n"
                + "            </plugin>\n"
                + bootPlugin
                + "        </plugins>\n"
                + "    </build>\n"
                + "</project>\n";
    }

    static String applicationJava(String pkg, String appClass, boolean micro, boolean activiti,
                                   boolean payEnabled, boolean fileEnabled) {
        String feign = micro
                ? "\n"
                + "// V6.0 微服务设施：跨服务调用骨架，扫描 .remote 包；nacos 关闭时 Feign 走 url 直连\n"
                + "@EnableFeignClients(basePackages = {\"" + pkg + ".remote\"})\n"
                : "";
        String feignImport = micro
                ? "import org.springframework.cloud.openfeign.EnableFeignClients;\n" : "";
        // V6.1：Activiti starter 自带 Method Security 集成，与产物 AuthContextFilter token 体系必然打架，
        // 启动类直接排除（8.x 实际类名；exclude 属性编译期解析 Class，类名错产物直接编不过——实测踩过）
        String excludeAttr = activiti
                ? "(exclude = org.activiti.spring.boot.ActivitiMethodSecurityAutoConfiguration.class)" : "";
        // V6.3：支付模块 mapper 落 common/pay，随能力包开关进扫描范围。
        // markerInterface=BaseMapper 是硬约束：common/pay 里还有渠道抽象接口 PaymentService（非 Mapper），
        // 不加过滤会被 MyBatis 注册成 mapper 代理，注入时抢赢真正的渠道实现 bean，
        // 运行期报 Invalid bound statement: PaymentService.create（V6.3 冒烟实测踩坑，坑库 #44）
        String mapperScanBase = "basePackages = {\"" + pkg + ".mapper\", \"" + pkg + ".system\""
                + (payEnabled ? ", \"" + pkg + ".common.pay\"" : "")
                + (fileEnabled ? ", \"" + pkg + ".common.file\"" : "") + "}";
        String mapperScanAttr = "@MapperScan(markerInterface = com.baomidou.mybatisplus.core.mapper.BaseMapper.class, "
                + mapperScanBase + ")";
        return "package " + pkg + ";\n\n"
                + "import org.mybatis.spring.annotation.MapperScan;\n"
                + "import org.springframework.boot.SpringApplication;\n"
                + "import org.springframework.boot.autoconfigure.SpringBootApplication;\n"
                + feignImport
                + "\n@SpringBootApplication" + excludeAttr + "\n"
                + feign
                + "// 基座 OpeLogMapper 在 .system 包下，业务 mapper 在 .mapper 包下，两个都要扫到\n"
                + mapperScanAttr + "\n"
                + "public class " + appClass + "Application {\n\n"
                + "    public static void main(String[] args) {\n"
                + "        SpringApplication.run(" + appClass + "Application.class, args);\n"
                + "    }\n"
                + "}\n";
    }

    /**
     * V6.0 微服务设施：gateway 模块启动类（webflux，包名 <pkg>.gateway）。
     */

    /**
     * V6.0 微服务设施：gateway 模块启动类（webflux，包名 <pkg>.gateway）。
     */
    static String gatewayApplicationJava(String pkg, String appClass) {
        return "package " + pkg + ".gateway;\n\n"
                + "import org.springframework.boot.SpringApplication;\n"
                + "import org.springframework.boot.autoconfigure.SpringBootApplication;\n\n"
                + "/**\n"
                + " * 统一入口网关：路由/跨域在本层，鉴权透传不解析（token 头原样转发，各业务服务自校验）。\n"
                + " * nacos.discovery.enabled=false 时直连本机业务服务，与多模块单体行为一致。\n"
                + " */\n"
                + "@SpringBootApplication\n"
                + "public class " + appClass + "GatewayApplication {\n\n"
                + "    public static void main(String[] args) {\n"
                + "        SpringApplication.run(" + appClass + "GatewayApplication.class, args);\n"
                + "    }\n"
                + "}\n";
    }

    /**
     * V6.0 微服务设施：gateway 路由配置。
     *
     * <p>M1 诚实边界：scaffold 时业务模块名还未知，且业务服务只有 <stem>-bootstrap 一个，
     * 所以先放 catch-all（Path=/**）+ 注释态的按模块聚合示例——真拆出第二个服务再细分。
     */

    /**
     * V6.0 微服务设施：gateway 路由配置。
     *
     * <p>M1 诚实边界：scaffold 时业务模块名还未知，且业务服务只有 <stem>-bootstrap 一个，
     * 所以先放 catch-all（Path=/**）+ 注释态的按模块聚合示例——真拆出第二个服务再细分。
     */
    static String gatewayApplicationYml(String pkg, String stem, int serverPort) {
        return "server:\n"
                + "  port: " + (serverPort + 1) + "   # 服务端口 + 1\n\n"
                + "spring:\n"
                + "  application:\n"
                + "    name: " + stem + "-gateway\n"
                + "  cloud:\n"
                + "    nacos:\n"
                + "      discovery:\n"
                + "        # 默认关：直连本机业务服务（两栖运行）；启用注册中心改 true 并先启动 Nacos\n"
                + "        enabled: false\n"
                + "        server-addr: 127.0.0.1:8848\n"
                + "      config:\n"
                + "        enabled: false\n"
                + "        server-addr: 127.0.0.1:8848\n"
                + "    gateway:\n"
                + "      globalcors:\n"
                + "        cors-configurations:\n"
                + "          '[/**]':\n"
                + "            allowedOriginPatterns: \"*\"\n"
                + "            allowedMethods: \"*\"\n"
                + "            allowedHeaders: \"*\"\n"
                + "      routes:\n"
                + "        # catch-all：nacos 关闭直连；开启后把 uri 改成 lb://" + stem + "-bootstrap\n"
                + "        - id: business\n"
                + "          uri: http://localhost:" + serverPort + "\n"
                + "          predicates:\n"
                + "            - Path=/**\n"
                + "        # 拆出第二个服务后按业务模块聚合（apiBase = /{module}）：\n"
                + "        # - id: <module>\n"
                + "        #   uri: lb://<stem>-<service>\n"
                + "        #   predicates:\n"
                + "        #     - Path=/{module}/**\n";
    }

    /**
     * V6.0 微服务设施：OpenFeign 骨架。url 有值=直连（application.yml 默认下发，
     * nacos 关闭时可用）；清空 url 值即走注册中心负载均衡（lb://name）。
     */

    /**
     * V6.0 微服务设施：OpenFeign 骨架。url 有值=直连（application.yml 默认下发，
     * nacos 关闭时可用）；清空 url 值即走注册中心负载均衡（lb://name）。
     */
    static String remoteSampleClientJava(String pkg, String stem) {
        return "package " + pkg + ".remote;\n\n"
                + "import org.springframework.cloud.openfeign.FeignClient;\n"
                + "import org.springframework.web.bind.annotation.GetMapping;\n\n"
                + "/**\n"
                + " * 跨服务调用骨架（V6.0）：只给约定与示例，跨服务接口按需在此包手写，\n"
                + " * 不做按表自动生成（跨服务契约需要人工判断 DTO 归属与事务边界）。\n"
                + " *\n"
                + " * <p>切换规则（见 bootstrap 模块 application.yml 的 " + stem + ".remote.bootstrap-url）：\n"
                + " * <ul>\n"
                + " *   <li>url 有值（默认）→ 直连 http://localhost:<port>，不依赖注册中心</li>\n"
                + " *   <li>url 清空 → 走 lb://" + stem + "-bootstrap，需 nacos.discovery.enabled=true</li>\n"
                + " * </ul>\n"
                + " */\n"
                + "@FeignClient(name = \"" + stem + "-bootstrap\", url = \"${" + stem + ".remote.bootstrap-url:}\")\n"
                + "public interface RemoteSampleClient {\n\n"
                + "    /** 健康检查示例：能 ping 通即链路可用，替换成真实跨服务接口即可 */\n"
                + "    @GetMapping(\"/actuator/health\")\n"
                + "    String health();\n"
                + "}\n";
    }

    /**
     * V6.0 微服务设施：Nacos 配置中心接入模板（注释态）。
     */

    /**
     * V6.0 微服务设施：Nacos 配置中心接入模板（注释态）。
     */
    static String bootstrapYml(String stem) {
        return "# =====================================================================\n"
                + "# Nacos 配置中心接入模板（V6.0，默认注释态）\n"
                + "#\n"
                + "# 注意：Spring Boot 3 默认不读取 bootstrap.yml，下面这行是「配置中心」的\n"
                + "# 接入开关；运行开关（注册中心）在 application.yml 的 spring.cloud.nacos.*。\n"
                + "# 使用：放开 spring.config.import 并确认 nacos.config.enabled=true。\n"
                + "# =====================================================================\n"
                + "spring:\n"
                + "#  config:\n"
                + "#    import: optional:nacos:" + stem + "-bootstrap.yml\n";
    }

    static String applicationYml(GenScaffoldDTO dto, String pkg, String project, int serverPort,
                                  String stem, boolean micro, boolean payEnabled, boolean fileEnabled) {
        String db = StringUtils.hasText(dto.getDbName()) ? dto.getDbName() : project.replace('-', '_');
        String host = StringUtils.hasText(dto.getDbHost()) ? dto.getDbHost() : "127.0.0.1:3306";
        String user = StringUtils.hasText(dto.getDbUsername()) ? dto.getDbUsername() : "root";
        String pwd = StringUtils.hasText(dto.getDbPassword()) ? dto.getDbPassword() : "root";
        boolean activiti = "activiti".equalsIgnoreCase(
                StringUtils.hasText(dto.getFlowEngine()) ? dto.getFlowEngine().trim() : "");
        // V6.1：Activiti 自带 Spring Security 自动装配，与 AuthContextFilter 打架——双保险：
        // 启动类 exclude（主）+ yml exclude（兜底，改依赖不用动代码）
        String activitiBlock = activiti
                ? "  autoconfigure:\n"
                + "    exclude: org.activiti.spring.boot.ActivitiMethodSecurityAutoConfiguration\n" : "";
        // V6.0 micro：Nacos 开关块留在 spring: 内部；Feign 直连默认值是顶层键，必须放 yml 末尾——
        // 插进 spring: 里会把后面的 profiles/datasource 挤成顶层键，datasource 全部失效（S8 实测翻过车）
        String microBlock = micro
                ? "  cloud:\n"
                + "    nacos:\n"
                + "      discovery:\n"
                + "        # 默认关：直连运行（与多模块一致）；启用注册中心改 true 并先启动本机 Nacos\n"
                + "        enabled: false\n"
                + "        server-addr: 127.0.0.1:8848\n"
                + "      config:\n"
                + "        enabled: false\n"
                + "        server-addr: 127.0.0.1:8848\n" : "";
        String feignBlock = micro
                ? "\n# V6.0 跨服务调用（Feign）：url 有值=直连；启用 Nacos 后清空该值即走 lb://\n"
                + stem + ":\n"
                + "  remote:\n"
                + "    bootstrap-url: http://localhost:" + serverPort + "\n" : "";
        // V6.0 micro：注册名用 stem-bootstrap（网关 lb:// 与 Feign name 都指向它）；mono/multi 维持原名零变化
        String appName = micro ? stem + "-bootstrap" : project + "-server";
        // V6.3 支付能力包：密钥全部环境变量占位符（复用 V6.2 机制），缺省 mock 渠道开箱即跑；
        // 顶层键块必须放 yml 末尾（坑 40：插进 spring: 会把后续块挤成顶层键）
        String payBlock = payEnabled
                ? "\n# V6.3 支付能力包：渠道单选，缺省 mock（无商户资质也能全流程演示）；\n"
                + "# 真渠道接入=改 PAY_CHANNEL + 填对应凭据环境变量，零代码改动。密钥泄露=资金风险，严禁明文入 yml\n"
                + "pay:\n"
                + "  channel: ${PAY_CHANNEL:mock}\n"
                + "  wxpay:\n"
                + "    app-id: ${PAY_WX_APPID:}\n"
                + "    mch-id: ${PAY_WX_MCH_ID:}\n"
                + "    merchant-serial-no: ${PAY_WX_SERIAL_NO:}\n"
                + "    api-v3-key: ${PAY_WX_API_V3_KEY:}\n"
                + "    private-key-path: ${PAY_WX_PRIVATE_KEY_PATH:}\n"
                + "    notify-url: ${PAY_WX_NOTIFY_URL:}\n"
                + "  alipay:\n"
                + "    app-id: ${PAY_ALI_APP_ID:}\n"
                + "    private-key: ${PAY_ALI_PRIVATE_KEY:}\n"
                + "    alipay-public-key: ${PAY_ALI_PUBLIC_KEY:}\n"
                + "    notify-url: ${PAY_ALI_NOTIFY_URL:}\n"
                + "  # 回调不可达兜底：定时查单补偿（本机/内网部署靠它对账收状态）\n"
                + "  reconcile-enabled: true\n"
                + "  reconcile-interval-ms: 300000\n" : "";
        // V6.6 文件能力包：Spring 默认 multipart 上限 1MB，不配的话 2MB 图片在解析层就 500，
        // 业务层的大小校验（file.max-size-mb）根本轮不到——留 2 倍余量，让业务层给出友好提示。
        // Tomcat 侧还有一层 max-http-form-post-size 默认 10MB（实测 11MB 上传被 Tomcat 直接拒 500），
        // 必须同步放宽，否则 Spring 配了也白配
        // 注意缩进：spring 的直接子键是 2 空格（写成 4 会变成 application.servlet，配置静默失效——S8 实测踩过）
        String multipartBlock = fileEnabled
                ? "  servlet:\n"
                + "    multipart:\n"
                + "      max-file-size: 20MB\n"
                + "      max-request-size: 25MB\n" : "";
        String tomcatBlock = fileEnabled
                ? "  tomcat:\n"
                + "    max-http-form-post-size: 25MB\n" : "";
        // V6.6 文件能力包：顶层键块放 yml 末尾（坑 40）；白名单扩展名可省略（FileProps 内置默认）
        String fileBlock = fileEnabled
                ? "\n# V6.6 文件能力包：上传落盘目录相对启动目录；生产容器建议挂卷到独立数据盘\n"
                + "file:\n"
                + "  upload-dir: ${FILE_UPLOAD_DIR:./uploads}\n"
                + "  max-size-mb: ${FILE_MAX_SIZE_MB:10}\n" : "";
        // V6.2 配置安全：url/username/password 改环境变量占位符——冒号后默认值=本机直跑原行为，
        // 生产由 DB_* 环境变量覆盖，yml 本身不再含可提交泄露的明文密码
        return "server:\n"
                + "  port: " + serverPort + "\n"
                + tomcatBlock + "\n"
                + "spring:\n"
                + "  application:\n"
                + "    name: " + appName + "\n"
                + multipartBlock
                + microBlock
                + activitiBlock
                + "  profiles:\n"
                + "    # 本机私有配置（数据库密码等）放 application-local.yml，不提交仓库\n"
                + "    include: local\n"
                + "  datasource:\n"
                + "    # V6.2：环境变量优先（容器/CI 场景），冒号后默认值=本机直连（与旧版明文一致）\n"
                + "    url: ${DB_URL:jdbc:mysql://" + host + "/" + db
                + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true}\n"
                + "    username: ${DB_USERNAME:" + user + "}\n"
                + "    password: ${DB_PASSWORD:" + pwd + "}\n"
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
                + "# 操作日志的 IP 归属地：先查 classpath:/ip/ip2region.db 离线库，查不到才走在线接口\n"
                + "# 内网/本机不发请求；结果缓存 24h，失败负缓存 5min\n"
                + "app:\n"
                + "  ip-region:\n"
                + "    enabled: true\n"
                + "    offline: true\n"
                + "    api: http://ip-api.com/json/{ip}?lang=zh-CN\n"
                + "    timeout-ms: 3000\n"
                + "    # proxy: http://127.0.0.1:7890   # 需要走代理才配，不配则强制直连\n\n"
                + "logging:\n"
                + "  level:\n"
                + "    " + pkg + ": info\n"
                + feignBlock
                + payBlock
                + fileBlock
                // V6.4 P0-1：会话时长（滑动续期）；顶层键块放 yml 末尾（坑 40：插进 spring: 会挤坏后续块）
                + "\n# V6.4 登录会话：token 持久化 sys_session，滑动续期；改短便于验证过期清理\n"
                + "auth:\n"
                + "  session-hours: 24\n";
    }

    static String applicationLocalExample(String pkg) {
        return "# 本机私有配置（复制为 application-local.yml 后填写，该文件不入库）\n"
                + "spring:\n"
                + "  datasource:\n"
                + "    password: 你的本机数据库密码\n\n"
                + "logging:\n"
                + "  level:\n"
                + "    " + pkg + ": debug\n";
    }

    static String backendGitignore() {
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

    static String backendReadme(String project, String pkg, String module, int serverPort) {
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
}

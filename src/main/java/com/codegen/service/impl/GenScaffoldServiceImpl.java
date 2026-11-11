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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

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

    /** V8.0：读取该系统的用户译文，组装产物语言包 */
    @Autowired
    private I18nMessageService i18nMessageService;

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
        // V6.8 品牌参数化：显示名/主色统一走 GenTemplateModel 的回落实现（唯一实现；
        // 非法色值会回落默认色，避免把 "red;" 这类串拼进 CSS 打坏整页）
        String appName = com.codegen.util.GenTemplateModel.resolveAppName(
                dto.getAppName(), project, null);
        String themeColor = com.codegen.util.GenTemplateModel.resolveThemeColor(dto.getAppThemeColor());
        // V6.8 Logo：空 = favicon 用自带 placeholder.svg、品牌区用文字标识（不拼不存在的 logo.png）
        String appLogo = com.codegen.util.GenTemplateModel.resolveAppLogo(dto.getAppLogo());
        // V7.5 项目语言配置：默认 zh-CN，与改造前硬编码一致；解析只在 LanguageConfig 一处
        String htmlLang = com.codegen.config.LanguageConfig.resolveLocale(dto.getDefaultLanguage());
        String htmlDir = com.codegen.config.LanguageConfig.resolveDir(htmlLang);

        Path backendRoot = resolveRoot(dto.getBackendRoot(), outputProperties.getBackendRoot(), backendModule);
        Path frontendRoot = resolveRoot(dto.getFrontendRoot(), outputProperties.getFrontendRoot(), frontendModule);

        // V5.5 多模块：模块 stem 唯一实现在 GenNaming.multiModuleStem（rootPackage 最后一段），
        // 与 apply 侧 GenPathResolver.moduleRelPrefix 必须算出同名，否则生成物落进不存在的模块目录
        boolean multi = GenPathResolver.isMultiLike(dto.getProjectStructure());
        // V6.0 微服务设施：multi 基础上追加 gateway 模块 + Nacos/OpenFeign 设施（默认关，两栖运行）
        boolean micro = GenPathResolver.isMicro(dto.getProjectStructure());
        // V6.7 产物测试出厂：缺省开（只写 src/test，零运行时侵入）；
        // multi/micro 产物启动类在 bootstrap、业务类在 service，跨模块 @SpringBootTest 落点待 P1，先关保证不产坏文件
        boolean testEnabled = !Boolean.FALSE.equals(dto.getTestEnabled()) && !multi;
        String stem = GenNaming.multiModuleStem(pkg);
        // 基座类（common/system 包）落 common 模块；启动类/yml/db 脚本落 bootstrap 模块；单模块两者都是 backendRoot
        Path commonRoot = multi ? backendRoot.resolve(stem + "-common") : backendRoot;
        Path bootRoot = multi ? backendRoot.resolve(stem + "-bootstrap") : backendRoot;
        // V6.0：Feign 骨架归 service 模块；gateway 模块目录
        Path serviceRoot = multi ? backendRoot.resolve(stem + "-service") : backendRoot;
        Path gatewayRoot = backendRoot.resolve(stem + "-gateway");

        // V8.0：产物国际化开关。放在 VO 上（每次 scaffold 新建）而不是实例字段，
        // 单例 service 并发生成时不会串状态；write() 据此决定是否做文案重写。
        boolean i18nOn = Boolean.TRUE.equals(dto.getI18nEnabled());

        GenScaffoldVO vo = new GenScaffoldVO();
        vo.setI18nEnabled(i18nOn);
        vo.setBackendPath(backendRoot.toString());
        vo.setFrontendPath(Boolean.TRUE.equals(dto.getWithFrontend()) ? frontendRoot.toString() : null);

        boolean force = Boolean.TRUE.equals(dto.getForce());
        int[] counter = new int[]{0, 0};
        String appClass = toClassName(project);

        boolean activiti = "activiti".equalsIgnoreCase(
                StringUtils.hasText(dto.getFlowEngine()) ? dto.getFlowEngine().trim() : "");
        // V6.3 支付能力包：系统级开关（DTO 由调用方按 sys_project.pay_enabled + resolvePay 组装）
        boolean payEnabled = Boolean.TRUE.equals(dto.getPayEnabled());
        // V6.6 文件能力包：系统级开关（DTO 由调用方按 sys_project.file_enabled + resolveFile 组装）
        boolean fileEnabled = Boolean.TRUE.equals(dto.getFileEnabled());
        // ---------- 后端 ----------
        if (multi) {
            // 多模块：聚合 pom + 4 个子模块 pom；依赖拆分原则——common 承载全部基座依赖，
            // domain 只加 mybatis-plus，service 加 web 层与业务依赖，bootstrap 只做启动壳
            // V5.5 收尾修复：artifactId 必须匹配 Maven id 模式（[A-Za-z0-9_\-.]+），
            // backendModule 默认取 projectName（用户常中文命名），中文直进 artifactId 产物 mvn 直接拒读。
            // 聚合坐标统一用 stem（rootPackage 最后一段，纯合法字符）；可读名放 <name>。
            String parentArtifactId = stem + "-parent";
            write(backendRoot, "pom.xml", pomMultiParent(appName, project, pkg, stem, backendModule, micro, activiti, payEnabled), "backend", force, vo, counter);
            write(backendRoot, stem + "-common/pom.xml", pomMultiModule(pkg, parentArtifactId, stem, "common",
                    dep("org.springframework.boot", "spring-boot-starter-web", null)
                            + dep("org.springframework.boot", "spring-boot-starter-validation", null)
                            + dep("org.springframework.boot", "spring-boot-starter-aop", null)
                            + dep("com.baomidou", "mybatis-plus-spring-boot3-starter", "${mybatis-plus.version}")
                            // OpeLogController/MaskUtils 的 @Operation/@Schema 注解在这层
                            + dep("org.springdoc", "springdoc-openapi-starter-webmvc-ui", "2.3.0")
                            // V6.4 密码 BCrypt：仅 crypto 单模块，不引 Spring Security 全家桶（版本走 BOM）
                            + dep("org.springframework.security", "spring-security-crypto", null)
                            + dep("org.projectlombok", "lombok", "${lombok.version}")
                            // V6.3 支付模块类落 common 层，SDK 依赖随类归位（坑 41：谁 import 谁依赖）
                            + (payEnabled
                                ? dep("com.github.wechatpay-apiv3", "wechatpay-java", "${wechatpay.version}")
                                    + dep("com.alipay.sdk", "alipay-sdk-java", "${alipay.version}")
                                : ""),
                    false), "backend", force, vo, counter);
            write(backendRoot, stem + "-domain/pom.xml", pomMultiModule(pkg, parentArtifactId, stem, "domain",
                    internalDep(pkg, stem, "common")
                            + dep("com.baomidou", "mybatis-plus-spring-boot3-starter", "${mybatis-plus.version}")
                            + dep("org.projectlombok", "lombok", "${lombok.version}"),
                    false), "backend", force, vo, counter);
            write(backendRoot, stem + "-service/pom.xml", pomMultiModule(pkg, parentArtifactId, stem, "service",
                    internalDep(pkg, stem, "domain")
                            + dep("org.springframework.boot", "spring-boot-starter-web", null)
                            + dep("org.springframework.boot", "spring-boot-starter-validation", null)
                            + dep("org.springdoc", "springdoc-openapi-starter-webmvc-ui", "2.3.0")
                            + dep("org.apache.poi", "poi-ooxml", "5.2.5")
                            // V6.0：Feign 骨架在 .remote 包，注解依赖必须在本模块（bootstrap 只放 lb 运行时）
                            + (micro ? dep("org.springframework.cloud", "spring-cloud-starter-openfeign", null) : "")
                            + dep("org.projectlombok", "lombok", "${lombok.version}")
                            + extraDepsXml(dto),
                    false), "backend", force, vo, counter);
            write(backendRoot, stem + "-bootstrap/pom.xml", pomMultiModule(pkg, parentArtifactId, stem, "bootstrap",
                    internalDep(pkg, stem, "service")
                            // V6.0 微服务设施：注册/配置中心与 lb 运行时（版本由聚合 pom BOM 管理，enabled=false 时零行为；
                            // openfeign 注解依赖在 service 模块——Feign 骨架在那层编译）
                            + (micro
                                ? dep("com.alibaba.cloud", "spring-cloud-starter-alibaba-nacos-discovery", null)
                                    + dep("com.alibaba.cloud", "spring-cloud-starter-alibaba-nacos-config", null)
                                    + dep("org.springframework.cloud", "spring-cloud-starter-loadbalancer", null)
                                : "")
                            // V6.1：流程引擎在启动壳层装配（Runner/引擎服务都在 common，随依赖可达）
                            + (activiti
                                ? dep("org.activiti", "activiti-spring-boot-starter", "${activiti.version}")
                                : "")
                            + "        <dependency>\n"
                            + "            <groupId>com.mysql</groupId>\n"
                            + "            <artifactId>mysql-connector-j</artifactId>\n"
                            + "            <scope>runtime</scope>\n"
                            + "        </dependency>\n",
                    true), "backend", force, vo, counter);
            if (micro) {
                // V6.0 微服务设施：gateway 模块（webflux，独立进程，端口=服务端口+1）
                write(gatewayRoot, "pom.xml", pomMultiModule(pkg, parentArtifactId, stem, "gateway",
                        dep("org.springframework.cloud", "spring-cloud-starter-gateway", null)
                                + dep("com.alibaba.cloud", "spring-cloud-starter-alibaba-nacos-discovery", null)
                                + dep("org.springframework.cloud", "spring-cloud-starter-loadbalancer", null)
                                + dep("com.alibaba.cloud", "spring-cloud-starter-alibaba-nacos-config", null)
                                + dep("org.springframework.cloud", "spring-cloud-starter-bootstrap", null),
                        true), "backend", force, vo, counter);
                write(gatewayRoot, "src/main/java/" + pkg.replace('.', '/') + "/gateway/"
                                + appClass + "GatewayApplication.java",
                        gatewayApplicationJava(pkg, appClass), "backend", force, vo, counter);
                write(gatewayRoot, "src/main/resources/application.yml",
                        gatewayApplicationYml(pkg, stem, serverPort), "backend", force, vo, counter);
                // Feign 骨架：只给约定与示例，跨服务接口按需手写（规划定案，不按表自动生成）
                write(serviceRoot, "src/main/java/" + pkg.replace('.', '/') + "/remote/RemoteSampleClient.java",
                        remoteSampleClientJava(pkg, stem), "backend", force, vo, counter);
            }
        } else {
            write(backendRoot, "pom.xml", pomXml(dto, appName, project, pkg, backendModule, serverPort, activiti, payEnabled, testEnabled), "backend", force, vo, counter);
        }

        String pkgPath = "src/main/java/" + pkg.replace('.', '/');
        // 启动类与运行配置归 bootstrap；包名不变（仍是 <pkg> 根），@SpringBootApplication 默认扫描覆盖全部模块；
        // micro 时启动类追加 @EnableFeignClients 扫 remote 包（跨服务调用骨架）
        write(bootRoot, pkgPath + "/" + appClass + "Application.java",
                applicationJava(pkg, appClass, micro, activiti, payEnabled, fileEnabled), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/result/Result.java", resultJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/result/ResultCode.java", resultCodeJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/exception/BusinessException.java",
                businessExceptionJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/exception/GlobalExceptionHandler.java",
                globalExceptionHandlerJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/context/UserContext.java", userContextJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/entity/BaseEntity.java", baseEntityJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/page/PageQuery.java", pageQueryJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/config/MybatisPlusConfig.java",
                mybatisPlusConfigJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/datascope/DataScopeInterceptor.java",
                dataScopeInterceptorJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/datascope/DataScopeRegistry.java",
                dataScopeRegistryJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/log/Log.java", logAnnotationJava(pkg),
                "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/log/LogAspect.java", logAspectJava(pkg),
                "backend", force, vo, counter);
        // 归属地解析器：日志切面依赖它把 IP 翻成省市，独立成类便于替换成自建接口
        write(commonRoot, pkgPath + "/common/log/IpRegionResolver.java", ipRegionResolverJava(pkg),
                "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/log/Ip2RegionSearcher.java", ip2RegionSearcherJava(pkg),
                "backend", force, vo, counter);
        // 离线 IP 库是二进制，跟着解析器一起发；没有它归属地就只能赌公网接口的可用性
        writeBinary(commonRoot, "src/main/resources/ip/ip2region.db", Ip2RegionSearcher.RESOURCE,
                "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/system/OpeLog.java", operLogEntityJava(pkg),
                "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/system/OpeLogMapper.java", operLogMapperJava(pkg),
                "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/system/OpeLogController.java", operLogControllerJava(pkg),
                "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/config/LongIdJacksonConfig.java",
                longIdJacksonConfigJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/config/TimeJacksonConfig.java",
                timeJacksonConfigJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/util/DictTexts.java",
                dictTextsJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/handler/AuditMetaObjectHandler.java",
                auditMetaObjectHandlerJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/util/MaskUtils.java", maskUtilsJava(pkg),
                "backend", force, vo, counter);
        if (Boolean.TRUE.equals(dto.getWithAuth())) {
            write(commonRoot, pkgPath + "/common/config/AuthContextFilter.java",
                    authContextFilterJava(pkg, payEnabled, fileEnabled), "backend", force, vo, counter);
            // V5.0 F-07：登录基座闭环——以前 filter「非空 token 即放行」是占位实现，
            // 401 拦得住陌生人却发不出合法 token，登录页成了摆设
            write(commonRoot, pkgPath + "/common/auth/TokenStore.java",
                    tokenStoreJava(pkg), "backend", force, vo, counter);
            write(commonRoot, pkgPath + "/common/auth/AuthController.java",
                    authControllerJava(pkg), "backend", force, vo, counter);
            // V6.4 P0-3：接口权限点后端强制——贡献者接口 + 集中校验器 + 基座端点豁免清单
            write(commonRoot, pkgPath + "/common/auth/ApiPermContributor.java",
                    apiPermContributorJava(pkg), "backend", force, vo, counter);
            write(commonRoot, pkgPath + "/common/auth/PermissionChecker.java",
                    permissionCheckerJava(pkg), "backend", force, vo, counter);
            write(commonRoot, pkgPath + "/common/auth/FlowApiPerms.java",
                    flowApiPermsJava(pkg), "backend", force, vo, counter);
            if (payEnabled) {
                write(commonRoot, pkgPath + "/common/auth/PayApiPerms.java",
                        payApiPermsJava(pkg), "backend", force, vo, counter);
            }
            // V6.4 P0-4：登录日志只读查询接口
            write(commonRoot, pkgPath + "/system/LoginLogController.java",
                    loginLogControllerJava(pkg), "backend", force, vo, counter);
            write(bootRoot, "db/init_sys_user.sql", seedSql(),
                    "backend", force, vo, counter);
        }
        // V6.8 字典脚手架（恒写）：建表 + 通用种子 + 只读接口。
        // 之前生成页面的字典下拉依赖目标库「恰好已有」sys_dict_data，新库直接空下拉；
        // 现在产物自带表结构与种子，接口挂在 /system/dict（白名单只读）。
        write(bootRoot, "db/dict.sql", loadScaffoldResource("scaffold/dict.sql"),
                "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/system/SysDictController.java",
                dictControllerJava(pkg), "backend", force, vo, counter);
        // V6.8 素材链路：static/img 里放一张默认图，/img/placeholder.svg 开箱即 200
        // （白名单常驻 /img，前端 devServer 代理 /img 不剥前缀——三处都对上这条路径）
        write(bootRoot, "src/main/resources/static/img/placeholder.svg",
                loadScaffoldResource("scaffold/web/placeholder.svg"), "backend", force, vo, counter);

        // V6.1 流程基座（恒写）：修复 controller.ftl 引用 ${pkg}.common.flow.FlowService
        // 而基座从未生成的历史断裂——绑定 flow_code 的表生成产物此前编译不过。
        // 引擎实现二选一：builtin=移植平台简易流（无第三方依赖）；activiti=引擎实现 + 部署 Runner
        write(commonRoot, pkgPath + "/common/flow/FlowService.java",
                flowServiceJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/flow/FlowRecord.java",
                flowRecordJava(pkg), "backend", force, vo, counter);
        if (activiti) {
            write(commonRoot, pkgPath + "/common/flow/ActivitiFlowService.java",
                    activitiFlowServiceJava(pkg), "backend", force, vo, counter);
            write(commonRoot, pkgPath + "/common/flow/FlowDeployRunner.java",
                    flowDeployRunnerJava(pkg), "backend", force, vo, counter);
        } else {
            write(commonRoot, pkgPath + "/common/flow/BuiltinFlowService.java",
                    builtinFlowServiceJava(pkg), "backend", force, vo, counter);
        }
        write(commonRoot, pkgPath + "/common/flow/FlowController.java",
                flowControllerJava(pkg), "backend", force, vo, counter);
        // 产物库流程表：DDL 恒写；绑定流程的定义 INSERT 由调用方经 flowDefs 下发（平台不直连产物库）
        write(bootRoot, "db/init_sys_flow.sql", initSysFlowSql(dto.getFlowDefs()),
                "backend", force, vo, counter);
        // V6.2 部署物：genDeploy 开启才生成（缺省关闭 → 存量产物与旧版逐文件一致）
        if (Boolean.TRUE.equals(dto.getGenDeploy())) {
            write(bootRoot, "Dockerfile", dockerfileText(), "backend", force, vo, counter);
            write(bootRoot, ".dockerignore", dockerignoreText(), "backend", force, vo, counter);
            write(bootRoot, "docker-compose.yml", dockerComposeYml(project, serverPort, micro),
                    "backend", force, vo, counter);
            write(bootRoot, "deploy/ci-github-actions.yml.example", ciGithubActionsText(),
                    "backend", force, vo, counter);
            write(bootRoot, "deploy/Jenkinsfile.example", jenkinsfileText(),
                    "backend", force, vo, counter);
            vo.getNextSteps().add("部署物：docker build -t " + project.toLowerCase() + " . 后 docker compose up -d"
                    + "（基础镜像可经 --build-arg 覆盖；CI 用 deploy/ 下的 .example 改名即用）");
        }
        // V6.3 支付能力包：payEnabled 开启才生成（缺省关闭 → 存量产物与旧版逐文件一致）。
        // 渠道抽象 PaymentService + wxpay/alipay/mock 三实现；回调/补偿/收银台见 docs/design/V6.3支付能力包-设计.md。
        // 源码走 classpath 资源 scaffold-pay/*.java（__PKG__ 占位），比几千行转义字符串块可维护
        if (payEnabled) {
            write(bootRoot, "db/pay.sql", payResource("pay.sql", pkg), "backend", force, vo, counter);
            for (String f : List.of("PayOrder.java", "PayOrderMapper.java", "PayOrderVO.java", "PayProps.java",
                    "PaymentService.java", "AbstractPayService.java", "MockPayService.java",
                    "WxPayNativeService.java", "AliPayPageService.java", "PayController.java",
                    "PayScheduleConfig.java", "PayReconcileJob.java")) {
                write(commonRoot, pkgPath + "/common/pay/" + f, payResource(f, pkg),
                        "backend", force, vo, counter);
            }
            vo.getNextSteps().add("支付能力包：缺省 mock 渠道开箱即跑（收银台 /pay/cashier，模拟支付走与真渠道同构链路）；"
                    + "接真渠道改 yml pay.channel 并配置 PAY_* 环境变量，零代码改动");
        }
        // V6.6 文件能力包：fileEnabled 开启才生成（缺省关闭 → 存量产物与旧版逐文件一致）。
        // sys_file 元数据表 + /file 上传下载端点 + ImageUpload/FileUpload 前端组件；
        // 设计见 docs/design/V6.6文件能力包-设计.md。纯 Spring Web + MyBatis-Plus，无新依赖。
        if (fileEnabled) {
            write(bootRoot, "db/file.sql", fileResource("file.sql", pkg), "backend", force, vo, counter);
            for (String f : List.of("FileRecord.java", "FileMapper.java", "FileProps.java",
                    "FileController.java", "FileWebConfig.java")) {
                write(commonRoot, pkgPath + "/common/file/" + f, fileResource(f, pkg),
                        "backend", force, vo, counter);
            }
            // V6.4 权限闭环：/file 端点注册为「仅登录」（同 FlowApiPerms/PayApiPerms 模式）
            if (Boolean.TRUE.equals(dto.getWithAuth())) {
                write(commonRoot, pkgPath + "/common/auth/FileApiPerms.java",
                        fileApiPermsJava(pkg), "backend", force, vo, counter);
            }
            vo.getNextSteps().add("文件能力包：上传目录默认 ./uploads（yml file.upload-dir 可改）；"
                    + "首次启动前导入 db/file.sql 建 sys_file 表（流水线自动导入），"
                    + "生成页面里 html_type=图片/文件上传 的列自动带上传控件与缩略图预览");
        }
        // V6.7 产物测试出厂：testEnabled 开启才生成（缺省开；multi/micro 已在上方置关）。
        // src/test 全套：H2 测试 profile + 基座表 DDL + 登录态基类；业务表测试与建表在 /gen/apply 按表落盘。
        // pom 测试依赖（starter-test + h2）随 pomXml 注入，本块只写文件
        if (testEnabled) {
            write(bootRoot, "src/test/resources/application-test.yml",
                    testResource("application-test.yml", pkg), "backend", force, vo, counter);
            write(bootRoot, "src/test/resources/schema/base.sql",
                    testResource("base.sql", pkg), "backend", force, vo, counter);
            if (payEnabled) {
                write(bootRoot, "src/test/resources/schema/pay.sql",
                        testResource("pay.sql", pkg), "backend", force, vo, counter);
            }
            if (fileEnabled) {
                write(bootRoot, "src/test/resources/schema/file.sql",
                        testResource("file.sql", pkg), "backend", force, vo, counter);
            }
            write(bootRoot, "src/test/java/" + pkg.replace('.', '/') + "/LoginStateTestSupport.java",
                    testResource("LoginStateTestSupport.java", pkg), "backend", force, vo, counter);
            vo.getNextSteps().add("测试骨架：cd " + bootRoot + " && mvn test（H2 内存库，无需外部 MySQL）；"
                    + "业务表的集成测试在代码生成（/gen/apply）时逐表落盘到 src/test；"
                    + "关掉请在 系统管理→项目 里把「测试骨架」设为不生成后重新脚手架");
        }
        write(bootRoot, "src/main/resources/application.yml",
                applicationYml(dto, pkg, project, serverPort, stem, micro, payEnabled, fileEnabled),
                "backend", force, vo, counter);
        if (micro) {
            // V6.0：Nacos 配置中心接入模板（注释态）。说明头里写清 Boot 3 默认不读 bootstrap.yml，
            // 真正的运行开关在 application.yml 的 spring.cloud.nacos.*——放这是为了注释态一目了然
            write(bootRoot, "src/main/resources/bootstrap.yml", bootstrapYml(stem),
                    "backend", force, vo, counter);
        }
        write(bootRoot, "src/main/resources/application-local.yml.example",
                applicationLocalExample(pkg), "backend", force, vo, counter);
        write(bootRoot, ".gitignore", backendGitignore(), "backend", force, vo, counter);
        write(bootRoot, "README.md", backendReadme(appName, pkg, backendModule, serverPort),
                "backend", force, vo, counter);

        // ---------- 前端 ----------
        if (Boolean.TRUE.equals(dto.getWithFrontend())) {
            write(frontendRoot, "package.json", frontendPackageJson(project, frontendModule, payEnabled),
                    "frontend", force, vo, counter);
            // V6.0 micro：前端请求走网关（端口=服务端口+1），apiBase 不带 /api 前缀（网关按模块路由）
            int proxyPort = micro ? serverPort + 1 : serverPort;
            write(frontendRoot, "vue.config.js", frontendVueConfig(webPort, proxyPort,
                    StringUtils.hasText(dto.getGatewayPrefix()) ? dto.getGatewayPrefix().trim() : "demo",
                    fileEnabled),
                    "frontend", force, vo, counter);
            write(frontendRoot, "babel.config.js", frontendBabelConfig(), "frontend", force, vo, counter);
            write(frontendRoot, ".env.development",
                    frontendEnvDev(proxyPort), "frontend", force, vo, counter);
            write(frontendRoot, ".env.production", frontendEnvProd(), "frontend", force, vo, counter);
            write(frontendRoot, "public/index.html",
                    frontendIndexHtml(appName, appLogo, themeColor, htmlLang, htmlDir, i18nOn),
                    "frontend", force, vo, counter);
            // V6.8 素材链路：产物自带占位图，图片列 onerror / el-image error 槽统一指向它，
            // 种子数据里指向不存在文件的 URL 不再显示浏览器破图
            write(frontendRoot, "public/placeholder.svg", loadScaffoldResource("scaffold/web/placeholder.svg"),
                    "frontend", force, vo, counter);
            write(frontendRoot, "src/main.js", frontendMainJs(i18nOn), "frontend", force, vo, counter);
            write(frontendRoot, "src/App.vue", frontendAppVue(), "frontend", force, vo, counter);
            write(frontendRoot, "src/utils/request.js", frontendRequestJs(), "frontend", force, vo, counter);
            write(frontendRoot, "src/layout/index.vue", frontendLayoutVue(appName, appLogo, themeColor, i18nOn),
                    "frontend", force, vo, counter);
            write(frontendRoot, "src/views/index.vue", frontendHomeVue(appName), "frontend", force, vo, counter);
            // V5.0 F-07：登录页与路由守卫（401 跳转目标从 request.js 来）
            write(frontendRoot, "src/views/login.vue", frontendLoginVue(appName, appLogo, themeColor), "frontend", force, vo, counter);
            write(frontendRoot, "src/router/index.js", frontendRouterJs(), "frontend", force, vo, counter);
            // 占位：route-sync 未跑前保证 webpack 能解析 import（后续被 GenMenuServiceImpl 整文件重建）
            write(frontendRoot, "src/router/gen-routes.js",
                    "// 该文件由代码生成器自动维护，请勿手工编辑。\n"
                  + "// 结构为三级：系统 → 业务域 → 页面，菜单树与路由共用同一套分组推导。\n"
                  + "export default []\n",
                    "frontend", force, vo, counter);
            write(frontendRoot, "src/store/index.js", frontendStoreJs(), "frontend", force, vo, counter);
            // V6.6 文件能力包：上传组件随开关写盘（生成页面的 image/file 列 import 这两个组件）
            if (fileEnabled) {
                write(frontendRoot, "src/components/ImageUpload.vue",
                        fileResource("ImageUpload.vue", pkg), "frontend", force, vo, counter);
                write(frontendRoot, "src/components/FileUpload.vue",
                        fileResource("FileUpload.vue", pkg), "frontend", force, vo, counter);
                // V6.8 多图字段：multiImage 列 import 这个组件（JSON 数组字符串 v-model）
                write(frontendRoot, "src/components/MultiImageUpload.vue",
                        fileResource("MultiImageUpload.vue", pkg), "frontend", force, vo, counter);
            }
            // V8.0 产物国际化：只有开关打开才写这一组文件，存量工程零 diff
            if (i18nOn) {
                writeI18nResources(frontendRoot, dto, force, vo, counter);
                vo.getNextSteps().add("产物国际化：系统管理→文案翻译 可逐语言补译；"
                        + "页面右上角切换语言，RTL 语言自动镜像；语言包在 src/locales/");
            }
            write(frontendRoot, ".gitignore", frontendGitignore(), "frontend", force, vo, counter);
            write(frontendRoot, "README.md", frontendReadme(appName, frontendModule, webPort, serverPort),
                    "frontend", force, vo, counter);
        }

        // ---------- V6.5 移动端工程（uni-app 单源 H5 + 微信小程序） ----------
        // 与 PC 前端互不依赖：独立工程、独立构建；开关关闭时整段跳过，存量产物零 diff。
        // 表业务配置（mobile/src/config/tables/**）由表生成侧产出，这里只出工程骨架与动态页。
        if (Boolean.TRUE.equals(dto.getMobileEnabled())) {
            Path mobileRoot = resolveRoot(dto.getFrontendRoot(), outputProperties.getFrontendRoot(),
                    frontendModule + "-mobile");
            vo.setMobilePath(mobileRoot.toString());
            // micro 时 H5 代理指向网关（端口=服务端口+1），与 PC vue.config.js 同口径
            int apiTarget = micro ? serverPort + 1 : serverPort;
            String gwPrefix = StringUtils.hasText(dto.getGatewayPrefix())
                    ? dto.getGatewayPrefix().trim() : "demo";
            for (Map.Entry<String, String> e : mobileResources(project, apiTarget, gwPrefix, htmlLang, htmlDir).entrySet()) {
                write(mobileRoot, e.getKey(), e.getValue(), "mobile", force, vo, counter);
            }
            vo.getNextSteps().add("移动端工程：cd " + mobileRoot + " && npm install && npm run dev:h5（H5 实机调试）；"
                    + "小程序包 npm run build:mp-weixin，用微信开发者工具导入 dist/build/mp-weixin"
                    + "（本地联调勾选「不校验合法域名」，发布前在 mp 后台配置 request 合法域名）");
        }

        vo.setWritten(counter[0]);
        vo.setSkipped(counter[1]);

        vo.getNextSteps().add("cd " + backendRoot + " && mvn -DskipTests package 编译验证");
        if (micro) {
            vo.getNextSteps().add("微服务设施：默认 nacos.enabled=false（直连运行，与多模块一致）；"
                    + "启用注册中心先启动本机 Nacos（127.0.0.1:8848），再把两个 application.yml 里 enabled 改 true，"
                    + "Feign 直连 url 清空即走负载均衡");
        }
        vo.getNextSteps().add("按 application-local.yml.example 建 application-local.yml 填本机数据库密码");
        if (Boolean.TRUE.equals(dto.getWithFrontend())) {
            vo.getNextSteps().add("cd " + frontendRoot + " && npm install && npm run dev");
        }
        vo.getNextSteps().add("回到平台「脚手架体检」核对基座是否齐全");
        vo.getNextSteps().add("在生成器里导入表配置，生成代码后写入本工程");
        return vo;
    }

    /**
     * 解析输出根：根目录（无论显式传入还是配置回落）一律按「容器根」处理，
     * 工程固定落在 {@code <root>/<module>} 子目录。
     *
     * <p>V5.0 修复（人事系统 pipeline 撞 CreateProcess error=267）：此前显式传入
     * 的绝对路径被当作工程根本身，脚手架把 45 个文件直接写进容器根、污染既有工程；
     * 而流水线打包步按 {@code <root>/<module>/pom.xml} 找工程、自检按
     * {@code <root>/<module>/src/main/java} 找基座类源码根——同一份 backendRoot
     * 三种理解。现全链路统一为「容器根 + 模块子目录」，谁都不许再特立独行。
     */
    private Path resolveRoot(String dtoRoot, String configRoot, String module) {
        String safeModule = safeModule(module);
        if (StringUtils.hasText(dtoRoot)) {
            return Paths.get(dtoRoot.trim()).toAbsolutePath().normalize().resolve(safeModule).normalize();
        }
        if (!StringUtils.hasText(configRoot)) {
            throw new IllegalStateException("未配置输出根目录：请在 gen.output.backend-root / frontend-root 配置，或显式传入路径");
        }
        return Paths.get(configRoot.trim(), safeModule).toAbsolutePath().normalize();
    }

    /**
     * 模块目录名只认单层目录。{@code Path.resolve} 遇到绝对路径参数会直接丢弃左侧根，
     * 遇到 {@code ../} 又能逃出根——而 backendModule/frontendModule 可由请求指定，
     * 不校验就等于允许把整个基座写到任意可写目录。
     */
    static String safeModule(String module) {
        String v = module == null ? "" : module.trim();
        if (v.isEmpty() || ".".equals(v) || "..".equals(v) || v.contains("..")
                || !v.matches("[A-Za-z0-9_.-]+")
                || Paths.get(v).isAbsolute() || Paths.get(v).getNameCount() != 1) {
            throw new BusinessException(400, "非法模块目录名：" + module);
        }
        return v;
    }

    /** 生成物必须落在自己的输出根内：relPath 由 rootPackage 等入参派生，越界一律拒绝 */
    static Path safeTarget(Path root, String relPath) {
        // 两侧都 normalize：只归一化一侧会因根里带 .. / 相对路径而误判合法路径（坑库坑 51 同源）
        Path base = root.toAbsolutePath().normalize();
        Path target = base.resolve(relPath).normalize();
        if (!target.startsWith(base)) {
            throw new BusinessException(400, "非法生成路径（越界）：" + relPath);
        }
        return target;
    }

    private void write(Path root, String relPath, String content, String side,
                       boolean force, GenScaffoldVO vo, int[] counter) {
        // V8.0：骨架文件（Java 字符串拼出来的布局/登录/首页/main.js/request.js）
        // 与表生成页走同一个重写器、同一份规则表，避免两套文案接线分叉。
        // 开关关闭时 rewriteOne 不会被调用，产物逐字节零 diff。
        if (vo.isI18nEnabled() && "frontend".equals(side)
                && I18nTextRewriter.isFrontendFile(relPath)) {
            content = I18nTextRewriter.rewriteOne(relPath, content);
        }
        Path target = safeTarget(root, relPath);
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
     * 二进制资源写入（离线 IP 库这类几 MB 的字节文件）。
     *
     * <p>单独开一个方法而不是把内容读成 String：二进制经 String 中转会被
     * 字符集解码改坏，而且 8.7MB 的库用流直接拷更省内存。
     * 资源缺失不抛异常——生成过程不该因为一个可选的增强文件整体失败。
     */
    private void writeBinary(Path root, String relPath, String classpathResource, String side,
                             boolean force, GenScaffoldVO vo, int[] counter) {
        Path target = root.resolve(relPath);
        boolean existed = Files.exists(target);
        if (existed && !force) {
            vo.getFiles().add(new GenScaffoldVO.Item(relPath, side, "SKIPPED"));
            counter[1]++;
            return;
        }
        try (java.io.InputStream in = getClass().getResourceAsStream(classpathResource)) {
            if (in == null) {
                log.warn("脚手架资源缺失 {}，跳过 {}", classpathResource, relPath);
                vo.getFiles().add(new GenScaffoldVO.Item(relPath, side, "SKIPPED"));
                counter[1]++;
                return;
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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

    // ============================ V6.1 流程基座 ============================

    /** 产物 FlowService 接口：双引擎共用契约（submit/approve/reject/records/state + todo/done/taskDetail） */
    private String flowServiceJava(String pkg) {
        return "package " + pkg + ".common.flow;\n\n"
                + "import java.util.List;\n"
                + "import java.util.Map;\n\n"
                + "/**\n"
                + " * V6.1 审批流统一契约：内置简易流（BuiltinFlowService）与 Activiti 引擎（ActivitiFlowService）\n"
                + " * 二选一装配，controller.ftl 的四端点与本类的 todo/done/taskDetail 契约保持不变。\n"
                + " */\n"
                + "public interface FlowService {\n\n"
                + "    void submit(String flowCode, Long bizId, String comment);\n\n"
                + "    void approve(String flowCode, Long bizId, String comment);\n\n"
                + "    void reject(String flowCode, Long bizId, String comment);\n\n"
                + "    List<FlowRecord> records(String flowCode, Long bizId);\n\n"
                + "    Map<String, Object> state(String flowCode, Long bizId);\n\n"
                + "    /** 我的待办：引擎各自实现（builtin=当前状态+角色匹配；activiti=TaskService 候选查询） */\n"
                + "    List<Map<String, Object>> todo();\n\n"
                + "    /** 我的已办 */\n"
                + "    List<Map<String, Object>> done();\n\n"
                + "    /** 任务详情：任务 + 业务单据 + 审批记录聚合 */\n"
                + "    Map<String, Object> taskDetail(String taskId);\n"
                + "}\n";
    }

    /** 产物 FlowRecord：与平台 common.flow.FlowRecord 同构（records 兼容视图的投影目标） */
    private String flowRecordJava(String pkg) {
        return "package " + pkg + ".common.flow;\n\n"
                + "import java.time.LocalDateTime;\n\n"
                + "/** 审批流水（双引擎投影目标：builtin 直读 sys_flow_task，activiti 由 ACT_HI_* 投影） */\n"
                + "public class FlowRecord {\n"
                + "    private Long id;\n"
                + "    private String flowCode;\n"
                + "    private String bizTable;\n"
                + "    private Long bizId;\n"
                + "    private String fromStatus;\n"
                + "    private String toStatus;\n"
                + "    private String action;\n"
                + "    private String comment;\n"
                + "    private Long operUserId;\n"
                + "    private String operName;\n"
                + "    private LocalDateTime operTime;\n\n"
                + "    public Long getId() { return id; }\n"
                + "    public void setId(Long v) { this.id = v; }\n"
                + "    public String getFlowCode() { return flowCode; }\n"
                + "    public void setFlowCode(String v) { this.flowCode = v; }\n"
                + "    public String getBizTable() { return bizTable; }\n"
                + "    public void setBizTable(String v) { this.bizTable = v; }\n"
                + "    public Long getBizId() { return bizId; }\n"
                + "    public void setBizId(Long v) { this.bizId = v; }\n"
                + "    public String getFromStatus() { return fromStatus; }\n"
                + "    public void setFromStatus(String v) { this.fromStatus = v; }\n"
                + "    public String getToStatus() { return toStatus; }\n"
                + "    public void setToStatus(String v) { this.toStatus = v; }\n"
                + "    public String getAction() { return action; }\n"
                + "    public void setAction(String v) { this.action = v; }\n"
                + "    public String getComment() { return comment; }\n"
                + "    public void setComment(String v) { this.comment = v; }\n"
                + "    public Long getOperUserId() { return operUserId; }\n"
                + "    public void setOperUserId(Long v) { this.operUserId = v; }\n"
                + "    public String getOperName() { return operName; }\n"
                + "    public void setOperName(String v) { this.operName = v; }\n"
                + "    public LocalDateTime getOperTime() { return operTime; }\n"
                + "    public void setOperTime(LocalDateTime v) { this.operTime = v; }\n"
                + "}\n";
    }

    /** 产物 BuiltinFlowService：平台 FlowServiceImpl 移植 + todo/done（M1 直线流语义） */
    private String builtinFlowServiceJava(String pkg) {
        return "package " + pkg + ".common.flow;\n\n"
                + "import " + pkg + ".common.context.UserContext;\n"
                + "import " + pkg + ".common.exception.BusinessException;\n"
                + "import com.fasterxml.jackson.core.type.TypeReference;\n"
                + "import com.fasterxml.jackson.databind.ObjectMapper;\n"
                + "import org.springframework.beans.factory.annotation.Autowired;\n"
                + "import org.springframework.jdbc.core.JdbcTemplate;\n"
                + "import org.springframework.stereotype.Service;\n"
                + "import org.springframework.transaction.annotation.Transactional;\n"
                + "import org.springframework.util.StringUtils;\n\n"
                + "import java.util.ArrayList;\n"
                + "import java.util.LinkedHashMap;\n"
                + "import java.util.List;\n"
                + "import java.util.Map;\n"
                + "import java.util.regex.Pattern;\n\n"
                + "/**\n"
                + " * 内置简易流实现（V2.2 语义 + V6.1 待办扩展）。\n"
                + " * 安全：table_name/status_col 走白名单正则——流程定义被改出注入串也进不了 SQL。\n"
                + " */\n"
                + "@Service\n"
                + "public class BuiltinFlowService implements FlowService {\n\n"
                + "    private static final Pattern IDENT = Pattern.compile(\"^[A-Za-z0-9_]{1,64}$\");\n"
                + "    private static final String DRAFT = \"0\";\n"
                + "    private static final String PENDING = \"1\";\n\n"
                + "    @Autowired\n"
                + "    private JdbcTemplate jdbcTemplate;\n\n"
                + "    @Autowired\n"
                + "    private ObjectMapper objectMapper;\n\n"
                + "    @Override\n"
                + "    @Transactional(rollbackFor = Exception.class)\n"
                + "    public void submit(String flowCode, Long bizId, String comment) {\n"
                + "        Def def = loadDef(flowCode);\n"
                + "        String current = currentStatus(def, bizId);\n"
                + "        if (!DRAFT.equals(current)) {\n"
                + "            throw new BusinessException(400, \"只有草稿状态可以提交，当前状态：\" + current);\n"
                + "        }\n"
                + "        String to = resolveSubmitTarget(def);\n"
                + "        move(def, bizId, current, to, \"SUBMIT\", comment);\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    @Transactional(rollbackFor = Exception.class)\n"
                + "    public void approve(String flowCode, Long bizId, String comment) {\n"
                + "        Def def = loadDef(flowCode);\n"
                + "        String current = currentStatus(def, bizId);\n"
                + "        Map<String, Object> node = findNode(def, current);\n"
                + "        if (node == null) {\n"
                + "            throw new BusinessException(400, \"当前状态没有配置审批节点：\" + current);\n"
                + "        }\n"
                + "        checkRole(node);\n"
                + "        String to = str(node.get(\"to\"));\n"
                + "        if (!StringUtils.hasText(to)) {\n"
                + "            throw new BusinessException(400, \"流程节点缺少目标状态，请检查流程定义\");\n"
                + "        }\n"
                + "        move(def, bizId, current, to, \"APPROVE\", comment);\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    @Transactional(rollbackFor = Exception.class)\n"
                + "    public void reject(String flowCode, Long bizId, String comment) {\n"
                + "        Def def = loadDef(flowCode);\n"
                + "        String current = currentStatus(def, bizId);\n"
                + "        Map<String, Object> node = findNode(def, current);\n"
                + "        if (node == null) {\n"
                + "            throw new BusinessException(400, \"当前状态没有配置审批节点：\" + current);\n"
                + "        }\n"
                + "        checkRole(node);\n"
                + "        String to = str(node.get(\"rejectTo\"));\n"
                + "        if (!StringUtils.hasText(to)) {\n"
                + "            to = DRAFT;\n"
                + "        }\n"
                + "        move(def, bizId, current, to, \"REJECT\", comment);\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public List<FlowRecord> records(String flowCode, Long bizId) {\n"
                + "        if (!StringUtils.hasText(flowCode) || bizId == null) {\n"
                + "            return List.of();\n"
                + "        }\n"
                + "        return jdbcTemplate.query(\n"
                + "                \"SELECT id, flow_code, biz_table, biz_id, from_status, to_status, action, comment, \"\n"
                + "                        + \"oper_user_id, oper_name, oper_time FROM sys_flow_task \"\n"
                + "                        + \"WHERE flow_code = ? AND biz_id = ? ORDER BY oper_time, id\",\n"
                + "                (rs, i) -> {\n"
                + "                    FlowRecord vo = new FlowRecord();\n"
                + "                    vo.setId(rs.getLong(\"id\"));\n"
                + "                    vo.setFlowCode(rs.getString(\"flow_code\"));\n"
                + "                    vo.setBizTable(rs.getString(\"biz_table\"));\n"
                + "                    vo.setBizId(rs.getLong(\"biz_id\"));\n"
                + "                    vo.setFromStatus(rs.getString(\"from_status\"));\n"
                + "                    vo.setToStatus(rs.getString(\"to_status\"));\n"
                + "                    vo.setAction(rs.getString(\"action\"));\n"
                + "                    vo.setComment(rs.getString(\"comment\"));\n"
                + "                    long uid = rs.getLong(\"oper_user_id\");\n"
                + "                    vo.setOperUserId(rs.wasNull() ? null : uid);\n"
                + "                    vo.setOperName(rs.getString(\"oper_name\"));\n"
                + "                    if (rs.getTimestamp(\"oper_time\") != null) {\n"
                + "                        vo.setOperTime(rs.getTimestamp(\"oper_time\").toLocalDateTime());\n"
                + "                    }\n"
                + "                    return vo;\n"
                + "                }, flowCode, bizId);\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public Map<String, Object> state(String flowCode, Long bizId) {\n"
                + "        Def def = loadDef(flowCode);\n"
                + "        Map<String, Object> data = new LinkedHashMap<>();\n"
                + "        data.put(\"flowCode\", def.code);\n"
                + "        data.put(\"flowName\", def.name);\n"
                + "        String current = bizId == null ? DRAFT : currentStatus(def, bizId);\n"
                + "        data.put(\"status\", current);\n"
                + "        data.put(\"nodes\", def.nodes);\n"
                + "        List<String> actions = new ArrayList<>();\n"
                + "        if (DRAFT.equals(current)) {\n"
                + "            actions.add(\"SUBMIT\");\n"
                + "        } else {\n"
                + "            Map<String, Object> node = findNode(def, current);\n"
                + "            if (node != null && StringUtils.hasText(str(node.get(\"to\"))) && roleMatched(node)) {\n"
                + "                actions.add(\"APPROVE\");\n"
                + "                actions.add(\"REJECT\");\n"
                + "            }\n"
                + "        }\n"
                + "        data.put(\"actions\", actions);\n"
                + "        return data;\n"
                + "    }\n\n"
                + "    /** 我的待办：当前状态停在审批节点的单据（产物基座暂无角色体系，登录用户可见全部） */\n"
                + "    @Override\n"
                + "    public List<Map<String, Object>> todo() {\n"
                + "        List<Map<String, Object>> result = new ArrayList<>();\n"
                + "        for (Def def : allDefs()) {\n"
                + "            for (Map<String, Object> node : def.nodes) {\n"
                + "                String from = str(node.get(\"from\"));\n"
                + "                if (!StringUtils.hasText(str(node.get(\"roleCode\"))) || !StringUtils.hasText(from)) {\n"
                + "                    continue;\n"
                + "                }\n"
                + "                result.addAll(jdbcTemplate.queryForList(\n"
                + "                        \"SELECT id AS bizId, '\" + def.code + \"' AS flowCode, '\" + def.name\n"
                + "                                + \"' AS flowName, '\" + from + \"' AS nodeName FROM \" + def.tableName\n"
                + "                                + \" WHERE \" + def.statusCol + \" = ? ORDER BY id DESC LIMIT 200\", from));\n"
                + "            }\n"
                + "        }\n"
                + "        return result;\n"
                + "    }\n\n"
                + "    /** 我的已办：sys_flow_task 里我操作过的流水 */\n"
                + "    @Override\n"
                + "    public List<Map<String, Object>> done() {\n"
                + "        return jdbcTemplate.queryForList(\n"
                + "                \"SELECT flow_code AS flowCode, biz_table AS bizTable, biz_id AS bizId, \"\n"
                + "                        + \"action, comment, oper_time AS operTime FROM sys_flow_task \"\n"
                + "                        + \"WHERE oper_user_id = ? ORDER BY oper_time DESC LIMIT 200\",\n"
                + "                UserContext.getUserId());\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public Map<String, Object> taskDetail(String taskId) {\n"
                + "        // 内置流没有独立任务域：taskId 约定为 flowCode:bizId（前端从待办行拼出来）\n"
                + "        int idx = taskId == null ? -1 : taskId.indexOf(':');\n"
                + "        if (idx <= 0) {\n"
                + "            throw new BusinessException(400, \"任务标识非法，期待 flowCode:bizId\");\n"
                + "        }\n"
                + "        String flowCode = taskId.substring(0, idx);\n"
                + "        Long bizId = Long.valueOf(taskId.substring(idx + 1));\n"
                + "        Def def = loadDef(flowCode);\n"
                + "        Map<String, Object> data = new LinkedHashMap<>();\n"
                + "        data.put(\"flowCode\", flowCode);\n"
                + "        data.put(\"bizId\", bizId);\n"
                + "        data.put(\"bizTable\", def.tableName);\n"
                + "        data.put(\"state\", state(flowCode, bizId));\n"
                + "        data.put(\"records\", records(flowCode, bizId));\n"
                + "        return data;\n"
                + "    }\n\n"
                + "    private void move(Def def, Long bizId, String from, String to, String action, String comment) {\n"
                + "        int n = jdbcTemplate.update(\"UPDATE \" + def.tableName + \" SET \" + def.statusCol\n"
                + "                + \" = ? WHERE id = ?\", to, bizId);\n"
                + "        if (n == 0) {\n"
                + "            throw new BusinessException(404, \"单据不存在：\" + bizId);\n"
                + "        }\n"
                + "        String operName = UserContext.getUsername();\n"
                + "        jdbcTemplate.update(\n"
                + "                \"INSERT INTO sys_flow_task(flow_code, biz_table, biz_id, from_status, to_status, action, \"\n"
                + "                        + \"comment, oper_user_id, oper_name) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)\",\n"
                + "                def.code, def.tableName, bizId, from, to, action, comment,\n"
                + "                UserContext.getUserId(), StringUtils.hasText(operName) ? operName : \"system\");\n"
                + "    }\n\n"
                + "    private String resolveSubmitTarget(Def def) {\n"
                + "        Map<String, Object> node = findNode(def, DRAFT);\n"
                + "        if (node != null && StringUtils.hasText(str(node.get(\"to\")))) {\n"
                + "            return str(node.get(\"to\"));\n"
                + "        }\n"
                + "        return PENDING;\n"
                + "    }\n\n"
                + "    private Map<String, Object> findNode(Def def, String from) {\n"
                + "        for (Map<String, Object> node : def.nodes) {\n"
                + "            if (from.equals(str(node.get(\"from\")))) {\n"
                + "                return node;\n"
                + "            }\n"
                + "        }\n"
                + "        return null;\n"
                + "    }\n\n"
                + "    private void checkRole(Map<String, Object> node) {\n"
                + "        // TODO 产物角色体系引入后按 roleCode 校验（当前基座无角色，恒放行）\n"
                + "    }\n\n"
                + "    private boolean roleMatched(Map<String, Object> node) {\n"
                + "        return true;\n"
                + "    }\n\n"
                + "    private String currentStatus(Def def, Long bizId) {\n"
                + "        List<String> list = jdbcTemplate.queryForList(\n"
                + "                \"SELECT \" + def.statusCol + \" FROM \" + def.tableName + \" WHERE id = ?\",\n"
                + "                String.class, bizId);\n"
                + "        if (list.isEmpty()) {\n"
                + "            throw new BusinessException(404, \"单据不存在：\" + bizId);\n"
                + "        }\n"
                + "        return list.get(0) == null ? DRAFT : list.get(0);\n"
                + "    }\n\n"
                + "    private List<Def> allDefs() {\n"
                + "        List<Map<String, Object>> rows = jdbcTemplate.queryForList(\n"
                + "                \"SELECT flow_code, flow_name, table_name, status_col, nodes FROM sys_flow_def \"\n"
                + "                        + \"WHERE deleted = 0 AND enabled = 1\");\n"
                + "        List<Def> defs = new ArrayList<>();\n"
                + "        for (Map<String, Object> r : rows) {\n"
                + "            try {\n"
                + "                defs.add(toDef(r));\n"
                + "            } catch (Exception ignore) {\n"
                + "                // 单条定义坏了不拖垮整个待办列表\n"
                + "            }\n"
                + "        }\n"
                + "        return defs;\n"
                + "    }\n\n"
                + "    private Def loadDef(String flowCode) {\n"
                + "        if (!StringUtils.hasText(flowCode)) {\n"
                + "            throw new BusinessException(400, \"流程编码不能为空\");\n"
                + "        }\n"
                + "        List<Map<String, Object>> rows = jdbcTemplate.queryForList(\n"
                + "                \"SELECT flow_code, flow_name, table_name, status_col, nodes, enabled \"\n"
                + "                        + \"FROM sys_flow_def WHERE flow_code = ? AND deleted = 0\", flowCode.trim());\n"
                + "        if (rows.isEmpty()) {\n"
                + "            throw new BusinessException(404, \"流程不存在：\" + flowCode);\n"
                + "        }\n"
                + "        Def def = toDef(rows.get(0));\n"
                + "        Object enabled = rows.get(0).get(\"enabled\");\n"
                + "        if (enabled != null && ((Number) enabled).intValue() != 1) {\n"
                + "            throw new BusinessException(400, \"流程已停用：\" + flowCode);\n"
                + "        }\n"
                + "        return def;\n"
                + "    }\n\n"
                + "    private Def toDef(Map<String, Object> r) {\n"
                + "        Def def = new Def();\n"
                + "        def.code = str(r.get(\"flow_code\"));\n"
                + "        def.name = str(r.get(\"flow_name\"));\n"
                + "        def.tableName = str(r.get(\"table_name\"));\n"
                + "        def.statusCol = str(r.get(\"status_col\"));\n"
                + "        if (!IDENT.matcher(def.tableName).matches()) {\n"
                + "            throw new BusinessException(400, \"流程绑定的表名非法：\" + def.tableName);\n"
                + "        }\n"
                + "        if (!IDENT.matcher(def.statusCol).matches()) {\n"
                + "            throw new BusinessException(400, \"流程绑定的状态列非法：\" + def.statusCol);\n"
                + "        }\n"
                + "        def.nodes = parseNodes(str(r.get(\"nodes\")));\n"
                + "        return def;\n"
                + "    }\n\n"
                + "    private List<Map<String, Object>> parseNodes(String json) {\n"
                + "        if (!StringUtils.hasText(json)) {\n"
                + "            return List.of();\n"
                + "        }\n"
                + "        try {\n"
                + "            List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<>() {\n"
                + "            });\n"
                + "            return list == null ? List.of() : list;\n"
                + "        } catch (Exception e) {\n"
                + "            throw new BusinessException(400, \"流程节点 JSON 解析失败：\" + e.getMessage());\n"
                + "        }\n"
                + "    }\n\n"
                + "    private static String str(Object o) {\n"
                + "        return o == null ? \"\" : String.valueOf(o).trim();\n"
                + "    }\n\n"
                + "    private static final class Def {\n"
                + "        String code;\n"
                + "        String name;\n"
                + "        String tableName;\n"
                + "        String statusCol;\n"
                + "        List<Map<String, Object>> nodes = List.of();\n"
                + "    }\n"
                + "}\n";
    }

    /** 产物 ActivitiFlowService：引擎桥接（提交=start，通过=complete+approved=true，驳回=complete+false 回网关） */
    private String activitiFlowServiceJava(String pkg) {
        return "package " + pkg + ".common.flow;\n\n"
                + "import " + pkg + ".common.context.UserContext;\n"
                + "import " + pkg + ".common.exception.BusinessException;\n"
                + "import com.fasterxml.jackson.core.type.TypeReference;\n"
                + "import com.fasterxml.jackson.databind.ObjectMapper;\n"
                + "import org.activiti.engine.HistoryService;\n"
                + "import org.activiti.engine.RuntimeService;\n"
                + "import org.activiti.engine.TaskService;\n"
                + "import org.activiti.engine.history.HistoricTaskInstance;\n"
                + "import org.activiti.engine.task.Task;\n"
                + "import org.springframework.beans.factory.annotation.Autowired;\n"
                + "import org.springframework.jdbc.core.JdbcTemplate;\n"
                + "import org.springframework.stereotype.Service;\n"
                + "import org.springframework.transaction.annotation.Transactional;\n"
                + "import org.springframework.util.StringUtils;\n\n"
                + "import java.util.ArrayList;\n"
                + "import java.util.HashMap;\n"
                + "import java.util.LinkedHashMap;\n"
                + "import java.util.List;\n"
                + "import java.util.Map;\n"
                + "import java.util.regex.Pattern;\n\n"
                + "/**\n"
                + " * V6.1 Activiti 引擎实现。对外契约与 BuiltinFlowService 完全一致。\n"
                + " * 业务状态列仍同步维护（flow_status 同值），保证既有列表查询/打印不感知引擎切换。\n"
                + " */\n"
                + "@Service\n"
                + "public class ActivitiFlowService implements FlowService {\n\n"
                + "    private static final Pattern IDENT = Pattern.compile(\"^[A-Za-z0-9_]{1,64}$\");\n"
                + "    private static final String DRAFT = \"0\";\n\n"
                + "    @Autowired\n"
                + "    private RuntimeService runtimeService;\n\n"
                + "    @Autowired\n"
                + "    private TaskService taskService;\n\n"
                + "    @Autowired\n"
                + "    private HistoryService historyService;\n\n"
                + "    @Autowired\n"
                + "    private JdbcTemplate jdbcTemplate;\n\n"
                + "    @Autowired\n"
                + "    private ObjectMapper objectMapper;\n\n"
                + "    @Override\n"
                + "    @Transactional(rollbackFor = Exception.class)\n"
                + "    public void submit(String flowCode, Long bizId, String comment) {\n"
                + "        Def def = loadDef(flowCode);\n"
                + "        String current = currentStatus(def, bizId);\n"
                + "        if (!DRAFT.equals(current)) {\n"
                + "            throw new BusinessException(400, \"只有草稿状态可以提交，当前状态：\" + current);\n"
                + "        }\n"
                + "        setIdentity();\n"
                + "        Map<String, Object> vars = new HashMap<>();\n"
                + "        vars.put(\"bizId\", bizId);\n"
                + "        vars.put(\"approved\", false);\n"
                + "        runtimeService.startProcessInstanceByKey(flowCode, String.valueOf(bizId), vars);\n"
                + "        writeTask(def, bizId, current, str(firstTo(def)), \"SUBMIT\", comment);\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    @Transactional(rollbackFor = Exception.class)\n"
                + "    public void approve(String flowCode, Long bizId, String comment) {\n"
                + "        complete(flowCode, bizId, true, comment);\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    @Transactional(rollbackFor = Exception.class)\n"
                + "    public void reject(String flowCode, Long bizId, String comment) {\n"
                + "        complete(flowCode, bizId, false, comment);\n"
                + "    }\n\n"
                + "    private void complete(String flowCode, Long bizId, boolean approved, String comment) {\n"
                + "        Def def = loadDef(flowCode);\n"
                + "        setIdentity();\n"
                + "        Task task = taskService.createTaskQuery()\n"
                + "                .processInstanceBusinessKey(String.valueOf(bizId)).singleResult();\n"
                + "        if (task == null) {\n"
                + "            throw new BusinessException(400, \"该单据没有进行中的审批任务\");\n"
                + "        }\n"
                + "        String from = String.valueOf(taskService.getVariable(task.getId(), \"nodeStatus\"));\n"
                + "        Map<String, Object> vars = new HashMap<>();\n"
                + "        vars.put(\"approved\", approved);\n"
                + "        taskService.complete(task.getId(), vars);\n"
                + "        // 状态列跟随网关走向：通过=节点 to；驳回=rejectTo 或草稿（与 BPMN 网关条件一一对应）\n"
                + "        String to;\n"
                + "        if (approved) {\n"
                + "            Map<String, Object> node = findNode(def, from);\n"
                + "            to = node == null ? null : str(node.get(\"to\"));\n"
                + "        } else {\n"
                + "            Map<String, Object> node = findNode(def, from);\n"
                + "            to = node != null && StringUtils.hasText(str(node.get(\"rejectTo\")))\n"
                + "                    ? str(node.get(\"rejectTo\")) : DRAFT;\n"
                + "        }\n"
                + "        writeTask(def, bizId, from, to == null ? from : to, approved ? \"APPROVE\" : \"REJECT\", comment);\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public List<FlowRecord> records(String flowCode, Long bizId) {\n"
                + "        // 兼容视图：ACT_HI_TASKINST 投影成 FlowRecord（旧前端零感知）\n"
                + "        List<FlowRecord> list = new ArrayList<>();\n"
                + "        for (HistoricTaskInstance h : historyService.createHistoricTaskInstanceQuery()\n"
                + "                .processInstanceBusinessKey(String.valueOf(bizId))\n"
                + "                .processDefinitionKey(flowCode)\n"
                + "                .finished().orderByHistoricTaskInstanceEndTime().asc().list()) {\n"
                + "            FlowRecord vo = new FlowRecord();\n"
                + "            vo.setFlowCode(flowCode);\n"
                + "            vo.setBizId(bizId);\n"
                + "            vo.setBizTable(h.getFormKey());\n"
                + "            vo.setAction(h.getDeleteReason() != null ? \"REJECT\" : \"APPROVE\");\n"
                + "            vo.setComment(h.getDescription());\n"
                + "            vo.setOperName(h.getAssignee());\n"
                + "            if (h.getEndTime() != null) {\n"
                + "                vo.setOperTime(h.getEndTime().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());\n"
                + "            }\n"
                + "            list.add(vo);\n"
                + "        }\n"
                + "        // 引擎历史之外保留 sys_flow_task 直写流水（SUBMIT 行等），按时间合并\n"
                + "        list.addAll(new BuiltinQuery().records(flowCode, bizId));\n"
                + "        list.sort((a, b) -> a.getOperTime() == null || b.getOperTime() == null ? 0\n"
                + "                : a.getOperTime().compareTo(b.getOperTime()));\n"
                + "        return list;\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public Map<String, Object> state(String flowCode, Long bizId) {\n"
                + "        Def def = loadDef(flowCode);\n"
                + "        Map<String, Object> data = new LinkedHashMap<>();\n"
                + "        data.put(\"flowCode\", flowCode);\n"
                + "        data.put(\"flowName\", def.name);\n"
                + "        data.put(\"status\", bizId == null ? DRAFT : currentStatus(def, bizId));\n"
                + "        data.put(\"nodes\", def.nodes);\n"
                + "        return data;\n"
                + "    }\n\n"
                + "    /** 我的待办：全部进行中的引擎任务（产物基座暂无角色体系，登录用户可见全部） */\n"
                + "    @Override\n"
                + "    public List<Map<String, Object>> todo() {\n"
                + "        setIdentity();\n"
                + "        List<Map<String, Object>> result = new ArrayList<>();\n"
                + "        for (Task t : taskService.createTaskQuery().list()) {\n"
                + "                Map<String, Object> row = new LinkedHashMap<>();\n"
                + "                row.put(\"taskId\", t.getId());\n"
                + "                row.put(\"flowCode\", t.getProcessDefinitionId().split(\":\")[0]);\n"
                + "                row.put(\"bizId\", tService(t));\n"
                + "                row.put(\"nodeName\", t.getName());\n"
                + "                row.put(\"createTime\", t.getCreateTime());\n"
                + "                result.add(row);\n"
                + "        }\n"
                + "        return result;\n"
                + "    }\n\n"
                + "    private Object tService(Task t) {\n"
                + "        Object bizId = runtimeService.getVariable(t.getProcessInstanceId(), \"bizId\");\n"
                + "        return bizId == null ? t.getBusinessKey() : bizId;\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public List<Map<String, Object>> done() {\n"
                + "        List<Map<String, Object>> result = new ArrayList<>();\n"
                + "        for (HistoricTaskInstance h : historyService.createHistoricTaskInstanceQuery()\n"
                + "                .taskAssignee(UserContext.getUsername()).finished()\n"
                + "                .orderByHistoricTaskInstanceEndTime().desc().list()) {\n"
                + "            Map<String, Object> row = new LinkedHashMap<>();\n"
                + "            row.put(\"flowCode\", h.getProcessDefinitionId().split(\":\")[0]);\n"
                + "            row.put(\"bizId\", h.getBusinessKey());\n"
                + "            row.put(\"nodeName\", h.getName());\n"
                + "            if (h.getEndTime() != null) {\n"
                + "                row.put(\"operTime\", h.getEndTime().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());\n"
                + "            }\n"
                + "            result.add(row);\n"
                + "        }\n"
                + "        return result;\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public Map<String, Object> taskDetail(String taskId) {\n"
                + "        Task t = taskService.createTaskQuery().taskId(taskId).singleResult();\n"
                + "        if (t == null) {\n"
                + "            throw new BusinessException(404, \"任务不存在或已办结\");\n"
                + "        }\n"
                + "        Map<String, Object> data = new LinkedHashMap<>();\n"
                + "        data.put(\"taskId\", t.getId());\n"
                + "        data.put(\"nodeName\", t.getName());\n"
                + "        data.put(\"flowCode\", t.getProcessDefinitionId().split(\":\")[0]);\n"
                + "        data.put(\"bizId\", t.getBusinessKey());\n"
                + "        return data;\n"
                + "    }\n\n"
                + "    private void setIdentity() {\n"
                + "        String name = UserContext.getUsername();\n"
                + "        org.activiti.engine.impl.identity.Authentication.setAuthenticatedUserId(\n"
                + "                StringUtils.hasText(name) ? name : \"system\");\n"
                + "    }\n\n"
                + "    private String firstTo(Def def) {\n"
                + "        for (Map<String, Object> node : def.nodes) {\n"
                + "            if (DRAFT.equals(str(node.get(\"from\")))) {\n"
                + "                return str(node.get(\"to\"));\n"
                + "            }\n"
                + "        }\n"
                + "        return \"1\";\n"
                + "    }\n\n"
                + "    private Map<String, Object> findNode(Def def, String from) {\n"
                + "        for (Map<String, Object> node : def.nodes) {\n"
                + "            if (from.equals(str(node.get(\"from\")))) {\n"
                + "                return node;\n"
                + "            }\n"
                + "        }\n"
                + "        return null;\n"
                + "    }\n\n"
                + "    private static String str(Object o) {\n"
                + "        return o == null ? \"\" : String.valueOf(o).trim();\n"
                + "    }\n\n"
                + "    private void writeTask(Def def, Long bizId, String from, String to, String action, String comment) {\n"
                + "        jdbcTemplate.update(\"UPDATE \" + def.tableName + \" SET \" + def.statusCol\n"
                + "                + \" = ? WHERE id = ?\", to, bizId);\n"
                + "        jdbcTemplate.update(\n"
                + "                \"INSERT INTO sys_flow_task(flow_code, biz_table, biz_id, from_status, to_status, action, \"\n"
                + "                        + \"comment, oper_user_id, oper_name) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)\",\n"
                + "                def.code, def.tableName, bizId, from, to, action, comment,\n"
                + "                UserContext.getUserId(), UserContext.getUsername());\n"
                + "    }\n\n"
                + "    private String currentStatus(Def def, Long bizId) {\n"
                + "        List<String> list = jdbcTemplate.queryForList(\n"
                + "                \"SELECT \" + def.statusCol + \" FROM \" + def.tableName + \" WHERE id = ?\",\n"
                + "                String.class, bizId);\n"
                + "        return list.isEmpty() ? DRAFT : (list.get(0) == null ? DRAFT : list.get(0));\n"
                + "    }\n\n"
                + "    private Def loadDef(String flowCode) {\n"
                + "        if (!StringUtils.hasText(flowCode) || !IDENT.matcher(flowCode.trim()).matches()) {\n"
                + "            throw new BusinessException(400, \"流程编码非法：\" + flowCode);\n"
                + "        }\n"
                + "        List<Map<String, Object>> rows = jdbcTemplate.queryForList(\n"
                + "                \"SELECT flow_code, flow_name, table_name, status_col, nodes FROM sys_flow_def \"\n"
                + "                        + \"WHERE flow_code = ? AND deleted = 0 AND enabled = 1\", flowCode.trim());\n"
                + "        if (rows.isEmpty()) {\n"
                + "            throw new BusinessException(404, \"流程不存在或未启用：\" + flowCode);\n"
                + "        }\n"
                + "        Map<String, Object> r = rows.get(0);\n"
                + "        Def def = new Def();\n"
                + "        def.code = String.valueOf(r.get(\"flow_code\"));\n"
                + "        def.name = String.valueOf(r.get(\"flow_name\"));\n"
                + "        def.tableName = String.valueOf(r.get(\"table_name\"));\n"
                + "        def.statusCol = String.valueOf(r.get(\"status_col\"));\n"
                + "        try {\n"
                + "            def.nodes = objectMapper.readValue(String.valueOf(r.get(\"nodes\")),\n"
                + "                    new TypeReference<List<Map<String, Object>>>() {\n"
                + "                    });\n"
                + "        } catch (Exception e) {\n"
                + "            throw new BusinessException(400, \"流程节点 JSON 解析失败：\" + e.getMessage());\n"
                + "        }\n"
                + "        return def;\n"
                + "    }\n\n"
                + "    /** records 合并用的直读小工具（复用 sys_flow_task 行结构） */\n"
                + "    private class BuiltinQuery {\n"                + "        List<FlowRecord> records(String flowCode, Long bizId) {\n"
                + "            List<FlowRecord> list = new ArrayList<>();\n"
                + "            for (Map<String, Object> row : jdbcTemplate.queryForList(\n"
                + "                    \"SELECT action, comment, oper_name, oper_time FROM sys_flow_task \"\n"
                + "                            + \"WHERE flow_code = ? AND biz_id = ? ORDER BY oper_time, id\", flowCode, bizId)) {\n"
                + "                FlowRecord vo = new FlowRecord();\n"
                + "                vo.setFlowCode(flowCode);\n"
                + "                vo.setBizId(bizId);\n"
                + "                vo.setBizTable(ActivitiFlowService.this.loadDef(flowCode).tableName);\n"
                + "                vo.setAction(String.valueOf(row.get(\"action\")));\n"
                + "                vo.setComment((String) row.get(\"comment\"));\n"
                + "                vo.setOperName((String) row.get(\"oper_name\"));\n"
                + "                Object t = row.get(\"oper_time\");\n"
                + "                if (t instanceof java.sql.Timestamp ts) {\n"
                + "                    vo.setOperTime(ts.toLocalDateTime());\n"
                + "                }\n"
                + "                list.add(vo);\n"
                + "            }\n"
                + "            return list;\n"
                + "        }\n"
                + "    }\n\n"
                + "    /** 流程定义的内存视图（与 BuiltinFlowService 同构） */\n"
                + "    private static final class Def {\n"
                + "        String code;\n"
                + "        String name;\n"
                + "        String tableName;\n"
                + "        String statusCol;\n"
                + "        List<Map<String, Object>> nodes = List.of();\n"
                + "    }\n"
                + "}\n";
    }

    /** 产物 FlowDeployRunner：启动时把 sys_flow_def.bpmn_xml 幂等部署进引擎（hash 相同跳过） */
    private String flowDeployRunnerJava(String pkg) {
        return "package " + pkg + ".common.flow;\n\n"
                + "import com.fasterxml.jackson.databind.ObjectMapper;\n"
                + "import org.activiti.engine.RepositoryService;\n"
                + "import org.activiti.engine.repository.Deployment;\n"
                + "import org.slf4j.Logger;\n"
                + "import org.slf4j.LoggerFactory;\n"
                + "import org.springframework.beans.factory.annotation.Autowired;\n"
                + "import org.springframework.boot.ApplicationArguments;\n"
                + "import org.springframework.boot.ApplicationRunner;\n"
                + "import org.springframework.jdbc.core.JdbcTemplate;\n"
                + "import org.springframework.stereotype.Component;\n"
                + "import org.springframework.util.StringUtils;\n\n"
                + "import java.nio.charset.StandardCharsets;\n"
                + "import java.security.MessageDigest;\n"
                + "import java.util.HexFormat;\n"
                + "import java.util.List;\n"
                + "import java.util.Map;\n\n"
                + "/**\n"
                + " * 启动部署器：逐条 sys_flow_def 检查 bpmn_xml 的 sha256，与已部署 hash 不同才重新部署。\n"
                + " * 幂等：重启零部署；平台改流程定义后重新生成/重导 init_sys_flow.sql 即自动升级版本。\n"
                + " */\n"
                + "@Component\n"
                + "public class FlowDeployRunner implements ApplicationRunner {\n\n"
                + "    private static final Logger log = LoggerFactory.getLogger(FlowDeployRunner.class);\n\n"
                + "    @Autowired\n"
                + "    private RepositoryService repositoryService;\n\n"
                + "    @Autowired\n"
                + "    private JdbcTemplate jdbcTemplate;\n\n"
                + "    @Override\n"
                + "    public void run(ApplicationArguments args) {\n"
                + "        List<Map<String, Object>> defs;\n"
                + "        try {\n"
                + "            defs = jdbcTemplate.queryForList(\n"
                + "                    \"SELECT id, flow_code, flow_name, bpmn_xml, bpmn_hash FROM sys_flow_def \"\n"
                + "                            + \"WHERE deleted = 0 AND enabled = 1 AND bpmn_xml IS NOT NULL\");\n"
                + "        } catch (Exception e) {\n"
                + "            log.warn(\"流程定义表不可读，跳过 BPMN 部署: {}\", e.getMessage());\n"
                + "            return;\n"
                + "        }\n"
                + "        for (Map<String, Object> def : defs) {\n"
                + "            String xml = (String) def.get(\"bpmn_xml\");\n"
                + "            String code = String.valueOf(def.get(\"flow_code\"));\n"
                + "            if (!StringUtils.hasText(xml)) {\n"
                + "                continue;\n"
                + "            }\n"
                + "            String hash = sha256(xml);\n"
                + "            if (hash.equals(def.get(\"bpmn_hash\"))) {\n"
                + "                continue;\n"
                + "            }\n"
                + "            try {\n"
                + "                Deployment d = repositoryService.createDeployment()\n"
                + "                        .key(code + \"-\" + hash.substring(0, 8))\n"
                + "                        .name(String.valueOf(def.get(\"flow_name\")))\n"
                + "                        .addString(code + \".bpmn20.xml\", xml)\n"
                + "                        .deploy();\n"
                + "                jdbcTemplate.update(\"UPDATE sys_flow_def SET bpmn_hash = ? WHERE id = ?\",\n"
                + "                        hash, ((Number) def.get(\"id\")).longValue());\n"
                + "                log.info(\"BPMN 流程已部署: {} -> deployment {}\", code, d.getId());\n"
                + "            } catch (Exception e) {\n"
                + "                log.error(\"BPMN 部署失败（不阻断启动）: {} - {}\", code, e.getMessage());\n"
                + "            }\n"
                + "        }\n"
                + "    }\n\n"
                + "    private String sha256(String text) {\n"
                + "        try {\n"
                + "            byte[] digest = MessageDigest.getInstance(\"SHA-256\")\n"
                + "                    .digest(text.getBytes(StandardCharsets.UTF_8));\n"
                + "            return HexFormat.of().formatHex(digest);\n"
                + "        } catch (Exception e) {\n"
                + "            throw new IllegalStateException(e);\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
    }

    /** 产物 FlowController：全局 /flow 端点（apiJs.ftl 的 body 风格四端点契约 + V6.1 三端点） */
    private String flowControllerJava(String pkg) {
        return "package " + pkg + ".common.flow;\n\n"
                + "import " + pkg + ".common.result.Result;\n"
                + "import io.swagger.v3.oas.annotations.Operation;\n"
                + "import org.springframework.beans.factory.annotation.Autowired;\n"
                + "import org.springframework.web.bind.annotation.*;\n\n"
                + "import java.util.List;\n"
                + "import java.util.Map;\n\n"
                + "/**\n"
                + " * 全局审批流端点：前端 flowSubmit/flowApprove 等走 body 风格（flowCode+bizId），\n"
                + " * 与各业务 controller 的 /{module}/flow/* 路径风格互补，两套并存契约都成立。\n"
                + " */\n"
                + "@RestController\n"
                + "@RequestMapping(\"/flow\")\n"
                + "public class FlowController {\n\n"
                + "    @Autowired\n"
                + "    private FlowService flowService;\n\n"
                + "    @Operation(summary = \"提交审批\")\n"
                + "    @PostMapping(\"/submit\")\n"
                + "    public Result<Void> submit(@RequestBody Map<String, Object> body) {\n"
                + "        flowService.submit((String) body.get(\"flowCode\"), asLong(body.get(\"bizId\")),\n"
                + "                (String) body.get(\"comment\"));\n"
                + "        return Result.ok();\n"
                + "    }\n\n"
                + "    @Operation(summary = \"审批通过\")\n"
                + "    @PostMapping(\"/approve\")\n"
                + "    public Result<Void> approve(@RequestBody Map<String, Object> body) {\n"
                + "        flowService.approve((String) body.get(\"flowCode\"), asLong(body.get(\"bizId\")),\n"
                + "                (String) body.get(\"comment\"));\n"
                + "        return Result.ok();\n"
                + "    }\n\n"
                + "    @Operation(summary = \"审批驳回\")\n"
                + "    @PostMapping(\"/reject\")\n"
                + "    public Result<Void> reject(@RequestBody Map<String, Object> body) {\n"
                + "        flowService.reject((String) body.get(\"flowCode\"), asLong(body.get(\"bizId\")),\n"
                + "                (String) body.get(\"comment\"));\n"
                + "        return Result.ok();\n"
                + "    }\n\n"
                + "    @Operation(summary = \"审批记录\")\n"
                + "    @GetMapping(\"/records\")\n"
                + "    public Result<List<FlowRecord>> records(@RequestParam String flowCode,\n"
                + "                                            @RequestParam Long bizId) {\n"
                + "        return Result.ok(flowService.records(flowCode, bizId));\n"
                + "    }\n\n"
                + "    @Operation(summary = \"流程状态\")\n"
                + "    @GetMapping(\"/state\")\n"
                + "    public Result<Map<String, Object>> state(@RequestParam String flowCode,\n"
                + "                                             @RequestParam(required = false) Long bizId) {\n"
                + "        return Result.ok(flowService.state(flowCode, bizId));\n"
                + "    }\n\n"
                + "    @Operation(summary = \"我的待办（V6.1）\")\n"
                + "    @GetMapping(\"/todo\")\n"
                + "    public Result<List<Map<String, Object>>> todo() {\n"
                + "        return Result.ok(flowService.todo());\n"
                + "    }\n\n"
                + "    @Operation(summary = \"我的已办（V6.1）\")\n"
                + "    @GetMapping(\"/done\")\n"
                + "    public Result<List<Map<String, Object>>> done() {\n"
                + "        return Result.ok(flowService.done());\n"
                + "    }\n\n"
                + "    @Operation(summary = \"任务详情（V6.1）\")\n"
                + "    @GetMapping(\"/task/{taskId}\")\n"
                + "    public Result<Map<String, Object>> taskDetail(@PathVariable String taskId) {\n"
                + "        return Result.ok(flowService.taskDetail(taskId));\n"
                + "    }\n\n"
                + "    private Long asLong(Object v) {\n"
                + "        return v == null ? null : Long.valueOf(String.valueOf(v));\n"
                + "    }\n"
                + "}\n";
    }

    /**
     * 产物库流程表 DDL + 定义 INSERT。恒写（builtin 也要表）；bpmn_xml/bpmn_hash 只在 activiti 定义里有值。
     */
    private String initSysFlowSql(java.util.List<com.codegen.dto.GenScaffoldDTO.FlowDef> defs) {
        StringBuilder sql = new StringBuilder(1024);
        sql.append("-- V6.1 产物流程表（幂等：CREATE TABLE IF NOT EXISTS）\n")
                .append("CREATE TABLE IF NOT EXISTS `sys_flow_def` (\n")
                .append("  `id` bigint NOT NULL,\n")
                .append("  `flow_code` varchar(64) NOT NULL COMMENT '流程编码',\n")
                .append("  `flow_name` varchar(64) NOT NULL COMMENT '流程名称',\n")
                .append("  `table_name` varchar(64) NOT NULL COMMENT '业务表名',\n")
                .append("  `status_col` varchar(64) NOT NULL DEFAULT 'status' COMMENT '状态列',\n")
                .append("  `nodes` varchar(1024) NOT NULL COMMENT '节点 JSON [{from,to,roleCode,rejectTo}]',\n")
                .append("  `bpmn_xml` mediumtext NULL COMMENT 'V6.1 自动翻译的 BPMN XML',\n")
                .append("  `bpmn_hash` varchar(64) DEFAULT NULL COMMENT '已部署的 bpmn_xml sha256（幂等部署标记）',\n")
                .append("  `enabled` tinyint NOT NULL DEFAULT 1,\n")
                .append("  `remark` varchar(255) DEFAULT NULL,\n")
                .append("  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,\n")
                .append("  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n")
                .append("  `deleted` tinyint NOT NULL DEFAULT 0,\n")
                .append("  PRIMARY KEY (`id`),\n")
                .append("  UNIQUE KEY `uk_flow_code` (`flow_code`, `deleted`)\n")
                .append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程定义';\n\n")
                .append("CREATE TABLE IF NOT EXISTS `sys_flow_task` (\n")
                .append("  `id` bigint NOT NULL,\n")
                .append("  `flow_code` varchar(64) NOT NULL,\n")
                .append("  `biz_table` varchar(64) NOT NULL,\n")
                .append("  `biz_id` bigint NOT NULL,\n")
                .append("  `from_status` varchar(20) DEFAULT NULL,\n")
                .append("  `to_status` varchar(20) DEFAULT NULL,\n")
                .append("  `action` varchar(20) NOT NULL,\n")
                .append("  `comment` varchar(255) DEFAULT NULL,\n")
                .append("  `oper_user_id` bigint DEFAULT NULL,\n")
                .append("  `oper_name` varchar(64) DEFAULT NULL,\n")
                .append("  `oper_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,\n")
                .append("  PRIMARY KEY (`id`),\n")
                .append("  KEY `idx_flow_biz` (`flow_code`, `biz_id`)\n")
                .append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流水';\n");
        if (defs == null) {
            return sql.toString();
        }
        for (com.codegen.dto.GenScaffoldDTO.FlowDef d : defs) {
            if (d.getFlowCode() == null || !d.getFlowCode().matches("[A-Za-z0-9_]{1,64}")) {
                continue;
            }
            String nodesJson = "[]";
            try {
                nodesJson = new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(d.getNodes() == null ? List.of() : d.getNodes());
            } catch (Exception ignore) {
                // 序列化失败按空节点下发，产物侧报错可读
            }
            String bpmn = d.getBpmnXml() == null ? "NULL" : "'" + escSql(d.getBpmnXml()) + "'";
            sql.append("\n-- 流程定义：").append(d.getFlowName() == null ? d.getFlowCode() : d.getFlowName())
                    .append("\nINSERT INTO sys_flow_def (id, flow_code, flow_name, table_name, status_col, nodes, bpmn_xml, enabled)\n")
                    .append("SELECT ").append(snowflakeish()).append(", '").append(escSql(d.getFlowCode()))
                    .append("', '").append(escSql(d.getFlowName() == null ? d.getFlowCode() : d.getFlowName()))
                    .append("', '").append(escSql(d.getTableName() == null ? "" : d.getTableName()))
                    .append("', '").append(escSql(d.getStatusCol() == null || d.getStatusCol().isEmpty()
                            ? "status" : d.getStatusCol()))
                    .append("', '").append(escSql(nodesJson)).append("', ")
                    .append(bpmn).append(", 1\n")
                    .append("WHERE NOT EXISTS (SELECT 1 FROM sys_flow_def WHERE flow_code = '")
                    .append(escSql(d.getFlowCode())).append(" AND deleted = 0);\n");
        }
        return sql.toString();
    }

    /** SQL 字面量转义（单引号与反斜杠） */
    private static String escSql(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("'", "\\'");
    }

    /** 轻量 id：时间戳左移 8 位 + 3 位随机，够产物定义行用且不依赖雪花框架 */
    private static String snowflakeish() {
        return String.valueOf(System.currentTimeMillis() * 1000 % 1_000_000_000_000L * 1000
                + java.util.concurrent.ThreadLocalRandom.current().nextInt(1000));
    }

    // ============================ V6.2 部署物 ============================

    private String dockerfileText() {
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

    private String dockerignoreText() {
        return "target/\nlogs/\ndb/\ndeploy/\n*.log\n.idea/\n*.iml\n";
    }

    private String dockerComposeYml(String project, int serverPort, boolean micro) {
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

    private String ciGithubActionsText() {
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

    private String jenkinsfileText() {
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
    private String payResource(String file, String pkg) {
        try {
            org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource("scaffold-pay/" + file);
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return content.replace("__PKG__", pkg);
        } catch (java.io.IOException e) {
            throw new com.codegen.common.exception.BusinessException(500, "支付模块源码资源缺失：scaffold-pay/" + file);
        }
    }

    /** V6.6 文件能力包：源码资源读取（V6.3 payResource 同款模式，__PKG__ 占位替换） */
    private String fileResource(String file, String pkg) {
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
    private String testResource(String file, String pkg) {
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
    private String fileApiPermsJava(String pkg) {
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
    private static final List<String> MOBILE_FILES = List.of(
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
    private Map<String, String> mobileResources(String project, int apiPort, String gwPrefix) {
        return mobileResources(project, apiPort, gwPrefix,
                com.codegen.config.LanguageConfig.DEFAULT_LANGUAGE, "ltr");
    }

    private Map<String, String> mobileResources(String project, int apiPort, String gwPrefix,
                                                String htmlLang, String htmlDir) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (String f : MOBILE_FILES) {
            out.put(f, mobileResource(f, project, apiPort, gwPrefix, htmlLang, htmlDir));
        }
        out.put(".gitignore", mobileGitignore());
        return out;
    }

    private String mobileGitignore() {
        return "node_modules/\ndist/\n.hbuilderx/\n.DS_Store\n*.local\n";
    }

    /** 读 classpath scaffold-mobile/&lt;file&gt;，__PROJECT__/__API_PORT__/__GW__ 占位替换 */
    private String mobileResource(String file, String project, int apiPort, String gwPrefix,
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

    private String pomXml(GenScaffoldDTO dto, String appName, String project, String pkg, String module, int serverPort,
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

    /**
     * V5.5 多模块：内部模块依赖坐标（{@code <rootPackage-stem>} groupId + {@code <stem>-<module>}）。
     * 版本由聚合 pom 的 dependencyManagement 统一管，子模块不写 version。
     */
    private String internalDep(String pkg, String stem, String module) {
        return dep(pkg, stem + "-" + module, null);
    }

    private String extraDepsXml(GenScaffoldDTO dto) {
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
    private String pomMultiParent(String appName, String project, String pkg, String stem, String backendModule, boolean micro, boolean activiti, boolean payEnabled) {
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
    private String pomMultiModule(String parentGroupId, String parentArtifactId, String stem, String module,
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

    private String applicationJava(String pkg, String appClass, boolean micro, boolean activiti,
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
    private String gatewayApplicationJava(String pkg, String appClass) {
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
    private String gatewayApplicationYml(String pkg, String stem, int serverPort) {
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
    private String remoteSampleClientJava(String pkg, String stem) {
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
    private String bootstrapYml(String stem) {
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
                + "    private static final ThreadLocal<String> DATA_SCOPE_HOLDER = new ThreadLocal<>();\n\n"
                + "    private static final ThreadLocal<Long> DEPT_ID_HOLDER = new ThreadLocal<>();\n\n"
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
                + "    /**\n"
                + "     * 数据权限范围：all / dept / self。\n"
                + "     * 未显式设置时按 self 兜底——漏配宁严不松，不能因为没设置就看到全量。\n"
                + "     */\n"
                + "    public static String getDataScope() {\n"
                + "        String scope = DATA_SCOPE_HOLDER.get();\n"
                + "        return scope == null || scope.isBlank() ? \"self\" : scope;\n"
                + "    }\n\n"
                + "    public static void setDataScope(String dataScope) {\n"
                + "        DATA_SCOPE_HOLDER.set(dataScope);\n"
                + "    }\n\n"
                + "    public static void setDeptId(Long deptId) {\n"
                + "        DEPT_ID_HOLDER.set(deptId);\n"
                + "    }\n\n"
                + "    public static Long getDeptId() {\n"
                + "        return DEPT_ID_HOLDER.get();\n"
                + "    }\n\n"
                + "    public static void clear() {\n"
                + "        USER_ID_HOLDER.remove();\n"
                + "        USERNAME_HOLDER.remove();\n"
                + "        DATA_SCOPE_HOLDER.remove();\n"
                + "        DEPT_ID_HOLDER.remove();\n"
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

    /**
     * IP 归属地解析器：模板放 resources/scaffold/backend，改内容不用碰 Java 拼接。
     */
    private String ipRegionResolverJava(String pkg) {
        return loadScaffoldResource("scaffold/backend/IpRegionResolver.java.tpl")
                .replace("__PKG__", pkg);
    }

    /**
     * 离线 IP 库查询器：与解析器配套，缺了它解析器会静默降级成纯在线。
     */
    private String ip2RegionSearcherJava(String pkg) {
        return loadScaffoldResource("scaffold/backend/Ip2RegionSearcher.java.tpl")
                .replace("__PKG__", pkg);
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
                + "            \"(?i)\\\"(password|passwd|pwd|token|secret|authorization|credential)\\\"\\\\s*:\\\\s*\"\n"
                + "                    + \"(\\\"(?:[^\\\"\\\\\\\\]|\\\\\\\\.)*\\\"|[^,}\\\\]\\\\s]+)\");\n\n"
                + "    @Autowired\n"
                + "    private OpeLogMapper opeLogMapper;\n\n"
                + "    @Autowired\n"
                + "    private ObjectMapper objectMapper;\n\n"
                + "    @Autowired\n"
                + "    private IpRegionResolver ipRegionResolver;\n\n"
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
                + "        String ip = null;\n"
                + "        if (request != null) {\n"
                + "            entity.setRequestUri(request.getRequestURI());\n"
                + "            entity.setRequestMethod(request.getMethod());\n"
                + "            ip = clientIp(request);\n"
                + "            entity.setOperIp(ip);\n"
                + "        }\n"
                + "        entity.setOperType(inferType(operLog.type(), request, signature.getName()));\n"
                + "        if (operLog.saveParam()) {\n"
                + "            entity.setOperParam(truncate(toJson(sanitize(point.getArgs()))));\n"
                + "        }\n"
                + "        if (operLog.saveResult() && result != null) {\n"
                + "            entity.setJsonResult(truncate(toJson(result)));\n"
                + "        }\n"
                + "        opeLogMapper.insert(entity);\n\n"
                + "        // 省市由 IP 反查：审计时只看 IP 判断不了地域，但查询要走网络，\n"
                + "        // 必须异步回填，否则每条日志都让业务接口慢一次网络往返\n"
                + "        if (ip != null) {\n"
                + "            Long logId = entity.getId();\n"
                + "            ipRegionResolver.resolveAsync(ip, region -> {\n"
                + "                OpeLog patch = new OpeLog();\n"
                + "                patch.setId(logId);\n"
                + "                patch.setProvince(region.province());\n"
                + "                patch.setCity(region.city());\n"
                + "                opeLogMapper.updateById(patch);\n"
                + "            });\n"
                + "        }\n"
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
                + "            return maskSensitive(objectMapper.writeValueAsString(value));\n"
                + "        } catch (Exception e) {\n"
                + "            return String.valueOf(value);\n"
                + "        }\n"
                + "    }\n\n"
                + "    /**\n"
                + "     * 按字段名脱敏：只换值不换键，正常业务文本里出现 password 三个字不会被误伤。\n"
                + "     * 匹配必须连值一起：只替换字段名会留下 {\"password\":\"****\":\"真实口令\"}。\n"
                + "     */\n"
                + "    static String maskSensitive(String json) {\n"
                + "        if (json == null) {\n"
                + "            return null;\n"
                + "        }\n"
                + "        return SENSITIVE.matcher(json).replaceAll(m -> \"\\\"\" + m.group(1) + \"\\\":\\\"****\\\"\");\n"
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
                + "    /**\n"
                + "     * 省份（由 IP 解析，内网/本机时记「内网」「本机」）\n"
                + "     */\n"
                + "    private String province;\n\n"
                + "    /**\n"
                + "     * 城市（由 IP 解析）\n"
                + "     */\n"
                + "    private String city;\n\n"
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

    /**
     * 时间序列化统一格式：LocalDateTime 输出 yyyy-MM-dd HH:mm:ss，
     * 避免前端拿到 ISO 默认格式（2026-10-21T13:31:22，中间带 T）。
     */
    private String timeJacksonConfigJava(String pkg) {
        return "package " + pkg + ".common.config;\n\n"
                + "import com.fasterxml.jackson.databind.Module;\n"
                + "import com.fasterxml.jackson.databind.module.SimpleModule;\n"
                + "import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;\n"
                + "import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;\n"
                + "import org.springframework.context.annotation.Bean;\n"
                + "import org.springframework.context.annotation.Configuration;\n\n"
                + "import java.time.LocalDateTime;\n"
                + "import java.time.format.DateTimeFormatter;\n\n"
                + "/**\n"
                + " * 时间序列化统一格式：LocalDateTime 输出 yyyy-MM-dd HH:mm:ss。\n"
                + " * 由 codegen-server 脚手架生成。\n"
                + " */\n"
                + "@Configuration\n"
                + "public class TimeJacksonConfig {\n\n"
                + "    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(\"yyyy-MM-dd HH:mm:ss\");\n\n"
                + "    @Bean\n"
                + "    public Module localDateTimeModule() {\n"
                + "        return new SimpleModule()\n"
                + "                .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(FORMATTER))\n"
                + "                .addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(FORMATTER));\n"
                + "    }\n"
                + "}\n";
    }

    /**
     * 字典翻译组件：dict_code + value -> label，导出/展示用。
     * 结果按 code 缓存，字典变更后重启生效（导出场景足够）。
     */
    private String dictTextsJava(String pkg) {
        return "package " + pkg + ".common.util;\n\n"
                + "import org.springframework.beans.factory.annotation.Autowired;\n"
                + "import org.springframework.jdbc.core.JdbcTemplate;\n"
                + "import org.springframework.stereotype.Component;\n\n"
                + "import java.util.HashMap;\n"
                + "import java.util.Map;\n"
                + "import java.util.concurrent.ConcurrentHashMap;\n\n"
                + "/**\n"
                + " * 导出/展示用的字典翻译：dict_code + value -> label。\n"
                + " * 由 codegen-server 脚手架生成；结果按 code 缓存，字典变更后重启生效。\n"
                + " * 依赖目标库存在 sys_dict_data(dict_code, dict_value, dict_label, deleted) 列结构，\n"
                + " * 表不存在或查询异常时 label 原样返回值，且不缓存（下次调用重试，不会失效到重启）。\n"
                + " */\n"
                + "@Component\n"
                + "public class DictTexts {\n\n"
                + "    @Autowired\n"
                + "    private JdbcTemplate jdbcTemplate;\n\n"
                + "    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();\n\n"
                + "    public String label(String dictCode, String value) {\n"
                + "        if (dictCode == null || value == null || value.isBlank()) {\n"
                + "            return value == null ? \"\" : value;\n"
                + "        }\n"
                + "        Map<String, String> mapping = cache.get(dictCode);\n"
                + "        if (mapping == null) {\n"
                + "            mapping = loadDict(dictCode);\n"
                + "            // 查询失败返回 null 不缓存：DB 抖动后下次导出自动重试，不至于失效到重启\n"
                + "            if (mapping != null) {\n"
                + "                cache.put(dictCode, mapping);\n"
                + "            }\n"
                + "        }\n"
                + "        return mapping == null ? value : mapping.getOrDefault(value, value);\n"
                + "    }\n\n"
                + "    private Map<String, String> loadDict(String dictCode) {\n"
                + "        Map<String, String> m = new HashMap<>();\n"
                + "        try {\n"
                + "            // 用 queryForList 而不是 query(String, lambda, args)：后者同时匹配\n"
                + "            // ResultSetExtractor 与 RowCallbackHandler 两个重载，表达式 lambda 会编译歧义\n"
                + "            for (Map<String, Object> row : jdbcTemplate.queryForList(\n"
                + "                    \"SELECT dict_value, dict_label FROM sys_dict_data WHERE dict_code = ? AND deleted = 0\",\n"
                + "                    dictCode)) {\n"
                + "                m.put(String.valueOf(row.get(\"dict_value\")), String.valueOf(row.get(\"dict_label\")));\n"
                + "            }\n"
                + "        } catch (Exception e) {\n"
                + "            return null;\n"
                + "        }\n"
                + "        return m;\n"
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

    private String authContextFilterJava(String pkg, boolean payEnabled, boolean fileEnabled) {
        // V6.3：渠道异步通知由支付渠道服务器回调，无法携带登录 token，必须进白名单
        String payWhiteEntry = payEnabled ? ",\n            \"/pay/notify\"" : "";
        // V6.6：图片直链 /uploads/** 是静态资源（img 标签/浏览器直开不带 token），必须进白名单
        String fileWhiteEntry = fileEnabled ? ",\n            \"/uploads\"" : "";
        // V6.8 素材链路：/img（产物自带静态素材）+ /system/dict（只读字典）常驻白名单；
        // /shop 预留给 V6.9 的 C 端公开接口，先放行空前缀不影响后台
        String baseWhiteEntry = ",\n            \"/img\",\n            \"/system/dict\",\n            \"/shop\"";
        return "package " + pkg + ".common.config;\n\n"
                + "import " + pkg + ".common.auth.PermissionChecker;\n"
                + "import " + pkg + ".common.auth.TokenStore;\n"
                + "import " + pkg + ".common.context.UserContext;\n"
                + "import " + pkg + ".common.result.Result;\n"
                + "import " + pkg + ".common.result.ResultCode;\n"
                + "import com.fasterxml.jackson.databind.ObjectMapper;\n"
                + "import jakarta.servlet.FilterChain;\n"
                + "import jakarta.servlet.ServletException;\n"
                + "import jakarta.servlet.http.HttpServletRequest;\n"
                + "import jakarta.servlet.http.HttpServletResponse;\n"
                + "import org.springframework.beans.factory.annotation.Autowired;\n"
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
                + " * 登录上下文填充 + 两级鉴权拦截。\n"
                + " *\n"
                + " * <p>V6.4 权限闭环：第一级登录校验（token 有效性，401）；\n"
                + " * 第二级接口权限点校验（{@link PermissionChecker}，无权 403）——\n"
                + " * 结束「前端 v-hasPermi 藏按钮、后端裸奔」的历史。\n"
                + " */\n"
                + "@Component\n"
                + "@Order(Ordered.HIGHEST_PRECEDENCE + 10)\n"
                + "public class AuthContextFilter extends OncePerRequestFilter {\n\n"
                + "    private static final String BEARER_PREFIX = \"Bearer \";\n\n"
                + "    @Autowired\n"
                + "    private TokenStore tokenStore;\n\n"
                + "    @Autowired\n"
                + "    private PermissionChecker permissionChecker;\n\n"
                + "    /** 无需 token 即可访问的路径前缀 */\n"
                + "    private static final List<String> WHITE_LIST = List.of(\n"
                + "            \"/auth/login\",\n"
                + "            \"/auth/captcha\",\n"
                + "            \"/health\",\n"
                + "            \"/actuator\",\n"
                + "            \"/v3/api-docs\",\n"
                + "            \"/swagger-ui\",\n"
                + "            \"/swagger-resources\",\n"
                + "            \"/error\""
                + payWhiteEntry
                + fileWhiteEntry
                + baseWhiteEntry + "\n"
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
                + "            // 第一级：登录校验（过期/登出即 401）\n"
                + "            String username = tokenStore.usernameOf(token);\n"
                + "            if (username == null) {\n"
                + "                writeUnauthorized(response, \"登录状态已失效，请重新登录\");\n"
                + "                return;\n"
                + "            }\n"
                + "            // 第二级：接口权限点校验（V6.4 fail-closed：映射缺失读放行写拒绝）\n"
                + "            if (!permissionChecker.check(request, username)) {\n"
                + "                writeForbidden(response, \"无该操作权限\");\n"
                + "                return;\n"
                + "            }\n"
                + "            UserContext.setUsername(username);\n"
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
                + "    }\n\n"
                + "    private void writeForbidden(HttpServletResponse response, String msg) throws IOException {\n"
                + "        response.setStatus(HttpServletResponse.SC_FORBIDDEN);\n"
                + "        response.setCharacterEncoding(StandardCharsets.UTF_8.name());\n"
                + "        response.setContentType(MediaType.APPLICATION_JSON_VALUE);\n"
                + "        Result<Void> body = Result.fail(ResultCode.FORBIDDEN.getCode(), msg);\n"
                + "        response.getWriter().write(objectMapper.writeValueAsString(body));\n"
                + "    }\n"
                + "}\n";
    }

    /**
     * V6.4 P0-1：登录令牌持久化存储（内存缓存 + sys_session 落盘双层）。
     * 修复历史欠账：旧内存实现重启即全员掉线（10-24 真实工单）。
     */
    private String tokenStoreJava(String pkg) {
        return "package " + pkg + ".common.auth;\n\n"
                + "import org.springframework.beans.factory.annotation.Autowired;\n"
                + "import org.springframework.beans.factory.annotation.Value;\n"
                + "import org.springframework.jdbc.core.JdbcTemplate;\n"
                + "import org.springframework.stereotype.Component;\n\n"
                + "import java.sql.Timestamp;\n"
                + "import java.time.Instant;\n"
                + "import java.time.LocalDateTime;\n"
                + "import java.util.Map;\n"
                + "import java.util.UUID;\n"
                + "import java.util.concurrent.ConcurrentHashMap;\n\n"
                + "/**\n"
                + " * 登录令牌存储：/auth/login 签发，AuthContextFilter 校验。\n"
                + " * V6.4：签发写 sys_session 落盘，校验先查内存缓存（TTL 60s）miss 回源查库——\n"
                + " * 内存加速、重启不丢；校验命中即滑动续期。登出删行删缓存。\n"
                + " */\n"
                + "@Component\n"
                + "public class TokenStore {\n\n"
                + "    private static final long CACHE_TTL_SECONDS = 60;\n\n"
                + "    @Autowired\n"
                + "    private JdbcTemplate jdbcTemplate;\n\n"
                + "    /** 会话时长（小时），yml auth.session-hours 可配 */\n"
                + "    @Value(\"${auth.session-hours:24}\")\n"
                + "    private int sessionHours;\n\n"
                + "    private final Map<String, Entry> cache = new ConcurrentHashMap<>();\n"
                + "    private volatile long lastCleanupAt = 0;\n\n"
                + "    public String create(String username, String loginIp) {\n"
                + "        String token = UUID.randomUUID().toString().replace(\"-\", \"\");\n"
                + "        LocalDateTime expire = LocalDateTime.now().plusHours(sessionHours);\n"
                + "        jdbcTemplate.update(\"INSERT INTO sys_session (token, username, login_ip, expire_time) VALUES (?, ?, ?, ?)\",\n"
                + "                token, username, loginIp, Timestamp.valueOf(expire));\n"
                + "        cache.put(token, new Entry(username, Instant.now().plusSeconds(CACHE_TTL_SECONDS)));\n"
                + "        cleanupExpiredIfDue();\n"
                + "        return token;\n"
                + "    }\n\n"
                + "    public String usernameOf(String token) {\n"
                + "        Entry cached = cache.get(token);\n"
                + "        Instant now = Instant.now();\n"
                + "        if (cached != null && now.isBefore(cached.cachedUntil())) {\n"
                + "            return cached.username();\n"
                + "        }\n"
                + "        // 缓存过期/未命中 → 回源查库；库里有则滑动续期并回填缓存\n"
                + "        var rows = jdbcTemplate.queryForList(\n"
                + "                \"SELECT username, expire_time FROM sys_session WHERE token = ? AND deleted = 0\",\n"
                + "                token);\n"
                + "        if (rows.isEmpty()) {\n"
                + "            cache.remove(token);\n"
                + "            return null;\n"
                + "        }\n"
                + "        Object expireRaw = rows.get(0).get(\"expire_time\");\n"
                + "        LocalDateTime expire = expireRaw instanceof java.time.LocalDateTime ldt\n"
                + "                ? ldt : ((Timestamp) expireRaw).toLocalDateTime();\n"
                + "        if (LocalDateTime.now().isAfter(expire)) {\n"
                + "            remove(token);\n"
                + "            return null;\n"
                + "        }\n"
                + "        String username = String.valueOf(rows.get(0).get(\"username\"));\n"
                + "        LocalDateTime newExpire = LocalDateTime.now().plusHours(sessionHours);\n"
                + "        jdbcTemplate.update(\"UPDATE sys_session SET expire_time = ? WHERE token = ?\",\n"
                + "                Timestamp.valueOf(newExpire), token);\n"
                + "        cache.put(token, new Entry(username, now.plusSeconds(CACHE_TTL_SECONDS)));\n"
                + "        return username;\n"
                + "    }\n\n"
                + "    public void remove(String token) {\n"
                + "        cache.remove(token);\n"
                + "        jdbcTemplate.update(\"DELETE FROM sys_session WHERE token = ?\", token);\n"
                + "    }\n\n"
                + "    /** 惰性清理过期会话：距上次清理超 1 小时才扫一次，不依赖调度器 */\n"
                + "    private void cleanupExpiredIfDue() {\n"
                + "        long now = System.currentTimeMillis();\n"
                + "        if (now - lastCleanupAt < 3600_000L) {\n"
                + "            return;\n"
                + "        }\n"
                + "        lastCleanupAt = now;\n"
                + "        try {\n"
                + "            jdbcTemplate.update(\"DELETE FROM sys_session WHERE expire_time < NOW()\");\n"
                + "        } catch (Exception ignored) {\n"
                + "            // 清理失败不影响登录主链路，下次再试\n"
                + "        }\n"
                + "    }\n\n"
                + "    private record Entry(String username, Instant cachedUntil) {\n"
                + "    }\n"
                + "}\n";
    }

    private String authControllerJava(String pkg) {
        // V6.4：BCrypt 分派迁移 + 算术验证码 + 失败锁定 + 登录日志 + perms 下发
        return "package " + pkg + ".common.auth;\n\n"
                + "import " + pkg + ".common.log.IpRegionResolver;\n"
                + "import jakarta.servlet.http.HttpServletRequest;\n"
                + "import org.springframework.beans.factory.annotation.Autowired;\n"
                + "import org.springframework.jdbc.core.JdbcTemplate;\n"
                + "import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;\n"
                + "import org.springframework.util.StringUtils;\n"
                + "import org.springframework.web.bind.annotation.GetMapping;\n"
                + "import org.springframework.web.bind.annotation.PostMapping;\n"
                + "import org.springframework.web.bind.annotation.RequestBody;\n"
                + "import org.springframework.web.bind.annotation.RequestHeader;\n"
                + "import org.springframework.web.bind.annotation.RequestMapping;\n"
                + "import org.springframework.web.bind.annotation.RequestParam;\n"
                + "import org.springframework.web.bind.annotation.RestController;\n\n"
                + "import javax.imageio.ImageIO;\n"
                + "import java.awt.BasicStroke;\n"
                + "import java.awt.Color;\n"
                + "import java.awt.Font;\n"
                + "import java.awt.Graphics2D;\n"
                + "import java.awt.image.BufferedImage;\n"
                + "import java.io.ByteArrayOutputStream;\n"
                + "import java.nio.charset.StandardCharsets;\n"
                + "import java.security.MessageDigest;\n"
                + "import java.security.SecureRandom;\n"
                + "import java.sql.Timestamp;\n"
                + "import java.time.LocalDateTime;\n"
                + "import java.util.ArrayList;\n"
                + "import java.util.Base64;\n"
                + "import java.util.HashMap;\n"
                + "import java.util.List;\n"
                + "import java.util.Map;\n"
                + "import java.util.UUID;\n"
                + "import java.util.concurrent.ConcurrentHashMap;\n\n"
                + "/**\n"
                + " * 登录基座：POST /auth/login、POST /auth/logout、GET /auth/me、GET /auth/captcha。\n"
                + " * V6.4 安全基线：\n"
                + " * <ul>\n"
                + " *   <li>密码 BCrypt（password_algo 分派，旧 SHA-256 存量用户登录成功即无感迁移）</li>\n"
                + " *   <li>算术验证码（60s 一次性，内存存答案）</li>\n"
                + " *   <li>失败锁定：连续 5 次锁 10 分钟（计数落库，重启不清零）</li>\n"
                + " *   <li>登录日志 sys_login_log（含 IP 归属地异步回填）</li>\n"
                + " * </ul>\n"
                + " */\n"
                + "@RestController\n"
                + "@RequestMapping(\"/auth\")\n"
                + "public class AuthController {\n\n"
                + "    private static final int MAX_FAIL = 5;\n"
                + "    private static final int LOCK_MINUTES = 10;\n"
                + "    private static final long CAPTCHA_TTL_MS = 60_000L;\n\n"
                + "    @Autowired\n"
                + "    private JdbcTemplate jdbcTemplate;\n\n"
                + "    @Autowired\n"
                + "    private TokenStore tokenStore;\n\n"
                + "    @Autowired\n"
                + "    private IpRegionResolver ipRegionResolver;\n\n"
                + "    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();\n"
                + "    private final SecureRandom random = new SecureRandom();\n"
                + "    private final Map<String, CaptchaEntry> captchas = new ConcurrentHashMap<>();\n\n"
                + "    /** 算术验证码：题目画进图里，答案只存服务端内存 */\n"
                + "    @GetMapping(\"/captcha\")\n"
                + "    public Map<String, Object> captcha() {\n"
                + "        captchas.entrySet().removeIf(e -> e.getValue().expiredAt() < System.currentTimeMillis());\n"
                + "        int a = random.nextInt(9) + 1;\n"
                + "        int b = random.nextInt(9) + 1;\n"
                + "        boolean plus = random.nextBoolean();\n"
                + "        int x = Math.max(a, b);\n"
                + "        int y = Math.min(a, b);\n"
                + "        int answer = plus ? x + y : x - y;\n"
                + "        String text = x + (plus ? \"+\" : \"-\") + y + \"=?\";\n"
                + "        String captchaId = UUID.randomUUID().toString().replace(\"-\", \"\");\n"
                + "        captchas.put(captchaId, new CaptchaEntry(String.valueOf(answer),\n"
                + "                System.currentTimeMillis() + CAPTCHA_TTL_MS));\n"
                + "        Map<String, Object> data = new HashMap<>();\n"
                + "        data.put(\"captchaId\", captchaId);\n"
                + "        data.put(\"image\", toBase64Png(text));\n"
                + "        return Map.of(\"code\", 200, \"msg\", \"success\", \"data\", data);\n"
                + "    }\n\n"
                + "    @PostMapping(\"/login\")\n"
                + "    public Map<String, Object> login(@RequestBody Map<String, String> body, HttpServletRequest request) {\n"
                + "        String username = body.getOrDefault(\"username\", \"\");\n"
                + "        String password = body.getOrDefault(\"password\", \"\");\n"
                + "        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {\n"
                + "            return Map.of(\"code\", 400, \"msg\", \"用户名或密码不能为空\");\n"
                + "        }\n"
                + "        String ip = clientIp(request);\n"
                + "        // 1) 验证码：一次性 + 60s，防脚本撞库（答案校验放在密码比对前）\n"
                + "        String captchaId = body.getOrDefault(\"captchaId\", \"\");\n"
                + "        String captchaAnswer = body.getOrDefault(\"captchaAnswer\", \"\");\n"
                + "        CaptchaEntry cap = captchas.remove(captchaId);\n"
                + "        if (cap == null || cap.expiredAt() < System.currentTimeMillis()\n"
                + "                || !cap.answer().equals(captchaAnswer.trim())) {\n"
                + "            return loginLogAndRespond(username, ip, false, \"验证码错误或已过期\", null);\n"
                + "        }\n"
                + "        // 2) 失败锁定：计数落库，重启不清零\n"
                + "        List<Map<String, Object>> users = jdbcTemplate.queryForList(\n"
                + "                \"SELECT id, username, password, password_algo, fail_count, lock_until FROM sys_user WHERE username = ? AND deleted = 0\",\n"
                + "                username);\n"
                + "        if (users.isEmpty()) {\n"
                + "            return loginLogAndRespond(username, ip, false, \"用户名或密码错误\", null);\n"
                + "        }\n"
                + "        Map<String, Object> user = users.get(0);\n"
                + "        long userId = ((Number) user.get(\"id\")).longValue();\n"
                + "        int failCount = ((Number) user.getOrDefault(\"fail_count\", 0)).intValue();\n"
                + "        Object lockUntilObj = user.get(\"lock_until\");\n"
                + "        if (lockUntilObj != null) {\n"
                + "            LocalDateTime lockUntil = lockUntilObj instanceof java.time.LocalDateTime ldt2\n"
                + "                    ? ldt2 : ((Timestamp) lockUntilObj).toLocalDateTime();\n"
                + "            if (LocalDateTime.now().isBefore(lockUntil)) {\n"
                + "                long minutes = java.time.Duration.between(LocalDateTime.now(), lockUntil).toMinutes() + 1;\n"
                + "                return loginLogAndRespond(username, ip, false, \"失败次数过多，账号已锁定，请 \" + minutes + \" 分钟后再试\", null);\n"
                + "            }\n"
                + "        }\n"
                + "        // 3) 密码校验：按 password_algo 分派；旧 SHA-256 命中后立即 BCrypt 重哈希写回\n"
                + "        String stored = String.valueOf(user.get(\"password\"));\n"
                + "        String algo = String.valueOf(user.getOrDefault(\"password_algo\", \"sha256\"));\n"
                + "        boolean matched;\n"
                + "        if (\"bcrypt\".equalsIgnoreCase(algo)) {\n"
                + "            matched = bcrypt.matches(password, stored);\n"
                + "        } else {\n"
                + "            matched = sha256(password).equalsIgnoreCase(stored);\n"
                + "            if (matched) {\n"
                + "                // 无感迁移：旧哈希命中即升级 BCrypt，全量用户在自然登录中完成\n"
                + "                jdbcTemplate.update(\"UPDATE sys_user SET password = ?, password_algo = 'bcrypt' WHERE id = ?\",\n"
                + "                        bcrypt.encode(password), userId);\n"
                + "            }\n"
                + "        }\n"
                + "        if (!matched) {\n"
                + "            int nextFail = failCount + 1;\n"
                + "            String msg = \"用户名或密码错误\";\n"
                + "            if (nextFail >= MAX_FAIL) {\n"
                + "                LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(LOCK_MINUTES);\n"
                + "                jdbcTemplate.update(\"UPDATE sys_user SET fail_count = ?, lock_until = ? WHERE id = ?\",\n"
                + "                        nextFail, Timestamp.valueOf(lockUntil), userId);\n"
                + "                msg = \"连续失败 \" + nextFail + \" 次，账号锁定 \" + LOCK_MINUTES + \" 分钟\";\n"
                + "            } else {\n"
                + "                jdbcTemplate.update(\"UPDATE sys_user SET fail_count = ? WHERE id = ?\", nextFail, userId);\n"
                + "            }\n"
                + "            return loginLogAndRespond(username, ip, false, msg, null);\n"
                + "        }\n"
                + "        // 4) 成功：清零失败计数 + 签发 token + 下发 perms\n"
                + "        jdbcTemplate.update(\"UPDATE sys_user SET fail_count = 0, lock_until = NULL WHERE id = ?\", userId);\n"
                + "        String token = tokenStore.create(username, ip);\n"
                + "        List<String> perms = loadPerms(userId);\n"
                + "        Map<String, Object> data = new HashMap<>();\n"
                + "        data.put(\"token\", token);\n"
                + "        data.put(\"username\", username);\n"
                + "        data.put(\"perms\", perms);\n"
                + "        loginLogAndRespond(username, ip, true, null, userId);\n"
                + "        return Map.of(\"code\", 200, \"msg\", \"success\", \"data\", data);\n"
                + "    }\n\n"
                + "    /** 用户权限点集：sys_user_role → sys_role_menu.perms；admin 角色直通下发 *:*:* */\n"
                + "    private List<String> loadPerms(long userId) {\n"
                + "        List<String> perms = new ArrayList<>();\n"
                + "        Integer admin = jdbcTemplate.queryForObject(\n"
                + "                \"SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id \"\n"
                + "                        + \"WHERE ur.user_id = ? AND r.deleted = 0 AND (r.role_key = 'admin' OR r.id = 1)\",\n"
                + "                Integer.class, userId);\n"
                + "        if (admin != null && admin > 0) {\n"
                + "            perms.add(\"*:*:*\");\n"
                + "            return perms;\n"
                + "        }\n"
                + "        List<Map<String, Object>> rows = jdbcTemplate.queryForList(\n"
                + "                \"SELECT DISTINCT m.perms FROM sys_user_role ur \"\n"
                + "                        + \"JOIN sys_role_menu rm ON rm.role_id = ur.role_id \"\n"
                + "                        + \"JOIN sys_menu m ON m.id = rm.menu_id \"\n"
                + "                        + \"WHERE ur.user_id = ? AND m.deleted = 0 AND m.perms IS NOT NULL AND m.perms <> ''\",\n"
                + "                userId);\n"
                + "        for (Map<String, Object> row : rows) {\n"
                + "            perms.add(String.valueOf(row.get(\"perms\")));\n"
                + "        }\n"
                + "        return perms;\n"
                + "    }\n\n"
                + "    /** 登录日志：结果先落库，归属地异步回填（查询走网络，不能阻塞登录链路） */\n"
                + "    private Map<String, Object> loginLogAndRespond(String username, String ip, boolean success,\n"
                + "                                                   String failReason, Long userId) {\n"
                + "        jdbcTemplate.update(\n"
                + "                \"INSERT INTO sys_login_log (username, ip, success, fail_reason) VALUES (?, ?, ?, ?)\",\n"
                + "                username, ip, success ? 1 : 0, failReason);\n"
                + "        if (ip != null && !ip.isBlank()) {\n"
                + "            Long logId = jdbcTemplate.queryForObject(\n"
                + "                    \"SELECT MAX(id) FROM sys_login_log WHERE username = ?\", Long.class, username);\n"
                + "            if (logId != null) {\n"
                + "                ipRegionResolver.resolveAsync(ip, region ->\n"
                + "                        jdbcTemplate.update(\"UPDATE sys_login_log SET region = ? WHERE id = ?\",\n"
                + "                                region.province() + (region.city() == null ? \"\" : \" \" + region.city()), logId));\n"
                + "            }\n"
                + "        }\n"
                + "        if (failReason == null) {\n"
                + "            return null;\n"
                + "        }\n"
                + "        return Map.of(\"code\", 400, \"msg\", failReason);\n"
                + "    }\n\n"
                + "    @PostMapping(\"/logout\")\n"
                + "    public Map<String, Object> logout(@RequestHeader(value = \"Authorization\", required = false) String authorization) {\n"
                + "        if (StringUtils.hasText(authorization) && authorization.startsWith(\"Bearer \")) {\n"
                + "            tokenStore.remove(authorization.substring(7));\n"
                + "        }\n"
                + "        return Map.of(\"code\", 200, \"msg\", \"success\");\n"
                + "    }\n\n"
                + "    @GetMapping(\"/me\")\n"
                + "    public Map<String, Object> me(@RequestHeader(value = \"Authorization\", required = false) String authorization) {\n"
                + "        if (!StringUtils.hasText(authorization) || !authorization.startsWith(\"Bearer \")) {\n"
                + "            return Map.of(\"code\", 401, \"msg\", \"未登录\");\n"
                + "        }\n"
                + "        String username = tokenStore.usernameOf(authorization.substring(7));\n"
                + "        if (username == null) {\n"
                + "            return Map.of(\"code\", 401, \"msg\", \"登录状态已失效\");\n"
                + "        }\n"
                + "        return Map.of(\"code\", 200, \"msg\", \"success\", \"data\", Map.of(\"username\", username));\n"
                + "    }\n\n"
                + "    private String toBase64Png(String text) {\n"
                + "        try {\n"
                + "            int w = 120;\n"
                + "            int h = 40;\n"
                + "            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);\n"
                + "            Graphics2D g = img.createGraphics();\n"
                + "            g.setColor(new Color(240, 244, 248));\n"
                + "            g.fillRect(0, 0, w, h);\n"
                + "            // 干扰线\n"
                + "            for (int i = 0; i < 4; i++) {\n"
                + "                g.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));\n"
                + "                g.setStroke(new BasicStroke(1.2f));\n"
                + "                g.drawLine(random.nextInt(w), random.nextInt(h), random.nextInt(w), random.nextInt(h));\n"
                + "            }\n"
                + "            g.setColor(new Color(40, 60, 90));\n"
                + "            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));\n"
                + "            g.drawString(text, 18, 28);\n"
                + "            g.dispose();\n"
                + "            ByteArrayOutputStream bos = new ByteArrayOutputStream();\n"
                + "            ImageIO.write(img, \"png\", bos);\n"
                + "            return \"data:image/png;base64,\" + Base64.getEncoder().encodeToString(bos.toByteArray());\n"
                + "        } catch (Exception e) {\n"
                + "            throw new IllegalStateException(\"验证码图片生成失败\", e);\n"
                + "        }\n"
                + "    }\n\n"
                + "    private String clientIp(HttpServletRequest request) {\n"
                + "        for (String header : new String[]{\"X-Forwarded-For\", \"X-Real-IP\"}) {\n"
                + "            String value = request.getHeader(header);\n"
                + "            if (value != null && !value.isBlank()) {\n"
                + "                return value.split(\",\")[0].trim();\n"
                + "            }\n"
                + "        }\n"
                + "        return request.getRemoteAddr();\n"
                + "    }\n\n"
                + "    private String sha256(String text) {\n"
                + "        try {\n"
                + "            MessageDigest digest = MessageDigest.getInstance(\"SHA-256\");\n"
                + "            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));\n"
                + "            StringBuilder sb = new StringBuilder();\n"
                + "            for (byte b : hash) {\n"
                + "                sb.append(String.format(\"%02x\", b));\n"
                + "            }\n"
                + "            return sb.toString();\n"
                + "        } catch (Exception e) {\n"
                + "            throw new IllegalStateException(\"SHA-256 不可用\", e);\n"
                + "        }\n"
                + "    }\n\n"
                + "    private record CaptchaEntry(String answer, long expiredAt) {\n"
                + "    }\n"
                + "}\n";
    }

    /** V6.4 P0-3：端点→perms 映射贡献者接口。key="METHOD /path"，value=所需 perms，空串=仅登录即可 */
    private String apiPermContributorJava(String pkg) {
        return "package " + pkg + ".common.auth;\n\n"
                + "import java.util.Map;\n\n"
                + "/**\n"
                + " * 接口权限点映射贡献者：业务模块声明自己的端点-权限映射，PermissionChecker 汇集后集中校验。\n"
                + " * <p>key 形如 {@code POST /fee/page}（路径段可用 {@code {id}} 通配）。\n"
                + " * value 为所需权限串（如 {@code demo_fee:add}），空串表示仅登录即可（基座能力豁免）。\n"
                + " * 未注册的端点走安全缺省：GET 放行、写方法拒绝（fail-closed）。\n"
                + " */\n"
                + "public interface ApiPermContributor {\n\n"
                + "    Map<String, String> endpointPerms();\n"
                + "}\n";
    }

    /** V6.4 P0-3：权限集中校验器。加载用户 perms（60s 缓存）+ admin 直通 + fail-closed 缺省 */
    private String permissionCheckerJava(String pkg) {
        return "package " + pkg + ".common.auth;\n\n"
                + "import jakarta.servlet.http.HttpServletRequest;\n"
                + "import org.springframework.beans.factory.annotation.Autowired;\n"
                + "import org.springframework.jdbc.core.JdbcTemplate;\n"
                + "import org.springframework.stereotype.Component;\n"
                + "import org.springframework.util.StringUtils;\n\n"
                + "import java.util.Arrays;\n"
                + "import java.util.HashSet;\n"
                + "import java.util.List;\n"
                + "import java.util.Map;\n"
                + "import java.util.Set;\n"
                + "import java.util.concurrent.ConcurrentHashMap;\n\n"
                + "/**\n"
                + " * 接口权限点集中校验（V6.4 P0-3）。\n"
                + " *\n"
                + " * <p>映射来源：所有 {@link ApiPermContributor} bean（业务表生成 {@code XxxApiPerms}，\n"
                + " * 基座自带 Flow/Pay 豁免清单）。用户 perms 从 sys_user_role→sys_role_menu.perms 加载，60s 缓存。\n"
                + " * admin 角色（role_key=admin 或 id=1）直通。\n"
                + " * <p>安全缺省 fail-closed：映射缺失时 GET 放行、写方法拒绝——存量系统升级后\n"
                + " * 宁可补一次授权配置，也不能让写接口继续裸奔。\n"
                + " */\n"
                + "@Component\n"
                + "public class PermissionChecker {\n\n"
                + "    private static final long CACHE_TTL_MS = 60_000L;\n\n"
                + "    @Autowired\n"
                + "    private JdbcTemplate jdbcTemplate;\n\n"
                + "    private final Map<String, String> endpointPerms = new ConcurrentHashMap<>();\n"
                + "    private final Map<String, PermsCacheEntry> permsCache = new ConcurrentHashMap<>();\n\n"
                + "    public PermissionChecker(List<ApiPermContributor> contributors) {\n"
                + "        for (ApiPermContributor contributor : contributors) {\n"
                + "            endpointPerms.putAll(contributor.endpointPerms());\n"
                + "        }\n"
                + "    }\n\n"
                + "    public boolean check(HttpServletRequest request, String username) {\n"
                + "        String path = request.getRequestURI();\n"
                + "        String contextPath = request.getContextPath();\n"
                + "        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {\n"
                + "            path = path.substring(contextPath.length());\n"
                + "        }\n"
                + "        String method = request.getMethod().toUpperCase();\n"
                + "        String required = resolveRequired(method, path);\n"
                + "        if (required == null) {\n"
                + "            // 未注册映射：管理员直通（基座 sys 端点未注册权限点，超管必须可用）；\n"
                + "            // 普通用户读放行、写拒绝（fail-closed）\n"
                + "            if (hasPerm(permsOf(username), \"*:*:*\")) {\n"
                + "                return true;\n"
                + "            }\n"
                + "            return \"GET\".equals(method);\n"
                + "        }\n"
                + "        if (required.isEmpty()) {\n"
                + "            // 仅登录即可（基座能力豁免）\n"
                + "            return true;\n"
                + "        }\n"
                + "        return hasPerm(permsOf(username), required);\n"
                + "    }\n\n"
                + "    /** 先精确匹配，再按段通配（/{id} 段匹配任意一段） */\n"
                + "    private String resolveRequired(String method, String path) {\n"
                + "        String exact = endpointPerms.get(method + \" \" + path);\n"
                + "        if (exact != null) {\n"
                + "            return exact;\n"
                + "        }\n"
                + "        String[] actualSegs = path.split(\"/\");\n"
                + "        for (Map.Entry<String, String> entry : endpointPerms.entrySet()) {\n"
                + "            String[] keySegs = entry.getKey().split(\" \");\n"
                + "            if (keySegs.length != 2 || !keySegs[0].equals(method)) {\n"
                + "                continue;\n"
                + "            }\n"
                + "            if (segmentMatch(keySegs[1].split(\"/\"), actualSegs)) {\n"
                + "                return entry.getValue();\n"
                + "            }\n"
                + "        }\n"
                + "        return null;\n"
                + "    }\n\n"
                + "    private boolean segmentMatch(String[] pattern, String[] actual) {\n"
                + "        if (pattern.length != actual.length) {\n"
                + "            return false;\n"
                + "        }\n"
                + "        for (int i = 0; i < pattern.length; i++) {\n"
                + "            String p = pattern[i];\n"
                + "            if (p.startsWith(\"{\") && p.endsWith(\"}\")) {\n"
                + "                if (actual[i].isEmpty()) {\n"
                + "                    return false;\n"
                + "                }\n"
                + "            } else if (!p.equals(actual[i])) {\n"
                + "                return false;\n"
                + "            }\n"
                + "        }\n"
                + "        return true;\n"
                + "    }\n\n"
                + "    /** perms 匹配：精确值、前缀通配（demo_fee:*）、超级通配（*:*:*） */\n"
                + "    private boolean hasPerm(Set<String> perms, String required) {\n"
                + "        if (perms.contains(\"*:*:*\") || perms.contains(required)) {\n"
                + "            return true;\n"
                + "        }\n"
                + "        int i = required.lastIndexOf(':');\n"
                + "        return i > 0 && perms.contains(required.substring(0, i) + \":*\");\n"
                + "    }\n\n"
                + "    private Set<String> permsOf(String username) {\n"
                + "        PermsCacheEntry cached = permsCache.get(username);\n"
                + "        long now = System.currentTimeMillis();\n"
                + "        if (cached != null && cached.expiredAt() > now) {\n"
                + "            return cached.perms();\n"
                + "        }\n"
                + "        Set<String> perms = loadPerms(username);\n"
                + "        permsCache.put(username, new PermsCacheEntry(perms, now + CACHE_TTL_MS));\n"
                + "        return perms;\n"
                + "    }\n\n"
                + "    private Set<String> loadPerms(String username) {\n"
                + "        List<Long> userIds = jdbcTemplate.queryForList(\n"
                + "                \"SELECT id FROM sys_user WHERE username = ? AND deleted = 0\", Long.class, username);\n"
                + "        if (userIds.isEmpty()) {\n"
                + "            return Set.of();\n"
                + "        }\n"
                + "        Integer admin = jdbcTemplate.queryForObject(\n"
                + "                \"SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id \"\n"
                + "                        + \"WHERE ur.user_id = ? AND r.deleted = 0 AND (r.role_key = 'admin' OR r.id = 1)\",\n"
                + "                Integer.class, userIds.get(0));\n"
                + "        if (admin != null && admin > 0) {\n"
                + "            return Set.of(\"*:*:*\");\n"
                + "        }\n"
                + "        List<String> rows = jdbcTemplate.queryForList(\n"
                + "                \"SELECT DISTINCT m.perms FROM sys_user_role ur \"\n"
                + "                        + \"JOIN sys_role_menu rm ON rm.role_id = ur.role_id \"\n"
                + "                        + \"JOIN sys_menu m ON m.id = rm.menu_id \"\n"
                + "                        + \"WHERE ur.user_id = ? AND m.deleted = 0 AND m.perms IS NOT NULL AND m.perms <> ''\",\n"
                + "                String.class, userIds.get(0));\n"
                + "        return new HashSet<>(rows);\n"
                + "    }\n\n"
                + "    private record PermsCacheEntry(Set<String> perms, long expiredAt) {\n"
                + "    }\n"
                + "}\n";
    }

    /** V6.4：流程基座端点豁免清单——审批是平台能力不接业务 perms，登录即可 */
    private String flowApiPermsJava(String pkg) {
        return "package " + pkg + ".common.auth;\n\n"
                + "import org.springframework.stereotype.Component;\n\n"
                + "import java.util.HashMap;\n"
                + "import java.util.Map;\n\n"
                + "/**\n"
                + " * 流程基座端点映射：审批流是平台内置能力，不接业务 perms，登录即可操作。\n"
                + " * 值为空串 = 仅登录。若业务要求审批权限管控，可改为具体 perms 值。\n"
                + " */\n"
                + "@Component\n"
                + "public class FlowApiPerms implements ApiPermContributor {\n\n"
                + "    @Override\n"
                + "    public Map<String, String> endpointPerms() {\n"
                + "        Map<String, String> m = new HashMap<>();\n"
                + "        m.put(\"POST /flow/submit\", \"\");\n"
                + "        m.put(\"POST /flow/approve\", \"\");\n"
                + "        m.put(\"POST /flow/reject\", \"\");\n"
                + "        m.put(\"GET /flow/records\", \"\");\n"
                + "        m.put(\"GET /flow/state\", \"\");\n"
                + "        m.put(\"GET /flow/todo\", \"\");\n"
                + "        m.put(\"GET /flow/done\", \"\");\n"
                + "        m.put(\"GET /flow/task/{taskId}\", \"\");\n"
                + "        return m;\n"
                + "    }\n"
                + "}\n";
    }

    /** V6.4：支付基座端点豁免清单——收银台链路登录即可（notify 在白名单） */
    private String payApiPermsJava(String pkg) {
        return "package " + pkg + ".common.auth;\n\n"
                + "import org.springframework.stereotype.Component;\n\n"
                + "import java.util.HashMap;\n"
                + "import java.util.Map;\n\n"
                + "/**\n"
                + " * 支付基座端点映射：支付链路是平台内置能力，登录即可（/pay/notify 在 Filter 白名单）。\n"
                + " */\n"
                + "@Component\n"
                + "public class PayApiPerms implements ApiPermContributor {\n\n"
                + "    @Override\n"
                + "    public Map<String, String> endpointPerms() {\n"
                + "        Map<String, String> m = new HashMap<>();\n"
                + "        m.put(\"POST /pay/order\", \"\");\n"
                + "        m.put(\"GET /pay/channel\", \"\");\n"
                + "        m.put(\"GET /pay/order/{outTradeNo}\", \"\");\n"
                + "        m.put(\"POST /pay/mock/pay/{outTradeNo}\", \"\");\n"
                + "        m.put(\"POST /pay/close/{outTradeNo}\", \"\");\n"
                + "        return m;\n"
                + "    }\n"
                + "}\n";
    }

    /** V6.4 P0-4：登录日志只读查询（管理页 P1 接入） */
    /**
     * V6.8 字典只读接口：GET /system/dict/data/type/{code} → Result.ok([{value,label}])。
     *
     * <p>用 JdbcTemplate 而不是实体+Mapper：字典表由 {@code db/dict.sql} 保证存在，
     * 多一套实体只会多一处漂移面。表不存在/查询异常时返回空数组——下拉降级为空，
     * 页面照常可用（前端本来就有「取不到就当空选项」的兜底）。
     *
     * <p><b>必须套 Result.ok 包装</b>：目标工程 request 拦截器按 {@code res.code === 200}
     * 判成功，vuePage.ftl 的 loadDicts 取 {@code res.data}。若直接返回裸 List，
     * 每个带字典的列表页都会弹「请求失败」且字典翻译全空（V6.8 同源缺陷）。
     */
    private String dictControllerJava(String pkg) {
        return "package " + pkg + ".system;\n\n"
                + "import " + pkg + ".common.result.Result;\n"
                + "import org.springframework.beans.factory.annotation.Autowired;\n"
                + "import org.springframework.jdbc.core.JdbcTemplate;\n"
                + "import org.springframework.web.bind.annotation.GetMapping;\n"
                + "import org.springframework.web.bind.annotation.PathVariable;\n"
                + "import org.springframework.web.bind.annotation.RequestMapping;\n"
                + "import org.springframework.web.bind.annotation.RestController;\n\n"
                + "import java.util.ArrayList;\n"
                + "import java.util.LinkedHashMap;\n"
                + "import java.util.List;\n"
                + "import java.util.Map;\n\n"
                + "/**\n"
                + " * 字典数据只读接口（V6.8 字典脚手架）：前端按 dict_code 取 value/label 列表。\n"
                + " * 表结构与种子由 db/dict.sql 提供；端点在白名单里（/system/dict），免登录可读。\n"
                + " * 返回统一包装 Result，与 request 拦截器按 code 判断的契约一致。\n"
                + " */\n"
                + "@RestController\n"
                + "@RequestMapping(\"/system/dict\")\n"
                + "public class SysDictController {\n\n"
                + "    @Autowired\n"
                + "    private JdbcTemplate jdbcTemplate;\n\n"
                + "    @GetMapping(\"/data/type/{code}\")\n"
                + "    public Result<List<Map<String, String>>> byType(@PathVariable(\"code\") String code) {\n"
                + "        List<Map<String, String>> out = new ArrayList<>();\n"
                + "        try {\n"
                + "            for (Map<String, Object> row : jdbcTemplate.queryForList(\n"
                + "                    \"SELECT dict_value, dict_label FROM sys_dict_data \"\n"
                + "                            + \"WHERE dict_code = ? AND deleted = 0 ORDER BY sort, id\", code)) {\n"
                + "                Map<String, String> item = new LinkedHashMap<>();\n"
                + "                item.put(\"value\", String.valueOf(row.get(\"dict_value\")));\n"
                + "                item.put(\"label\", String.valueOf(row.get(\"dict_label\")));\n"
                + "                out.add(item);\n"
                + "            }\n"
                + "        } catch (Exception e) {\n"
                + "            // 表没建/库抖动：返回空列表，前端下拉为空但页面不炸\n"
                + "            return Result.ok(List.of());\n"
                + "        }\n"
                + "        return Result.ok(out);\n"
                + "    }\n"
                + "}\n";
    }

    private String loginLogControllerJava(String pkg) {
        return "package " + pkg + ".system;\n\n"
                + "import org.springframework.beans.factory.annotation.Autowired;\n"
                + "import org.springframework.jdbc.core.JdbcTemplate;\n"
                + "import org.springframework.web.bind.annotation.PostMapping;\n"
                + "import org.springframework.web.bind.annotation.RequestBody;\n"
                + "import org.springframework.web.bind.annotation.RequestMapping;\n"
                + "import org.springframework.web.bind.annotation.RestController;\n\n"
                + "import java.util.List;\n"
                + "import java.util.Map;\n\n"
                + "/**\n"
                + " * 登录日志只读查询（V6.4 P0-4）：谁在什么时候从哪里登录、成败与原因。\n"
                + " * 写入端在 AuthController；本控制器只读，端点纳入权限校验 fail-closed 缺省（GET/POST/page 读放行）。\n"
                + " */\n"
                + "@RestController\n"
                + "@RequestMapping(\"/loginLog\")\n"
                + "public class LoginLogController {\n\n"
                + "    @Autowired\n"
                + "    private JdbcTemplate jdbcTemplate;\n\n"
                + "    @PostMapping(\"/page\")\n"
                + "    public Map<String, Object> page(@RequestBody(required = false) Map<String, Object> body) {\n"
                + "        int pageNum = body == null || body.get(\"pageNum\") == null ? 1 : ((Number) body.get(\"pageNum\")).intValue();\n"
                + "        int pageSize = body == null || body.get(\"pageSize\") == null ? 10 : ((Number) body.get(\"pageSize\")).intValue();\n"
                + "        pageSize = Math.min(Math.max(pageSize, 1), 100);\n"
                + "        StringBuilder where = new StringBuilder(\" WHERE 1=1\");\n"
                + "        var args = new java.util.ArrayList<Object>();\n"
                + "        if (body != null && body.get(\"username\") != null && !String.valueOf(body.get(\"username\")).isBlank()) {\n"
                + "            where.append(\" AND username LIKE ?\");\n"
                + "            args.add(\"%\" + body.get(\"username\") + \"%\");\n"
                + "        }\n"
                + "        if (body != null && body.get(\"success\") != null) {\n"
                + "            where.append(\" AND success = ?\");\n"
                + "            args.add(((Number) body.get(\"success\")).intValue());\n"
                + "        }\n"
                + "        Integer total = jdbcTemplate.queryForObject(\n"
                + "                \"SELECT COUNT(*) FROM sys_login_log\" + where, Integer.class, args.toArray());\n"
                + "        List<Map<String, Object>> records = jdbcTemplate.queryForList(\n"
                + "                \"SELECT id, username, ip, region, success, fail_reason AS failReason, create_time AS createTime \"\n"
                + "                        + \"FROM sys_login_log\" + where + \" ORDER BY id DESC LIMIT ? OFFSET ?\",\n"
                + "                args.toArray(), pageSize, (pageNum - 1) * pageSize);\n"
                + "        Map<String, Object> data = new java.util.HashMap<>();\n"
                + "        data.put(\"records\", records);\n"
                + "        data.put(\"total\", total == null ? 0 : total);\n"
                + "        data.put(\"current\", pageNum);\n"
                + "        data.put(\"size\", pageSize);\n"
                + "        return Map.of(\"code\", 200, \"msg\", \"success\", \"data\", data);\n"
                + "    }\n"
                + "}\n";
    }

    private String seedSql() {
        // V6.4：admin 种子密码升级 BCrypt（预生成值，admin123）；旧 SHA-256 存量用户登录时无感迁移
        String adminBcrypt = "$2a$10$BQz.XdhOV7sJOpH9z/BeheARd01UQKdchBsEgPfa.rYgmwTh75PaO";
        return "-- 骨架自带登录基座与权限体系：admin / admin123\n"
                + "-- 由建系统流水线在建库后自动执行；手工部署时也可以手动跑本文件\n"
                + "-- V6.4：密码 BCrypt（password_algo 标记算法，旧 SHA-256 存量用户登录时无感迁移）\n"
                + "CREATE TABLE IF NOT EXISTS sys_user (\n"
                + "  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n"
                + "  username varchar(64) NOT NULL COMMENT '登录名',\n"
                + "  password varchar(72) NOT NULL COMMENT '密码（BCrypt 60位，兼容旧 SHA-256 64位）',\n"
                + "  password_algo varchar(16) NOT NULL DEFAULT 'bcrypt' COMMENT '密码算法：bcrypt/sha256',\n"
                + "  fail_count int NOT NULL DEFAULT 0 COMMENT '连续失败次数（V6.4 登录锁定）',\n"
                + "  lock_until datetime NULL COMMENT '锁定截止时间',\n"
                + "  nickname varchar(64) NULL COMMENT '昵称',\n"
                + "  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n"
                + "  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n"
                + "  create_by bigint NULL COMMENT '创建人',\n"
                + "  update_by bigint NULL COMMENT '更新人',\n"
                + "  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',\n"
                + "  PRIMARY KEY (id),\n"
                + "  UNIQUE KEY uk_username (username)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';\n\n"
                + "CREATE TABLE IF NOT EXISTS sys_session (\n"
                + "  token varchar(64) NOT NULL COMMENT '登录令牌',\n"
                + "  username varchar(64) NOT NULL COMMENT '登录名',\n"
                + "  login_ip varchar(64) NULL COMMENT '登录IP',\n"
                + "  login_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',\n"
                + "  expire_time datetime NOT NULL COMMENT '过期时间（滑动续期）',\n"
                + "  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',\n"
                + "  PRIMARY KEY (token),\n"
                + "  KEY idx_session_expire (expire_time)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录会话（V6.4 持久化，重启不掉线）';\n\n"
                + "CREATE TABLE IF NOT EXISTS sys_login_log (\n"
                + "  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n"
                + "  username varchar(64) NULL COMMENT '尝试登录的用户名',\n"
                + "  ip varchar(64) NULL COMMENT '来源IP',\n"
                + "  region varchar(128) NULL COMMENT 'IP归属地',\n"
                + "  success tinyint NOT NULL DEFAULT 0 COMMENT '是否成功：1是0否',\n"
                + "  fail_reason varchar(255) NULL COMMENT '失败原因',\n"
                + "  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '时间',\n"
                + "  PRIMARY KEY (id),\n"
                + "  KEY idx_login_log_time (create_time)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志（V6.4）';\n\n"
                // 坑48：操作日志表此前不在任何种子 SQL 里，新建库一调业务接口就 500
                + "CREATE TABLE IF NOT EXISTS sys_oper_log (\n"
                + "  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n"
                + "  title varchar(64) NULL COMMENT '模块标题',\n"
                + "  oper_type varchar(32) NULL COMMENT '操作类型',\n"
                + "  method varchar(128) NULL COMMENT '方法名',\n"
                + "  request_uri varchar(128) NULL COMMENT '请求URI',\n"
                + "  request_method varchar(16) NULL COMMENT '请求方式',\n"
                + "  oper_param text NULL COMMENT '请求参数',\n"
                + "  json_result text NULL COMMENT '返回结果',\n"
                + "  status tinyint NULL COMMENT '1成功 0失败',\n"
                + "  error_msg varchar(512) NULL COMMENT '错误消息',\n"
                + "  oper_user_id bigint NULL COMMENT '操作人ID',\n"
                + "  oper_name varchar(64) NULL COMMENT '操作人',\n"
                + "  oper_ip varchar(64) NULL COMMENT '来源IP',\n"
                + "  province varchar(32) NULL COMMENT '省份',\n"
                + "  city varchar(32) NULL COMMENT '城市',\n"
                + "  cost_ms bigint NULL COMMENT '耗时毫秒',\n"
                + "  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',\n"
                + "  create_by bigint NULL, create_time datetime NULL, update_by bigint NULL, update_time datetime NULL,\n"
                + "  PRIMARY KEY (id),\n"
                + "  KEY idx_oper_log_time (create_time)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志（V6.4 补齐种子）';\n\n"
                // V6.4 权限体系表基座：字段口径与 V3.0 菜单注册 SQL 一致（目标工程此前缺失）
                + "CREATE TABLE IF NOT EXISTS sys_role (\n"
                + "  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n"
                + "  role_name varchar(64) NOT NULL COMMENT '角色名',\n"
                + "  role_key varchar(64) NOT NULL COMMENT '角色标识（admin 直通）',\n"
                + "  remark varchar(255) NULL COMMENT '备注',\n"
                + "  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n"
                + "  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n"
                + "  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',\n"
                + "  PRIMARY KEY (id)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色';\n\n"
                + "CREATE TABLE IF NOT EXISTS sys_menu (\n"
                + "  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n"
                + "  parent_id bigint NOT NULL DEFAULT 0 COMMENT '父菜单ID',\n"
                + "  name varchar(64) NOT NULL COMMENT '名称',\n"
                + "  path varchar(128) NULL COMMENT '路由路径',\n"
                + "  component varchar(128) NULL COMMENT '组件路径',\n"
                + "  perms varchar(128) NULL COMMENT '权限标识（如 demo_fee:list）',\n"
                + "  type tinyint NOT NULL DEFAULT 1 COMMENT '类型：1目录2菜单3按钮',\n"
                + "  sort int NOT NULL DEFAULT 0 COMMENT '排序',\n"
                + "  status tinyint NOT NULL DEFAULT 1 COMMENT '状态：1启用0停用',\n"
                + "  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',\n"
                + "  PRIMARY KEY (id)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单与权限点';\n\n"
                + "CREATE TABLE IF NOT EXISTS sys_user_role (\n"
                + "  user_id bigint NOT NULL COMMENT '用户ID',\n"
                + "  role_id bigint NOT NULL COMMENT '角色ID',\n"
                + "  PRIMARY KEY (user_id, role_id)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';\n\n"
                + "CREATE TABLE IF NOT EXISTS sys_role_menu (\n"
                + "  role_id bigint NOT NULL COMMENT '角色ID',\n"
                + "  menu_id bigint NOT NULL COMMENT '菜单ID',\n"
                + "  PRIMARY KEY (role_id, menu_id)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单（perms 随菜单授予）';\n\n"
                + "INSERT IGNORE INTO sys_user (username, password, password_algo, nickname)\n"
                + "VALUES ('admin', '" + adminBcrypt + "', 'bcrypt', '管理员');\n\n"
                + "INSERT IGNORE INTO sys_role (id, role_name, role_key, remark)\n"
                + "VALUES (1, '超级管理员', 'admin', '内置角色：接口权限点直通');\n"
                + "INSERT IGNORE INTO sys_user_role (user_id, role_id)\n"
                + "SELECT u.id, 1 FROM sys_user u WHERE u.username = 'admin';\n";
    }

    private String frontendLoginVue(String appName, String appLogo, String themeColor) {
        // V6.4：登录页带算术验证码（GET /auth/captcha，60s 一次性，点图刷新）
        // V6.8：品牌 Logo 置于标题上方（空则整行不出现，不留空洞）
        String logoTag = appLogo == null || appLogo.isBlank() ? ""
                : "      <img class=\"login-logo\" src=\"" + appLogo + "\" alt=\"" + appName + "\" />\n";
        return "<template>\n"
                + "  <div class=\"login-wrap\">\n"
                + "    <el-card class=\"login-card\">\n"
                + logoTag
                + "      <h2>" + appName + "</h2>\n"
                + "      <el-form @submit.native.prevent=\"doLogin\">\n"
                + "        <el-form-item>\n"
                + "          <el-input v-model=\"form.username\" placeholder=\"用户名\" />\n"
                + "        </el-form-item>\n"
                + "        <el-form-item>\n"
                + "          <el-input v-model=\"form.password\" type=\"password\" placeholder=\"密码\" show-password />\n"
                + "        </el-form-item>\n"
                + "        <el-form-item>\n"
                + "          <div class=\"captcha-row\">\n"
                + "            <el-input v-model=\"form.captchaAnswer\" placeholder=\"计算结果\" @keyup.enter.native=\"doLogin\" />\n"
                + "            <img v-if=\"captchaImage\" :src=\"captchaImage\" class=\"captcha-img\" title=\"点击刷新\" @click=\"loadCaptcha\" />\n"
                + "          </div>\n"
                + "        </el-form-item>\n"
                + "        <el-button type=\"primary\" :loading=\"loading\" native-type=\"submit\" style=\"width:100%; background:" + themeColor + "; border-color:" + themeColor + "\">登 录</el-button>\n"
                + "      </el-form>\n"
                + "      <p class=\"tip\">默认账号 admin / admin123</p>\n"
                + "    </el-card>\n"
                + "  </div>\n"
                + "</template>\n\n"
                + "<script>\n"
                + "import request from '@/utils/request'\n\n"
                + "export default {\n"
                + "  name: 'Login',\n"
                + "  data() {\n"
                + "    return {\n"
                + "      form: { username: 'admin', password: '', captchaId: '', captchaAnswer: '' },\n"
                + "      captchaImage: '',\n"
                + "      loading: false\n"
                + "    }\n"
                + "  },\n"
                + "  created() {\n"
                + "    this.loadCaptcha()\n"
                + "  },\n"
                + "  methods: {\n"
                + "    async loadCaptcha() {\n"
                + "      const res = await request.get('/auth/captcha')\n"
                + "      this.captchaImage = res.data.image\n"
                + "      this.form.captchaId = res.data.captchaId\n"
                + "      this.form.captchaAnswer = ''\n"
                + "    },\n"
                + "    async doLogin() {\n"
                + "      if (!this.form.username || !this.form.password) return\n"
                + "      if (!this.form.captchaAnswer) {\n"
                + "        this.$message.warning('请输入验证码')\n"
                + "        return\n"
                + "      }\n"
                + "      this.loading = true\n"
                + "      try {\n"
                + "        const res = await request.post('/auth/login', this.form)\n"
                + "        localStorage.setItem('token', res.data.token)\n"
                + "        localStorage.setItem('username', res.data.username)\n"
                + "        // V6.4：perms 落地供 v-hasPermi 指令与后端权限校验对齐\n"
                + "        localStorage.setItem('perms', JSON.stringify(res.data.perms || []))\n"
                + "        this.$router.push('/')\n"
                + "      } catch (e) {\n"
                + "        // 验证码一次性：失败（含密码错）后必须换新码，否则重放必错\n"
                + "        this.loadCaptcha()\n"
                + "      } finally {\n"
                + "        this.loading = false\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}\n"
                + "</script>\n\n"
                + "<style scoped>\n"
                + ".login-wrap { display: flex; align-items: center; justify-content: center; height: 100vh; background: #f0f2f5; }\n"
                + ".login-card { width: 360px; text-align: center; }\n"
                + ".login-logo { width: 56px; height: 56px; object-fit: contain; margin-bottom: 6px; }\n"
                + ".captcha-row { display: flex; gap: 8px; width: 100%; }\n"
                + ".captcha-img { height: 40px; cursor: pointer; border-radius: 4px; }\n"
                + ".tip { color: #999; font-size: 12px; margin-bottom: 0; }\n"
                + "</style>\n";
    }

    private String applicationYml(GenScaffoldDTO dto, String pkg, String project, int serverPort,
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

    private String frontendPackageJson(String project, String module, boolean payEnabled) {
        // V6.3：收银台二维码由前端 qrcode 生成（后端零图像依赖），随能力包开关注入依赖
        String qrcodeDep = payEnabled ? ",\n    \"qrcode\": \"^1.5.4\"" : "";
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
                + "    \"vuex\": \"^3.6.2\""
                + qrcodeDep + "\n"
                + "  },\n"
                + "  \"devDependencies\": {\n"
                + "    \"@vue/cli-plugin-babel\": \"~5.0.8\",\n"
                + "    \"@vue/cli-service\": \"~5.0.8\",\n"
                + "    \"vue-template-compiler\": \"^2.7.16\"\n"
                + "  }\n"
                + "}\n";
    }

    private String frontendVueConfig(int webPort, int serverPort, String gatewayPrefix, boolean fileEnabled) {
        // api.js 的路径带 /api/{gatewayPrefix}（给网关预留），本地直连后端没有网关，
        // 代理必须把这段前缀剥掉，否则后端收到 /api/demo/xxx 全部 404
        // V6.6 文件能力包：图片直链 /uploads/** 在后端，dev 必须单独代理（不剥任何前缀）
        // V6.8 素材链路：/img/** 是产物自带的静态素材（resources/static/img），dev 必须代理；
        // 漏了这一条整页图片都会落到 dev server 自己身上变成 404 → 全站占位图
        String imgProxy = ",\n      // 静态素材（后端 resources/static/img），漏配会整页显示占位图\n"
                + "      '/img': {\n"
                + "        target: 'http://127.0.0.1:" + serverPort + "',\n"
                + "        changeOrigin: true\n"
                + "      }";
        String uploadsProxy = fileEnabled
                ? ",\n      '/uploads': {\n"
                + "        target: 'http://127.0.0.1:" + serverPort + "',\n"
                + "        changeOrigin: true\n"
                + "      }" : "";
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
                + "        // http-proxy-middleware 多条 pathRewrite 不保证顺序执行，合并成一条正则\n"
                + "        pathRewrite: { '^/dev-api(/api/" + gatewayPrefix + ")?': '' }\n"
                + "      }" + imgProxy + uploadsProxy + "\n"
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

    private String frontendIndexHtml(String appName, String appLogo, String themeColor,
                                     String htmlLang, String htmlDir) {
        return frontendIndexHtml(appName, appLogo, themeColor, htmlLang, htmlDir, false);
    }

    /** V8.0：i18nOn 时补一段 RTL 镜像样式；关开关时输出与 V7.5 逐字节一致 */
    private String frontendIndexHtml(String appName, String appLogo, String themeColor,
                                     String htmlLang, String htmlDir, boolean i18nOn) {
        // favicon 必须指向真的存在的文件：空 Logo 回落 placeholder.svg（脚手架必写），
        // 历史上写死的 logo.png 从来没有被生成过，等于每个新工程都挂一个 404
        String favicon = appLogo == null || appLogo.isBlank()
                ? com.codegen.util.GenTemplateModel.FALLBACK_APP_LOGO : appLogo;
        String rtlCss = i18nOn
                ? "  <style>[dir='rtl'] .header-right { flex-direction: row-reverse; }"
                + "[dir='rtl'] .content, [dir='rtl'] .el-table, [dir='rtl'] .el-form-item__label { direction: rtl; text-align: right; }"
                + "[dir='rtl'] code, [dir='rtl'] pre, [dir='rtl'] .path { direction: ltr; text-align: left; }"
                + "[dir='rtl'] .lang-switch { margin-inline-start: 0; margin-inline-end: 12px; }</style>\n"
                : "";
        return "<!DOCTYPE html>\n"
                + "<html lang=\"" + htmlLang + "\"" + ("rtl".equals(htmlDir) ? " dir=\"rtl\"" : "") + ">\n"
                + "<head>\n"
                + "  <meta charset=\"utf-8\">\n"
                + "  <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">\n"
                + "  <title>" + appName + "</title>\n"
                + "  <link rel=\"icon\" href=\"" + favicon + "\">\n"
                + "  <style>:root { --app-primary: " + themeColor + "; }</style>\n"
                + rtlCss
                + "</head>\n"
                + "<body>\n"
                + "  <noscript>请启用 JavaScript 后继续访问。</noscript>\n"
                + "  <div id=\"app\"></div>\n"
                + "</body>\n"
                + "</html>\n";
    }

    private String frontendMainJs() {
        return frontendMainJs(false);
    }

    /** V8.0：开关打开时全局注册 $t / $setLocale 与切换器组件；关闭时内容零 diff */
    private String frontendMainJs(boolean i18nOn) {
        // V6.4：注册 v-hasPermi 指令——perms 与后端权限点校验同源（登录响应下发），前后端一致
        String i18nImports = i18nOn
                ? "import i18n, { t, setLocale, availableLocales } from './utils/i18n'\n"
                + "import LanguageSwitch from './components/LanguageSwitch.vue'\n"
                : "";
        String i18nInit = i18nOn
                ? "// V8.0 产物国际化：Vue.observable 的 state 让所有 $t 调用点响应式重渲染\n"
                + "Vue.component('LanguageSwitch', LanguageSwitch)\n"
                + "Vue.prototype.$t = t\n"
                + "Vue.prototype.$setLocale = setLocale\n"
                + "Vue.prototype.$i18nLocales = availableLocales()\n"
                + "Vue.prototype.$i18n = i18n\n"
                + "window.$t = t\n\n"
                : "";
        return "import Vue from 'vue'\n"
                + "import ElementUI from 'element-ui'\n"
                + "import 'element-ui/lib/theme-chalk/index.css'\n"
                + "import App from './App.vue'\n"
                + "import router from './router'\n"
                + "import store from './store'\n"
                + i18nImports + "\n"
                + "Vue.use(ElementUI, { size: 'small' })\n"
                + "Vue.config.productionTip = false\n\n"
                + i18nInit
                + "// V6.4 按钮级权限：v-hasPermi=\"['demo_fee:add']\"，命中任一即显示；超管 *:*:* 直通\n"
                + "Vue.directive('hasPermi', {\n"
                + "  inserted(el, binding) {\n"
                + "    let perms = []\n"
                + "    try { perms = JSON.parse(localStorage.getItem('perms') || '[]') } catch (e) { perms = [] }\n"
                + "    const need = binding.value || []\n"
                + "    const ok = perms.includes('*:*:*') || need.some(p => perms.includes(p)\n"
                + "      || perms.includes(p.replace(/:[^:]*$/, ':*')))\n"
                + "    if (!ok) {\n"
                + "      el.parentNode && el.parentNode.removeChild(el)\n"
                + "    }\n"
                + "  }\n"
                + "})\n\n"
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

    private String frontendLayoutVue(String appName, String appLogo, String themeColor) {
        return frontendLayoutVue(appName, appLogo, themeColor, false);
    }

    /** V8.0：开关打开时把 header 占位符换成全局注册的切换器；关闭时占位符被清空，字节零 diff */
    private String frontendLayoutVue(String appName, String appLogo, String themeColor, boolean i18nOn) {
        String name = appName == null || appName.isBlank() ? "业务系统" : appName;
        String initial = name.length() > 1 ? name.substring(0, 1) : name;
        // 品牌区二选一：配了 Logo 用图，没配用首字标识（绝不留空占位）
        String brandMark = appLogo == null || appLogo.isBlank()
                ? "<div class=\"brand-mark\">" + initial + "</div>"
                : "<img class=\"brand-logo\" src=\"" + appLogo + "\" alt=\"" + name + "\">";
        String switchTag = i18nOn ? "\n          <language-switch />" : "";
        return loadScaffoldResource("scaffold/web/layout.vue")
                .replace("__BRAND_MARK__", brandMark)
                .replace("__PROJECT__", name)
                .replace("__THEME_COLOR__", themeColor)
                .replace("__I18N_SWITCH__", switchTag);
    }

    /**
     * V8.0 产物国际化资源：语言包 + 聚合入口 + 零依赖运行时 + 切换器。
     *
     * <p>文件名来自 {@link I18nCatalog#resolveLocales(String, String)} 的白名单标签
     * （{@code ^[A-Za-z0-9-]{2,32}$} 先验），拼路径前已过滤，不存在穿越风险。
     * 所有文件仍走 {@link #write}，备份 / 台账 / 受保护跳过三件套一个不少。
     */
    private void writeI18nResources(Path frontendRoot, GenScaffoldDTO dto,
                                    boolean force, GenScaffoldVO vo, int[] counter) {
        String defaultLocale = LanguageConfig.resolveLocale(dto.getDefaultLanguage());
        List<String> locales = I18nCatalog.resolveLocales(dto.getSupportedLanguages(), defaultLocale);
        if (locales.isEmpty()) {
            locales = List.of(defaultLocale);
        }
        Long projectId = dto.getProjectId();
        StringBuilder imports = new StringBuilder();
        StringBuilder messages = new StringBuilder();
        StringBuilder available = new StringBuilder();
        int idx = 0;
        for (String loc : locales) {
            Map<String, String> overrides = projectId == null ? Map.of()
                    : i18nMessageService.overrides(projectId, loc);
            write(frontendRoot, "src/locales/" + loc + ".json",
                    I18nCatalog.languagePackage(loc, overrides, defaultLocale),
                    "frontend", force, vo, counter);
            imports.append("import v").append(idx).append(" from './").append(loc).append(".json'\n");
            messages.append("  '").append(loc).append("': v").append(idx).append(",\n");
            I18nCatalog.LocaleMeta meta = I18nCatalog.localeMeta(loc);
            available.append("  { locale: '").append(loc)
                    .append("', label: '").append(jsQuote(meta.label()))
                    .append("', nameNative: '").append(jsQuote(meta.nameNative()))
                    .append("', dir: '").append(meta.dir()).append("' },\n");
            idx++;
        }
        String indexJs = "// V8.0 产物语言包入口（代码生成器自动维护，请勿手工编辑）\n"
                + imports + "\n"
                + "export const available = [\n" + available + "]\n\n"
                + "const messages = {\n" + messages + "}\n\n"
                + "export function messagesOf(locale) { return messages[locale] || {} }\n"
                + "export default messages\n";
        write(frontendRoot, "src/locales/index.js", indexJs, "frontend", force, vo, counter);
        write(frontendRoot, "src/utils/i18n.js",
                loadScaffoldResource("scaffold-i18n/i18n.js").replace("__DEFAULT_LOCALE__", defaultLocale),
                "frontend", force, vo, counter);
        write(frontendRoot, "src/components/LanguageSwitch.vue",
                loadScaffoldResource("scaffold-i18n/LanguageSwitch.vue"),
                "frontend", force, vo, counter);
        log.info("[I18nScaffold] 生成 {} 个语言包，默认语言 {}，词库 {} 键",
                locales.size(), defaultLocale, I18nCatalog.totalKeys());
    }

    /** JS 单引号字符串转义：语言自称里可能出现撇号（如 Norsk bokmål / Cymraeg） */
    private static String jsQuote(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("'", "\\'");
    }

    /**
     * 读脚手架资源（classpath 下的静态模板），缺失时抛业务异常而不是静默写空文件。
     */
    private String loadScaffoldResource(String path) {
        org.springframework.core.io.ClassPathResource resource =
                new org.springframework.core.io.ClassPathResource(path);
        try (java.io.InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("脚手架资源缺失：" + path, e);
        }
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
                + "import Layout from '@/layout/index.vue'\n"
                // gen-routes.js 由 /gen/menu/route-sync 重建（系统 → 业务域 → 页面三级）；
                // 侧边栏菜单树从 $router.options.routes 推导，不把它合进来菜单永远是空白
                + "import genRoutes from './gen-routes'\n\n"
                + "Vue.use(VueRouter)\n\n"
                + "const routes = [\n"
                + "  {\n"
                + "    path: '/login',\n"
                + "    name: 'Login',\n"
                + "    component: () => import('@/views/login.vue'),\n"
                + "    meta: { title: '登录' }\n"
                + "  },\n"
                + "  {\n"
                + "    path: '/',\n"
                + "    component: Layout,\n"
                + "    redirect: '/home',\n"
                + "    children: [\n"
                + "      {\n"
                + "        path: 'home',\n"
                + "        name: 'Home',\n"
                + "        component: () => import('@/views/index.vue'),\n"
                + "        meta: { title: '首页' }\n"
                + "      }\n"
                + "    ]\n"
                + "  },\n"
                + "  ...genRoutes\n"
                + "]\n\n"
                + "const router = new VueRouter({\n"
                + "  routes\n"
                + "})\n\n"
                + "// 未登录一律踢到 /login；401 时 request.js 会清 token 并跳这里\n"
                + "router.beforeEach((to, from, next) => {\n"
                + "  const token = localStorage.getItem('token')\n"
                + "  if (to.path !== '/login' && !token) {\n"
                + "    next('/login')\n"
                + "  } else {\n"
                + "    next()\n"
                + "  }\n"
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

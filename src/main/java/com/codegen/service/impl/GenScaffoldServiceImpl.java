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
            write(backendRoot, "pom.xml", ScaffoldPomWriter.pomMultiParent(appName, project, pkg, stem, backendModule, micro, activiti, payEnabled), "backend", force, vo, counter);
            write(backendRoot, stem + "-common/pom.xml", ScaffoldPomWriter.pomMultiModule(pkg, parentArtifactId, stem, "common",
                    ScaffoldPomWriter.dep("org.springframework.boot", "spring-boot-starter-web", null)
                            + ScaffoldPomWriter.dep("org.springframework.boot", "spring-boot-starter-validation", null)
                            + ScaffoldPomWriter.dep("org.springframework.boot", "spring-boot-starter-aop", null)
                            + ScaffoldPomWriter.dep("com.baomidou", "mybatis-plus-spring-boot3-starter", "${mybatis-plus.version}")
                            // OpeLogController/MaskUtils 的 @Operation/@Schema 注解在这层
                            + ScaffoldPomWriter.dep("org.springdoc", "springdoc-openapi-starter-webmvc-ui", "2.3.0")
                            // V6.4 密码 BCrypt：仅 crypto 单模块，不引 Spring Security 全家桶（版本走 BOM）
                            + ScaffoldPomWriter.dep("org.springframework.security", "spring-security-crypto", null)
                            + ScaffoldPomWriter.dep("org.projectlombok", "lombok", "${lombok.version}")
                            // V6.3 支付模块类落 common 层，SDK 依赖随类归位（坑 41：谁 import 谁依赖）
                            + (payEnabled
                                ? ScaffoldPomWriter.dep("com.github.wechatpay-apiv3", "wechatpay-java", "${wechatpay.version}")
                                    + ScaffoldPomWriter.dep("com.alipay.sdk", "alipay-sdk-java", "${alipay.version}")
                                : ""),
                    false), "backend", force, vo, counter);
            write(backendRoot, stem + "-domain/pom.xml", ScaffoldPomWriter.pomMultiModule(pkg, parentArtifactId, stem, "domain",
                    ScaffoldPomWriter.internalDep(pkg, stem, "common")
                            + ScaffoldPomWriter.dep("com.baomidou", "mybatis-plus-spring-boot3-starter", "${mybatis-plus.version}")
                            + ScaffoldPomWriter.dep("org.projectlombok", "lombok", "${lombok.version}"),
                    false), "backend", force, vo, counter);
            write(backendRoot, stem + "-service/pom.xml", ScaffoldPomWriter.pomMultiModule(pkg, parentArtifactId, stem, "service",
                    ScaffoldPomWriter.internalDep(pkg, stem, "domain")
                            + ScaffoldPomWriter.dep("org.springframework.boot", "spring-boot-starter-web", null)
                            + ScaffoldPomWriter.dep("org.springframework.boot", "spring-boot-starter-validation", null)
                            + ScaffoldPomWriter.dep("org.springdoc", "springdoc-openapi-starter-webmvc-ui", "2.3.0")
                            + ScaffoldPomWriter.dep("org.apache.poi", "poi-ooxml", "5.2.5")
                            // V6.0：Feign 骨架在 .remote 包，注解依赖必须在本模块（bootstrap 只放 lb 运行时）
                            + (micro ? ScaffoldPomWriter.dep("org.springframework.cloud", "spring-cloud-starter-openfeign", null) : "")
                            + ScaffoldPomWriter.dep("org.projectlombok", "lombok", "${lombok.version}")
                            + ScaffoldPomWriter.extraDepsXml(dto),
                    false), "backend", force, vo, counter);
            write(backendRoot, stem + "-bootstrap/pom.xml", ScaffoldPomWriter.pomMultiModule(pkg, parentArtifactId, stem, "bootstrap",
                    ScaffoldPomWriter.internalDep(pkg, stem, "service")
                            // V6.0 微服务设施：注册/配置中心与 lb 运行时（版本由聚合 pom BOM 管理，enabled=false 时零行为；
                            // openfeign 注解依赖在 service 模块——Feign 骨架在那层编译）
                            + (micro
                                ? ScaffoldPomWriter.dep("com.alibaba.cloud", "spring-cloud-starter-alibaba-nacos-discovery", null)
                                    + ScaffoldPomWriter.dep("com.alibaba.cloud", "spring-cloud-starter-alibaba-nacos-config", null)
                                    + ScaffoldPomWriter.dep("org.springframework.cloud", "spring-cloud-starter-loadbalancer", null)
                                : "")
                            // V6.1：流程引擎在启动壳层装配（Runner/引擎服务都在 common，随依赖可达）
                            + (activiti
                                ? ScaffoldPomWriter.dep("org.activiti", "activiti-spring-boot-starter", "${activiti.version}")
                                : "")
                            + "        <dependency>\n"
                            + "            <groupId>com.mysql</groupId>\n"
                            + "            <artifactId>mysql-connector-j</artifactId>\n"
                            + "            <scope>runtime</scope>\n"
                            + "        </dependency>\n",
                    true), "backend", force, vo, counter);
            if (micro) {
                // V6.0 微服务设施：gateway 模块（webflux，独立进程，端口=服务端口+1）
                write(gatewayRoot, "pom.xml", ScaffoldPomWriter.pomMultiModule(pkg, parentArtifactId, stem, "gateway",
                        ScaffoldPomWriter.dep("org.springframework.cloud", "spring-cloud-starter-gateway", null)
                                + ScaffoldPomWriter.dep("com.alibaba.cloud", "spring-cloud-starter-alibaba-nacos-discovery", null)
                                + ScaffoldPomWriter.dep("org.springframework.cloud", "spring-cloud-starter-loadbalancer", null)
                                + ScaffoldPomWriter.dep("com.alibaba.cloud", "spring-cloud-starter-alibaba-nacos-config", null)
                                + ScaffoldPomWriter.dep("org.springframework.cloud", "spring-cloud-starter-bootstrap", null),
                        true), "backend", force, vo, counter);
                write(gatewayRoot, "src/main/java/" + pkg.replace('.', '/') + "/gateway/"
                                + appClass + "GatewayApplication.java",
                        ScaffoldPomWriter.gatewayApplicationJava(pkg, appClass), "backend", force, vo, counter);
                write(gatewayRoot, "src/main/resources/application.yml",
                        ScaffoldPomWriter.gatewayApplicationYml(pkg, stem, serverPort), "backend", force, vo, counter);
                // Feign 骨架：只给约定与示例，跨服务接口按需手写（规划定案，不按表自动生成）
                write(serviceRoot, "src/main/java/" + pkg.replace('.', '/') + "/remote/RemoteSampleClient.java",
                        ScaffoldPomWriter.remoteSampleClientJava(pkg, stem), "backend", force, vo, counter);
            }
        } else {
            write(backendRoot, "pom.xml", ScaffoldPomWriter.pomXml(dto, appName, project, pkg, backendModule, serverPort, activiti, payEnabled, testEnabled), "backend", force, vo, counter);
        }

        String pkgPath = "src/main/java/" + pkg.replace('.', '/');
        // 启动类与运行配置归 bootstrap；包名不变（仍是 <pkg> 根），@SpringBootApplication 默认扫描覆盖全部模块；
        // micro 时启动类追加 @EnableFeignClients 扫 remote 包（跨服务调用骨架）
        write(bootRoot, pkgPath + "/" + appClass + "Application.java",
                ScaffoldPomWriter.applicationJava(pkg, appClass, micro, activiti, payEnabled, fileEnabled), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/result/Result.java", ScaffoldCommonWriter.resultJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/result/ResultCode.java", ScaffoldCommonWriter.resultCodeJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/exception/BusinessException.java",
                ScaffoldCommonWriter.businessExceptionJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/exception/GlobalExceptionHandler.java",
                ScaffoldCommonWriter.globalExceptionHandlerJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/context/UserContext.java", ScaffoldCommonWriter.userContextJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/entity/BaseEntity.java", ScaffoldCommonWriter.baseEntityJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/page/PageQuery.java", ScaffoldCommonWriter.pageQueryJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/config/MybatisPlusConfig.java",
                ScaffoldCommonWriter.mybatisPlusConfigJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/datascope/DataScopeInterceptor.java",
                ScaffoldCommonWriter.dataScopeInterceptorJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/datascope/DataScopeRegistry.java",
                ScaffoldCommonWriter.dataScopeRegistryJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/log/Log.java", ScaffoldCommonWriter.logAnnotationJava(pkg),
                "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/log/LogAspect.java", ScaffoldCommonWriter.logAspectJava(pkg),
                "backend", force, vo, counter);
        // 归属地解析器：日志切面依赖它把 IP 翻成省市，独立成类便于替换成自建接口
        write(commonRoot, pkgPath + "/common/log/IpRegionResolver.java", ScaffoldCommonWriter.ipRegionResolverJava(pkg),
                "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/log/Ip2RegionSearcher.java", ScaffoldCommonWriter.ip2RegionSearcherJava(pkg),
                "backend", force, vo, counter);
        // 离线 IP 库是二进制，跟着解析器一起发；没有它归属地就只能赌公网接口的可用性
        writeBinary(commonRoot, "src/main/resources/ip/ip2region.db", Ip2RegionSearcher.RESOURCE,
                "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/system/OpeLog.java", ScaffoldCommonWriter.operLogEntityJava(pkg),
                "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/system/OpeLogMapper.java", ScaffoldCommonWriter.operLogMapperJava(pkg),
                "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/system/OpeLogController.java", ScaffoldCommonWriter.operLogControllerJava(pkg),
                "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/config/LongIdJacksonConfig.java",
                ScaffoldCommonWriter.longIdJacksonConfigJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/config/TimeJacksonConfig.java",
                ScaffoldCommonWriter.timeJacksonConfigJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/util/DictTexts.java",
                ScaffoldCommonWriter.dictTextsJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/handler/AuditMetaObjectHandler.java",
                ScaffoldCommonWriter.auditMetaObjectHandlerJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/util/MaskUtils.java", ScaffoldCommonWriter.maskUtilsJava(pkg),
                "backend", force, vo, counter);
        if (Boolean.TRUE.equals(dto.getWithAuth())) {
            write(commonRoot, pkgPath + "/common/config/AuthContextFilter.java",
                    ScaffoldAuthWriter.authContextFilterJava(pkg, payEnabled, fileEnabled), "backend", force, vo, counter);
            // V5.0 F-07：登录基座闭环——以前 filter「非空 token 即放行」是占位实现，
            // 401 拦得住陌生人却发不出合法 token，登录页成了摆设
            write(commonRoot, pkgPath + "/common/auth/TokenStore.java",
                    ScaffoldAuthWriter.tokenStoreJava(pkg), "backend", force, vo, counter);
            write(commonRoot, pkgPath + "/common/auth/AuthController.java",
                    ScaffoldAuthWriter.authControllerJava(pkg), "backend", force, vo, counter);
            // V6.4 P0-3：接口权限点后端强制——贡献者接口 + 集中校验器 + 基座端点豁免清单
            write(commonRoot, pkgPath + "/common/auth/ApiPermContributor.java",
                    ScaffoldAuthWriter.apiPermContributorJava(pkg), "backend", force, vo, counter);
            write(commonRoot, pkgPath + "/common/auth/PermissionChecker.java",
                    ScaffoldAuthWriter.permissionCheckerJava(pkg), "backend", force, vo, counter);
            write(commonRoot, pkgPath + "/common/auth/FlowApiPerms.java",
                    ScaffoldAuthWriter.flowApiPermsJava(pkg), "backend", force, vo, counter);
            if (payEnabled) {
                write(commonRoot, pkgPath + "/common/auth/PayApiPerms.java",
                        ScaffoldAuthWriter.payApiPermsJava(pkg), "backend", force, vo, counter);
            }
            // V6.4 P0-4：登录日志只读查询接口
            write(commonRoot, pkgPath + "/system/LoginLogController.java",
                    ScaffoldAuthWriter.loginLogControllerJava(pkg), "backend", force, vo, counter);
            write(bootRoot, "db/init_sys_user.sql", seedSql(),
                    "backend", force, vo, counter);
        }
        // V6.8 字典脚手架（恒写）：建表 + 通用种子 + 只读接口。
        // 之前生成页面的字典下拉依赖目标库「恰好已有」sys_dict_data，新库直接空下拉；
        // 现在产物自带表结构与种子，接口挂在 /system/dict（白名单只读）。
        write(bootRoot, "db/dict.sql", ScaffoldTextSupport.loadScaffoldResource("scaffold/dict.sql"),
                "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/system/SysDictController.java",
                dictControllerJava(pkg), "backend", force, vo, counter);
        // V6.8 素材链路：static/img 里放一张默认图，/img/placeholder.svg 开箱即 200
        // （白名单常驻 /img，前端 devServer 代理 /img 不剥前缀——三处都对上这条路径）
        write(bootRoot, "src/main/resources/static/img/placeholder.svg",
                ScaffoldTextSupport.loadScaffoldResource("scaffold/web/placeholder.svg"), "backend", force, vo, counter);

        // V6.1 流程基座（恒写）：修复 controller.ftl 引用 ${pkg}.common.flow.FlowService
        // 而基座从未生成的历史断裂——绑定 flow_code 的表生成产物此前编译不过。
        // 引擎实现二选一：builtin=移植平台简易流（无第三方依赖）；activiti=引擎实现 + 部署 Runner
        write(commonRoot, pkgPath + "/common/flow/FlowService.java",
                ScaffoldFlowWriter.flowServiceJava(pkg), "backend", force, vo, counter);
        write(commonRoot, pkgPath + "/common/flow/FlowRecord.java",
                ScaffoldFlowWriter.flowRecordJava(pkg), "backend", force, vo, counter);
        if (activiti) {
            write(commonRoot, pkgPath + "/common/flow/ActivitiFlowService.java",
                    ScaffoldFlowWriter.activitiFlowServiceJava(pkg), "backend", force, vo, counter);
            write(commonRoot, pkgPath + "/common/flow/FlowDeployRunner.java",
                    ScaffoldFlowWriter.flowDeployRunnerJava(pkg), "backend", force, vo, counter);
        } else {
            write(commonRoot, pkgPath + "/common/flow/BuiltinFlowService.java",
                    ScaffoldFlowWriter.builtinFlowServiceJava(pkg), "backend", force, vo, counter);
        }
        write(commonRoot, pkgPath + "/common/flow/FlowController.java",
                ScaffoldFlowWriter.flowControllerJava(pkg), "backend", force, vo, counter);
        // 产物库流程表：DDL 恒写；绑定流程的定义 INSERT 由调用方经 flowDefs 下发（平台不直连产物库）
        write(bootRoot, "db/init_sys_flow.sql", ScaffoldFlowWriter.initSysFlowSql(dto.getFlowDefs()),
                "backend", force, vo, counter);
        // V6.2 部署物：genDeploy 开启才生成（缺省关闭 → 存量产物与旧版逐文件一致）
        if (Boolean.TRUE.equals(dto.getGenDeploy())) {
            write(bootRoot, "Dockerfile", ScaffoldOpsWriter.dockerfileText(), "backend", force, vo, counter);
            write(bootRoot, ".dockerignore", ScaffoldOpsWriter.dockerignoreText(), "backend", force, vo, counter);
            write(bootRoot, "docker-compose.yml", ScaffoldOpsWriter.dockerComposeYml(project, serverPort, micro),
                    "backend", force, vo, counter);
            write(bootRoot, "deploy/ci-github-actions.yml.example", ScaffoldOpsWriter.ciGithubActionsText(),
                    "backend", force, vo, counter);
            write(bootRoot, "deploy/Jenkinsfile.example", ScaffoldOpsWriter.jenkinsfileText(),
                    "backend", force, vo, counter);
            vo.getNextSteps().add("部署物：docker build -t " + project.toLowerCase() + " . 后 docker compose up -d"
                    + "（基础镜像可经 --build-arg 覆盖；CI 用 deploy/ 下的 .example 改名即用）");
        }
        // V6.3 支付能力包：payEnabled 开启才生成（缺省关闭 → 存量产物与旧版逐文件一致）。
        // 渠道抽象 PaymentService + wxpay/alipay/mock 三实现；回调/补偿/收银台见 docs/design/V6.3支付能力包-设计.md。
        // 源码走 classpath 资源 scaffold-pay/*.java（__PKG__ 占位），比几千行转义字符串块可维护
        if (payEnabled) {
            write(bootRoot, "db/pay.sql", ScaffoldOpsWriter.payResource("pay.sql", pkg), "backend", force, vo, counter);
            for (String f : List.of("PayOrder.java", "PayOrderMapper.java", "PayOrderVO.java", "PayProps.java",
                    "PaymentService.java", "AbstractPayService.java", "MockPayService.java",
                    "WxPayNativeService.java", "AliPayPageService.java", "PayController.java",
                    "PayScheduleConfig.java", "PayReconcileJob.java")) {
                write(commonRoot, pkgPath + "/common/pay/" + f, ScaffoldOpsWriter.payResource(f, pkg),
                        "backend", force, vo, counter);
            }
            vo.getNextSteps().add("支付能力包：缺省 mock 渠道开箱即跑（收银台 /pay/cashier，模拟支付走与真渠道同构链路）；"
                    + "接真渠道改 yml pay.channel 并配置 PAY_* 环境变量，零代码改动");
        }
        // V6.6 文件能力包：fileEnabled 开启才生成（缺省关闭 → 存量产物与旧版逐文件一致）。
        // sys_file 元数据表 + /file 上传下载端点 + ImageUpload/FileUpload 前端组件；
        // 设计见 docs/design/V6.6文件能力包-设计.md。纯 Spring Web + MyBatis-Plus，无新依赖。
        if (fileEnabled) {
            write(bootRoot, "db/file.sql", ScaffoldOpsWriter.fileResource("file.sql", pkg), "backend", force, vo, counter);
            for (String f : List.of("FileRecord.java", "FileMapper.java", "FileProps.java",
                    "FileController.java", "FileWebConfig.java")) {
                write(commonRoot, pkgPath + "/common/file/" + f, ScaffoldOpsWriter.fileResource(f, pkg),
                        "backend", force, vo, counter);
            }
            // V6.4 权限闭环：/file 端点注册为「仅登录」（同 FlowApiPerms/PayApiPerms 模式）
            if (Boolean.TRUE.equals(dto.getWithAuth())) {
                write(commonRoot, pkgPath + "/common/auth/FileApiPerms.java",
                        ScaffoldOpsWriter.fileApiPermsJava(pkg), "backend", force, vo, counter);
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
                    ScaffoldOpsWriter.testResource("application-test.yml", pkg), "backend", force, vo, counter);
            write(bootRoot, "src/test/resources/schema/base.sql",
                    ScaffoldOpsWriter.testResource("base.sql", pkg), "backend", force, vo, counter);
            if (payEnabled) {
                write(bootRoot, "src/test/resources/schema/pay.sql",
                        ScaffoldOpsWriter.testResource("pay.sql", pkg), "backend", force, vo, counter);
            }
            if (fileEnabled) {
                write(bootRoot, "src/test/resources/schema/file.sql",
                        ScaffoldOpsWriter.testResource("file.sql", pkg), "backend", force, vo, counter);
            }
            write(bootRoot, "src/test/java/" + pkg.replace('.', '/') + "/LoginStateTestSupport.java",
                    ScaffoldOpsWriter.testResource("LoginStateTestSupport.java", pkg), "backend", force, vo, counter);
            vo.getNextSteps().add("测试骨架：cd " + bootRoot + " && mvn test（H2 内存库，无需外部 MySQL）；"
                    + "业务表的集成测试在代码生成（/gen/apply）时逐表落盘到 src/test；"
                    + "关掉请在 系统管理→项目 里把「测试骨架」设为不生成后重新脚手架");
        }
        write(bootRoot, "src/main/resources/application.yml",
                ScaffoldPomWriter.applicationYml(dto, pkg, project, serverPort, stem, micro, payEnabled, fileEnabled),
                "backend", force, vo, counter);
        if (micro) {
            // V6.0：Nacos 配置中心接入模板（注释态）。说明头里写清 Boot 3 默认不读 bootstrap.yml，
            // 真正的运行开关在 application.yml 的 spring.cloud.nacos.*——放这是为了注释态一目了然
            write(bootRoot, "src/main/resources/bootstrap.yml", ScaffoldPomWriter.bootstrapYml(stem),
                    "backend", force, vo, counter);
        }
        write(bootRoot, "src/main/resources/application-local.yml.example",
                ScaffoldPomWriter.applicationLocalExample(pkg), "backend", force, vo, counter);
        write(bootRoot, ".gitignore", ScaffoldPomWriter.backendGitignore(), "backend", force, vo, counter);
        write(bootRoot, "README.md", ScaffoldPomWriter.backendReadme(appName, pkg, backendModule, serverPort),
                "backend", force, vo, counter);

        // ---------- 前端 ----------
        if (Boolean.TRUE.equals(dto.getWithFrontend())) {
            write(frontendRoot, "package.json", ScaffoldFrontendWriter.frontendPackageJson(project, frontendModule, payEnabled),
                    "frontend", force, vo, counter);
            // V6.0 micro：前端请求走网关（端口=服务端口+1），apiBase 不带 /api 前缀（网关按模块路由）
            int proxyPort = micro ? serverPort + 1 : serverPort;
            write(frontendRoot, "vite.config.js", ScaffoldFrontendWriter.frontendViteConfig(webPort, proxyPort,
                    StringUtils.hasText(dto.getGatewayPrefix()) ? dto.getGatewayPrefix().trim() : "demo",
                    fileEnabled),
                    "frontend", force, vo, counter);
            write(frontendRoot, ".env.development",
                    ScaffoldFrontendWriter.frontendEnvDev(proxyPort), "frontend", force, vo, counter);
            write(frontendRoot, ".env.production", ScaffoldFrontendWriter.frontendEnvProd(), "frontend", force, vo, counter);
            // V9.0：Vite 的入口 HTML 必须在工程根目录（vue-cli 时代的 public/index.html
            // 会被 Vite 原样拷进 dist 而不当入口，直接 vite build 报 "Could not resolve entry module"）
            write(frontendRoot, "index.html",
                    ScaffoldFrontendWriter.frontendIndexHtml(appName, appLogo, themeColor, htmlLang, htmlDir, i18nOn),
                    "frontend", force, vo, counter);
            // V6.8 素材链路：产物自带占位图，图片列 onerror / el-image error 槽统一指向它，
            // 种子数据里指向不存在文件的 URL 不再显示浏览器破图
            write(frontendRoot, "public/placeholder.svg", ScaffoldTextSupport.loadScaffoldResource("scaffold/web/placeholder.svg"),
                    "frontend", force, vo, counter);
            write(frontendRoot, "src/main.js", ScaffoldFrontendWriter.frontendMainJs(i18nOn), "frontend", force, vo, counter);
            write(frontendRoot, "src/App.vue", ScaffoldFrontendWriter.frontendAppVue(), "frontend", force, vo, counter);
            write(frontendRoot, "src/utils/request.js", ScaffoldFrontendWriter.frontendRequestJs(), "frontend", force, vo, counter);
            write(frontendRoot, "src/layout/index.vue", ScaffoldFrontendWriter.frontendLayoutVue(appName, appLogo, themeColor, i18nOn),
                    "frontend", force, vo, counter);
            write(frontendRoot, "src/views/index.vue", ScaffoldFrontendWriter.frontendHomeVue(appName), "frontend", force, vo, counter);
            // V5.0 F-07：登录页与路由守卫（401 跳转目标从 request.js 来）
            write(frontendRoot, "src/views/login.vue", ScaffoldFrontendWriter.frontendLoginVue(appName, appLogo, themeColor), "frontend", force, vo, counter);
            write(frontendRoot, "src/router/index.js", ScaffoldFrontendWriter.frontendRouterJs(), "frontend", force, vo, counter);
            // 占位：route-sync 未跑前保证 webpack 能解析 import（后续被 GenMenuServiceImpl 整文件重建）
            write(frontendRoot, "src/router/gen-routes.js",
                    "// 该文件由代码生成器自动维护，请勿手工编辑。\n"
                  + "// 结构为三级：系统 → 业务域 → 页面，菜单树与路由共用同一套分组推导。\n"
                  + "export default []\n",
                    "frontend", force, vo, counter);
            write(frontendRoot, "src/store/index.js", ScaffoldFrontendWriter.frontendStoreJs(), "frontend", force, vo, counter);
            // V6.6 文件能力包：上传组件随开关写盘（生成页面的 image/file 列 import 这两个组件）
            if (fileEnabled) {
                write(frontendRoot, "src/components/ImageUpload.vue",
                        ScaffoldOpsWriter.fileResource("ImageUpload.vue", pkg), "frontend", force, vo, counter);
                write(frontendRoot, "src/components/FileUpload.vue",
                        ScaffoldOpsWriter.fileResource("FileUpload.vue", pkg), "frontend", force, vo, counter);
                // V6.8 多图字段：multiImage 列 import 这个组件（JSON 数组字符串 v-model）
                write(frontendRoot, "src/components/MultiImageUpload.vue",
                        ScaffoldOpsWriter.fileResource("MultiImageUpload.vue", pkg), "frontend", force, vo, counter);
            }
            // V8.0 产物国际化：只有开关打开才写这一组文件，存量工程零 diff
            if (i18nOn) {
                writeI18nResources(frontendRoot, dto, force, vo, counter);
                vo.getNextSteps().add("产物国际化：系统管理→文案翻译 可逐语言补译；"
                        + "页面右上角切换语言，RTL 语言自动镜像；语言包在 src/locales/");
            }
            write(frontendRoot, ".gitignore", ScaffoldFrontendWriter.frontendGitignore(), "frontend", force, vo, counter);
            write(frontendRoot, "README.md", ScaffoldFrontendWriter.frontendReadme(appName, frontendModule, webPort, serverPort),
                    "frontend", force, vo, counter);
        }

        // ---------- V6.5 移动端工程（uni-app 单源 H5 + 微信小程序） ----------
        // 与 PC 前端互不依赖：独立工程、独立构建；开关关闭时整段跳过，存量产物零 diff。
        // 表业务配置（mobile/src/config/tables/**）由表生成侧产出，这里只出工程骨架与动态页。
        if (Boolean.TRUE.equals(dto.getMobileEnabled())) {
            Path mobileRoot = resolveRoot(dto.getFrontendRoot(), outputProperties.getFrontendRoot(),
                    frontendModule + "-mobile");
            vo.setMobilePath(mobileRoot.toString());
            // micro 时 H5 代理指向网关（端口=服务端口+1），与 PC vite.config.js 同口径
            int apiTarget = micro ? serverPort + 1 : serverPort;
            String gwPrefix = StringUtils.hasText(dto.getGatewayPrefix())
                    ? dto.getGatewayPrefix().trim() : "demo";
            for (Map.Entry<String, String> e : ScaffoldOpsWriter.mobileResources(project, apiTarget, gwPrefix, htmlLang, htmlDir).entrySet()) {
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
                ScaffoldTextSupport.loadScaffoldResource("scaffold-i18n/i18n.js").replace("__DEFAULT_LOCALE__", defaultLocale),
                "frontend", force, vo, counter);
        // V8.1：i18n 纯函数核心（零依赖），与 i18n.js 同目录，随产物一起写出，供单测覆盖
        write(frontendRoot, "src/utils/i18n-core.js",
                ScaffoldTextSupport.loadScaffoldResource("scaffold-i18n/i18n-core.js"),
                "frontend", force, vo, counter);
        write(frontendRoot, "src/components/LanguageSwitch.vue",
                ScaffoldTextSupport.loadScaffoldResource("scaffold-i18n/LanguageSwitch.vue"),
                "frontend", force, vo, counter);
        // V8.1：翻译贡献校验脚本，随产物写出到 scripts/（CONTRIBUTING 引用的命令即此）
        write(frontendRoot, "scripts/check-locale-keys.mjs",
                ScaffoldTextSupport.loadScaffoldResource("scaffold-i18n/check-locale-keys.mjs"),
                "frontend", force, vo, counter);
        log.info("[I18nScaffold] 生成 {} 个语言包，默认语言 {}，词库 {} 键",
                locales.size(), defaultLocale, I18nCatalog.totalKeys());
    }

    /** JS 单引号字符串转义：语言自称里可能出现撇号（如 Norsk bokmål / Cymraeg） */
    private static String jsQuote(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("'", "\\'");
    }






    // ---------- V9.1 兼容桩 ----------
    // 反射基线测试（GenV64SecurityBaselineTest / GenV65MobileBaselineTest /
    // GenV68DictContractTest）仍以 getDeclaredMethod 挂在本类上；与 V7.5 移动端
    // 旧入口同款兼容策略：旧签名保留，实现委托给拆分后的 Writer。
    private String seedSql() { return ScaffoldSeedWriter.seedSql(); }

    private String dictControllerJava(String pkg) { return ScaffoldAuthWriter.dictControllerJava(pkg); }

    private Map<String, String> mobileResources(String project, int apiPort, String gwPrefix) {
        return ScaffoldOpsWriter.mobileResources(project, apiPort, gwPrefix);
    }

    private String authContextFilterJava(String pkg, boolean payEnabled, boolean fileEnabled) {
        return ScaffoldAuthWriter.authContextFilterJava(pkg, payEnabled, fileEnabled);
    }

    private String authControllerJava(String pkg) { return ScaffoldAuthWriter.authControllerJava(pkg); }

    private String tokenStoreJava(String pkg) { return ScaffoldAuthWriter.tokenStoreJava(pkg); }

    private String permissionCheckerJava(String pkg) { return ScaffoldAuthWriter.permissionCheckerJava(pkg); }

    private String flowApiPermsJava(String pkg) { return ScaffoldAuthWriter.flowApiPermsJava(pkg); }

    private String payApiPermsJava(String pkg) { return ScaffoldAuthWriter.payApiPermsJava(pkg); }

    private String loginLogControllerJava(String pkg) { return ScaffoldAuthWriter.loginLogControllerJava(pkg); }

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

            Path vueConfig = findFirst(frontend, "vite.config.js", 3);
            items.add(fileItem("frontend.vueConfig", "vite.config.js", "frontend", vueConfig,
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

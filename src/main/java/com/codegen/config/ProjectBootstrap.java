package com.codegen.config;

import com.codegen.entity.SysProject;
import com.codegen.service.SysProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 系统台账启动引导。
 *
 * <p>解决一个迁移期的尴尬：V3.0 之前配置只有 {@code application.yml} 一份，
 * 迁移脚本插入的「默认系统」只有编码和名称，路径是空的。如果让用户手工
 * 从 yml 抄一遍到管理页，换机器 / 改 application-local.yml 就又会不一致。
 *
 * <p>所以这里做单向同步：**只补空字段，不覆盖已有值**。
 * 用户一旦在页面上改过某个字段，就说明那是他的选择，启动时不能再被 yml 改回去。
 *
 * <p>{@code @Order} 放在模板种子之后无所谓，两者互不依赖。
 */
@Component
@Order(100)
public class ProjectBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProjectBootstrap.class);

    /** 迁移脚本建出来的默认系统编码 */
    private static final String DEFAULT_CODE = "default";

    @Autowired
    private SysProjectService projectService;

    @Autowired
    private GenOutputProperties outputProperties;

    @Autowired
    private GenSelfCheckProperties selfCheckProperties;

    @Override
    public void run(ApplicationArguments args) {
        try {
            SysProject project = projectService.getByCode(DEFAULT_CODE);
            if (project == null) {
                // 迁移脚本没跑过，或用户把默认系统删了。不擅自建，避免掩盖「脚本没执行」这个事实
                log.warn("ProjectBootstrap: 未找到编码为 {} 的系统，跳过 yml 同步；"
                        + "请确认已执行 docs/sql/V3.0__multi_project.sql", DEFAULT_CODE);
                return;
            }
            boolean changed = false;
            changed |= fillIfBlank(project::getBackendRoot, project::setBackendRoot,
                    outputProperties.getBackendRoot());
            changed |= fillIfBlank(project::getFrontendRoot, project::setFrontendRoot,
                    outputProperties.getFrontendRoot());
            changed |= fillIfBlank(project::getBackendPath, project::setBackendPath,
                    outputProperties.getBackendPath());
            changed |= fillIfBlank(project::getFrontendPath, project::setFrontendPath,
                    outputProperties.getFrontendPath());
            changed |= fillIfBlank(project::getRootPackage, project::setRootPackage,
                    outputProperties.getRootPackage());
            changed |= fillIfBlank(project::getGatewayPrefix, project::setGatewayPrefix,
                    outputProperties.getGatewayPrefix());
            changed |= fillIfBlank(project::getSelfcheckClasspath, project::setSelfcheckClasspath,
                    selfCheckProperties.getClasspath());

            if (changed) {
                projectService.update(project);
                log.info("ProjectBootstrap: 已用 application.yml 补齐默认系统的空配置项");
            }
        } catch (Exception e) {
            // 引导失败不能影响服务启动：配置缺失时生成会回落到全局 yml，仍可工作
            // 不带堆栈时「缺表 / 连不上库 / 字段不存在」全被压成一行 message，
            // 服务照常 UP 却要到运行期才炸，排查只能靠猜；末尾传 e 让 slf4j 打完整堆栈。
            log.warn("ProjectBootstrap: 同步默认系统配置失败，已跳过：{}", e.getMessage(), e);
        }
    }

    /**
     * 只有当前值为空、且 yml 有值时才写入。
     *
     * @return 是否发生了变更
     */
    private boolean fillIfBlank(java.util.function.Supplier<String> getter,
                                java.util.function.Consumer<String> setter,
                                String fallback) {
        if (StringUtils.hasText(getter.get()) || !StringUtils.hasText(fallback)) {
            return false;
        }
        setter.accept(fallback);
        return true;
    }
}

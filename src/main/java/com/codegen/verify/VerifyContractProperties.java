package com.codegen.verify;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * V9.18 验证契约配置（S9 产物等价性 / S8 截图目标）。
 *
 * <p>阈值由后端统一管理：执行器把这里的值注入子进程环境变量，
 * Node 侧 {@code scripts/dir-diff.mjs} 与 {@code testcenter-suites.mjs} 读取后做判定，
 * 页面、CLI、CI 三条入口拿到的都是同一份阈值，不存在「页面宽松、CI 严格」的漂移。
 *
 * <pre>
 * verify:
 *   contract:
 *     max-added: 20          # 允许的新增文件数，超出即 S9 FAILED
 *     max-removed: 0         # 默认不允许删文件（等价性守门）
 *     max-modified: 50       # 允许的修改文件数
 *     whitelist: node_modules,dist,.cache
 *     new-dir: ""            # 新生成产物目录；为空则 S9 SKIPPED
 *     baseline-dir: ""       # 基线产物目录；为空则 S9 SKIPPED
 *     shot-urls: ""          # 形如 login=http://127.0.0.1:9530/#/login；为空用默认控制台地址
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "verify.contract")
public class VerifyContractProperties {

    /** 超过即失败；默认 20 给生成器新增字段留余量，但不会放过成片差异 */
    private int maxAdded = 20;
    /** 基线里有的文件被删掉通常意味着模板回归，默认零容忍 */
    private int maxRemoved = 0;
    private int maxModified = 50;
    /** 逗号分隔；命中目录前缀或中间段即忽略 */
    private String whitelist = "node_modules,dist,.cache,.git";
    /** 为空表示本环境没有基线，S9 如实 SKIPPED */
    private String newDir = "";
    private String baselineDir = "";
    /** 为空则用脚本内置的默认控制台登录页 */
    private String shotUrls = "";
    private int shotTimeoutMs = 20000;
}

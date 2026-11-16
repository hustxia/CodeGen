package com.codegen.testcenter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 测试中心配置。默认值全部可离线工作，不写死任何本机绝对路径。
 *
 * <pre>
 * app:
 *   test-center:
 *     store: jdbc            # jdbc（默认，落 MySQL）| memory（无 DB 环境调试用）
 *     workdir: ""            # 默认 ${user.home}/.codegen/test-center/runs
 *     server-root: ""        # 默认 ${user.dir}
 *     web-root: ""           # 默认 serverRoot/../codegen-web
 *     node-bin: node
 *     tail-lines: 200
 *     keep-runs: 10
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.test-center")
public class TcProperties {

    /** jdbc = MyBatis-Plus 落库；memory = 进程内存储（仅用于无 DB 环境调试 / 单测） */
    private String store = "jdbc";
    private String workdir = "";
    private String serverRoot = "";
    private String webRoot = "";
    private String nodeBin = "node";
    private int tailLines = 200;
    private int keepRuns = 10;
    private boolean enabled = true;

    public Path runsRoot() {
        return workdir == null || workdir.isBlank()
                ? Paths.get(System.getProperty("user.home"), ".codegen", "test-center", "runs")
                : Paths.get(workdir);
    }

    public Path workDirOf(String runNo) {
        return runsRoot().resolve(runNo).toAbsolutePath().normalize();
    }

    public Path serverRoot() {
        return (serverRoot == null || serverRoot.isBlank())
                ? Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
                : Paths.get(serverRoot).toAbsolutePath().normalize();
    }

    public Path webRoot() {
        return (webRoot == null || webRoot.isBlank())
                ? serverRoot().getParent().resolve("codegen-web").normalize()
                : Paths.get(webRoot).toAbsolutePath().normalize();
    }
}

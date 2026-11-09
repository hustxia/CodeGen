package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 生成自检结果
 */
@Data
public class GenSelfCheckVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 是否通过（无 ERROR 即通过）
     */
    private boolean passed;

    /**
     * 参与检查的文件数
     */
    private int fileCount;

    /**
     * 参与编译的 Java 文件数（仅 src/main；产物测试见 {@link #testCount}）
     */
    private int javaCount;

    /**
     * 产物测试文件数（src/test/java，V7.3 起单独用测试 classpath 编译，不混入主编译）
     */
    private int testCount;

    private int errorCount;

    private int warningCount;

    /**
     * 耗时（毫秒）
     */
    private long elapsedMs;

    /**
     * 编译器是否可用（无 JDK 时为 false，仅做结构检查）
     */
    private boolean compilerAvailable;

    private List<GenCheckIssueVO> issues = new ArrayList<>();

    public void addIssue(String file, Long line, String severity, String message) {
        issues.add(new GenCheckIssueVO(file, line, severity, message));
        if ("ERROR".equals(severity)) {
            errorCount++;
        } else {
            warningCount++;
        }
        passed = errorCount == 0;
    }
}

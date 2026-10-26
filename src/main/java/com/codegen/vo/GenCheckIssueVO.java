package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 生成自检问题项
 */
@Data
public class GenCheckIssueVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 出错文件（相对路径）
     */
    private String file;

    /**
     * 行号，0 表示与具体行无关
     */
    private Long line;

    /**
     * 级别：ERROR / WARNING
     */
    private String severity;

    /**
     * 消息
     */
    private String message;

    public GenCheckIssueVO() {
    }

    public GenCheckIssueVO(String file, Long line, String severity, String message) {
        this.file = file;
        this.line = line;
        this.severity = severity;
        this.message = message;
    }
}

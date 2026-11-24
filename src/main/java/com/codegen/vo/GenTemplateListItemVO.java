package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 模板清单项（模板影响面预览左侧列表）
 */
@Data
public class GenTemplateListItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模板相对路径（相对 resources，如 gen-templates/controller.ftl）
     */
    private String template;

    /**
     * 所属目录（如 gen-templates、scaffold-mobile/src/pages）
     */
    private String directory;

    /**
     * 文件名（如 controller.ftl）
     */
    private String name;

    /**
     * 引用次数（Java 代码字面量 + ftl include 的粗略计数）
     */
    private int refCount;
}

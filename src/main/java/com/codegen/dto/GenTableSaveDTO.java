package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 代码生成器-表配置保存 DTO（新增/覆盖/修改）
 */
@Data
public class GenTableSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，修改时必填
     */
    private Long id;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 表注释（功能名）
     */
    private String tableComment;

    /**
     * 实体类名（大驼峰）
     */
    private String className;

    /**
     * 前端模块名
     */
    private String moduleName;

    /**
     * 生成 Java 包名
     */
    private String packageName;

    /**
     * 所属系统（sys_project.id）。为空表示未绑定，生成时回落全局配置。
     */
    private Long projectId;

    /**
     * 后端输出基准相对路径
     */
    private String backendPath;

    /**
     * 后端仓库根绝对路径（写盘用）
     */
    private String backendRoot;

    /**
     * 前端仓库根绝对路径（写盘用）
     */
    private String frontendRoot;

    /**
     * 前端网关服务前缀
     */
    private String gatewayPrefix;

    /**
     * 作者
     */
    private String author;

    /**
     * 表名冲突时是否覆盖
     */
    private Boolean overwrite;

    /**
     * 功能开关（1=生成 0=不生成）
     */
    private Integer genEntity;
    private Integer genMapper;
    private Integer genService;
    private Integer genController;
    private Integer genPageApi;
    private Integer genDetailApi;
    private Integer genAddApi;
    private Integer genEditApi;
    private Integer genDeleteApi;
    private Integer genFrontend;
    private Integer genApiJs;
    private Integer genVuePage;
    private Integer genQueryForm;
    private Integer genAddBtn;
    private Integer genEditBtn;
    private Integer genDeleteBtn;

    /**
     * 备注
     */
    private String remark;

    /**
     * 目标模块（V1.4-3）：命中 gen.output.modules 的 key 时按该模块输出
     */
    private String targetModule;

    /**
     * 树形表父字段（V1.4-2），如 parent_id
     */
    private String treeParentColumn;

    /**
     * 树形表展示字段，如 category_name
     */
    private String treeLabelColumn;

    /**
     * 主子表：主表 ID（V1.4-1），从表填
     */
    private Long masterTableId;

    /**
     * 主子表：从表指向主表的外键字段，如 order_id
     */
    private String masterForeignKey;

    /**
     * 字段配置
     */
    private List<GenColumnDTO> columns;
}

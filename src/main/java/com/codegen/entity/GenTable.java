package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 代码生成器-表配置
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_table")
public class GenTable extends BaseEntity {

    /**
     * 表名（唯一）
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
     * 前端模块名（api/<模块>.js、views/<模块>/）
     */
    private String moduleName;

    /**
     * 生成 Java 包名
     */
    private String packageName;

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
     * 开关:生成entity
     */
    private Integer genEntity;

    /**
     * 开关:生成mapper
     */
    private Integer genMapper;

    /**
     * 开关:生成service+impl
     */
    private Integer genService;

    /**
     * 开关:生成controller
     */
    private Integer genController;

    /**
     * 开关:分页接口
     */
    private Integer genPageApi;

    /**
     * 开关:详情接口
     */
    private Integer genDetailApi;

    /**
     * 开关:新增接口
     */
    private Integer genAddApi;

    /**
     * 开关:修改接口
     */
    private Integer genEditApi;

    /**
     * 开关:删除接口
     */
    private Integer genDeleteApi;

    /**
     * 开关:生成前端
     */
    private Integer genFrontend;

    /**
     * 开关:生成api.js
     */
    private Integer genApiJs;

    /**
     * 开关:生成vue页面
     */
    private Integer genVuePage;

    /**
     * 开关:查询区
     */
    private Integer genQueryForm;

    /**
     * 开关:新增按钮
     */
    private Integer genAddBtn;

    /**
     * 开关:编辑按钮
     */
    private Integer genEditBtn;

    /**
     * 开关:删除按钮
     */
    private Integer genDeleteBtn;

    /**
     * 备注
     */
    private String remark;

    /**
     * 目标模块 key（admin/product/order/...），选定后回填 backendPath/packageName/gatewayPrefix
     */
    private String targetModule;

    /**
     * 树形父级列（如 parent_id），非空即按树形生成
     */
    private String treeParentColumn;

    /**
     * 树节点显示列
     */
    private String treeLabelColumn;

    /**
     * 作为子表时所属主表的 gen_table.id
     */
    private Long masterTableId;

    /**
     * 子表中指向主表主键的列（如 order_id）
     */
    private String masterForeignKey;

    /**
     * 已注册菜单ID（sys_menu.id），为空表示尚未注册
     */
    private Long menuId;

    /**
     * 最近一次写盘时间
     */
    private LocalDateTime lastGenTime;

    /**
     * 最近一次写盘操作人
     */
    private Long lastGenBy;
}

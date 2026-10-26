package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
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
     * 开关：生成操作日志注解 @Log:0否1是。
     * 未设置时按「生成 Service 即记录」处理，新增字段默认为开。
     */
    private Integer genLog;

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
     * 主子表层级深度：0 为顶层，1 为一级子表，2 为二级子表（P3-1 多级主子孙）
     */
    private Integer masterDepth;

    /**
     * 表关系类型：middle 表示多对多中间表（P3-2），此类表不生成独立页面
     */
    private String relationType;

    /**
     * P3-3 绑定的审批流程编码（sys_flow_def.flow_code），为空表示不启用审批
     */
    private String flowCode;

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

    /**
     * V4.4 删除级联模式（仅主子表主表生效）：NULL=未配置（回落 sys_project 默认，再回落硬编码级联），
     * 0=阻止（存在子行时拒删并提示）1=级联（同事务删除全部子表行）2=不处理（维持 removeById 现状）。
     * 必须可空：若 NOT NULL DEFAULT 1，表级永远是 1，系统默认就成了死配置。
     */
    private Integer deleteCascadeMode;

    /**
     * V4.5 打印单据：NULL=跟随系统默认（默认关）1=生成 A4 打印页 print.vue 0=强制不生成。
     * 必须可空三态（坑库 mysql.md）：NOT NULL DEFAULT 会让系统默认层被表级短路成死配置。
     * 有效生成还需：gen_frontend=1、gen_api_js=1、非从表、有取数接口（with-children 或详情）。
     */
    private Integer genPrint;

    /**
     * 所属系统（sys_project.id）。为空表示沿用 application.yml 的全局默认，
     * 这是 V3.0 之前的存量表兼容路径。
     *
     * <p>生成时的配置回落顺序：本表字段 → 所属系统 → 全局默认。
     *
     * <p>更新策略显式设为 IGNORED：MyBatis-Plus 默认 NOT_NULL，null 字段不进 UPDATE，
     * 用户在弹窗里把系统清空想「解绑」时会静默失败。这里允许写 null。
     * 代价是其它走 updateById 的局部更新必须带上此字段——
     * 目前只有 updateConfig / saveConfig 两处写它，且都带着完整 DTO，不存在误清风险。
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long projectId;
}

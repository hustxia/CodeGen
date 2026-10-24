package com.codegen.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.codegen.dto.GenColumnDTO;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 代码生成器-表配置 VO
 */
@Data
public class GenTableVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 雪花 ID：以字符串下发，避免前端精度丢失
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String tableName;
    private String tableComment;
    private String className;
    private String moduleName;
    private String packageName;
    private String backendPath;
    private String backendRoot;
    private String frontendRoot;
    private String gatewayPrefix;
    private String author;
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
    private String remark;

    /**
     * 目标模块（V1.4-3）
     */
    private String targetModule;

    /**
     * 树形父字段（V1.4-2）
     */
    private String treeParentColumn;

    /**
     * 树形展示字段
     */
    private String treeLabelColumn;

    /**
     * 主子表：主表 ID（V1.4-1）
     */
    private Long masterTableId;

    /**
     * 主子表：从表外键字段
     */
    private String masterForeignKey;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 最近一次写盘时间（列表「生成状态」列用）
     */
    private LocalDateTime lastGenTime;

    private Long lastGenBy;

    /**
     * 结构漂移：库里新增了、但字段配置里没有的列
     */
    private List<String> driftNewColumns;

    /**
     * 结构漂移：字段配置里还有、但库里已删除的列
     */
    private List<String> driftMissingColumns;

    /**
     * 漂移列总数（0 = 配置与表结构一致）
     */
    private Integer driftCount = 0;

    /**
     * 字段配置（详情接口返回）
     */
    /**
     * 已注册菜单ID（字符串下发，避免前端精度丢失）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long menuId;

    /**
     * 所属系统ID（字符串下发，避免前端精度丢失）。
     *
     * <p>为空表示 V3.0 存量表，生成时回落到 application.yml 的全局默认。
     * 列表页要用它显示系统归属，编辑弹窗要用它回显下拉，所以必须出现在 VO 里——
     * 之前漏了这个字段，分页接口按系统筛得对，但前端拿不到值、列显示空白。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    /**
     * 所属系统的 17 个生成开关默认值（详情接口返回）。
     *
     * <p>key 与实体字段同名（genEntity / genFrontend ...），值已按「系统 → 1」解析。
     * 前端用它在开关旁标「默认」，并作为「恢复系统默认」的目标值，
     * 不必为了 17 个数字再单独请求一次系统详情。
     */
    private Map<String, Integer> projectGenDefaults;

    /**
     * V4.4 删除级联模式（表级配置原值）：null=未配置、0=阻止、1=级联、2=不处理。
     *
     * <p>下发的是「原值」而不是解析后的生效值，前端下拉才能如实显示「未配置」；
     * 真正参与模板渲染的三级回落结果在生成阶段计算（{@code GenCascadeMode.resolve}）。
     */
    private Integer deleteCascadeMode;

    /**
     * V4.5 打印单据（表级配置原值）：null=跟随系统、1=生成、0=强制不生成。
     * 下发原值让前端下拉能如实显示「跟随系统」；生效判定在生成阶段
     * 走 {@code GenTemplateModel.resolveGenPrint} 三级回落。
     */
    private Integer genPrint;

    private List<GenColumnDTO> columns;
}

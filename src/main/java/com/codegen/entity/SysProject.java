package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统台账：多系统输出的根配置。
 *
 * <p>为什么要有这张表：V2 之前 backend_root / root_package / gateway_prefix
 * 要么在 application.yml（进程级单份），要么抄在每张 gen_table 上。
 * 前者改一次要重启，后者改一个系统的包名得 UPDATE 全表，而且没人知道
 * 「这个系统到底有哪些表」。把系统提成一等公民后，gen_table 只存 project_id。
 *
 * <p>配置的回落顺序是「表级覆盖 → 本表 → application.yml 全局默认」，
 * 所以这里字段允许为空，空即表示沿用上一级。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_project")
public class SysProject extends BaseEntity {

    /**
     * 系统编码：英文小写，用于生成物路径、字典编码前缀、权限点前缀。
     * 一旦有表挂上去就不该再改，否则已生成的产物会对不上。
     */
    private String projectCode;

    /**
     * 系统名称（中文展示用）
     */
    private String projectName;

    /**
     * 后端工程根绝对路径，写盘基准
     */
    private String backendRoot;

    /**
     * 前端工程根绝对路径，写盘基准
     */
    private String frontendRoot;

    /**
     * 后端相对路径，如 demo-server/src/main/java/com/demo
     */
    private String backendPath;

    /**
     * 前端相对路径，如 demo-web；与 frontendRoot 重合时留空
     */
    private String frontendPath;

    /**
     * 根包名，生成物里对所有基座类的 import 前缀
     */
    private String rootPackage;

    /**
     * 网关 / 接口前缀，拼在 apiBase 前面
     */
    private String gatewayPrefix;

    /**
     * 该系统的依赖 classpath，支持 dir/* 通配符。
     *
     * <p>必须按系统分开配：系统 A 和系统 B 的依赖版本不同，
     * 共用一份会在自检编译时出现「依赖明明在却报 cannot find symbol」。
     */
    private String selfcheckClasspath;

    /**
     * 1 为默认系统，新建表时预选
     */
    private Integer isDefault;

    /**
     * 1 启用 0 停用。停用后不再出现在新建表的下拉里，但已有表仍按原配置生成，
     * 避免停用一个系统把存量表全部打断。
     */
    private Integer enabled;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * V4.4 主子表删除级联模式的系统默认值（0=阻止 1=级联 2=不处理）。
     * NULL = 未配置，回落硬编码级联（1）；表级 gen_table.delete_cascade_mode 优先于本字段。
     */
    private Integer defaultDeleteCascadeMode;

    /**
     * V4.5 打印单据的系统默认值。NULL = 未配置，回落硬编码关（0）；
     * 表级 gen_table.gen_print 优先于本字段（表级 NULL 时才看这里）。
     */
    private Integer defaultGenPrint;

    /**
     * V5.5 工程结构：NULL = 单模块（现状），"multi" = Maven 多模块
     * （parent + common/domain/service/bootstrap，微服务就绪单体）。
     *
     * <p>系统级唯一来源，不进表级回落；三态可空延续 V4.4 教训
     * （NOT NULL DEFAULT 会杀死回落层）。模块命名规则唯一实现在
     * {@link com.codegen.support.GenNaming#multiModuleStem}，scaffold 与
     * apply 两侧都必须调它，禁止各自拼 {@code <code>-common} 字符串。
     */
    private String projectStructure;

    /**
     * V6.1 产物流程引擎：NULL/builtin = 内置简易流（现状），activiti = Activiti 8 引擎。
     *
     * <p>三态可空同款约定（V4.4 教训）；保存时显式 SET（updateById NOT_NULL 不写 null）。
     */
    private String flowEngine;

    /**
     * V6.2 部署物系统默认值。NULL = 未配置，回落硬编码关（0）。
     */
    private Integer defaultGenDeploy;

    /**
     * V6.3 支付能力包系统级开关。NULL = 未配置，回落硬编码关（0）；
     * 回落唯一实现 {@link com.codegen.util.GenTemplateModel#resolvePay}。
     */
    private Integer payEnabled;

    /**
     * V6.5 移动端支持系统级开关。NULL = 未配置，回落硬编码关（0）；
     * 回落唯一实现 {@link com.codegen.util.GenTemplateModel#resolveMobile}。
     */
    private Integer mobileEnabled;

    /**
     * V6.6 文件能力包系统级开关。NULL = 未配置，回落硬编码关（0）；
     * 回落唯一实现 {@link com.codegen.util.GenTemplateModel#resolveFile}。
     * <p>三态可空同款约定（V4.4 教训）；保存时显式 SET（updateById NOT_NULL 不写 null）。
     */
    private Integer fileEnabled;

    /**
     * V6.7 产物测试出厂系统级开关。NULL = 未配置，回落硬编码<b>开</b>（1）；
     * 回落唯一实现 {@link com.codegen.util.GenTemplateModel#resolveTest}。
     * <p>方向与其它能力包相反：测试只写 src/test 目录，零运行时侵入，默认开收益最大。
     * <p>三态可空同款约定（V4.4 教训）；保存时显式 SET（updateById NOT_NULL 不写 null）。
     */
    private Integer testEnabled;

    /**
     * V6.8 品牌参数化：应用显示名（title / 侧边栏 / 登录页 / README 统一取这一个值）。
     * <p>空 = 回落 {@code projectName}，存量项目零 diff。
     */
    private String appName;

    /**
     * V6.8 品牌参数化：Logo 图片 URL（产物 {@code public/logo.png} 的远程替代）。
     * <p>空 = 使用脚手架内置 logo。
     */
    private String appLogo;

    /**
     * V6.8 品牌参数化：主题主色（如 {@code #e1251b}）。
     * <p>空 = 回落 {@code #e1251b}。唯一回落实现 {@link com.codegen.util.GenTemplateModel#resolveThemeColor}。
     */
    private String appThemeColor;

    /**
     * 备注
     */
    private String remark;

    /* =========================================================
     * 17 个生成开关的系统默认值（V3.1 P0-3）
     *
     * NULL = 未配置，回落硬编码 1。不用「NOT NULL DEFAULT 1」是为了区分
     * 「这个系统明确要求关掉」和「压根没配过」——否则以后调整硬编码默认值时，
     * 分不清哪些行是人为设置，一改就覆盖了用户的意图。
     *
     * 生效时机是「从数据库导入」：按这里填进 gen_table 的对应列。
     * 已经存在的表不会被改写，避免改一次系统配置把存量表全部翻掉。
     * ========================================================= */

    /**
     * 默认：生成 entity
     */
    private Integer defaultGenEntity;

    /**
     * 默认：生成 mapper
     */
    private Integer defaultGenMapper;

    /**
     * 默认：生成 service + impl
     */
    private Integer defaultGenService;

    /**
     * 默认：生成操作日志注解 @Log
     */
    private Integer defaultGenLog;

    /**
     * 默认：生成 controller
     */
    private Integer defaultGenController;

    /**
     * 默认：分页接口
     */
    private Integer defaultGenPageApi;

    /**
     * 默认：详情接口
     */
    private Integer defaultGenDetailApi;

    /**
     * 默认：新增接口
     */
    private Integer defaultGenAddApi;

    /**
     * 默认：修改接口
     */
    private Integer defaultGenEditApi;

    /**
     * 默认：删除接口
     */
    private Integer defaultGenDeleteApi;

    /**
     * 默认：生成前端
     */
    private Integer defaultGenFrontend;

    /**
     * 默认：生成 api.js
     */
    private Integer defaultGenApiJs;

    /**
     * 默认：生成 vue 页面
     */
    private Integer defaultGenVuePage;

    /**
     * 默认：生成查询区
     */
    private Integer defaultGenQueryForm;

    /**
     * 默认：生成新增按钮
     */
    private Integer defaultGenAddBtn;

    /**
     * 默认：生成编辑按钮
     */
    private Integer defaultGenEditBtn;

    /**
     * 默认：生成删除按钮
     */
    private Integer defaultGenDeleteBtn;
}

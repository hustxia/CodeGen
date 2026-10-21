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
     * 备注
     */
    private String remark;
}

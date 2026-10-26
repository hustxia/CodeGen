package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 表配置批量操作：批量设置输出参数 / 批量套用开关预设 / 批量删除。
 */
@Data
public class GenTableBatchDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<Long> ids;

    /**
     * 前端模块名；留空表示不修改
     */
    private String moduleName;

    private String packageName;

    private String author;

    private String backendRoot;

    private String frontendRoot;

    private String gatewayPrefix;

    /**
     * 批量改所属系统；null 表示不修改。
     *
     * <p>典型场景：先在旧系统下导了一批表，之后才新建目标系统——
     * 表的归属不会跟着「设为默认」自动搬家，需要在这里批量转移。
     */
    private Long projectId;

    /**
     * 开关预设：backend-only（仅后端 API）/ standard（标准 CRUD）/ readonly（只读列表）
     */
    private String preset;
}

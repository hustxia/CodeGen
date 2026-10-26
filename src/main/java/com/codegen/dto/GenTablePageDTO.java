package com.codegen.dto;

import com.codegen.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 代码生成器-表配置分页查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GenTablePageDTO extends PageQuery {

    private String tableName;

    private String tableComment;

    /**
     * 生成状态筛选：generated（生成过）/ ungenerated（没生成过）/ drift（结构漂移）/ menu（已注册菜单）
     */
    private String genStatus;

    /**
     * 所属系统筛选（V3.0）。null 为全部；-1 表示筛选「未绑定系统」的存量表
     */
    private Long projectId;
}

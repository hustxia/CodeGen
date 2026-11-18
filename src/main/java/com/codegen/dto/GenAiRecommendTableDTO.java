package com.codegen.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * V7.3 P0-3 表级 AI 推荐请求：表配置页勾选若干表后直接出建议。
 *
 * <p>与流水线入口的差别只在「推荐范围」：这里是用户在页面上明确选的表，
 * 流水线是整系统全表。两者产物格式一致（{@link com.codegen.vo.GenAiRecommendVO}），
 * 预览与应用逻辑完全共用。
 */
@Data
public class GenAiRecommendTableDTO {

    /** sys_project.id；表必须属于该系统，否则拒（防跨系统误推） */
    private Long projectId;

    /** 选中的 gen_table.id 列表；空表示「该系统全部表」 */
    private List<Long> tableIds = new ArrayList<>();
}

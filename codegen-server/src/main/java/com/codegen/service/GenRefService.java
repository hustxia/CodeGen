package com.codegen.service;

import com.codegen.vo.RefDetectItemVO;

import java.util.List;

/**
 * 外键关联推断：扫描 xxx_id 列，推断它属于哪张表的哪个名称列。
 */
public interface GenRefService {

    /**
     * 推断（可选落库）。
     *
     * @param projectId 只处理该项目的表，null 表示全部
     * @param apply     true 才回填 gen_table_column.ref_table / ref_label
     */
    List<RefDetectItemVO> detect(Long projectId, boolean apply);
}

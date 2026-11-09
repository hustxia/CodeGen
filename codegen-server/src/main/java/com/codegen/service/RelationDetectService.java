package com.codegen.service;

import com.codegen.vo.RelationDetectVO;

import java.util.List;

/**
 * P3-2 多对多（中间表）识别。
 *
 * <p>判定条件（全部满足才算，宁可漏判）：
 * <ol>
 *   <li>表里恰好有 2 个外键列，且分别指向两张不同的表</li>
 *   <li>这两个外键组成唯一键，或整表只有这 2 个业务列（其余是 id/审计字段）</li>
 *   <li>表名里同时含两张目标表的词根（如 product_tag），或两个外键名就是「表名_id」</li>
 * </ol>
 */
public interface RelationDetectService {

    /**
     * 扫描库里候选的中间表（只读，不落库）
     */
    List<RelationDetectVO> detect(String schema);

    /**
     * 把选中的关系落库：把中间表标记成 relation_type=middle，
     * 两个外键列标记 relation_key=1，两侧主表则在提示里体现。
     *
     * @param middleTables 要落库的中间表名
     * @return 实际标记的表数量
     */
    int apply(String schema, List<String> middleTables);
}

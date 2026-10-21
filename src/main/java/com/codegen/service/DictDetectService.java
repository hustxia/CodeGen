package com.codegen.service;

import com.codegen.vo.DictDetectItemVO;

import java.util.List;

/**
 * P2-3 字典全链路自动识别。
 *
 * <p>目标：建表阶段就把「男/女」「待付款/已付款」这类枚举变成正式字典，
 * 而不是让开发者在字段配置里手填 dict_type、再跑到字典管理里手工录一遍选项。
 *
 * <p>识别两条来源，优先级从高到低：
 * <ol>
 *   <li>列类型是 {@code enum('1','2')}：值直接来自 DDL，标签取列注释或值本身</li>
 *   <li>列注释里写了选项列表：{@code 性别:男=1,女=2} / {@code 状态（待付款、已付款）}</li>
 * </ol>
 *
 * <p>写入逻辑必须幂等：反复调用同一个表，不产生重复字典项，也不覆盖用户已手工改过的标签。
 */
public interface DictDetectService {

    /**
     * 识别某张表里可转成字典的列，只读，不落库。
     *
     * @param tableId gen_table.id
     */
    List<DictDetectItemVO> detect(Long tableId);

    /**
     * 识别单条 DDL 文本里的枚举（预览/导入阶段用，不需要先落 gen_table）。
     *
     * @param tableName   表名（用于生成 dict_code）
     * @param ddl         建表语句
     */
    List<DictDetectItemVO> detectFromDdl(String tableName, String ddl);

    /**
     * 把识别结果落库：建/补 sys_dict_type + sys_dict_data，并回填 gen_table_column.dict_type。
     *
     * <p>只处理传入的 dictCode 子集；为空表示全部落库。
     *
     * @param tableId    gen_table.id
     * @param dictCodes  要落库的字典编码；null/空 = 全部识别结果
     * @return 实际写入的字典数量
     */
    int apply(Long tableId, List<String> dictCodes);
}

package com.codegen.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.codegen.dto.GenTablePageDTO;
import com.codegen.dto.GenTableSaveDTO;
import com.codegen.entity.GenTable;
import com.codegen.vo.GenDbTableVO;
import com.codegen.vo.GenImportVO;
import com.codegen.vo.GenTableVO;

import java.util.List;

/**
 * 代码生成器-表配置 Service
 */
public interface GenTableService extends IService<GenTable> {

    /**
     * 分页查询表配置
     */
    IPage<GenTableVO> pageList(GenTablePageDTO dto);

    /**
     * 详情（含字段配置）
     */
    GenTableVO detail(Long id);

    /**
     * 保存表配置（tableName 冲突时需 overwrite=true 覆盖）
     */
    Long saveConfig(GenTableSaveDTO dto);

    /**
     * 修改表配置（整体替换字段配置）
     */
    void updateConfig(GenTableSaveDTO dto);

    /**
     * 删除表配置（级联逻辑删字段配置）
     */
    void deleteConfig(Long id);

    /**
     * 批量设置输出参数 / 套用开关预设
     *
     * @return 受影响行数
     */
    int batchUpdate(com.codegen.dto.GenTableBatchDTO dto);

    /**
     * 批量删除表配置（连同已注册的菜单）
     *
     * @return 删除行数
     */
    int batchDelete(java.util.List<Long> ids);

    /**
     * 结构漂移同步：把库里新增的列补进字段配置（只增不删）
     *
     * @param removeMissing true 时同时删除「库中已不存在」的字段配置
     */
    com.codegen.vo.GenColumnSyncVO syncColumns(Long id, boolean removeMissing);

    /**
     * 列出可导入的数据库（排除系统库）
     */
    List<String> listDbSchemas();

    /**
     * 列出指定库的基础表（排除 gen_table/gen_table_column）
     */
    List<GenDbTableVO> listDbTables(String schema);

    /**
     * 批量导入库表为生成配置（已存在的跳过）
     */
    List<GenImportVO> importTables(String schema, List<String> tableNames, Long projectId);

    /**
     * 表名是否已注册过生成配置（含逻辑删除残留以外的有效行）
     */
    boolean existsByTableName(String tableName);

    /**
     * 把 17 个生成开关恢复成「所属系统的默认值」（系统没配则按 1）。
     *
     * <p>只动开关，不动包名/路径/字段配置——用户点这个按钮的意图是
     * 「这批开关恢复默认」，不是「把我改过的所有东西都撤销」。
     *
     * @return 恢复后的开关值，key 与实体字段同名
     */
    java.util.Map<String, Integer> resetSwitchDefaults(Long tableId);
}

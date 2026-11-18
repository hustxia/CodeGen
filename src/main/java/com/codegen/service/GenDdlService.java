package com.codegen.service;

import com.codegen.dto.GenDdlExecuteDTO;
import com.codegen.vo.GenDdlExecuteVO;

/**
 * 建库建表一键导入：执行 DDL 并自动生成表配置。
 */
public interface GenDdlService {

    /**
     * 拆分 DDL 为语句列表（忽略注释、保留引号内的分号）
     */
    java.util.List<String> splitStatements(String ddl);

    /**
     * 执行 DDL，可选自动导入表配置
     */
    GenDdlExecuteVO execute(GenDdlExecuteDTO dto);

    /**
     * DDL 文本一键导入：不执行 DDL，直接解析建表语句注册生成配置，
     * 自动识别字典，并回读落库结果出「字典信息是否正确导入」的逐表校验报告。
     */
    java.util.List<com.codegen.vo.GenDdlImportVO> importFromDdl(com.codegen.dto.GenDdlImportDTO dto);
}

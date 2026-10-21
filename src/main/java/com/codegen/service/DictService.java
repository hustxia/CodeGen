package com.codegen.service;

import com.codegen.dto.DictDataSaveDTO;
import com.codegen.dto.DictTypeSaveDTO;
import com.codegen.vo.DictDataVO;
import com.codegen.vo.DictTypeVO;

import java.util.List;

/**
 * 字典维护：类型 + 字典项。
 *
 * <p>为什么不用 MyBatis-Plus 实体：`sys_dict_type` / `sys_dict_data` 是租户版列名
 * （created_at / updated_at），与 BaseEntity 的 create_time 不一致，
 * 走 JdbcTemplate 可以完全绕开这个映射问题（与 V1.1 的 DictController 一致）。
 */
public interface DictService {

    /**
     * 字典类型列表（含每个类型的字典项数量）
     */
    List<DictTypeVO> listTypes(String keyword);

    /**
     * 新增/修改字典类型，返回主键
     */
    Long saveType(DictTypeSaveDTO dto);

    /**
     * 删除字典类型（同时删除其字典项），返回删除的字典项数量
     */
    int deleteType(Long id);

    /**
     * 某类型的全部字典项（含停用）
     */
    List<DictDataVO> listData(String dictCode);

    /**
     * 新增/修改字典项
     */
    Long saveData(DictDataSaveDTO dto);

    /**
     * 删除字典项
     */
    void deleteData(Long id);

    /**
     * 孤儿字典：有字典项但类型表里没有对应记录
     */
    List<String> listOrphans();

    /**
     * 为孤儿字典补建类型（名称默认取 dict_code），返回补建数量
     */
    int repairOrphans();
}

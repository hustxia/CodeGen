package com.codegen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codegen.entity.GenTemplate;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 代码生成器-模板配置 Mapper
 */
@Mapper
public interface GenTemplateMapper extends BaseMapper<GenTemplate> {

    /**
     * 物理删除（绕过逻辑删除，避免残留行占用 uk_code_table 唯一键）
     */
    @Delete("DELETE FROM gen_template WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    /**
     * 物理清理同编码同表的逻辑删除残留行
     */
    @Delete("DELETE FROM gen_template WHERE tpl_code = #{tplCode} AND table_id = #{tableId} AND deleted = 1")
    int purgeDeletedResidual(@Param("tplCode") String tplCode, @Param("tableId") Long tableId);
}

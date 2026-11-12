package com.codegen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codegen.entity.SysI18nMessage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 译文表 Mapper。upsert 用原生 SQL：MyBatis-Plus 的 saveOrUpdate 在「唯一键冲突 +
 * 逻辑删除」组合下会把 NULL 值误写，这里直接交给数据库处理。
 */
@Mapper
public interface SysI18nMessageMapper extends BaseMapper<SysI18nMessage> {

    @Insert("""
            INSERT INTO sys_i18n_message
              (id, project_id, locale, msg_key, msg_value, create_time, update_time, deleted)
            VALUES
              (#{id}, #{projectId}, #{locale}, #{msgKey}, #{msgValue}, NOW(), NOW(), 0)
            ON DUPLICATE KEY UPDATE
              msg_value = VALUES(msg_value), update_time = NOW(), deleted = 0
            """)
    int upsert(SysI18nMessage entity);

    @Select("""
            SELECT project_id, locale, msg_key, msg_value
            FROM sys_i18n_message
            WHERE project_id = #{projectId} AND locale = #{locale} AND deleted = 0
            """)
    List<SysI18nMessage> selectByProjectLocale(@Param("projectId") Long projectId,
                                               @Param("locale") String locale);

    @Select("""
            SELECT COUNT(*) FROM sys_i18n_message
            WHERE project_id = #{projectId} AND locale = #{locale} AND deleted = 0
              AND msg_value IS NOT NULL AND msg_value <> ''
            """)
    int countTranslated(@Param("projectId") Long projectId,
                        @Param("locale") String locale);

    @Delete("DELETE FROM sys_i18n_message WHERE project_id = #{projectId}")
    int physicalDeleteByProject(Long projectId);
}

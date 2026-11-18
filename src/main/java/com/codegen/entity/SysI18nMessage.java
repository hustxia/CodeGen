package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * V8.0 用户译文覆盖表：按 (系统, 语言, 文案键) 唯一。
 *
 * <p>保存走 Mapper 的 {@code INSERT ... ON DUPLICATE KEY UPDATE}，不产生
 * {@code deleted=1} 行——逻辑删除与唯一键共存会让「删了再新增同键」静默失败
 * （坑库 mysql.md 同族）。清空译文 = 把 msg_value 写成 null，回落链接管。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_i18n_message")
public class SysI18nMessage extends BaseEntity {

    private Long projectId;

    private String locale;

    private String msgKey;

    private String msgValue;
}

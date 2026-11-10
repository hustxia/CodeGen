package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * V6.9 平台文档：上传的 Word/PDF 等文档元数据。
 *
 * <p>只存「怎么找到文件」，不存公开访问 URL——平台不开放免鉴权直链，
 * 所有读取都经 DocController 校验登录态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_doc")
public class SysDoc extends BaseEntity {

    /** 原始文件名（含扩展名，仅展示与下载命名用） */
    private String originalName;

    /** 落盘文件名（uuid.ext） */
    private String storageName;

    /** 相对 upload-dir 的路径（yyyyMM/uuid.ext） */
    private String relativePath;

    /** 小写扩展名（不带点，与白名单同口径） */
    private String ext;

    /** MIME 类型（取自上传请求，仅提示用，不参与安全判定） */
    private String contentType;

    /** 字节数 */
    private Long fileSize;

    /** MD5（预留去重/缓存键） */
    private String md5;

    private String remark;
}

package com.codegen.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档列表行（V6.9）。
 *
 * <p>id 用雪花号，超出 JS 安全整数范围，必须序列化成字符串，
 * 否则前端回传 id 会丢精度（坑库 spring-boot-3.md 坑 57）。
 */
@Data
public class DocVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String originalName;

    private String ext;

    private Long fileSize;

    private String contentType;

    /** 上传人展示名；用户已删除时为「—」 */
    private String uploaderName;

    private LocalDateTime createTime;

    /** pdf / browser-docx / unsupported */
    private String previewMode;
}

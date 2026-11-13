package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 测试中心-产物索引。文件本体只落磁盘，不入库 blob；读取时走 TcPathGuard 白名单。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tc_artifact")
public class TcArtifact extends BaseEntity {

    private Long runId;
    private String suiteId;

    /** LOG / REPORT / SCREENSHOT / SUREFIRE / DIFF */
    private String artifactType;

    private String fileName;
    private String filePath;
    private Long fileSize;
    private String mimeType;
}

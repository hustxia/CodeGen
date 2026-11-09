-- ============================================================
-- V6.9 平台文档中心：sys_doc 文档元数据表
-- 幂等：CREATE TABLE IF NOT EXISTS（同 V5.5/V6.0 的写法）
-- 主键雪花（BaseEntity ASSIGN_ID），故不设 AUTO_INCREMENT
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_doc` (
  `id` bigint NOT NULL COMMENT '文档ID',
  `original_name` varchar(255) NOT NULL COMMENT '原始文件名（含扩展名）',
  `storage_name` varchar(128) NOT NULL COMMENT '落盘文件名（uuid.ext）',
  `relative_path` varchar(255) NOT NULL COMMENT '相对 upload-dir 的路径（yyyyMM/uuid.ext）',
  `ext` varchar(20) NOT NULL COMMENT '小写扩展名（不带点，与白名单同口径）',
  `content_type` varchar(128) DEFAULT NULL COMMENT 'MIME类型',
  `file_size` bigint NOT NULL DEFAULT 0 COMMENT '字节数',
  `md5` char(32) DEFAULT NULL COMMENT 'MD5（预留去重/缓存键）',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_by` bigint DEFAULT NULL COMMENT '上传人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_relative_path` (`relative_path`),
  KEY `idx_ext` (`ext`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台文档（V6.9 文档中心）';

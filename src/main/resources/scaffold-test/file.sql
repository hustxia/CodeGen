-- V6.6 文件能力包：sys_file 元数据表（H2 测试版，与 db/file.sql 同源）
CREATE TABLE IF NOT EXISTS sys_file (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  original_name varchar(255) NOT NULL COMMENT '原始文件名',
  storage_name varchar(128) NOT NULL COMMENT '落盘文件名（uuid.ext）',
  relative_path varchar(255) NOT NULL COMMENT '相对 upload-dir 的路径',
  url varchar(512) NOT NULL COMMENT '访问URL（/uploads/...）',
  content_type varchar(128) DEFAULT NULL COMMENT 'MIME类型',
  file_size bigint NOT NULL DEFAULT 0 COMMENT '字节数',
  md5 char(32) DEFAULT NULL COMMENT 'MD5（预留去重）',
  biz_type varchar(64) DEFAULT NULL COMMENT '业务归类（预留）',
  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  create_by varchar(64) DEFAULT '' COMMENT '创建人',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by varchar(64) DEFAULT '' COMMENT '更新人',
  update_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  remark varchar(255) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  INDEX idx_relative_path (relative_path)
);

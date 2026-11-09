-- ${comment} 测试库建表（H2 MODE=MySQL；由代码生成器 V6.7 逐表落盘，重新生成覆盖）
-- 与正式 db/*.sql 同一列模型产出；转换规则：去 ENGINE/CHARSET/表注释/ON UPDATE
CREATE TABLE IF NOT EXISTS ${tableName} (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
<#list bizCols as c>
  ${c.dbField} ${c.columnType}<#if c.isRequired> NOT NULL</#if> DEFAULT NULL COMMENT '${c.columnComment}',
</#list>  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  create_by bigint NULL COMMENT '创建人',
  create_time datetime NULL COMMENT '创建时间',
  update_by bigint NULL COMMENT '更新人',
  update_time datetime NULL COMMENT '更新时间',
  PRIMARY KEY (id)
);

-- V6.8 字典脚手架：产物自带字典表与常用种子
-- 幂等：建表 IF NOT EXISTS；种子用 INSERT ... WHERE NOT EXISTS，重复执行不会重复插入。
-- 业务字典（如订单状态）由表级 dict_type 声明，本文件只放各项目通用的三类。

CREATE TABLE IF NOT EXISTS `sys_dict_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dict_code` varchar(64) NOT NULL COMMENT '字典编码',
  `dict_name` varchar(64) NOT NULL COMMENT '字典名称',
  `remark` varchar(255) NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint NULL COMMENT '创建人',
  `update_by` bigint NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_code` (`dict_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型';

CREATE TABLE IF NOT EXISTS `sys_dict_data` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dict_code` varchar(64) NOT NULL COMMENT '字典编码',
  `dict_value` varchar(64) NOT NULL COMMENT '字典值',
  `dict_label` varchar(64) NOT NULL COMMENT '字典标签',
  `sort` int DEFAULT 0 COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint NULL COMMENT '创建人',
  `update_by` bigint NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_dict_code` (`dict_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典数据';

INSERT INTO `sys_dict_type` (`dict_code`, `dict_name`, `remark`)
SELECT 'sys_status', '通用状态', '1=启用 0=停用' FROM (SELECT 1) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_code` = 'sys_status' AND `deleted` = 0);

INSERT INTO `sys_dict_type` (`dict_code`, `dict_name`, `remark`)
SELECT 'sys_yes_no', '是否', '1=是 0=否' FROM (SELECT 1) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_code` = 'sys_yes_no' AND `deleted` = 0);

INSERT INTO `sys_dict_type` (`dict_code`, `dict_name`, `remark`)
SELECT 'order_status', '订单状态', '0=待支付 1=待发货 2=已发货 3=已完成 4=已取消' FROM (SELECT 1) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_code` = 'order_status' AND `deleted` = 0);

INSERT INTO `sys_dict_data` (`dict_code`, `dict_value`, `dict_label`, `sort`)
SELECT 'sys_status', '1', '启用', 1 FROM (SELECT 1) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_code` = 'sys_status' AND `dict_value` = '1' AND `deleted` = 0);

INSERT INTO `sys_dict_data` (`dict_code`, `dict_value`, `dict_label`, `sort`)
SELECT 'sys_status', '0', '停用', 2 FROM (SELECT 1) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_code` = 'sys_status' AND `dict_value` = '0' AND `deleted` = 0);

INSERT INTO `sys_dict_data` (`dict_code`, `dict_value`, `dict_label`, `sort`)
SELECT 'sys_yes_no', '1', '是', 1 FROM (SELECT 1) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_code` = 'sys_yes_no' AND `dict_value` = '1' AND `deleted` = 0);

INSERT INTO `sys_dict_data` (`dict_code`, `dict_value`, `dict_label`, `sort`)
SELECT 'sys_yes_no', '0', '否', 2 FROM (SELECT 1) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_code` = 'sys_yes_no' AND `dict_value` = '0' AND `deleted` = 0);

INSERT INTO `sys_dict_data` (`dict_code`, `dict_value`, `dict_label`, `sort`)
SELECT 'order_status', '0', '待支付', 1 FROM (SELECT 1) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_code` = 'order_status' AND `dict_value` = '0' AND `deleted` = 0);

INSERT INTO `sys_dict_data` (`dict_code`, `dict_value`, `dict_label`, `sort`)
SELECT 'order_status', '1', '待发货', 2 FROM (SELECT 1) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_code` = 'order_status' AND `dict_value` = '1' AND `deleted` = 0);

INSERT INTO `sys_dict_data` (`dict_code`, `dict_value`, `dict_label`, `sort`)
SELECT 'order_status', '2', '已发货', 3 FROM (SELECT 1) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_code` = 'order_status' AND `dict_value` = '2' AND `deleted` = 0);

INSERT INTO `sys_dict_data` (`dict_code`, `dict_value`, `dict_label`, `sort`)
SELECT 'order_status', '3', '已完成', 4 FROM (SELECT 1) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_code` = 'order_status' AND `dict_value` = '3' AND `deleted` = 0);

INSERT INTO `sys_dict_data` (`dict_code`, `dict_value`, `dict_label`, `sort`)
SELECT 'order_status', '4', '已取消', 5 FROM (SELECT 1) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_code` = 'order_status' AND `dict_value` = '4' AND `deleted` = 0);

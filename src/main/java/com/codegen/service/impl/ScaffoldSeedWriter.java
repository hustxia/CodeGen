package com.codegen.service.impl;

import com.codegen.common.exception.BusinessException;
import com.codegen.common.log.Ip2RegionSearcher;
import com.codegen.config.GenOutputProperties;
import com.codegen.config.I18nCatalog;
import com.codegen.config.LanguageConfig;
import com.codegen.dto.GenScaffoldDTO;
import com.codegen.service.GenScaffoldService;
import com.codegen.service.I18nMessageService;
import com.codegen.service.I18nTextRewriter;
import com.codegen.support.GenNaming;
import com.codegen.support.GenPathResolver;
import com.codegen.vo.GenScaffoldCheckVO;
import com.codegen.vo.GenScaffoldVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import static com.codegen.service.impl.ScaffoldTextSupport.loadScaffoldResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * V9.1 拆分自 {@link GenScaffoldServiceImpl}：种子数据 SQL（sys_user/sys_role/sys_menu/sys_session 等）产物字符串工厂。
 * 纯函数集合，无实例状态；产物内容与拆分前逐字节一致。
 */
final class ScaffoldSeedWriter {

    static String seedSql() {
        // V6.4：admin 种子密码升级 BCrypt（预生成值，admin123）；旧 SHA-256 存量用户登录时无感迁移
        String adminBcrypt = "$2a$10$BQz.XdhOV7sJOpH9z/BeheARd01UQKdchBsEgPfa.rYgmwTh75PaO";
        return "-- 骨架自带登录基座与权限体系：admin / admin123\n"
                + "-- 由建系统流水线在建库后自动执行；手工部署时也可以手动跑本文件\n"
                + "-- V6.4：密码 BCrypt（password_algo 标记算法，旧 SHA-256 存量用户登录时无感迁移）\n"
                + "CREATE TABLE IF NOT EXISTS sys_user (\n"
                + "  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n"
                + "  username varchar(64) NOT NULL COMMENT '登录名',\n"
                + "  password varchar(72) NOT NULL COMMENT '密码（BCrypt 60位，兼容旧 SHA-256 64位）',\n"
                + "  password_algo varchar(16) NOT NULL DEFAULT 'bcrypt' COMMENT '密码算法：bcrypt/sha256',\n"
                + "  fail_count int NOT NULL DEFAULT 0 COMMENT '连续失败次数（V6.4 登录锁定）',\n"
                + "  lock_until datetime NULL COMMENT '锁定截止时间',\n"
                + "  nickname varchar(64) NULL COMMENT '昵称',\n"
                + "  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n"
                + "  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n"
                + "  create_by bigint NULL COMMENT '创建人',\n"
                + "  update_by bigint NULL COMMENT '更新人',\n"
                + "  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',\n"
                + "  PRIMARY KEY (id),\n"
                + "  UNIQUE KEY uk_username (username)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';\n\n"
                + "CREATE TABLE IF NOT EXISTS sys_session (\n"
                + "  token varchar(64) NOT NULL COMMENT '登录令牌',\n"
                + "  username varchar(64) NOT NULL COMMENT '登录名',\n"
                + "  login_ip varchar(64) NULL COMMENT '登录IP',\n"
                + "  login_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',\n"
                + "  expire_time datetime NOT NULL COMMENT '过期时间（滑动续期）',\n"
                + "  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',\n"
                + "  PRIMARY KEY (token),\n"
                + "  KEY idx_session_expire (expire_time)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录会话（V6.4 持久化，重启不掉线）';\n\n"
                + "CREATE TABLE IF NOT EXISTS sys_login_log (\n"
                + "  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n"
                + "  username varchar(64) NULL COMMENT '尝试登录的用户名',\n"
                + "  ip varchar(64) NULL COMMENT '来源IP',\n"
                + "  region varchar(128) NULL COMMENT 'IP归属地',\n"
                + "  success tinyint NOT NULL DEFAULT 0 COMMENT '是否成功：1是0否',\n"
                + "  fail_reason varchar(255) NULL COMMENT '失败原因',\n"
                + "  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '时间',\n"
                + "  PRIMARY KEY (id),\n"
                + "  KEY idx_login_log_time (create_time)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志（V6.4）';\n\n"
                // 坑48：操作日志表此前不在任何种子 SQL 里，新建库一调业务接口就 500
                + "CREATE TABLE IF NOT EXISTS sys_oper_log (\n"
                + "  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n"
                + "  title varchar(64) NULL COMMENT '模块标题',\n"
                + "  oper_type varchar(32) NULL COMMENT '操作类型',\n"
                + "  method varchar(128) NULL COMMENT '方法名',\n"
                + "  request_uri varchar(128) NULL COMMENT '请求URI',\n"
                + "  request_method varchar(16) NULL COMMENT '请求方式',\n"
                + "  oper_param text NULL COMMENT '请求参数',\n"
                + "  json_result text NULL COMMENT '返回结果',\n"
                + "  status tinyint NULL COMMENT '1成功 0失败',\n"
                + "  error_msg varchar(512) NULL COMMENT '错误消息',\n"
                + "  oper_user_id bigint NULL COMMENT '操作人ID',\n"
                + "  oper_name varchar(64) NULL COMMENT '操作人',\n"
                + "  oper_ip varchar(64) NULL COMMENT '来源IP',\n"
                + "  province varchar(32) NULL COMMENT '省份',\n"
                + "  city varchar(32) NULL COMMENT '城市',\n"
                + "  cost_ms bigint NULL COMMENT '耗时毫秒',\n"
                + "  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',\n"
                + "  create_by bigint NULL, create_time datetime NULL, update_by bigint NULL, update_time datetime NULL,\n"
                + "  PRIMARY KEY (id),\n"
                + "  KEY idx_oper_log_time (create_time)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志（V6.4 补齐种子）';\n\n"
                // V6.4 权限体系表基座：字段口径与 V3.0 菜单注册 SQL 一致（目标工程此前缺失）
                + "CREATE TABLE IF NOT EXISTS sys_role (\n"
                + "  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n"
                + "  role_name varchar(64) NOT NULL COMMENT '角色名',\n"
                + "  role_key varchar(64) NOT NULL COMMENT '角色标识（admin 直通）',\n"
                + "  remark varchar(255) NULL COMMENT '备注',\n"
                + "  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n"
                + "  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n"
                + "  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',\n"
                + "  PRIMARY KEY (id)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色';\n\n"
                + "CREATE TABLE IF NOT EXISTS sys_menu (\n"
                + "  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n"
                + "  parent_id bigint NOT NULL DEFAULT 0 COMMENT '父菜单ID',\n"
                + "  name varchar(64) NOT NULL COMMENT '名称',\n"
                + "  path varchar(128) NULL COMMENT '路由路径',\n"
                + "  component varchar(128) NULL COMMENT '组件路径',\n"
                + "  perms varchar(128) NULL COMMENT '权限标识（如 demo_fee:list）',\n"
                + "  type tinyint NOT NULL DEFAULT 1 COMMENT '类型：1目录2菜单3按钮',\n"
                + "  sort int NOT NULL DEFAULT 0 COMMENT '排序',\n"
                + "  status tinyint NOT NULL DEFAULT 1 COMMENT '状态：1启用0停用',\n"
                + "  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',\n"
                + "  PRIMARY KEY (id)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单与权限点';\n\n"
                + "CREATE TABLE IF NOT EXISTS sys_user_role (\n"
                + "  user_id bigint NOT NULL COMMENT '用户ID',\n"
                + "  role_id bigint NOT NULL COMMENT '角色ID',\n"
                + "  PRIMARY KEY (user_id, role_id)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';\n\n"
                + "CREATE TABLE IF NOT EXISTS sys_role_menu (\n"
                + "  role_id bigint NOT NULL COMMENT '角色ID',\n"
                + "  menu_id bigint NOT NULL COMMENT '菜单ID',\n"
                + "  PRIMARY KEY (role_id, menu_id)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单（perms 随菜单授予）';\n\n"
                + "INSERT IGNORE INTO sys_user (username, password, password_algo, nickname)\n"
                + "VALUES ('admin', '" + adminBcrypt + "', 'bcrypt', '管理员');\n\n"
                + "INSERT IGNORE INTO sys_role (id, role_name, role_key, remark)\n"
                + "VALUES (1, '超级管理员', 'admin', '内置角色：接口权限点直通');\n"
                + "INSERT IGNORE INTO sys_user_role (user_id, role_id)\n"
                + "SELECT u.id, 1 FROM sys_user u WHERE u.username = 'admin';\n";
    }
}

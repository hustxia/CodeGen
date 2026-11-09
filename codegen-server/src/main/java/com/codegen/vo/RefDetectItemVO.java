package com.codegen.vo;

/**
 * 外键推断结果：把「xxx_id 列」关联到同库某张表的主键，页面据此把 ID 显示成名称。
 *
 * @param tableName  当前表名
 * @param columnName 外键列名
 * @param refTable   推断出的关联表
 * @param refLabel   关联表用于显示名称的列
 * @param reason     命中依据（列名精确 / 同前缀 / 注释匹配），便于人工复核
 */
public record RefDetectItemVO(String tableName, String columnName, String refTable,
                              String refLabel, String reason) {
}

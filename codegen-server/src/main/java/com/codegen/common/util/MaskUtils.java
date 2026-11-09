package com.codegen.common.util;

/**
 * 敏感字段脱敏工具。
 *
 * <p>生成物 VO 的 getter 会调用这里，保证列表、详情、导出三条路径都脱敏，
 * 而不是只在前端表格里替换——前端脱敏在接口层面仍是明文。
 *
 * <p>设计约定：
 * <ul>
 *   <li>入参为 null 或空串原样返回，避免把 null 变成 "null"；</li>
 *   <li>长度不足时用 {@code *} 补齐可见长度，不做越界截取；</li>
 *   <li>未知类型原样返回，不吞数据。</li>
 * </ul>
 */
public final class MaskUtils {

    private MaskUtils() {
    }

    /**
     * 按类型脱敏
     *
     * @param value 原值
     * @param type  phone / idcard / email / name / bankcard
     */
    public static String mask(String value, String type) {
        if (value == null || value.isEmpty() || type == null) {
            return value;
        }
        switch (type.trim().toLowerCase()) {
            case "phone":
                return phone(value);
            case "idcard":
                return idcard(value);
            case "email":
                return email(value);
            case "name":
                return name(value);
            case "bankcard":
                return bankcard(value);
            default:
                return value;
        }
    }

    /**
     * 手机号：138****8000（保留前3后4）
     */
    public static String phone(String v) {
        if (v.length() < 7) {
            return repeat(v.length());
        }
        return v.substring(0, 3) + "****" + v.substring(v.length() - 4);
    }

    /**
     * 身份证：110***********1234（保留前3后4）
     */
    public static String idcard(String v) {
        if (v.length() < 7) {
            return repeat(v.length());
        }
        return v.substring(0, 3) + repeat(v.length() - 7) + v.substring(v.length() - 4);
    }

    /**
     * 邮箱：ab***@example.com（保留首2字符与完整域名）
     */
    public static String email(String v) {
        int at = v.indexOf('@');
        if (at <= 0) {
            return repeat(v.length());
        }
        String local = v.substring(0, at);
        String domain = v.substring(at);
        if (local.length() <= 2) {
            return local.charAt(0) + "***" + domain;
        }
        return local.substring(0, 2) + "***" + domain;
    }

    /**
     * 姓名：张*、欧阳**（保留姓，名用 * 代替）
     */
    public static String name(String v) {
        if (v.length() <= 1) {
            return v;
        }
        return v.charAt(0) + repeat(v.length() - 1);
    }

    /**
     * 银行卡：6222 **** **** 1234（保留前4后4）
     */
    public static String bankcard(String v) {
        String digits = v.replace(" ", "");
        if (digits.length() < 9) {
            return repeat(digits.length());
        }
        return digits.substring(0, 4) + " **** **** " + digits.substring(digits.length() - 4);
    }

    private static String repeat(int n) {
        if (n <= 0) {
            return "";
        }
        return "*".repeat(n);
    }
}

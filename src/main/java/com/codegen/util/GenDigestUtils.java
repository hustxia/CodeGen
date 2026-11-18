package com.codegen.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 内容指纹工具（V4.2）
 *
 * <p>写盘时算一次、漂移检测时再算一次，两处必须**同一份实现**——
 * 否则算法细节（编码、字节顺序）一有差异，漂移检测就会整批误报，
 * 而误报比不检测更糟：用户会学会忽略它。
 */
public final class GenDigestUtils {

    private GenDigestUtils() {
    }

    /**
     * 字符串按 UTF-8 取 MD5（与写盘编码一致）
     */
    public static String md5Hex(String content) {
        return md5Hex(content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8));
    }

    public static String md5Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(bytes);
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    /**
     * 磁盘文件取 MD5
     *
     * @return 文件不存在返回 null；读取失败抛 IOException 由调用方判定为 UNKNOWN
     */
    public static String md5Hex(Path file) throws IOException {
        if (file == null || !Files.exists(file) || !Files.isRegularFile(file)) {
            return null;
        }
        return md5Hex(Files.readAllBytes(file));
    }
}

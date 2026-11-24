package com.codegen.support;

import com.codegen.vo.GenAiTemplateVO;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * V9.19 模板包编解码（纯函数，无 Spring 依赖）。
 *
 * <p>导入只做「字节 -> 模板对象」的内存解析，不落盘；
 * 落盘路径由服务层基于 key 生成，且只接受 basename，避免路径穿越。
 *
 * <p>ZIP 解压不写磁盘，但对解压后的总字节数与条目数设上限（防 zip bomb）：
 * 超限直接返回错误，不把内存吃满。
 */
public final class TemplatePackCodec {

    /** ZIP 内 JSON 条目数上限 */
    public static final int MAX_ZIP_ENTRIES = 300;

    private TemplatePackCodec() {
    }

    /** 解析结果：成功解析的模板 + 逐条错误（两者可并存，例如部分文件损坏） */
    public record Parsed(List<GenAiTemplateVO> templates, List<String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    /** 只取文件名部分；包含 ".."、绝对路径或空名一律拒绝（返回空串） */
    public static String safeBasename(String rawFilename) {
        if (rawFilename == null) {
            return "";
        }
        String name = rawFilename.replace('\\', '/').trim();
        if (name.isEmpty() || name.indexOf("..") >= 0) {
            return "";
        }
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            return "";
        }
        return name;
    }

    public static boolean isZip(String filename) {
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    public static boolean isJson(String filename) {
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".json");
    }

    /** 按扩展名分派：.zip 解压逐个解析，.json 解析单个模板 */
    public static Parsed parse(byte[] data, String filename, long maxBytes, ObjectMapper mapper) {
        if (isZip(filename)) {
            return parseZip(data, maxBytes, mapper);
        }
        return parseJson(data, filename, mapper);
    }

    public static Parsed parseJson(byte[] data, String filename, ObjectMapper mapper) {
        List<String> errors = new ArrayList<>();
        if (data == null || data.length == 0) {
            errors.add("文件为空：" + filename);
            return new Parsed(List.of(), errors);
        }
        try {
            GenAiTemplateVO vo = mapper.readValue(data, GenAiTemplateVO.class);
            if (vo == null) {
                errors.add("文件内容不是有效的模板对象：" + filename);
                return new Parsed(List.of(), errors);
            }
            return new Parsed(new ArrayList<>(List.of(vo)), new ArrayList<>());
        } catch (Exception e) {
            errors.add("JSON 解析失败：" + filename + " - " + rootMessage(e));
            return new Parsed(List.of(), errors);
        }
    }

    public static Parsed parseZip(byte[] data, long maxBytes, ObjectMapper mapper) {
        List<GenAiTemplateVO> templates = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        if (data == null || data.length == 0) {
            errors.add("ZIP 文件为空");
            return new Parsed(templates, errors);
        }
        long total = 0;
        int jsonEntries = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String rawName = entry.getName();
                if (rawName == null || rawName.indexOf("..") >= 0) {
                    errors.add("ZIP 内文件路径非法（含 ..），已拒绝：" + rawName);
                    continue;
                }
                String basename = safeBasename(rawName);
                if (basename.isEmpty()) {
                    errors.add("ZIP 内文件名为空，已跳过");
                    continue;
                }
                if (!isJson(basename)) {
                    errors.add("ZIP 内非 JSON 文件已忽略：" + basename);
                    continue;
                }
                if (++jsonEntries > MAX_ZIP_ENTRIES) {
                    errors.add("ZIP 内 JSON 文件过多，超过上限 " + MAX_ZIP_ENTRIES + " 个");
                    break;
                }
                long remaining = maxBytes - total;
                byte[] entryBytes;
                try {
                    entryBytes = readLimited(zis, remaining);
                } catch (PackTooLargeException e) {
                    errors.add("ZIP 解压后总大小超过上限 " + maxBytes + " 字节");
                    break;
                }
                total += entryBytes.length;
                Parsed one = parseJson(entryBytes, basename, mapper);
                templates.addAll(one.templates());
                errors.addAll(one.errors());
            }
        } catch (Exception e) {
            errors.add("ZIP 解析失败：" + rootMessage(e));
        }
        return new Parsed(templates, errors);
    }

    /** 序列化单个模板为可下载 JSON（带缩进，方便用户手工编辑） */
    public static byte[] toJson(GenAiTemplateVO vo, ObjectMapper mapper) throws IOException {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(vo);
    }

    /** 打包成 ZIP：entry 名固定为 key.json（key 已通过校验，不含路径分隔符） */
    public static byte[] toZip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> e : new LinkedHashMap<>(files).entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                zos.putNextEntry(entry);
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static byte[] readLimited(InputStream in, long remaining) throws IOException, PackTooLargeException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (remaining <= 0 || total > remaining) {
                throw new PackTooLargeException();
            }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static String rootMessage(Exception e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        return msg == null || msg.isBlank() ? t.getClass().getSimpleName() : msg;
    }

    private static final class PackTooLargeException extends Exception {
        private PackTooLargeException() {
            super("pack too large");
        }
    }
}

package com.codegen.service;

import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.ResultCode;
import com.codegen.config.GenTemplatePackProperties;
import com.codegen.support.IndustryTemplateRegistry;
import com.codegen.support.TemplatePackCodec;
import com.codegen.support.TemplatePackValidator;
import com.codegen.vo.GenAiTemplateVO;
import com.codegen.vo.GenTemplatePackImportVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V9.19 模板市场：行业模板包的导出 / 导入 / 删除用户包。
 *
 * <p>导出只读注册表，不写盘；导入先全量校验，<b>全部通过才落盘</b>，
 * 避免 ZIP 中一半合法一半非法时出现半套模板。
 * 同名 key 默认拒绝，{@code overwrite=true} 才覆盖（预置/用户包口径一致）。
 */
@Slf4j
@Service
public class GenTemplatePackService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final IndustryTemplateRegistry registry;
    private final GenTemplatePackProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GenTemplatePackService(IndustryTemplateRegistry registry, GenTemplatePackProperties props) {
        this.registry = registry;
        this.props = props;
    }

    public List<GenAiTemplateVO> list() {
        return registry.list();
    }

    /** 导出单个模板（key 为空时返回参数错误） */
    public PackFile export(String key) {
        GenAiTemplateVO template = requireTemplate(key);
        try {
            byte[] body = TemplatePackCodec.toJson(template, objectMapper);
            String filename = "template-" + template.getKey() + "-" + LocalDate.now().format(DATE) + ".json";
            return new PackFile(filename, "application/json", body);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "模板序列化失败：" + e.getMessage());
        }
    }

    /** 多选打包：keys 为逗号分隔的模板 key，逐个序列化后压进一个 ZIP */
    public PackFile exportZip(String keysCsv) {
        Set<String> keys = new LinkedHashSet<>();
        if (keysCsv != null) {
            for (String raw : keysCsv.split(",")) {
                String k = raw == null ? "" : raw.trim();
                if (!k.isEmpty()) {
                    keys.add(k);
                }
            }
        }
        if (keys.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请指定要导出的模板 key（keys=a,b,c）");
        }
        Map<String, byte[]> files = new LinkedHashMap<>();
        try {
            for (String key : keys) {
                GenAiTemplateVO template = requireTemplate(key);
                files.put(template.getKey() + ".json", TemplatePackCodec.toJson(template, objectMapper));
            }
            byte[] body = TemplatePackCodec.toZip(files);
            String filename = "industry-templates-" + LocalDate.now().format(DATE) + ".zip";
            return new PackFile(filename, "application/zip", body);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "模板打包失败：" + e.getMessage());
        }
    }

    /**
     * 导入 .json 或 .zip：先解析、再逐模板校验，全部通过后写入 external-dir。
     * 任何一个模板出错都返回完整错误列表且不落盘。
     */
    public GenTemplatePackImportVO importPack(MultipartFile file, boolean overwrite) {
        List<String> errors = new ArrayList<>();
        if (file == null || file.isEmpty()) {
            return GenTemplatePackImportVO.fail(List.of("请选择要导入的 .json 或 .zip 模板包"));
        }
        String basename = TemplatePackCodec.safeBasename(file.getOriginalFilename());
        if (basename.isEmpty()) {
            return GenTemplatePackImportVO.fail(List.of("文件名非法：只允许上传文件名，禁止 ../ 等路径片段"));
        }
        boolean zip = TemplatePackCodec.isZip(basename);
        if (!zip && !TemplatePackCodec.isJson(basename)) {
            return GenTemplatePackImportVO.fail(List.of("仅支持 .json 或 .zip 模板包，当前文件：" + basename));
        }
        long maxBytes = props.maxPackBytes();
        if (maxBytes > 0 && file.getSize() > maxBytes) {
            return GenTemplatePackImportVO.fail(List.of(
                    "模板包过大：" + file.getSize() + " 字节，超过上限 " + maxBytes + " 字节（"
                            + props.getMaxPackSizeMb() + "MB）"));
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            return GenTemplatePackImportVO.fail(List.of("读取上传文件失败：" + e.getMessage()));
        }

        TemplatePackValidator.Limits limits = limits();
        TemplatePackCodec.Parsed parsed = TemplatePackCodec.parse(data, basename, maxBytes, objectMapper);
        errors.addAll(parsed.errors());
        if (parsed.templates().isEmpty()) {
            if (errors.isEmpty()) {
                errors.add("模板包中未解析到任何模板");
            }
            return GenTemplatePackImportVO.fail(errors);
        }

        Map<String, GenAiTemplateVO> toSave = new LinkedHashMap<>();
        int overwritten = 0;
        for (GenAiTemplateVO vo : parsed.templates()) {
            TemplatePackValidator.Result check = TemplatePackValidator.validate(vo, limits);
            if (!check.ok()) {
                errors.add("模板「" + describe(vo) + "」校验未通过：");
                errors.addAll(check.errors());
                continue;
            }
            String key = vo.getKey().trim();
            if (toSave.containsKey(key)) {
                errors.add("模板包内 key 重复：" + key);
                continue;
            }
            GenAiTemplateVO existing = registry.get(key);
            if (existing != null) {
                if (!overwrite) {
                    errors.add("模板 key 已存在：" + key + "（来源：" + sourceLabel(existing)
                            + "）。如需覆盖，请勾选「覆盖同名模板」后重新导入");
                    continue;
                }
                overwritten++;
            }
            toSave.put(key, vo);
        }
        if (!errors.isEmpty()) {
            return GenTemplatePackImportVO.fail(errors);
        }

        Path dir = registry.externalDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            return GenTemplatePackImportVO.fail(List.of("创建用户模板目录失败：" + dir + " - " + e.getMessage()));
        }
        List<Path> written = new ArrayList<>();
        try {
            for (Map.Entry<String, GenAiTemplateVO> entry : toSave.entrySet()) {
                Path target = safeTarget(dir, entry.getKey());
                Files.write(target, TemplatePackCodec.toJson(entry.getValue(), objectMapper),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                written.add(target);
            }
        } catch (Exception e) {
            // 不删除已写文件：可能是覆盖用户原包，删掉反而丢数据；错误如实返回由用户决定重试
            log.warn("模板包落盘失败：已写入 {} 个文件，错误 {}", written.size(), e.getMessage());
            return GenTemplatePackImportVO.fail(List.of("写入用户模板目录失败：" + e.getMessage()));
        }

        registry.reload();
        log.info("V9.19 模板包导入成功：目录 {}，导入 {} 套（覆盖 {} 套）",
                dir, toSave.size(), overwritten);
        return GenTemplatePackImportVO.ok(toSave.size(), overwritten, new ArrayList<>(toSave.keySet()));
    }

    /** 仅允许删除 external-dir 下的用户包；预置包不可删 */
    public void deleteUserPack(String key) {
        GenAiTemplateVO template = requireTemplate(key);
        if (!"user".equals(template.getSource())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(),
                    "预置模板不可删除：" + key + "（仅允许删除用户自定义包）");
        }
        Path file = registry.userFilePath(key);
        Path dir = registry.externalDir();
        if (file == null || !file.toAbsolutePath().normalize().startsWith(dir)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(),
                    "用户模板路径非法，已拒绝删除：" + key);
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "删除用户模板失败：" + e.getMessage());
        }
        registry.reload();
        log.info("V9.19 已删除用户模板包：{}（{}）", key, file);
    }

    private TemplatePackValidator.Limits limits() {
        return new TemplatePackValidator.Limits(
                props.getMaxTables(), props.getMaxKeyLength(), props.maxPackBytes());
    }

    private GenAiTemplateVO requireTemplate(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "模板 key 不能为空");
        }
        GenAiTemplateVO template = registry.get(key);
        if (template == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "行业模板不存在：" + key);
        }
        return template;
    }

    private Path safeTarget(Path dir, String key) {
        Path target = dir.resolve(key + ".json").toAbsolutePath().normalize();
        if (!target.startsWith(dir)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "模板 key 生成的路径非法：" + key);
        }
        return target;
    }

    private String describe(GenAiTemplateVO vo) {
        if (vo == null) {
            return "（空模板）";
        }
        if (vo.getKey() != null && !vo.getKey().isBlank()) {
            return vo.getKey().trim();
        }
        if (vo.getName() != null && !vo.getName().isBlank()) {
            return vo.getName().trim();
        }
        return "（未命名）";
    }

    private String sourceLabel(GenAiTemplateVO vo) {
        return "user".equals(vo.getSource()) ? "用户包" : "预置包";
    }

    /** 下载内容：文件名 + 媒体类型 + 字节体 */
    public record PackFile(String filename, String contentType, byte[] content) {
    }
}

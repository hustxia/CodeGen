package com.codegen.support;

import com.codegen.config.GenTemplatePackProperties;
import com.codegen.vo.GenAiTemplateVO;
import com.codegen.vo.GenPipelineAiVO;
import com.codegen.vo.GenPipelineAiVO.TableModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * V9.7 行业模板注册表：加载 {@code classpath:industry-templates/*.json} 预置模板包，
 * V9.19 起再合并 {@code gen.template.external-dir} 下的用户自定义包。
 *
 * <p>每个 JSON 一个行业包（电商/ERP/CRM/CMS），结构：
 * {@code {"key","name","desc","modules":[{"name","tables":[TableModel...]}]}}。
 * 表结构与 AI 建模产物同形（{@link TableModel}），克隆后走同一条收口/编辑/落库链路。
 *
 * <p>同名 key 时<b>用户包覆盖预置包</b>：用户需要改一套预置模板时，导出后改 key 之外的内容
 * 再导入即可生效，不需要改 jar 内的资产。来源通过 {@link GenAiTemplateVO#getSource()} 暴露。
 *
 * <p>加载失败的单个文件只告警不阻断启动；用户包结构不合法同样跳过并告警，
 * 不能让一个坏文件把整个模板库带崩。
 */
@Slf4j
@Component
public class IndustryTemplateRegistry {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, GenAiTemplateVO> templates = new LinkedHashMap<>();
    /** 用户包 key -> 实际文件（文件名允许与 key 不同，删除时按真实路径处理） */
    private final Map<String, Path> userFiles = new LinkedHashMap<>();

    private GenTemplatePackProperties packProperties;

    /** Spring 注入；纯单测直接 new 时保持默认配置（~/.codegen/industry-templates） */
    @Autowired(required = false)
    public void setPackProperties(GenTemplatePackProperties packProperties) {
        this.packProperties = packProperties;
    }

    private GenTemplatePackProperties props() {
        return packProperties == null ? new GenTemplatePackProperties() : packProperties;
    }

    @PostConstruct
    public synchronized void load() {
        doLoad();
    }

    /** 导入/删除用户包后重新扫描（预置包重新读一遍，成本可接受：250+ 个小文件） */
    public synchronized void reload() {
        doLoad();
    }

    private void doLoad() {
        templates.clear();
        userFiles.clear();
        int[] builtin = loadBuiltin();
        int user = loadExternal();
        int totalTables = templates.values().stream().mapToInt(GenAiTemplateVO::getTableCount).sum();
        log.info("V9.19 行业模板库已加载：预置 {} 套 / 用户包 {} 套，共 {} 套 {} 张表（分类 {} 个）",
                builtin[0], user, templates.size(), totalTables, categories().size());
    }

    private int[] loadBuiltin() {
        int loaded = 0;
        int tables = 0;
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources;
        try {
            resources = resolver.getResources("classpath:industry-templates/*.json");
        } catch (Exception e) {
            log.warn("行业模板目录扫描失败：{}", e.getMessage());
            return new int[]{0, 0};
        }
        Arrays.sort(resources, Comparator.comparing(Resource::getFilename));
        Set<String> seen = new HashSet<>();
        for (Resource r : resources) {
            try {
                GenAiTemplateVO vo = objectMapper.readValue(r.getInputStream(), GenAiTemplateVO.class);
                if (vo.getKey() == null || vo.getKey().isBlank()) {
                    log.warn("行业模板缺 key，跳过：{}", r.getFilename());
                    continue;
                }
                String key = vo.getKey().trim();
                if (!seen.add(key)) {
                    log.warn("预置行业模板 key 冲突（跳过后者）：{} - {}", r.getFilename(), key);
                    continue;
                }
                vo.setKey(key);
                // 历史 4 套（ecommerce/erp/crm/cms）无 category，归入通用分类
                if (vo.getCategory() == null || vo.getCategory().isBlank()) {
                    vo.setCategory("通用企业");
                }
                fillCounts(vo);
                vo.setSource("builtin");
                templates.put(key, vo);
                loaded++;
                tables += vo.getTableCount();
            } catch (Exception e) {
                log.warn("行业模板加载失败（跳过）：{} - {}", r.getFilename(), e.getMessage());
            }
        }
        return new int[]{loaded, tables};
    }

    /** 加载外部用户包；同名 key 覆盖预置包（后加载者生效），并记录来源为 user */
    private int loadExternal() {
        GenTemplatePackProperties props = props();
        Path dir = props.resolvedExternalDir();
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(p -> TemplatePackCodec.isJson(p.getFileName().toString()))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (Exception e) {
            log.warn("用户模板目录扫描失败（{}）：{}", dir, e.getMessage());
            return 0;
        }

        TemplatePackValidator.Limits limits = new TemplatePackValidator.Limits(
                props.getMaxTables(), props.getMaxKeyLength(), props.maxPackBytes());
        int loaded = 0;
        for (Path file : files) {
            String basename = TemplatePackCodec.safeBasename(file.getFileName().toString());
            if (basename.isEmpty()) {
                log.warn("用户模板文件名非法，跳过：{}", file);
                continue;
            }
            try {
                long size = Files.size(file);
                if (props.maxPackBytes() > 0 && size > props.maxPackBytes()) {
                    log.warn("用户模板文件超过大小上限，跳过：{}（{} 字节）", basename, size);
                    continue;
                }
                GenAiTemplateVO vo = objectMapper.readValue(Files.readAllBytes(file), GenAiTemplateVO.class);
                TemplatePackValidator.Result check = TemplatePackValidator.validate(vo, limits);
                if (!check.ok()) {
                    log.warn("用户模板结构不合法，跳过：{} - {}", basename, String.join("；", check.errors()));
                    continue;
                }
                String key = vo.getKey().trim();
                if (templates.containsKey(key)) {
                    log.warn("用户模板覆盖同名模板：key={}，来源将标记为 user", key);
                }
                normalize(vo);
                vo.setSource("user");
                templates.put(key, vo);
                userFiles.put(key, file);
                loaded++;
            } catch (Exception e) {
                log.warn("用户模板加载失败（跳过）：{} - {}", basename, e.getMessage());
            }
        }
        return loaded;
    }

    /**
     * 模板清单（列表接口：key/name/desc/category/source/模块数/表数，<b>不含</b>表结构明细）。
     *
     * <p>V9.9 模板扩到 250+ 套后，清单若带 modules 明细会有数 MB 的 payload；
     * 这里返回去掉 modules 的浅拷贝，明细走 {@link #get(String)} 按需取。
     */
    public List<GenAiTemplateVO> list() {
        List<GenAiTemplateVO> out = new ArrayList<>(templates.size());
        for (GenAiTemplateVO t : templates.values()) {
            GenAiTemplateVO light = new GenAiTemplateVO();
            light.setKey(t.getKey());
            light.setName(t.getName());
            light.setDesc(t.getDesc());
            light.setCategory(t.getCategory());
            light.setModuleCount(t.getModuleCount());
            light.setTableCount(t.getTableCount());
            light.setSource(t.getSource());
            out.add(light);
        }
        return out;
    }

    /** 全部行业分类（去重、按名称排序），供前端分组下拉 */
    public List<String> categories() {
        return templates.values().stream()
                .map(GenAiTemplateVO::getCategory)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    /** 取单个模板；不存在返回 null（服务层转 BusinessException） */
    public GenAiTemplateVO get(String key) {
        return key == null ? null : templates.get(key.trim().toLowerCase());
    }

    /** 用户包真实文件；预置包或不存在返回 null */
    public Path userFilePath(String key) {
        return key == null ? null : userFiles.get(key.trim().toLowerCase());
    }

    /** 用户包目录（不存在时返回配置路径，调用方自行创建） */
    public Path externalDir() {
        return props().resolvedExternalDir();
    }

    /**
     * 克隆模板表结构：按 {@code moduleNames} 过滤模块（空/ null = 全量），
     * 返回「扁平表清单 + 模块→表名映射」，形状与 AI 建模产物一致。
     */
    public CloneResult cloneTables(GenAiTemplateVO template, List<String> moduleNames) {
        List<TableModel> tables = new ArrayList<>();
        Map<String, List<String>> moduleTableNames = new LinkedHashMap<>();
        Set<String> wanted = moduleNames == null || moduleNames.isEmpty()
                ? null : new HashSet<>(moduleNames);
        for (GenAiTemplateVO.Module m : template.getModules()) {
            if (wanted != null && (m.getName() == null || !wanted.contains(m.getName()))) {
                continue;
            }
            List<String> names = new ArrayList<>();
            for (TableModel t : m.getTables()) {
                // 深拷贝：模板是单例常驻内存，编辑器改表不能污染模板资产
                tables.add(deepCopy(t));
                names.add(t.getTableName());
            }
            if (!names.isEmpty()) {
                moduleTableNames.put(m.getName(), names);
            }
        }
        return new CloneResult(tables, moduleTableNames);
    }

    private TableModel deepCopy(TableModel t) {
        TableModel copy = new TableModel();
        copy.setTableName(t.getTableName());
        copy.setTableComment(t.getTableComment());
        for (GenPipelineAiVO.Column c : t.getColumns()) {
            GenPipelineAiVO.Column cc = new GenPipelineAiVO.Column();
            cc.setName(c.getName());
            cc.setType(c.getType());
            cc.setComment(c.getComment());
            cc.setPk(c.isPk());
            copy.getColumns().add(cc);
        }
        return copy;
    }

    private void normalize(GenAiTemplateVO vo) {
        if (vo.getCategory() == null || vo.getCategory().isBlank()) {
            vo.setCategory("通用企业");
        }
        fillCounts(vo);
    }

    private void fillCounts(GenAiTemplateVO vo) {
        int tables = 0;
        for (GenAiTemplateVO.Module m : vo.getModules()) {
            tables += m.getTables() == null ? 0 : m.getTables().size();
        }
        vo.setModuleCount(vo.getModules().size());
        vo.setTableCount(tables);
    }

    /** 克隆结果：扁平表清单 + 模块映射（与 AI 建模产物同形） */
    public record CloneResult(List<TableModel> tables, Map<String, List<String>> moduleTableNames) {
    }
}

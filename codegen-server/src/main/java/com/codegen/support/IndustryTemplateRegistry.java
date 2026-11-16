package com.codegen.support;

import com.codegen.vo.GenAiTemplateVO;
import com.codegen.vo.GenPipelineAiVO;
import com.codegen.vo.GenPipelineAiVO.TableModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V9.7 行业模板注册表：加载 {@code classpath:industry-templates/*.json} 预置模板包。
 *
 * <p>每个 JSON 一个行业包（电商/ERP/CRM/CMS），结构：
 * {@code {"key","name","desc","modules":[{"name","tables":[TableModel...]}]}}。
 * 表结构与 AI 建模产物同形（{@link TableModel}），克隆后走同一条收口/编辑/落库链路。
 *
 * <p>加载失败的单个文件只告警不阻断启动；key 冲突启动即失败（资产冲突必须暴露）。
 */
@Slf4j
@Component
public class IndustryTemplateRegistry {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, GenAiTemplateVO> templates = new LinkedHashMap<>();

    @PostConstruct
    void load() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:industry-templates/*.json");
        Arrays.sort(resources, Comparator.comparing(Resource::getFilename));
        Set<String> seen = new HashSet<>();
        for (Resource r : resources) {
            try {
                GenAiTemplateVO vo = objectMapper.readValue(r.getInputStream(), GenAiTemplateVO.class);
                if (vo.getKey() == null || vo.getKey().isBlank()) {
                    log.warn("行业模板缺 key，跳过：{}", r.getFilename());
                    continue;
                }
                if (!seen.add(vo.getKey())) {
                    throw new IllegalStateException("行业模板 key 冲突：" + vo.getKey());
                }
                fillCounts(vo);
                templates.put(vo.getKey(), vo);
                log.info("V9.7 行业模板已加载：{}（{}）模块 {} 表 {} 张",
                        vo.getKey(), vo.getName(), vo.getModuleCount(), vo.getTableCount());
            } catch (Exception e) {
                log.warn("行业模板加载失败（跳过）：{} - {}", r.getFilename(), e.getMessage());
            }
        }
    }

    /** 模板清单（列表接口：key/name/desc/模块数/表数，不含表结构明细） */
    public List<GenAiTemplateVO> list() {
        return new ArrayList<>(templates.values());
    }

    /** 取单个模板；不存在抛业务侧可识别的空（服务层转 BusinessException） */
    public GenAiTemplateVO get(String key) {
        return key == null ? null : templates.get(key.trim().toLowerCase());
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

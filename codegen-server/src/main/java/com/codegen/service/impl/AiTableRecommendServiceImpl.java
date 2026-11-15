package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.ResultCode;
import com.codegen.config.GenPipelineProperties;
import com.codegen.entity.GenTable;
import com.codegen.mapper.GenTableMapper;
import com.codegen.dto.GenAiBuildConfirmDTO;
import com.codegen.dto.GenAiBuildPlanDTO;
import com.codegen.dto.GenPipelineCreateDTO;
import com.codegen.dto.GenPipelineDdlPreviewDTO;
import com.codegen.dto.GenPipelinePrepareDTO;
import com.codegen.service.AiModelService;
import com.codegen.service.AiTableRecommendService;
import com.codegen.service.GenPipelineService;
import com.codegen.vo.GenAiBuildConfirmVO;
import com.codegen.vo.GenAiBuildPlanVO;
import com.codegen.vo.GenPipelineAiVO;
import com.codegen.vo.GenPipelineDdlPreviewVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * V9.5 编排实现。仅复用 V9.4 的 {@link AiModelService} 与 {@link GenPipelineService}，
 * 不改动它们（AC-01）。核心新增：规模分批建模、关系视图、微服务拆分落库。
 */
@Slf4j
@Service
public class AiTableRecommendServiceImpl implements AiTableRecommendService {

    /** 与 V9.4 GenPipelineServiceImpl.DB_NAME 保持一致：小写字母开头的小写字母/数字/下划线，1~63 位 */
    private static final Pattern DB_NAME = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");

    /** 超大型同步建模时模块数上限：避免一次性上百次本地 LLM 调用把请求拖死；
     *  超出部分由前端分批或沿用现有流水线逐模块建模（见设计 §4.4） */
    private static final int MODULE_CAP = 40;

    @Autowired
    private AiModelService aiModelService;
    @Autowired
    private GenPipelineService pipelineService;
    @Autowired
    private GenPipelineProperties properties;
    /** 补表差集数据源：只读 gen_table 已有表名（@TableLogic 自动过滤 deleted=1） */
    @Autowired
    private GenTableMapper genTableMapper;

    @Override
    public GenAiBuildPlanVO plan(GenAiBuildPlanDTO dto) {
        if (!properties.getAi().isEnabled()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "AI 建模未启用，请在配置中开启 LLM 或在「一句话建系统」向导使用手动 DDL");
        }
        GenAiBuildPlanVO vo = new GenAiBuildPlanVO();
        vo.setScale(dto.getScale());
        vo.setDeploy(dto.getDeploy());

        // 补表：先读出目标系统已有表做差集基数（设计 §5.4「只推荐该系统缺失的表」）
        boolean supplement = isSupplement(dto.getScope());
        Set<String> existing = new LinkedHashSet<>();
        if (supplement) {
            if (dto.getProjectId() == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                        "补表必须选择目标系统（projectId），否则无法判断哪些表已存在");
            }
            existing.addAll(loadExistingTableNames(dto.getProjectId()));
            vo.setProjectId(dto.getProjectId());
            vo.setExistingTableCount(existing.size());
            log.info("V9.5 补表模式 projectId={} 已有表 {} 张", dto.getProjectId(), existing.size());
        }
        // 已有表清单同时写进 prompt：先让模型主动避让，再靠下面的服务端差集兜底
        String descForAi = withExistingHint(dto.getSystemDesc(), existing);

        List<GenPipelineAiVO.TableModel> tables = new ArrayList<>();
        Map<String, List<String>> moduleTableNames = new LinkedHashMap<>();

        boolean huge = "large".equals(dto.getScale()) || "huge".equals(dto.getScale());
        if (!huge) {
            // 精简：一次整体建模
            GenPipelineAiVO r = aiModelService.model(dto.getSystemName(), descForAi, null);
            if (r != null && r.getTables() != null) {
                tables.addAll(r.getTables());
            }
        } else {
            // 大型/超大型：先拆模块，再逐模块建模（复用 V9.4 aiModules + 带 moduleName 的 model）
            // 拆模块只看业务描述：把已有表清单塞进来会让它按「已建过的模块」重复拆分，
            // 已有表约束只下沉到逐模块建模的 prompt（见 descForAi）
            com.codegen.vo.GenPipelineAiModulesVO modulesVo =
                    aiModelService.modules(dto.getSystemName(), dto.getSystemDesc());
            if (modulesVo != null) {
                vo.setRelevanceWarning(modulesVo.isRelevanceWarning());
                vo.setRelevanceTip(modulesVo.getRelevanceTip());
                List<com.codegen.vo.GenPipelineAiModulesVO.Module> mods = modulesVo.getModules();
                int limit = Math.min(mods.size(), MODULE_CAP);
                if (mods.size() > MODULE_CAP) {
                    log.warn("V9.5 模块数 {} 超过同步上限 {}，仅建模前 {} 个；剩余建议分批或沿用流水线",
                            mods.size(), MODULE_CAP, MODULE_CAP);
                }
                for (int i = 0; i < limit; i++) {
                    com.codegen.vo.GenPipelineAiModulesVO.Module m = mods.get(i);
                    GenPipelineAiVO r = aiModelService.model(dto.getSystemName(), descForAi, m.getName());
                    if (r != null && r.getTables() != null && !r.getTables().isEmpty()) {
                        List<String> names = new ArrayList<>();
                        for (GenPipelineAiVO.TableModel t : r.getTables()) {
                            tables.add(t);
                            names.add(t.getTableName());
                        }
                        moduleTableNames.put(m.getName(), names);
                    }
                }
            }
        }

        // 服务端兜底：推荐结果内部去重 + 补表差集。
        // 只靠 prompt 约束模型避让已有表不可靠（实测模型仍会整批返回已有表），所以在这里硬过滤。
        List<String> excluded = new ArrayList<>();
        List<GenPipelineAiVO.TableModel> kept = filterTables(tables, existing, excluded);
        Map<String, List<String>> keptModules = pruneModules(moduleTableNames, namesOf(kept));

        vo.setTables(kept);
        vo.setModuleTableNames(keptModules);
        vo.setExcludedTables(excluded);
        vo.setExcludedCount(excluded.size());
        vo.setTableCount(kept.size());
        vo.setModuleCount(keptModules.size());
        if (!excluded.isEmpty()) {
            log.info("V9.5 已排除 {} 张重复表：{}", excluded.size(), excluded);
        }

        // 关系视图（规则推导，不依赖 AI 输出 relations，零侵入 V9.4）
        List<GenAiBuildPlanVO.TableRelation> relations = computeRelations(kept);
        vo.setRelations(relations);
        for (GenAiBuildPlanVO.TableRelation r : relations) {
            switch (r.getRole()) {
                case "master" -> vo.setMasterCount(vo.getMasterCount() + 1);
                case "detail" -> vo.setDetailCount(vo.getDetailCount() + 1);
                case "single" -> vo.setSingleCount(vo.getSingleCount() + 1);
                case "tree"   -> vo.setTreeCount(vo.getTreeCount() + 1);
                default -> { }
            }
        }
        return vo;
    }

    @Override
    public GenPipelineDdlPreviewVO ddlPreview(GenPipelineDdlPreviewDTO dto) {
        // 直接委托 V9.4，复用同一 buildDdl（AC-05 同源）
        return pipelineService.previewDdl(dto);
    }

    @Override
    public GenAiBuildConfirmVO confirm(GenAiBuildConfirmDTO dto) {
        if (!DB_NAME.matcher(dto.getDbName() == null ? "" : dto.getDbName().trim()).matches()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "数据库名只能用小写字母开头的小写字母/数字/下划线（1~63 位）");
        }
        boolean micro = "micro".equals(dto.getDeploy());
        boolean build = "build".equals(dto.getMode());

        // 补表：确认阶段再剔一次已有表（计划生成后库里可能又落了同表，编辑器里也可能被手工加回）
        Set<String> existing = new LinkedHashSet<>();
        if (isSupplement(dto.getScope())) {
            if (dto.getProjectId() == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                        "补表必须指定目标系统（projectId），否则无法跳过已有表");
            }
            existing.addAll(loadExistingTableNames(dto.getProjectId()));
        }
        List<String> skipped = new ArrayList<>();
        List<GenPipelineAiVO.TableModel> keptTables =
                filterTables(dto.getTables() == null ? List.of() : dto.getTables(), existing, skipped);
        if (keptTables.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "没有可落库的表：全部与目标系统已有表重复，请调整表设计后再确认");
        }
        Map<String, List<String>> modMap = pruneModules(dto.getModuleTableNames(), namesOf(keptTables));

        GenAiBuildConfirmVO result = new GenAiBuildConfirmVO();
        result.setMode(dto.getMode());
        result.setDeploy(dto.getDeploy());
        result.setProjectId(dto.getProjectId());
        result.setSkippedTables(skipped);
        result.setSkippedCount(skipped.size());
        result.setTableCount(keptTables.size());

        if (!micro) {
            // 单体：一张表集合 → 一个流水线任务
            GenPipelineCreateDTO createDto = toCreateDto(dto, dto.getSystemName(), dto.getDbName(), keptTables);
            GenPipelineVOHolder holder = doCreate(createDto);
            if (build) {
                doPrepare(holder.id, keptTables);
            }
            result.setPipelineId(holder.id);
            result.setServiceCount(1);
            return result;
        }

        // 微服务：按模块拆分，逐服务 create（+ 可选 prepareDb），每服务自有 schema
        if (modMap.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "微服务形态必须提供 moduleTableNames（模块→表名）以拆分服务");
        }
        int idx = 0;
        for (Map.Entry<String, List<String>> e : modMap.entrySet()) {
            String moduleName = e.getKey();
            List<GenPipelineAiVO.TableModel> modTables = filterByNames(keptTables, e.getValue());
            if (modTables.isEmpty()) {
                continue;
            }
            String dbNameM = sanitizeDbName(dto.getDbName(), moduleName, idx++);
            GenPipelineCreateDTO createDto =
                    toCreateDto(dto, dto.getSystemName() + " · " + moduleName, dbNameM, modTables);
            GenAiBuildConfirmVO.ServiceResult sr = new GenAiBuildConfirmVO.ServiceResult();
            sr.setModuleName(moduleName);
            sr.setDbName(dbNameM);
            try {
                GenPipelineVOHolder holder = doCreate(createDto);
                sr.setPipelineId(holder.id);
                if (build) {
                    doPrepare(holder.id, modTables);
                }
                sr.setStatus("ok");
            } catch (Exception ex) {
                log.error("V9.5 微服务建库失败 module={} db={}", moduleName, dbNameM, ex);
                sr.setStatus("failed");
                sr.setFailReason(ex.getMessage());
            }
            result.getServices().add(sr);
        }
        result.setServiceCount(result.getServices().size());
        return result;
    }

    // ---------------- 内部工具 ----------------

    private static boolean isSupplement(String scope) {
        return scope != null && "supplement".equalsIgnoreCase(scope.trim());
    }

    /** 目标系统已有表名（小写归一），只查 table_name 一列；逻辑删除由 @TableLogic 自动过滤 */
    private Set<String> loadExistingTableNames(Long projectId) {
        List<GenTable> rows = genTableMapper.selectList(new QueryWrapper<GenTable>()
                .select("table_name")
                .eq("project_id", projectId));
        Set<String> names = new LinkedHashSet<>();
        for (GenTable row : rows) {
            String n = normalize(row.getTableName());
            if (!n.isEmpty()) {
                names.add(n);
            }
        }
        return names;
    }

    /** 已有表清单追加进系统描述：先提示模型别重复，服务端再兜底过滤（模型不听话时也不放行） */
    private static String withExistingHint(String desc, Set<String> existing) {
        String base = desc == null ? "" : desc;
        if (existing == null || existing.isEmpty()) {
            return base;
        }
        return base + "\n\n【硬性要求】该系统已存在下列表，禁止重复推荐，只需补充缺失的业务表：\n"
                + String.join("、", existing)
                + "\n已有表需要扩展功能时，请新增关联表（如 xxx_extra / xxx_record），不要复用原表名。";
    }

    static String normalize(String tableName) {
        return tableName == null ? "" : tableName.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 去重 + 补表差集：同一次推荐里跨模块重名的表只留第一张，已有表直接剔除。
     * 排除明细写进 {@code excluded}（形如 {@code asset_device（系统中已存在）}），供前端提示。
     */
    static List<GenPipelineAiVO.TableModel> filterTables(List<GenPipelineAiVO.TableModel> in,
                                                         Set<String> existing, List<String> excluded) {
        List<GenPipelineAiVO.TableModel> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Set<String> excludedDedup = new LinkedHashSet<>();
        if (in != null) {
            for (GenPipelineAiVO.TableModel t : in) {
                String n = normalize(t.getTableName());
                if (n.isEmpty()) {
                    continue;
                }
                if (existing != null && existing.contains(n)) {
                    excludedDedup.add(n + "（系统中已存在）");
                    continue;
                }
                if (!seen.add(n)) {
                    excludedDedup.add(n + "（推荐结果内重名）");
                    continue;
                }
                out.add(t);
            }
        }
        if (excluded != null) {
            excluded.addAll(excludedDedup);
        }
        return out;
    }

    /** 模块 → 表名映射按保留表裁剪；整模块被剔空的直接丢弃，避免建出空服务工程 */
    static Map<String, List<String>> pruneModules(Map<String, List<String>> mods, Set<String> kept) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (mods == null || mods.isEmpty()) {
            return out;
        }
        for (Map.Entry<String, List<String>> e : mods.entrySet()) {
            List<String> names = new ArrayList<>();
            if (e.getValue() != null) {
                for (String n : e.getValue()) {
                    if (kept.contains(normalize(n))) {
                        names.add(n);
                    }
                }
            }
            if (!names.isEmpty()) {
                out.put(e.getKey(), names);
            }
        }
        return out;
    }

    static Set<String> namesOf(List<GenPipelineAiVO.TableModel> tables) {
        Set<String> set = new LinkedHashSet<>();
        if (tables != null) {
            for (GenPipelineAiVO.TableModel t : tables) {
                String n = normalize(t.getTableName());
                if (!n.isEmpty()) {
                    set.add(n);
                }
            }
        }
        return set;
    }

    private GenPipelineVOHolder doCreate(GenPipelineCreateDTO createDto) {
        com.codegen.vo.GenPipelineVO vo = pipelineService.create(createDto);
        return new GenPipelineVOHolder(vo.getId());
    }

    private void doPrepare(Long id, List<GenPipelineAiVO.TableModel> tables) {
        pipelineService.prepareDb(id, new GenPipelinePrepareDTO() {{
            setTables(tables);
        }});
    }

    private GenPipelineCreateDTO toCreateDto(GenAiBuildConfirmDTO dto, String sysName, String dbName,
                                            List<GenPipelineAiVO.TableModel> tables) {
        GenPipelineCreateDTO c = new GenPipelineCreateDTO();
        c.setSystemName(sysName);
        c.setSystemDesc(dto.getSystemDesc());
        c.setDbName(dbName);
        c.setMode("ai");
        c.setSeedMode("off"); // 建库阶段不自动 seed，run 时再决定
        c.setTables(tables);
        return c;
    }

    private List<GenPipelineAiVO.TableModel> filterByNames(List<GenPipelineAiVO.TableModel> all,
                                                          List<String> names) {
        Map<String, GenPipelineAiVO.TableModel> byName = new LinkedHashMap<>();
        for (GenPipelineAiVO.TableModel t : all) {
            byName.put(t.getTableName(), t);
        }
        List<GenPipelineAiVO.TableModel> out = new ArrayList<>();
        for (String n : names) {
            GenPipelineAiVO.TableModel t = byName.get(n);
            if (t != null) {
                out.add(t);
            }
        }
        return out;
    }

    /** 微服务每服务 schema：base + "_" + 模块 slug（中文模块名转 ascii，失败回退 m{idx}），保证合法且唯一 */
    private String sanitizeDbName(String base, String moduleName, int idx) {
        StringBuilder sb = new StringBuilder();
        for (char ch : moduleName.toCharArray()) {
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')) {
                sb.append(Character.toLowerCase(ch));
            }
        }
        String slug = sb.length() > 0 ? sb.toString() : ("m" + idx);
        String candidate = (base + "_" + slug);
        // 截断到 63，去尾下划线
        if (candidate.length() > 63) {
            candidate = candidate.substring(0, 63);
        }
        while (candidate.endsWith("_")) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        if (!DB_NAME.matcher(candidate).matches()) {
            candidate = (base + "_m" + idx);
        }
        return candidate;
    }

    /** 规则推导关系视图：扫描 *_id 外键 + 自关联 parent_id */
    private List<GenAiBuildPlanVO.TableRelation> computeRelations(List<GenPipelineAiVO.TableModel> tables) {
        Map<String, GenPipelineAiVO.TableModel> byName = new LinkedHashMap<>();
        for (GenPipelineAiVO.TableModel t : tables) {
            byName.put(t.getTableName(), t);
        }
        Map<String, String> masterOf = new LinkedHashMap<>();   // 表 → 其指向的主表（detail 用）
        Map<String, List<String>> detailsOf = new LinkedHashMap<>(); // 主表 → 从表清单
        Map<String, Boolean> selfRef = new LinkedHashMap<>();   // 表 → 是否自关联

        for (GenPipelineAiVO.TableModel t : tables) {
            List<GenPipelineAiVO.Column> cols = t.getColumns() == null ? List.of() : t.getColumns();
            for (GenPipelineAiVO.Column c : cols) {
                String col = c.getName();
                if (col == null || col.isBlank()) {
                    continue;
                }
                // V9.5 §4.8：自关联列（parent_id / pid，忽略大小写）→ 树形，不参与跨表主从推导。
                // 早期实现只靠 findMasterTable("parent", …) 找主表，业务库里几乎不存在名为 parent 的表，
                // 悬空外键被 continue 后 tree 角色永远判不出来；此处按列名语义直接钉住自关联。
                if (col.equalsIgnoreCase("parent_id") || col.equalsIgnoreCase("pid")) {
                    selfRef.put(t.getTableName(), true);
                    continue;
                }
                if (col.endsWith("_id")) {
                    String base = col.substring(0, col.length() - 3);
                    GenPipelineAiVO.TableModel master = findMasterTable(base, byName, t.getTableName());
                    if (master == null) {
                        continue; // 悬空外键，关系视图不计
                    }
                    if (master.getTableName().equals(t.getTableName())) {
                        selfRef.put(t.getTableName(), true); // 自关联 → 树形
                    } else {
                        masterOf.put(t.getTableName(), master.getTableName());
                        detailsOf.computeIfAbsent(master.getTableName(), k -> new ArrayList<>()).add(t.getTableName());
                    }
                }
            }
        }

        List<GenAiBuildPlanVO.TableRelation> out = new ArrayList<>();
        for (GenPipelineAiVO.TableModel t : tables) {
            GenAiBuildPlanVO.TableRelation r = new GenAiBuildPlanVO.TableRelation();
            r.setName(t.getTableName());
            if (Boolean.TRUE.equals(selfRef.get(t.getTableName()))) {
                r.setRole("tree");
            } else if (detailsOf.containsKey(t.getTableName())) {
                r.setRole("master");
                r.setDetails(detailsOf.get(t.getTableName()));
            } else if (masterOf.containsKey(t.getTableName())) {
                r.setRole("detail");
                r.setMaster(masterOf.get(t.getTableName()));
            } else {
                r.setRole("single");
            }
            out.add(r);
        }
        out.sort(Comparator.comparing(GenAiBuildPlanVO.TableRelation::getName));
        return out;
    }

    private GenPipelineAiVO.TableModel findMasterTable(String base, Map<String, GenPipelineAiVO.TableModel> byName,
                                                      String selfTable) {
        // 1) 精确 2) 以 _base 结尾 3) 以 base_ 开头 4) bus_base
        if (byName.containsKey(base)) {
            return byName.get(base);
        }
        for (String name : byName.keySet()) {
            if (name.endsWith("_" + base) || name.startsWith(base + "_")) {
                return byName.get(name);
            }
        }
        String bus = "bus_" + base;
        if (byName.containsKey(bus)) {
            return byName.get(bus);
        }
        return null;
    }

    private static class GenPipelineVOHolder {
        final Long id;
        GenPipelineVOHolder(Long id) { this.id = id; }
    }
}

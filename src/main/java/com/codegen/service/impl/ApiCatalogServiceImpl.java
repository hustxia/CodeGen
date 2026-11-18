package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codegen.entity.GenTable;
import com.codegen.mapper.GenTableMapper;
import com.codegen.service.ApiCatalogService;
import com.codegen.util.GenUtils;
import com.codegen.vo.ApiCatalogItemVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 接口目录实现。
 *
 * <p>刻意按「生成模板里真实会产出的接口」逐条对齐，避免文档与代码两张皮：
 * <ul>
 *   <li>page / export / import / import-template / import-failed / detail / add / edit / delete
 *       各自受对应 gen*Api 开关控制</li>
 *   <li>树形表额外有 /tree，主子表额外有 /with-children</li>
 *   <li>有关联下拉时额外有 /options，有唯一校验时额外有 /check-unique</li>
 * </ul>
 */
@Service
public class ApiCatalogServiceImpl implements ApiCatalogService {

    @Autowired
    private GenTableMapper genTableMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public List<ApiCatalogItemVO> catalog(Long tableId, String keyword) {
        LambdaQueryWrapper<GenTable> w = new LambdaQueryWrapper<>();
        if (tableId != null) {
            w.eq(GenTable::getId, tableId);
        }
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            w.and(q -> q.like(GenTable::getTableName, k)
                    .or().like(GenTable::getTableComment, k)
                    .or().like(GenTable::getClassName, k));
        }
        w.orderByAsc(GenTable::getTableName);
        List<GenTable> tables = genTableMapper.selectList(w);

        List<ApiCatalogItemVO> result = new ArrayList<>();
        for (GenTable t : tables) {
            result.add(build(t));
        }
        return result;
    }

    private ApiCatalogItemVO build(GenTable t) {
        ApiCatalogItemVO vo = new ApiCatalogItemVO();
        vo.setTableId(t.getId());
        vo.setTableName(t.getTableName());
        vo.setTableComment(orDefault(t.getTableComment(), t.getTableName()));
        vo.setClassName(t.getClassName());
        vo.setModuleName(orDefault(t.getModuleName(), GenUtils.inferModuleName(t.getTableName())));

        String module = vo.getModuleName();
        // 与 GenTemplateModel 的 apiBase 保持同一算法：/api/{网关前缀}/{模块}
        String prefix = StringUtils.hasText(t.getGatewayPrefix()) ? t.getGatewayPrefix() : "admin";
        vo.setBasePath("/api/" + prefix + "/" + module);

        Map<String, Object> flags = loadFlags(t.getId());
        boolean genController = on(t.getGenController());
        boolean genService = on(t.getGenService());
        // 控制器都不生成，谈接口没有意义
        if (!genController || !genService) {
            vo.setGenerated(false);
            return vo;
        }

        boolean genTree = str(t.getTreeParentColumn()) != null;
        boolean hasChild = t.getMasterTableId() != null;
        boolean hasRef = bool(flags.get("has_ref"));
        boolean hasUnique = bool(flags.get("has_unique"));
        boolean hasImport = bool(flags.get("has_import"));
        boolean hasExport = bool(flags.get("has_export"));
        boolean hasQuery = bool(flags.get("has_query"));

        String base = vo.getBasePath();
        String tbl = t.getTableName();
        String cn = vo.getTableComment();
        List<ApiCatalogItemVO.Api> apis = vo.getApis();

        if (on(t.getGenPageApi())) {
            apis.add(new ApiCatalogItemVO.Api("POST", base + "/page", cn + "分页列表",
                    tbl + (hasQuery ? ":query" : ":list"), "get" + t.getClassName() + "List"));
        }
        if (genTree) {
            apis.add(new ApiCatalogItemVO.Api("GET", base + "/tree", cn + "树形列表",
                    tbl + ":list", "get" + t.getClassName() + "Tree"));
        }
        if (on(t.getGenDetailApi())) {
            apis.add(new ApiCatalogItemVO.Api("GET", base + "/{id}", cn + "详情",
                    tbl + ":query", "get" + t.getClassName() + "Detail"));
        }
        if (on(t.getGenAddApi())) {
            apis.add(new ApiCatalogItemVO.Api("POST", base, "新增" + cn,
                    tbl + ":add", "create" + t.getClassName()));
        }
        if (on(t.getGenEditApi())) {
            apis.add(new ApiCatalogItemVO.Api("PUT", base + "/{id}", "修改" + cn,
                    tbl + ":edit", "update" + t.getClassName()));
        }
        if (on(t.getGenDeleteApi())) {
            apis.add(new ApiCatalogItemVO.Api("DELETE", base + "/{id}", "删除" + cn,
                    tbl + ":remove", "delete" + t.getClassName()));
        }
        if (hasChild) {
            apis.add(new ApiCatalogItemVO.Api("GET", base + "/with-children/{id}", cn + "含子表详情",
                    tbl + ":query", "get" + t.getClassName() + "WithChildren"));
            apis.add(new ApiCatalogItemVO.Api("POST", base + "/with-children", "主子表同事务保存",
                    tbl + ":add", "save" + t.getClassName() + "WithChildren"));
        }
        if (hasRef) {
            apis.add(new ApiCatalogItemVO.Api("GET", base + "/options", "关联下拉选项（按 refTable 远程搜索）",
                    tbl + ":query", "get" + t.getClassName() + "Options"));
        }
        if (hasUnique) {
            apis.add(new ApiCatalogItemVO.Api("GET", base + "/check-unique", "唯一性校验（前端失焦查重）",
                    null, "check" + t.getClassName() + "Unique"));
        }
        if (hasExport) {
            apis.add(new ApiCatalogItemVO.Api("POST", base + "/export", "按条件导出 Excel",
                    tbl + ":query", "export" + t.getClassName()));
        }
        if (hasImport) {
            apis.add(new ApiCatalogItemVO.Api("GET", base + "/import-template", "下载导入模板",
                    tbl + ":add", "download" + t.getClassName() + "Template"));
            apis.add(new ApiCatalogItemVO.Api("POST", base + "/import", "批量导入（逐行独立事务）",
                    tbl + ":add", "import" + t.getClassName()));
        }
        if (hasImport) {
            apis.add(new ApiCatalogItemVO.Api("GET", base + "/import/failed", "下载导入失败明细",
                    tbl + ":add", null));
        }

        vo.setGenerated(isGenerated(t.getId()));
        return vo;
    }

    /**
     * 一次查出该表影响接口清单的派生标记，避免每张表打好几次库
     */
    private Map<String, Object> loadFlags(Long tableId) {
        Map<String, Object> flags = new LinkedHashMap<>();
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT "
                        + " SUM(CASE WHEN ref_table IS NOT NULL AND ref_table <> '' THEN 1 ELSE 0 END) AS ref_cnt, "
                        + " SUM(CASE WHEN unique_flag = 1 THEN 1 ELSE 0 END) AS uniq_cnt, "
                        + " SUM(CASE WHEN excel_import = 1 THEN 1 ELSE 0 END) AS imp_cnt, "
                        + " SUM(CASE WHEN excel_export = 1 THEN 1 ELSE 0 END) AS exp_cnt, "
                        + " SUM(CASE WHEN is_query = 1 THEN 1 ELSE 0 END) AS qry_cnt "
                        + "FROM gen_table_column WHERE table_id = ? AND deleted = 0",
                tableId);
        flags.put("has_ref", num(row.get("ref_cnt")) > 0);
        flags.put("has_unique", num(row.get("uniq_cnt")) > 0);
        flags.put("has_import", num(row.get("imp_cnt")) > 0);
        flags.put("has_export", num(row.get("exp_cnt")) > 0);
        flags.put("has_query", num(row.get("qry_cnt")) > 0);
        return flags;
    }

    /**
     * 生成过代码的表会留下 gen_output 记录；没有就说明这还只是配置
     */
    private boolean isGenerated(Long tableId) {
        try {
            Integer c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM gen_output WHERE deleted = 0 AND table_id = ?", Integer.class, tableId);
            return c != null && c > 0;
        } catch (Exception e) {
            // gen_output 不存在时不影响目录展示
            return false;
        }
    }

    private static int num(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    private static boolean bool(Object o) {
        return o instanceof Boolean b && b;
    }

    private static boolean on(Integer flag) {
        return flag != null && flag == 1;
    }

    private static String str(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static String orDefault(String v, String d) {
        return StringUtils.hasText(v) ? v : d;
    }
}

package com.codegen.controller;

import com.codegen.dto.DictDataSaveDTO;
import com.codegen.dto.DictTypeSaveDTO;
import com.codegen.service.DictService;
import com.codegen.vo.DictDataVO;
import com.codegen.vo.DictItemVO;
import com.codegen.vo.DictTypeVO;
import com.codegen.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字典：运行时查询（生成页面用）+ 管理维护（字典管理页用）。
 *
 * <p>运行时接口 {@code GET /dict/{code}} 只返回启用项，是生成页下拉的数据源；
 * 管理接口统一放在两段路径下（{@code /dict/type/**}、{@code /dict/data/**}），
 * 与单段的 {@code /dict/{code}} 不冲突。
 */
@Tag(name = "字典", description = "字典查询与维护")
@RestController
@RequestMapping("/dict")
public class DictController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DictService dictService;

    // ==================== 运行时：生成页下拉 ====================

    @Operation(summary = "按字典编码查询字典项（仅启用项）")
    @GetMapping("/{code}")
    public Result<List<DictItemVO>> list(@PathVariable("code") String code) {
        if (code == null || !code.matches("[A-Za-z0-9_]{1,64}")) {
            return Result.ok(List.of());
        }
        String sql = "SELECT dict_label, dict_value FROM sys_dict_data "
                + "WHERE dict_code = ? AND status = 1 ORDER BY sort_no, id";
        List<DictItemVO> items = jdbcTemplate.query(sql,
                (rs, i) -> new DictItemVO(rs.getString("dict_label"), rs.getString("dict_value")), code);
        return Result.ok(items);
    }

    // ==================== 管理：字典类型 ====================

    @Operation(summary = "字典类型列表（含字典项数量）")
    @GetMapping("/type/list")
    public Result<List<DictTypeVO>> listTypes(@RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(dictService.listTypes(keyword));
    }

    @Operation(summary = "保存字典类型（新增/修改）")
    @PostMapping("/type/save")
    public Result<Long> saveType(@RequestBody DictTypeSaveDTO dto) {
        return Result.ok(dictService.saveType(dto));
    }

    @Operation(summary = "删除字典类型（连同其字典项）")
    @PostMapping("/type/delete/{id}")
    public Result<Integer> deleteType(@PathVariable("id") Long id) {
        return Result.ok(dictService.deleteType(id));
    }

    // ==================== 管理：字典项 ====================

    @Operation(summary = "某类型的字典项列表（含停用项）")
    @GetMapping("/data/list")
    public Result<List<DictDataVO>> listData(@RequestParam("code") String code) {
        return Result.ok(dictService.listData(code));
    }

    @Operation(summary = "保存字典项（新增/修改）")
    @PostMapping("/data/save")
    public Result<Long> saveData(@RequestBody DictDataSaveDTO dto) {
        return Result.ok(dictService.saveData(dto));
    }

    @Operation(summary = "删除字典项")
    @PostMapping("/data/delete/{id}")
    public Result<Void> deleteData(@PathVariable("id") Long id) {
        dictService.deleteData(id);
        return Result.ok();
    }

    // ==================== 管理：健康检查 ====================

    @Operation(summary = "孤儿字典（有字典项但无类型记录）")
    @GetMapping("/orphans")
    public Result<List<String>> orphans() {
        return Result.ok(dictService.listOrphans());
    }

    @Operation(summary = "为孤儿字典补建类型记录")
    @PostMapping("/orphans/repair")
    public Result<Integer> repairOrphans() {
        return Result.ok(dictService.repairOrphans());
    }
}

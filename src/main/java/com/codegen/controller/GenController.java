package com.codegen.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.codegen.config.GenOutputProperties;
import com.codegen.dto.GenApplyDTO;
import com.codegen.dto.GenDbImportDTO;
import com.codegen.dto.GenDdlDTO;
import com.codegen.dto.GenDdlExecuteDTO;
import com.codegen.dto.GenDiffDTO;
import com.codegen.dto.GenDictApplyDTO;
import com.codegen.dto.RelationApplyDTO;
import com.codegen.dto.GenDownloadDTO;
import com.codegen.dto.GenMenuRegisterDTO;
import com.codegen.dto.GenOutputPageDTO;
import com.codegen.dto.GenScaffoldDTO;
import com.codegen.dto.GenTableBatchDTO;
import com.codegen.dto.GenTablePageDTO;
import com.codegen.dto.GenTableSaveDTO;
import com.codegen.dto.GenWriteDTO;
import com.codegen.entity.GenOutput;
import com.codegen.service.ApiCatalogService;
import com.codegen.service.RelationDetectService;
import com.codegen.service.DictDetectService;
import com.codegen.service.GenApplyService;
import com.codegen.service.GenCodeService;
import com.codegen.service.GenDdlService;
import com.codegen.service.GenMenuService;
import com.codegen.service.GenOutputService;
import com.codegen.service.GenScaffoldService;
import com.codegen.service.GenSelfCheckService;
import com.codegen.service.GenTableService;
import com.codegen.service.GenVerifyService;
import com.codegen.util.GenUtils;
import com.codegen.vo.GenApplyVO;
import com.codegen.vo.GenColumnSyncVO;
import com.codegen.vo.GenDbTableVO;
import com.codegen.vo.DictDetectItemVO;
import com.codegen.vo.ApiCatalogItemVO;
import com.codegen.vo.RelationDetectVO;
import com.codegen.vo.GenDdlExecuteVO;
import com.codegen.vo.GenDiffVO;
import com.codegen.vo.GenFileVO;
import com.codegen.vo.GenImportVO;
import com.codegen.vo.GenMenuRegisterVO;
import com.codegen.vo.GenOutputBatchVO;
import com.codegen.vo.GenScaffoldCheckVO;
import com.codegen.vo.GenScaffoldVO;
import com.codegen.vo.GenSelfCheckVO;
import com.codegen.vo.GenTableVO;
import com.codegen.vo.GenVerifyVO;
import com.codegen.vo.GenWriteVO;
import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 代码生成器 Controller
 */
@Tag(name = "代码生成", description = "代码生成器：DDL 导入/表配置/代码生成")
@RestController
@RequestMapping("/gen")
public class GenController {

    @Autowired
    private GenTableService genTableService;

    @Autowired
    private GenCodeService genCodeService;

    @Autowired
    private GenSelfCheckService genSelfCheckService;

    @Autowired
    private GenMenuService genMenuService;

    @Autowired
    private GenApplyService genApplyService;

    @Autowired
    private GenOutputService genOutputService;

    @Autowired
    private GenVerifyService genVerifyService;

    @Autowired
    private GenScaffoldService genScaffoldService;

    @Autowired
    private GenDdlService genDdlService;

    @Autowired
    private DictDetectService dictDetectService;

    @Autowired
    private ApiCatalogService apiCatalogService;

    @Autowired
    private RelationDetectService relationDetectService;

    @Autowired
    private GenOutputProperties outputProperties;

    @Operation(summary = "P3-2 多对多中间表识别（只读）")
    @GetMapping("/relation/detect")
    public Result<List<RelationDetectVO>> detectRelation(@RequestParam(required = false) String schema) {
        return Result.ok(relationDetectService.detect(schema));
    }

    @Operation(summary = "P3-2 标记中间表与两侧外键")
    @PostMapping("/relation/apply")
    public Result<Integer> applyRelation(@RequestBody RelationApplyDTO dto) {
        return Result.ok(relationDetectService.apply(dto.getSchema(), dto.getMiddleTables()));
    }

    @Operation(summary = "P2-5 接口目录：按表聚合全部生成接口")
    @GetMapping("/api-catalog")
    public Result<List<ApiCatalogItemVO>> apiCatalog(
            @RequestParam(required = false) Long tableId,
            @RequestParam(required = false) String keyword) {
        return Result.ok(apiCatalogService.catalog(tableId, keyword));
    }

    @Operation(summary = "P2-3 字典自动识别（只读，不落库）")
    @GetMapping("/dict/detect")
    public Result<List<DictDetectItemVO>> detectDict(@RequestParam Long tableId) {
        return Result.ok(dictDetectService.detect(tableId));
    }

    @Operation(summary = "P2-3 DDL 内联识别枚举（导入前预览）")
    @PostMapping("/dict/detect-ddl")
    public Result<List<DictDetectItemVO>> detectDictFromDdl(@RequestBody GenDdlDTO dto) {
        return Result.ok(dictDetectService.detectFromDdl(dto.getTableName(), dto.getDdl()));
    }

    @Operation(summary = "P2-3 落库：建字典 + 回填 dict_type")
    @PostMapping("/dict/apply")
    public Result<Integer> applyDict(@RequestBody GenDictApplyDTO dto) {
        return Result.ok(dictDetectService.apply(dto.getTableId(), dto.getDictCodes()));
    }

    @Operation(summary = "生成输出全局配置（前端展示用）")
    @GetMapping("/output-config")
    public Result<GenOutputProperties> outputConfig() {
        return Result.ok(outputProperties);
    }

    @Operation(summary = "解析 DDL")
    @PostMapping("/parseDdl")
    public Result<GenTableSaveDTO> parseDdl(@RequestBody GenDdlDTO dto) {
        try {
            return Result.ok(GenUtils.parseDdl(dto.getDdl()));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, e.getMessage());
        }
    }

    @Operation(summary = "表配置分页查询")
    @PostMapping("/table/page")
    public Result<IPage<GenTableVO>> page(@RequestBody GenTablePageDTO dto) {
        return Result.ok(genTableService.pageList(dto));
    }

    @Operation(summary = "表配置详情（含字段）")
    @GetMapping("/table/{id}")
    public Result<GenTableVO> detail(@PathVariable("id") Long id) {
        return Result.ok(genTableService.detail(id));
    }

    @Operation(summary = "保存表配置（导入）")
    @PostMapping("/table")
    public Result<Long> save(@RequestBody GenTableSaveDTO dto) {
        return Result.ok(genTableService.saveConfig(dto));
    }

    @Operation(summary = "修改表配置（字段整体替换）")
    @PutMapping("/table")
    public Result<Void> update(@RequestBody GenTableSaveDTO dto) {
        genTableService.updateConfig(dto);
        return Result.ok();
    }

    @Operation(summary = "删除表配置")
    @DeleteMapping("/table/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        genTableService.deleteConfig(id);
        return Result.ok();
    }

    @Operation(summary = "批量设置输出参数 / 套用开关预设")
    @PostMapping("/table/batch")
    public Result<Integer> batchUpdate(@RequestBody GenTableBatchDTO dto) {
        return Result.ok(genTableService.batchUpdate(dto));
    }

    @Operation(summary = "批量删除表配置（连同菜单）")
    @PostMapping("/table/batch-delete")
    public Result<Integer> batchDelete(@RequestBody GenTableBatchDTO dto) {
        return Result.ok(genTableService.batchDelete(dto.getIds()));
    }

    @Operation(summary = "结构漂移一键同步（默认只增不删）")
    @PostMapping("/table/{id}/sync-columns")
    public Result<GenColumnSyncVO> syncColumns(@PathVariable("id") Long id,
                                               @RequestParam(value = "removeMissing", defaultValue = "false")
                                               boolean removeMissing) {
        return Result.ok(genTableService.syncColumns(id, removeMissing));
    }

    @Operation(summary = "17 个生成开关恢复所属系统默认值")
    @PostMapping("/table/{id}/switch-defaults/reset")
    public Result<java.util.Map<String, Integer>> resetSwitchDefaults(@PathVariable("id") Long id) {
        return Result.ok(genTableService.resetSwitchDefaults(id));
    }

    @Operation(summary = "差异预览（渲染结果 vs 磁盘）")
    @PostMapping("/diff")
    public Result<List<GenDiffVO>> diff(@RequestBody GenDiffDTO dto) {
        return Result.ok(genCodeService.diff(dto));
    }

    @Operation(summary = "生成代码 ZIP 下载")
    @PostMapping("/download")
    public ResponseEntity<byte[]> download(@RequestBody GenDownloadDTO dto) {
        GenCodeService.GenZipResult result = genCodeService.generateZip(dto);
        String manifest = String.join("\n", result.files());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gen_code.zip");
        headers.set("X-Gen-Files", new String(manifest.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.ISO_8859_1));
        return new ResponseEntity<>(result.bytes(), headers, HttpStatus.OK);
    }

    // ==================== 二期：数据库导入 / 预览 / 写盘 ====================

    @Operation(summary = "可导入数据库列表")
    @GetMapping("/db/schemas")
    public Result<List<String>> dbSchemas() {
        return Result.ok(genTableService.listDbSchemas());
    }

    @Operation(summary = "库表列表")
    @GetMapping("/db/tables")
    public Result<List<GenDbTableVO>> dbTables(@RequestParam("schema") String schema) {
        return Result.ok(genTableService.listDbTables(schema));
    }

    @Operation(summary = "库表批量导入")
    @PostMapping("/db/import")
    public Result<List<GenImportVO>> dbImport(@RequestBody GenDbImportDTO dto) {
        return Result.ok(genTableService.importTables(dto.getSchema(), dto.getTableNames(), dto.getProjectId()));
    }

    @Operation(summary = "生成预览（路径+内容）")
    @PostMapping("/preview")
    public Result<List<GenFileVO>> preview(@RequestBody GenDownloadDTO dto) {
        return Result.ok(genCodeService.preview(dto));
    }

    @Operation(summary = "写盘（按每表前后端根目录）")
    @PostMapping("/write")
    public Result<List<GenWriteVO>> write(@RequestBody GenWriteDTO dto) {
        return Result.ok(genCodeService.writeToDisk(dto));
    }

    @Operation(summary = "生成自检（内存编译 + 结构检查）")
    @PostMapping("/self-check")
    public Result<GenSelfCheckVO> selfCheck(@RequestBody GenDownloadDTO dto) {
        return Result.ok(genSelfCheckService.check(dto.getIds()));
    }

    @Operation(summary = "注册菜单与按钮权限（幂等）")
    @PostMapping("/menu/register")
    public Result<GenMenuRegisterVO> registerMenu(@RequestBody GenMenuRegisterDTO dto) {
        return Result.ok(genMenuService.register(dto));
    }

    @Operation(summary = "重建前端路由文件")
    @PostMapping("/menu/route-sync")
    public Result<GenMenuRegisterVO> syncRoute(@RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        return Result.ok(genMenuService.syncRoute(dryRun));
    }

    // ==================== V1.2：一键生成 / 回滚 ====================

    @Operation(summary = "一键生成（自检→备份→写盘→编译→注册菜单→重建路由）")
    @PostMapping("/apply")
    public Result<GenApplyVO> apply(@RequestBody GenApplyDTO dto) {
        return Result.ok(genApplyService.apply(dto));
    }

    @Operation(summary = "回滚某个写盘批次")
    @PostMapping("/history/rollback")
    public Result<Map<String, Object>> rollback(@RequestParam("batchNo") String batchNo) {
        return Result.ok(genApplyService.rollback(batchNo));
    }

    @Operation(summary = "最近写盘批次")
    @GetMapping("/history/batches")
    public Result<List<Map<String, Object>>> batches(@RequestParam(value = "limit", defaultValue = "10") int limit) {
        return Result.ok(genApplyService.recentBatches(limit));
    }

    // ==================== 四期：写盘台账 / 真校验 ====================

    @Operation(summary = "写盘台账分页（文件级一行一条，F13）")
    @PostMapping("/output/page")
    public Result<IPage<GenOutput>> outputPage(@RequestBody GenOutputPageDTO dto) {
        return Result.ok(genOutputService.pageList(dto));
    }

    @Operation(summary = "写盘批次摘要（按批次聚合，F13）")
    @GetMapping("/output/batches")
    public Result<List<GenOutputBatchVO>> outputBatches(
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return Result.ok(genOutputService.batchSummary(limit));
    }

    @Operation(summary = "前端产物真校验（读磁盘实际文件静态解析，F14）")
    @PostMapping("/verify")
    public Result<GenVerifyVO> verify(@RequestBody GenDownloadDTO dto) {
        return Result.ok(genVerifyService.check(dto.getIds()));
    }

    // ==================== P0：一键建工程 ====================

    @Operation(summary = "一键建工程（生成可运行的空白工程，含已验证基座）")
    @PostMapping("/scaffold")
    public Result<GenScaffoldVO> scaffold(@Valid @RequestBody GenScaffoldDTO dto) {
        return Result.ok(genScaffoldService.scaffold(dto));
    }

    @Operation(summary = "脚手架体检（缺什么、怎么补）")
    @GetMapping("/scaffold/check")
    public Result<GenScaffoldCheckVO> scaffoldCheck(
            @RequestParam(value = "backendRoot", required = false) String backendRoot,
            @RequestParam(value = "frontendRoot", required = false) String frontendRoot,
            @RequestParam(value = "rootPackage", required = false) String rootPackage) {
        return Result.ok(genScaffoldService.check(backendRoot, frontendRoot, rootPackage));
    }

    @Operation(summary = "执行 DDL 并自动导入表配置（建库建表一键导入）")
    @PostMapping("/db/execute-ddl")
    public Result<GenDdlExecuteVO> executeDdl(@Valid @RequestBody GenDdlExecuteDTO dto) {
        return Result.ok(genDdlService.execute(dto));
    }
}

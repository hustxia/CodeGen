package ${pkg}.controller;

<#if ctrlPageApi || hasChild || hasExcelExport>import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
</#if>
<#if hasFlow>import ${rootPkg}.common.flow.FlowService;
</#if>
<#if ctrlPageApi || hasExcelExport>import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ${pkg}.dto.${className}PageDTO;
</#if>
<#if ctrlAddApi || ctrlEditApi || hasChild>import ${pkg}.dto.${className}SaveDTO;
</#if>
<#if hasChild><#list children as child>import ${pkg}.dto.${child.className}SaveDTO;
import ${pkg}.entity.${child.className};
import ${pkg}.service.${child.className}Service;
import ${pkg}.vo.${child.className}VO;
<#list child.grandChildren as gc>import ${pkg}.entity.${gc.className};
import ${pkg}.service.${gc.className}Service;
import ${pkg}.vo.${gc.className}VO;
</#list>
</#list></#if>
<#-- Service 无条件注入：page/detail 等基础方法也要走 Service，
     曾经按开关条件注入，开关全关时方法体还在、字段没了，生成产物直接编译失败 -->
import ${pkg}.service.${className}Service;
import ${pkg}.entity.${className};
import ${pkg}.vo.${className}VO;
import ${rootPkg}.common.result.Result;
<#if hasRef || hasExcelImport || hasUnique>import ${rootPkg}.common.exception.BusinessException;
</#if>
<#if genLog>import ${rootPkg}.common.log.Log;
</#if>
<#if hasRef>import org.springframework.jdbc.core.JdbcTemplate;
</#if>
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
<#if ctrlValid>import jakarta.validation.Valid;
</#if>
<#if ctrlAnyApi || genTree || hasChild>import org.springframework.beans.BeanUtils;
</#if>
<#if ctrlAnyApi || genTree || hasChild || hasUnique || hasFlow || hasRef>import org.springframework.beans.factory.annotation.Autowired;
</#if>
<#if (ctrlAnyApi || genTree || hasChild) && ctrlPageApi>import org.springframework.util.StringUtils;
</#if>
<#if hasExcelExport && !ctrlPageApi>import org.springframework.util.StringUtils;
</#if>
<#if ctrlDeleteApi>import org.springframework.web.bind.annotation.DeleteMapping;
</#if>
<#if ctrlDetailApi || genTree || hasRef || hasExcelExport || hasUnique || hasExcelImport || hasFlow>import org.springframework.web.bind.annotation.GetMapping;
</#if>
import org.springframework.web.bind.annotation.PathVariable;
<#if ctrlPageApi || ctrlAddApi || hasChild || hasFlow>import org.springframework.web.bind.annotation.PostMapping;
</#if>
<#if ctrlEditApi>import org.springframework.web.bind.annotation.PutMapping;
</#if>
<#if ctrlPageApi || ctrlAddApi || ctrlEditApi || hasChild>import org.springframework.web.bind.annotation.RequestBody;
</#if>
import org.springframework.web.bind.annotation.RequestMapping;
<#if genTree || hasRef || hasExcelImport || hasUnique || hasFlow>import org.springframework.web.bind.annotation.RequestParam;
</#if>
import org.springframework.web.bind.annotation.RestController;
<#if ctrlPageApi || genTree || hasChild || hasExcelImport || hasFlow>import java.util.ArrayList;
</#if>
<#if ctrlPageApi || genTree || hasChild || hasRef || hasFlow>import java.util.List;
</#if>
<#if hasRef || hasExcelImport || hasChild || hasFlow>import java.util.LinkedHashMap;
import java.util.Map;
</#if>
<#if hasExcelExport || hasExcelImport>import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
</#if>
<#if hasExcelImport && !(ctrlAnyApi || genTree || hasChild)>import org.springframework.jdbc.core.JdbcTemplate;
</#if>

/**
 * ${comment} Controller
 */
@Tag(name = "${comment}管理", description = "${comment}管理")
@RestController
@RequestMapping("/${moduleName}")
public class ${className}Controller {
${''}
    // Service 注入无条件保留（理由见 import 区注释）
    @Autowired
    private ${className}Service ${varName}Service;
<#if hasChild><#list children as child>    @Autowired
    private ${child.className}Service ${child.varName}Service;
<#-- 孙表 Service：详情接口装配三级明细要用，漏注入生成的 Controller 编译不过 -->
<#list child.grandChildren as gc>    @Autowired
    private ${gc.className}Service ${gc.varName}Service;
</#list>
</#list></#if>
<#if hasFlow>    @Autowired
    private FlowService flowService;
</#if>
<#if hasDictColumn>
    @Autowired
    private ${rootPkg}.common.util.DictTexts dictTexts;
</#if>
    // <gen:begin key="controller-body">
<#if hasRef>
    @Autowired
    private JdbcTemplate jdbcTemplate;
</#if>
<#if isMiddle && relationKeys?size gte 2>${''}
@Operation(summary = "${comment}：按一个外键取另一侧的全部值（多选回显用）")
@GetMapping("/bind-values")
public Result<List<Long>> bindValues(@RequestParam("column") String column,
                                     @RequestParam("id") Long id) {
    // column 只接受本表的两个外键列，其余一律拒绝，杜绝把任意列拼进 SQL
    boolean left = "${relationKeys[0].dbField}".equalsIgnoreCase(column);
    boolean right = "${relationKeys[1].dbField}".equalsIgnoreCase(column);
    if (!left && !right) {
        throw new BusinessException(400, "不是合法的关联列：" + column);
    }
    // 传入的是左键就返右键的集合，反之亦然；两边都按同一个 id 过滤
    // Lambda 选择在 Java 侧做（模板里写三元会被 FreeMarker 当字符串），两条分支各自编译期确定
    List<${className}> rows = left
            ? ${varName}Service.list(new LambdaQueryWrapper<${className}>()
                    .eq(${className}::get${relationKeys[0].capProp}, id))
            : ${varName}Service.list(new LambdaQueryWrapper<${className}>()
                    .eq(${className}::get${relationKeys[1].capProp}, id));
    List<Long> values = new ArrayList<>();
    for (${className} row : rows) {
        Object v = left ? row.get${relationKeys[1].capProp}() : row.get${relationKeys[0].capProp}();
        if (v != null) {
            values.add(Long.valueOf(String.valueOf(v)));
        }
    }
    return Result.ok(values);
}
</#if>
<#if ctrlPageApi>${''}
@Operation(summary = "分页查询${comment}")
@PostMapping("/page")
public Result<Page<${className}VO>> page(@RequestBody ${className}PageDTO dto) {
    Page<${className}> page = new Page<>(dto.getPageNum(), dto.getPageSize());
    LambdaQueryWrapper<${className}> wrapper = new LambdaQueryWrapper<>();
<#list queryColumns as c>
        wrapper.${c.queryOp}(<#if c.isString>StringUtils.hasText(dto.get${c.capField}())<#else>dto.get${c.capField}() != null</#if>, ${className}::get${c.capField}, dto.get${c.capField}());
</#list>
        wrapper.orderByDesc(${className}::getId);
    ${varName}Service.page(page, wrapper);
    Page<${className}VO> voPage = new Page<>();
    BeanUtils.copyProperties(page, voPage);
    List<${className}VO> records = page.getRecords().stream()
            .map(e -> {
                ${className}VO vo = new ${className}VO();
                BeanUtils.copyProperties(e, vo);
                return vo;
            })
            .toList();
    voPage.setRecords(records);
    return Result.ok(voPage);
}
</#if>
<#if genTree>${''}
@Operation(summary = "${comment}树形列表")
@GetMapping("/tree")
public Result<List<${className}VO>> tree() {
    return Result.ok(${varName}Service.tree());
}
</#if>
<#if hasRef>${''}
@Operation(summary = "关联下拉选项（通用，按 ref_table 远程搜索）")
@GetMapping("/options")
public Result<List<Map<String, Object>>> options(@RequestParam("refTable") String refTable,
                                                @RequestParam(value = "label", required = false) String label,
                                                @RequestParam(value = "keyword", required = false) String keyword) {
    if (refTable == null || !refTable.matches("[A-Za-z0-9_]+")) {
        throw new BusinessException(400, "非法关联表名");
    }
    String labelCol = label == null || !label.matches("[A-Za-z0-9_]+") ? "name" : label;
    String base = "SELECT id, " + labelCol + " AS label FROM " + refTable;
    String like = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
    // 无关键字是「列表页翻译 ID 为名称」，要一次拉够；有关键字是下拉搜索，20 条足够
    int limit = like == null ? 500 : 20;
    String sql = base + " WHERE deleted = 0" + (like == null ? "" : " AND " + labelCol + " LIKE ?")
            + " ORDER BY id DESC LIMIT " + limit;
    try {
        return Result.ok(like == null
                ? jdbcTemplate.queryForList(sql)
                : jdbcTemplate.queryForList(sql, like));
    } catch (Exception e) {
        // 关联表没有 deleted 列（如关联表）时降级为不带软删条件
        String fallback = base + (like == null ? "" : " WHERE " + labelCol + " LIKE ?")
                + " ORDER BY id DESC LIMIT " + limit;
        return Result.ok(like == null
                ? jdbcTemplate.queryForList(fallback)
                : jdbcTemplate.queryForList(fallback, like));
    }
}
</#if>
<#if hasChild>${''}
@Operation(summary = "主表详情（含${childCount}张子表明细）")
@GetMapping("/{id}/with-children")
public Result<Map<String, Object>> detailWithChildren(@PathVariable("id") Long id) {
    ${className} entity = ${varName}Service.getById(id);
    Map<String, Object> data = new LinkedHashMap<>();
    if (entity == null) {
        return Result.ok(data);
    }
    ${className}VO masterVo = new ${className}VO();
    BeanUtils.copyProperties(entity, masterVo);
    List<Map<String, Object>> children = new ArrayList<>();
<#list children as child>
    // 子表：${child.comment}（整棵树一次返回，避免前端逐行发请求）
    Map<String, Object> ${child.varName}Group = new LinkedHashMap<>();
    ${child.varName}Group.put("key", "${child.varName}");
    ${child.varName}Group.put("tableName", "${child.tableName}");
    ${child.varName}Group.put("comment", "${child.comment}");
    List<${child.className}VO> ${child.varName}List = new ArrayList<>();
    for (${child.className} row : ${child.varName}Service.list(
            new LambdaQueryWrapper<${child.className}>().eq(${child.className}::get${child.fkCapField}, id))) {
        ${child.className}VO childVo = new ${child.className}VO();
        BeanUtils.copyProperties(row, childVo);
<#list child.grandChildren as gc>
        // 三级：按子表主键再查一层
        List<${gc.className}VO> ${gc.varName}List = new ArrayList<>();
        for (${gc.className} g : ${gc.varName}Service.list(
                new LambdaQueryWrapper<${gc.className}>()
                        .eq(${gc.className}::get${gc.fkCapField}, row.getId()))) {
            ${gc.className}VO gVo = new ${gc.className}VO();
            BeanUtils.copyProperties(g, gVo);
            ${gc.varName}List.add(gVo);
        }
        childVo.set${gc.className}s(${gc.varName}List);
</#list>
        ${child.varName}List.add(childVo);
    }
    ${child.varName}Group.put("rows", ${child.varName}List);
    children.add(${child.varName}Group);
</#list>
    data.put("master", masterVo);
    data.put("children", children);
    return Result.ok(data);
}

@Operation(summary = "保存${comment}（主表 + 子表同事务；子表字段为 null 表示不动，空列表表示清空）")
@PostMapping("/save-with-children")
public Result<Void> saveWithChildren(<#if ctrlValid>@Valid </#if>@RequestBody WithChildrenDTO dto) {
    ${varName}Service.saveWithChildren(dto.getMaster()<#list children as child>,
                                       dto.get${child.varName?cap_first}Rows()</#list>);
    return Result.ok();
}

/**
 * 主子表保存入参：每张子表一个可空字段（null=不动该子表，[]=清空该子表）
 */
@lombok.Data
public static class WithChildrenDTO {

    private ${className}SaveDTO master;
<#list children as child>

    private List<${child.className}SaveDTO> ${child.varName}Rows;
</#list>
}
</#if>
<#if hasExcelExport && !isMiddle>${''}
@Operation(summary = "导出${comment} Excel（按查询条件过滤；不传条件导出全部）")
@PostMapping("/export")
public ResponseEntity<byte[]> export(@RequestBody(required = false) ${className}PageDTO query) throws IOException {
    // 复用列表页的筛选口径：导出的行必须和用户在页面上看到的一致，
    // 否则「筛出 20 条、导出 2000 条」会被当成 bug。
    LambdaQueryWrapper<${className}> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
<#list queryColumns as c>
        wrapper.${c.queryOp}(<#if c.isString>StringUtils.hasText(query.get${c.capField}())<#else>query.get${c.capField}() != null</#if>, ${className}::get${c.capField}, query.get${c.capField}());
</#list>
    }
    wrapper.orderByDesc(${className}::getId);
    List<${className}> rows = ${varName}Service.list(wrapper);
    try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
        Sheet sheet = wb.createSheet("${comment}");
        Row header = sheet.createRow(0);
<#list excelColumns as c>
        header.createCell(${c?index}).setCellValue("${c.commentLabel}");
</#list>
        int rowIndex = 1;
        for (${className} e : rows) {
            Row row = sheet.createRow(rowIndex++);
<#list excelColumns as c>
<#if c.hasDict>
            // 字典列导出中文标签，与页面显示一致；null 写空串，不能落成字面量 "null"
            row.createCell(${c?index}).setCellValue(e.get${c.capField}() == null ? "" : dictTexts.label("${c.dictType}", String.valueOf(e.get${c.capField}())));
<#elseif c.isNumber>
            row.createCell(${c?index}).setCellValue(cellNum(e.get${c.capField}()));
<#else>
            row.createCell(${c?index}).setCellValue(cellText(e.get${c.capField}()));
</#if>
</#list>
        }
        wb.write(bos);
        String fileName = URLEncoder.encode("${comment}.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bos.toByteArray());
    }
}
</#if>
<#if hasExcelImport>${''}
@Operation(summary = "导入${comment} Excel（首行为表头，逐行独立事务）")
<#if genLog>@Log(title = "${comment}", type = "IMPORT", saveParam = false)
</#if>@PostMapping("/import")
public Result<Map<String, Object>> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
    if (file == null || file.isEmpty()) {
        throw new BusinessException(400, "请选择要导入的文件");
    }
    int success = 0;
    int skipped = 0;
    List<Map<String, Object>> failed = new ArrayList<>();
    try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
        Sheet sheet = wb.getSheetAt(0);
        Row header = sheet.getRow(0);
        if (header == null) {
            throw new BusinessException(400, "表头为空，无法导入");
        }
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < header.getLastCellNum(); i++) {
            index.put(cellText(header.getCell(i)).trim(), i);
        }
        // 表头缺失的列直接提示出来，避免用户以为导入了却全是空值
        List<String> missing = new ArrayList<>();
<#list excelImportColumns as c>
        Integer col${c.capField} = index.get("${c.commentLabel}");
        if (col${c.capField} == null) {
            missing.add("${c.commentLabel}");
        }
</#list>
        if (!missing.isEmpty()) {
            throw new BusinessException(400, "表头缺少列：" + String.join("、", missing));
        }
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || isBlankRow(row)) {
                continue;
            }
            // 每行独立事务：单行失败只回滚该行，不影响已成功的行
            try {
                ${className} entity = new ${className}();
<#list excelImportColumns as c>
                entity.set${c.capField}(<#if c.isString>text(row, col${c.capField})<#elseif c.javaType == "Integer">parseInteger(row, col${c.capField})<#elseif c.javaType == "Long">parseLong(row, col${c.capField})<#elseif c.javaType == "BigDecimal">parseDecimal(row, col${c.capField})<#elseif c.javaType == "LocalDate">parseDate(row, col${c.capField})<#elseif c.javaType == "LocalDateTime">parseDateTime(row, col${c.capField})<#else>text(row, col${c.capField})</#if>);
</#list>
                List<String> rowErrors = validateImportRow(entity, r + 1);
                if (!rowErrors.isEmpty()) {
                    Map<String, Object> err = new LinkedHashMap<>();
                    err.put("row", r + 1);
                    err.put("msg", String.join("；", rowErrors));
                    failed.add(err);
                    continue;
                }
                if (saveImportRow(entity)) {
                    success++;
                } else {
                    skipped++;
                }
            } catch (Exception ex) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("row", r + 1);
                err.put("msg", rootMessage(ex));
                failed.add(err);
            }
        }
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("total", success + skipped + failed.size());
    data.put("success", success);
    data.put("skipped", skipped);
    data.put("failedCount", failed.size());
    data.put("failed", failed);
    return Result.ok(data);
}

@Operation(summary = "下载${comment}导入模板（表头 + 填写说明行）")
@GetMapping("/import-template")
public ResponseEntity<byte[]> importTemplate() throws IOException {
    try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
        Sheet sheet = wb.createSheet("${comment}导入模板");
        // 第 1 行是表头，必须与 /import 的表头匹配逻辑完全一致，否则下载的模板自己导不回去。
        // 导出表头/导入定位/本模板三处统一 commentLabel（剥枚举说明），改一处必须同步三处
        Row header = sheet.createRow(0);
<#list excelImportColumns as c>
        header.createCell(${c?index}).setCellValue("${c.commentLabel}");
</#list>
        // 第 2 行是填写说明。导入逻辑按列名匹配表头、逐行校验，
        // 这一行必然过不了校验而被记进 failed，所以文案里直接写「请删除本行」。
        // 不放下拉/数据验证：POI 的 DataValidation 需要额外依赖，且模板只起提示作用。
        Row hint = sheet.createRow(1);
<#list excelImportColumns as c>
        hint.createCell(${c?index}).setCellValue("【说明行，导入前请删除】<#if c.isRequired>必填<#else>选填</#if><#if (c.maxLen gt 0)>，不超过${c.maxLen}字</#if>");
</#list>
<#list excelImportColumns as c>
        sheet.setColumnWidth(${c?index}, Math.min(${c.colWidth}, 60) * 256);
</#list>
        wb.write(bos);
        String fileName = URLEncoder.encode("${comment}导入模板.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bos.toByteArray());
    }
}

@Operation(summary = "下载导入失败明细")
@GetMapping("/import/failed")
public ResponseEntity<byte[]> downloadImportFailed(@RequestParam("msg") List<String> messages) throws IOException {
    try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
        Sheet sheet = wb.createSheet("导入失败明细");
        Row head = sheet.createRow(0);
        head.createCell(0).setCellValue("行号");
        head.createCell(1).setCellValue("失败原因");
        int r = 1;
        for (String msg : messages) {
            Row row = sheet.createRow(r);
            String[] parts = msg.split("\\|", 2);
            row.createCell(0).setCellValue(parts.length > 1 ? parts[0] : String.valueOf(r));
            row.createCell(1).setCellValue(parts.length > 1 ? parts[1] : msg);
            r++;
        }
        sheet.setColumnWidth(0, 10 * 256);
        sheet.setColumnWidth(1, 60 * 256);
        wb.write(bos);
        String fileName = URLEncoder.encode("${comment}导入失败明细.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bos.toByteArray());
    }
}

/**
 * 逐行业务校验：只校验「必填」与「长度」，其余交给数据库约束。
 * 返回空列表代表该行可入库。
 */
private List<String> validateImportRow(${className} entity, int rowNum) {
    List<String> errors = new ArrayList<>();
<#list excelImportColumns as c>
<#if c.isRequired>
    if (<#if c.isString>entity.get${c.capField}() == null || entity.get${c.capField}().isBlank()<#else>entity.get${c.capField}() == null</#if>) {
        errors.add("${c.commentLabel}不能为空");
    }
</#if>
<#if c.isString && c.maxLen gt 0>
    if (entity.get${c.capField}() != null && entity.get${c.capField}().length() > ${c.maxLen}) {
        errors.add("${c.commentLabel}长度不能超过${c.maxLen}个字符");
    }
</#if>
</#list>
    return errors;
}

/**
 * 保存单行。返回 false 表示命中唯一约束被跳过（视为已存在，不算失败）。
 */
private boolean saveImportRow(${className} entity) {
    try {
        return ${varName}Service.save(entity);
    } catch (org.springframework.dao.DuplicateKeyException ex) {
        return false;
    }
}

private static String rootMessage(Throwable ex) {
    Throwable cur = ex;
    while (cur.getCause() != null && cur.getCause() != cur) {
        cur = cur.getCause();
    }
    String msg = cur.getMessage();
    if (msg == null || msg.isBlank()) {
        return "数据格式不正确";
    }
    // 数据库原始报错太长，截断到可读长度
    return msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
}

private static boolean isBlankRow(Row row) {
    for (int i = 0; i < row.getLastCellNum(); i++) {
        if (!cellText(row.getCell(i)).isBlank()) {
            return false;
        }
    }
    return true;
}
</#if>
<#if ctrlDetailApi>${''}
@Operation(summary = "根据ID查询${comment}")
@GetMapping("/{id}")
public Result<${className}VO> getById(@PathVariable("id") Long id) {
    ${className} entity = ${varName}Service.getById(id);
    if (entity == null) {
        return Result.ok(null);
    }
    ${className}VO vo = new ${className}VO();
    BeanUtils.copyProperties(entity, vo);
    return Result.ok(vo);
}
</#if>
<#if ctrlAddApi>${''}
@Operation(summary = "新增${comment}")
<#if genLog>@Log(title = "${comment}", type = "INSERT")
</#if>@PostMapping
public Result<Void> add(<#if ctrlValid>@Valid </#if>@RequestBody ${className}SaveDTO dto) {
<#if hasUnique>
    checkUnique(dto, null);
</#if>
    ${className} entity = new ${className}();
    BeanUtils.copyProperties(dto, entity);
    ${varName}Service.save(entity);
    return Result.ok();
}
</#if>
<#if ctrlEditApi>${''}
@Operation(summary = "修改${comment}")
<#if genLog>@Log(title = "${comment}", type = "UPDATE")
</#if>@PutMapping
public Result<Void> update(<#if ctrlValid>@Valid </#if>@RequestBody ${className}SaveDTO dto) {
<#if hasUnique>
    checkUnique(dto, dto.getId());
</#if>
    ${className} entity = new ${className}();
    BeanUtils.copyProperties(dto, entity);
    ${varName}Service.updateById(entity);
    return Result.ok();
}
</#if>
<#if ctrlDeleteApi>${''}
@Operation(summary = "删除${comment}<#if hasChild>（按表的级联模式处理明细）</#if>")
<#if genLog>@Log(title = "${comment}", type = "DELETE")
</#if>@DeleteMapping("/{id}")
public Result<Void> delete(@PathVariable("id") Long id) {
<#if hasChild>
    // V4.4：主子表走 removeWithChildren（级联/阻止/不处理由生成配置决定），不能直接 removeById 留孤儿明细
    ${varName}Service.removeWithChildren(id);
<#else>
    ${varName}Service.removeById(id);
</#if>
    return Result.ok();
}
</#if>
    // <gen:end key="controller-body">
<#if hasFlow>${''}
    // ==================== P3-3 审批流（流程：${flowCode}） ====================

    @Operation(summary = "提交${comment}审批")
    <#if genLog>@Log(title = "${comment}", type = "SUBMIT")
    </#if>@PostMapping("/flow/submit/{id}")
    public Result<Void> flowSubmit(@PathVariable("id") Long id) {
        flowService.submit("${flowCode}", id, null);
        return Result.ok();
    }

    @Operation(summary = "审批通过${comment}")
    @PostMapping("/flow/approve/{id}")
    public Result<Void> flowApprove(@PathVariable("id") Long id,
                                    @RequestParam(value = "comment", required = false) String comment) {
        flowService.approve("${flowCode}", id, comment);
        return Result.ok();
    }

    @Operation(summary = "驳回${comment}")
    @PostMapping("/flow/reject/{id}")
    public Result<Void> flowReject(@PathVariable("id") Long id,
                                   @RequestParam(value = "comment", required = false) String comment) {
        flowService.reject("${flowCode}", id, comment);
        return Result.ok();
    }

    @Operation(summary = "${comment}审批记录")
    @GetMapping("/flow/records/{id}")
    public Result<List<Map<String, Object>>> flowRecords(@PathVariable("id") Long id) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (${rootPkg}.common.flow.FlowRecord r : flowService.records("${flowCode}", id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("action", r.getAction());
            m.put("fromStatus", r.getFromStatus());
            m.put("toStatus", r.getToStatus());
            m.put("comment", r.getComment());
            m.put("operName", r.getOperName());
            m.put("operTime", r.getOperTime());
            list.add(m);
        }
        return Result.ok(list);
    }
</#if>
<#if hasUnique>${''}
    // ==================== P1-3 唯一性校验 ====================

    @Operation(summary = "字段唯一性校验（前端异步查重用）")
    @GetMapping("/check-unique")
    public Result<Boolean> checkUniqueField(@RequestParam("field") String field,
                                            @RequestParam("value") String value,
                                            @RequestParam(value = "excludeId", required = false) Long excludeId) {
        <#list uniqueColumns as c>
        if ("${c.javaField}".equals(field)) {
            return Result.ok(countBy("${c.dbField}", value, excludeId) > 0);
        }
        </#list>
        return Result.ok(false);
    }

    /**
     * 保存前兜底校验：前端异步校验只是体验优化，绕过接口直接调保存时这里必须拦住。
     * 单列 unique 逐列判断；多列 unique 联合判断（同时相等才算重复）。
     */
    private void checkUnique(${className}SaveDTO dto, Long excludeId) {
        <#if hasSingleUnique>
        <#list uniqueColumns as c>
        if (dto.get${c.capField}() != null && !String.valueOf(dto.get${c.capField}()).isBlank()
                && countBy("${c.dbField}", String.valueOf(dto.get${c.capField}()), excludeId) > 0) {
            throw new BusinessException(400, "${c.commentLabel}已存在");
        }
        </#list>
        <#else>
        boolean anyNull = <#list uniqueColumns as c>dto.get${c.capField}() == null || </#list>false;
        if (!anyNull) {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<${className}> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            <#list uniqueColumns as c>
            wrapper.eq(${className}::get${c.capField}, dto.get${c.capField}());
            </#list>
            if (excludeId != null) {
                wrapper.ne(${className}::getId, excludeId);
            }
            if (${varName}Service.count(wrapper) > 0) {
                throw new BusinessException(400, "<#list uniqueColumns as c>${c.commentLabel}<#if c_has_next>+</#if></#list>组合已存在");
            }
        }
        </#if>
    }

    /**
     * 按列名统计重复条数。列名来自生成期常量，不存在拼接用户输入的风险。
     */
    private long countBy(String column, String value, Long excludeId) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<${className}> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        wrapper.eq(column, value);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        return ${varName}Service.count(wrapper);
    }
</#if>
<#if hasExcelExport || hasExcelImport>${''}
    private static String cellText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static double cellNum(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0D;
    }

    private static String text(Row row, Integer col) {
        if (col == null || row == null) {
            return null;
        }
        String value = cellText(row.getCell(col)).trim();
        return value.isEmpty() ? null : value;
    }
<#if hasExcelImport>${''}
    private static Integer parseInteger(Row row, Integer col) {
        String value = text(row, col);
        return value == null ? null : new BigDecimal(value).intValue();
    }

    private static Long parseLong(Row row, Integer col) {
        String value = text(row, col);
        return value == null ? null : new BigDecimal(value).longValue();
    }

    private static BigDecimal parseDecimal(Row row, Integer col) {
        String value = text(row, col);
        return value == null ? null : new BigDecimal(value);
    }

    private static LocalDate parseDate(Row row, Integer col) {
        String value = text(row, col);
        return value == null ? null : LocalDate.parse(value.replace("/", "-"));
    }

    private static LocalDateTime parseDateTime(Row row, Integer col) {
        String value = text(row, col);
        return value == null ? null : LocalDateTime.parse(value.replace("/", "-").replace(" ", "T"));
    }
</#if>
</#if>
    // ===== 以下为手写区：自定义接口写在这里，重新生成不会被覆盖 =====
}

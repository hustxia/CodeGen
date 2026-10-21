package ${pkg}.controller;

<#if ctrlPageApi || hasChild>import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
</#if>
<#if ctrlPageApi>import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ${pkg}.dto.${className}PageDTO;
</#if>
<#if ctrlAddApi || ctrlEditApi || hasChild>import ${pkg}.dto.${className}SaveDTO;
</#if>
<#if hasChild>import ${pkg}.dto.${child.className}SaveDTO;
import ${pkg}.entity.${child.className};
import ${pkg}.service.${child.className}Service;
import ${pkg}.vo.${child.className}VO;
</#if>
<#if ctrlAnyApi || genTree || hasChild>import ${pkg}.service.${className}Service;
import ${pkg}.entity.${className};
</#if>
import ${pkg}.vo.${className}VO;
<#if ctrlAnyApi || genTree || hasChild>import ${rootPkg}.common.result.Result;
</#if>
<#if hasRef || hasExcelImport>import ${rootPkg}.common.exception.BusinessException;
</#if>
<#if hasRef>import org.springframework.jdbc.core.JdbcTemplate;
</#if>
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
<#if ctrlValid>import jakarta.validation.Valid;
</#if>
<#if ctrlAnyApi || genTree || hasChild>import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
<#if ctrlPageApi>import org.springframework.util.StringUtils;
</#if>
<#if ctrlDeleteApi>import org.springframework.web.bind.annotation.DeleteMapping;
</#if>
<#if ctrlDetailApi || genTree || hasRef || hasExcelExport>import org.springframework.web.bind.annotation.GetMapping;
</#if>
import org.springframework.web.bind.annotation.PathVariable;
<#if ctrlPageApi || ctrlAddApi || hasChild>import org.springframework.web.bind.annotation.PostMapping;
</#if>
<#if ctrlEditApi>import org.springframework.web.bind.annotation.PutMapping;
</#if>
<#if ctrlPageApi || ctrlAddApi || ctrlEditApi || hasChild>import org.springframework.web.bind.annotation.RequestBody;
</#if>
import org.springframework.web.bind.annotation.RequestMapping;
<#if genTree || hasRef || hasExcelImport>import org.springframework.web.bind.annotation.RequestParam;
</#if>
import org.springframework.web.bind.annotation.RestController;
<#if ctrlPageApi || genTree || hasChild || hasExcelImport>import java.util.ArrayList;
</#if>
<#if ctrlPageApi || genTree || hasChild || hasRef>import java.util.List;
</#if>
<#if hasRef || hasExcelImport || hasChild>import java.util.LinkedHashMap;
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
<#if ctrlAnyApi || genTree || hasChild>    @Autowired
    private ${className}Service ${varName}Service;
</#if>
<#if hasChild>    @Autowired
    private ${child.className}Service ${child.varName}Service;
</#if>
<#if hasRef>
    @Autowired
    private JdbcTemplate jdbcTemplate;
</#if>
    // <gen:begin key="controller-body">
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
    String sql = base + " WHERE deleted = 0" + (like == null ? "" : " AND " + labelCol + " LIKE ?")
            + " ORDER BY id DESC LIMIT 20";
    try {
        return Result.ok(like == null
                ? jdbcTemplate.queryForList(sql)
                : jdbcTemplate.queryForList(sql, like));
    } catch (Exception e) {
        // 关联表没有 deleted 列（如关联表）时降级为不带软删条件
        String fallback = base + (like == null ? "" : " WHERE " + labelCol + " LIKE ?")
                + " ORDER BY id DESC LIMIT 20";
        return Result.ok(like == null
                ? jdbcTemplate.queryForList(fallback)
                : jdbcTemplate.queryForList(fallback, like));
    }
}
</#if>
<#if hasChild>${''}
@Operation(summary = "主表详情（含子表${child.comment}）")
@GetMapping("/{id}/with-children")
public Result<Map<String, Object>> detailWithChildren(@PathVariable("id") Long id) {
    ${className} entity = ${varName}Service.getById(id);
    Map<String, Object> data = new LinkedHashMap<>();
    if (entity == null) {
        return Result.ok(data);
    }
    ${className}VO vo = new ${className}VO();
    BeanUtils.copyProperties(entity, vo);
    List<${child.className}> childRows = ${child.varName}Service.list(
            new LambdaQueryWrapper<${child.className}>().eq(${child.className}::get${child.fkCapField}, id));
    List<${child.className}VO> children = new ArrayList<>();
    for (${child.className} row : childRows) {
        ${child.className}VO childVo = new ${child.className}VO();
        BeanUtils.copyProperties(row, childVo);
        children.add(childVo);
    }
    data.put("master", vo);
    data.put("children", children);
    return Result.ok(data);
}

@Operation(summary = "保存${comment}（主表 + 子表同事务）")
@PostMapping("/save-with-children")
public Result<Void> saveWithChildren(<#if ctrlValid>@Valid </#if>@RequestBody WithChildrenDTO dto) {
    ${varName}Service.saveWithChildren(dto.getMaster(), dto.getChildren());
    return Result.ok();
}

/**
 * 主子表保存入参
 */
@lombok.Data
public static class WithChildrenDTO {

    private ${className}SaveDTO master;

    private List<${child.className}SaveDTO> children;
}
</#if>
<#if hasExcelExport>${''}
@Operation(summary = "导出${comment} Excel")
@GetMapping("/export")
public ResponseEntity<byte[]> export() throws IOException {
    List<${className}> rows = ${varName}Service.list();
    try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
        Sheet sheet = wb.createSheet("${comment}");
        Row header = sheet.createRow(0);
<#list excelColumns as c>
        header.createCell(${c?index}).setCellValue("${c.columnComment}");
</#list>
        int rowIndex = 1;
        for (${className} e : rows) {
            Row row = sheet.createRow(rowIndex++);
<#list excelColumns as c>
<#if c.isNumber>
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
@Operation(summary = "导入${comment} Excel（首行为表头）")
@PostMapping("/import")
public Result<Map<String, Object>> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
    if (file == null || file.isEmpty()) {
        throw new BusinessException(400, "请选择要导入的文件");
    }
    int success = 0;
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
<#list excelImportColumns as c>
        Integer col${c.capField} = index.get("${c.columnComment}");
</#list>
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            try {
                ${className} entity = new ${className}();
<#list excelImportColumns as c>
                entity.set${c.capField}(<#if c.isString>text(row, col${c.capField})<#elseif c.javaType == "Integer">parseInteger(row, col${c.capField})<#elseif c.javaType == "Long">parseLong(row, col${c.capField})<#elseif c.javaType == "BigDecimal">parseDecimal(row, col${c.capField})<#elseif c.javaType == "LocalDate">parseDate(row, col${c.capField})<#elseif c.javaType == "LocalDateTime">parseDateTime(row, col${c.capField})<#else>text(row, col${c.capField})</#if>);
</#list>
                ${varName}Service.save(entity);
                success++;
            } catch (Exception ex) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("row", r + 1);
                err.put("msg", ex.getMessage() == null ? "数据格式不正确" : ex.getMessage());
                failed.add(err);
            }
        }
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("success", success);
    data.put("failed", failed);
    return Result.ok(data);
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
@PostMapping
public Result<Void> add(<#if ctrlValid>@Valid </#if>@RequestBody ${className}SaveDTO dto) {
    ${className} entity = new ${className}();
    BeanUtils.copyProperties(dto, entity);
    ${varName}Service.save(entity);
    return Result.ok();
}
</#if>
<#if ctrlEditApi>${''}
@Operation(summary = "修改${comment}")
@PutMapping
public Result<Void> update(<#if ctrlValid>@Valid </#if>@RequestBody ${className}SaveDTO dto) {
    ${className} entity = new ${className}();
    BeanUtils.copyProperties(dto, entity);
    ${varName}Service.updateById(entity);
    return Result.ok();
}
</#if>
<#if ctrlDeleteApi>${''}
@Operation(summary = "删除${comment}")
@DeleteMapping("/{id}")
public Result<Void> delete(@PathVariable("id") Long id) {
    ${varName}Service.removeById(id);
    return Result.ok();
}
</#if>
    // <gen:end key="controller-body">
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

package com.codegen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * V9.19 模板包导入结果。
 *
 * <p>校验失败时 HTTP 仍返回 {@code code=200}，由 {@code success=false + errors}
 * 承载逐条错误，前端一次性展示全部问题，不用"修一条报一条"。
 */
@Data
@Schema(description = "模板包导入结果")
public class GenTemplatePackImportVO {

    @Schema(description = "是否导入成功（全部模板通过校验并已落盘）")
    private boolean success;

    @Schema(description = "成功导入的模板数")
    private int imported;

    @Schema(description = "覆盖已有 key 的数量")
    private int overwritten;

    @Schema(description = "成功导入的模板 key 列表")
    private List<String> keys = new ArrayList<>();

    @Schema(description = "逐条错误列表；为空表示成功")
    private List<String> errors = new ArrayList<>();

    public static GenTemplatePackImportVO ok(int imported, int overwritten, List<String> keys) {
        GenTemplatePackImportVO vo = new GenTemplatePackImportVO();
        vo.setSuccess(true);
        vo.setImported(imported);
        vo.setOverwritten(overwritten);
        vo.setKeys(keys == null ? new ArrayList<>() : new ArrayList<>(keys));
        return vo;
    }

    public static GenTemplatePackImportVO fail(List<String> errors) {
        GenTemplatePackImportVO vo = new GenTemplatePackImportVO();
        vo.setSuccess(false);
        vo.setErrors(errors == null ? new ArrayList<>() : new ArrayList<>(errors));
        return vo;
    }
}

package com.codegen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 脚手架体检结果：逐项通过/不通过 + 修复建议。
 *
 * <p>痛点来源：新工程缺 pom 依赖、缺基座类、缺分页插件时不会立刻报错，
 * 而是等到运行期出现 {@code total=0}、{@code ClassNotFoundException} 才发现。
 * 这里把「该有的东西」显式列出来核对。
 */
@Data
@Schema(description = "脚手架体检结果")
public class GenScaffoldCheckVO {

    @Schema(description = "后端工程绝对路径")
    private String backendPath;

    @Schema(description = "前端工程绝对路径")
    private String frontendPath;

    @Schema(description = "是否全部通过")
    private boolean passed;

    @Schema(description = "通过项数")
    private int passedCount;

    @Schema(description = "不通过项数")
    private int failedCount;

    @Schema(description = "检查项明细")
    private List<Item> items = new ArrayList<>();

    @Data
    @Schema(description = "单个检查项")
    public static class Item {

        @Schema(description = "检查项标识")
        private String key;

        @Schema(description = "检查项名称")
        private String name;

        @Schema(description = "归属：backend / frontend")
        private String side;

        @Schema(description = "是否通过")
        private boolean passed;

        @Schema(description = "判定依据（找到的路径或缺失说明）")
        private String detail;

        @Schema(description = "不通过时的修复建议")
        private String suggestion;

        public Item() {
        }

        public Item(String key, String name, String side, boolean passed, String detail, String suggestion) {
            this.key = key;
            this.name = name;
            this.side = side;
            this.passed = passed;
            this.detail = detail;
            this.suggestion = suggestion;
        }
    }
}

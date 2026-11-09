package com.codegen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一键建工程结果：逐文件回报三种状态，便于前端展示与重复执行。
 */
@Data
@Schema(description = "一键建工程结果")
public class GenScaffoldVO {

    @Schema(description = "后端工程绝对路径")
    private String backendPath;

    @Schema(description = "前端工程绝对路径")
    private String frontendPath;

    @Schema(description = "V6.5 移动端工程绝对路径（mobile_enabled 关闭时为 null）")
    private String mobilePath;

    @Schema(description = "本次实际写入文件数")
    private int written;

    @Schema(description = "已存在而跳过的文件数")
    private int skipped;

    @Schema(description = "逐文件明细")
    private List<Item> files = new ArrayList<>();

    @Schema(description = "下一步提示（前端可直接展示给用户）")
    private List<String> nextSteps = new ArrayList<>();

    @Data
    @Schema(description = "单个产物文件")
    public static class Item {

        @Schema(description = "相对所属工程根的路径")
        private String path;

        @Schema(description = "归属：backend / frontend")
        private String side;

        @Schema(description = "状态：WRITTEN 新建 / SKIPPED 已存在跳过 / OVERWRITTEN 覆盖")
        private String status;

        public Item() {
        }

        public Item(String path, String side, String status) {
            this.path = path;
            this.side = side;
            this.status = status;
        }
    }
}

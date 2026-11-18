package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * P2-5 接口目录：一张表对应一组接口。
 *
 * <p>数据来源是 gen_table / gen_table_column 的开关，不依赖运行时反射扫描，
 * 所以「还没生成代码」的表也能提前看到将会有哪些接口。
 */
@Data
public class ApiCatalogItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** gen_table.id */
    private Long tableId;

    private String tableName;

    private String tableComment;

    private String className;

    private String moduleName;

    /** 接口前缀，如 /api/v2-order */
    private String basePath;

    /** 该表是否已经生成过代码（有 output 记录） */
    private boolean generated;

    private List<Api> apis = new ArrayList<>();

    /**
     * 单个接口
     */
    @Data
    public static class Api implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String method;

        private String path;

        private String summary;

        /** 权限编码，如 v2Order:add；空表示不校验 */
        private String perms;

        /** 对应前端 api 方法名，便于前后端对照排查 */
        private String frontendFn;

        public Api(String method, String path, String summary) {
            this.method = method;
            this.path = path;
            this.summary = summary;
        }

        public Api(String method, String path, String summary, String perms, String frontendFn) {
            this.method = method;
            this.path = path;
            this.summary = summary;
            this.perms = perms;
            this.frontendFn = frontendFn;
        }
    }
}

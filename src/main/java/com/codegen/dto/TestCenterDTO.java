package com.codegen.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * V9.2 测试中心入参。按资源分嵌套类，与 {@code I18nMessageDTO} 同款写法。
 */
public final class TestCenterDTO {

    private TestCenterDTO() {
    }

    @Data
    public static class EnvPage {
        private Long projectId;
        private String keyword;
        private Integer pageNum = 1;
        private Integer pageSize = 20;
    }

    @Data
    public static class EnvSave {
        private Long id;
        private Long projectId;
        private String name;
        private String baseUrl;
        private String headersJson;
        private String secretRef;
        private Integer isProd;
        private Integer enabled;
        private String remark;
    }

    @Data
    public static class SuitePage {
        private Long projectId;
        private String type;
        private String keyword;
        private Integer pageNum = 1;
        private Integer pageSize = 50;
    }

    @Data
    public static class SuiteSave {
        private Long id;
        private Long projectId;
        private Long tableId;
        private String name;
        private String type;
        private String source;
        private String moduleName;
        private Integer enabled;
        private String remark;
    }

    @Data
    public static class CasePage {
        private Long suiteId;
        private String source;
        private String keyword;
        private Integer pageNum = 1;
        private Integer pageSize = 50;
    }

    @Data
    public static class CaseSave {
        private Long id;
        private Long suiteId;
        private String name;
        private String source;
        private String method;
        private String path;
        private String headersJson;
        private String bodyJson;
        private String extractJson;
        private String assertsJson;
        private String cleanupSql;
        private Integer orderNo;
        private Integer enabled;
    }

    /** 发起执行 / 单用例试运行共用 */
    @Data
    public static class RunRequest {
        private Long envId;
        private Long caseId;
        private List<Long> suiteIds = new ArrayList<>();
        private String triggerType;
    }

    @Data
    public static class RunPage {
        private Long projectId;
        private String status;
        private Integer pageNum = 1;
        private Integer pageSize = 20;
    }

    /** 定时回归任务：新增/编辑共用 */
    @Data
    public static class ScheduleSave {
        private Long id;
        private Long projectId;
        private String name;
        private Long envId;
        private String suiteIds;
        private String cron;
        private Integer enabled = 1;
        private String notifyType;
        private String notifyTarget;
    }

    @Data
    public static class SchedulePage {
        private Long projectId;
        private String name;
        private Integer pageNum = 1;
        private Integer pageSize = 20;
    }
}

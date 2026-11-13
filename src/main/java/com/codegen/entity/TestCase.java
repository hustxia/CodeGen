package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * V9.2 测试用例：声明式 JSON 描述一个接口调用 + 断言集。
 *
 * <p>{@code fingerprint} 是生成器与手工修改的分界：生成时写入内容哈希，
 * 重新生成时哈希不一致就只标记「待合并」，不覆盖用户改过的用例。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_case")
public class TestCase extends BaseEntity {

    private Long suiteId;

    private String name;

    /** generated / manual */
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

    private String fingerprint;

    /** pass / fail / skip */
    private String lastResult;

    private LocalDateTime lastRunTime;
}

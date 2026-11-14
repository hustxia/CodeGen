package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * V9.2 测试套件：一个业务表通常对应一个套件。
 *
 * <p>{@code source=generated} 的套件随生成批次重建，{@code manual} 的永不覆盖；
 * 用例级别的指纹判定在 {@link TestCase#getFingerprint()} 上，不在套件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_suite")
public class TestSuite extends BaseEntity {

    private Long projectId;

    private Long tableId;

    private String name;

    /** api / ui */
    private String type;

    /** generated / manual */
    private String source;

    private String moduleName;

    private Integer enabled;

    private String remark;
}

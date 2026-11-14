package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * V9.2 测试环境：一个系统可有多套（test / 预发 / 生产只读）。
 *
 * <p>{@code isProd=1} 时执行器硬拦截非 GET 用例，不靠前端隐藏按钮。
 * {@code secretRef} 只存引用（环境变量名 / 配置键），密钥本身不落库。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_env")
public class TestEnv extends BaseEntity {

    private Long projectId;

    private String name;

    private String baseUrl;

    private String headersJson;

    private String secretRef;

    private Integer isProd;

    private Integer enabled;

    private String remark;
}

package com.codegen.dto;

import com.codegen.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 运行历史分页查询。时间字段用字符串，避免前端时区把 LocalDateTime 绑定搞乱。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TcRunQuery extends PageQuery {

    private String status;
    private String triggerType;
    private String gitCommit;
    private String keyword;
    private String startTime;
    private String endTime;
}

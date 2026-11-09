package com.codegen.common.page;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分页查询入参基类
 */
@Data
public class PageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}

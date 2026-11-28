package com.codegen.dto;

import lombok.Data;

@Data
public class GenMcpLogPageDTO {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private String toolName;

    /** SUCCESS / FAILED，空为全部 */
    private String status;
}

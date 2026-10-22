package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 代码生成写盘请求 DTO
 */
@Data
public class GenWriteDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 表配置 ID 列表
     */
    private List<Long> ids;

    /**
     * 同名文件是否覆盖（默认跳过）
     */
    private Boolean overwrite;

    /**
     * 只写未漂移文件（V4.2）：磁盘内容已被手改的文件一律跳过。
     *
     * <p>覆盖模式最大的风险不是覆盖模板生成的内容，而是覆盖掉用户在块外手写的逻辑。
     * 打开这个开关后，{@code DRIFTED} 文件按 SKIPPED 处理并在结果里说明原因，
     * 想强推就关掉它——但那时至少用户是知道自己在做什么的。
     */
    private Boolean skipDrifted;
}

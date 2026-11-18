package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 回滚请求（V4.2 P0-2）
 *
 * <p>{@code ledgerIds} / {@code paths} 都为空 = 整批回滚，保持与旧接口一致的行为。
 * 台账 ID 用字符串接收：雪花 ID 19 位，前端按 Number 传会丢精度（V3.1 踩过）。
 */
@Data
public class GenRollbackDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 要回滚的台账行 ID（gen_output.id），空 = 整批
     */
    private List<String> ledgerIds;

    /**
     * 要回滚的文件：可传相对路径或绝对路径，空 = 整批
     */
    private List<String> paths;

    /**
     * 跳过已被手改的文件（默认 false：预览里已明确告知会覆盖手改，执行按用户勾选来）
     */
    private Boolean skipDrifted;
}

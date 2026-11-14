package com.codegen.dto;

import lombok.Data;

/**
 * V9.3「验证生成产物」手动触发请求体。
 *
 * <p>与 {@link TcRunRequest} 不同：这里只接收受控的 {@code genRoot}（生成产物根目录），
 * 不引入任何命令/路径黑名单逻辑——genRoot 经由执行器注入到子进程环境变量 TC_GEN_ROOT，
 * 由 node 驱动脚本在产物内定位后端模块，不做任意命令执行。
 */
@Data
public class TcGenRunRequest {
    /** 生成产物根目录（SysProject.backendRoot + backendPath），为空则按服务端默认解析失败 → SKIP。 */
    private String genRoot;
}

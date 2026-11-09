package com.codegen.service;

import com.codegen.vo.GenSqlDeliverVO;

import java.nio.file.Path;
import java.util.List;

/**
 * SQL 交付物统一出口（V7.3 P0-2）。
 *
 * <p>V7.2 只在流水线成功后把 db.sql / seed.sql / menu.sql 复制到目标工程 {@code db/}，
 * 表配置页「写入磁盘」和下载 ZIP 走的是另一条链路，同一条生成链路会出现两种交付结果。
 * 本接口把「命名 + 覆盖前备份 + 落盘」收敛成唯一实现，三个出口只负责提供内容：
 * <ul>
 *   <li>流水线：日志目录里的 db.sql / seed.sql + 最新 .gen-artifacts 的 menu.sql</li>
 *   <li>手动写盘：按当前库结构导出 DDL + 规则 seed SQL + 最新 menu.sql</li>
 *   <li>下载 ZIP：同手动写盘的内容，作为 {@code db/} 条目打包</li>
 * </ul>
 */
public interface GenSqlDeliverService {

    /**
     * 构建目标工程 {@code db/} 下的 SQL 交付物（不落盘）。
     *
     * <p>数据源：所属系统最近一次流水线的库名（没有流水线时回落当前连接库）；
     * 表不存在或内容为空时对应文件不产出，不阻断调用方。
     */
    List<SqlArtifact> buildProjectArtifacts(Long projectId, List<Long> tableIds);

    /**
     * 统一落盘：写入 {@code <backendRoot>/<projectCode>-server/db/}。
     * 已存在文件先备份到 {@code <backendRoot>/.gen-backup/<batchNo>/<projectCode>-server/db/}。
     *
     * <p>单文件失败只记入返回值，不抛出——交付物落盘失败不应回滚已经写好的代码。
     */
    GenSqlDeliverVO deliver(Long projectId, String batchNo, List<SqlArtifact> artifacts);

    /**
     * 目标工程 db 目录（与流水线 V7.2 保持同一路径规则）。
     */
    Path dbDir(Long projectId);

    /**
     * 交付物内容：文件名（如 init_tables.sql）与 SQL 文本。
     */
    record SqlArtifact(String fileName, String content) {
    }
}

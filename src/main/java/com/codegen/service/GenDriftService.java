package com.codegen.service;

import com.codegen.vo.GenDriftSummaryVO;

import java.util.List;
import java.util.Map;

/**
 * 漂移检测（V4.2 P0-3）
 *
 * <p>{@code gen_output.md5} 从四期写进库之后一直没有消费方，等于原料躺在库里。
 * 本服务的职责是把它变成「磁盘上这份还是不是我生成的那份」这个可回答的问题，
 * 并作为「只写未漂移文件」策略的判定依据——避免一键覆盖掉用户手写的代码。
 */
public interface GenDriftService {

    /**
     * 全量扫描：每个产物路径取最新一条有 md5 的台账，与磁盘实算比对
     *
     * @param tableIds 只扫这些表（null/空 = 全部）
     */
    GenDriftSummaryVO scan(List<Long> tableIds);

    /**
     * 台账最新快照：abs_path -> 快照（仅 md5 非空的记录，SKIPPED/ERROR 不参与）
     *
     * <p>一次查完供批量调用方复用，避免逐文件查库。
     */
    Map<String, LedgerSnapshot> latestByPath();

    /**
     * 判定单一文件（diff 已读到内容时可直接用 md5 判定，省一次读盘）
     *
     * @param expectedMd5 台账 md5，空表示无台账
     * @param actualMd5   磁盘 md5，null 表示文件不存在
     */
    String judge(String expectedMd5, String actualMd5);

    /**
     * 台账快照（写盘动作 + 内容指纹 + 批次）
     */
    record LedgerSnapshot(Long id, Long tableId, String batchNo, String tableName, String relPath,
                          String action, String md5, String backupPath, String prevMd5) {
    }

    String SAME = "SAME";
    String DRIFTED = "DRIFTED";
    String MISSING = "MISSING";
    String UNKNOWN = "UNKNOWN";
}

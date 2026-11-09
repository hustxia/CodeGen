package com.codegen.service;

import com.codegen.vo.GenLedgerHealthVO;

import java.util.Map;

/**
 * 台账巡检与备份保留（V4.2 P1）
 */
public interface GenLedgerService {

    /**
     * 台账健康检查：孤儿记录 / 备份缺失 / 重复记录 / 漂移数
     */
    GenLedgerHealthVO health();

    /**
     * 清理过期备份目录（{@code .gen-backup/<batchNo>/}）
     *
     * <p>只删「既不在最近 N 个批次内、又超过保留天数」的目录——两个条件都满足才动，
     * 避免刚生成的批次因为时钟/命名问题被误删。
     *
     * @return 删除的目录数与释放字节数
     */
    Map<String, Object> cleanupBackups();
}

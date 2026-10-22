package com.codegen.service;

import com.codegen.dto.GenApplyDTO;
import com.codegen.dto.GenRollbackDTO;
import com.codegen.vo.GenApplyVO;
import com.codegen.vo.GenRollbackVO;

import java.util.List;
import java.util.Map;

/**
 * 一键生成：把 V1.1 里靠人记顺序的四个动作串成一条流水线。
 *
 * <p>顺序不是随便定的：路由文件指向的 {@code views/<module>/index.vue} 必须先落盘，
 * 否则菜单点进去 404。所以固定为 自检 → 备份 → 写盘 →（编译）→ 注册菜单 → 重建路由。
 */
public interface GenApplyService {

    /**
     * 执行一键生成
     */
    GenApplyVO apply(GenApplyDTO dto);

    /**
     * 回滚某个批次（整批）：有备份的还原，首次生成的文件删除
     *
     * <p>保留旧签名是为了不破坏既有调用；内部走 {@link #rollback(String, GenRollbackDTO)}。
     *
     * @return 还原/删除/跳过的文件数
     */
    Map<String, Object> rollback(String batchNo);

    /**
     * 回滚某个批次（V4.2）：支持只回滚部分文件，且覆盖模式（MERGED/OVERWRITTEN）也能还原。
     *
     * <p>数据源统一为 {@code gen_output}——原来查 {@code gen_history}，而那张表只记
     * WRITTEN/MERGED 且没有 project_id/md5，导致「有备份的覆盖文件」反而最不可回滚。
     *
     * @param dto 为 null 或未指定文件 = 整批
     */
    GenRollbackVO rollback(String batchNo, GenRollbackDTO dto);

    /**
     * 回滚预览（dry-run）：不落盘，只返回「将还原 N / 将删除 M / 将跳过 K」与冲突明细
     */
    GenRollbackVO rollbackPreview(String batchNo, GenRollbackDTO dto);

    /**
     * 最近的写盘批次（数据源 gen_output）
     */
    List<Map<String, Object>> recentBatches(int limit);
}

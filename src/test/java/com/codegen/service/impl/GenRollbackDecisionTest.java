package com.codegen.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 回滚动作判定：全分支覆盖。
 *
 * <p>背景：V4.2 之前回滚只处理 WRITTEN（{@code GenApplyServiceImpl:228} 一行 continue 跳过 MERGED），
 * 而 MERGED 恰恰是「手写代码与生成代码混在一起」的覆盖场景——94 个文件备份都在，
 * 回滚却视而不见。现在判定收敛成 {@code decideOp} 这个纯函数，本测试把四条规则钉死：
 *
 * <ol>
 *   <li>有备份 → 还原（覆盖模式也能救回来）</li>
 *   <li>首生成 + 未改 → 删除</li>
 *   <li>首生成 + 已改 → 跳过（删掉就是丢用户内容）</li>
 *   <li>覆盖类 + 备份丢 → 跳过（没有备份还硬还原，等于什么都没做却报成功）</li>
 * </ol>
 */
@DisplayName("回滚动作判定：有备份还原 / 首生成删除 / 手改不删 / 备份缺失不硬来")
class GenRollbackDecisionTest {

    @Test
    @DisplayName("MERGED 且有备份 → RESTORE（这正是 V4.2 修的那一类）")
    void mergedWithBackupRestores() {
        GenApplyServiceImpl.Decision d = GenApplyServiceImpl.decideOp("MERGED", true, true, false);
        assertEquals("RESTORE", d.op());
    }

    @Test
    @DisplayName("OVERWRITTEN 且有备份 → RESTORE")
    void overwrittenWithBackupRestores() {
        assertEquals("RESTORE", GenApplyServiceImpl.decideOp("OVERWRITTEN", true, true, false).op());
    }

    @Test
    @DisplayName("有备份但文件被删了 → 仍然 RESTORE（从备份恢复）")
    void restoreWhenTargetDeleted() {
        assertEquals("RESTORE", GenApplyServiceImpl.decideOp("OVERWRITTEN", true, false, false).op());
    }

    @Test
    @DisplayName("有备份且磁盘已手改 → RESTORE，但理由必须写明会覆盖手改")
    void restoreDriftedWarns() {
        GenApplyServiceImpl.Decision d = GenApplyServiceImpl.decideOp("OVERWRITTEN", true, true, true);
        assertEquals("RESTORE", d.op());
        assertEquals(true, d.reason().contains("手改"));
    }

    @Test
    @DisplayName("首生成未改 → DELETE")
    void writtenDeletes() {
        assertEquals("DELETE", GenApplyServiceImpl.decideOp("WRITTEN", false, true, false).op());
    }

    @Test
    @DisplayName("首生成但已被手改 → SKIP，不能删")
    void writtenDriftedSkips() {
        assertEquals("SKIP", GenApplyServiceImpl.decideOp("WRITTEN", false, true, true).op());
    }

    @Test
    @DisplayName("首生成的文件已不在磁盘 → SKIP，不必报错")
    void writtenMissingSkips() {
        assertEquals("SKIP", GenApplyServiceImpl.decideOp("WRITTEN", false, false, false).op());
    }

    @Test
    @DisplayName("覆盖类记录但备份丢失 → SKIP，不能假装还原成功")
    void overwriteWithoutBackupSkips() {
        assertEquals("SKIP", GenApplyServiceImpl.decideOp("OVERWRITTEN", false, true, false).op());
        assertEquals("SKIP", GenApplyServiceImpl.decideOp("MERGED", false, true, false).op());
    }

    @Test
    @DisplayName("回滚批次自身的记录（ROLLBACK）也有备份 → 可再回滚")
    void rollbackRecordIsRollbackable() {
        assertEquals("RESTORE", GenApplyServiceImpl.decideOp("ROLLBACK", true, true, false).op());
    }
}

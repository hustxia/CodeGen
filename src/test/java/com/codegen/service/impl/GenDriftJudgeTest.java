package com.codegen.service.impl;

import com.codegen.service.GenDriftService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 漂移判定：三态 + 两个边界。
 *
 * <p>背景：{@code gen_output.md5} 从四期开始写，V4.2 之前全库零消费方。
 * 现在用它判断「磁盘上这份还是我生成的那份吗」，判定必须严格：
 * 误报 DRIFTED 会让用户学会忽略这个提示，那等于没做。
 *
 * <p>两个边界最容易写错：
 * <ul>
 *   <li>台账没有 md5（从未生成过 / 本次被 SKIPPED）→ UNKNOWN，不能算 DRIFTED；</li>
 *   <li>文件不存在 → MISSING，不能算 DRIFTED（否则「删了文件」和「改了文件」混在一起）。</li>
 * </ul>
 */
@DisplayName("漂移判定：SAME / DRIFTED / MISSING / UNKNOWN")
class GenDriftJudgeTest {

    private final GenDriftService service = new GenDriftServiceImpl();

    @Test
    @DisplayName("指纹一致 → SAME")
    void sameWhenEqual() {
        assertEquals(GenDriftService.SAME,
                service.judge("0cc175b9c0f1b6a831c399e269772661", "0cc175b9c0f1b6a831c399e269772661"));
    }

    @Test
    @DisplayName("指纹不一致 → DRIFTED")
    void driftedWhenDifferent() {
        assertEquals(GenDriftService.DRIFTED,
                service.judge("0cc175b9c0f1b6a831c399e269772661", "92eb5ffee6ae2fec3ad71c777531578f"));
    }

    @Test
    @DisplayName("大小写不同不算漂移（DB 里历史数据可能大写）")
    void caseInsensitive() {
        assertEquals(GenDriftService.SAME,
                service.judge("0CC175B9C0F1B6A831C399E269772661", "0cc175b9c0f1b6a831c399e269772661"));
    }

    @Test
    @DisplayName("文件不存在 → MISSING，不是 DRIFTED")
    void missingWhenNoFile() {
        assertEquals(GenDriftService.MISSING,
                service.judge("0cc175b9c0f1b6a831c399e269772661", null));
    }

    @Test
    @DisplayName("台账没 md5 → UNKNOWN，不能误报漂移")
    void unknownWhenNoLedgerMd5() {
        assertEquals(GenDriftService.UNKNOWN, service.judge(null, "92eb5ffee6ae2fec3ad71c777531578f"));
        assertEquals(GenDriftService.UNKNOWN, service.judge("", "92eb5ffee6ae2fec3ad71c777531578f"));
    }
}

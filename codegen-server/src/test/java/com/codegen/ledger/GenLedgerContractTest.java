package com.codegen.ledger;

import com.codegen.dto.GenWriteDTO;
import com.codegen.entity.GenOutput;
import com.codegen.vo.GenDiffVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 台账契约：字段齐全 + 只有一个写入口。
 *
 * <p>背景：V4.2 之前同一个事实写两张表，字段还互补——
 * {@code gen_history} 有 backup_path 没 project_id/md5，{@code gen_output} 反过来，
 * 两表 collation 不一致到连 UNION 都报 {@code ERROR 1271}。
 * 回滚查 A 表、漂移需要 B 表的 md5，于是「有备份的文件反而不可回滚」。
 *
 * <p>本测试守两条，防止有人日后「顺手」再把第二套台账加回来：
 * <ol>
 *   <li>回滚与漂移要用到的列必须在 {@link GenOutput} 上；</li>
 *   <li>源码里不允许再出现写 {@code gen_history} 的语句。</li>
 * </ol>
 */
@DisplayName("台账契约：backup_path/prev_md5 同源，且只有 gen_output 一个写入口")
class GenLedgerContractTest {

    @Test
    @DisplayName("GenOutput 具备回滚与漂移所需的四个关键字段")
    void outputHasLedgerFields() {
        assertHasField(GenOutput.class, "backupPath");
        assertHasField(GenOutput.class, "prevMd5");
        assertHasField(GenOutput.class, "md5");
        assertHasField(GenOutput.class, "projectId");
    }

    @Test
    @DisplayName("写盘 DTO 支持「只写未漂移」策略，差异 VO 带漂移状态")
    void dtoAndVoExposeDrift() {
        assertHasField(GenWriteDTO.class, "skipDrifted");
        assertHasField(GenDiffVO.class, "drift");
    }

    @Test
    @DisplayName("主源码里不再有 INSERT gen_history（台账已合一）")
    void noSecondLedgerWrite() throws IOException {
        Path src = Paths.get("src/main/java");
        assertTrue(Files.isDirectory(src), "找不到源码目录，测试工作目录应为本模块根：" + src.toAbsolutePath());
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(src)) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
                String content = Files.readString(file);
                String lower = content.toLowerCase();
                if (lower.contains("insert into gen_history")
                        || lower.contains("update gen_history")
                        || lower.contains("delete from gen_history")) {
                    offenders.add(src.relativize(file).toString());
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "台账已统一到 gen_output，以下文件仍在写 gen_history：" + offenders);
    }

    private static void assertHasField(Class<?> type, String name) {
        Field field = null;
        try {
            field = type.getDeclaredField(name);
        } catch (NoSuchFieldException ignored) {
            // 下方断言给出可读信息
        }
        assertNotNull(field, type.getSimpleName() + " 缺少字段 " + name);
    }
}

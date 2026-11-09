package com.codegen.service.impl;

import com.codegen.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第五轮走查契约：一键建工程的写盘路径必须留在输出根内。
 *
 * <p>{@code backendModule/frontendModule} 与 {@code rootPackage} 都来自请求，
 * {@code Path.resolve} 遇绝对路径会丢掉左侧根、遇 {@code ../} 会逃出根；
 * scaffold 侧此前没有任何越界校验，可把约 45 个基座文件写到任意可写目录。
 * 这里只测纯函数，不连数据库、不真的写盘。
 */
@DisplayName("脚手架写盘路径越界防线")
class GenScaffoldPathSafetyTest {

    @Test
    @DisplayName("模块名拒绝 ../、绝对路径、分隔符与空值")
    void moduleMustBeSingleSegment() {
        for (String bad : new String[]{"../../tmp/pwn", "..", ".", "", "   ", "a/b", "a\\b",
                "C:/Windows/Temp/pwn", "server;rm"}) {
            assertThrows(BusinessException.class,
                    () -> GenScaffoldServiceImpl.safeModule(bad), "应拒绝的模块名：" + bad);
        }
        assertEquals("demo-server", GenScaffoldServiceImpl.safeModule(" demo-server "));
        assertEquals("demo.web", GenScaffoldServiceImpl.safeModule("demo.web"));
    }

    @Test
    @DisplayName("relPath 越界一律拒绝，合法相对路径仍在根内")
    void targetMustStayInsideRoot() {
        Path root = Path.of(System.getProperty("java.io.tmpdir"), "codegen-root")
                .toAbsolutePath().normalize();
        for (String bad : new String[]{"../../../../pwn/pom.xml", "../outside/pom.xml",
                "src/../../outside/App.java", "/etc/passwd"}) {
            assertThrows(BusinessException.class,
                    () -> GenScaffoldServiceImpl.safeTarget(root, bad), "应拒绝的相对路径：" + bad);
        }
        Path ok = GenScaffoldServiceImpl.safeTarget(root, "src/main/java/com/demo/App.java");
        assertTrue(ok.startsWith(root), "合法生成物必须落在输出根内");
        assertEquals(root.resolve("src/main/java/com/demo/App.java"), ok);
    }

    @Test
    @DisplayName("根路径本身带 .. 时合法子路径不误判（坑库坑 51 同源）")
    void normalizedRootDoesNotFalsePositive() {
        Path messy = Path.of(System.getProperty("java.io.tmpdir"), "codegen-root", "sub", "..")
                .toAbsolutePath();
        Path ok = GenScaffoldServiceImpl.safeTarget(messy, "src/main/java/App.java");
        assertTrue(ok.startsWith(messy.normalize()), "两侧都 normalize，不应把合法路径误判为越界");
    }
}

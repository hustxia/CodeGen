package com.codegen.testcenter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V9.2 产物路径白名单：防目录穿越、绝对路径越界、不存在文件。 */
class TcPathGuardTest {

    @TempDir
    Path temp;

    @Test
    void allowsFileInsideRunDirectory() throws Exception {
        Path work = Files.createDirectories(temp.resolve("TC001"));
        Path log = Files.writeString(work.resolve("S1.log"), "PASS");
        assertEquals(log.toRealPath(), TcPathGuard.resolve(work, log.toString()));
    }

    @Test
    void rejectsParentTraversal() throws Exception {
        Path work = Files.createDirectories(temp.resolve("TC002"));
        Files.writeString(temp.resolve("secret.log"), "secret");
        assertThrows(SecurityException.class,
                () -> TcPathGuard.resolve(work, work.resolve("../../secret.log").toString()));
        assertThrows(SecurityException.class,
                () -> TcPathGuard.resolve(work, "../TC001/S1.log"));
    }

    @Test
    void rejectsAbsolutePathOutsideRunDirectory() throws Exception {
        Path work = Files.createDirectories(temp.resolve("TC003"));
        Path outside = Files.writeString(temp.resolve("outside.log"), "x");
        assertThrows(SecurityException.class, () -> TcPathGuard.resolve(work, outside.toString()));
    }

    @Test
    void rejectsMissingFileAndEmptyPath() throws Exception {
        Path work = Files.createDirectories(temp.resolve("TC004"));
        assertThrows(SecurityException.class, () -> TcPathGuard.resolve(work, work.resolve("nope.log").toString()));
        assertThrows(SecurityException.class, () -> TcPathGuard.resolve(work, ""));
        assertThrows(SecurityException.class, () -> TcPathGuard.resolve(null, "x"));
    }

    @Test
    void relativeLogPathNeverLeaksAbsoluteMachinePath() throws Exception {
        Path work = Files.createDirectories(temp.resolve("TC20261113-001"));
        Path log = work.resolve("S4.log");
        String rel = TcPathGuard.relativeLogPath(work, log);
        assertEquals("TC20261113-001/S4.log", rel);
        assertTrue(!rel.contains(":\\"));
    }
}

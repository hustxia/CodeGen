package com.codegen.service;

import com.codegen.common.exception.BusinessException;
import com.codegen.config.DocProperties;
import com.codegen.entity.SysDoc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V6.9 文档中心基线：预览回落判据、扩展名口径、落盘路径安全。
 *
 * <p>这三块是「判错了就静默出错」的位置：回落判据错了前端拿不到渲染器、
 * 扩展名口径错了整个白名单失效、路径校验错了就是任意文件读取。
 */
class DocCenterBaselineTest {

    @TempDir
    Path tempDir;

    private DocProperties props(String engine) {
        DocProperties p = new DocProperties();
        p.setUploadDir(tempDir.toString());
        p.setAllowedExts("pdf,doc,docx,xls,xlsx,ppt,pptx");
        p.getPreview().setEngine(engine);
        p.getPreview().setCacheDir(tempDir.resolve(".preview").toString());
        return p;
    }

    private DocPreviewEngine engine(String engineName, String sofficePath) {
        DocProperties p = props(engineName);
        DocStorage storage = new DocStorage();
        ReflectionTestUtils.setField(storage, "props", p);
        try {
            storage.afterPropertiesSet();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        DocPreviewEngine engine = new DocPreviewEngine();
        ReflectionTestUtils.setField(engine, "props", p);
        ReflectionTestUtils.setField(engine, "storage", storage);
        ReflectionTestUtils.setField(engine, "sofficePath", sofficePath);
        // 不调 afterPropertiesSet：避免在单测里真的去探测/启动 soffice
        return engine;
    }

    private SysDoc doc(String ext) {
        SysDoc d = new SysDoc();
        d.setExt(ext);
        d.setRelativePath("202610/abc." + ext);
        d.setFileSize(100L);
        return d;
    }

    @Test
    void extOfIgnoresCaseAndDot() {
        assertEquals("pdf", DocStorage.extOf("方案.PDF"));
        assertEquals("docx", DocStorage.extOf("报告.v2.docx"));
        assertEquals("", DocStorage.extOf("无扩展名"));
        assertEquals("", DocStorage.extOf("结尾点."));
        assertEquals("", DocStorage.extOf(null));
    }

    @Test
    void allowedExtSetStripsDotAndLowercases() {
        DocProperties p = new DocProperties();
        p.setAllowedExts("PDF, .Docx ,doc");
        Set<String> set = p.allowedExtSet();
        assertTrue(set.contains("pdf"));
        assertTrue(set.contains("docx"));
        assertTrue(set.contains("doc"));
        assertEquals(3, set.size());
    }

    @Test
    void defaultWhitelistExcludesScriptCarriers() {
        Set<String> set = new DocProperties().allowedExtSet();
        assertFalse(set.contains("svg"), "svg 可含脚本，叠加直链即成存储型 XSS");
        assertFalse(set.contains("html"));
        assertFalse(set.contains("md"));
        assertTrue(set.contains("pdf"));
        assertTrue(set.contains("docx"));
    }

    @Test
    void browserEngineModeMatrix() {
        DocPreviewEngine e = engine("browser", null);
        assertEquals(DocPreviewEngine.Mode.PDF, e.modeOf(doc("pdf")));
        assertEquals(DocPreviewEngine.Mode.BROWSER_DOCX, e.modeOf(doc("docx")));
        assertEquals(DocPreviewEngine.Mode.UNSUPPORTED, e.modeOf(doc("doc")));
        assertEquals(DocPreviewEngine.Mode.UNSUPPORTED, e.modeOf(doc("xlsx")));
        assertEquals("browser", e.engineName());
    }

    @Test
    void autoEngineWithoutSofficeFallsBackToBrowser() {
        DocPreviewEngine e = engine("auto", null);
        assertEquals(DocPreviewEngine.Mode.PDF, e.modeOf(doc("pdf")));
        assertEquals(DocPreviewEngine.Mode.BROWSER_DOCX, e.modeOf(doc("docx")));
        assertEquals(DocPreviewEngine.Mode.UNSUPPORTED, e.modeOf(doc("doc")));
        assertEquals("browser", e.engineName());
    }

    @Test
    void autoEngineWithSofficeConvertsEverything() {
        DocPreviewEngine e = engine("auto", "/fake/soffice");
        assertEquals(DocPreviewEngine.Mode.PDF, e.modeOf(doc("docx")));
        assertEquals(DocPreviewEngine.Mode.PDF, e.modeOf(doc("doc")));
        assertEquals(DocPreviewEngine.Mode.PDF, e.modeOf(doc("xlsx")));
        assertEquals("libreoffice", e.engineName());
    }

    @Test
    void forcedLibreofficeWithoutSofficeMustNotPretendToWork() {
        DocPreviewEngine e = engine("libreoffice", null);
        // docx 能回落 docx-preview，仍然可看；但绝不能报 PDF，否则前端拿到 404 的转换结果
        assertEquals(DocPreviewEngine.Mode.BROWSER_DOCX, e.modeOf(doc("docx")), "缺 soffice 时 docx 必须回落浏览器渲染");
        // 只有 LibreOffice 能渲染的格式，此时只能不支持
        assertEquals(DocPreviewEngine.Mode.UNSUPPORTED, e.modeOf(doc("doc")));
        String reason = e.unsupportedReason(doc("doc"));
        assertTrue(reason.contains("soffice"), "原因必须指出探测不到 soffice，否则运维以为高保真生效了");
    }

    @Test
    void docReasonMentionsDownloadWhenBrowserOnly() {
        DocPreviewEngine e = engine("browser", null);
        String reason = e.unsupportedReason(doc("doc"));
        assertTrue(reason.contains("下载"));
    }

    @Test
    void storeWritesMonthDirAndUuidName() throws Exception {
        DocStorage storage = new DocStorage();
        ReflectionTestUtils.setField(storage, "props", props("browser"));
        storage.afterPropertiesSet();

        MockMultipartFile file = new MockMultipartFile("file", "需求说明.pdf",
                "application/pdf", "hello".getBytes());
        String relative = storage.store(file, "pdf");

        assertTrue(relative.matches("\\d{6}/[0-9a-f]{32}\\.pdf"), "落盘路径必须是 yyyyMM/uuid.ext：" + relative);
        Path stored = storage.resolve(relative);
        assertEquals("hello", Files.readString(stored));
    }

    @Test
    void resolveRejectsTraversalAndMissing() throws Exception {
        DocStorage storage = new DocStorage();
        ReflectionTestUtils.setField(storage, "props", props("browser"));
        storage.afterPropertiesSet();

        BusinessException up = assertThrows(BusinessException.class,
                () -> storage.resolve("../../etc/passwd"));
        assertEquals(404, up.getCode());

        BusinessException missing = assertThrows(BusinessException.class,
                () -> storage.resolve("202610/not-exists.pdf"));
        assertEquals(404, missing.getCode());
    }
}

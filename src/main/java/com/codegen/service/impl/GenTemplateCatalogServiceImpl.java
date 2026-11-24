package com.codegen.service.impl;

import com.codegen.service.GenTemplateCatalogService;
import com.codegen.support.GenTemplateImpactSupport;
import com.codegen.vo.GenTemplateListItemVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * 模板文件清单服务实现：扫描 classpath 上的模板目录，返回相对路径 + 引用次数。
 *
 * <p>只读：只读取 classpath 资源与 Java 源码（可解析时），不碰数据库、不写盘。
 */
@Service
public class GenTemplateCatalogServiceImpl implements GenTemplateCatalogService {

    private static final Logger log = LoggerFactory.getLogger(GenTemplateCatalogServiceImpl.class);

    /** 需求约定的模板目录：gen-templates、scaffold、scaffold-*（未来加 .ftl 也会被扫到） */
    private static final List<String> TEMPLATE_PATTERNS = List.of(
            "classpath*:gen-templates/*.ftl",
            "classpath*:scaffold/**/*.ftl",
            "classpath*:scaffold-*/**/*.ftl");

    /** 单个 Java 源码文件上限，避免误读超大文件拖慢预览 */
    private static final long MAX_JAVA_SOURCE_SIZE = 1024L * 1024L;

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /** Java 源码内容缓存：源码在进程生命周期内不变，算一次即可 */
    private volatile List<String> javaSourceCache;

    @Override
    public List<GenTemplateListItemVO> listTemplates() {
        Map<String, String> contents = new TreeMap<>();
        for (String pattern : TEMPLATE_PATTERNS) {
            try {
                for (Resource resource : resolver.getResources(pattern)) {
                    String relative = GenTemplateImpactSupport.templatePathFromResource(
                            resource.getURI().toString());
                    if (relative == null) {
                        continue;
                    }
                    contents.putIfAbsent(relative,
                            new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                log.warn("扫描模板目录失败 pattern={}: {}", pattern, e.getMessage());
            }
        }

        Map<String, Integer> javaRefs = javaReferenceCounts();
        List<GenTemplateListItemVO> list = new ArrayList<>(contents.size());
        for (Map.Entry<String, String> entry : contents.entrySet()) {
            String template = entry.getKey();
            String name = template.substring(template.lastIndexOf('/') + 1);
            String directory = template.substring(0, template.lastIndexOf('/'));
            String code = GenTemplateImpactSupport.templateCode(template);
            GenTemplateListItemVO item = new GenTemplateListItemVO();
            item.setTemplate(template);
            item.setDirectory(directory);
            item.setName(name);
            // 粗略引用计数：Java 代码里的 tplCode 字面量 + 其它模板对文件名的 include 引用
            item.setRefCount(javaRefs.getOrDefault(code, 0)
                    + includeReferenceCount(name, template, contents));
            list.add(item);
        }
        return list;
    }

    /**
     * 其它模板对 {@code filename} 的引用次数（<#include "xxx.ftl"> 等）。
     * 当前模板均为独立文件，通常为 0；保留给后续拆分公共片段时使用。
     */
    private int includeReferenceCount(String filename, String selfPath, Map<String, String> contents) {
        int count = 0;
        for (Map.Entry<String, String> entry : contents.entrySet()) {
            if (entry.getKey().equals(selfPath)) {
                continue;
            }
            count += countOccurrences(entry.getValue(), filename);
        }
        return count;
    }

    /**
     * Java 源码里 tplCode 字面量（如 {@code "controller"}）的出现次数。
     * 运行时只有源码目录存在（本地/IDE 启动）才有值，打包后的 jar 返回空 Map。
     */
    private Map<String, Integer> javaReferenceCounts() {
        List<String> sources = javaSources();
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (sources.isEmpty()) {
            return counts;
        }
        for (Map.Entry<String, String> entry : loadTemplateCodes().entrySet()) {
            String code = entry.getKey();
            int count = 0;
            for (String content : sources) {
                count += countOccurrences(content, "\"" + code + "\"");
            }
            if (count > 0) {
                counts.put(code, count);
            }
        }
        return counts;
    }

    /** 模板编码集合（用作 Java 字面量搜索词），复用同一个 classpath 扫描口径 */
    private Map<String, String> loadTemplateCodes() {
        Map<String, String> codes = new LinkedHashMap<>();
        for (String pattern : TEMPLATE_PATTERNS) {
            try {
                for (Resource resource : resolver.getResources(pattern)) {
                    String relative = GenTemplateImpactSupport.templatePathFromResource(
                            resource.getURI().toString());
                    String code = GenTemplateImpactSupport.templateCode(relative);
                    if (code != null) {
                        codes.putIfAbsent(code, code);
                    }
                }
            } catch (Exception e) {
                log.warn("扫描模板编码失败 pattern={}: {}", pattern, e.getMessage());
            }
        }
        return codes;
    }

    private List<String> javaSources() {
        List<String> cached = javaSourceCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (javaSourceCache != null) {
                return javaSourceCache;
            }
            List<String> contents = new ArrayList<>();
            Path root = javaSourceRoot();
            if (root != null) {
                try (Stream<Path> files = Files.walk(root)) {
                    files.filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".java"))
                            .forEach(p -> {
                                try {
                                    if (Files.size(p) <= MAX_JAVA_SOURCE_SIZE) {
                                        contents.add(Files.readString(p, StandardCharsets.UTF_8));
                                    }
                                } catch (Exception e) {
                                    // 单个文件读失败不影响整体计数
                                }
                            });
                } catch (Exception e) {
                    log.warn("扫描 Java 源码失败 root={}: {}", root, e.getMessage());
                }
            }
            javaSourceCache = contents;
            return javaSourceCache;
        }
    }

    /** 运行时源码根目录：支持从模块目录（mvn spring-boot:run）或仓库根目录启动 */
    private Path javaSourceRoot() {
        String userDir = System.getProperty("user.dir", ".");
        List<Path> candidates = List.of(
                Paths.get(userDir, "src", "main", "java"),
                Paths.get(userDir, "codegen-server", "src", "main", "java"));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static int countOccurrences(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }
}

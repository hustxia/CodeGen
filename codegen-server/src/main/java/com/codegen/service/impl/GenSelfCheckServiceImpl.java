package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codegen.config.GenSelfCheckProperties;
import com.codegen.dto.GenDownloadDTO;
import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import com.codegen.mapper.GenTableMapper;
import com.codegen.service.GenCodeService;
import com.codegen.service.GenSelfCheckService;
import com.codegen.service.GenTableColumnService;
import com.codegen.vo.GenFileVO;
import com.codegen.vo.GenSelfCheckVO;
import com.codegen.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 生成自检实现：把渲染产物中的 Java 源码用 JDK 编译器在内存里编译一遍，
 * 把「模板改坏 → 写盘后人工 mvn compile 才发现」的反馈循环从分钟级压到秒级。
 *
 * <p>同时做轻量结构检查（Vue/JS 结构、未渲染的占位符、下拉缺字典），
 * 让明显问题不需要编译器也能被发现。
 */
@Service
public class GenSelfCheckServiceImpl implements GenSelfCheckService {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");

    private static final Pattern CLASS_PATTERN = Pattern.compile("(?m)^\\s*public\\s+(?:final\\s+|abstract\\s+)?(?:class|interface|enum|record)\\s+(\\w+)");

    /**
     * 产物测试类通常是包级私有（{@code class XxxControllerTest}），不能复用只匹配 public 的 CLASS_PATTERN，
     * 否则整批测试源都会因为「解析不出类名」被误报为错误。
     */
    private static final Pattern TEST_TYPE_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:public\\s+|protected\\s+|private\\s+)?(?:static\\s+)?(?:final\\s+|abstract\\s+)?"
                    + "(?:class|interface|enum|record)\\s+(\\w+)");

    /**
     * 产物测试专属依赖（test-scope）：基座 classpath 快照通常不含这些包。
     * 自检编译产物测试时，命中这些符号缺失只降级为 WARNING，不把「基座缺 test 依赖」误报成生成器缺陷。
     */
    private static final String[] TEST_SCOPE_MARKERS = {
            "org.junit", "org.mockito", "org.springframework.test", "org.springframework.boot.test",
            "org.hamcrest", "com.h2database", "org.assertj", "org.testcontainers",
            "org.springframework.security.test", "MockMvc", "SpringBootTest", "TestRestTemplate",
            "Mockito", "Assertions", "junit"
    };

    /**
     * import 语句里的全限定类名
     */
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s+([\\w.]+(?:\\.\\*)?)\\s*;");

    /**
     * 生成页里的权限指令：v-hasPermi="['demo_goods:add']"
     */
    private static final Pattern HAS_PERMI_PATTERN = Pattern.compile("v-hasPermi\\s*=\\s*\"([^\"]*)\"");

    /**
     * 指令里的权限码字面量（单引号或双引号）
     */
    private static final Pattern PERMS_TOKEN_PATTERN = Pattern.compile("'([^']+)'|\"([^\"]+)\"");

    @Autowired
    private GenSelfCheckProperties selfCheckProperties;

    @Autowired
    private com.codegen.config.GenOutputProperties outputProperties;

    @Autowired
    private com.codegen.mapper.SysProjectMapper sysProjectMapper;

    /**
     * 取本批表所属系统的 classpath。
     *
     * <p>一批里可能混了不同系统的表，这种情况不能悄悄用第一张表的配置——
     * 会给出误导性的「通过」。混选时直接返回空，回落到全局配置，
     * 并在结果里提示一句，让用户自己拆开自检。
     */
    private String projectClasspathOf(List<Long> ids) {
        com.codegen.entity.SysProject project = projectOfIds(ids);
        return project == null ? null : project.getSelfcheckClasspath();
    }

    /**
     * 取本批表共同所属的系统；混选多个系统或都未绑定时返回 null（回落全局配置）。
     */
    private com.codegen.entity.SysProject projectOfIds(List<Long> ids) {
        java.util.Set<Long> projectIds = new java.util.LinkedHashSet<>();
        for (GenTable table : genTableMapper.selectBatchIds(ids)) {
            if (table.getProjectId() != null) {
                projectIds.add(table.getProjectId());
            }
        }
        if (projectIds.isEmpty() || projectIds.size() > 1) {
            return null;
        }
        return sysProjectMapper.selectById(projectIds.iterator().next());
    }

    @Autowired
    private GenCodeService genCodeService;

    @Autowired
    private GenTableColumnService columnService;

    @Autowired
    private GenTableMapper genTableMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * BaseEntity 字段 -> 数据库列名与建议类型
     */
    private static final Map<String, String[]> BASE_ENTITY_COLUMNS = new LinkedHashMap<>();

    static {
        BASE_ENTITY_COLUMNS.put("createTime", new String[]{"create_time", "datetime"});
        BASE_ENTITY_COLUMNS.put("updateTime", new String[]{"update_time", "datetime"});
        BASE_ENTITY_COLUMNS.put("createBy", new String[]{"create_by", "bigint"});
        BASE_ENTITY_COLUMNS.put("updateBy", new String[]{"update_by", "bigint"});
        BASE_ENTITY_COLUMNS.put("deleted", new String[]{"deleted", "tinyint"});
    }

    @Override
    public GenSelfCheckVO check(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "请先勾选要自检的表");
        }
        long started = System.currentTimeMillis();
        GenSelfCheckVO result = new GenSelfCheckVO();
        GenDownloadDTO dto = new GenDownloadDTO();
        dto.setIds(ids);
        List<GenFileVO> files = genCodeService.preview(dto);
        result.setFileCount(files.size());

        // 字段/表注释本身可能写着 "${var}" 这类示例文本，属于数据而非未渲染模板，先收集起来用于排除
        List<GenTableColumn> columns = listColumns(ids);
        List<String> dataTexts = collectDataTexts(columns);

        List<JavaSource> javaSources = new ArrayList<>();
        List<JavaSource> testSources = new ArrayList<>();
        for (GenFileVO file : files) {
            String content = file.getContent() == null ? "" : file.getContent();
            String path = file.getPath();
            // 1) 模板占位符没被替换：模板语法错误或变量名拼错
            //    .js 里 ${id} 是有意的 JS 模板字符串，不参与该检查
            if (!path.endsWith(".js")) {
                checkPlaceholderResidue(result, path, content, dataTexts);
            }
            if (path.endsWith(".java")) {
                // V7.3（P0-1）：src/test/java 不再只计数——单独用「产物测试 classpath」编译，
                // 与主编译隔离（测试依赖 test-scope 包，混进主编译会污染结果）。
                if (isGeneratedTestSource(path)) {
                    result.setTestCount(result.getTestCount() + 1);
                    testSources.add(new JavaSource(path, content));
                } else {
                    javaSources.add(new JavaSource(path, content));
                }
            } else if (path.endsWith(".vue")) {
                checkVue(result, path, content);
            } else if (path.endsWith(".js")) {
                checkJs(result, path, content);
            }
        }
        result.setJavaCount(javaSources.size());

        // 2) 真实编译（带上所属系统：源码根/编译产物/包名都按系统解析，多系统下不再回落全局）
        com.codegen.entity.SysProject project = projectOfIds(ids);
        String projectClasspath = project == null ? null : project.getSelfcheckClasspath();
        compile(result, javaSources, projectClasspath, project);
        // 2.1) 产物测试独立编译：语法/符号错误按文件+行号报 ERROR，测试依赖缺口降级 WARNING
        compileTests(result, testSources, javaSources, projectClasspath, project);

        // 3) 语义提示：下拉/单选没有字典，生成出来的控件是空选项
        checkDictBinding(result, columns);

        // 4) 结构契约：实体映射的列必须真实存在于表中（编译期查不出，运行期才炸）
        checkEntityContract(result, files, ids);

        // 5) 权限一致性：页面里 v-hasPermi 引用的权限码，必须与菜单注册将生成的权限码对得上
        checkPermsConsistency(result, files, ids, columns);

        result.setElapsedMs(System.currentTimeMillis() - started);
        result.setPassed(result.getErrorCount() == 0);
        return result;
    }

    /**
     * 从源码里收集 import 的类名（只取以工程根包开头的，第三方库交给 classpath）。
     *
     * <p>根包按所属系统解析：系统配了 rootPackage 用系统的，否则回落全局。
     * 用全局根包判断多系统产物会漏收基座类（如 com.wms.common.*），
     * 这些类本该作为编译单元一起参与编译。
     */
    private void collectImportedTypes(String content, Set<String> out, String rootPkg) {
        Matcher m = IMPORT_PATTERN.matcher(content);
        while (m.find()) {
            String fqcn = m.group(1);
            if (fqcn.endsWith(".*")) {
                continue;
            }
            if (rootPkg != null && !rootPkg.isEmpty() && fqcn.startsWith(rootPkg + ".")) {
                out.add(fqcn);
            }
        }
    }

    /**
     * 定位产物相对路径里 src/main/java 的起始下标。
     *
     * <p>兼容两种布局：带模块前缀（{@code ecomFinance-server/src/main/java/...}）
     * 与不带（{@code src/main/java/...}，脚手架工程 backendRoot 即模块根）。
     * 返回 -1 表示不是 Java 产物路径。
     */
    private static int srcMainJavaStart(String rel) {
        if (rel.startsWith("src/main/java/")) {
            return 0;
        }
        int idx = rel.indexOf("/src/main/java/");
        return idx < 0 ? -1 : idx + 1;
    }

    /**
     * 由产物路径反推目标工程的编译产物目录（target/classes）
     */
    private List<Path> backendTargetClasses(List<JavaSource> sources, String backendRootOverride) {
        Set<Path> roots = new LinkedHashSet<>();
        for (JavaSource source : sources) {
            String rel = source.path().replace('\\', '/');
            int start = srcMainJavaStart(rel);
            if (start < 0) {
                continue;
            }
            Path moduleRoot = Path.of(effectiveBackendRoot(backendRootOverride), rel.substring(0, start));
            for (String sub : new String[]{"target/classes", "build/classes/java/main"}) {
                Path cls = moduleRoot.resolve(sub);
                if (Files.isDirectory(cls)) {
                    roots.add(cls.toAbsolutePath());
                }
            }
        }
        return new ArrayList<>(roots);
    }

    private List<Path> clsRootsOf(List<Path> classRoots) {
        return classRoots;
    }

    /**
     * 产物源码根/编译产物目录的解析基准：所属系统配了 backendRoot 用系统的，否则回落全局。
     *
     * <p>全局根固定指向 demo-target，多系统下拼 wms/财务的相对路径必不存在，
     * 基座类就这样被丢出 classpath——这就是「自检满屏 package xxx.common does not exist」的根因。
     */
    private String effectiveBackendRoot(String backendRootOverride) {
        return StringUtils.hasText(backendRootOverride) ? backendRootOverride : outputProperties.getBackendRoot();
    }

    /**
     * 目标目录里是否已有该类的 .class
     */
    private boolean hasCompiled(List<Path> classRoots, String fqcn) {
        String rel = fqcn.replace('.', '/') + ".class";
        for (Path root : classRoots) {
            if (Files.isRegularFile(root.resolve(rel))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 由产物路径反推目标工程的全部 Java 源码根
     */
    private List<Path> backendSourceRoots(List<JavaSource> sources, String backendRootOverride) {
        Set<Path> roots = new LinkedHashSet<>();
        for (JavaSource source : sources) {
            String rel = source.path().replace('\\', '/');
            int start = srcMainJavaStart(rel);
            if (start < 0) {
                continue;
            }
            Path abs = Path.of(effectiveBackendRoot(backendRootOverride),
                    rel.substring(0, start + "src/main/java".length()));
            if (Files.isDirectory(abs)) {
                roots.add(abs.toAbsolutePath());
            }
        }
        return new ArrayList<>(roots);
    }

    /**
     * 把目标工程的 Java 源码根目录追加到 classpath
     *
     * <p>javac 在 classpath 里遇到"目录 + 找不到的类"时，会按包路径去该目录下找 .java
     * 源文件并隐式编译，这样 {@code ${rootPkg}.common.*} 基座类能一起参与编译，
     * 基座类被改名/删字段导致的编译错误也就暴露出来了。
     */
    private String withBackendSourceRoot(String classpath, List<JavaSource> sources, String backendRootOverride) {
        // 从产物相对路径反推源码根：demo-server/src/main/java/com/demo/entity/X.java
        //   -> demo-server/src/main/java
        for (JavaSource source : sources) {
            String rel = source.path().replace('\\', '/');
            int start = srcMainJavaStart(rel);
            if (start < 0) {
                continue;
            }
            String srcRoot = rel.substring(0, start + "src/main/java".length());
            Path abs = Path.of(effectiveBackendRoot(backendRootOverride), srcRoot);
            if (!Files.isDirectory(abs)) {
                return classpath;
            }
            String sep = java.io.File.pathSeparator;
            String extra = abs.toAbsolutePath().toString();
            if (classpath == null || classpath.isEmpty()) {
                return extra;
            }
            // 已包含就不重复追加
            for (String part : classpath.split(java.util.regex.Pattern.quote(sep))) {
                if (part.trim().equalsIgnoreCase(extra)) {
                    return classpath;
                }
            }
            return classpath + sep + extra;
        }
        return classpath;
    }

    /**
     * 内存编译全部 Java 产物；类路径沿用当前进程，保证依赖版本与运行时一致
     */
    private void compile(GenSelfCheckVO result, List<JavaSource> sources, String projectClasspath,
                         com.codegen.entity.SysProject project) {
        if (sources.isEmpty()) {
            return;
        }
        if (Boolean.FALSE.equals(selfCheckProperties.getCompileEnabled())) {
            result.setCompilerAvailable(false);
            result.addIssue("javac", 0L, "WARNING",
                    "gen.selfcheck.compile-enabled=false，已跳过编译检查（仅做结构与权限检查）");
            return;
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            result.setCompilerAvailable(false);
            result.addIssue("javac", 0L, "WARNING",
                    "当前运行环境没有 JDK（仅 JRE），已跳过编译检查；用 JDK 启动 admin 可启用完整自检");
            return;
        }
        result.setCompilerAvailable(true);

        // V3.0：系统自带 classpath 优先。多系统下各系统依赖版本不同，
        // 共用一份会出现「依赖明明在却报 cannot find symbol」，比不配还难排查。
        String classpath = baseClasspath(projectClasspath);
        if (selfCheckProperties.classpathLooksNested() && !StringUtils.hasText(projectClasspath)) {
            // V9.2 修正（核心风险 #5）：肥 jar 启动且未配置 gen.selfcheck.classpath 时，
            // 嵌套依赖无法被 java.class.path 覆盖，编译必满屏 cannot find symbol——
            // 这会产生「环境性误报」把门禁判成失败，反而让正常产物过不了自检。
            // 不再硬跑注定失败的编译，改为诚实跳过并明确告警，由 operator 配置 classpath 启用完整自检。
            result.setCompilerAvailable(false);
            result.addIssue("javac", 0L, "WARNING",
                    "当前进程以肥 jar 启动且未配置 gen.selfcheck.classpath，无法解析嵌套依赖，"
                            + "已跳过编译检查（仅做结构与权限检查）。建议配置 gen.selfcheck.classpath 指向依赖目录（支持 lib/*）以启用完整自检");
            return;
        }

        // 所属系统的根目录/根包：产物源码根与基座类都按系统解析，不再回落全局
        //（全局根是 demo-target，拼 wms/财务的相对路径必不存在，基座类就这样丢出 classpath）
        String backendRootOverride = project != null && StringUtils.hasText(project.getBackendRoot())
                ? project.getBackendRoot().trim() : null;
        String rootPkg = project != null && StringUtils.hasText(project.getRootPackage())
                ? project.getRootPackage().trim() : outputProperties.getRootPackage();

        // 生成物大量 import 目标工程的基座类（${rootPkg}.common.*），但那部分源码不属于
        // 本次生成产物。这里把目标工程【已编译的 target/classes】挂到 classpath；
        // 只有在完全没有编译产物时才退而求其次挂源码根（会隐式编译，Lombok 可能不生效）。
        List<Path> classRoots = backendTargetClasses(sources, backendRootOverride);
        if (classRoots.isEmpty()) {
            classpath = withBackendSourceRoot(classpath, sources, backendRootOverride);
        } else {
            for (Path clsRoot : classRoots) {
                classpath = classpath.isEmpty()
                        ? clsRoot.toString()
                        : classpath + java.io.File.pathSeparator + clsRoot;
            }
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        Path outDir = null;
        try {
            outDir = Files.createTempDirectory("gen-self-check-");
            List<String> options = new ArrayList<>(List.of(
                    "-classpath", classpath,
                    "-d", outDir.toString(),
                    "-encoding", "UTF-8",
                    "-nowarn"));
            if (Runtime.version().feature() >= 21) {
                // JDK 21 起默认不再隐式跑注解处理器，Lombok 不会生成 getter/setter
                options.add("-proc:full");
            }
            // 处理器路径单独给：只靠 -classpath 时，JDK 21 有时探测不到 lombok 的 processor，
            // 结果就是"明明有 lombok 却满屏 cannot find symbol getXxx()"。显式指定最稳。
            // 全局找不到处理器时，再从实际使用的（系统自带）classpath 里找——
            // 肥 jar 启动时全局 classpath 没有 lombok，而 demo-lib/* 里有。
            String processorPath = selfCheckProperties.resolveProcessorPath();
            if (!StringUtils.hasText(processorPath)) {
                processorPath = selfCheckProperties.lombokSegmentsOf(classpath);
            }
            if (StringUtils.hasText(processorPath)) {
                options.add("-processorpath");
                options.add(processorPath);
            }

            List<JavaFileObject> units = new ArrayList<>();
            Map<String, String> classToPath = new HashMap<>();
            // 目标工程里被生成物引用的类（基座类、同批生成的其他表），也作为显式编译单元加入。
            // 不能只靠 -classpath 隐式加载：JDK 的隐式编译不参与注解处理，Lombok 不生效，
            // 会误报 cannot find symbol getXxx()。
            Set<String> externalClassNames = new LinkedHashSet<>();
            for (JavaSource source : sources) {
                String className = classNameOf(source.content());
                if (className == null) {
                    result.addIssue(source.path(), 0L, "ERROR", "无法从源码中解析出类名（package/class 声明缺失）");
                    continue;
                }
                units.add(new StringSource(className, source.content()));
                classToPath.put(className, source.path());
                collectImportedTypes(source.content(), externalClassNames, rootPkg);
            }
            if (units.isEmpty()) {
                return;
            }

            // 把 import 到的、属于目标工程源码根的类补成编译单元。
            //
            // 关键取舍：优先用 target/classes 里已编译好的 .class（已挂到 classpath），
            // 只有在没有编译产物时才回退到编译源码。
            // 原因：基座类（Result/PageQuery/BaseEntity…）自身大量依赖 Lombok 的 @Data，
            // 把它们当源码重复编译时，注解处理链路和 mvn 不完全一致，会误报
            // cannot find symbol getXxx()——明明是编译通过的代码。
            List<Path> srcRoots = new ArrayList<>(backendSourceRoots(sources, backendRootOverride));
            if (!classRoots.isEmpty()) {
                // 有已编译产物就完全信任它，不再补编译任何外部源码
                srcRoots = List.of();
            }
            for (Path srcRoot : srcRoots) {
                for (String fqcn : externalClassNames) {
                    if (classToPath.containsKey(fqcn)) {
                        continue;
                    }
                    Path javaFile = srcRoot.resolve(fqcn.replace('.', '/') + ".java");
                    if (!Files.isRegularFile(javaFile)) {
                        continue;
                    }
                    try {
                        String content = Files.readString(javaFile, StandardCharsets.UTF_8);
                        units.add(new StringSource(fqcn, content));
                        classToPath.put(fqcn, (backendRootOverride != null ? backendRootOverride : outputProperties.getBackendRoot())
                                + "/" + srcRoot.relativize(javaFile).toString()
                                        .replace('\\', '/'));
                    } catch (Exception ignored) {
                        // 读不出来就交给 classpath 兜底
                    }
                }
                // V5.0 修复（人事系统自检 12 个 cannot find symbol）：基座类互相引用
                // （Result → ResultCode），只补「被生成物 import 到的类」会漏掉链路中间人。
                // 漏掉的类走 -classpath 源码根隐式编译，而隐式编译不参与注解处理，
                // Lombok 的 @Getter/@AllArgsConstructor 全部失效，基座类自己先编译不过。
                // 这里把基座包（rootPkg）下所有源码整包补成显式编译单元，
                // 已在 classToPath 里的（生成物本身）自动跳过。
                Path pkgDir = srcRoot.resolve(rootPkg.replace('.', '/'));
                if (Files.isDirectory(pkgDir)) {
                    try (Stream<Path> walk = Files.walk(pkgDir)) {
                        for (Path javaFile : (Iterable<Path>) walk
                                .filter(p -> p.toString().endsWith(".java"))::iterator) {
                            try {
                                String content = Files.readString(javaFile, StandardCharsets.UTF_8);
                                Matcher pkgM = PACKAGE_PATTERN.matcher(content);
                                String pkg = pkgM.find() ? pkgM.group(1) : rootPkg;
                                String clsName = javaFile.getFileName().toString().replaceFirst("\\.java$", "");
                                String fqcn = pkg + "." + clsName;
                                if (classToPath.containsKey(fqcn)) {
                                    continue;
                                }
                                units.add(new StringSource(fqcn, content));
                                classToPath.put(fqcn, (backendRootOverride != null ? backendRootOverride : outputProperties.getBackendRoot())
                                        + "/" + srcRoot.relativize(javaFile).toString()
                                                .replace('\\', '/'));
                            } catch (Exception ignored) {
                                // 单个文件读不出来就交给 classpath 兜底
                            }
                        }
                    } catch (Exception ignored) {
                        // 遍历失败不影响主流程
                    }
                }
            }

            StringWriter compilerOut = new StringWriter();
            boolean ok = compiler.getTask(compilerOut, fileManager, diagnostics, options, null, units).call();
            if (!ok) {
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    if (d.getKind() != Diagnostic.Kind.ERROR) {
                        continue;
                    }
                    String file = resolveFile(d, classToPath);
                    long line = d.getLineNumber() > 0 ? d.getLineNumber() : 0L;
                    result.addIssue(file, line, "ERROR", d.getMessage(Locale.ROOT));
                }
            }
        } catch (Exception e) {
            result.addIssue("javac", 0L, "ERROR", "编译检查执行失败：" + e.getMessage());
        } finally {
            try {
                fileManager.close();
            } catch (Exception ignored) {
                // 忽略关闭异常
            }
            deleteQuietly(outDir);
        }
    }

    /**
     * 自检 classpath 的公共底座：所属系统 classpath 优先（版本以目标工程为准），平台自身 classpath 兜底。
     */
    private String baseClasspath(String projectClasspath) {
        String classpath = StringUtils.hasText(projectClasspath)
                ? GenSelfCheckProperties.expandWildcards(projectClasspath)
                : selfCheckProperties.resolveClasspath();
        return mergeWithAppClasspath(classpath, System.getProperty("java.class.path"));
    }

    private String appendClassRoots(String classpath, List<Path> roots) {
        String result = classpath;
        for (Path root : roots) {
            result = result == null || result.isEmpty()
                    ? root.toString()
                    : result + java.io.File.pathSeparator + root;
        }
        return result;
    }

    /**
     * 定位产物相对路径里 src/test/java 的起始下标；不是测试产物路径返回 -1。
     */
    static int srcTestJavaStart(String rel) {
        if (rel.startsWith("src/test/java/")) {
            return 0;
        }
        int idx = rel.indexOf("/src/test/java/");
        return idx < 0 ? -1 : idx + 1;
    }

    /**
     * 由测试产物路径反推目标工程的测试编译产物目录（target/test-classes）。
     *
     * <p>产物测试会继承基座里的 {@code LoginStateTestSupport}，它属于脚手架（不在本次预览产物里）。
     * 目标工程跑过 {@code mvn test-compile} 后该类就在 target/test-classes，挂上它测试类才能解析基类。
     */
    private List<Path> backendTestClasses(List<JavaSource> sources, String backendRootOverride) {
        Set<Path> roots = new LinkedHashSet<>();
        for (JavaSource source : sources) {
            String rel = source.path().replace('\\', '/');
            int start = srcTestJavaStart(rel);
            if (start < 0) {
                continue;
            }
            Path moduleRoot = Path.of(effectiveBackendRoot(backendRootOverride), rel.substring(0, start));
            for (String sub : new String[]{"target/test-classes", "build/classes/java/test"}) {
                Path cls = moduleRoot.resolve(sub);
                if (Files.isDirectory(cls)) {
                    roots.add(cls.toAbsolutePath());
                }
            }
        }
        return new ArrayList<>(roots);
    }

    /**
     * 产物测试编译（V7.3 P0-1）：与主编译隔离，单独用测试 classpath 编译 src/test/java。
     *
     * <p>取舍：测试类依赖 JUnit/MockMvc/H2 等 test-scope 包，基座快照通常没有。
     * 这些「依赖缺口」降级为 WARNING（并汇总成一条），语法/符号错误仍然按文件+行号报 ERROR——
     * 这样故意写坏测试能在自检阶段暴露，而不会因为基座缺测试依赖满屏假错。
     */
    private void compileTests(GenSelfCheckVO result, List<JavaSource> testSources, List<JavaSource> mainSources,
                              String projectClasspath, com.codegen.entity.SysProject project) {
        if (testSources.isEmpty()) {
            return;
        }
        if (Boolean.FALSE.equals(selfCheckProperties.getCompileEnabled())) {
            return;
        }
        // V9.3 修正（核心风险 #5 补漏）：主编译 compile() 有「肥 jar 且未配 classpath 就诚实跳过」保护，
        // 产物测试编译此前漏了同一判断。空 classpath 下 spring-beans 等【主依赖】缺失会被当成 ERROR，
        // 且不属于 TEST_SCOPE_HINTS 降级白名单，导致自检误报满屏假错、误判门禁失败并阻断写盘
        //（2026-11-14 一句话建系统流水线实测：72 错误，首条 AssetCategoryControllerTest
        //  package org.springframework.beans.factory.annotation does not exist）。
        // 与 compile() 保持一致：环境性缺依赖时跳过测试编译并降级为 WARNING，主编译结果不受影响。
        if (selfCheckProperties.classpathLooksNested() && !StringUtils.hasText(projectClasspath)
                && !StringUtils.hasText(selfCheckProperties.resolveTestClasspath())) {
            result.addIssue("产物测试", 0L, "WARNING",
                    "当前进程以肥 jar 启动且未配置 gen.selfcheck.classpath / test-classpath，无法解析嵌套依赖，"
                            + "已跳过产物测试编译检查（主编译结果不受影响）。建议配置依赖目录（支持 lib/*）以启用完整自检");
            return;
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            // compile() 已就「无 JDK」给出一次 WARNING，这里不重复
            return;
        }

        String backendRootOverride = project != null && StringUtils.hasText(project.getBackendRoot())
                ? project.getBackendRoot().trim() : null;
        String classpath = baseClasspath(projectClasspath);
        String testClasspath = selfCheckProperties.resolveTestClasspath();
        if (StringUtils.hasText(testClasspath)) {
            classpath = mergeWithAppClasspath(classpath, testClasspath);
        }
        // 主编译产物：测试类引用 Controller/Service 等生成物
        List<Path> mainClassRoots = backendTargetClasses(mainSources, backendRootOverride);
        classpath = appendClassRoots(classpath, mainClassRoots);
        // 测试编译产物：基座测试支持类（LoginStateTestSupport 等）
        classpath = appendClassRoots(classpath, backendTestClasses(testSources, backendRootOverride));
        if (mainClassRoots.isEmpty()) {
            classpath = withBackendSourceRoot(classpath, mainSources, backendRootOverride);
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        Path outDir = null;
        try {
            outDir = Files.createTempDirectory("gen-self-check-test-");
            List<String> options = new ArrayList<>(List.of(
                    "-classpath", classpath,
                    "-d", outDir.toString(),
                    "-encoding", "UTF-8",
                    "-nowarn"));
            if (Runtime.version().feature() >= 21) {
                options.add("-proc:full");
            }
            String processorPath = selfCheckProperties.resolveProcessorPath();
            if (!StringUtils.hasText(processorPath)) {
                processorPath = selfCheckProperties.lombokSegmentsOf(classpath);
            }
            if (StringUtils.hasText(processorPath)) {
                options.add("-processorpath");
                options.add(processorPath);
            }

            List<JavaFileObject> units = new ArrayList<>();
            Map<String, String> classToPath = new HashMap<>();
            for (JavaSource source : testSources) {
                String className = classNameOfTestSource(source.content());
                if (className == null) {
                    result.addIssue(source.path(), 0L, "ERROR", "无法从测试源码中解析出类名（package/class 声明缺失）");
                    continue;
                }
                units.add(new StringSource(className, source.content()));
                classToPath.put(className, source.path());
            }
            // V9.3 修正：产物测试类继承脚手架基座类 LoginStateTestSupport —— 它由 scaffold 步骤
            // 落盘到目标工程 src/test/java，但不在本次【预览生成】的 testSources 里。
            // 只编译本次预览文件会让 javac 看不到基类 → cannot find symbol，误判门禁失败并阻断写盘
            //（2026-11-14 完整系统流程实测 4 错，首条 AssetCategoryControllerTest）。
            // 显式把磁盘上已存在的基座测试源码加入编译单元：坑库 spring-boot-3 坑 28 的教训是
            // 参与编译的单元必须显式列出，别依赖 classpath 隐式编译（注解处理黑洞）。
            addScaffoldTestSources(units, classToPath, testSources, backendRootOverride, result);
            if (units.isEmpty()) {
                return;
            }

            boolean ok = compiler.getTask(new StringWriter(), fileManager, diagnostics, options, null, units).call();
            if (!ok) {
                Set<String> gapFiles = new LinkedHashSet<>();
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    if (d.getKind() != Diagnostic.Kind.ERROR) {
                        continue;
                    }
                    String message = d.getMessage(Locale.ROOT);
                    String file = resolveFile(d, classToPath);
                    long line = d.getLineNumber() > 0 ? d.getLineNumber() : 0L;
                    if (isTestDependencyGap(message)) {
                        gapFiles.add(file);
                        continue;
                    }
                    result.addIssue(file, line, "ERROR", message);
                }
                if (!gapFiles.isEmpty()) {
                    result.addIssue("产物测试", 0L, "WARNING",
                            "产物测试依赖的 test-scope 包不在自检 classpath，已降级为告警（涉及 "
                                    + gapFiles.size() + " 个文件）；可配置 gen.selfcheck.test-classpath 补齐："
                                    + String.join(", ", gapFiles));
                }
            }
        } catch (Exception e) {
            result.addIssue("产物测试", 0L, "WARNING",
                    "产物测试编译检查执行失败（已降级，不影响主编译结果）：" + e.getMessage());
        } finally {
            try {
                fileManager.close();
            } catch (Exception ignored) {
                // 忽略关闭异常
            }
            deleteQuietly(outDir);
        }
    }

    /**
     * V9.3：把目标工程磁盘上已存在的脚手架测试源码（如 {@code LoginStateTestSupport}）
     * 显式加入 javac 编译单元。按全限定类名去重，本次预览生成的同名类优先。
     */
    private void addScaffoldTestSources(List<JavaFileObject> units, Map<String, String> classToPath,
                                        List<JavaSource> testSources, String backendRootOverride,
                                        GenSelfCheckVO result) {
        String base = effectiveBackendRoot(backendRootOverride);
        Set<Path> roots = new LinkedHashSet<>();
        for (JavaSource s : testSources) {
            String rel = s.path().replace('\\', '/');
            int start = srcTestJavaStart(rel);
            if (start < 0) {
                continue;
            }
            roots.add(Path.of(base, rel.substring(0, start), "src", "test", "java"));
        }
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var walk = Files.walk(root)) {
                for (Path p : walk.filter(x -> x.toString().endsWith(".java")).toList()) {
                    String content = Files.readString(p, StandardCharsets.UTF_8);
                    String className = classNameOfTestSource(content);
                    if (className == null || classToPath.containsKey(className)) {
                        continue;
                    }
                    units.add(new StringSource(className, content));
                    classToPath.put(className, p.toString());
                }
            } catch (Exception e) {
                result.addIssue("产物测试", 0L, "WARNING", "读取脚手架测试源码失败（已降级）：" + e.getMessage());
            }
        }
    }

    /**
     * 测试源码里的类名：测试类通常是包级私有，允许省略访问修饰符。
     */
    static String classNameOfTestSource(String content) {
        Matcher pm = PACKAGE_PATTERN.matcher(content);
        Matcher cm = TEST_TYPE_PATTERN.matcher(content);
        if (!cm.find()) {
            return null;
        }
        String simple = cm.group(1);
        return pm.find() ? pm.group(1) + "." + simple : simple;
    }

    /**
     * 诊断是否属于「产物测试专属依赖缺失」：命中 test-scope 包/API 名即视为依赖缺口。
     */
    static boolean isTestDependencyGap(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        for (String marker : TEST_SCOPE_MARKERS) {
            if (lower.contains(marker.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void checkVue(GenSelfCheckVO result, String path, String content) {
        if (!content.contains("<template>") || !content.contains("</template>")) {
            result.addIssue(path, 1L, "ERROR", "缺少 <template> 根节点");
        }
        if (!content.contains("export default")) {
            result.addIssue(path, 1L, "ERROR", "缺少 export default 组件定义");
        }
        long open = countOf(content, "<el-form-item");
        long close = countOf(content, "</el-form-item>");
        if (open != close) {
            result.addIssue(path, 0L, "ERROR", "el-form-item 标签未闭合（开 " + open + " / 闭 " + close + "）");
        }
    }

    private void checkJs(GenSelfCheckVO result, String path, String content) {
        if (!content.contains("export function")) {
            result.addIssue(path, 1L, "WARNING", "api.js 没有 export function，页面导入会失败");
        }
    }

    /**
     * select/radio 未配字典时提示（生成出来的下拉是空的，能看不能用）。
     * 配了关联表（ref_table）的除外：它的选项走 /options 远端接口，不需要字典。
     */
    private void checkDictBinding(GenSelfCheckVO result, List<GenTableColumn> columns) {
        for (GenTableColumn col : columns) {
            String htmlType = col.getHtmlType();
            if (!"select".equals(htmlType) && !"radio".equals(htmlType)) {
                continue;
            }
            if (col.getRefTable() != null && !col.getRefTable().isBlank()) {
                continue;
            }
            boolean used = isOn(col.getIsForm()) || isOn(col.getIsQuery());
            if (used && (col.getDictType() == null || col.getDictType().isBlank())) {
                result.addIssue("字段配置：" + col.getColumnName(), 0L, "WARNING",
                        "控件为" + htmlType + "但未配置字典编码，下拉选项为空");
            }
        }
    }

    /**
     * 权限一致性检查（V2.0 新增，第 5 类规则）。
     *
     * <p>生成页的按钮靠 {@code v-hasPermi="['表名:动作']"} 控制显隐，而菜单注册只会生成
     * {@code 表名:list/query/add/edit/remove} 这几个权限码。两边一旦拼错（模板写成
     * {@code :create}、表名大小写不一致），表现是"按钮点了没反应/按钮永远不出现"，
     * 编译器完全查不出来。这里做双向比对：
     *
     * <ul>
     *   <li>页面引用了菜单不会注册的权限码 → 按钮永远不显示；</li>
     *   <li>菜单注册了但页面没引用 → 按钮对能进页面的人全部可见（等于没拦）。</li>
     * </ul>
     */
    /**
     * 菜单注册会为这张表生成哪些权限动作：list + 实际存在的按钮。
     *
     * <p>口径必须与 GenMenuServiceImpl.buttonsOf 一致：没有查询条件的表不生成查询按钮，
     * 关掉的新增/修改/删除按钮也不生成对应权限码。
     */
    private Set<String> expectedActions(GenTable table, List<GenTableColumn> columns) {
        Set<String> actions = new LinkedHashSet<>();
        actions.add("list");
        boolean hasQuery = columns != null && columns.stream()
                .anyMatch(c -> table.getId() != null && table.getId().equals(c.getTableId()) && isOn(c.getIsQuery()));
        if (hasQuery) {
            actions.add("query");
        }
        if (isOn(table.getGenAddBtn())) {
            actions.add("add");
        }
        if (isOn(table.getGenEditBtn())) {
            actions.add("edit");
        }
        if (isOn(table.getGenDeleteBtn())) {
            actions.add("remove");
        }
        return actions;
    }

    /**
     * 权限点前缀，与 GenMenuServiceImpl.permsPrefix / 模板 ${permsPrefix} 三方一致。
     * 任何一处规则改动都要同步另外两处，否则自检会报假警、或真实越权被漏掉。
     */
    /**
     * 一次查回表配置，避免在 ids 循环里逐条 selectById。
     *
     * <p>自检要跑十几个检查项，同一批 ids 会被反复遍历；
     * 每项都各查一遍就会变成「检查项 × 表数」次往返。
     */
    private Map<Long, GenTable> tablesById(List<Long> ids) {
        Map<Long, GenTable> byId = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return byId;
        }
        for (GenTable table : genTableMapper.selectBatchIds(ids)) {
            byId.put(table.getId(), table);
        }
        return byId;
    }

    private String permsPrefixOf(GenTable table) {
        if (table.getProjectId() == null) {
            return com.codegen.support.GenNaming.permsPrefix(null, table.getTableName());
        }
        com.codegen.entity.SysProject project = sysProjectMapper.selectById(table.getProjectId());
        String code = project == null ? null : project.getProjectCode();
        return com.codegen.support.GenNaming.permsPrefix(code, table.getTableName());
    }

    private void checkPermsConsistency(GenSelfCheckVO result, List<GenFileVO> files, List<Long> ids,
                                       List<GenTableColumn> columns) {
        Map<String, Set<String>> expected = new LinkedHashMap<>();
        Map<Long, GenTable> tableById = tablesById(ids);
        for (Long id : ids) {
            GenTable table = tableById.get(id);
            if (table == null || table.getTableName() == null) {
                continue;
            }
            // V3.0：页面里的权限码前缀是 permsPrefix（带系统编码），
            // 这里必须用同一个规则，否则每条都会误报「权限码对不上」
            expected.put(permsPrefixOf(table), expectedActions(table, columns));
        }
        Set<String> knownTables = expected.keySet();
        if (knownTables.isEmpty()) {
            return;
        }
        Map<String, Set<String>> referenced = new LinkedHashMap<>();
        Map<String, String> pageOfTable = new LinkedHashMap<>();
        for (GenFileVO file : files) {
            String path = file.getPath();
            if (path == null || !path.endsWith(".vue")) {
                continue;
            }
            String content = file.getContent() == null ? "" : file.getContent();
            Matcher hasPermi = HAS_PERMI_PATTERN.matcher(content);
            while (hasPermi.find()) {
                Matcher token = PERMS_TOKEN_PATTERN.matcher(hasPermi.group(1));
                while (token.find()) {
                    String perm = token.group(1) != null ? token.group(1) : token.group(2);
                    if (perm == null) {
                        continue;
                    }
                    int idx = perm.lastIndexOf(':');
                    if (idx <= 0) {
                        continue;
                    }
                    String tableName = perm.substring(0, idx);
                    String action = perm.substring(idx + 1);
                    if ("*".equals(tableName)) {
                        continue;
                    }
                    if (!knownTables.contains(tableName)) {
                        result.addIssue(path, 0L, "WARNING",
                                "页面引用了权限码 " + perm + "，但本次自检的表里没有 " + tableName
                                        + "；菜单注册不会生成该权限码，对应按钮永远不显示");
                        continue;
                    }
                    referenced.computeIfAbsent(tableName, k -> new LinkedHashSet<>()).add(action);
                    pageOfTable.putIfAbsent(tableName, path);
                }
            }
        }
        for (String tableName : knownTables) {
            Set<String> refs = referenced.getOrDefault(tableName, Set.of());
            if (refs.isEmpty()) {
                // 没生成页面（或页面没有任何权限指令），不判
                continue;
            }
            String page = pageOfTable.getOrDefault(tableName, tableName);
            Set<String> want = expected.getOrDefault(tableName, Set.of("list"));
            for (String action : refs) {
                if (!want.contains(action)) {
                    result.addIssue(page, 0L, "WARNING",
                            "页面引用了权限码 " + tableName + ":" + action + "，但菜单注册不会生成它（本次只会生成 "
                                    + tableName + ":" + String.join("/", want) + "）；该按钮将对所有人隐藏");
                }
            }
            for (String action : want) {
                if ("list".equals(action) || refs.contains(action)) {
                    continue;
                }
                result.addIssue(page, 0L, "WARNING",
                        "菜单注册会生成 " + tableName + ":" + action + "，但页面里没有 v-hasPermi 引用它；"
                                + "对应按钮没有权限控制（能进页面的人都看得到）");
            }
        }
    }

    /**
     * 结构契约检查：生成的实体继承 {@code BaseEntity}，MyBatis-Plus 会把这些字段
     * （create_time/update_time/create_by/update_by/deleted）拼进每一条 SQL。
     * 表里没有这些列时，编译完全正常，但第一次查询就 `Unknown column`。
     *
     * <p>这是编译期无法覆盖的盲区，只能拿 information_schema 对账。
     */
    private void checkEntityContract(GenSelfCheckVO result, List<GenFileVO> files, List<Long> ids) {
        Set<String> baseTables = new HashSet<>();
        for (GenFileVO file : files) {
            String content = file.getContent() == null ? "" : file.getContent();
            if (file.getPath() != null && file.getPath().endsWith(".java")
                    && content.contains("extends BaseEntity")) {
                baseTables.add(file.getTableName());
            }
        }
        if (baseTables.isEmpty()) {
            return;
        }
        Map<Long, GenTable> tableById = tablesById(ids);
        for (Long id : ids) {
            GenTable table = tableById.get(id);
            if (table == null || !baseTables.contains(table.getTableName())) {
                continue;
            }
            Set<String> actual = dbColumns(table.getTableName());
            if (actual.isEmpty()) {
                // 表还没建（纯设计阶段），不判
                continue;
            }
            List<String> missing = new ArrayList<>();
            for (String[] def : BASE_ENTITY_COLUMNS.values()) {
                if (!actual.contains(def[0])) {
                    missing.add(def[0]);
                }
            }
            if (missing.isEmpty()) {
                continue;
            }
            result.addIssue("表结构：" + table.getTableName(), 0L, "ERROR",
                    "实体继承 BaseEntity，SQL 会带上 " + String.join("、", missing)
                            + "，但表中没有这些列 —— 编译能过，运行时必报 Unknown column。建议执行："
                            + buildAlterSql(table.getTableName(), missing));
        }
    }

    private Set<String> dbColumns(String tableName) {
        List<String> cols = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ?",
                String.class, tableName);
        Set<String> set = new HashSet<>();
        for (String col : cols) {
            set.add(col.toLowerCase(Locale.ROOT));
        }
        return set;
    }

    private String buildAlterSql(String tableName, List<String> missing) {
        StringBuilder sb = new StringBuilder("ALTER TABLE ").append(tableName).append(' ');
        for (int i = 0; i < missing.size(); i++) {
            String col = missing.get(i);
            String type = "varchar(64)";
            for (String[] def : BASE_ENTITY_COLUMNS.values()) {
                if (def[0].equals(col)) {
                    type = def[1];
                    break;
                }
            }
            sb.append(i == 0 ? "ADD COLUMN " : ", ADD COLUMN ")
                    .append(col).append(' ').append(type).append(" NULL");
        }
        return sb.toString();
    }

    private List<GenTableColumn> listColumns(List<Long> ids) {
        LambdaQueryWrapper<GenTableColumn> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(GenTableColumn::getTableId, ids);
        return columnService.list(wrapper);
    }

    /**
     * 注释类数据文本：模板把注释原样输出，若注释里带了 ${...} 示例，不能算成未渲染的模板变量
     */
    private List<String> collectDataTexts(List<GenTableColumn> columns) {
        List<String> texts = new ArrayList<>();
        for (GenTableColumn col : columns) {
            if (col.getColumnComment() != null) {
                texts.add(col.getColumnComment().trim());
            }
        }
        texts.removeIf(t -> t.length() < 4);
        return texts;
    }

    /**
     * 检查未渲染的模板占位符。
     *
     * <p>只报「真正说明模板出错」的残留：注释行里的 ${} 不影响编译，来自字段注释的示例文本属于数据，
     * 两者都直接放行，避免正常产物被误判。
     */
    private void checkPlaceholderResidue(GenSelfCheckVO result, String path, String content, List<String> dataTexts) {
        if (!content.contains("${")) {
            return;
        }
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.contains("${")) {
                continue;
            }
            if (isCommentLine(line) || containsDataText(line, dataTexts)) {
                continue;
            }
            result.addIssue(path, (long) (i + 1), "ERROR",
                    "存在未渲染的模板占位符 ${...}，模板变量名可能有误");
            return;
        }
    }

    private boolean isCommentLine(String line) {
        String t = line.trim();
        return t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") || t.startsWith("<!--");
    }

    private boolean containsDataText(String line, List<String> dataTexts) {
        for (String text : dataTexts) {
            if (line.contains(text)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOn(Integer flag) {
        return flag != null && flag == 1;
    }

    private String resolveFile(Diagnostic<? extends JavaFileObject> d, Map<String, String> classToPath) {
        JavaFileObject source = d.getSource();
        if (source == null) {
            return "javac";
        }
        String name = source.getName();
        String className = name;
        int scheme = name.lastIndexOf("string:///");
        if (scheme >= 0) {
            className = name.substring(scheme + "string:///".length());
        } else {
            className = name;
        }
        className = className.replace('\\', '/');
        if (className.startsWith("/")) {
            className = className.substring(1);
        }
        if (className.endsWith(".java")) {
            className = className.substring(0, className.length() - 5);
        }
        className = className.replace('/', '.');
        return classToPath.getOrDefault(className, className);
    }

    /**
     * 产物测试源（src/test/java）识别：V7.3 起单独参与测试编译（compileTests），
     * 不再混进主编译——测试类可能是包级私有且依赖 test-scope 包。
     */
    static boolean isGeneratedTestSource(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.replace('\\', '/');
        return normalized.contains("/src/test/java/") || normalized.startsWith("src/test/java/");
    }

    /**
     * 自检 classpath 合并平台自身 classpath：基座在前（版本以目标工程为准），
     * 平台在后兜底基座快照缺的三方包（如 spring-security-crypto）。
     */
    static String mergeWithAppClasspath(String configured, String appClasspath) {
        if (!StringUtils.hasText(appClasspath)) {
            return configured == null ? "" : configured;
        }
        if (!StringUtils.hasText(configured)) {
            return appClasspath;
        }
        return configured + java.io.File.pathSeparator + appClasspath;
    }

    private String classNameOf(String content) {
        Matcher pm = PACKAGE_PATTERN.matcher(content);
        Matcher cm = CLASS_PATTERN.matcher(content);
        if (!cm.find()) {
            return null;
        }
        String simple = cm.group(1);
        return pm.find() ? pm.group(1) + "." + simple : simple;
    }

    private long countOf(String content, String needle) {
        long count = 0;
        int idx = 0;
        while ((idx = content.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // 临时目录清理失败不影响结果
                }
            });
        } catch (Exception ignored) {
            // 同上
        }
    }

    /**
     * 参与编译的 Java 源码
     */
    private record JavaSource(String path, String content) {
    }

    /**
     * 内存中的 Java 源码对象
     */
    private static final class StringSource extends SimpleJavaFileObject {

        private final String code;

        private StringSource(String className, String code) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}

package i18n_snapshot;

import freemarker.template.*;
import freemarker.cache.*;
import java.io.*;
import java.util.*;

/**
 * V8.1 快照测试：用 Freemarker 离线渲染真实 vuePage.ftl（不依赖 Spring/Maven/DB）。
 * 覆盖 9 种生成模式，对每种验证 i18n 双轨制契约。
 *
 * 契约：
 *  1) OFF（i18nEnabled 缺失）→ 输出中不得出现 $t( 调用（纯中文，等价"无 i18n"基线）。
 *  2) ON （i18nEnabled=1） → 输出必须出现 $t( 调用。
 *  3) 零 diff 契约：把 ON 中所有 $t 包裹（插值 {{ $t('k','中文') }} 与属性 :x="$t('k','中文')"）
 *     还原为 '中文' 后，必须与 OFF 输出逐字节相等 —— i18n 只是纯包裹。
 *
 * 运行：javac -cp freemarker.jar -d . VuePageI18nContract.java
 *       java  -cp freemarker.jar;. i18n_snapshot.VuePageI18nContract
 */
public class VuePageI18nContract {

    // 默认指向本机调试路径；CI（Linux runner）通过 -Dftl.dir 覆盖为仓库内相对/绝对路径。
    static final String FTL_DIR = System.getProperty("ftl.dir",
            "D:/workspace/xyh-devbranch/codegen-server/src/main/resources/gen-templates");
    static final String FTL_NAME = "vuePage.ftl";

    /** 未显式提供的变量统一兜底：布尔 false / 字符串 "" / 数字 0 / 空序列。 */
    static class UniversalDefault implements TemplateBooleanModel, TemplateScalarModel,
            TemplateNumberModel, TemplateSequenceModel {
        public boolean getAsBoolean() { return false; }
        public String getAsString() { return ""; }
        public Number getAsNumber() { return 0; }
        public TemplateModel get(int index) { return null; }
        public int size() { return 0; }
    }
    static final TemplateModel DEFAULT = new UniversalDefault();

    /** 宽松根模型：已知 key 返回真实值，未知 key 返回 UniversalDefault。 */
    static class LenientRoot implements TemplateHashModel {
        final Map<String, Object> data = new HashMap<>();
        final ObjectWrapper wrapper;
        LenientRoot(ObjectWrapper w) { this.wrapper = w; }
        public boolean isEmpty() { return false; }
        public TemplateModel get(String key) throws TemplateModelException {
            Object v = data.get(key);
            if (v != null) return wrapper.wrap(v);
            return DEFAULT;
        }
    }

    static LenientRoot col(ObjectWrapper w, String javaField, String commentLabel, String... flags) {
        LenientRoot m = new LenientRoot(w);
        m.data.put("javaField", javaField);
        m.data.put("commentLabel", commentLabel);
        m.data.put("dictType", "status");
        m.data.put("refTable", "category");
        m.data.put("refLabel", "name");
        m.data.put("colWidth", "120");
        m.data.put("jsInit", "undefined");
        m.data.put("queryJsInit", "undefined");
        m.data.put("hasLink", false);
        for (String f : new String[]{"isSelect","isRadio","isSwitch","isDate","isDatetime",
                "isImage","isMultiImage","isFile","hasRef","hasDict","hasEnum",
                "isText","isTextarea","isNumber","isNumberCtrl","hasVueRule","required","unique"})
            m.data.put(f, false);
        for (String f : flags) m.data.put(f, true);
        return m;
    }

    static LenientRoot makeChild(ObjectWrapper w, String varName, String comment, boolean withGrand) {
        List<LenientRoot> cf = new ArrayList<>();
        cf.add(col(w, "name", "名称", "isText"));
        cf.add(col(w, "status", "状态", "isSelect", "hasDict"));
        cf.add(col(w, "amount", "金额", "isNumberCtrl", "isNumber"));
        cf.add(col(w, "remark", "备注", "isTextarea"));
        List<LenientRoot> gf = withGrand ? new ArrayList<>(Arrays.asList(
                col(w, "specName", "规格名", "isText"), col(w, "specVal", "规格值", "isText"))) : null;
        LenientRoot m = new LenientRoot(w);
        m.data.put("varName", varName);
        m.data.put("comment", comment);
        m.data.put("formColumns", cf);
        m.data.put("listColumns", cf);
        if (withGrand) {
            LenientRoot gc = new LenientRoot(w);
            gc.data.put("varName", "spec");
            gc.data.put("comment", "规格");
            gc.data.put("formColumns", gf);
            m.data.put("grandChildren", Collections.singletonList(gc));
        }
        return m;
    }

    static List<LenientRoot> makeCols(ObjectWrapper w) {
        List<LenientRoot> cols = new ArrayList<>();
        cols.add(col(w, "name", "名称", "isText"));
        cols.add(col(w, "status", "状态", "isSelect", "hasDict"));
        cols.add(col(w, "type", "类型", "isRadio", "hasDict"));
        cols.add(col(w, "enabled", "是否启用", "isSwitch"));
        cols.add(col(w, "birthday", "生日", "isDate"));
        cols.add(col(w, "createTime", "创建时间", "isDatetime"));
        cols.add(col(w, "avatar", "头像", "isImage"));
        cols.add(col(w, "photos", "相册", "isMultiImage"));
        cols.add(col(w, "attach", "附件", "isFile"));
        cols.add(col(w, "categoryId", "分类", "hasRef"));
        cols.add(col(w, "level", "等级", "hasEnum"));
        cols.add(col(w, "remark", "备注", "isTextarea"));
        cols.add(col(w, "amount", "金额", "isNumberCtrl", "isNumber"));
        return cols;
    }

    // flags: [genTree, hasChild, hasRef, hasExcelImport, hasExcelExport, hasFlow, hasUpload]
    static LenientRoot build(ObjectWrapper w, boolean i18n, boolean[] f, int childCount, boolean withGrand) {
        LenientRoot root = new LenientRoot(w);
        root.data.put("className", "DemoOrder");
        root.data.put("comment", "演示订单");
        root.data.put("moduleName", "demo");
        root.data.put("permsPrefix", "demo:order");
        root.data.put("genPageApi", true);
        root.data.put("genTree", f[0]);
        root.data.put("genAddApi", true);
        root.data.put("genEditApi", true);
        root.data.put("genDeleteApi", true);
        root.data.put("genAddBtn", true);
        root.data.put("genEditBtn", true);
        root.data.put("genDeleteBtn", true);
        root.data.put("hasChild", f[1]);
        root.data.put("hasRef", f[2]);
        root.data.put("hasExcelImport", f[3]);
        root.data.put("hasExcelExport", f[4]);
        root.data.put("hasFlow", f[5]);
        root.data.put("hasUpload", f[6]);
        root.data.put("hasMultiImage", true);
        root.data.put("hasDetail", true);
        root.data.put("hasRefHelper", true);
        root.data.put("hasSingleUnique", false);
        root.data.put("vueHasDict", true);
        root.data.put("hasGrandDialog", withGrand);
        root.data.put("fileEnabled", true);
        root.data.put("imageListSize", 60);
        root.data.put("treeParentField", "parentId");
        root.data.put("idColWidth", 70);

        List<LenientRoot> cols = makeCols(w);
        root.data.put("vueQueryColumns", cols.subList(0, 5));
        root.data.put("listColumns", cols.subList(0, 10));
        root.data.put("formColumns", cols);

        if (f[1]) {
            List<LenientRoot> children = new ArrayList<>();
            for (int i = 0; i < childCount; i++)
                children.add(makeChild(w, "child" + i, "子表" + i, withGrand && i == 0));
            root.data.put("children", children);
        }
        if (i18n) root.data.put("i18nEnabled", 1);
        return root;
    }

    public static void main(String[] args) throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setDirectoryForTemplateLoading(new File(FTL_DIR));
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        Template t = cfg.getTemplate(FTL_NAME);
        ObjectWrapper w = cfg.getObjectWrapper();

        String[] names = {"simple","relation","master-detail","master-multichild","multi","micro","flow","pay","file"};
        boolean[][] f = {
            {false,false,false,false,false,false,false},
            {false,false,true ,false,false,false,false},
            {false,true ,false,false,false,false,false},
            {false,true ,false,false,false,false,false},
            {false,false,false,true ,true ,false,false},
            {true ,false,false,false,false,false,false},
            {false,false,false,false,false,true ,false},
            {false,true ,false,false,true ,false,false},
            {false,false,false,false,false,false,true },
        };
        int[] childCount =   {0,0,1,2,0,0,0,1,0};
        boolean[] withGrand = {false,false,false,true,false,false,false,false,false};

        int pass = 0, fail = 0;
        for (int i = 0; i < names.length; i++) {
            String off = render(t, build(w, false, f[i], childCount[i], withGrand[i]));
            String on  = render(t, build(w, true,  f[i], childCount[i], withGrand[i]));
            List<String> errs = new ArrayList<>();
            if (off.contains("$t(")) errs.add("OFF 含 $t(");
            if (!on.contains("$t(")) errs.add("ON 无 $t(");
            String s1 = on.replaceAll("\\{\\{\\s*\\$t\\('[^']*',\\s*'([^']*)'\\)\\s*\\}\\}", "$1");
            String stripped = s1.replaceAll("(\\s+):([\\w-]+)=\"\\$t\\('[^']*',\\s*'([^']*)'\\)\"", "$1$2=\"$3\"");
            if (!stripped.equals(off)) errs.add("零 diff 失败");
            if (errs.isEmpty()) {
                pass++;
                System.out.println("PASS  " + names[i] + "  OFF=" + off.length() + "  ON 含$t×" + count(on, "$t("));
            } else {
                fail++;
                System.err.println("FAIL  " + names[i] + "  " + errs);
            }
        }
        System.out.println("\n==== " + pass + "/" + names.length + " 模式通过, " + fail + " 失败 ====");
        if (fail > 0) System.exit(1);
    }

    static String render(Template t, Object m) throws Exception {
        StringWriter w = new StringWriter();
        t.process(m, w);
        return w.toString();
    }
    static int count(String s, String sub) {
        int c = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) { c++; i += sub.length(); }
        return c;
    }
}

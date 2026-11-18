package com.codegen.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 生成物合并与行级 diff。
 *
 * <p><b>标记块约定</b>：可被重新生成覆盖的区域用成对标记包起来，标记外的内容是手写区，二次生成原样保留。
 * <pre>
 * // &lt;gen:begin key="controller-body"&gt;      .java / .js
 * // &lt;gen:end key="controller-body"&gt;
 * &lt;!-- &lt;gen:begin key="vue-page"&gt; --&gt;       .vue / .xml
 * &lt;!-- &lt;gen:end key="vue-page"&gt; --&gt;
 * </pre>
 *
 * <p>合并规则见 {@link #merge(String, String)}：老文件没有标记时退化为整体覆盖（V1.2 行为），
 * 保证历史产物不受影响。
 */
public final class GenMergeUtils {

    private static final Pattern BEGIN = Pattern.compile("<gen:begin\\s+key=\"([^\"]+)\">");
    private static final Pattern END = Pattern.compile("<gen:end\\s+key=\"([^\"]+)\">");

    private GenMergeUtils() {
    }

    /**
     * 合并结果
     *
     * @param content 合并后内容
     * @param mode    MERGED=块级合并 / OVERWRITTEN=老文件无标记整体覆盖 / WRITTEN=新文件
     * @param keys    本次实际替换的块 key，便于排查
     */
    public record MergeResult(String content, String mode, List<String> keys) {
    }

    /**
     * 单个标记块（行下标，闭区间）
     */
    public record Block(String key, int startLine, int endLine) {
    }

    /**
     * 合并：把 newContent 里的每个标记块替换进 oldContent 的同名块，块外内容逐字保留。
     *
     * @param oldContent 磁盘上已有内容（可为 null/空）
     * @param newContent 本次渲染结果
     */
    public static MergeResult merge(String oldContent, String newContent) {
        if (oldContent == null || oldContent.isEmpty()) {
            return new MergeResult(newContent, "WRITTEN", List.of());
        }
        Map<String, Block> oldBlocks = blocks(oldContent);
        if (oldBlocks.isEmpty()) {
            return new MergeResult(newContent, "OVERWRITTEN", List.of());
        }
        Map<String, Block> newBlocks = blocks(newContent);
        if (newBlocks.isEmpty()) {
            // 新模板不再输出标记：按整体覆盖处理，但已有备份兜底
            return new MergeResult(newContent, "OVERWRITTEN", List.of());
        }

        List<String> oldLines = lines(oldContent);
        List<String> newLines = lines(newContent);
        List<String> replaced = new ArrayList<>();

        // 从后往前替换，避免前面的替换导致后续行下标偏移
        List<Block> targets = new ArrayList<>();
        for (Map.Entry<String, Block> entry : oldBlocks.entrySet()) {
            if (newBlocks.containsKey(entry.getKey())) {
                targets.add(entry.getValue());
                replaced.add(entry.getKey());
            }
        }
        targets.sort((a, b) -> Integer.compare(b.startLine(), a.startLine()));
        for (Block oldBlock : targets) {
            Block newBlock = newBlocks.get(oldBlock.key());
            List<String> blockLines = new ArrayList<>(
                    newLines.subList(newBlock.startLine(), newBlock.endLine() + 1));
            oldLines.subList(oldBlock.startLine(), oldBlock.endLine() + 1).clear();
            oldLines.addAll(oldBlock.startLine(), blockLines);
        }

        // 新模板新增、老文件没有的块：插到最后一个已有块之后（无块则插到末尾花括号前）
        List<String> appendedKeys = new ArrayList<>();
        for (Map.Entry<String, Block> entry : newBlocks.entrySet()) {
            if (oldBlocks.containsKey(entry.getKey())) {
                continue;
            }
            List<String> blockLines = new ArrayList<>(
                    newLines.subList(entry.getValue().startLine(), entry.getValue().endLine() + 1));
            blockLines.add(0, "");
            // style 类块只能落在文件末尾：插到「最后一个已有块之后」会掉进 <script> 里，
            // 直接产出语法错误（vue 文件里 style 一定在 script 之后）。
            int insertAt = entry.getKey().toLowerCase().contains("style")
                    ? oldLines.size()
                    : insertPoint(oldLines, oldBlocks);
            oldLines.addAll(insertAt, blockLines);
            appendedKeys.add(entry.getKey());
        }

        List<String> keys = new ArrayList<>(replaced);
        keys.addAll(appendedKeys);

        // import 同步：块外合并会保留旧文件的 import 区，但新模板可能新增了 import
        // （例如这次打开了「操作日志」开关，body 块里出现 @Log 而 import 没有）。
        // 取新模板 import 与合并结果的并集，缺的插到最后一行 import 之后。
        List<String> merged = syncImports(oldLines, newLines);
        return new MergeResult(String.join("\n", merged), "MERGED", keys);
    }

    /**
     * 把 newLines 里有而合并结果里没有的 import 行补进去，插到最后一行 import 之后。
     * Java 里多余的 import 无害，宁多勿缺。
     */
    private static List<String> syncImports(List<String> mergedLines, List<String> newLines) {
        java.util.Set<String> existing = new java.util.LinkedHashSet<>();
        int lastImportIdx = -1;
        for (int i = 0; i < mergedLines.size(); i++) {
            String t = mergedLines.get(i).trim();
            if (t.startsWith("import ")) {
                existing.add(t);
                lastImportIdx = i;
            }
        }
        List<String> missing = new ArrayList<>();
        for (String line : newLines) {
            String t = line.trim();
            if (t.startsWith("import ") && !existing.contains(t)) {
                missing.add(line);
                existing.add(t);
            }
        }
        if (missing.isEmpty()) {
            return mergedLines;
        }
        int at = lastImportIdx >= 0 ? lastImportIdx + 1 : 0;
        mergedLines.addAll(at, missing);
        return mergedLines;
    }

    /**
     * 新块插入位置：最后一个 end 标记之后（仍在类/标签内部）；无标记时退化为末尾 "}" 之前
     */
    private static int insertPoint(List<String> oldLines, Map<String, Block> oldBlocks) {
        int lastEnd = lastEndMarkerLine(oldLines);
        if (lastEnd >= 0) {
            return lastEnd + 1;
        }
        for (int i = oldLines.size() - 1; i >= 0; i--) {
            if ("}".equals(oldLines.get(i).trim())) {
                return i;
            }
        }
        return oldLines.size();
    }

    private static int lastEndMarkerLine(List<String> lines) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (END.matcher(lines.get(i)).find()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 解析标记块：key -> 行区间（闭区间，含 begin/end 两行）
     */
    public static Map<String, Block> blocks(String content) {
        Map<String, Block> result = new LinkedHashMap<>();
        if (content == null || content.isEmpty()) {
            return result;
        }
        List<String> lines = lines(content);
        Map<String, Integer> open = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher begin = BEGIN.matcher(line);
            if (begin.find()) {
                open.put(begin.group(1), i);
                continue;
            }
            Matcher end = END.matcher(line);
            if (end.find()) {
                Integer start = open.remove(end.group(1));
                if (start != null) {
                    result.put(end.group(1), new Block(end.group(1), start, i));
                }
            }
        }
        return result;
    }

    /**
     * 行级差异：{@code type} 取 EQUAL / ADD / DEL
     */
    public record DiffLine(String type, Integer oldLine, Integer newLine, String text) {
    }

    /**
     * LCS 行级 diff（先裁剪公共前后缀，中间段落做 DP，足够应付生成物这种百行级文件）
     */
    public static List<DiffLine> diff(String oldContent, String newContent) {
        List<String> a = lines(oldContent == null ? "" : oldContent);
        List<String> b = lines(newContent == null ? "" : newContent);

        int prefix = 0;
        while (prefix < a.size() && prefix < b.size() && a.get(prefix).equals(b.get(prefix))) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < a.size() - prefix && suffix < b.size() - prefix
                && a.get(a.size() - 1 - suffix).equals(b.get(b.size() - 1 - suffix))) {
            suffix++;
        }

        List<DiffLine> result = new ArrayList<>();
        for (int i = 0; i < prefix; i++) {
            result.add(new DiffLine("EQUAL", i + 1, i + 1, a.get(i)));
        }

        List<String> midA = a.subList(prefix, a.size() - suffix);
        List<String> midB = b.subList(prefix, b.size() - suffix);
        result.addAll(lcsDiff(midA, midB, prefix, prefix));

        for (int i = 0; i < suffix; i++) {
            int oldIdx = a.size() - suffix + i;
            int newIdx = b.size() - suffix + i;
            result.add(new DiffLine("EQUAL", oldIdx + 1, newIdx + 1, a.get(oldIdx)));
        }
        return result;
    }

    private static List<DiffLine> lcsDiff(List<String> a, List<String> b, int aOffset, int bOffset) {
        List<DiffLine> out = new ArrayList<>();
        int n = a.size();
        int m = b.size();
        if (n == 0 && m == 0) {
            return out;
        }
        if (n == 0) {
            for (int j = 0; j < m; j++) {
                out.add(new DiffLine("ADD", null, bOffset + j + 1, b.get(j)));
            }
            return out;
        }
        if (m == 0) {
            for (int i = 0; i < n; i++) {
                out.add(new DiffLine("DEL", aOffset + i + 1, null, a.get(i)));
            }
            return out;
        }
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                dp[i][j] = a.get(i).equals(b.get(j))
                        ? dp[i + 1][j + 1] + 1
                        : Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (a.get(i).equals(b.get(j))) {
                out.add(new DiffLine("EQUAL", aOffset + i + 1, bOffset + j + 1, a.get(i)));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                out.add(new DiffLine("DEL", aOffset + i + 1, null, a.get(i)));
                i++;
            } else {
                out.add(new DiffLine("ADD", null, bOffset + j + 1, b.get(j)));
                j++;
            }
        }
        while (i < n) {
            out.add(new DiffLine("DEL", aOffset + i + 1, null, a.get(i)));
            i++;
        }
        while (j < m) {
            out.add(new DiffLine("ADD", null, bOffset + j + 1, b.get(j)));
            j++;
        }
        return out;
    }

    /**
     * 差异统计（用于列表快速展示 +/- 行数）
     */
    public static int[] stats(List<DiffLine> diff) {
        int add = 0;
        int del = 0;
        for (DiffLine d : diff) {
            if ("ADD".equals(d.type())) {
                add++;
            } else if ("DEL".equals(d.type())) {
                del++;
            }
        }
        return new int[]{add, del};
    }

    private static List<String> lines(String content) {
        // split 保留尾部空行语义：-1 让 "a\n" 分成 ["a",""]，避免合并时丢换行
        String normalized = content.replace("\r\n", "\n").replace("\r", "\n");
        List<String> list = new ArrayList<>(List.of(normalized.split("\n", -1)));
        // 文件以换行结尾时最后会多出一个空串，去掉它，写回时统一补换行
        if (!list.isEmpty() && list.get(list.size() - 1).isEmpty()) {
            list.remove(list.size() - 1);
        }
        return list;
    }
}

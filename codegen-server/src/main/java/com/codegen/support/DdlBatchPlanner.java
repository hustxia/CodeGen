package com.codegen.support;

import java.util.ArrayList;
import java.util.List;

/**
 * 建库 DDL 分批规划器：把切分好的语句列表按批大小切成若干批。
 *
 * <p>纯函数、无状态、不碰数据库，只做「列表切段」，便于单测覆盖
 * 整除 / 有余数 / 空列表 / 非法批大小 / 单批五种边界。
 *
 * <p>批大小 {@code <= 0} 时按「不分批」兜底（全部语句一批）：配置缺失或写错时
 * 退化成改动前的整段执行，不因为分批而改变已有行为。
 */
public final class DdlBatchPlanner {

    /** 失败信息里语句摘要的字符上限 */
    private static final int SNIPPET_LIMIT = 200;

    private DdlBatchPlanner() {
    }

    /**
     * 按批大小切分语句列表。
     *
     * @param statements 语句列表，可为 null
     * @param size       每批语句数；{@code <= 0} 视为不分批
     * @return 批次列表；入参为空时返回空列表（调用方无需特殊判断）
     */
    public static List<List<String>> split(List<String> statements, int size) {
        List<List<String>> batches = new ArrayList<>();
        if (statements == null || statements.isEmpty()) {
            return batches;
        }
        int batchSize = size > 0 ? size : statements.size();
        for (int from = 0; from < statements.size(); from += batchSize) {
            int to = Math.min(from + batchSize, statements.size());
            batches.add(new ArrayList<>(statements.subList(from, to)));
        }
        return batches;
    }

    /**
     * 语句摘要：取前 {@value #SNIPPET_LIMIT} 字符（换行压成空格），超长截断并加省略号。
     * 失败定位时只带这一小段，避免把整条 DDL 刷进异常信息。
     */
    public static String snippet(String statement) {
        if (statement == null) {
            return "";
        }
        String one = statement.replace('\n', ' ').replace('\r', ' ');
        return one.length() > SNIPPET_LIMIT ? one.substring(0, SNIPPET_LIMIT) + "..." : one;
    }
}

package com.codegen.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * V9.18 子表增量保存的差集规划器（纯函数，无 Spring/MyBatis 依赖）。
 *
 * <p>为什么抽成纯函数：生成物里的三路合并逻辑最终会落到用户工程里跑，
 * 一旦差集算错就是「保存后数据少了一行 / 多了一行」这类难复现事故。
 * 这里把「新增 / 更新 / 删除」「一对一 upsert」「多对多关联差集」三种判定
 * 收敛成可单测的纯函数，生成侧（serviceImplMasterdetail.ftl）与测试共用同一套口径。
 *
 * <p>三条不变式（对应需求「幂等」）：
 * <ol>
 *   <li>同一份提交连续保存两次，第二次的 {@code inserts/deletes} 必须为空；</li>
 *   <li>主键为空的入参一律算新增，不会被误判成更新；</li>
 *   <li>多对多关联按集合比较，重复关联只会保留一份。</li>
 * </ol>
 */
public final class ChildSavePlanner {

    private ChildSavePlanner() {
    }

    /**
     * 三路合并结果。
     *
     * @param inserts 需要 insert 的行（主键为空或主键不在已有集合里）
     * @param updates 需要 update 的行（主键命中已有行）
     * @param deletes 需要 delete 的已有行主键（已有集合里有、本次提交没有）
     */
    public record Plan<T, K>(List<T> inserts, List<T> updates, List<K> deletes) {
    }

    /**
     * 按主键三路合并（一对一/一对多默认策略）。
     *
     * @param existing 库里按外键查出的已有行，可为 null
     * @param incoming 本次提交的行，可为 null（前端约定 null=不动该子表）
     * @param keyFn    取主键的函数；返回 null 表示「新行」
     */
    public static <T, K> Plan<T, K> plan(List<T> existing, List<T> incoming, Function<T, K> keyFn) {
        List<T> oldList = existing == null ? List.of() : existing;
        List<T> newList = incoming == null ? List.of() : incoming;

        Set<K> existingKeys = new LinkedHashSet<>();
        for (T old : oldList) {
            K key = keyFn.apply(old);
            if (key != null) {
                existingKeys.add(key);
            }
        }

        List<T> inserts = new ArrayList<>();
        List<T> updates = new ArrayList<>();
        Set<K> matched = new HashSet<>();
        for (T row : newList) {
            K key = keyFn.apply(row);
            if (key != null && existingKeys.contains(key)) {
                // 重复提交同一主键：多次 update 同一行，结果仍然收敛，不会多出行
                updates.add(row);
                matched.add(key);
            } else {
                inserts.add(row);
            }
        }

        List<K> deletes = new ArrayList<>();
        for (K key : existingKeys) {
            if (!matched.contains(key)) {
                deletes.add(key);
            }
        }
        return new Plan<>(inserts, updates, deletes);
    }

    /**
     * 一对一 upsert 结果：{@code updateTarget} 与 {@code insert} 至多一个非空。
     *
     * @param updateTarget 已存在的那一行（调用方应把提交值合并到它上面，保留其主键）
     * @param insert       不存在时需要新建的行
     */
    public record Upsert<T>(T updateTarget, T insert) {
    }

    /**
     * 一对一：外键即身份，同一外键最多一行。
     *
     * <p>即便提交里带了别的主键，只要按外键查到了既有行，就一律更新它——
     * 否则会把「改了主键的脏提交」插成第二行，一对一关系直接破功。
     */
    public static <T> Upsert<T> planUpsert(List<T> existing, T incoming) {
        if (incoming == null) {
            return new Upsert<>(null, null);
        }
        if (existing != null && !existing.isEmpty()) {
            // 历史脏数据可能有多行，取第一行收敛（不在这里做清理，交给业务/DB 唯一键）
            return new Upsert<>(existing.get(0), null);
        }
        return new Upsert<>(null, incoming);
    }

    /**
     * 多对多关联差集结果。
     *
     * @param toAdd    本次新增的关联键
     * @param toRemove 本次要删除的关联键
     * @param toKeep   两端都有的关联键（幂等重放时 toAdd/toRemove 都为空）
     */
    public record AssociationPlan<K>(List<K> toAdd, List<K> toRemove, List<K> toKeep) {
    }

    /**
     * 多对多中间表按关联键做差集，重复保存不会产生重复关联行。
     */
    public static <K> AssociationPlan<K> planAssociation(Collection<K> existingKeys,
                                                         Collection<K> incomingKeys) {
        Set<K> oldKeys = distinctNonNull(existingKeys);
        Set<K> newKeys = distinctNonNull(incomingKeys);

        List<K> toAdd = new ArrayList<>();
        List<K> toRemove = new ArrayList<>();
        List<K> toKeep = new ArrayList<>();
        for (K key : newKeys) {
            if (oldKeys.contains(key)) {
                toKeep.add(key);
            } else {
                toAdd.add(key);
            }
        }
        for (K key : oldKeys) {
            if (!newKeys.contains(key)) {
                toRemove.add(key);
            }
        }
        return new AssociationPlan<>(toAdd, toRemove, toKeep);
    }

    /** 去重并保留顺序，null 关联键不入集合（无法定位关联行，交给入参校验拦截） */
    private static <K> Set<K> distinctNonNull(Collection<K> keys) {
        Set<K> out = new LinkedHashSet<>();
        if (keys != null) {
            for (K key : keys) {
                if (key != null) {
                    out.add(key);
                }
            }
        }
        return out;
    }

    /** 关系类型归一：middle/ONE_TO_ONE/MANY_TO_MANY 等写法统一成大写常量 */
    public static String normalizeRelationType(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase().replace('-', '_');
        if (value.isEmpty()) {
            return "ONE_TO_MANY";
        }
        if ("MIDDLE".equals(value)) {
            return "MANY_TO_MANY";
        }
        return switch (value) {
            case "ONE_TO_ONE", "MANY_TO_MANY", "ONE_TO_MANY" -> value;
            default -> "ONE_TO_MANY";
        };
    }

    /** 两个值是否相等（含 null），供生成物里的关联键比对使用 */
    public static boolean sameKey(Object left, Object right) {
        return Objects.equals(left, right);
    }
}

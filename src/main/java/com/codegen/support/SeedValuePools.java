package com.codegen.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V7.1 种子数据 AI 增强值池。
 *
 * <p>职责边界：AI 只提供「语义取值」（人名/公司/商品/备注等与业务贴合的候选词），
 * 结构约束（外键取值区间、唯一后缀、枚举合法性、varchar 截断）仍由
 * {@link GenSeedGenerator} 的规则引擎负责——AI 永远不直接产出行数据。
 *
 * <p>两级取值：
 * <ol>
 *   <li>类别池（persons/companies/products/...）：替换原写死的通用词库，
 *       使「商城系统」出「羽绒服/无线耳机」而不是「不锈钢板材」；</li>
 *   <li>列级覆盖（columns，键为小写 "表名.列名"）：AI 对重点业务列
 *       （如商品名、活动标题）给出的专属取值，优先级高于类别池。</li>
 * </ol>
 *
 * <p>降级基线：{@link #defaults()} 返回的实例与 V5.2 规则词库行为完全一致，
 * AI 失败/输出非法时回落它，seed 步骤永不因 AI 中断。
 */
public class SeedValuePools {

    /** 单池上限：防 AI 长输出撑爆 SQL 拼接与单测断言 */
    public static final int MAX_PER_LIST = 24;
    /** 单值上限（字符）：超出直接丢弃该项 */
    public static final int MAX_VALUE_LEN = 100;
    /** 列级覆盖条目上限 */
    public static final int MAX_COLUMN_OVERRIDES = 200;

    private List<String> persons;
    private List<String> companies;
    private List<String> cities;
    private List<String> streets;
    private List<String> depts;
    private List<String> products;
    private List<String> titles;
    private List<String> remarks;
    private List<String> specs;
    /** 小写 "表名.列名" → 候选值 */
    private Map<String, List<String>> columnOverrides = new LinkedHashMap<>();

    /** V5.2 规则词库原值（自 GenSeedGenerator 原样迁移）——降级基线，行为与 AI 增强前完全一致 */
    public static SeedValuePools defaults() {
        SeedValuePools p = new SeedValuePools();
        p.persons = list("张伟", "王芳", "李娜", "刘强", "陈静", "杨洋", "赵磊", "黄敏",
                "周杰", "吴丽", "徐明", "孙娟", "马超", "朱婷", "胡军", "郭涛");
        p.companies = list("华信科技有限公司", "恒达贸易有限公司", "宏远物流有限公司",
                "蓝天医疗器械公司", "瑞丰食品有限公司", "中兴建材集团", "汇金投资有限公司", "永昌实业",
                "天成电子科技", "博雅文化传播");
        p.cities = list("北京市", "上海市", "广州市", "深圳市", "杭州市",
                "成都市", "武汉市", "南京市", "西安市", "重庆市", "苏州市", "郑州市");
        p.streets = list("中山路", "人民路", "建设大道", "解放路", "和平街",
                "科技园区", "工业园区", "滨江大道");
        p.depts = list("综合办公室", "人力资源部", "财务部", "采购部", "销售部",
                "仓储物流部", "质量管理部", "信息技术部");
        p.products = list("不锈钢板材", "包装纸箱", "电子元器件", "润滑油", "精密轴承",
                "密封圈套装", "电缆线", "滤芯组件", "标签耗材", "标准紧固件");
        p.titles = list("综合管理", "生产运营", "客户服务", "质量管理", "技术研发",
                "市场拓展", "供应链管理", "行政后勤", "财务管理", "信息服务");
        p.remarks = list("常规业务", "客户加急", "按合同执行", "样品试用",
                "内部领用", "退货换货", "重点跟踪", "");
        p.specs = list("标准型", "加强型", "经济型", "定制型");
        return p;
    }

    /** AI 输出 → 校验清洗后的值池；任一类别为空/非法时该项回落默认词库 */
    public static SeedValuePools of(List<String> persons, List<String> companies, List<String> cities,
                                    List<String> streets, List<String> depts, List<String> products,
                                    List<String> titles, List<String> remarks, List<String> specs,
                                    Map<String, List<String>> columnOverrides) {
        SeedValuePools d = defaults();
        SeedValuePools p = new SeedValuePools();
        p.persons = clean(persons, d.persons);
        p.companies = clean(companies, d.companies);
        p.cities = clean(cities, d.cities);
        p.streets = clean(streets, d.streets);
        p.depts = clean(depts, d.depts);
        p.products = clean(products, d.products);
        p.titles = clean(titles, d.titles);
        p.remarks = clean(remarks, d.remarks);
        p.specs = clean(specs, d.specs);
        if (columnOverrides != null) {
            int i = 0;
            for (Map.Entry<String, List<String>> e : columnOverrides.entrySet()) {
                if (i++ >= MAX_COLUMN_OVERRIDES) {
                    break;
                }
                List<String> v = clean(e.getValue(), null);
                if (e.getKey() != null && !e.getKey().isBlank() && v != null && !v.isEmpty()) {
                    p.columnOverrides.put(e.getKey().trim().toLowerCase(), v);
                }
            }
        }
        return p;
    }

    /** 去空白、去重、截断长度与数量；非法时回落 fallback（可为 null 表示丢弃） */
    private static List<String> clean(List<String> in, List<String> fallback) {
        if (in == null || in.isEmpty()) {
            return fallback;
        }
        List<String> out = new ArrayList<>();
        for (String s : in) {
            if (s == null) {
                continue;
            }
            String v = s.trim();
            if (v.isEmpty() || v.length() > MAX_VALUE_LEN || out.contains(v)) {
                continue;
            }
            out.add(v);
            if (out.size() >= MAX_PER_LIST) {
                break;
            }
        }
        return out.isEmpty() ? fallback : out;
    }

    /** 列级覆盖：查不到返回空列表（调用方继续走类别池） */
    public List<String> columnValues(String tableName, String columnName) {
        if (columnOverrides.isEmpty() || tableName == null || columnName == null) {
            return Collections.emptyList();
        }
        return columnOverrides.getOrDefault(
                (tableName + "." + columnName).toLowerCase(), Collections.emptyList());
    }

    private static List<String> list(String... arr) {
        return Collections.unmodifiableList(Arrays.asList(arr));
    }

    public List<String> getPersons() {
        return persons;
    }

    public List<String> getCompanies() {
        return companies;
    }

    public List<String> getCities() {
        return cities;
    }

    public List<String> getStreets() {
        return streets;
    }

    public List<String> getDepts() {
        return depts;
    }

    public List<String> getProducts() {
        return products;
    }

    public List<String> getTitles() {
        return titles;
    }

    public List<String> getRemarks() {
        return remarks;
    }

    public List<String> getSpecs() {
        return specs;
    }
}

package com.codegen.support;

import com.codegen.entity.SysProject;

import java.util.List;
import java.util.Map;

/**
 * V9.15 看板页生成器：系统级 1 份，跨表聚合成图卡配置。
 *
 * <p>为什么用 Java 方法而不是 ftl：图卡清单是「该项目所有有图的表」聚合出来的，
 * 单表渲染模型拿不到全系统视图（和 V6.4 ApiPerms / V6.5 mobileConfig 同款理由）。
 *
 * <p>通用性：图卡配置（table/module/field/type/title）全部来自列元数据推导，
 * 这里没有任何系统名/领域词；标题只用 commentLabel/tableComment。
 */
public final class GenDashboardWriter {

    private GenDashboardWriter() {
    }

    /** 一张图卡的渲染描述。apiBase = 该表取数路径前缀（沿用 GenTemplateModel.apiBase，多系统/网关通用）。 */
    public record Card(String table, String module, String field, String type,
                       String title, String metric, String range, String apiBase) {
    }

    /**
     * 生成看板页 src/views/dashboard/index.vue。
     *
     * @param appName  系统显示名
     * @param cards    全系统图卡清单
     * @param apiRoot  前端 api 前缀（空或 /api/{gw}，走 request 封装）
     */
    public static String dashboardVue(String appName, List<Card> cards, String apiRoot) {
        StringBuilder cfg = new StringBuilder();
        cfg.append("const CHART_CARDS = [\n");
        for (Card c : cards) {
            cfg.append("  { table: '").append(js(c.table)).append("', module: '").append(js(c.module))
                    .append("', field: '").append(js(c.field)).append("', type: '").append(js(c.type))
                    .append("', title: '").append(js(c.title)).append("', metric: '").append(js(c.metric))
                    .append("', range: '").append(js(c.range == null ? "30d" : c.range))
                    .append("', apiBase: '").append(js(c.apiBase == null ? "" : c.apiBase)).append("' },\n");
        }
        if (cards.isEmpty()) {
            cfg.append("  // 当前系统无可图表化列，看板显示空态\n");
        }
        cfg.append("]\n");

        String placeholder = cards.isEmpty() ? "<!-- V9.15 scaffold placeholder: 待 write 阶段用真实图卡覆盖 -->\n" : "";
        return placeholder
                + "<template>\n"
                + "  <div class=\"dashboard\">\n"
                + "    <div class=\"dash-head\">\n"
                + "      <div><h2>数据看板</h2><p>图表由列元数据自动推导 · 数据实时取自业务表</p></div>\n"
                + "      <el-button type=\"primary\" @click=\"loadAll\" :loading=\"loading\">刷新</el-button>\n"
                + "    </div>\n"
                + "    <div v-if=\"!chartCards.length\" class=\"dash-empty\">当前系统暂无可图表化的数据列</div>\n"
                + "    <el-row :gutter=\"16\">\n"
                + "      <el-col :span=\"12\" v-for=\"card in chartCards\" :key=\"card.table + '_' + card.field\">\n"
                + "        <ChartCard :title=\"card.title\" :type=\"card.type\" :subtitle=\"card.table + ' · ' + card.field\"\n"
                + "          :labels=\"card.labels\" :values=\"card.values\" :drill-text=\"card.table\" @drill=\"drill(card)\" />\n"
                + "      </el-col>\n"
                + "    </el-row>\n"
                + "  </div>\n"
                + "</template>\n\n"
                + "<script>\n"
                + "/**\n"
                + " * 数据看板（V9.15 自动生成）。图卡清单生成期固化，取数走每表 /stats 白名单接口。\n"
                + " * 请求失败明确提示，不静默吞（坑 75/78 教训）。\n"
                + " */\n"
                + "import request from '@/utils/request'\n"
                + "import { ElMessage } from 'element-plus'\n"
                + "import ChartCard from '@/components/ChartCard.vue'\n\n"
                + cfg + "\n"
                + "export default {\n"
                + "  name: 'Dashboard',\n"
                + "  components: { ChartCard },\n"
                + "  data() { return { loading: false, chartCards: CHART_CARDS.map(c => ({ ...c, labels: [], values: [] })) } },\n"
                + "  mounted() { this.loadAll() },\n"
                + "  methods: {\n"
                + "    loadAll() {\n"
                + "      this.loading = true\n"
                + "      this.chartCards.forEach(card => this.loadCard(card).finally(() => { this.loading = false }))\n"
                + "    },\n"
                + "    loadCard(card) {\n"
                + "      const base = card.apiBase || ('/' + card.module)\n"
                + "      return request({ url: base + '/stats', method: 'get',\n"
                + "        params: { field: card.field, metric: card.metric, range: card.range } })\n"
                + "        .then(res => { const data = (res && res.data) || res || {}; card.labels = data.labels || []; card.values = data.values || [] })\n"
                + "        .catch(err => { ElMessage.error('图表加载失败：' + card.title + ' ' + (err.message || '')); card.labels = []; card.values = [] })\n"
                + "    },\n"
                + "    drill(card) { this.$router.push({ path: '/' + card.module + '/list' }) }\n"
                + "  }\n"
                + "}\n"
                + "</script>\n\n"
                + "<style scoped>\n"
                + ".dashboard { padding: 4px; }\n"
                + ".dash-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }\n"
                + ".dash-head h2 { margin: 0; font-size: 20px; color: var(--app-primary); }\n"
                + ".dash-head p { margin: 4px 0 0; color: #9ca3af; font-size: 12px; }\n"
                + ".dash-empty { height: 240px; display: grid; place-items: center; color: #9ca3af; }\n"
                + "</style>\n";
    }

    /** 生成 src/api/chart.js（图卡取数封装，路径不硬编码 /api，用模块级相对路径由看板直接拼）。 */
    public static String chartApiJs() {
        return "// 数据看板取数封装（V9.15 自动生成）\n"
                + "import request from '@/utils/request'\n\n"
                + "export function getChartStats(module, params) {\n"
                + "  return request({ url: '/' + module + '/stats', method: 'get', params })\n"
                + "}\n";
    }

    /** 看板路由片段（系统级 1 份，供 ZIP 说明；真正路由由 route-sync 或 scaffold router 注入）。 */
    public static String routerSnippet() {
        return "// V9.15 数据看板路由：追加到 src/router/index.js 主布局 children\n"
                + "{ path: 'dashboard', name: 'Dashboard', component: () => import('@/views/dashboard/index.vue'), meta: { title: '数据看板' } }\n";
    }

    private static String js(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ");
    }
}

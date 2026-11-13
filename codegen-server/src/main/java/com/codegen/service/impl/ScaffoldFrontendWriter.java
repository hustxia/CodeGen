package com.codegen.service.impl;

import com.codegen.common.exception.BusinessException;
import com.codegen.common.log.Ip2RegionSearcher;
import com.codegen.config.GenOutputProperties;
import com.codegen.config.I18nCatalog;
import com.codegen.config.LanguageConfig;
import com.codegen.dto.GenScaffoldDTO;
import com.codegen.service.GenScaffoldService;
import com.codegen.service.I18nMessageService;
import com.codegen.service.I18nTextRewriter;
import com.codegen.support.GenNaming;
import com.codegen.support.GenPathResolver;
import com.codegen.vo.GenScaffoldCheckVO;
import com.codegen.vo.GenScaffoldVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import static com.codegen.service.impl.ScaffoldTextSupport.loadScaffoldResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * V9.1 拆分自 {@link GenScaffoldServiceImpl}：前端骨架（package.json/vite/main.js/layout/login/router/store 等）产物字符串工厂。
 * 纯函数集合，无实例状态；产物内容与拆分前逐字节一致。
 */
final class ScaffoldFrontendWriter {

    static String frontendLoginVue(String appName, String appLogo, String themeColor) {
        // V6.4：登录页带算术验证码（GET /auth/captcha，60s 一次性，点图刷新）
        // V6.8：品牌 Logo 置于标题上方（空则整行不出现，不留空洞）
        String logoTag = appLogo == null || appLogo.isBlank() ? ""
                : "      <img class=\"login-logo\" src=\"" + appLogo + "\" alt=\"" + appName + "\" />\n";
        return "<template>\n"
                + "  <div class=\"login-wrap\">\n"
                + "    <el-card class=\"login-card\">\n"
                + logoTag
                + "      <h2>" + appName + "</h2>\n"
                + "      <el-form @submit.prevent=\"doLogin\">\n"
                + "        <el-form-item>\n"
                + "          <el-input v-model=\"form.username\" placeholder=\"用户名\" />\n"
                + "        </el-form-item>\n"
                + "        <el-form-item>\n"
                + "          <el-input v-model=\"form.password\" type=\"password\" placeholder=\"密码\" show-password />\n"
                + "        </el-form-item>\n"
                + "        <el-form-item>\n"
                + "          <div class=\"captcha-row\">\n"
                + "            <el-input v-model=\"form.captchaAnswer\" placeholder=\"计算结果\" @keyup.enter=\"doLogin\" />\n"
                + "            <img v-if=\"captchaImage\" :src=\"captchaImage\" class=\"captcha-img\" title=\"点击刷新\" @click=\"loadCaptcha\" />\n"
                + "          </div>\n"
                + "        </el-form-item>\n"
                + "        <el-button type=\"primary\" :loading=\"loading\" native-type=\"submit\" style=\"width:100%; background:" + themeColor + "; border-color:" + themeColor + "\">登 录</el-button>\n"
                + "      </el-form>\n"
                + "      <p class=\"tip\">默认账号 admin / admin123</p>\n"
                + "    </el-card>\n"
                + "  </div>\n"
                + "</template>\n\n"
                + "<script>\n"
                + "import request from '@/utils/request'\n\n"
                + "export default {\n"
                + "  name: 'Login',\n"
                + "  data() {\n"
                + "    return {\n"
                + "      form: { username: 'admin', password: '', captchaId: '', captchaAnswer: '' },\n"
                + "      captchaImage: '',\n"
                + "      loading: false\n"
                + "    }\n"
                + "  },\n"
                + "  created() {\n"
                + "    this.loadCaptcha()\n"
                + "  },\n"
                + "  methods: {\n"
                + "    async loadCaptcha() {\n"
                + "      const res = await request.get('/auth/captcha')\n"
                + "      this.captchaImage = res.data.image\n"
                + "      this.form.captchaId = res.data.captchaId\n"
                + "      this.form.captchaAnswer = ''\n"
                + "    },\n"
                + "    async doLogin() {\n"
                + "      if (!this.form.username || !this.form.password) return\n"
                + "      if (!this.form.captchaAnswer) {\n"
                + "        this.$message.warning('请输入验证码')\n"
                + "        return\n"
                + "      }\n"
                + "      this.loading = true\n"
                + "      try {\n"
                + "        const res = await request.post('/auth/login', this.form)\n"
                + "        localStorage.setItem('token', res.data.token)\n"
                + "        localStorage.setItem('username', res.data.username)\n"
                + "        // V6.4：perms 落地供 v-hasPermi 指令与后端权限校验对齐\n"
                + "        localStorage.setItem('perms', JSON.stringify(res.data.perms || []))\n"
                + "        this.$router.push('/')\n"
                + "      } catch (e) {\n"
                + "        // 验证码一次性：失败（含密码错）后必须换新码，否则重放必错\n"
                + "        this.loadCaptcha()\n"
                + "      } finally {\n"
                + "        this.loading = false\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}\n"
                + "</script>\n\n"
                + "<style scoped>\n"
                + ".login-wrap { display: flex; align-items: center; justify-content: center; height: 100vh; background: #f0f2f5; }\n"
                + ".login-card { width: 360px; text-align: center; }\n"
                + ".login-logo { width: 56px; height: 56px; object-fit: contain; margin-bottom: 6px; }\n"
                + ".captcha-row { display: flex; gap: 8px; width: 100%; }\n"
                + ".captcha-img { height: 40px; cursor: pointer; border-radius: 4px; }\n"
                + ".tip { color: #999; font-size: 12px; margin-bottom: 0; }\n"
                + "</style>\n";
    }

    static String frontendPackageJson(String project, String module, boolean payEnabled) {
        // V6.3：收银台二维码由前端 qrcode 生成（后端零图像依赖），随能力包开关注入依赖
        String qrcodeDep = payEnabled ? ",\n    \"qrcode\": \"^1.5.4\"" : "";
        return "{\n"
                + "  \"name\": \"" + module + "\",\n"
                + "  \"version\": \"1.0.0\",\n"
                + "  \"private\": true,\n"
                + "  \"type\": \"module\",\n"
                + "  \"description\": \"" + project + " 前端（由 codegen-server 脚手架生成）\",\n"
                + "  \"scripts\": {\n"
                + "    \"dev\": \"vite\",\n"
                + "    \"build\": \"vite build\",\n"
                + "    \"preview\": \"vite preview\"\n"
                + "  },\n"
                + "  \"dependencies\": {\n"
                + "    \"axios\": \"^1.6.8\",\n"
                + "    \"@element-plus/icons-vue\": \"^2.3.1\",\n"
                + "    \"element-plus\": \"^2.7.0\",\n"
                + "    \"vue\": \"^3.4.21\",\n"
                + "    \"vue-router\": \"^4.3.0\",\n"
                + "    \"vuex\": \"^4.1.0\""
                + qrcodeDep + "\n"
                + "  },\n"
                + "  \"devDependencies\": {\n"
                + "    \"@vitejs/plugin-vue\": \"^5.0.4\",\n"
                + "    \"vite\": \"^5.2.0\"\n"
                + "  }\n"
                + "}\n";
    }

    static String frontendViteConfig(int webPort, int serverPort, String gatewayPrefix, boolean fileEnabled) {
        // V9.0：Vite 配置（替代 vue-cli 的 vue.config.js）。
        // dev 代理把 /dev-api 前缀剥掉；history 路由回退由 Vite SPA 默认处理（无需 historyApiFallback）。
        // V6.6 文件能力包：图片直链 /uploads/** 在后端，dev 必须单独代理（不剥任何前缀）
        // V6.8 素材链路：/img/** 是产物自带的静态素材（resources/static/img），dev 必须代理；
        // 漏了这一条整页图片都会落到 dev server 自己身上变成 404 → 全站占位图
        String imgProxy = ",\n      // 静态素材（后端 resources/static/img），漏配会整页显示占位图\n"
                + "      '/img': {\n"
                + "        target: 'http://127.0.0.1:" + serverPort + "',\n"
                + "        changeOrigin: true\n"
                + "      }";
        String uploadsProxy = fileEnabled
                ? ",\n      '/uploads': {\n"
                + "        target: 'http://127.0.0.1:" + serverPort + "',\n"
                + "        changeOrigin: true\n"
                + "      }" : "";
        return "import { defineConfig } from 'vite'\n"
                + "import vue from '@vitejs/plugin-vue'\n"
                + "import { fileURLToPath, URL } from 'node:url'\n\n"
                + "export default defineConfig({\n"
                + "  plugins: [vue()],\n"
                // V9.0：路由/组件用 @/ 别名（vue-cli 隐式内置，Vite 必须显式声明，否则 build 报 Rollup resolve 失败）
                + "  resolve: {\n"
                + "    alias: {\n"
                + "      '@': fileURLToPath(new URL('./src', import.meta.url))\n"
                + "    }\n"
                + "  },\n"
                + "  server: {\n"
                + "    port: " + webPort + ",\n"
                + "    proxy: {\n"
                + "      '/dev-api': {\n"
                + "        target: 'http://127.0.0.1:" + serverPort + "',\n"
                + "        changeOrigin: true,\n"
                + "        rewrite: (p) => p.replace(/^\\/dev-api(\\/api\\/" + gatewayPrefix + ")?/, '')\n"
                + "      }" + imgProxy + uploadsProxy + "\n"
                + "    }\n"
                + "  }\n"
                + "})\n";
    }

    static String frontendBabelConfig() {
        return "module.exports = {\n"
                + "  presets: [\n"
                + "    '@vue/cli-plugin-babel/preset'\n"
                + "  ]\n"
                + "}\n";
    }

    static String frontendEnvDev(int serverPort) {
        return "# 开发环境\n"
                + "ENV = 'development'\n\n"
                + "VITE_APP_BASE_API = '/dev-api'\n"
                + "VITE_APP_PROXY_TARGET = 'http://127.0.0.1:" + serverPort + "'\n";
    }

    static String frontendEnvProd() {
        return "# 生产环境\n"
                + "ENV = 'production'\n\n"
                + "VITE_APP_BASE_API = '/prod-api'\n";
    }

    static String frontendIndexHtml(String appName, String appLogo, String themeColor,
                                     String htmlLang, String htmlDir) {
        return frontendIndexHtml(appName, appLogo, themeColor, htmlLang, htmlDir, false);
    }

    /** V8.0：i18nOn 时补一段 RTL 镜像样式；关开关时输出与 V7.5 逐字节一致 */

    /** V8.0：i18nOn 时补一段 RTL 镜像样式；关开关时输出与 V7.5 逐字节一致 */
    static String frontendIndexHtml(String appName, String appLogo, String themeColor,
                                     String htmlLang, String htmlDir, boolean i18nOn) {
        // favicon 必须指向真的存在的文件：空 Logo 回落 placeholder.svg（脚手架必写），
        // 历史上写死的 logo.png 从来没有被生成过，等于每个新工程都挂一个 404
        String favicon = appLogo == null || appLogo.isBlank()
                ? "/" + com.codegen.util.GenTemplateModel.FALLBACK_APP_LOGO : appLogo;
        String rtlCss = i18nOn
                ? "  <style>[dir='rtl'] .header-right { flex-direction: row-reverse; }"
                + "[dir='rtl'] .content, [dir='rtl'] .el-table, [dir='rtl'] .el-form-item__label { direction: rtl; text-align: right; }"
                + "[dir='rtl'] code, [dir='rtl'] pre, [dir='rtl'] .path { direction: ltr; text-align: left; }"
                + "[dir='rtl'] .lang-switch { margin-inline-start: 0; margin-inline-end: 12px; }</style>\n"
                : "";
        return "<!DOCTYPE html>\n"
                + "<html lang=\"" + htmlLang + "\"" + ("rtl".equals(htmlDir) ? " dir=\"rtl\"" : "") + ">\n"
                + "<head>\n"
                + "  <meta charset=\"utf-8\">\n"
                + "  <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">\n"
                + "  <title>" + appName + "</title>\n"
                + "  <link rel=\"icon\" href=\"" + favicon + "\">\n"
                + "  <style>:root { --app-primary: " + themeColor + "; }</style>\n"
                + rtlCss
                + "</head>\n"
                + "<body>\n"
                + "  <noscript>请启用 JavaScript 后继续访问。</noscript>\n"
                + "  <div id=\"app\"></div>\n"
                // V9.0：Vite 靠这个 module script 找应用入口（webpack 时代由 vue-cli 插件自动注入）
                + "  <script type=\"module\" src=\"/src/main.js\"></script>\n"
                + "</body>\n"
                + "</html>\n";
    }

    static String frontendMainJs() {
        return frontendMainJs(false);
    }

    /** V9.0：Vue 3 createApp；开关打开时全局注册 $t / $setLocale 与切换器组件 */

    /** V9.0：Vue 3 createApp；开关打开时全局注册 $t / $setLocale 与切换器组件 */
    static String frontendMainJs(boolean i18nOn) {
        // V6.4：注册 v-hasPermi 指令——perms 与后端权限点校验同源（登录响应下发），前后端一致
        String i18nImports = i18nOn
                ? "import i18n, { t, setLocale, availableLocales } from './utils/i18n'\n"
                + "import LanguageSwitch from './components/LanguageSwitch.vue'\n"
                : "";
        String i18nInit = i18nOn
                ? "// V9.0 产物国际化：reactive 的 state 让所有 $t 调用点响应式重渲染\n"
                + "app.component('LanguageSwitch', LanguageSwitch)\n"
                + "app.config.globalProperties.$t = t\n"
                + "app.config.globalProperties.$setLocale = setLocale\n"
                + "app.config.globalProperties.$i18nLocales = availableLocales()\n"
                + "app.config.globalProperties.$i18n = i18n\n"
                + "window.$t = t\n\n"
                : "";
        return "import { createApp } from 'vue'\n"
                + "import ElementPlus from 'element-plus'\n"
                + "import 'element-plus/dist/index.css'\n"
                + "import * as ElementPlusIconsVue from '@element-plus/icons-vue'\n"
                + "import App from './App.vue'\n"
                + "import router from './router'\n"
                + "import store from './store'\n"
                + i18nImports + "\n"
                + "const app = createApp(App)\n"
                + "for (const [key, component] of Object.entries(ElementPlusIconsVue)) {\n"
                + "  app.component(key, component)\n"
                + "}\n"
                + "app.use(ElementPlus, { size: 'small' })\n"
                + i18nInit
                + "// V6.4 按钮级权限：v-hasPermi=\"['demo_fee:add']\"，命中任一即显示；超管 *:*:* 直通\n"
                + "app.directive('hasPermi', {\n"
                + "  mounted(el, binding) {\n"
                + "    let perms = []\n"
                + "    try { perms = JSON.parse(localStorage.getItem('perms') || '[]') } catch (e) { perms = [] }\n"
                + "    const need = binding.value || []\n"
                + "    const ok = perms.includes('*:*:*') || need.some(p => perms.includes(p)\n"
                + "      || perms.includes(p.replace(/:[^:]*$/, ':*')))\n"
                + "    if (!ok) {\n"
                + "      el.parentNode && el.parentNode.removeChild(el)\n"
                + "    }\n"
                + "  }\n"
                + "})\n\n"
                + "app.use(router)\n"
                + "app.use(store)\n"
                + "app.mount('#app')\n";
    }

    static String frontendAppVue() {
        return "<template>\n"
                + "  <div id=\"app\">\n"
                + "    <router-view />\n"
                + "  </div>\n"
                + "</template>\n\n"
                + "<script>\n"
                + "export default {\n"
                + "  name: 'App'\n"
                + "}\n"
                + "</script>\n\n"
                + "<style>\n"
                + "html,\n"
                + "body,\n"
                + "#app {\n"
                + "  height: 100%;\n"
                + "  margin: 0;\n"
                + "  padding: 0;\n"
                + "}\n"
                + "</style>\n";
    }

    static String frontendRequestJs() {
        return "import axios from 'axios'\n"
                + "import { ElMessage } from 'element-plus'\n\n"
                + "const service = axios.create({\n"
                + "  baseURL: import.meta.env.VITE_APP_BASE_API,\n"
                + "  timeout: 15000\n"
                + "})\n\n"
                + "service.interceptors.request.use(\n"
                + "  config => {\n"
                + "    const token = localStorage.getItem('token')\n"
                + "    if (token) {\n"
                + "      config.headers['Authorization'] = 'Bearer ' + token\n"
                + "    }\n"
                + "    return config\n"
                + "  },\n"
                + "  error => Promise.reject(error)\n"
                + ")\n\n"
                + "service.interceptors.response.use(\n"
                + "  response => {\n"
                + "    if (response.config.responseType === 'blob') {\n"
                + "      return response\n"
                + "    }\n"
                + "    const res = response.data\n"
                + "    if (res.code !== 0 && res.code !== 200) {\n"
                + "      ElMessage({ message: res.msg || '请求失败', type: 'error', duration: 5000 })\n"
                + "      return Promise.reject(new Error(res.msg || '请求失败'))\n"
                + "    }\n"
                + "    return res\n"
                + "  },\n"
                + "  error => {\n"
                + "    const resp = error.response\n"
                + "    if (resp && resp.status === 401) {\n"
                + "      localStorage.removeItem('token')\n"
                + "      window.location.href = '/login'\n"
                + "      return Promise.reject(new Error('登录状态已失效'))\n"
                + "    }\n"
                + "    ElMessage({ message: (resp && resp.data && resp.data.msg) || error.message, type: 'error', duration: 5000 })\n"
                + "    return Promise.reject(error)\n"
                + "  }\n"
                + ")\n\n"
                + "// MyBatis-Plus Page {records,total} -> {list,total}\n"
                + "export function toPageData(res) {\n"
                + "  const page = res.data || {}\n"
                + "  return {\n"
                + "    code: res.code,\n"
                + "    msg: res.msg,\n"
                + "    data: { list: page.records || [], total: page.total || 0 }\n"
                + "  }\n"
                + "}\n\n"
                + "export default service\n";
    }

    static String frontendLayoutVue(String appName, String appLogo, String themeColor) {
        return frontendLayoutVue(appName, appLogo, themeColor, false);
    }

    /** V8.0：开关打开时把 header 占位符换成全局注册的切换器；关闭时占位符被清空，字节零 diff */

    /** V8.0：开关打开时把 header 占位符换成全局注册的切换器；关闭时占位符被清空，字节零 diff */
    static String frontendLayoutVue(String appName, String appLogo, String themeColor, boolean i18nOn) {
        String name = appName == null || appName.isBlank() ? "业务系统" : appName;
        String initial = name.length() > 1 ? name.substring(0, 1) : name;
        // 品牌区二选一：配了 Logo 用图，没配用首字标识（绝不留空占位）
        String brandMark = appLogo == null || appLogo.isBlank()
                ? "<div class=\"brand-mark\">" + initial + "</div>"
                : "<img class=\"brand-logo\" src=\"" + appLogo + "\" alt=\"" + name + "\">";
        String switchTag = i18nOn ? "\n          <language-switch />" : "";
        return loadScaffoldResource("scaffold/web/layout.vue")
                .replace("__BRAND_MARK__", brandMark)
                .replace("__PROJECT__", name)
                .replace("__THEME_COLOR__", themeColor)
                .replace("__I18N_SWITCH__", switchTag);
    }

    /**
     * V8.0 产物国际化资源：语言包 + 聚合入口 + 零依赖运行时 + 切换器。
     *
     * <p>文件名来自 {@link I18nCatalog#resolveLocales(String, String)} 的白名单标签
     * （{@code ^[A-Za-z0-9-]{2,32}$} 先验），拼路径前已过滤，不存在穿越风险。
     * 所有文件仍走 {@link #write}，备份 / 台账 / 受保护跳过三件套一个不少。
     */

    static String frontendHomeVue(String project) {
        return "<template>\n"
                + "  <div class=\"home\">\n"
                + "    <el-card shadow=\"never\">\n"
                + "      <template #header>" + project + " 已就绪</template>\n"
                + "      <p>这是一个由 codegen-server 生成的空工程。</p>\n"
                + "      <p>下一步：在平台的「代码生成」里导入表配置，生成页面后会自动落到 src/views 下。</p>\n"
                + "      <el-button type=\"primary\" size=\"small\" @click=\"ping\">测试后端连通</el-button>\n"
                + "      <span class=\"result\">{{ result }}</span>\n"
                + "    </el-card>\n"
                + "  </div>\n"
                + "</template>\n\n"
                + "<script>\n"
                + "import request from '@/utils/request'\n\n"
                + "export default {\n"
                + "  name: 'Home',\n"
                + "  data() {\n"
                + "    return { result: '' }\n"
                + "  },\n"
                + "  methods: {\n"
                + "    ping() {\n"
                + "      request({ url: '/health', method: 'get' })\n"
                + "        .then(res => { this.result = '连通正常：' + JSON.stringify(res.data) })\n"
                + "        .catch(() => { this.result = '请求失败，检查后端是否启动' })\n"
                + "    }\n"
                + "  }\n"
                + "}\n"
                + "</script>\n\n"
                + "<style scoped>\n"
                + ".home {\n"
                + "  padding: 8px;\n"
                + "}\n"
                + ".result {\n"
                + "  margin-left: 12px;\n"
                + "  color: #67c23a;\n"
                + "}\n"
                + "</style>\n";
    }

    static String frontendRouterJs() {
        return "import { createRouter, createWebHistory } from 'vue-router'\n"
                + "import Layout from '@/layout/index.vue'\n"
                // gen-routes.js 由 /gen/menu/route-sync 重建（系统 → 业务域 → 页面三级）；
                // 侧边栏菜单树从 $router.options.routes 推导，不把它合进来菜单永远是空白
                + "import genRoutes from './gen-routes'\n\n"
                + "const routes = [\n"
                + "  {\n"
                + "    path: '/login',\n"
                + "    name: 'Login',\n"
                + "    component: () => import('@/views/login.vue'),\n"
                + "    meta: { title: '登录' }\n"
                + "  },\n"
                + "  {\n"
                + "    path: '/',\n"
                + "    component: Layout,\n"
                + "    redirect: '/home',\n"
                + "    children: [\n"
                + "      {\n"
                + "        path: 'home',\n"
                + "        name: 'Home',\n"
                + "        component: () => import('@/views/index.vue'),\n"
                + "        meta: { title: '首页' }\n"
                + "      }\n"
                + "    ]\n"
                + "  },\n"
                + "  ...genRoutes\n"
                + "]\n\n"
                + "const router = createRouter({\n"
                + "  history: createWebHistory(),\n"
                + "  routes\n"
                + "})\n\n"
                + "// 未登录一律踢到 /login；401 时 request.js 会清 token 并跳这里\n"
                + "router.beforeEach((to, from, next) => {\n"
                + "  const token = localStorage.getItem('token')\n"
                + "  if (to.path !== '/login' && !token) {\n"
                + "    next('/login')\n"
                + "  } else {\n"
                + "    next()\n"
                + "  }\n"
                + "})\n\n"
                + "export default router\n";
    }

    static String frontendStoreJs() {
        return "import { createStore } from 'vuex'\n\n"
                + "export default createStore({\n"
                + "  state: {\n"
                + "    user: null\n"
                + "  },\n"
                + "  mutations: {\n"
                + "    SET_USER(state, user) {\n"
                + "      state.user = user\n"
                + "    }\n"
                + "  }\n"
                + "})\n";
    }

    static String frontendGitignore() {
        return "node_modules/\n"
                + "dist/\n"
                + "logs/\n"
                + "*.log\n"
                + ".DS_Store\n"
                + ".idea/\n"
                + ".vscode/\n"
                + ".gen-artifacts/\n"
                + ".gen-verify/\n";
    }

    static String frontendReadme(String project, String module, int webPort, int serverPort) {
        return "# " + project + " 前端（" + module + "）\n\n"
                + "Vue 3 + Element Plus，由 codegen-server 脚手架生成。\n\n"
                + "## 快速开始\n\n"
                + "```bash\n"
                + "npm install\n"
                + "npm run serve\n"
                + "```\n\n"
                + "访问 `http://127.0.0.1:" + webPort + "`，`/dev-api` 已代理到后端 `http://127.0.0.1:" + serverPort + "`。\n\n"
                + "## 已内置\n\n"
                + "- `vite.config.js` 内置 SPA history 回退（刷新页面不会 404）\n"
                + "- `utils/request.js` 统一注入 token、按 `code` 判成败、401 回登录页\n"
                + "- `layout/index.vue` 侧边栏骨架\n";
    }
}

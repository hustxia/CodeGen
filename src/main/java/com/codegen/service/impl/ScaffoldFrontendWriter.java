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
                + "import zhCn from 'element-plus/es/locale/lang/zh-cn'\n"
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
                + "app.use(ElementPlus, { size: 'small', locale: zhCn })\n"
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
                + "// 演示系统：启动即自动签发会话，打开页面不需要登录\n"
                + "async function ensureSession() {\n"
                + "  if (localStorage.getItem('token')) return\n"
                + "  const base = import.meta.env.VITE_APP_BASE_API || ''\n"
                + "  try {\n"
                + "    const res = await fetch(base + '/auth/demo-login', {\n"
                + "      method: 'POST',\n"
                + "      headers: { 'Content-Type': 'application/json' },\n"
                + "      body: '{}'\n"
                + "    })\n"
                + "    const json = await res.json()\n"
                + "    if (json.code === 200 && json.data && json.data.token) {\n"
                + "      localStorage.setItem('token', json.data.token)\n"
                + "      localStorage.setItem('username', json.data.username || 'admin')\n"
                + "      localStorage.setItem('perms', JSON.stringify(json.data.perms || ['*:*:*']))\n"
                + "    }\n"
                + "  } catch (e) {\n"
                + "    console.warn('[demo] 自动会话签发失败，页面仍可浏览：', e)\n"
                + "  }\n"
                + "}\n\n"
                + "ensureSession().finally(() => {\n"
                + "  app.use(router)\n"
                + "  app.use(store)\n"
                + "  app.mount('#app')\n"
                + "})\n";
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
                + "      return Promise.reject(new Error('会话已失效，请刷新页面'))\n"
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
        return frontendRouterJs(false);
    }

    /** V9.12：withAuth 时追加系统管理路由（页面只在开启鉴权时生成）。 */
    static String frontendRouterJs(boolean withAuth) {
        String systemRoutes = withAuth
                ? ",\n"
                + "  {\n"
                + "    path: '/system',\n"
                + "    component: Layout,\n"
                + "    meta: { title: '系统管理', icon: 'Setting' },\n"
                + "    children: [\n"
                + "      {\n"
                + "        path: 'user',\n"
                + "        name: 'SysUser',\n"
                + "        component: () => import('@/views/system/user.vue'),\n"
                + "        meta: { title: '用户管理' }\n"
                + "      },\n"
                + "      {\n"
                + "        path: 'role',\n"
                + "        name: 'SysRole',\n"
                + "        component: () => import('@/views/system/role.vue'),\n"
                + "        meta: { title: '角色管理' }\n"
                + "      },\n"
                + "      {\n"
                + "        path: 'menu',\n"
                + "        name: 'SysMenu',\n"
                + "        component: () => import('@/views/system/menu.vue'),\n"
                + "        meta: { title: '菜单管理' }\n"
                + "      }\n"
                + "    ]\n"
                + "  }"
                : "";
        return "import { createRouter, createWebHistory } from 'vue-router'\n"
                + "import Layout from '@/layout/index.vue'\n"
                // gen-routes.js 由 /gen/menu/route-sync 重建（系统 → 业务域 → 页面三级）；
                // 侧边栏菜单树从 $router.options.routes 推导，不把它合进来菜单永远是空白
                + "import genRoutes from './gen-routes'\n\n"
                + "const routes = [\n"
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
                + "  ...genRoutes"
                // V9.12：系统管理（用户/角色/菜单）静态路由。放在 genRoutes 之后，
                // 避免抢占「系统壳 = 第一个带 children 的路由」的侧边栏推导规则
                + systemRoutes
                + "\n"
                + "]\n\n"
                + "const router = createRouter({\n"
                + "  history: createWebHistory(),\n"
                + "  routes\n"
                + "})\n\n"
                + "// 演示系统免登录：会话由 main.js 启动时自动签发，无需登录页与跳转守卫\n"
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

    /** V9.12：RBAC 管理接口封装（用户/角色/菜单三组）。 */
    static String frontendApiSystemJs() {
        return "import request from '@/utils/request'\n\n"
                + "export const sysUserApi = {\n"
                + "  page: (data) => request.post('/sysUser/page', data),\n"
                + "  save: (data) => request.post('/sysUser/save', data),\n"
                + "  update: (data) => request.post('/sysUser/update', data),\n"
                + "  remove: (data) => request.post('/sysUser/remove', data),\n"
                + "  resetPwd: (data) => request.post('/sysUser/resetPwd', data),\n"
                + "  roles: (data) => request.post('/sysUser/roles', data),\n"
                + "  assignRoles: (data) => request.post('/sysUser/assignRoles', data)\n"
                + "}\n\n"
                + "export const sysRoleApi = {\n"
                + "  page: (data) => request.post('/sysRole/page', data),\n"
                + "  list: () => request.post('/sysRole/list', {}),\n"
                + "  save: (data) => request.post('/sysRole/save', data),\n"
                + "  update: (data) => request.post('/sysRole/update', data),\n"
                + "  remove: (data) => request.post('/sysRole/remove', data),\n"
                + "  menus: (data) => request.post('/sysRole/menus', data),\n"
                + "  assignMenus: (data) => request.post('/sysRole/assignMenus', data)\n"
                + "}\n\n"
                + "export const sysMenuApi = {\n"
                + "  list: () => request.post('/sysMenu/list', {}),\n"
                + "  save: (data) => request.post('/sysMenu/save', data),\n"
                + "  update: (data) => request.post('/sysMenu/update', data),\n"
                + "  remove: (data) => request.post('/sysMenu/remove', data)\n"
                + "}\n";
    }

    /** V9.12：用户管理页（含分配角色、重置密码）。 */
    static String frontendSystemUserVue() {
        return "<template>\n"
                + "  <div class=\"app-container\">\n"
                + "    <el-card shadow=\"never\">\n"
                + "      <div class=\"toolbar\">\n"
                + "        <el-input v-model=\"query.keyword\" placeholder=\"用户名 / 昵称\" clearable style=\"width:220px\" @keyup.enter=\"load\" />\n"
                + "        <el-button type=\"primary\" @click=\"load\">查询</el-button>\n"
                + "        <el-button @click=\"openDialog()\">新增用户</el-button>\n"
                + "      </div>\n"
                + "      <el-table v-loading=\"loading\" :data=\"list\" stripe>\n"
                + "        <el-table-column prop=\"id\" label=\"ID\" width=\"80\" />\n"
                + "        <el-table-column prop=\"username\" label=\"用户名\" min-width=\"140\" />\n"
                + "        <el-table-column prop=\"nickname\" label=\"昵称\" min-width=\"140\" />\n"
                + "        <el-table-column prop=\"roleNames\" label=\"角色\" min-width=\"160\" />\n"
                + "        <el-table-column prop=\"createTime\" label=\"创建时间\" width=\"180\" />\n"
                + "        <el-table-column label=\"操作\" width=\"300\" fixed=\"right\">\n"
                + "          <template #default=\"{ row }\">\n"
                + "            <el-button link type=\"primary\" @click=\"openDialog(row)\">编辑</el-button>\n"
                + "            <el-button link type=\"primary\" @click=\"openRoles(row)\">分配角色</el-button>\n"
                + "            <el-button link type=\"warning\" @click=\"resetPwd(row)\">重置密码</el-button>\n"
                + "            <el-button link type=\"danger\" :disabled=\"row.username === 'admin'\" @click=\"remove(row)\">删除</el-button>\n"
                + "          </template>\n"
                + "        </el-table-column>\n"
                + "      </el-table>\n"
                + "      <el-pagination class=\"pagination\" background layout=\"total, sizes, prev, pager, next\"\n"
                + "        :total=\"total\" :page-sizes=\"[10, 20, 50]\" v-model:page-size=\"query.pageSize\"\n"
                + "        v-model:current-page=\"query.pageNum\" @size-change=\"load\" @current-change=\"load\" />\n"
                + "    </el-card>\n\n"
                + "    <el-dialog v-model=\"dialogVisible\" :title=\"form.id ? '编辑用户' : '新增用户'\" width=\"480px\">\n"
                + "      <el-form ref=\"formRef\" :model=\"form\" :rules=\"rules\" label-width=\"90px\">\n"
                + "        <el-form-item label=\"用户名\" prop=\"username\">\n"
                + "          <el-input v-model=\"form.username\" :disabled=\"!!form.id\" placeholder=\"登录名\" />\n"
                + "        </el-form-item>\n"
                + "        <el-form-item label=\"昵称\"><el-input v-model=\"form.nickname\" /></el-form-item>\n"
                + "        <el-form-item v-if=\"!form.id\" label=\"初始密码\">\n"
                + "          <el-input v-model=\"form.password\" placeholder=\"留空默认 123456\" />\n"
                + "        </el-form-item>\n"
                + "      </el-form>\n"
                + "      <template #footer>\n"
                + "        <el-button @click=\"dialogVisible = false\">取消</el-button>\n"
                + "        <el-button type=\"primary\" :loading=\"saving\" @click=\"submit\">保存</el-button>\n"
                + "      </template>\n"
                + "    </el-dialog>\n\n"
                + "    <el-dialog v-model=\"roleDialog\" title=\"分配角色\" width=\"420px\">\n"
                + "      <el-checkbox-group v-model=\"checkedRoles\">\n"
                + "        <el-checkbox v-for=\"r in roleOptions\" :key=\"r.id\" :value=\"r.id\">{{ r.roleName }}</el-checkbox>\n"
                + "      </el-checkbox-group>\n"
                + "      <template #footer>\n"
                + "        <el-button @click=\"roleDialog = false\">取消</el-button>\n"
                + "        <el-button type=\"primary\" :loading=\"saving\" @click=\"submitRoles\">保存</el-button>\n"
                + "      </template>\n"
                + "    </el-dialog>\n"
                + "  </div>\n"
                + "</template>\n\n"
                + "<script setup>\n"
                + "import { ref, reactive, onMounted } from 'vue'\n"
                + "import { ElMessage, ElMessageBox } from 'element-plus'\n"
                + "import { sysUserApi, sysRoleApi } from '@/api/system'\n\n"
                + "const loading = ref(false)\n"
                + "const saving = ref(false)\n"
                + "const list = ref([])\n"
                + "const total = ref(0)\n"
                + "const roleOptions = ref([])\n"
                + "const query = reactive({ keyword: '', pageNum: 1, pageSize: 10 })\n"
                + "const dialogVisible = ref(false)\n"
                + "const roleDialog = ref(false)\n"
                + "const formRef = ref()\n"
                + "const form = reactive({ id: null, username: '', nickname: '', password: '' })\n"
                + "const rules = { username: [{ required: true, message: '请输入用户名', trigger: 'blur' }] }\n"
                + "const currentUser = ref(null)\n"
                + "const checkedRoles = ref([])\n\n"
                + "async function load() {\n"
                + "  loading.value = true\n"
                + "  try {\n"
                + "    const res = await sysUserApi.page({ ...query })\n"
                + "    list.value = res.data.records || []\n"
                + "    total.value = res.data.total || 0\n"
                + "  } finally { loading.value = false }\n"
                + "}\n\n"
                + "function openDialog(row) {\n"
                + "  Object.assign(form, { id: null, username: '', nickname: '', password: '' })\n"
                + "  if (row) Object.assign(form, { id: row.id, username: row.username, nickname: row.nickname })\n"
                + "  dialogVisible.value = true\n"
                + "}\n\n"
                + "async function submit() {\n"
                + "  await formRef.value.validate()\n"
                + "  saving.value = true\n"
                + "  try {\n"
                + "    if (form.id) await sysUserApi.update({ id: form.id, nickname: form.nickname })\n"
                + "    else await sysUserApi.save({ username: form.username, nickname: form.nickname, password: form.password })\n"
                + "    ElMessage.success('保存成功')\n"
                + "    dialogVisible.value = false\n"
                + "    load()\n"
                + "  } finally { saving.value = false }\n"
                + "}\n\n"
                + "async function openRoles(row) {\n"
                + "  currentUser.value = row\n"
                + "  const res = await sysRoleApi.list()\n"
                + "  roleOptions.value = res.data || []\n"
                + "  const mine = await sysUserApi.roles({ userId: row.id })\n"
                + "  checkedRoles.value = (mine.data || []).map(Number)\n"
                + "  roleDialog.value = true\n"
                + "}\n\n"
                + "async function submitRoles() {\n"
                + "  saving.value = true\n"
                + "  try {\n"
                + "    await sysUserApi.assignRoles({ userId: currentUser.value.id, roleIds: checkedRoles.value })\n"
                + "    ElMessage.success('授权成功')\n"
                + "    roleDialog.value = false\n"
                + "    load()\n"
                + "  } finally { saving.value = false }\n"
                + "}\n\n"
                + "async function resetPwd(row) {\n"
                + "  const { value } = await ElMessageBox.prompt('请输入新密码（留空则重置为 123456）', '重置密码', { inputValue: '' })\n"
                + "  await sysUserApi.resetPwd({ id: row.id, password: value || '' })\n"
                + "  ElMessage.success('密码已重置')\n"
                + "}\n\n"
                + "async function remove(row) {\n"
                + "  await ElMessageBox.confirm('确认删除用户 ' + row.username + '？', '提示', { type: 'warning' })\n"
                + "  await sysUserApi.remove({ id: row.id })\n"
                + "  ElMessage.success('已删除')\n"
                + "  load()\n"
                + "}\n\n"
                + "onMounted(load)\n"
                + "</script>\n\n"
                + "<style scoped>\n"
                + ".toolbar { display: flex; gap: 8px; margin-bottom: 12px; }\n"
                + ".pagination { margin-top: 12px; justify-content: flex-end; }\n"
                + "</style>\n";
    }

    /** V9.12：角色管理页（含角色菜单授权树）。 */
    static String frontendSystemRoleVue() {
        return "<template>\n"
                + "  <div class=\"app-container\">\n"
                + "    <el-card shadow=\"never\">\n"
                + "      <div class=\"toolbar\">\n"
                + "        <el-input v-model=\"query.keyword\" placeholder=\"角色名 / 标识\" clearable style=\"width:220px\" @keyup.enter=\"load\" />\n"
                + "        <el-button type=\"primary\" @click=\"load\">查询</el-button>\n"
                + "        <el-button @click=\"openDialog()\">新增角色</el-button>\n"
                + "      </div>\n"
                + "      <el-table v-loading=\"loading\" :data=\"list\" stripe>\n"
                + "        <el-table-column prop=\"id\" label=\"ID\" width=\"80\" />\n"
                + "        <el-table-column prop=\"roleName\" label=\"角色名称\" min-width=\"160\" />\n"
                + "        <el-table-column prop=\"roleKey\" label=\"角色标识\" min-width=\"160\" />\n"
                + "        <el-table-column prop=\"remark\" label=\"备注\" min-width=\"200\" />\n"
                + "        <el-table-column prop=\"createTime\" label=\"创建时间\" width=\"180\" />\n"
                + "        <el-table-column label=\"操作\" width=\"260\" fixed=\"right\">\n"
                + "          <template #default=\"{ row }\">\n"
                + "            <el-button link type=\"primary\" @click=\"openDialog(row)\">编辑</el-button>\n"
                + "            <el-button link type=\"primary\" :disabled=\"row.id === 1\" @click=\"openMenus(row)\">分配菜单</el-button>\n"
                + "            <el-button link type=\"danger\" :disabled=\"row.id === 1\" @click=\"remove(row)\">删除</el-button>\n"
                + "          </template>\n"
                + "        </el-table-column>\n"
                + "      </el-table>\n"
                + "      <el-pagination class=\"pagination\" background layout=\"total, sizes, prev, pager, next\"\n"
                + "        :total=\"total\" :page-sizes=\"[10, 20, 50]\" v-model:page-size=\"query.pageSize\"\n"
                + "        v-model:current-page=\"query.pageNum\" @size-change=\"load\" @current-change=\"load\" />\n"
                + "    </el-card>\n\n"
                + "    <el-dialog v-model=\"dialogVisible\" :title=\"form.id ? '编辑角色' : '新增角色'\" width=\"480px\">\n"
                + "      <el-form ref=\"formRef\" :model=\"form\" :rules=\"rules\" label-width=\"90px\">\n"
                + "        <el-form-item label=\"角色名称\" prop=\"roleName\"><el-input v-model=\"form.roleName\" /></el-form-item>\n"
                + "        <el-form-item label=\"角色标识\" prop=\"roleKey\"><el-input v-model=\"form.roleKey\" :disabled=\"!!form.id\" placeholder=\"如 village_admin\" /></el-form-item>\n"
                + "        <el-form-item label=\"备注\"><el-input v-model=\"form.remark\" type=\"textarea\" :rows=\"3\" /></el-form-item>\n"
                + "      </el-form>\n"
                + "      <template #footer>\n"
                + "        <el-button @click=\"dialogVisible = false\">取消</el-button>\n"
                + "        <el-button type=\"primary\" :loading=\"saving\" @click=\"submit\">保存</el-button>\n"
                + "      </template>\n"
                + "    </el-dialog>\n\n"
                + "    <el-dialog v-model=\"menuDialog\" title=\"分配菜单与权限点\" width=\"520px\">\n"
                + "      <el-tree ref=\"treeRef\" :data=\"menuTree\" node-key=\"id\" show-checkbox default-expand-all\n"
                + "        :props=\"{ label: 'name', children: 'children' }\" />\n"
                + "      <template #footer>\n"
                + "        <el-button @click=\"menuDialog = false\">取消</el-button>\n"
                + "        <el-button type=\"primary\" :loading=\"saving\" @click=\"submitMenus\">保存</el-button>\n"
                + "      </template>\n"
                + "    </el-dialog>\n"
                + "  </div>\n"
                + "</template>\n\n"
                + "<script setup>\n"
                + "import { ref, reactive, nextTick, onMounted } from 'vue'\n"
                + "import { ElMessage, ElMessageBox } from 'element-plus'\n"
                + "import { sysRoleApi, sysMenuApi } from '@/api/system'\n\n"
                + "const loading = ref(false)\n"
                + "const saving = ref(false)\n"
                + "const list = ref([])\n"
                + "const total = ref(0)\n"
                + "const query = reactive({ keyword: '', pageNum: 1, pageSize: 10 })\n"
                + "const dialogVisible = ref(false)\n"
                + "const menuDialog = ref(false)\n"
                + "const formRef = ref()\n"
                + "const treeRef = ref()\n"
                + "const form = reactive({ id: null, roleName: '', roleKey: '', remark: '' })\n"
                + "const rules = {\n"
                + "  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],\n"
                + "  roleKey: [{ required: true, message: '请输入角色标识', trigger: 'blur' }]\n"
                + "}\n"
                + "const menuTree = ref([])\n"
                + "const currentRole = ref(null)\n\n"
                + "function buildTree(rows) {\n"
                + "  const map = new Map()\n"
                + "  rows.forEach(r => map.set(r.id, { ...r, children: [] }))\n"
                + "  const roots = []\n"
                + "  map.forEach(node => {\n"
                + "    const parent = map.get(node.parentId)\n"
                + "    if (parent) parent.children.push(node)\n"
                + "    else roots.push(node)\n"
                + "  })\n"
                + "  return roots\n"
                + "}\n\n"
                + "async function load() {\n"
                + "  loading.value = true\n"
                + "  try {\n"
                + "    const res = await sysRoleApi.page({ ...query })\n"
                + "    list.value = res.data.records || []\n"
                + "    total.value = res.data.total || 0\n"
                + "  } finally { loading.value = false }\n"
                + "}\n\n"
                + "function openDialog(row) {\n"
                + "  Object.assign(form, { id: null, roleName: '', roleKey: '', remark: '' })\n"
                + "  if (row) Object.assign(form, { id: row.id, roleName: row.roleName, roleKey: row.roleKey, remark: row.remark })\n"
                + "  dialogVisible.value = true\n"
                + "}\n\n"
                + "async function submit() {\n"
                + "  await formRef.value.validate()\n"
                + "  saving.value = true\n"
                + "  try {\n"
                + "    if (form.id) await sysRoleApi.update({ id: form.id, roleName: form.roleName, remark: form.remark })\n"
                + "    else await sysRoleApi.save({ roleName: form.roleName, roleKey: form.roleKey, remark: form.remark })\n"
                + "    ElMessage.success('保存成功')\n"
                + "    dialogVisible.value = false\n"
                + "    load()\n"
                + "  } finally { saving.value = false }\n"
                + "}\n\n"
                + "async function openMenus(row) {\n"
                + "  currentRole.value = row\n"
                + "  const menus = await sysMenuApi.list()\n"
                + "  menuTree.value = buildTree(menus.data || [])\n"
                + "  const owned = await sysRoleApi.menus({ roleId: row.id })\n"
                + "  menuDialog.value = true\n"
                + "  await nextTick()\n"
                + "  // 只回显叶子节点：父节点由级联自动推导，避免整棵子树被误勾选\n"
                + "  const ownedSet = new Set((owned.data || []).map(Number))\n"
                + "  treeRef.value.setCheckedKeys(leafIds(menuTree.value).filter(id => ownedSet.has(id)), false)\n"
                + "}\n\n"
                + "function leafIds(nodes, acc) {\n"
                + "  const out = acc || []\n"
                + "  nodes.forEach(n => {\n"
                + "    if (n.children && n.children.length) leafIds(n.children, out)\n"
                + "    else out.push(n.id)\n"
                + "  })\n"
                + "  return out\n"
                + "}\n\n"
                + "async function submitMenus() {\n"
                + "  saving.value = true\n"
                + "  try {\n"
                + "    const checked = treeRef.value.getCheckedKeys()\n"
                + "    const half = treeRef.value.getHalfCheckedKeys()\n"
                + "    await sysRoleApi.assignMenus({ roleId: currentRole.value.id, menuIds: checked.concat(half) })\n"
                + "    ElMessage.success('授权成功')\n"
                + "    menuDialog.value = false\n"
                + "  } finally { saving.value = false }\n"
                + "}\n\n"
                + "async function remove(row) {\n"
                + "  await ElMessageBox.confirm('确认删除角色 ' + row.roleName + '？', '提示', { type: 'warning' })\n"
                + "  await sysRoleApi.remove({ id: row.id })\n"
                + "  ElMessage.success('已删除')\n"
                + "  load()\n"
                + "}\n\n"
                + "onMounted(load)\n"
                + "</script>\n\n"
                + "<style scoped>\n"
                + ".toolbar { display: flex; gap: 8px; margin-bottom: 12px; }\n"
                + ".pagination { margin-top: 12px; justify-content: flex-end; }\n"
                + "</style>\n";
    }

    /** V9.12：菜单与权限点管理页（树表 + 表单）。 */
    static String frontendSystemMenuVue() {
        return "<template>\n"
                + "  <div class=\"app-container\">\n"
                + "    <el-card shadow=\"never\">\n"
                + "      <div class=\"toolbar\">\n"
                + "        <el-button type=\"primary\" @click=\"openDialog()\">新增菜单</el-button>\n"
                + "        <el-button @click=\"load\">刷新</el-button>\n"
                + "      </div>\n"
                + "      <el-table v-loading=\"loading\" :data=\"tree\" row-key=\"id\" :tree-props=\"{ children: 'children' }\" default-expand-all>\n"
                + "        <el-table-column prop=\"name\" label=\"菜单名称\" min-width=\"200\" />\n"
                + "        <el-table-column prop=\"path\" label=\"路由\" min-width=\"180\" />\n"
                + "        <el-table-column prop=\"component\" label=\"组件\" min-width=\"160\" />\n"
                + "        <el-table-column prop=\"perms\" label=\"权限标识\" min-width=\"180\" />\n"
                + "        <el-table-column label=\"类型\" width=\"90\">\n"
                + "          <template #default=\"{ row }\">{{ typeText(row.type) }}</template>\n"
                + "        </el-table-column>\n"
                + "        <el-table-column prop=\"sort\" label=\"排序\" width=\"80\" />\n"
                + "        <el-table-column label=\"操作\" width=\"240\" fixed=\"right\">\n"
                + "          <template #default=\"{ row }\">\n"
                + "            <el-button link type=\"primary\" @click=\"openDialog(null, row.id)\">新增子项</el-button>\n"
                + "            <el-button link type=\"primary\" @click=\"openDialog(row)\">编辑</el-button>\n"
                + "            <el-button link type=\"danger\" @click=\"remove(row)\">删除</el-button>\n"
                + "          </template>\n"
                + "        </el-table-column>\n"
                + "      </el-table>\n"
                + "    </el-card>\n\n"
                + "    <el-dialog v-model=\"dialogVisible\" :title=\"form.id ? '编辑菜单' : '新增菜单'\" width=\"520px\">\n"
                + "      <el-form ref=\"formRef\" :model=\"form\" :rules=\"rules\" label-width=\"96px\">\n"
                + "        <el-form-item label=\"上级菜单\">\n"
                + "          <el-tree-select v-model=\"form.parentId\" :data=\"parentOptions\" check-strictly\n"
                + "            :props=\"{ label: 'name', children: 'children' }\" node-key=\"id\" placeholder=\"顶级菜单\" style=\"width:100%\" />\n"
                + "        </el-form-item>\n"
                + "        <el-form-item label=\"名称\" prop=\"name\"><el-input v-model=\"form.name\" /></el-form-item>\n"
                + "        <el-form-item label=\"类型\">\n"
                + "          <el-radio-group v-model=\"form.type\">\n"
                + "            <el-radio :value=\"1\">目录</el-radio>\n"
                + "            <el-radio :value=\"2\">菜单</el-radio>\n"
                + "            <el-radio :value=\"3\">按钮</el-radio>\n"
                + "          </el-radio-group>\n"
                + "        </el-form-item>\n"
                + "        <el-form-item label=\"路由\"><el-input v-model=\"form.path\" placeholder=\"/system/user\" /></el-form-item>\n"
                + "        <el-form-item label=\"组件\"><el-input v-model=\"form.component\" placeholder=\"system/user\" /></el-form-item>\n"
                + "        <el-form-item label=\"权限标识\"><el-input v-model=\"form.perms\" placeholder=\"sys_user:list\" /></el-form-item>\n"
                + "        <el-form-item label=\"排序\"><el-input-number v-model=\"form.sort\" :min=\"0\" /></el-form-item>\n"
                + "      </el-form>\n"
                + "      <template #footer>\n"
                + "        <el-button @click=\"dialogVisible = false\">取消</el-button>\n"
                + "        <el-button type=\"primary\" :loading=\"saving\" @click=\"submit\">保存</el-button>\n"
                + "      </template>\n"
                + "    </el-dialog>\n"
                + "  </div>\n"
                + "</template>\n\n"
                + "<script setup>\n"
                + "import { ref, reactive, computed, onMounted } from 'vue'\n"
                + "import { ElMessage, ElMessageBox } from 'element-plus'\n"
                + "import { sysMenuApi } from '@/api/system'\n\n"
                + "const loading = ref(false)\n"
                + "const saving = ref(false)\n"
                + "const flat = ref([])\n"
                + "const tree = ref([])\n"
                + "const dialogVisible = ref(false)\n"
                + "const formRef = ref()\n"
                + "const form = reactive({ id: null, parentId: 0, name: '', path: '', component: '', perms: '', type: 2, sort: 0, status: 1 })\n"
                + "const rules = { name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }] }\n\n"
                + "const parentOptions = computed(() => [{ id: 0, name: '顶级菜单', children: tree.value }])\n\n"
                + "function typeText(type) {\n"
                + "  return type === 1 ? '目录' : type === 3 ? '按钮' : '菜单'\n"
                + "}\n\n"
                + "function buildTree(rows) {\n"
                + "  const map = new Map()\n"
                + "  rows.forEach(r => map.set(r.id, { ...r, children: [] }))\n"
                + "  const roots = []\n"
                + "  map.forEach(node => {\n"
                + "    const parent = map.get(node.parentId)\n"
                + "    if (parent) parent.children.push(node)\n"
                + "    else roots.push(node)\n"
                + "  })\n"
                + "  return roots\n"
                + "}\n\n"
                + "async function load() {\n"
                + "  loading.value = true\n"
                + "  try {\n"
                + "    const res = await sysMenuApi.list()\n"
                + "    flat.value = res.data || []\n"
                + "    tree.value = buildTree(flat.value)\n"
                + "  } finally { loading.value = false }\n"
                + "}\n\n"
                + "function openDialog(row, parentId) {\n"
                + "  Object.assign(form, { id: null, parentId: parentId || 0, name: '', path: '', component: '', perms: '', type: 2, sort: 0, status: 1 })\n"
                + "  if (row) Object.assign(form, { id: row.id, parentId: row.parentId || 0, name: row.name, path: row.path, component: row.component, perms: row.perms, type: row.type, sort: row.sort, status: row.status })\n"
                + "  dialogVisible.value = true\n"
                + "}\n\n"
                + "async function submit() {\n"
                + "  await formRef.value.validate()\n"
                + "  saving.value = true\n"
                + "  try {\n"
                + "    const payload = { ...form }\n"
                + "    if (form.id) await sysMenuApi.update(payload)\n"
                + "    else await sysMenuApi.save(payload)\n"
                + "    ElMessage.success('保存成功')\n"
                + "    dialogVisible.value = false\n"
                + "    load()\n"
                + "  } finally { saving.value = false }\n"
                + "}\n\n"
                + "async function remove(row) {\n"
                + "  await ElMessageBox.confirm('确认删除菜单 ' + row.name + '？', '提示', { type: 'warning' })\n"
                + "  await sysMenuApi.remove({ id: row.id })\n"
                + "  ElMessage.success('已删除')\n"
                + "  load()\n"
                + "}\n\n"
                + "onMounted(load)\n"
                + "</script>\n\n"
                + "<style scoped>\n"
                + ".toolbar { display: flex; gap: 8px; margin-bottom: 12px; }\n"
                + "</style>\n";
    }

    static String frontendReadme(String project, String module, int webPort, int serverPort) {
        return "# " + project + " 前端（" + module + "）\n\n"
                + "Vue 3 + Element Plus，由 codegen-server 脚手架生成。\n\n"
                + "## 快速开始\n\n"
                + "```bash\n"
                + "npm install\n"
                + "npm run dev\n"
                + "```\n\n"
                + "访问 `http://127.0.0.1:" + webPort + "`，`/dev-api` 已代理到后端 `http://127.0.0.1:" + serverPort + "`。\n\n"
                + "## 已内置\n\n"
                + "- `vite.config.js` 内置 SPA history 回退（刷新页面不会 404）\n"
                + "- `utils/request.js` 统一注入 token、按 `code` 判成败、401 回登录页\n"
                + "- `layout/index.vue` 侧边栏骨架\n";
    }
}

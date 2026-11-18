<template>
  <el-select
    :value="current"
    size="small"
    class="lang-switch"
    @change="onChange"
  >
    <el-option
      v-for="l in locales"
      :key="l.locale"
      :label="l.label"
      :value="l.locale"
    >
      <span>{{ l.label }}</span>
      <span class="lang-native">{{ l.nameNative }}<el-icon v-if="l.dir === 'rtl'"><Refresh /></el-icon></span>
    </el-option>
  </el-select>
</template>

<script>
/**
 * V8.0 语言切换器：由 main.js 全局注册，布局模板只放 <language-switch /> 标签，
 * 避免 layout.vue 与路由表互相 import（历史上出过循环依赖白屏）。
 */
import { state, setLocale, availableLocales, t } from '../utils/i18n'

export default {
  name: 'LanguageSwitch',
  data: function () {
    return { locales: availableLocales() }
  },
  computed: {
    current: function () {
      return state.locale
    }
  },
  methods: {
    t: t,
    onChange: function (locale) {
      setLocale(locale)
    }
  }
}
</script>

<style scoped>
.lang-switch { width: 132px; margin-inline-start: 12px; }
.lang-native { float: right; color: #8492a6; font-size: 12px; }
[dir='rtl'] .lang-native { float: left; }
</style>

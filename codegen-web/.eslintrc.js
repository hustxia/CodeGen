/**
 * ESLint 配置（V4.3 新增）。
 *
 * 为什么现在才有：`npm run lint` 这个脚本一直存在，但从没装过插件，
 * 执行时报 `command "lint" does not exist`——一个跑了很久的假门禁。
 *
 * 规则取舍：只把「一定是 bug」的规则设为 error（未定义变量、重复 key、模板语法），
 * 风格类（缩进、引号、分号）一律关掉或降为 warn。
 * 理由是历史代码量大（6,997 行 Vue），风格噪声会把真实问题淹掉，
 * 那是「有 lint 但没人看」的典型死法。
 */
module.exports = {
  root: true,
  env: {
    browser: true,
    node: true,
    es2021: true
  },
  parserOptions: {
    ecmaVersion: 2021,
    sourceType: 'module'
  },
  extends: ['plugin:vue/essential', 'eslint:recommended'],
  globals: {
    process: 'readonly'
  },
  rules: {
    // 未使用变量在重构期很常见，降为提示，不阻断
    'no-unused-vars': ['warn', { args: 'none', ignoreRestSiblings: true }],
    'no-console': 'off',
    'vue/multi-word-component-names': 'off',
    // 模板里 <= 与 < 混排是历史写法，不影响正确性
    'vue/no-parsing-error': ['error', { 'unexpected-character-in-attribute-name': false }]
  },
  ignorePatterns: ['dist/', 'node_modules/', 'public/', 'tests/']
}

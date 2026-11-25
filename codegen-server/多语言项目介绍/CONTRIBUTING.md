# 翻译贡献指南（Contributing Translations）

欢迎为代码生成器产出的系统贡献语言翻译。本指南说明翻译存放在哪里、如何新增一种精译语言，以及提交前需要通过的校验。

## 一、现状

- 框架层面**支持 206 种语言**的切换与回落。
- 目前**精译 12 种**：`zh-CN`、`en-US`、`ja-JP`、`ko-KR`、`fr-FR`、`de-DE`、`es-ES`、`ar-SA`、`ru-RU`、`pt-BR`、`id-ID`、`vi-VN`。
- 其余 194 种语言**回落到简体中文**，页面不会空白（回落链见下文）。
- "支持 206 种"指框架能力（切换 / 回落 / RTL），"精译 12 种"指我们已逐条翻译的词条。两者含义不同，对外表述请以此为准。

## 二、翻译存放在哪里

本仓库（codegen-server）内的**唯一真源**：

| 文件 | 作用 |
| --- | --- |
| `src/main/resources/i18n/keys.json` | 文案键真源（60 个 `common.*` 键）与精译集合 `builtinLocales` |
| `src/main/resources/i18n/languages.json` | 206 种语言清单，每项含 `{ code, locale, nameEn, nameZh, nameNative, region, script, dir, enabled, sortNo }` |
| `src/main/resources/i18n/messages/<locale>.json` | 某精译语言的词条文件，键集合必须与 `keys.json` 完全一致 |

生成出来的系统里，对应结构为 `src/locales/<locale>.json`、`src/locales/index.js`，运行时翻译函数 `$t(key, fallback)` 的回落链见下。

**运行时回落链（唯一实现）**：当前语言包非空译文 → 默认语言（`zh-CN`）→ 第二参回文（模板里 `$t('crud.X.field', '中文')` 的中文原文）。

## 三、新增一门语言（5 步）

以新增 `xx-XX` 为例：

1. **登记语言清单**：在 `src/main/resources/i18n/languages.json` 的 `languages` 数组补一条记录，`enabled` 设为 `true`，并给出 `code` / `iso6392` / `locale` / `nameEn` / `nameZh` / `nameNative` / `region` / `script` / `dir` / `readme` / `sortNo`；`sortNo` 接在现有精译语言之后，保证全局唯一且递增。
2. **纳入精译集合**：在 `src/main/resources/i18n/keys.json` 的 `builtinLocales` 追加 `xx-XX`。只有进入该集合，生成物才会使用这份人工译文。
3. **新建词条文件**：新增 `src/main/resources/i18n/messages/xx-XX.json`，键与 `keys.json` 的 60 个 `common.*` **完全一致**——不缺失、不新增；文件为 UTF-8 无 BOM、2 空格缩进，`{placeholder}` 变量与源语言保持一致。
4. **本地校验**：依次执行下节命令，覆盖率必须 100%、缺失/多余键均为 0，JSON 必须可解析。
5. **更新对外表述并提交**：把 `README.md` / `README_ZH.md` 中的精译数量与新语种代码同步更新；PR 标题统一为 `[i18n] add <locale> translation`，CI 的 `i18n contract gate` 会再次校验。

## 四、本地校验命令

```bash
# 12 精译覆盖矩阵：键总数 / 已翻译 / 缺失 / 多余 / 覆盖率
# 有缺失或多出的键时退出码非 0，完整明细写入 target/i18n-coverage.txt
node scripts/i18n-coverage.mjs

# 只警告不阻断（本地补译过程中使用）
node scripts/i18n-coverage.mjs --warn

# 全平台 206 种语言 + 精译一致性的批量校验
node scripts/check-all-locales.mjs

# 单个新文件的 JSON 合法性
node -e "JSON.parse(require('fs').readFileSync('src/main/resources/i18n/messages/xx-XX.json','utf8'))"
```

PR 门禁：`.github/workflows/i18n-contract.yml` 中的 `backend-i18n-coverage` job 会执行同一脚本，任一语言文件缺失或多出键即阻断合并。

## 五、回落与 RTL

- 任何缺失键都会回落到简体中文，**不会报错或白屏**。
- `dir: "rtl"` 的语言（如 `ar-SA`）会自动镜像整体布局，无需额外处理。

## 六、约定

- 译文仅保留与源语言一致的 `{placeholder}` 插值变量，**不要新增或删除**花括号变量。
- 保持与 `zh-CN` 语义一致，避免过度意译；使用该语言在后台管理界面中的惯用表达，不要中英混排、不要保留中文原文或拼音。
- 专有名词（如实体名、字段名）建议保留中文原文作为回落，确保任何语言缺失时仍可理解。

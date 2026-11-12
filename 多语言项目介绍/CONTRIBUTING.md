# 翻译贡献指南（Contributing Translations）

欢迎为代码生成器产出的系统贡献语言翻译。本指南说明翻译存放在哪里、如何新增一种精译语言，以及提交前需要通过的校验。

## 一、现状

- 框架层面**支持 206 种语言**的切换与回落。
- 目前**精译 8 种**：`zh-CN`、`en-US`、`ja-JP`、`ko-KR`、`fr-FR`、`de-DE`、`es-ES`、`ar-SA`。
- 其余 198 种语言**回落到简体中文**，页面不会空白（回落链见下文）。
- "支持 206 种"指框架能力（切换 / 回落 / RTL），"精译 8 种"指我们已逐条翻译的词条。两者含义不同，对外表述请以此为准。

## 二、翻译存放在哪里

生成出来的系统里，国际化资源结构如下：

| 文件 | 作用 |
| --- | --- |
| `i18n/languages.json` | 语言清单，`total=206`，每项含 `{ locale, nameNative, dir, enabled }` |
| `src/locales/<locale>.json` | 某语言的词条文件 |
| `src/locales/index.js` | 汇总并注册所有语言包 |
| 运行时 `i18n.js` | 翻译函数 `$t(key, fallback)`，回落链见下 |

**运行时回落链（唯一实现）**：当前语言包非空译文 → 默认语言（`zh-CN`）→ 第二参回文（模板里 `$t('crud.X.field', '中文')` 的中文原文）。

## 三、如何贡献一种新语言的精译

1. Fork 仓库，在 `i18n/languages.json` 将该语言项的 `enabled` 设为 `true`。
2. 新增 `src/locales/<locale>.json`，键格式（`<Class>` 为实体类名，`<field>` 为字段名，`<var>` 为子表/孙表变量名）：
   - `crud.<Class>.title` / `.desc`
   - `crud.<Class>.field.<field>` / `crud.<Class>.field.<field>.ph`（占位符）
   - `crud.<Class>.addTitle` / `.editTitle` / `.detailTitle` / `.importTitle`
   - `crud.<Class>.child.<var>` / `crud.<Class>.gc.<var>`
   - 通用键：`common.*`、`menu.*`
3. 至少覆盖你负责的实体全部 `crud.<Class>.*` 键 + 通用键 `common.*` / `menu.*`。
4. 提交 PR，标题统一为 `[i18n] add <locale> translation`。

## 四、键覆盖校验

提交前运行键覆盖检查（生成物已自动产出 `scripts/check-locale-keys.mjs`，无需手动复制）：

```bash
node scripts/check-locale-keys.mjs <locale>
```

PR 需通过该检查——确保你新增的语言包覆盖了基线键集合，避免出现"某语言某个按钮仍是中文回落"的不一致。

## 五、回落与 RTL

- 任何缺失键都会回落到简体中文，**不会报错或白屏**。
- `dir: "rtl"` 的语言（如 `ar-SA`）会自动镜像整体布局，无需额外处理。

## 六、约定

- 译文仅保留与源语言一致的 `{placeholder}` 插值变量，**不要新增或删除**花括号变量。
- 保持与 `zh-CN` 语义一致，避免过度意译。
- 专有名词（如实体名、字段名）建议保留中文原文作为回落，确保任何语言缺失时仍可理解。

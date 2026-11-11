# 麻將·程式碼產生器（CodeGen）—— 專案介紹

> Languages: [简体中文](README_ZH.md) · **繁體中文** · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> 一個針對 **Spring Boot + Vue** 的多專案程式碼產生器：從資料庫表結構出發，產出**可編譯、可執行、具備選單與權限**的前後端業務程式碼，並處理寫入磁碟、備份、記帳與回滾的完整交付流程。

GitHub: https://github.com/hustxia/CodeGen

## 1. 這是什麼

CodeGen 是一個「以表驅動」的程式碼工廠。帶入你的業務表（透過資料庫匯入或貼上 DDL），設定欄位與產生開關，然後點選「產生」——它會產出後端的 Entity/Mapper/Service/Controller/DTO/VO、前端的 `api.js` 與 Vue 頁面、選單與路由，甚至 A4 列印頁面與整合測試骨架，並安全地寫入你的目標專案。

它不只是「吐出程式碼」——它是一條**具備品質關卡與回滾能力的交付管線**：

```
Table intake (DB import / DDL)
   → Field & switch configuration (with AI recommendations)
      → One-click generation: self-check → backup → write to disk → compile/validate → register menu → rebuild routes
         → Ledger bookkeeping → rollback-capable
```

---

## 2. 使用者是誰，會怎麼用

| 使用者 | 典型訴求 | 平台提供的路徑 |
| --- | --- | --- |
| 後端開發 | 手上有一批表（或 DDL），想快速拉起 CRUD | 表設定頁：匯入 → 配欄位 → 一鍵產生 |
| 全端／獨立開發 | 想從零起一個完整系統（含登入、權限、字典） | 管線頁：一句話建系統 |
| 團隊負責人 | 想統一程式碼風格、程式碼規範、目錄結構 | 系統管理：一套系統 = 一套輸出設定，批次生效 |
| 平台維護者 | 想改模板而不改程式碼 | 模板管理：線上編輯，儲存即生效，支援歷史回滾 |
## 3. 它解決了什麼問題

| 痛點 | CodeGen 的對應做法 |
| --- | --- |
| 重複 CRUD 地獄——每張表都是一堆樣板程式碼 | 逐表開關一鍵產出完整的前後端，包含 CRUD、分頁、驗證、匯入/匯出 |
| 前後端欄位與 API 不一致導致無盡的整合修正 | 同一份表元資料同時驅動後端 API 與前端頁面——零語意漂移 |
| 團隊規範難以統一；每個人風格不同 | 模板即規範：22 個 FreeMarker 模板集中管理，可線上編輯並支援版本回滾 |
| 產生器「覆寫即遺失」手寫邏輯 | 標記區塊合併機制：保留手寫程式碼，僅替換產生區域 |
| 產生的程式碼要到編譯期才可信 | 記憶體內編譯自檢（真實 `javac`）、結構契約檢查、磁碟差異預覽——未通過編譯絕不落地 |
| 從零搭建新系統耗時 | 管線「一句話建系統」：建庫 → 腳手架 → 產生 → 打包 → 註冊選單，全自動 |

---

## 4. 能力版圖

- **表格匯入**：DDL 解析、直接資料庫匯入、批次匯入精靈、自動推導欄位設定（控制項 / 驗證 / 字典 / 關聯）。
- **表格形態**：單表、樹狀表（樹狀 API + 樹狀表格）、主從表（同交易儲存 + 明細分頁）、多對多關聯表。
- **產出物**：Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue 頁面 + A4 列印頁面 + 整合測試骨架。
- **業務增強**：關聯下拉、字典轉換、資料遮罩、唯一性檢查、表單聯動、資料範圍權限、Excel 匯入/匯出、審批流程、支付 / 檔案 / 行動能力包。
- **品質關卡**：記憶體內編譯自檢（真實 `javac`）、結構契約檢查、權限一致性檢查、磁碟差異預覽、前端產出物真實驗證。
- **安全落地**：寫入磁碟 + `.gen-backup` 批次備份、標記區塊合併保留手寫程式碼、`gen_output` 四態記帳（建立 / 合併 / 跳過 / 失敗）、歷史回滾。
- **多系統隔離**：每個目標專案即一個「系統設定」（前/後端路徑、套件名稱、閘道前綴、依賴類別路徑、產生開關預設值）——可並行服務多個專案。
- **AI 增強**：一句話建系統（AI 建模）、種子資料 AI 增強、逐表 AI 推薦設定（差異預覽 + 勾選套用）。

---

## 5. 典型場景

| 場景 | 說明 |
| --- | --- |
| 樹狀表 | 商品分類等分層資料——自動產出樹狀 API、樹狀表格與上層選擇下拉 |
| 單表 CRUD | 客戶、商品等——外鍵下拉 + 字典轉換 + 匯入/匯出開箱即用 |
| 主從表 | 銷售訂單 + 訂單明細——同交易儲存，含雙分頁的明細抽屜（主檔資訊 / 明細） |
| 唯讀 / 統計 | 庫存流水等——關閉寫入開關，僅查詢與匯出 |
| 列印文件 | 勾選「列印」開關產出 A4 列印頁面與路由，含表頭 + 明細表 |
| 一句話建系統 | 描述業務領域——AI 一次性建立資料庫與資料表 + 腳手架 + 完整產生 + 選單註冊 |

> 所有場景皆有可執行示範（`demo_admin` 專案）與螢幕截圖——詳見 `docs/典型场景操作指南-演示后台demo.md`（中文）。

---

## 6. 核心工作流程

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Table intake │ → │ Field/switch conf. │ → │ One-click generate (self-check→backup→write→     │ → │ Ledger · Rollback │
│              │   │                    │   │ compile→menu→route)                              │   │                   │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     controls/validation     Lands on disk only after all quality gates pass;      Batch-level traceability
                    /dictionaries           hand-written code is preserved
```

- **差異預覽**：產出物會與目前磁碟狀態逐檔比對差異；寫入前需你確認，避免意外覆寫。
- **產生記帳**：每個檔案記錄其動作、位元組數、MD5 與備份位置；整批可回滾。

---

## 7. 一句話建系統（管線）

一項針對「從零搭建後端」的高階能力。描述業務領域後，管線自動推進 5 個階段：

| 階段 | 含義 |
| --- | --- |
| `CHECK` | 驗證目標專案設定與依賴類別路徑 |
| `WRITE` | 建立資料庫與資料表，寫入腳手架與產出物 |
| `COMPILE` | 後端 `mvn package` / 前端建置，記憶體內編譯自檢 |
| `MENU` | 產出並註冊選單權限（雙出口：產出物 SQL / 內部直接寫入） |
| `ROUTE` | 重建前端路由片段 |

任何階段失敗會立即中止並給出可讀錯誤，便於定位。

---

## 8. 系統架構

前後端分離，兩個倉庫協同運作：

| 倉庫 | 角色 | 技術堆疊 |
| --- | --- | --- |
| `codegen-server` | 後端：產生引擎、模板、驗證、記帳、管線 | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | 前端：控制台（表設定 / 模板 / 記帳 / 系統管理） | Vue 2.7 · Element UI · axios · Vue Router（history 模式） |

後端分層：

```
controller/    API layer (Gen* generation chain / Sys* system management / pipeline)
service/impl/  Core implementations (generation chain, template engine, merge, validation, pipeline execution)
util/          Template engine, model assembly, marker-block merge, self-check compilation
config/        Output config, self-check config, template seeder
common/        Result / BaseEntity / exceptions / audit
resources/gen-templates/   22 FreeMarker templates (backend / frontend / shared / test groups)
```

**目標專案必須提供的基礎能力**（產生器僅產出業務程式碼；缺少基礎會靜默失敗）：

1. MyBatis-Plus 分頁外掛（`PaginationInnerInterceptor`）
2. `Result` / `BaseEntity` / `PageQuery` 等基礎類別（套件前綴須符合 `root-package`）
3. 具備 `toPageData`（將 `Page{records,total}` 轉換為 `{list,total}`）的前端 `utils/request.js`
4. 依賴 `springdoc-openapi-starter-webmvc-ui`（產出程式碼使用 `@Operation`）與 `poi-ooxml`（Excel）

---

## 9. 技術堆疊一覽

| 維度 | 選擇 |
| --- | --- |
| 後端語言 / 框架 | Java 21 / Spring Boot 3.2.5 |
| 持久層 | MyBatis-Plus 3.5.5 |
| 模板引擎 | FreeMarker（22 個種子模板，線上編輯 + 版本回滾） |
| 安全 | JWT 認證 |
| 資料庫 | MySQL 8 |
| 前端 | Vue 2.7 + Element UI |
| 本地 AI | qwen2.5-coder:7b（建模 / 推薦設定 / 種子資料增強） |
| API 文件 | springdoc-openapi（Swagger UI） |

---

## 10. 快速開始

### 1. 建立資料庫（13 個平台表 + 預設帳號 admin/admin123）

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. 設定目標專案

複製設定範本並調整為你的本地路徑：

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` 已被 `.gitignore` 排除，不會被提交。主要設定：

```yaml
gen:
  output:
    backend-root: /path/to/your-target-project      # write-to-disk base for artifacts
    frontend-root: /path/to/your-target-project
    frontend-path: demo-web                          # frontend subdirectory for monorepos
    backend-path: demo-server/src/main/java/com/demo
    package-name: com.demo
    root-package: com.demo                           # prefix for foundation-class imports in artifacts
  selfcheck:
    classpath: /path/to/your-target-project/demo-lib/*
```

匯出自檢依賴目錄方式如下：

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. 啟動後端

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

服務預設埠 **8120**；API 文件位於 `/swagger-ui/index.html`。

### 4. 啟動前端

```bash
cd ../codegen-web
npm install
npm run serve
```

前端預設埠 **9530**，將 `/dev-api` 代理至後端埠 8120。預設帳號 `admin / admin123`。

### 5. 主要設定項目

| 設定 | 說明 |
| --- | --- |
| `gen.output.*` | 產出物寫入路徑、套件名稱、模組對應 |
| `gen.selfcheck.classpath` | 記憶體內編譯自檢的依賴類別路徑，支援 `dir/*` |
| `gen.menu.outlet` | `artifact` 僅產出選單 SQL 與路由片段；`internal` 同時寫入平台自身的 `sys_menu` |

---

## 11. 文件地圖

| 文件 | 內容 |
| --- | --- |
| `docs/代码生成器-操作手册.md` (中文) | 部署、5 分鐘上手、表設定、批次匯入、模板管理、記帳、API 目錄、完整管線（含截圖） |
| `docs/典型场景操作指南-演示后台demo.md` (中文) | 6 張表涵蓋 5 個典型場景，含可執行示範與實際執行截圖 |
| `docs/项目介绍.md` | 專案介紹文件，與本文件同源 |
| `docs/代码生成器-Vx.x-规划.md` | V3.1 → V7.3 的版本規劃與功能清單 |
| `codegen-web/README.md` | 前端控制台快速上手參考 |

---

## 12. 版本歷史

| 版本 | 主題 / 規劃文件 | 落地提交 |
| --- | --- | --- |
| V1.2 / V1.3 | 一鍵產生 / 批次 / 回滾；差異預覽與結構同步 | —（僅在 `src/api/gen.js` 註解中） |
| V2.0 | 動態路由（後端下發選單樹） | — |
| V3.0 / V3.1 | 多系統記帳；多系統循環 + 首批自動化測試 | V3.1（174 行） |
| V4.2 | 可追蹤、可回滾的產生結果（寫入記帳） | V4.2（292 行） |
| V4.3 | 模板迴歸與品質防護 | V4.3（200 行） |
| V4.4 | 一主多從、級聯寫入完整性、模板分道 | V4.4（154 行） |
| V4.5 | A4 列印文件（零第三方依賴） | V4.5（127 行） |
| V5.0 | 一句話建系統：管線 + AI 建模 + 腳手架基底 | — |
| V5.1 | DDL 審計欄位自動補全 | V5.1（100 行） |
| V5.2 | 管線種子資料初始化步驟 | V5.2（107 行），84ec8dd |
| V5.5 | 多模組專案產生收尾 | af73dd3 |
| V6.0 | 微服務基礎設施（Nacos / Gateway / Feign，第三模式「micro」） | V6.0（111 行），1deecbd |
| V6.1 | Activiti 工作流程雙引擎 | V6.1（100 行），02b671e |
| V6.2 | 產出物交付面（CLI / 部署產出物 / 設定安全） | V6.2（114 行），02b671e |
| V6.3 | 支付能力包（微信 / 支付寶 + Mock） | V6.3（104 行），1c4ee6a，22eede5 |
| V6.4 | 權限與安全基線（令牌持久化 / BCrypt / API 權限 / 登入保護） | V6.4（107 行），007a202 |
| V6.5 | 行動 uni-app（H5 + 微信小程式） | V6.5（105 行），779ac41 |
| V6.6 | 檔案能力包 | 98f3c09 |
| V6.7 | 產出物測試工廠驗收（test_enabled 三態 + H2 骨架） | V6.7（134 行），4e7d318 |
| V6.8 | 產出物可維護性（dryRun / 受保護跳過 / 品牌參數化） | V6.8（141 行），be6f858 |
| V6.9 | 平台文件中心（後端基底，前端同批完成） | — |
| V6.10 | 平台可觀測性：Actuator + 記憶體指標視窗 + 內嵌監控頁面 | — |
| V7.0 | AI 頁面與選單推薦（預覽 / 套用） | V7.0（94 行），45f2670 |
| V7.1 | 種子資料 AI 增強（AI 負責語意，規則負責結構） | V7.1（42 行），5ca6e72 |
| V7.2 | 管線成功後將 SQL 交付物寫入 `db/` | eebff88 |
| **V7.3** | **交付可信度：自檢已編譯產出物測試 / 統一 SQL 交付出口 / 逐表 AI 推薦** | **V7.3（138 行）** |
| **V7.4** | 可重複交付：可從空資料庫部署，每次提交保證可執行 | **目前版本** |

---

> **V6.10 平台可觀測性**：整合 Actuator + Micrometer，每 5 秒取樣至記憶體環形緩衝區（30 分鐘視窗），並新增內嵌「系統監控」頁面——後端 `/monitor/*` 聚合 API + 前端 ECharts 面板。僅開放 `health/info/metrics` 端點；`/actuator/health` 免令牌，而 `metrics` 等需驗證；`env/beans/heapdump/threaddump/loggers` 永不暴露。取樣視窗僅限單一程序，重啟即遺失，且不跨實例共享。詳見 `docs/可观测性说明.md`（中文）。

## 13. 專案狀態

- 目前規劃的版本線已抵達 **V7.3**（能力持續迭代：AI 建模、種子資料增強、能力包系統等）。
- 配套示範專案 `demo_admin`（後端 8140 / 前端 9040）涵蓋樹狀 / CRUD / 主從 / 唯讀 / 列印場景，可獨立執行驗證。
- 兩個倉庫皆託管於 Gitee：`https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. 作者與支援

- **作者**：一名中國程式設計師（獨立開發者）
- **電子郵件**：`hustxiayuhong@126.com`
- **技術支援**：請以**中文**撰寫電子郵件，內容包含問題描述、執行環境（JDK / MySQL / 版本）與重現步驟

> 我閱讀並回覆每一封郵件。錯誤回報、功能建議與合作洽詢皆歡迎。

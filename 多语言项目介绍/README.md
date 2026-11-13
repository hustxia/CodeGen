# Majiang · Code Generator (CodeGen) — Project Introduction

> Languages: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · **English** · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> A multi-project code generator for **Spring Boot + Vue**: starting from database table structures, it produces **compilable, runnable, menu-and-permission-ready** front-end and back-end business code, and handles the full delivery loop of writing to disk, backup, ledgering and rollback.

GitHub: https://github.com/hustxia/CodeGen

> **Current release — V9.0 (Vue 3 generated artifacts) on top of V8.1 (quality gate)**
> Generated projects now target **Vue 3 + Element Plus + Vite**: `package.json`, `vite.config.js`,
> `main.js`, router, store and `request.js` are emitted Vue 3-native, and template mechanics were
> migrated (`.sync` → `v-model`, `slot=` → `#`, `slot-scope` → `#default`, `.native` removed,
> font icons → component icons, `size="mini"` → `small`). Verified end to end at runtime: Maven
> build → API-driven generation → 40/40 contract assertions → `vite build` success → 14 Chrome
> screenshots including ar-SA RTL; 3 real defects were caught and fixed at runtime. V8.1 adds the
> quality gate behind it: an offline Freemarker contract harness (9 modes, OFF byte-zero-diff),
> `i18n-core` unit tests, a CI gate, and `check-locale-keys.mjs` shipped inside generated projects.
>
> **V8.0 - Full i18n for generated applications (including menus), 206 languages worldwide**
> Once i18n is enabled in project settings and the default / supported languages are picked,
> every generated page (buttons, table headers, field labels, dialog titles and menus) resolves
> through the `$t` runtime. Eight hand-translated packs ship out of the box (zh-CN, en-US,
> ja-JP, ko-KR, fr-FR, de-DE, es-ES, ar-SA); every other locale falls back through
> `current locale -> zh-CN -> inline Chinese`, so nothing ever renders blank. The language
> switcher lists all 206 locales and RTL languages mirror automatically. With the switch off,
> generated output stays byte-for-byte unchanged.

## 1. What Is This

CodeGen is a "table-driven" code factory. Bring in your business tables (via database import or by pasting DDL), configure fields and generation switches, then click "Generate" — it produces the backend Entity/Mapper/Service/Controller/DTO/VO, the frontend `api.js` and Vue pages, menus and routes, even A4 print pages and integration-test skeletons, and safely writes them into your target project.

It doesn't just "spit out code" — it is a **delivery pipeline with quality gates and rollback capability**:

```
Table intake (DB import / DDL)
   → Field & switch configuration (with AI recommendations)
      → One-click generation: self-check → backup → write to disk → compile/validate → register menu → rebuild routes
         → Ledger bookkeeping → rollback-capable
```

---

## 2. Who Are the Users, and How They Use It

| Users | Typical Needs | Path the Platform Provides |
| --- | --- | --- |
| Backend developer | Has a batch of tables (or DDL) and wants to quickly spin up CRUD | Table config page: import → configure fields → one-click generation |
| Full-stack / solo developer | Wants to build a complete system from scratch (with login, permissions, dictionaries) | Pipeline page: build a system with one sentence |
| Team lead | Wants to unify code style, coding standards, directory structure | System management: one system = one output config, applied in batch |
| Platform maintainer | Wants to change templates without changing code | Template management: online editing, takes effect on save, supports history rollback |
## 3. What Problems It Solves

| Pain point | What CodeGen does |
| --- | --- |
| Repetitive CRUD hell — a pile of boilerplate for every table | Per-table switches produce a complete front & back end in one click, including CRUD, pagination, validation, import/export |
| Front/back field & API mismatches causing endless integration fixes | The same table metadata drives both backend APIs and frontend pages — zero semantic drift |
| Team conventions hard to unify; everyone's style differs | Templates are the convention: 22 FreeMarker templates centrally managed, editable online with version rollback |
| Generators "overwrite and lose" hand-written logic | Marker-block merge mechanism: hand-written code is preserved, only generated regions are replaced |
| Generated code untrustworthy until compile time | In-memory compile self-check (real `javac`), structural contract checks, on-disk diff preview — nothing lands on disk unless it compiles |
| Scaffolding a new system from zero is time-consuming | Pipeline "build a system with one sentence": create DB → scaffold → generate → package → register menus, fully automated |

---

## 4. Capability Landscape

- **Table intake**: DDL parsing, direct database import, batch intake wizard, auto-derived column configuration (controls / validation / dictionaries / relations).
- **Table shapes**: single table, tree table (tree API + tree grid), master-detail (same-transaction save + detail tabs), many-to-many junction tables.
- **Generated artifacts**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue pages + A4 print pages + integration-test skeletons.
- **Business enhancements**: relation dropdowns, dictionary translation, data masking, uniqueness checks, form linkage, data-scope permissions, Excel import/export, approval workflow, payment / file / mobile capability packs.
- **Quality gates**: in-memory compile self-check (real `javac`), structural contract checks, permission-consistency checks, on-disk diff preview, real validation of frontend artifacts.
- **Safe landing**: write to disk + `.gen-backup` batch backups, marker-block merging to preserve hand-written code, `gen_output` four-state ledger (created / merged / skipped / failed), historical rollback.
- **Multi-system isolation**: each target project is a "system configuration" (front/back paths, package names, gateway prefix, dependency classpath, generation-switch defaults) — multiple projects served in parallel.
- **AI enhancements**: build a system with one sentence (AI modeling), seed-data AI enhancement, per-table AI recommended configuration (diff preview + checked apply).

---

## 5. Typical Scenarios

| Scenario | Description |
| --- | --- |
| Tree table | Hierarchical data such as product categories — auto-produced tree API, tree grid and parent-selection dropdown |
| Single-table CRUD | Customers, products, etc. — FK dropdowns + dictionary translation + import/export out of the box |
| Master-detail | Sales orders + order items — same-transaction save, detail drawer with dual tabs (master info / details) |
| Read-only / stats | Inventory flows, etc. — write switches off, query and export only |
| Print documents | Tick the "print" switch to produce an A4 print page and route, with header + detail table |
| One-sentence system build | Describe the business domain — AI creates DB & tables + scaffold + full generation + menu registration in one go |

> All scenarios have runnable demos (`demo_admin` project) with screenshots — see `../codegen-server/docs/典型场景操作指南-演示后台demo.md` (Chinese).

---

## 6. Core Workflow

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Table intake │ → │ Field/switch conf. │ → │ One-click generate (self-check→backup→write→     │ → │ Ledger · Rollback │
│              │   │                    │   │ compile→menu→route)                              │   │                   │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     controls/validation     Lands on disk only after all quality gates pass;      Batch-level traceability
                    /dictionaries           hand-written code is preserved
```

- **Diff preview**: generated artifacts are diffed file-by-file against the current disk state; you confirm before anything is written, avoiding accidental overwrites.
- **Generation ledger**: every file records its action, byte count, MD5 and backup location; a whole batch can be rolled back.

---

## 7. Build a System with One Sentence (Pipeline)

A high-level capability aimed at "scaffolding a backend from scratch". After you describe the business domain, the pipeline advances automatically through 5 stages:

| Stage | Meaning |
| --- | --- |
| `CHECK` | Validate target project configuration and dependency classpath |
| `WRITE` | Create database & tables, write scaffold and generated artifacts |
| `COMPILE` | Backend `mvn package` / frontend build, in-memory compile self-check |
| `MENU` | Produce and register menu permissions (dual outlets: artifact SQL / internal direct write) |
| `ROUTE` | Rebuild frontend route fragments |

Any stage failure aborts immediately with a readable error for easy locating.

---

## 8. System Architecture

Front-end and back-end separated, two repositories working together:

| Repository | Role | Stack |
| --- | --- | --- |
| `codegen-server` | Backend: generation engine, templates, validation, ledger, pipeline | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Frontend: console (table config / templates / ledger / system management) | Vue 2.7 · Element UI · axios · Vue Router (history mode) |

Backend layering:

```
controller/    API layer (Gen* generation chain / Sys* system management / pipeline)
service/impl/  Core implementations (generation chain, template engine, merge, validation, pipeline execution)
util/          Template engine, model assembly, marker-block merge, self-check compilation
config/        Output config, self-check config, template seeder
common/        Result / BaseEntity / exceptions / audit
resources/gen-templates/   22 FreeMarker templates (backend / frontend / shared / test groups)
```

**Foundation capabilities the target project must provide** (the generator only produces business code; a missing foundation fails silently):

1. MyBatis-Plus pagination plugin (`PaginationInnerInterceptor`)
2. Foundation classes such as `Result` / `BaseEntity` / `PageQuery` (package prefix must match `root-package`)
3. Frontend `utils/request.js` with `toPageData` (converts `Page{records,total}` into `{list,total}`)
4. Dependencies `springdoc-openapi-starter-webmvc-ui` (generated code uses `@Operation`) and `poi-ooxml` (Excel)

---

## 9. Tech Stack at a Glance

| Dimension | Choice |
| --- | --- |
| Backend language / framework | Java 21 / Spring Boot 3.2.5 |
| Persistence | MyBatis-Plus 3.5.5 |
| Template engine | FreeMarker (22 seed templates, online editing + version rollback) |
| Security | JWT authentication |
| Database | MySQL 8 |
| Frontend | Vue 2.7 + Element UI |
| Local AI | qwen2.5-coder:7b (modeling / recommended config / seed-data enhancement) |
| API docs | springdoc-openapi (Swagger UI) |

---

## 10. Quick Start

### 1. Create the database (13 platform tables + default account admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Configure the target project

Copy the config template and adjust to your local paths:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` is excluded by `.gitignore` and will not be committed. Key settings:

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

Export the self-check dependency directory like this:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Start the backend

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

The service defaults to port **8120**; API docs at `/swagger-ui/index.html`.

### 4. Start the frontend

```bash
cd ../codegen-web
npm install
npm run serve
```

The frontend defaults to port **9530**, proxying `/dev-api` to backend port 8120. Default account `admin / admin123`.

### 5. Key configuration items

| Setting | Description |
| --- | --- |
| `gen.output.*` | Artifact write paths, package names, module mapping |
| `gen.selfcheck.classpath` | Dependency classpath for in-memory compile self-check, supports `dir/*` |
| `gen.menu.outlet` | `artifact` produces menu SQL & route fragments only; `internal` also writes the platform's own `sys_menu` |

---

## 11. Documentation Map

| Document | Content |
| --- | --- |
| `../codegen-server/docs/代码生成器-操作手册.md` (Chinese) | Deployment, 5-minute start, table config, batch intake, template management, ledger, API catalog, full pipeline (with screenshots) |
| `../codegen-server/docs/典型场景操作指南-演示后台demo.md` (Chinese) | 6 tables covering 5 typical scenarios, with runnable demo and real-run screenshots |
| `项目介绍.md` | Project introduction document, same source as this file |
| `../codegen-server/docs/代码生成器-Vx.x-规划.md` | Version plans and feature inventories from V3.1 → V9.0 |
| `../codegen-web/README.md` | Frontend console quick-start reference |

---

## 12. Version History

| Version | Theme / plan document | Landing commit |
| --- | --- | --- |
| V1.2 / V1.3 | One-click generation / batch / rollback; diff preview & structure sync | — (only in `src/api/gen.js` comments) |
| V2.0 | Dynamic routes (backend-delivered menu tree) | — |
| V3.0 / V3.1 | Multi-system ledger; multi-system loop + first automated tests | V3.1 (174 lines) |
| V4.2 | Traceable, rollback-able generation results (write ledger) | V4.2 (292 lines) |
| V4.3 | Template regression & quality guardrails | V4.3 (200 lines) |
| V4.4 | One-master-many-detail, cascade-write completeness, template lanes | V4.4 (154 lines) |
| V4.5 | A4 print documents (zero third-party dependencies) | V4.5 (127 lines) |
| V5.0 | One-sentence system build: pipeline + AI modeling + scaffold base | — |
| V5.1 | DDL audit columns auto-completion | V5.1 (100 lines) |
| V5.2 | Pipeline seed-data initialization step | V5.2 (107 lines), 84ec8dd |
| V5.5 | Multi-module project generation wrap-up | af73dd3 |
| V6.0 | Microservice infrastructure (Nacos / Gateway / Feign, third mode "micro") | V6.0 (111 lines), 1deecbd |
| V6.1 | Activiti workflow dual engine | V6.1 (100 lines), 02b671e |
| V6.2 | Artifact delivery surface (CLI / deployment artifacts / config security) | V6.2 (114 lines), 02b671e |
| V6.3 | Payment capability pack (WeChat / Alipay + Mock) | V6.3 (104 lines), 1c4ee6a, 22eede5 |
| V6.4 | Permission & security baseline (token persistence / BCrypt / API permissions / login protection) | V6.4 (107 lines), 007a202 |
| V6.5 | Mobile uni-app (H5 + WeChat Mini Program) | V6.5 (105 lines), 779ac41 |
| V6.6 | File capability pack | 98f3c09 |
| V6.7 | Artifact test factory acceptance (test_enabled tri-state + H2 skeleton) | V6.7 (134 lines), 4e7d318 |
| V6.8 | Artifact maintainability (dryRun / protected skip / brand parameterization) | V6.8 (141 lines), be6f858 |
| V6.9 | Platform documentation center (backend base, frontend finished in the same batch) | — |
| V6.10 | Platform observability: Actuator + in-memory metric window + embedded monitor page | — |
| V7.0 | AI page & menu recommendations (preview / apply) | V7.0 (94 lines), 45f2670 |
| V7.1 | Seed-data AI enhancement (AI owns semantics, rules own structure) | V7.1 (42 lines), 5ca6e72 |
| V7.2 | SQL deliverables written to `db/` after successful pipeline | eebff88 |
| **V7.3** | **Delivery trustworthiness: self-check compiled artifact tests / unified SQL delivery outlet / per-table AI recommendations** | **V7.3 (138 lines), 696765b** |
| **V7.4** | Repeatable delivery: deployable from an empty database, every commit guaranteed runnable | c306e29 |
| **V7.5** | **Project language configuration: 206-language list / default language / enabled languages, generated `lang` & `dir` injection** | 7a9afa0 + abac56d |
| **V8.0** | **Full-page i18n: 206-language framework (8 professionally translated), dual-track i18n with OFF zero-diff contract** | 9e5dd57 → 7d57aea |
| **V8.1** | **Quality hardening: offline Freemarker contract harness (9 modes, OFF byte-zero-diff) + `i18n-core` unit tests + CI gate + honest README split (framework 206 / translated 8) + `check-locale-keys.mjs`** | 93a12d5 + e49330b |
| **V9.0** | **Generated-artifact modernization: Vue 3 + Element Plus + Vite; runtime-verified (40/40 assertions, `vite build` success, 14 Chrome screenshots incl. ar-SA RTL); 3 runtime defects found & fixed** | 93a12d5 + e49330b, **Current version** |

---

> **V6.10 Platform observability**: integrates Actuator + Micrometer, sampling every 5 seconds into an in-memory ring buffer (30-minute window), plus a new embedded "System Monitor" page — backend `/monitor/*` aggregation APIs + frontend ECharts panels. Only `health/info/metrics` endpoints are whitelisted; `/actuator/health` is token-free while `metrics` etc. require auth; `env/beans/heapdump/threaddump/loggers` are never exposed. The sampling window is per-process only, lost on restart, and not shared across instances. See `../codegen-server/docs/可观测性说明.md` (Chinese) for details.

## 13. Project Status

- The current planned version line has reached **V9.0** (capabilities keep iterating: AI modeling, seed-data enhancement, capability-pack system, etc.).
- A companion demo project `demo_admin` (backend 8140 / frontend 9040) covers tree / CRUD / master-detail / read-only / print scenarios and runs standalone for verification.
- Both repositories are hosted on Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. Author & Support

- **Author**: a Chinese programmer (independent developer)
- **Email**: `hustxiayuhong@126.com`
- **Technical support**: please write your email **in Chinese**, including the problem description, runtime environment (JDK / MySQL / version) and reproduction steps

> I read and reply to every email. Bug reports, feature requests and collaboration inquiries are all welcome.

# Majiang · Code Generator (CodeGen) — Project ka ta'aruf

> Languages: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · **Roman Urdu** · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> **Spring Boot + Vue** ke liye ek multi-project code generator: database table structures se shuruat karke ye **compilable, runnable, menu-aur-permission-ready** front-end aur back-end business code tayar karta hai, aur disk par likhne, backup, ledgering aur rollback ka poora delivery loop sambhalta hai.

GitHub: https://github.com/hustxia/CodeGen

> **maujooda version V9.1 · Dekh bhaal ke liye refactoring · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9**
> **V9.0 · Generated artifacts ko Vue 3 + Element Plus + Vite par modernize kiya; runtime par verify shuda (40/40 assertions, kamyab vite build, 14 screenshots incl. ar-SA RTL); 3 runtime defects theek kiye**
> **V8.1** · Quality hardening: offline contract harness (9 modes, OFF zero-diff) + i18n-core unit tests + CI gate + imandaar README taqseem (framework 206 / tarjuma 8) + check-locale-keys.mjs
> **V8.0** · Full-page i18n: 206 zabanoon ka framework (8 professional tarjuma shuda), OFF zero-diff contract ke saath dual-track i18n

## 1. Ye kya hai

CodeGen ek "table-driven" code factory hai. Apne business tables laayein (database import ke zariye ya DDL paste karke), fields aur generation switches configure karein, phir "Generate" par click karein — ye backend Entity/Mapper/Service/Controller/DTO/VO, frontend `api.js` aur Vue pages, menus aur routes, yahan tak ke A4 print pages aur integration-test skeletons tayar karta hai, aur unhein apne target project mein safely likh deta hai.

Ye sirf "code bahar nikalne" ka kaam nahi karta — ye ek **delivery pipeline hai jisme quality gates aur rollback capability hoti hai**:

```
Table intake (DB import / DDL)
   → Field & switch configuration (with AI recommendations)
      → One-click generation: self-check → backup → write to disk → compile/validate → register menu → rebuild routes
         → Ledger bookkeeping → rollback-capable
```

---

## 2. Users kaun hain aur kaise istemal karenge

| User | Typical zaroorat | Platform ka rasta |
| --- | --- | --- |
| Backend developer | Paas kuch tables (ya DDL) hain aur jaldi CRUD khara karna chahta hai | Table config page: import → fields configure → one-click generation |
| Full-stack / independent developer | Zero se ek mukammal system (login, permissions, dictionaries ke sath) banana chahta hai | Pipeline page: ek jumle se system banayein |
| Team lead | Code style, code conventions aur directory structure ek karna chahta hai | System management: ek system = ek output configuration, batch mein lagu |
| Platform maintainer | Code badle baghair templates edit karna chahta hai | Template management: online editing, save karte hi lagu, version rollback support |

## 3. Is se hal hone wale masail

| Problem (Pain point) | CodeGen kya karta hai |
| --- | --- |
| Bar-bar hone wala CRUD ka azab — har table ke liye boilerplate ka dhair | Har table ke switches ek click mein mukammal front aur back end produce karte hain, jisme CRUD, pagination, validation, import/export shamil hain |
| Front/back field aur API mein farq jis se integration fixes ka silsila chalta rahe | Wahi table metadata backend APIs aur frontend pages dono ko chalata hai — koi semantic drift nahi |
| Team ke conventions ek karna mushkil; har kisi ka style alag | Templates hi convention hain: 22 FreeMarker templates centrally manage kiye gaye, online edit-able version rollback ke sath |
| Generator "overwrite karke" hand-written logic kho dete hain | Marker-block merge mechanism: hand-written code mahfooz rehta hai, sirf generated regions badle jate hain |
| Generated code compile time tak bharosa-mand nahi | In-memory compile self-check (asli `javac`), structural contract checks, on-disk diff preview — jab tak compile na ho, kuch bhi disk par nahi aata |
| Zero se naya system scaffold karna time-consuming hai | Pipeline "ek jumle se system banayein": create DB → scaffold → generate → package → register menus, bilkul automated |

---

## 4. Capability Landscape

- **Table intake**: DDL parsing, direct database import, batch intake wizard, aur auto-derived column configuration (controls / validation / dictionaries / relations).
- **Table shapes**: single table, tree table (tree API + tree grid), master-detail (same-transaction save + detail tabs), aur many-to-many junction tables.
- **Generated artifacts**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue pages + A4 print pages + integration-test skeletons.
- **Business enhancements**: relation dropdowns, dictionary translation, data masking, uniqueness checks, form linkage, data-scope permissions, Excel import/export, approval workflow, payment / file / mobile capability packs.
- **Quality gates**: in-memory compile self-check (real `javac`), structural contract checks, permission-consistency checks, on-disk diff preview, frontend artifacts ki real validation.
- **Safe landing**: write to disk + `.gen-backup` batch backups, marker-block merging se hand-written code mahfooz, `gen_output` four-state ledger (created / merged / skipped / failed), historical rollback.
- **Multi-system isolation**: har target project ek "system configuration" hai (front/back paths, package names, gateway prefix, dependency classpath, generation-switch defaults) — multiple projects parallel serve kiye jate hain.
- **AI enhancements**: ek jumle se system banayein (AI modeling), seed-data AI enhancement, per-table AI recommended configuration (diff preview + checked apply).

---

## 5. Typical Scenarios

| Scenario | Tafseel (Description) |
| --- | --- |
| Tree table | Product categories jaisi hierarchical data — auto-produced tree API, tree grid aur parent-selection dropdown |
| Single-table CRUD | Customers, products, waghaira — FK dropdowns + dictionary translation + import/export out of the box |
| Master-detail | Sales orders + order items — same-transaction save, detail drawer dual tabs ke sath (master info / details) |
| Read-only / stats | Inventory flows, waghaira — write switches off, sirf query aur export |
| Print documents | "Print" switch tick karein A4 print page aur route ke liye, header + detail table ke sath |
| One-sentence system build | Business domain describe karein — AI DB & tables + scaffold + full generation + menu registration ek saath banata hai |

> Tamam scenarios ke runnable demos hain (`demo_admin` project) screenshots ke sath — dekhein `../codegen-server/docs/典型场景操作指南-演示后台demo.md` (Chinese).

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

- **Diff preview**: generated artifacts ko current disk state ke khilaf file-by-file diff kiya jata hai; likhne se pehle aap confirm karte hain, accidental overwrites se bachne ke liye.
- **Generation ledger**: har file apna action, byte count, MD5 aur backup location record karti hai; poori batch rollback ki ja sakti hai.

---

## 7. Ek jumle se system banayein (Pipeline)

Ek high-level capability jo "scratch se backend scaffold" karne ke liye hai. Jab aap business domain describe karte hain, pipeline automatik 5 stages se aage badhta hai:

| Stage | Matlab (Meaning) |
| --- | --- |
| `CHECK` | Target project configuration aur dependency classpath validate karein |
| `WRITE` | Database & tables banayein, scaffold aur generated artifacts likhein |
| `COMPILE` | Backend `mvn package` / frontend build, in-memory compile self-check |
| `MENU` | Menu permissions produce aur register karein (dual outlets: artifact SQL / internal direct write) |
| `ROUTE` | Frontend route fragments dobara banayein |

Kisi bhi stage ki failure turant readable error ke sath abort ho jati hai, jisse locate karna aasan ho.

---

## 8. System ki Architecture

Front-end aur back-end alag hain, do repositories milkar kaam karte hain:

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

**Wo foundation capabilities jo target project ko provide karni chahiye** (generator sirf business code produce karta hai; kami hone par foundation silently fail ho jata hai):

1. MyBatis-Plus pagination plugin (`PaginationInnerInterceptor`)
2. Foundation classes jaise `Result` / `BaseEntity` / `PageQuery` (package prefix `root-package` se match karna chahiye)
3. Frontend `utils/request.js` with `toPageData` (jo `Page{records,total}` ko `{list,total}` mein convert karta hai)
4. Dependencies `springdoc-openapi-starter-webmvc-ui` (generated code `@Operation` use karta hai) aur `poi-ooxml` (Excel)

---

## 9. Tech Stack ek nazar mein

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

### 1. Database banayein (13 platform tables + default account admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Target project configure karein

Config template copy karein aur apne local paths adjust karein:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` `.gitignore` mein exclude hai aur commit nahi hoga. Key settings:

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

Self-check dependency directory ko is tarah export karein:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Backend start karein

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

Service default port **8120** hai; API docs `/swagger-ui/index.html` par.

### 4. Frontend start karein

```bash
cd ../codegen-web
npm install
npm run serve
```

Frontend default port **9530** hai, `/dev-api` ko backend port 8120 par proxy karta hai. Default account `admin / admin123`.

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
| `../codegen-server/docs/代码生成器-Vx.x-规划.md` | Version plans and feature inventories from V3.1 → V9.1 |
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
| **V7.5** | **Project ki zubaan configuration: 206 zabanoon ki list / default zubaan / enabled zabanein, generated code mein lang aur dir injection** | 7a9afa0 + abac56d |
| **V8.0** | **Full-page i18n: 206 zabanoon ka framework (8 professional tarjuma shuda), OFF zero-diff contract ke saath dual-track i18n** | 9e5dd57 → 7d57aea |
| **V8.1** | **Quality hardening: offline contract harness (9 modes, OFF zero-diff) + i18n-core unit tests + CI gate + imandaar README taqseem (framework 206 / tarjuma 8) + check-locale-keys.mjs** | 93a12d5 + e49330b |
| **V9.0** | **Generated artifacts ko Vue 3 + Element Plus + Vite par modernize kiya; runtime par verify shuda (40/40 assertions, kamyab vite build, 14 screenshots incl. ar-SA RTL); 3 runtime defects theek kiye** | 93a12d5 + e49330b  |
| **V9.1** | **Dekh bhaal ke liye refactoring · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9** | 654b8e0 → b0d081e, **maujooda version** |

---

> **V6.10 Platform observability**: integrates Actuator + Micrometer, sampling every 5 seconds into an in-memory ring buffer (30-minute window), plus a new embedded "System Monitor" page — backend `/monitor/*` aggregation APIs + frontend ECharts panels. Only `health/info/metrics` endpoints are whitelisted; `/actuator/health` is token-free while `metrics` etc. require auth; `env/beans/heapdump/threaddump/loggers` are never exposed. The sampling window is per-process only, lost on restart, and not shared across instances. See `../codegen-server/docs/可观测性说明.md` (Chinese) for details.

## 13. Project Status

- Mazkoor planned version line **V9.1** tak pahunch chuki hai (capabilities iterate hoti rahtin hain: AI modeling, seed-data enhancement, capability-pack system, waghaira).
- Ek companion demo project `demo_admin` (backend 8140 / frontend 9040) tree / CRUD / master-detail / read-only / print scenarios cover karta hai aur verification ke liye standalone run hota hai.
- Dono repositories Gitee par host hain: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. Author & Support

- **Author**: ek Chinese programmer (independent developer)
- **Email**: `hustxiayuhong@126.com`
- **Technical support**: apna email **Chinese mein** likhein, jisme problem description, runtime environment (JDK / MySQL / version) aur reproduction steps shamil hon

> Main har email padhta aur jawab deta hoon. Bug reports, feature requests aur collaboration inquiries sabhi ka swagat hai.


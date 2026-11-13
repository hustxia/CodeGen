# Majiang · Code Generator (CodeGen) — Pagpapakilala sa Proyekto

> Mga Wika: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · **Filipino** · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> Isang multi-project code generator para sa **Spring Boot + Vue**: simula sa mga istruktura ng talahanayan ng database, gumagawa ito ng **nakakabuo, nakakatakbo** na business code para sa front-end at back-end na may mga menu at pahintulot, at pinangangasiwaan ang buong siklo ng paghahatid ng pagsusulat sa disk, backup, ledger, at rollback.

GitHub: https://github.com/hustxia/CodeGen

## 1. Ano Ito

Ang CodeGen ay isang "talahanayan-driven" na pabrika ng code. Dalhin ang iyong mga talahanayan ng negosyo (sa pamamagitan ng pag-import mula sa database o sa pamamagitan ng pag-paste ng DDL), i-configure ang mga field at generation switch, pagkatapos ay i-click ang "Generate" — pagkatapos ay gumagawa ito ng back-end Entity/Mapper/Service/Controller/DTO/VO, ang front-end `api.js` at mga pahina ng Vue, mga menu at ruta, kahit na mga pahina ng pag-print na A4 at mga skeleton ng integration test, at ligtas na isinusulat ang lahat sa iyong target na proyekto.

Hindi lang ito "naglalabas ng code" — ito ay isang **pipeline ng paghahatid na may mga pintuan ng kalidad at kakayahang rollback**.

## 2. Sino ang mga User, at Paano Kagamitin

| User | Karaniwang Pangangailangan | Landas na Ibinibigay ng Platform |
| --- | --- | --- |
| Developer ng back-end | May isang set ng mga talahanayan (o DDL), gustong mabilis na buuin ang CRUD | Pahina ng configuration ng talahanayan: i-import → i-configure ang mga field → Generate sa isang pag-click |
| Full-stack / independiyenteng developer | Gustong bumuo ng kumpletong sistema mula sa simula (login, pahintulot, diksyunaryo) | Pahina ng pipeline: bumuo ng sistema sa isang pangungusap |
| Pinuno ng koponan | Gustong pag-isahin ang style ng code, pamantayan ng code, istruktura ng direktoryo | Pamamahala ng sistema: isang sistema = isang configuration ng output, epektibo sa batch |
| Tagapangalaga ng platform | Gustong baguhin ang mga template nang hindi binabago ang code | Pamamahala ng template: maaaring i-edit online, agad na epektibo pagkatapos i-save, sumusuporta sa rollback ng bersyon |

## 3. Anong mga Problema ang Nalulutas Nito

| Sakit | Ginagawa ng CodeGen |
| --- | --- |
| Paulit-ulit na impiyerno ng CRUD — tumpok ng boilerplate para sa bawat talahanayan | Ang mga switch sa antas ng talahanayan ay gumagawa ng kumpletong front-end at back-end sa isang pag-click, kabilang ang CRUD, pagination, validation, import/export |
| Mismatch sa field at API sa pagitan ng front at back na nagdudulot ng walang katapusang pag-aayos ng integrasyon | Ang parehong metadata ng talahanayan ang nagtutulak sa parehong back-end API at front-end na pahina — zero na semantikong paglihis |
| Mahirap pag-isaunlad ang mga kumbensyon ng koponan; lahat ay sumusulat sa sariling istilo | Ang mga template ay ang kumbensyon: 22 FreeMarker template na sentralisadong pinamamahalaan, maaaring i-edit online na may rollback ng bersyon |
| Ang mga generator ay "nang-overshoot at nawawala" ang lohika na isinulat ng kamay | Mekanismo ng marker-block merge: ang code na isinulat ng kamay ay pinangangalagaan, tanging ang mga nabuong rehiyon ang pinapalitan |
| Ang nabuong code ay hindi mapagkakatiwalaan hanggang sa oras ng pagbuo | Self-check sa compilation sa memory (tunay na `javac`), mga pagsusuri sa kontraktong istruktural, preview ng diff sa disk — walang anumang bagay na lulusot sa disk maliban kung ito ay bumubuo |
| Ang pag-set up ng bagong sistema mula sa simula ay nangangailangan ng oras | Pipeline "bumuo ng sistema sa isang pangungusap": lumikha ng DB → scaffold → gumawa → i-package → magrehistro ng mga menu, ganap na awtomatiko |

## 4. Pangkalahatang Tanawin ng mga Kakayahan

- **Pagkuha ng talahanayan**: pag-parse ng DDL, direktang pag-import mula sa database, wizard ng batch intake, awtomatikong hinangong configuration ng column (mga kontrol / validation / diksyunaryo / relasyon).
- **Mga anyo ng talahanayan**: simpleng talahanayan, punong talahanayan (tree API + tree grid), master-detail (pag-save sa parehong transaksyon + mga tab ng detalye), marami-sa-maraming junction na talahanayan.
- **Mga nabuong artefakto**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + mga pahina ng Vue + mga pahina ng pag-print na A4 + mga skeleton ng integration test.
- **Pagpapahusay sa negosyo**: dropdown ng relasyon, pagsasalin ng diksyunaryo, pagtatago ng datos, pagsusuri ng uniqueness, pag-uugnay ng form, pahintulot sa saklaw ng datos, import/export ng Excel, workflow ng pag-apruba, mga pakete ng kakayahan sa pagbabayad / file / mobile.
- **Mga pintuan ng kalidad**: self-check sa compilation sa memory (tunay na `javac`), mga pagsusuri sa kontraktong istruktural, mga pagsusuri sa pagkakapare-pareho ng pahintulot, preview ng diff sa disk, tunay na pagpapatunay ng mga artefakto sa front-end.
- **Ligtas na landin**: pagsusulat sa disk + `.gen-backup` na mga backup ng batch, marker-block merge upang pangalagaan ang code na isinulat ng kamay, `gen_output` na apat na kalagayang ledger (nilikha / pinagsama / nilaktawan / nabigo), makasaysayang rollback.
- **Paghihiwalay ng maraming sistema**: ang bawat target na proyekto ay isang "configuration ng sistema" (mga landas sa front/back, pangalan ng package, prefix ng gateway, classpath ng dependency, mga default na halaga ng generation switch).
- **Pagpapahusay ng AI**: bumuo ng sistema sa isang pangungusap (pagmomodelo ng AI), pagpapahusay ng AI sa seed data, AI na inirerekomendang configuration sa antas ng talahanayan.

## 5. Mga Tipikal na Scenario

| Scenario | Paglalarawan |
| --- | --- |
| Punong talahanayan | Hierarkikal na datos tulad ng mga kategorya ng produkto — awtomatikong gumagawa ng tree API, tree grid at dropdown ng pagpili ng magulang |
| Simpleng talahanayan na CRUD | Mga customer, produkto, atbp. — FK dropdown + pagsasalin ng diksyunaryo + import/export na handa na |
| Master-detail | Mga order ng benta + mga item ng order — pag-save sa parehong transaksyon, drawer ng detalye na may dalawang tab (pangunahing impormasyon / mga detalye) |
| Read-only / istatistika | Mga daloy ng imbentaryo, atbp. — mga switch ng pagsulat ay naka-off, query at export lamang |
| Mga dokumento ng pag-print | Markahan ang switch na "print" upang makabuo ng pahina ng pag-print na A4 at ruta |
| Paggawa ng sistema sa isang pangungusap | Ilarawan ang domain ng negosyo — lumilikha ang AI ng DB at mga talahanayan + scaffold + buong pagbuo + pagrehistro ng menu sa isang hakbang |

## 6. Pangunahing Workflow

```
Pagkuha ng talahanayan → Config ng field/switch → Generate sa isang pag-click → Ledger · Rollback
DB import/DDL              Mga kontrol/validation   Lumapag sa disk lamang pagkatapos ng lahat ng pintuan ng kalidad;
                         /diksyunaryo              ang code na isinulat ng kamay ay pinangangalagaan
```

## 7. Bumuo ng Sistema sa Isang Pangungusap (Pipeline)

| Yugto | Kahulugan |
| --- | --- |
| `CHECK` | Pinapatunayan ang configuration ng target na proyekto at classpath ng dependency |
| `WRITE` | Lumilikha ng database at mga talahanayan, isinusulat ang scaffold at mga nabuong artefakto |
| `COMPILE` | Back-end `mvn package` / build ng front-end, self-check sa compilation sa memory |
| `MENU` | Gumagawa at nagrerehistro ng mga pahintulot sa menu (dalawang outlet: artefaktong SQL / direktang panloob na pagsusulat) |
| `ROUTE` | Muling bumubuo ng mga fragment ng ruta sa front-end |

## 8. Arkitektura ng Sistema

| Repository | Tungkulin | Stack |
| --- | --- | --- |
| `codegen-server` | Back-end: engine ng pagbuo, mga template, validation, ledger, pipeline | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Front-end: console (config ng talahanayan / mga template / ledger / pamamahala ng sistema) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (history mode) |

## 9. Teknikal na Stack

| Dimensyon | Pagpipilian |
| --- | --- |
| Wika/Framework ng back-end | Java 21 / Spring Boot 3.2.5 |
| Persistence | MyBatis-Plus 3.5.5 |
| Engine ng template | FreeMarker (22 seed template, online na pag-edit + rollback ng bersyon) |
| Seguridad | JWT authentication |
| Database | MySQL 8 |
| Front-end (generated projects) | Vue 3 + Element Plus + Vite |
| Front-end (platform console) | Vue 2.7 + Element UI |
| Lokal na AI | qwen2.5-coder:7b |
| Dokumentasyon ng API | springdoc-openapi (Swagger UI) |

## 10. Mabilis na Simula

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql

cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml

mvn package -DskipTests
java -jar target/codegen-server.jar

cd ../codegen-web
npm install
npm run serve
```

Ang back-end ay naka-port na default na **8120**, ang front-end ay naka-port na **9530**. Default na account `admin / admin123`.

## 11. Kalagayan ng Proyekto

- Ang kasalukuyang pinaplanong linya ng bersyon ay umabot na sa **V9.2**.
- **V9.2** · Sentro ng pagsubok & sentro ng beripikasyon · Test Center (7 tables: env/suite/case/dataset/run/result/schedule) + Verification Center (tc_run/tc_case_result/tc_artifact, 9 suites S1-S9, single-run 409 lock, `codegen.testcenter/v1`); 373/373 tests; 206/206 locales; output byte-identical
- **V9.2** · Refactoring para sa maintainability · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9
- Ang dalawang repository ay naka-host sa Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

## 12. May-akda

- **May-akda**: isang Chinese programmer (independiyenteng developer)
- **Email**: `hustxiayuhong@126.com`
- **Teknikal na Suporta**: mangyaring isulat ang iyong email sa **Chinese**, kabilang ang paglalarawan ng problema, runtime na kapaligiran (JDK / MySQL / bersyon), at mga hakbang para sa pagpaparami.

---

## 13. Paghahambat sa Mga Katulad na Tool

CodeGen is neither a mere code snippet generator nor a full-stack low-code platform — it sits in a specific niche. Here is how it positions itself relative to the more commonly known tools in the Spring Boot / Java ecosystem.

### vs. Snippet Generators (JPA Buddy · MapStruct · Spring Initializr)

Tools like **JPA Buddy**, **MapStruct**, and **Spring Initializr** are great for scaffolding and small ergonomic wins, but they only produce **fragments**: an entity here, a mapper there, some DTO conversion helpers, or an empty starter project. You still need to wire up the CRUD pages, menus, routes, and permission codes by hand — and when the table changes, you re-do it.

CodeGen, by contrast, produces a **zero-edit, runnable vertical slice**: full backend (Entity → Mapper → Service → Controller → DTO/VO) + full frontend (`api.js` + Vue pages + A4 print page) + menu + route registration. Bring your DDL, click once, and the feature is live.

### vs. Scaffold Frameworks (JeecgBoot · RuoYi · dlow)

**JeecgBoot**, **RuoYi**, and similar frameworks ship a complete admin scaffold first, then bolt a code generator on top. You are required to **fork their scaffold**, carry its conventions, its authentication layer, its menu schema, its file layout, and — critically — its upgrade story. The generator **inherits the parent framework's biases** (e.g., dynamic datasource conventions, Shiro/Spring Security choices, front-end tech-lock).

CodeGen takes the opposite stance: **it never touches your project's infrastructure**. It assumes your project already has `Result`, `BaseEntity`, `PageQuery`, `PaginationInnerInterceptor`, and a `utils/request.js` that returns `{list, total}`. It only **drops business code into your existing layout**, alongside whatever auth stack, UI, and conventions you already chose. No fork. No convention hijack. Clean, surgical.

### vs. Built-in Generators (Yunzhai · JeecgBoot built-in · RuoYi built-in)

Many scaffolds now include a code generator inside them. The generated code works — but quality gates are usually weak: no real compilation check, no structured contract test, no template versioning, no rollback, and no change ledger. When a template change produces broken output, you won't find out until runtime.

CodeGen's **quality-gate chain** is its differentiator:

| Step | What it actually does |
| --- | --- |
| Preview & diff | Generates to memory, renders per-file diffs against disk, you confirm before anything is written |
| Write with backup | Every touched file is copied to `.gen-backup/<batchNo>/` before being overwritten |
| Marked-zone merge | Hand-written code inside `// gen:begin-end` blocks is preserved; only generated zones are replaced |
| In-memory self-check | A real `javac` compile of all generated back-end artifacts (not a regex lint) — nothing lands on disk unless it compiles |
| Independent class-path compile | Generated integration tests are compiled against your target project's classpath — missing dependencies fail at write time, not runtime |
| Four-state ledger | `gen_output` records every file as **created / merged / skipped / failed**, with byte count, MD5, and backup path |
| Rollback by batch | Any batch can be rolled back in one click, restoring exactly the state before that generation ran |

These seven links — preview → backup → merge → javac self-check → class-path compile → ledger → rollback — are **not present in the built-in generators of any popular scaffold framework** the author is aware of.

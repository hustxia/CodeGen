# Majiang · Jenereta ya Kanuni (CodeGen) — Utangulizi wa Mradi

> Lugha: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · **Kiswahili** · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> Jenereta ya kanuni ya miradi mingi kwa **Spring Boot + Vue**: ikianza na muundo wa meza za hifadhidata, inazalisha kanuni ya biashara ya **inayoweza kukusanywa, inayoweza kuendeshwa** kwa mbele na nyuma na menyu na ruhusa, na inasimamia mzunguko kamili wa utoaji: kuandika kwenye diski, nakala za chelezo, leja, na kurudisha nyuma.

GitHub: https://github.com/hustxia/CodeGen

## 1. Ni Nini Hii

CodeGen ni "kiwanda cha kanuni kinachoendeshwa na meza". Lete meza zako za biashara (kwa kuagiza kutoka hifadhidata au kwa kubandika DDL), sanidi nyanja na swichi za kuzalisha, kisha bonyeza "Zalisha" — basi inazalisha Entity/Mapper/Service/Controller/DTO/VO za nyuma, `api.js` na kurasa za Vue za mbele, menyu na njia, hata kurasa za uchapishaji A4 na mifupa ya jaribio la ujumuishaji, na kuandika kila kitu kwa usalama kwenye mradi wako wa lengo.

Sio tu "inayoondoa kanuni" — ni **bomba la utoaji na milango ya ubora na uwezo wa kurudisha nyuma**.

## 2. Ni Nani Watumiaji, na Jinsi Watakavyoitumia

| Mtumiaji | Hitaji Maalum | Njia Inayotolewa na Jukwaa |
| --- | --- | --- |
| Msanidi wa nyuma | Ana meza kadhaa (au DDL), anataka kuanzisha CRUD haraka | Ukurasa wa usanidi wa meza: kuagiza → usanidi wa nyanja → zalisha kwa bonyeza moja |
| Full-stack / msanidi huru | Anataka kujenga mfumo kamili kutoka mwanzo (ingia, ruhusa, kamusi) | Ukurasa wa bomba: unda mfumo kwa sentensi moja |
| Kiongozi wa timu | Anataka kuunganisha mtindo wa kanuni, viwango vya kanuni, muundo wa saraka | Usimamizi wa mfumo: mfumo mmoja = usanidi mmoja wa pato, unaotumika kwa kundi |
| Mtunzaji wa jukwaa | Anataka kubadilisha mifano bila kubadilisha kanuni | Usimamizi wa mifano: inayoweza kuhaririwa mtandaoni, inayotumika mara moja baada ya kuhifadhi, inaauni kurudisha nyuma kwa matoleo |

## 3. Inatatua Matatizo Gani

| Jambo la Maumivu | Nini CodeGen Inafanya |
| --- | --- |
| Kuzimu kwa CRUD inayorudiwa — rundo la boilerplate kwa kila meza | Swichi za kiwango cha meza zinazalisha mbele na nyuma kamili kwa bonyeza moja, ikiwa ni pamoja na CRUD, ukurasa, uthibitishaji, kuagiza/kuuza nje |
| Kutoendana kwa nyanja na API kati ya mbele na nyuma | Metadata ya meza moja inaelekeza API za nyuma na kurasa za mbele — zero kutofautiana kwa maana |
| Kanuni za timu ngumu kuunganisha; kila mtu anaandika kwa mtindo wake | Mifano ni kanuni: mifano 22 ya FreeMarker inayosimamiwa kati, inayoweza kuhaririwa mtandaoni na kurudisha nyuma kwa matoleo |
| Jenereta "zinazibonyeza na kupoteza" mantiki iliyoandikwa kwa mkono | Mfumo wa kuunganisha vizuizi vya alama: kanuni iliyoandikwa kwa mkono imehifadhiwa, tu maeneo yaliyozalishwa ndiyo yanabadilishwa |
| Kanuni iliyozalishwa haiaminika mpaka wakati wa kukusanya | Kujikagua kwa kukusanya katika kumbukumbu (`javac` halisi), ukaguzi wa mikataba ya muundo, hakiki ya diff kwenye diski — hakuna chochote kinachoanguka kwenye diski isipokuwa kikikusanyika |
| Kuanzisha mfumo mpya kutoka mwanzo kunahitaji wakati | Bomba "unda mfumo kwa sentensi moja": unda DB → kiunzi → zalisha → pakiti → sajili menyu, otomatiki kabisa |

## 4. Muhtasari wa Uwezo

- **Kuingiza meza**: uchambuzi wa DDL, kuagiza moja kwa moja kutoka hifadhidata, mshauri wa kuingiza kwa kundi, usanidi wa safu unaopatikana moja kwa moja (vidhibiti / uthibitishaji / kamusi / uhusiano).
- **Miundo ya meza**: meza moja, meza ya mti (API ya mti + gridi ya mti), bwana-undani (kuhifadhiwa kwa shughuli moja + vichupo vya undani), meza za kuunganisha za nyingi-kwa-nyingi.
- **Vitu vilivyozalishwa**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + kurasa za Vue + kurasa za uchapishaji A4 + mifupa ya jaribio la ujumuishaji.
- **Maboresho ya biashara**: menyu za kushuka za uhusiano, tafsiri ya kamusi, kuficha data, ukaguzi wa kipekee, kuunganisha fomu, ruhusa za eneo la data, Excel kuagiza/kuuza nje, mtiririko wa kuthibitisha, vifurushi vya uwezo wa malipo / faili / simu.
- **Milango ya ubora**: kujikagua kwa kukusanya katika kumbukumbu (`javac` halisi), ukaguzi wa mikataba ya muundo, ukaguzi wa uthabiti wa ruhusa, hakiki ya diff kwenye diski, uthibitishaji halisi wa vitu vya mbele.
- **Kulandi salama**: kuandika kwenye diski + nakala za chelezo za kundi `.gen-backup`, kuunganisha vizuizi vya alama kuhifadhi kanuni iliyoandikwa kwa mkono, leja ya hali nne `gen_output` (iliyoundwa / imeunganishwa / imepitiwa / imeshindwa), kurudisha nyuma kwa historia.
- **Kutenga mifumo mingi**: kila mradi wa lengo ni "usanidi wa mfumo" (njia za mbele/nyuma, majina ya pakiti, kiambishi cha lango, classpath ya tegemezi, thamani za kiwango cha chini za swichi za kuzalisha).
- **Maboresho ya AI**: unda mfumo kwa sentensi moja (muundo wa AI), maboresho ya AI ya data ya mbegu, usanidi uliopendekezwa na AI kwa kiwango cha meza.

## 5. Mifano ya Kawaida

| Mfano | Maelezo |
| --- | --- |
| Meza ya mti | Data ya daraja kama vile kategoria za bidhaa — inazalisha moja kwa moja API ya mti, gridi ya mti na menyu ya kushuka ya uchaguzi wa mzazi |
| CRUD ya meza moja | Wateja, bidhaa, n.k. — menyu za kushuka FK + tafsiri ya kamusi + kuagiza/kuuza nje tayari kutumika |
| Bwana-undani | Oda za mauzo + vitu vya oda — kuhifadhiwa kwa shughuli moja, droo ya undani na vichupo viwili |
| Kusoma tu / takwimu | Mtiririko wa bidhaa zilizopo, n.k. — swichi za kuandika zimezimwa, swali tu na kuuza nje |
| Hati za uchapishaji | Chagua swichi ya "chapisha" kuzalisha kurasa ya uchapishaji A4 na njia |
| Kujenga mfumo kwa sentensi moja | Eleza eneo la biashara — AI inazalisha DB & meza + kiunzi + kuzalisha kamili + usajili wa menyu kwa hatua moja |

## 6. Mtiririko wa Kazi wa Msingi

```
Kuingiza meza → Usanidi wa nyanja/swichi → Zalisha kwa bonyeza moja → Leja · Rudisha nyuma
Kuingiza DB/DDL  Vidhibiti/uthibitishaji   Hualandisha kwenye diski tu baada ya milango yote ya ubora;
                 /kamusi                    kanuni iliyoandikwa kwa mkono imehifadhiwa
```

## 7. Unda Mfumo kwa Sentensi Moja (Bomba)

| Hatua | Maana |
| --- | --- |
| `CHECK` | Inathibitisha usanidi wa mradi wa lengo na classpath ya tegemezi |
| `WRITE` | Inaunda hifadhidata & meza, kuandika kiunzi na vitu vilivyozalishwa |
| `COMPILE` | Nyuma `mvn package` / mbele jenga, kujikagua kwa kukusanya katika kumbukumbu |
| `MENU` | Inazalisha na kusajili ruhusa za menyu (vituo viwili: SQL ya vitu / kuandika moja kwa moja ndani) |
| `ROUTE` | Inaunda upya vipande vya njia za mbele |

## 8. Usanifu wa Mfumo

| Hifadhi | Jukumu | Mfuriko |
| --- | --- | --- |
| `codegen-server` | Nyuma: injini ya kuzalisha, mifano, uthibitishaji, leja, bomba | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Mbele: konsole (usanidi wa meza / mifano / leja / usimamizi wa mfumo) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (historia) |

## 9. Mfuriko wa Teknolojia

| Upande | Chaguo |
| --- | --- |
| Lugha/mfumo wa nyuma | Java 21 / Spring Boot 3.2.5 |
| Kudumu | MyBatis-Plus 3.5.5 |
| Injini ya mifano | FreeMarker (mifano 22 ya mbegu, uhariri mtandaoni + kurudisha nyuma matoleo) |
| Usalama | Uthibitishaji wa JWT |
| Hifadhidata | MySQL 8 |
| Mbele (generated projects) | Vue 3 + Element Plus + Vite |
| Mbele (platform console) | Vue 2.7 + Element UI |
| AI ya ndani | qwen2.5-coder:7b |
| Nyaraka za API | springdoc-openapi (Swagger UI) |

## 10. Anza Haraka

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

Nyuma default kwenye bandari **8120**, mbele kwenye bandari **9530**. Akaunti ya default `admin / admin123`.

## 11. Hali ya Mradi

- Mstari wa matoleo uliopangwa sasa umefikia **V9.3**.
- **V9.3** · Majaribio ya kiotomatiki ya vitu vilivyozalishwa · Generated-artifact automated testing: S10 (mvn test + Surefire), S11 (API / TestContainers), S12 (E2E / Playwright); GEN trigger + TC_GEN_ROOT; 12-step pipeline green; real artifact run PASSED 20/20, run/case/artifact persisted
- **V9.2** · Kituo cha majaribio & uthibitishaji · Test Center (7 tables: env/suite/case/dataset/run/result/schedule) + Verification Center (tc_run/tc_case_result/tc_artifact, 9 suites S1-S9, single-run 409 lock, `codegen.testcenter/v1`); 373/373 tests; 206/206 locales; output byte-identical
- **V9.1** · Urekebishaji kwa matengenezo rahisi · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9
- Hifadhi zote mbili zinapatikana kwenye Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

## 12. Mwandishi

- **Mwandishi**: mpangaji wa programu wa Kichina (msanidi huru)
- **Barua pepe**: `hustxiayuhong@126.com`
- **Msaada wa teknolojia**: tafadhali andika barua pepe yako kwa **Kichina**, pamoja na maelezo ya tatizo, mazingira ya runtime (JDK / MySQL / toleo), na hatua za kuzalisha tena.

---

## 13. Ulinganisho na Zana Sawa

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

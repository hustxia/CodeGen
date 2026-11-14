# Majiang · Kodgenerator (CodeGen) — Projektintroduktion

> Språk: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · **Svenska** · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> En projektövergripande kodgenerator för **Spring Boot + Vue**: med utgångspunkt i databastabellstrukturer producerar den **kompilerbar, körbar** affärskod för front- och back-end med menyer och behörigheter, och hanterar den fullständiga leveranscykeln: skrivning till disk, backup, reskontra (ledger) och återställning.

GitHub: https://github.com/hustxia/CodeGen

> **aktuell version V9.3 · Automatiserade tester av genererade artefakter · Generated-artifact automated testing: S10 (mvn test + Surefire), S11 (API / TestContainers), S12 (E2E / Playwright); GEN trigger + TC_GEN_ROOT; 12-step pipeline green; real artifact run PASSED 20/20, run/case/artifact persisted**
> **V9.2 release · Testcenter & verifieringshubb · Test Center (7 tables: env/suite/case/dataset/run/result/schedule) + Verification Center (tc_run/tc_case_result/tc_artifact, 9 suites S1-S9, single-run 409 lock, `codegen.testcenter/v1`); 373/373 tests; 206/206 locales; output byte-identical**
> **V9.1 · Underhållsrefaktorering · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9**
> **V9.0 · Genererade artefakter moderniserade till Vue 3 + Element Plus + Vite; verifierade i drift (40/40 asserteringar, lyckad vite build, 14 skärmbilder inkl. ar-SA RTL); 3 runtime-fel åtgärdade**
> **V8.1** · Kvalitetshärdning: offline kontrakts-harness (9 lägen, OFF zero-diff) + i18n-core enhetstester + CI-grind + ärlig README-fördelning (ramverk 206 / översatta 8) + check-locale-keys.mjs
> **V8.0** · Helsides-i18n: ramverk med 206 språk (8 professionellt översatta), tvåspårig i18n med OFF zero-diff-kontrakt

## 1. Vad är detta

CodeGen är en "tabellstyrd" kodfabrik. Ta med dina affärstabeller (via databasimport eller genom att klistra in en DDL), konfigurera fält och generationsomkopplare och klicka på "Generera" — då producerar den backend Entity/Mapper/Service/Controller/DTO/VO, frontendens `api.js` och Vue-sidor, menyer och rutter, till och med A4-utskriftssidor och integrationstestskelett, och skriver allt säkert till ditt målprojekt.

Det är inte bara "kod som spottas ut" — det är en **leveranspipeline med kvalitetsportar och återställningsförmåga**:

```
Tabellinmatning (DB-import / DDL)
   → Fält- och omkopplarkonfiguration (med AI-rekommendationer)
      → Engångsklickgenerering: självkontroll → backup → skriv till disk → kompilera/validera → registrera meny → bygg om rutter
         → Reskontrabokning → återställningsbar
```

---

## 2. Vilka är användarna och hur använder de det

| Användare | Typiskt behov | Vägen plattformen erbjuder |
| --- | --- | --- |
| Back-end-utvecklare | Har en uppsättning tabeller (eller DDL) och vill snabbt sätta upp CRUD | Tabellkonfigurationssida: importera → konfigurera fält → generera med ett klick |
| Full-stack / fristående utvecklare | Vill bygga ett komplett system från grunden (inkl. inloggning, behörigheter, ordbok) | Pipelinsida: bygg ett system med en mening |
| Teamledare | Vill enhetliga kodstil, kodstandarder och katalogstruktur | Systemhantering: ett system = en utdatakonfiguration, gäller i batch |
| Plattformsadministratör | Vill ändra mallar utan att ändra kod | Mallhantering: redigera online, träder i kraft direkt vid sparning, med versionsåterställning |

## 3. Vilka problem den löser

| Smärtpunkt | Vad CodeGen gör |
| --- | --- |
| Upprepad CRUD-helvete — en hög med boilerplate för varje tabell | Tabellberoende omkopplare producerar komplett front- och back-end med ett klick, inklusive CRUD, paginering, validering, import/export |
| Fält- och API-mellanrum mellan front och back som orsakar oändliga integrationsfixar | Samma tabellmetadata driver både backend-API:er och frontend-sidor — noll semantisk avvikelse |
| Teamkonventioner svåra att enas om; alla skriver i sin egen stil | Mallar är konventionen: 22 FreeMarker-mallar centralt hanterade, redigerbara online med versionsåterställning |
| Generatorer "skriver över och förlorar" handskriven logik | Marker-block sammanslagningsmekanism: handskriven kod bevaras, endast genererade regioner ersätts |
| Genererad kod otillförlitlig förrän kompilering | Självkontroll med kompilering i minnet (äkta `javac`), strukturella kontraktskontroller, diff-förhandsvisning på disk — inget hamnar på disk om det inte kompilerar |
| Att bygga ett nytt system från grunden är tidskrävande | Pipeline "bygg ett system med en mening": skapa DB → ställning → generera → paketera → registrera menyer, helt automatiserat |

---

## 4. Kapacitetsöversikt

- **Tabellinmatning**: DDL-parsning, direkt databasimport, satsvis inmatningsguide, automatiskt härledd kolumnkonfiguration (kontroller / validering / ordböcker / relationer).
- **Tabellformer**: enkel tabell, träd-tabell (tree API + trädrutnät), master-detail (lagras i samma transaktion + detaljflikar), många-till-många kopplingstabeller.
- **Genererade artefakter**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue-sidor + A4-utskriftssidor + integrationstestskelett.
- **Affärsförbättringar**: relationsrullgardiner, ordboksöversättning, datamaskering, unikhetskontroller, formulärkoppling, databehörighet, Excel import/export, godkännandeflöde, betalnings- / fil- / mobilitetskapacitetspaket.
- **Kvalitetsportar**: självkontroll med kompilering i minnet (äkta `javac`), strukturella kontraktskontroller, behörighetskonsistenskontroller, diff-förhandsvisning på disk, verklig validering av frontend-artefakter.
- **Säker landning**: skrivning till disk + `.gen-backup` satsvisa backuper, marker-block sammanslagning för att bevara handskriven kod, `gen_output` fyrtillståndsreskontra (skapad / sammanslagen / överhoppad / misslyckad), historisk återställning.
- **Flera system isolerade**: varje målprojekt är en "systemkonfiguration" (front/back sökvägar, paketnamn, gateway-prefix, beroende-classpath, generationsomkopplarstandardvärden) — flera projekt hanteras parallellt.
- **AI-förbättringar**: bygg ett system med en mening (AI-modellering), frödata AI-förbättring, tabellberoende AI-rekommenderad konfiguration (diff-förhandsvisning + markerad tillämpning).

---

## 5. Typiska scenarier

| Scenario | Beskrivning |
| --- | --- |
| Träd-tabell | Hierarkisk data som produktkategorier — producerar automatiskt tree API, trädrutnät och överordnad vald rullgardin |
| Enkel tabell CRUD | Kunder, produkter, etc. — FK-rullgardiner + ordboksöversättning + import/export direkt ur lådan |
| Master-detail | Försäljningsorder + orderrader — lagras i samma transaktion, detaljlåda med dubbla flikar (masterinfo / detaljer) |
| Skrivskyddad / statistik | Lagerflöden, etc. — skrivomkopplare av, endast fråga och export |
| Utskriftsdokument | Kryssa i "utskrift"-omkopplaren för att producera en A4-utskriftssida och rutt, med rubrik + detaljtabell |
| En-meningars systembyggnad | Beskriv affärsdomen — AI skapar DB & tabeller + ställning + fullständig generering + menyregistrering i ett svep |

> Alla scenarier har körbara demo:r (`demo_admin`-projekt) med skärmdumpar — se `../codegen-server/docs/典型场景操作指南-演示后台demo.md` (kinesiska).

---

## 6. Grundläggande arbetsflöde

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Tabellinmatning │ → │ Fält/omkopplarkonf. │ → │ Engångsklick generera (självkontroll→backup→skriv→     │ → │ Reskontra · Återställning │
│              │   │                    │   │ kompilera→meny→rutt)                              │   │                   │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     kontroller/validering     Hamnar på disk först efter alla kvalitetsportar;
                    /ordböcker                handskriven kod bevaras
```

- **Diff-förhandsvisning**: genererade artefakter diffas fil för fil mot nuvarande diskstatus; du bekräftar innan något skrivs, för att undvika oavsiktliga överskrivningar.
- **Generationsreskontra**: varje fil registrerar sin åtgärd, byteantal, MD5 och backuplokalisering; en hel sats kan återställas.

---

## 7. Bygg ett system med en mening (Pipeline)

En högnivåkapacitet inriktad på "att bygga en backend från grunden". Efter att du beskrivit affärsdomen avancerar pipelinen automatiskt genom 5 faser:

| Fas | Betydelse |
| --- | --- |
| `CHECK` | Validerar målprojektkonfiguration och beroende-classpath |
| `WRITE` | Skapar databas & tabeller, skriver ställning och genererade artefakter |
| `COMPILE` | Backend `mvn package` / frontend byggnad, självkontroll med kompilering i minnet |
| `MENU` | Producerar och registrerar menybehörigheter (dubbla uttag: artefakt SQL / intern direkt skrivning) |
| `ROUTE` | Bygger om frontend-ruttfragment |

Varje fasfel avbryter omedelbart med ett läsbart fel för enkel lokalisering.

---

## 8. Systemarkitektur

Front- och back-end separerade, två repositorier som arbetar tillsammans:

| Repositorium | Roll | Teknikstack |
| --- | --- | --- |
| `codegen-server` | Backend: generationsmotor, mallar, validering, reskontra, pipeline | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Frontend: konsol (tabellkonfig / mallar / reskontra / systemhantering) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (history mode) |

Backend-lager:

```
controller/    API-lager (Gen* generationskedja / Sys* systemhantering / pipeline)
service/impl/  Kärnimplementeringar (generationskedja, mallmotor, sammanslagning, validering, pipelineutförande)
util/          Mallmotor, modellmontering, marker-block sammanslagning, självkontrollkompilering
config/        Utdatakonfig, självkontrollkonfig, mallsåmaskin
common/        Result / BaseEntity / undantag / revision
resources/gen-templates/   22 FreeMarker-mallar (backend / frontend / shared / test grupper)
```

**Grundläggande kapaciteter som målprojektet måste tillhandahålla** (generatorn producerar endast affärskod; saknad grund fallerar tyst):

1. MyBatis-Plus pagineringsplugin (`PaginationInnerInterceptor`)
2. Grundklasser som `Result` / `BaseEntity` / `PageQuery` (paketprefix måste matcha `root-package`)
3. Frontend `utils/request.js` med `toPageData` (konverterar `Page{records,total}` till `{list,total}`)
4. Beroenden `springdoc-openapi-starter-webmvc-ui` (genererad kod använder `@Operation`) och `poi-ooxml` (Excel)

---

## 9. Teknikstack i översikt

| Dimension | Val |
| --- | --- |
| Backend-språk / ramverk | Java 21 / Spring Boot 3.2.5 |
| Beständighet | MyBatis-Plus 3.5.5 |
| Mallmotor | FreeMarker (22 frömallar, online-redigering + versionsåterställning) |
| Säkerhet | JWT-autentisering |
| Databas | MySQL 8 |
| Frontend (generated projects) | Vue 3 + Element Plus + Vite |
| Frontend (platform console) | Vue 2.7 + Element UI |
| Lokal AI | qwen2.5-coder:7b (modellering / rekommenderad konfig / frödataförbättring) |
| API-dokumentation | springdoc-openapi (Swagger UI) |

---

## 10. Snabbstart

### 1. Skapa databasen (13 plattformstabeller + standardkonto admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Konfigurera målprojektet

Kopiera config-mallen och justera efter dina lokala sökvägar:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` är exkluderad av `.gitignore` och kommer inte att committas. Huvudinställningar:

```yaml
gen:
  output:
    backend-root: /sökväg/till/ditt-målprojekt      # skrivbas för artefakter
    frontend-root: /sökväg/till/ditt-målprojekt
    frontend-path: demo-web                          # frontend-undermapp för monorepor
    backend-path: demo-server/src/main/java/com/demo
    package-name: com.demo
    root-package: com.demo                           # prefix för grundklassimporter i artefakter
  selfcheck:
    classpath: /sökväg/till/ditt-målprojekt/demo-lib/*
```

Exportera självkontrollberoende-mappen så här:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Starta backend

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

Tjänsten körs som standard på port **8120**; API-dokumentation på `/swagger-ui/index.html`.

### 4. Starta frontend

```bash
cd ../codegen-web
npm install
npm run serve
```

Frontenden körs som standard på port **9530**, proxyrar `/dev-api` till backend-port 8120. Standardkonto `admin / admin123`.

### 5. Viktiga konfigurationsobjekt

| Inställning | Beskrivning |
| --- | --- |
| `gen.output.*` | Artefakt-sökvägar, paketnamn, mappning av moduler |
| `gen.selfcheck.classpath` | Beroende-classpath för självkontroll med kompilering i minnet, stöder `dir/*` |
| `gen.menu.outlet` | `artifact` producerar endast meny SQL & ruttfragment; `internal` skriver även plattformens egen `sys_menu` |

---

## 11. Dokumentationskarta

| Dokument | Innehåll |
| --- | --- |
| `../codegen-server/docs/代码生成器-操作手册.md` (kinesiska) | Distribution, 5-minutersstart, tabellkonfig, satsvis inmatning, mallhantering, reskontra, API-katalog, fullständig pipeline (med skärmdumpar) |
| `../codegen-server/docs/典型场景操作指南-演示后台demo.md` (kinesiska) | 6 tabeller som täcker 5 typiska scenarier, med körbar demo och verkliga skärmdumpar |
| `项目介绍.md` | Projektintroduktionsdokument, samma källa som denna fil |
| `../codegen-server/docs/代码生成器-Vx.x-规划.md` | Versionsplaner och funktionsinventarier från V3.1 → V9.3 |
| `../codegen-web/README.md` | Frontend-konsol snabbstartreferens |

---

## 12. Versionshistorik

| Version | Tema / plandokument | Landningscommit |
| --- | --- | --- |
| V1.2 / V1.3 | Engångsklickgenerering / satsvis / återställning; diff-förhandsvisning & struktur同步 | — (endast i `src/api/gen.js` kommentarer) |
| V2.0 | Dynamiska rutter (backend-levererad menyträd) | — |
| V3.0 / V3.1 | Flera systems reskontra; flera system loop + första automatiserade tester | V3.1 (174 rader) |
| V4.2 | Spårbara, återställningsbara genereringsresultat (skivreskontra) | V4.2 (292 rader) |
| V4.3 | Mallregression & kvalitetsguider | V4.3 (200 rader) |
| V4.4 | En-och-många, kaskadskriv fullständighet, mallfiler | V4.4 (154 rader) |
| V4.5 | A4-utskriftsdokument (noll tredjepartsberoenden) | V4.5 (127 rader) |
| V5.0 | En-meningars systembyggnad: pipeline + AI-modellering + ställningsgrund | — |
| V5.1 | DDL revisionskolumn automatisk komplettering | V5.1 (100 rader) |
| V5.2 | Pipeline frödatainitialiseringssteg | V5.2 (107 rader), 84ec8dd |
| V5.5 | Fler-modulsprojekt generering avslutning | af73dd3 |
| V6.0 | Mikrotjänsteinfrastruktur (Nacos / Gateway / Feign, tredje läget "micro") | V6.0 (111 rader), 1deecbd |
| V6.1 | Activiti arbetsflöde dubbelmotor | V6.1 (100 rader), 02b671e |
| V6.2 | Artefaktleveransyta (CLI / deploy-artefakter / konfigurationssäkerhet) | V6.2 (114 rader), 02b671e |
| V6.3 | Betalningskapacitetspaket (WeChat / Alipay + Mock) | V6.3 (104 rader), 1c4ee6a, 22eede5 |
| V6.4 | Behörigheter & säkerhetsgrund (tokenpersistens / BCrypt / API-behörigheter / inloggningsskydd) | V6.4 (107 rader), 007a202 |
| V6.5 | Mobil uni-app (H5 + WeChat Mini Program) | V6.5 (105 rader), 779ac41 |
| V6.6 | Filkapacitetspaket | 98f3c09 |
| V6.7 | Artefakttest fabriksgodkännande (test_enabled treläge + H2-skelett) | V6.7 (134 rader), 4e7d318 |
| V6.8 | Artefaktunderhållbarhet (dryRun / skyddad skip / varumärkesparameterisering) | V6.8 (141 rader), be6f858 |
| V6.9 | Plattformsdokumentationscentrum (backendgrund, frontend avslutad i samma sats) | — |
| V6.10 | Plattformsobserverbarhet: Actuator + in-memory metriskt fönster + inbäddad monitorsida | — |
| V7.0 | AI sid- & menyrekommendationer (preview / apply) | V7.0 (94 rader), 45f2670 |
| V7.1 | Frödata AI-förbättring (AI äger semantik, regler äger struktur) | V7.1 (42 rader), 5ca6e72 |
| V7.2 | SQL-leverbara objekt skrivna till `db/` efter lyckad pipeline | eebff88 |
| **V7.3** | **Leveranspålitlighet: självkontroll kompilerade artefakttester / enhetlig SQL-leveransuttag / tabellberoende AI-rekommendationer** | **V7.3 (138 rader), 696765b** |
| **V7.4** | Återställningsbar leverans: deployerbar från en tom databas, varje commit garanterad körbar | c306e29 |
| **V7.5** | **Projektspråkskonfiguration: lista över 206 språk / standardspråk / aktiverade språk, injicering av lang och dir i genererad kod** | 7a9afa0 + abac56d |
| **V8.0** | **Helsides-i18n: ramverk med 206 språk (8 professionellt översatta), tvåspårig i18n med OFF zero-diff-kontrakt** | 9e5dd57 → 7d57aea |
| **V8.1** | **Kvalitetshärdning: offline kontrakts-harness (9 lägen, OFF zero-diff) + i18n-core enhetstester + CI-grind + ärlig README-fördelning (ramverk 206 / översatta 8) + check-locale-keys.mjs** | 93a12d5 + e49330b |
| **V9.0** | **Genererade artefakter moderniserade till Vue 3 + Element Plus + Vite; verifierade i drift (40/40 asserteringar, lyckad vite build, 14 skärmbilder inkl. ar-SA RTL); 3 runtime-fel åtgärdade** | 93a12d5 + e49330b  |
| **V9.1** | **Underhållsrefaktorering · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9** | 654b8e0 → b0d081e  |
| **V9.2** | **Testcenter & verifieringshubb · Test Center (7 tables: env/suite/case/dataset/run/result/schedule) + Verification Center (tc_run/tc_case_result/tc_artifact, 9 suites S1-S9, single-run 409 lock, `codegen.testcenter/v1`); 373/373 tests; 206/206 locales; output byte-identical** | V9.2 release  |
| **V9.3** | **Automatiserade tester av genererade artefakter · Generated-artifact automated testing: S10 (mvn test + Surefire), S11 (API / TestContainers), S12 (E2E / Playwright); GEN trigger + TC_GEN_ROOT; 12-step pipeline green; real artifact run PASSED 20/20, run/case/artifact persisted** | V9.3 release, **aktuell version** |

---

> **V6.10 Plattformsobserverbarhet**: integrerar Actuator + Micrometer, provtar var 5:e sekund till en in-memory ringbuffert (30-minutersfönster), plus en ny inbäddad "Systemmonitor"-sida — backend `/monitor/*` aggregerings-API:er + frontend ECharts paneler. Endast `health/info/metrics` endpoints är vitlistade; `/actuator/health` är tokenfri medan `metrics` etc. kräver auth; `env/beans/heapdump/threaddump/loggers` exponeras aldrig. Provtagningsfönstret är endast per process, förloras vid omstart, och delas inte mellan instanser. Se `../codegen-server/docs/可观测性说明.md` (kinesiska) för detaljer.

## 13. Projektstatus

- Den nuvarande planerade versionslinjen har nått **V9.3** (kapaciteter fortsätter iterera: AI-modellering, frödataförbättring, kapacitetspaketsystem, etc.).
- Ett medföljande demo-projekt `demo_admin` (backend 8140 / frontend 9040) täcker träd / CRUD / master-detail / skrivskyddad / utskriftsscenarier och kör fristående för verifiering.
- Båda repositorierna hostas på Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. Författare & Support

- **Författare**: en kinesisk programmerare (oberoende utvecklare)
- **E-post**: `hustxiayuhong@126.com`
- **Teknisk support**: skriv din e-post **på kinesiska**, inklusive problembeskrivning, körmiljö (JDK / MySQL / version) och reproduktionssteg

> Jag läser och svarar på varje e-post. Felrapporter, funktionsförfrågningar och samarbetsförfrågningar är alla välkomna.

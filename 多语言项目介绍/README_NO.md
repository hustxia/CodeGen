# Majiang · Kodegenerator (CodeGen) — Prosjektintroduksjon

> Språk: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · **Norsk** · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> En flerprosjekt kodegenerator for **Spring Boot + Vue**: Utgangspunkt i databasetabellstrukturer produserer den **kompileringbar, kjørbar** forretningskode for for- og bakre deler med menyer og rettigheter, og håndterer hele leveringssyklusen: skriving til disk, sikkerhetskopi, hovedbok (ledger) og tilbakestilling.

GitHub: https://github.com/hustxia/CodeGen

> **gjeldende versjon V9.3 · Automatiserte tester av genererte artefakter · Generated-artifact automated testing: S10 (mvn test + Surefire), S11 (API / TestContainers), S12 (E2E / Playwright); GEN trigger + TC_GEN_ROOT; 12-step pipeline green; real artifact run PASSED 20/20, run/case/artifact persisted**
> **V9.2 release · Testsenter & verifikasjonssenter · Test Center (7 tables: env/suite/case/dataset/run/result/schedule) + Verification Center (tc_run/tc_case_result/tc_artifact, 9 suites S1-S9, single-run 409 lock, `codegen.testcenter/v1`); 373/373 tests; 206/206 locales; output byte-identical**
> **V9.1 · Vedlikeholdsrefaktorering · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9**
> **V9.0 · Genererte artefakter modernisert til Vue 3 + Element Plus + Vite; runtime-verifisert (40/40 asserts, vellykket vite build, 14 skjermbilder inkl. ar-SA RTL); 3 runtime-feil rettet**
> **V8.1** · Kvalitetsherdning: offline kontrakt-harness (9 moduser, OFF zero-diff) + i18n-core enhetstester + CI-port + ærlig README-inndeling (rammeverk 206 / oversatt 8) + check-locale-keys.mjs
> **V8.0** · Fullside-i18n: rammeverk med 206 språk (8 profesjonelt oversatt), tosporet i18n med OFF zero-diff-kontrakt

## 1. Hva er dette

CodeGen er en "tabellstyrt" kodefaktor. Ta med deg dine forretningstabeller (via databaseimport eller ved å lime inn en DDL), konfigurer felt og genereringsbrytere og klikk på "Generer" — da produserer den bakre delens Entity/Mapper/Service/Controller/DTO/VO, den forreste delens `api.js` og Vue-sider, menyer og ruter, selv A4-utskriftssider og integrasjonstestskjeletter, og skriver alt sikkert til ditt målprosjekt.

Den "spyr ikke bare ut kode" — den er en **leveringspipeline med kvalitetsgrinder og tilbakestillingsevne**:

```
Tabellinntak (DB-import / DDL)
   → Felt- og bryterkonfigurasjon (med AI-anbefalinger)
      → Énklikksgenerering: selvkontroll → sikkerhetskopi → skriv til disk → kompiler/valider → registrer meny → bygg om ruter
         → Hovedbokføring → tilbakestillbar
```

---

## 2. Hvem er brukerne og hvordan bruker de det

| Bruker | Typisk behov | Banen plattformen tilbyr |
| --- | --- | --- |
| Back-end-utvikler | Har et sett med tabeller (eller DDL) og vil raskt sette opp CRUD | Tabellkonfigurasjonsside: importer → konfigurer felt → generer med ett klikk |
| Full-stack / uavhengig utvikler | Vil bygge et komplett system fra bunnen (inkl. innlogging, rettigheter, ordbok) | Pipelineside: bygg et system med én setning |
| Teamleder | Vil forene kodestil, kodestandarder og katalogstruktur | Systemhåndtering: ett system = én utdata-konfigurasjon, gjelder i batch |
| Plattformadministrator | Vil endre maler uten å endre kode | Malhåndtering: rediger online, trer i kraft umiddelbart ved lagring, med versjonstilbakestilling |

## 3. Hvilke problemer den løser

| Smertepunkt | Hva CodeGen gjør |
| --- | --- |
| Gjentatt CRUD-helvete — en haug med boilerplate for hver tabell | Tabellstyrte brytere produserer en komplett for- og bakre del med ett klikk, inkludert CRUD, paginering, validering, import/eksport |
| For/bak felt- og API-mismatcher som forårsaker endeløse integrasjonsfikser | Samme tabellmetadata driver både bakre del API-er og forreste del sider — null semantisk avvik |
| Teamkonvensjoner vanskelig å forene; alle skriver i sin egen stil | Maler er konvensjonen: 22 FreeMarker-maler sentralt styrt, redigerbare online med versjonstilbakestilling |
| Generatorer "overskriver og mister" håndskrevet logikk | Marker-blokkslåningsmekanisme: håndskrevet kode beholdes, kun genererte regioner erstattes |
| Generert kode upålitelig frem til kompileringstid | Selvkontroll med kompilering i minnet (ekte `javac`), strukturelle kontraktkontroller, diff-forhåndsvisning på disk — ingenting lander på disk med mindre det kompilerer |
| Å sette opp et nytt system fra bunnen av er tidskrevende | Pipeline "bygg et system med én setning": lag DB → stillas → generer → pakk → registrer menyer, helt automatisert |

---

## 4. Oversikt over kapasiteter

- **Tabellinntak**: DDL-parsing, direkte databaseimport, batch-inntaksveiviser, automatisk utledet kolonnekonfigurasjon (kontroller / validering / ordbøker / relasjoner).
- **Tabellformer**: enkel tabell, tre tabell (tree API + tredel), master-detail (lagring i samme transaksjon + detaljfaner), mange-til-mange koblingstabeller.
- **Genererte artefakter**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue-sider + A4-utskriftssider + integrasjonstestskjeletter.
- **Forretningsforbedringer**: relasjonsnedtrekksmenyer, ordboksoversettelse, datamaskering, unikhetskontroller, skjemaforbindelse, datarettigheter, Excel import/eksport, godkjenning arbeidsflyt, betaling / fil / mobil kapasitetspakker.
- **Kvalitetsgrinder**: selvkontroll med kompilering i minnet (ekte `javac`), strukturelle kontraktkontroller, rettighetskonsistenskontroller, diff-forhåndsvisning på disk, reell validering av forreste del artefakter.
- **Sikker landing**: skriving til disk + `.gen-backup` batch sikkerhetskopier, marker-blokkslåning for å beholde håndskrevet kode, `gen_output` firetilstanders hovedbok (opprettet / slått sammen / hoppet over / mislykket), historisk tilbakestilling.
- **Fler systemer isolert**: hvert målprosjekt er en "systemkonfigurasjon" (for/bak stier, pakkenavn, gateway-prefiks, avhengighets classpath, genereringsbryter standardverdier) — flere prosjekter betjenes parallelt.
- **AI-forbedringer**: bygg et system med én setning (AI-modellering), seed-data AI-forbedring, tabellstyrt AI-anbefalet konfigurasjon (diff-forhåndsvisning + merket anvendelse).

---

## 5. Typiske scenarier

| Scenario | Beskrivelse |
| --- | --- |
| Tre tabell | Hierarkisk data som produktkategorier — produserer automatisk tree API, tredel og overliggende valg nedtrekksmeny |
| Enkel tabell CRUD | Kunder, produkter osv. — FK nedtrekksmenyer + ordboksoversettelse + import/eksport rett bort |
| Master-detail | Salgsordrer + ordrelinjer — lagring i samme transaksjon, detaljskuff med doble faner (hovedinfo / detaljer) |
| Skrivebeskyttet / statistikk | Lagerstrømmer osv. — skrivebrytere av, kun spørring og eksport |
| Utskriftsdokumenter | Kryss av for "utskrift" bryteren for å produsere en A4-utskriftsside og rute, med overskrift + detaljetabell |
| Én setnings systembygging | Beskriv forretningsdomenet — AI oppretter DB & tabeller + stillas + fullstendig generering + menyregistrering i ett svep |

> Alle scenarier har kjørbare demoer (`demo_admin` prosjekt) med skjermbilder — se `../codegen-server/docs/典型场景操作指南-演示后台demo.md` (kinesisk).

---

## 6. Grunnleggende arbeidsflyt

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Tabellinntak │ → │ Felt/bryterkonf.   │ → │ Énklikk generer (selvkontroll→sikkerhetskopi→skriv→   │ → │ Hovedbok · Tilbakestilling │
│             │   │                   │   │ kompiler→meny→rute)                           │   │                            │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     kontroller/validering     Landet på disk først etter alle kvalitetsgrinder;
                    /ordbøker                 håndskrevet kode beholdes
```

- **Diff-forhåndsvisning**: genererte artefakter diffes fil for fil mot nåværende diskstatus; du bekrefter før noe skrives, for å unngå utilsiktede overskrivninger.
- **Genereringshovedbok**: hver fil registrerer sin handling, byteantall, MD5 og sikkerhetskopilasjonssted; en hel batch kan tilbakestilles.

---

## 7. Bygg et system med én setning (Pipeline)

En høy nivå kapasitet rettet mot "å sette opp en backend fra bunnen av". Etter at du beskriver forretningsdomenet, avanserer pipelinen automatisk gjennom 5 faser:

| Fase | Betydning |
| --- | --- |
| `CHECK` | Validerer målprosjektkonfigurasjon og avhengighets classpath |
| `WRITE` | Oppretter database & tabeller, skriver stillas og genererte artefakter |
| `COMPILE` | Bakre del `mvn package` / forreste del bygging, selvkontroll med kompilering i minnet |
| `MENU` | Produserer og registrerer menyrektigheter (dobbelt uttak: artefakt SQL / intern direkte skriving) |
| `ROUTE` | Bygger om forreste del rutefragmenter |

Enhver fasefeil avbryter umiddelbart med en lesbar feil for enkel lokalisering.

---

## 8. Systemarkitektur

Forreste og bakre del adskilt, to repositorier som samarbeider:

| Repositorium | Rolle | Stack |
| --- | --- | --- |
| `codegen-server` | Bakre del: generasjonsmotor, maler, validering, hovedbok, pipeline | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Forreste del: konsoll (tabellkonfig / maler / hovedbok / systemhåndtering) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (history mode) |

Bakre del lag:

```
controller/    API-lag (Gen* generasjonskjede / Sys* systemhåndtering / pipeline)
service/impl/  Kjerneimplementeringer (generasjonskjede, malmotor, slå sammen, validering, pipelineutførelse)
util/          Malmotor, modellmontering, marker-blokkslåning, selvkontrollkompilering
config/        Utdatakonfig, selvkontrollkonfig, malfrø
common/        Result / BaseEntity / unntak / revisjon
resources/gen-templates/   22 FreeMarker-maler (backend / frontend / shared / test grupper)
```

**Grunnleggende kapasiteter som målprosjektet må levere** (generatoren produserer bare forretningskode; manglende grunn mislykkes stille):

1. MyBatis-Plus pagineringsutvidelse (`PaginationInnerInterceptor`)
2. Grunnklasser som `Result` / `BaseEntity` / `PageQuery` (pakke-prefiks må stemme overens med `root-package`)
3. Forreste del `utils/request.js` med `toPageData` (konverterer `Page{records,total}` til `{list,total}`)
4. Avhengigheter `springdoc-openapi-starter-webmvc-ui` (generert kode bruker `@Operation`) og `poi-ooxml` (Excel)

---

## 9. Teknisk stabel i oversikt

| Dimensjon | Valg |
| --- | --- |
| Bakre del språk / rammeverk | Java 21 / Spring Boot 3.2.5 |
| Persistens | MyBatis-Plus 3.5.5 |
| Malmotor | FreeMarker (22 frømaler, online-redigering + versjonstilbakestilling) |
| Sikkerhet | JWT-autentisering |
| Database | MySQL 8 |
| Forreste del (generated projects) | Vue 3 + Element Plus + Vite |
| Forreste del (platform console) | Vue 2.7 + Element UI |
| Lokal AI | qwen2.5-coder:7b (modellering / anbefalet konfigurasjon / seed-data forbedring) |
| API-dokumentasjon | springdoc-openapi (Swagger UI) |

---

## 10. Rask start

### 1. Opprett databasen (13 plattformtabeller + standardkonto admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Konfigurer målprosjektet

Kopier malen for konfigurasjon og tilpass til dine lokale stier:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` er utelukket av `.gitignore` og vil ikke bli committet. Hovedinnstillinger:

```yaml
gen:
  output:
    backend-root: /sti/til/ditt-målprosjekt      # grunnlag for skriving av artefakter
    frontend-root: /sti/til/ditt-målprosjekt
    frontend-path: demo-web                          # forreste del undermappe for monorepos
    backend-path: demo-server/src/main/java/com/demo
    package-name: com.demo
    root-package: com.demo                           # prefiks for grunnklasseimport i artefakter
  selfcheck:
    classpath: /sti/til/ditt-målprosjekt/demo-lib/*
```

Eksporter selvkontroll avhengighetsmappen slik:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Start bakre del

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

Tjenesten kjører som standard på port **8120**; API-dokumentasjon på `/swagger-ui/index.html`.

### 4. Start forreste del

```bash
cd ../codegen-web
npm install
npm run serve
```

Forreste del kjører som standard på port **9530**, proxyer `/dev-api` til bakre del port 8120. Standardkonto `admin / admin123`.

### 5. Viktige konfigurasjons Punkter

| Innstilling | Beskrivelse |
| --- | --- |
| `gen.output.*` | Artefakt skrivstier, pakkenavn, modulutvidelse |
| `gen.selfcheck.classpath` | Avhengighets classpath for selvkontroll med kompilering i minnet, støtter `dir/*` |
| `gen.menu.outlet` | `artifact` produserer bare meny SQL & rutefragmenter; `internal` skriver også plattformens egen `sys_menu` |

---

## 11. Dokumentasjonskart

| Dokument | Innhold |
| --- | --- |
| `../codegen-server/docs/代码生成器-操作手册.md` (kinesisk) | Utplassering, 5 minutters start, tabellkonfig, batch-inntak, malhåndtering, hovedbok, API-katalog, fullstendig pipeline (med skjermbilder) |
| `../codegen-server/docs/典型场景操作指南-演示后台demo.md` (kinesisk) | 6 tabeller som dekker 5 typiske scenarier, med kjørbar demo og virkelige skjermbilder |
| `项目介绍.md` | Prosjektintroduksjonsdokument, samme kilde som denne filen |
| `../codegen-server/docs/代码生成器-Vx.x-规划.md` | Versjonsplaner og funksjonsinventarer fra V3.1 → V9.3 |
| `../codegen-web/README.md` | Forreste del konsoll rask start referanse |

---

## 12. Versjonshistorikk

| Versjon | Tema / plandokument | Landningscommit |
| --- | --- | --- |
| V1.2 / V1.3 | Énklikk generering / batch / tilbakestilling; diff-forhåndsvisning & struktur sync | — (bare i `src/api/gen.js` kommentarer) |
| V2.0 | Dynamiske ruter (bakre del levert menytre) | — |
| V3.0 / V3.1 | Fler systemers hovedbok; fler systemers løkke + første automatiserte tester | V3.1 (174 linjer) |
| V4.2 | Sporbare, tilbakestillbare genereringsresultater (diskhovedbok) | V4.2 (292 linjer) |
| V4.3 | Malregresjon & kvalitetsvakter | V4.3 (200 linjer) |
| V4.4 | Én-mester-mange-detalj, kaskadskriving fullstendighet, malbaner | V4.4 (154 linjer) |
| V4.5 | A4-utskriftsdokumenter (null tredjeparts avhengigheter) | V4.5 (127 linjer) |
| V5.0 | Én setnings systembygging: pipeline + AI-modellering + stillas grunn | — |
| V5.1 | DDL revisjonskolonner automatisk utfylling | V5.1 (100 linjer) |
| V5.2 | Pipeline seed-data initialiseringstrinn | V5.2 (107 linjer), 84ec8dd |
| V5.5 | Flermodulsprosjekt generering avslutning | af73dd3 |
| V6.0 | Mikrotjenesteinfrastruktur (Nacos / Gateway / Feign, tredje tilstand "micro") | V6.0 (111 linjer), 1deecbd |
| V6.1 | Activiti arbeidsflyt dobbelmotor | V6.1 (100 linjer), 02b671e |
| V6.2 | Artefakt leveringsflate (CLI / deploy-artefakter / konfigurasjonssikkerhet) | V6.2 (114 linjer), 02b671e |
| V6.3 | Betaling kapasitetspakke (WeChat / Alipay + Mock) | V6.3 (104 linjer), 1c4ee6a, 22eede5 |
| V6.4 | Rettigheter & sikkerhetsgrunn (token persistens / BCrypt / API-rettigheter / innloggingsbeskyttelse) | V6.4 (107 linjer), 007a202 |
| V6.5 | Mobil uni-app (H5 + WeChat Mini Program) | V6.5 (105 linjer), 779ac41 |
| V6.6 | Fil kapasitetspakke | 98f3c09 |
| V6.7 | Artefakttest fabrikkgodkjenning (test_enabled tre-tilstander + H2 skjelett) | V6.7 (134 linjer), 4e7d318 |
| V6.8 | Artefakt vedlikeholdbarhet (dryRun / beskyttet hopp over / merkevare parameterisering) | V6.8 (141 linjer), be6f858 |
| V6.9 | Plattformdokumentasjonssenter (bakre del grunn, forreste del ferdig i samme batch) | — |
| V6.10 | Plattform observerbarhet: Actuator + minnet metrisk vindu + innebygd monitorside | — |
| V7.0 | AI side & meny anbefalinger (preview / apply) | V7.0 (94 linjer), 45f2670 |
| V7.1 | Seed-data AI forbedring (AI eier semantikk, regler eier struktur) | V7.1 (42 linjer), 5ca6e72 |
| V7.2 | SQL leverte objekter skrevet til `db/` etter vellykket pipeline | eebff88 |
| **V7.3** | **Leveringspålitelighet: selvkontroll kompilerte artefakt tester / enhetlig SQL leveringsutgang / tabellstyrte AI anbefalinger** | **V7.3 (138 linjer), 696765b** |
| **V7.4** | Gjennomsnittlig levering: deployerbar fra en tom database, hvert commit garantert kjørbar | c306e29 |
| **V7.5** | **Prosjektspråkkonfigurasjon: liste over 206 språk / standardspråk / aktiverte språk, injisering av lang & dir i generert kode** | 7a9afa0 + abac56d |
| **V8.0** | **Fullside-i18n: rammeverk med 206 språk (8 profesjonelt oversatt), tosporet i18n med OFF zero-diff-kontrakt** | 9e5dd57 → 7d57aea |
| **V8.1** | **Kvalitetsherdning: offline kontrakt-harness (9 moduser, OFF zero-diff) + i18n-core enhetstester + CI-port + ærlig README-inndeling (rammeverk 206 / oversatt 8) + check-locale-keys.mjs** | 93a12d5 + e49330b |
| **V9.0** | **Genererte artefakter modernisert til Vue 3 + Element Plus + Vite; runtime-verifisert (40/40 asserts, vellykket vite build, 14 skjermbilder inkl. ar-SA RTL); 3 runtime-feil rettet** | 93a12d5 + e49330b  |
| **V9.1** | **Vedlikeholdsrefaktorering · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9** | 654b8e0 → b0d081e  |
| **V9.2** | **Testsenter & verifikasjonssenter · Test Center (7 tables: env/suite/case/dataset/run/result/schedule) + Verification Center (tc_run/tc_case_result/tc_artifact, 9 suites S1-S9, single-run 409 lock, `codegen.testcenter/v1`); 373/373 tests; 206/206 locales; output byte-identical** | V9.2 release  |
| **V9.3** | **Automatiserte tester av genererte artefakter · Generated-artifact automated testing: S10 (mvn test + Surefire), S11 (API / TestContainers), S12 (E2E / Playwright); GEN trigger + TC_GEN_ROOT; 12-step pipeline green; real artifact run PASSED 20/20, run/case/artifact persisted** | V9.3 release, **gjeldende versjon** |

---

> **V6.10 Plattform observerbarhet**: integrerer Actuator + Micrometer, prøver hvert 5. sekund til en minnet ringbuffer (30-minutter vindu), pluss en ny innebygd "System Monitor" side — bakre del `/monitor/*` aggregerings API-er + forreste del ECharts paneler. Bare `health/info/metrics` endepunkter er hvitlistet; `/actuator/health` er tokenfri mens `metrics` osv. krever auth; `env/beans/heapdump/threaddump/loggers` eksponeres aldri. Prøvevinduet er bare per prosess, mistes ved omstart, og deles ikke på tvers av instanser. Se `../codegen-server/docs/可观测性说明.md` (kinesisk) for detaljer.

## 13. Prosjektstatus

- Den nåværende planlagte versjonslinjen har nådd **V9.3** (kapasiteter fortsetter å iterere: AI-modellering, seed-data forbedring, kapasitetspakke system osv.).
- Et følgedemo-prosjekt `demo_admin` (bakre del 8140 / forreste del 9040) dekker tre / CRUD / master-detail / skrivebeskyttet / utskrift scenarier og kjører uavhengig for verifisering.
- Begge repositorier hostes på Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. Forfatter & Støtte

- **Forfatter**: en kinesisk programmerer (uavhengig utvikler)
- **E-post**: `hustxiayuhong@126.com`
- **Teknisk støtte**: vennligst skriv din e-post **på kinesisk**, inkludert problembeskrivelse, kjøremiljø (JDK / MySQL / versjon) og reproduksjonstrinn

> Jeg leser og svarer på hver e-post. Feilrapporter, funksjonsforespørsler og samarbeidshenvendelser er alle velkomne.

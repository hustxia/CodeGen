# Majiang · Kodegenerator (CodeGen) — Projektintroduktion

> Sprog: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · **Dansk** · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> En projektspændende kodegenerator til **Spring Boot + Vue**: udgående fra databasetabelstrukturer producerer den **kompileringbar, kørbar** forretningskode for front-end og back-end med menuer og rettigheder, og håndterer den fuldstændige leveringscyklus: skrivning til disk, backup, regnskab (ledger) og rulle tilbage.

GitHub: https://github.com/hustxia/CodeGen

## 1. Hvad er dette

CodeGen er en "tabelstyret" kodefaktor. Bring dine forretningstabeller ind (via databaseimport eller ved at indsætte en DDL), konfigurer felter og generationskontakter og klik på "Generer" — så producerer den backend Entity/Mapper/Service/Controller/DTO/VO, frontendens `api.js` og Vue-sider, menuer og ruter, selv A4-udskriftssider og integrationstestskeletter, og skriver alt sikkert til dit målprojekt.

Det er ikke bare "kode der spyttes ud" — det er en **leveringspipeline med kvalitetsporte og rulle-tilbage-evne**:

```
Tabelindtag (DB-import / DDL)
   → Felt- og kontaktkonfiguration (med AI-anbefalinger)
      → Et-klik-generation: selvkontrol → backup → skriv til disk → kompilering/validering → registrer menu → genbyg ruter
         → Regnskabsføring → rulle-tilbage-bar
```

---

## 2. Hvem er brugerne og hvordan bruger de det

| Bruger | Typisk behov | Stien platformen tilbyder |
| --- | --- | --- |
| Back-end-udvikler | Har et sæt tabeller (eller DDL) og vil hurtigt sætte CRUD op | Tabelkonfigurationsside: importer → konfigurer felter → generer med ét klik |
| Full-stack / selvstændig udvikler | Vil bygge et komplet system fra bunden (inkl. login, rettigheder, ordbog) | Pipelineside: byg et system med én sætning |
| Teamleder | Vil forene kodestil, kodestandarder og mappestruktur | Systemhåndtering: ét system = én output-konfiguration, gælder i batch |
| Platformadministrator | Vil ændre skabeloner uden at ændre kode | Skabelonhåndtering: rediger online, træder i kraft med det samme ved gem, med versionsrulletilbage |

## 3. Hvilke problemer det løser

| Smertepunkt | Hvad CodeGen gør |
| --- | --- |
| Gentagne CRUD-helvede — en bunke boilerplate for hver tabel | Tabelbestemte kontakter producerer komplet front- og back-end med et klik, inklusive CRUD, paginering, validering, import/eksport |
| Front/back felt- og API-forskydninger der forårsager endeløse integrationsfix | Samme tabelmetadata driver både backend-API'er og frontend-sider — nul semantisk afvigelse |
| Teamkonventioner svære at forene; alle skriver i sin egen stil | Skabeloner er konventionen: 22 FreeMarker-skabeloner centralt styret, redigerbare online med versionsrulletilbage |
| Generatorer "overskriver og mister" håndskrevet logik | Marker-blok sammenfletningsmekanisme: håndskrevet kode bevares, kun genererede regioner udskiftes |
| Genereret kode upålidelig indtil kompileringstid | Selvkontrol med in-memory-kompilering (ægte `javac`), strukturelle kontraktkontroller, diff-forhåndsvisning på disk — intet landet på disk medmindre det kompilerer |
| At opsætte et nyt system fra bunden er tidskrævende | Pipeline "byg et system med en sætning": opret DB → scaffolding → generer → pak → registrer menuer, fuldstændig automatiseret |

---

## 4. Kapacitetsoversigt

- **Tabelindtag**: DDL-parsning, direkte databaseimport, batch-indtag-guid, automatisk afledt kolonnekonfiguration (kontroller / validering / ordbøger / relationer).
- **Tabelformer**: enkelt tabel, trætabel (tree API + trægrid), master-detail (lagring i samme transaktion + detaljefaner), mange-til-mange koblingstabeller.
- **Genererede artefakter**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue-sider + A4-udskriftssider + integrationstestskeletter.
- **Forretningsforbedringer**: relationsdropdowns, ordbogs translation, datamaskering, unikhedskontroller, formularforbindelse, datarettigheder, Excel import/eksport, godkendelsesworkflow, betaling / fil / mobil kapacitetspakker.
- **Kvalitetsporte**: selvkontrol med in-memory-kompilering (ægte `javac`), strukturelle kontraktkontroller, rettighedskonsistenskontroller, diff-forhåndsvisning på disk, reel validering af frontend-artefakter.
- **Sikker landing**: skrivning til disk + `.gen-backup` batchbackups, marker-blok sammenfletning for at bevare håndskrevet kode, `gen_output` fire-tilstandsregnskab (oprettet / sammenflettet / overstået / mislykket), historisk rulletilbage.
- **Flere systemer isoleret**: hvert målprojekt er en "systemkonfiguration" (front/back stier, pakkenavne, gateway-prefix, afhængigheds-classpath, generationskontakt-standardværdier) — flere projekter betjenes parallelt.
- **AI-forbedringer**: byg et system med en sætning (AI-modellering), seed-data AI-forbedring, tabelbestemt AI-anbefalet konfiguration (diff-forhåndsvisning + markeret anvendelse).

---

## 5. Typiske scenarier

| Scenario | Beskrivelse |
| --- | --- |
| Trætabel | Hierarkisk data såsom produktkategorier — producerer automatisk tree API, trægrid og overordnet valgdropdown |
| Enkelt tabel CRUD | Kunder, produkter osv. — FK-dropdowns + ordbogs translation + import/eksport out of the box |
| Master-detail | Salgsordrer + ordrelinjer — lagring i samme transaktion, detaljeskuff med dobbelte faner (masterinfo / detaljer) |
| Skrivebeskyttet / statistik | Lagerstrømme osv. — skrivekontakter slukket, kun forespørgsel og eksport |
| Udskrivningsdokumenter | Marker "udskriv" kontakten for at producere en A4-udskriftsside og rute, med header + detaljetabel |
| Én-sætnings systembygning | Beskriv forretningsdomen — AI opretter DB & tabeller + scaffolding + fuld generering + menuregistrering i ét træk |

> Alle scenarier har kørbare demo:er (`demo_admin` projekt) med skærmbilleder — se `docs/典型场景操作指南-演示后台demo.md` (kinesisk).

---

## 6. Grundlæggende arbejdsgang

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Tabelindtag │ → │ Felt/kontaktkonf. │ → │ Et-klik generer (selvkontrol→backup→skriv→     │ → │ Regnskab · Rulletilbage │
│             │   │                   │   │ kompilering→menu→rute)                       │   │                        │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     kontroller/validering     Landet på disk først efter alle kvalitetsporte;
                    /ordbøger                  håndskrevet kode bevares
```

- **Diff-forhåndsvisning**: genererede artefakter diffes fil for fil mod nuværende diskstatus; du bekræfter før noget skrives, for at undgå utilsigtede overskrivninger.
- **Generationsregnskab**: hver fil registrerer sin handling, byteantal, MD5 og backup lokation; en hel batch kan rulles tilbage.

---

## 7. Byg et system med en sætning (Pipeline)

En høj kapacitet rettet mod "at opsætte en backend fra bunden". Efter at du har beskrevet forretningsdomen, avancerer pipelinen automatisk gennem 5 faser:

| Fase | Betydning |
| --- | --- |
| `CHECK` | Validerer målprojektkonfiguration og afhængigheds-classpath |
| `WRITE` | Opretter database & tabeller, skriver scaffolding og genererede artefakter |
| `COMPILE` | Backend `mvn package` / frontend build, selvkontrol med in-memory-kompilering |
| `MENU` | Producerer og registrerer menurettigheder (dobbelt udgang: artefakt SQL / intern direkte skrivning) |
| `ROUTE` | Genbygger frontend-rutfragmenter |

Enhver fasefejl afbryder straks med en læsbar fejl for nem lokalisering.

---

## 8. Systemarkitektur

Frontend og backend adskilt, to repositorier arbejder sammen:

| Repositorium | Rolle | Stack |
| --- | --- | --- |
| `codegen-server` | Backend: generationsmotor, skabeloner, validering, regnskab, pipeline | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Frontend: konsol (tabelkonfig / skabeloner / regnskab / systemhåndtering) | Vue 2.7 · Element UI · axios · Vue Router (history mode) |

Backend-lag:

```
controller/    API-lag (Gen* generationskæde / Sys* systemhåndtering / pipeline)
service/impl/  Kerneimplementeringer (generationskæde, skabelonmotor, sammenfletning, validering, pipelineudførelse)
util/          Skabelonmotor, modelsamling, marker-blok sammenfletning, selvkontrolkompilering
config/        Outputkonfig, selvkontrolkonfig, skabelonfrøsikkerhed
common/        Result / BaseEntity / undtagelser / revision
resources/gen-templates/   22 FreeMarker-skabeloner (backend / frontend / shared / test grupper)
```

**Grundlæggende kapaciteter som målprojektet skal levere** (generatoren producerer kun forretningskode; manglende grund mislykkes tavst):

1. MyBatis-Plus pagineringsplugin (`PaginationInnerInterceptor`)
2. Grundklasser såsom `Result` / `BaseEntity` / `PageQuery` (pakke-prefix skal matche `root-package`)
3. Frontend `utils/request.js` med `toPageData` (konverterer `Page{records,total}` til `{list,total}`)
4. Afhængigheder `springdoc-openapi-starter-webmvc-ui` (genereret kode bruger `@Operation`) og `poi-ooxml` (Excel)

---

## 9. Teknikstack i overblik

| Dimension | Valg |
| --- | --- |
| Backend-sprog / framework | Java 21 / Spring Boot 3.2.5 |
| Persistens | MyBatis-Plus 3.5.5 |
| Skabelonmotor | FreeMarker (22 frøskabeloner, online-redigering + versionsrulletilbage) |
| Sikkerhed | JWT-autentisering |
| Database | MySQL 8 |
| Frontend | Vue 2.7 + Element UI |
| Lokal AI | qwen2.5-coder:7b (modellering / anbefalet konfiguration / seed-data forbedring) |
| API-dokumentation | springdoc-openapi (Swagger UI) |

---

## 10. Hurtig start

### 1. Opret databasen (13 platformstabeller + standardkonto admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Konfigurer målprojektet

Kopier config-skabelonen og juster til dine lokale stier:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` er ekskluderet af `.gitignore` og vil ikke blive committet. Hovedindstillinger:

```yaml
gen:
  output:
    backend-root: /sti/til/dit-målprojekt      # skrivebasis for artefakter
    frontend-root: /sti/til/dit-målprojekt
    frontend-path: demo-web                          # frontend-undermappe for monorepos
    backend-path: demo-server/src/main/java/com/demo
    package-name: com.demo
    root-package: com.demo                           # prefix for grundklasseimporter i artefakter
  selfcheck:
    classpath: /sti/til/dit-målprojekt/demo-lib/*
```

Eksporter selvkontrol-afhængighedsmappen således:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Start backend

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

Tjenesten kører som standard på port **8120**; API-dokumentation på `/swagger-ui/index.html`.

### 4. Start frontend

```bash
cd ../codegen-web
npm install
npm run serve
```

Frontenden kører som standard på port **9530**, proxyer `/dev-api` til backend port 8120. Standardkonto `admin / admin123`.

### 5. Vigtige konfigurationspunkter

| Indstilling | Beskrivelse |
| --- | --- |
| `gen.output.*` | Artefakt-skrivstier, pakkenavne, modultilknytning |
| `gen.selfcheck.classpath` | Afhængigheds-classpath for selvkontrol med in-memory-kompilering, understøtter `dir/*` |
| `gen.menu.outlet` | `artifact` producerer kun menu SQL & rutfragmenter; `internal` skriver også platformens egen `sys_menu` |

---

## 11. Dokumentationskort

| Dokument | Indhold |
| --- | --- |
| `docs/代码生成器-操作手册.md` (kinesisk) | Implementering, 5-minutters start, tabelkonfig, batch-indtag, skabelonhåndtering, regnskab, API-katalog, fuld pipeline (med skærmbilleder) |
| `docs/典型场景操作指南-演示后台demo.md` (kinesisk) | 6 tabeller der dækker 5 typiske scenarier, med kørbar demo og rigtige skærmbilleder |
| `docs/项目介绍.md` | Projektintroduktionsdokument, samme kilde som denne fil |
| `docs/代码生成器-Vx.x-规划.md` | Versionsplaner og funktionsopgørelser fra V3.1 → V7.3 |
| `codegen-web/README.md` | Frontend-konsol hurtig start reference |

---

## 12. Versionshistorik

| Version | Tema / plandokument | Landningscommit |
| --- | --- | --- |
| V1.2 / V1.3 | Et-klik generation / batch / rulletilbage; diff-forhåndsvisning & struktur sync | — (kun i `src/api/gen.js` kommentarer) |
| V2.0 | Dynamiske ruter (backend-leveret menutræ) | — |
| V3.0 / V3.1 | Multi-system regnskab; multi-system loop + første automatiserede tests | V3.1 (174 linjer) |
| V4.2 | Sporbare, rulletilbagebare generationsresultater (diskregnskab) | V4.2 (292 linjer) |
| V4.3 | Skabelonregression & kvalitetsafvisninger | V4.3 (200 linjer) |
| V4.4 | En-master-mange-detalj, kaskadskriv fuldstændighed, skabelonbaner | V4.4 (154 linjer) |
| V4.5 | A4-udskrivningsdokumenter (nul tredjepartsafhængigheder) | V4.5 (127 linjer) |
| V5.0 | Én-sætnings systembygning: pipeline + AI-modellering + scaffolding grund | — |
| V5.1 | DDL revisionskolom automatisk udfyldning | V5.1 (100 linjer) |
| V5.2 | Pipeline seed-data initialiseringstrin | V5.2 (107 linjer), 84ec8dd |
| V5.5 | Multi-module projekt generation afslutning | af73dd3 |
| V6.0 | Mikrotjenesteinfrastruktur (Nacos / Gateway / Feign, tredje tilstand "micro") | V6.0 (111 linjer), 1deecbd |
| V6.1 | Activiti arbejdsgang dobbeltmotor | V6.1 (100 linjer), 02b671e |
| V6.2 | Artefaktleveringsflade (CLI / deploy-artefakter / konfigurationssikkerhed) | V6.2 (114 linjer), 02b671e |
| V6.3 | Betalingskapacitetspakke (WeChat / Alipay + Mock) | V6.3 (104 linjer), 1c4ee6a, 22eede5 |
| V6.4 | Rettigheder & sikkerhedsgrund (tokenpersistens / BCrypt / API-rettigheder / loginbeskyttelse) | V6.4 (107 linjer), 007a202 |
| V6.5 | Mobil uni-app (H5 + WeChat Mini Program) | V6.5 (105 linjer), 779ac41 |
| V6.6 | Filkapacitetspakke | 98f3c09 |
| V6.7 | Artefakttest fabriksacceptance (test_enabled tre-tilstande + H2 skelet) | V6.7 (134 linjer), 4e7d318 |
| V6.8 | Artefaktvedligeholdbarhed (dryRun / beskyttet skip / brand parametrisering) | V6.8 (141 linjer), be6f858 |
| V6.9 | Platformsdokumentationscenter (backend grund, frontend afsluttet i samme batch) | — |
| V6.10 | Platform observerbarhed: Actuator + in-memory metrisk vindue + indlejret monitorside | — |
| V7.0 | AI side & menu anbefalinger (preview / apply) | V7.0 (94 linjer), 45f2670 |
| V7.1 | Seed-data AI forbedring (AI ejer semantik, regler ejer struktur) | V7.1 (42 linjer), 5ca6e72 |
| V7.2 | SQL leverede objekter skrevet til `db/` efter vellykket pipeline | eebff88 |
| **V7.3** | **Leveringspålidelighed: selvkontrol kompilerede artefakttests / ensartet SQL leveringsudgang / tabelbestemt AI anbefalinger** | **V7.3 (138 linjer)** |
| **V7.4** | Gentagelig levering: deployerbar fra en tom database, hvert commit garanteret kørbar | **Nuværende version** |

---

> **V6.10 Platform observerbarhed**: integrerer Actuator + Micrometer, sampler hvert 5. sekund til en in-memory ringbuffer (30-minutter vindue), plus en ny indlejret "System Monitor" side — backend `/monitor/*` aggregerings-API'er + frontend ECharts paneler. Kun `health/info/metrics` endpoints er whitelisted; `/actuator/health` er tokenfri mens `metrics` osv. kræver auth; `env/beans/heapdump/threaddump/loggers` eksponeres aldrig. Samplingsvinduet er kun per proces, mistes ved genstart, og deles ikke på tværs af instanser. Se `docs/可观测性说明.md` (kinesisk) for detaljer.

## 13. Projektstatus

- Den nuværende planerede versionlinje har nået **V7.3** (kapaciteter fortsætter iterere: AI-modellering, seed-data forbedring, kapacitetspakkessystem osv.).
- Et ledsagende demo-projekt `demo_admin` (backend 8140 / frontend 9040) dækker træ / CRUD / master-detail / skrivebeskyttet / udskrivningsscenarier og kører standalone til verifikation.
- Begge repositorier hostes på Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. Forfatter & Support

- **Forfatter**: en kinesisk programmør (uafhængig udvikler)
- **E-mail**: `hustxiayuhong@126.com`
- **Teknisk support**: venligst skriv din e-mail **på kinesisk**, inklusive problembeskrivelse, runtime-miljø (JDK / MySQL / version) og reproduktionsskridt

> Jeg læser og svarer på hver e-mail. Fejlrapporter, funktionsanmodninger og samarbejdshenvendelser er alle velkomne.

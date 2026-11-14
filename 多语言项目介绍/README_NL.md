# Majiang · CodeGenerator (CodeGen) — Projectintroductie

> Talen: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · **Nederlands** · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> Een multi-project codegenerator voor **Spring Boot + Vue**: vertrekkend vanuit database-tabelstructuren produceert het **compileerbare, uitvoerbare** businesscode voor front-end en back-end met menu's en rechtenbeheer, en verzorgt de volledige leveringscyclus van schrijven naar schijf, backup, grootboek (ledger) en rollback.

GitHub: https://github.com/hustxia/CodeGen

> **huidige versie V9.2 · Testcentrum & verificatiehub · Test Center (7 tables: env/suite/case/dataset/run/result/schedule) + Verification Center (tc_run/tc_case_result/tc_artifact, 9 suites S1-S9, single-run 409 lock, `codegen.testcenter/v1`); 373/373 tests; 206/206 locales; output byte-identical**
> **V9.1 · Onderhoudbaarheidsrefactoring · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9**
> **V9.0 · Gegenereerde artefacten gemoderniseerd naar Vue 3 + Element Plus + Vite; runtime geverifieerd (40/40 asserts, geslaagde vite build, 14 screenshots incl. ar-SA RTL); 3 runtimefouten gevonden & opgelost**
> **V8.1** · Kwaliteitsverharding: offline contract-harness (9 modi, OFF zero-diff) + i18n-core unittests + CI-poort + eerlijke README-splitsing (framework 206 / vertaald 8) + check-locale-keys.mjs
> **V8.0** · Volledige pagina-i18n: framework van 206 talen (8 professioneel vertaald), tweespoors i18n met OFF zero-diff contract

## 1. Wat is dit

CodeGen is een "tabelgestuurde" codefabriek. Breng je bedrijfstabellen binnen (via database-import of door een DDL te plakken), configureer velden en generatieschakelaars en klik op "Genereer" — dan produceert het de back-end Entity/Mapper/Service/Controller/DTO/VO, de front-end `api.js` en Vue-pagina's, menu's en routes, zelfs A4-afdrukpagina's en integratietest-skeletten, en schrijft alles veilig weg naar je doelproject.

Het "spuugt" niet alleen code uit, maar is een **leveringspijplijn met kwaliteitspoorten en rollback-mogelijkheid**:

```
Tabellenopname (DB-import / DDL)
   → Veld- en schakelaarconfiguratie (met AI-aanbevelingen)
      → Eénklik-generatie: zelftest → backup → naar schijf schrijven → compileren/valideren → menu registreren → routes herbouwen
         → Grootboekboeking → rollback-mogelijk
```

---

## 2. Wie zijn de gebruikers en hoe gebruiken zij het

| Gebruiker | Typisch verzoek | Pad dat het platform biedt |
| --- | --- | --- |
| Backend-ontwikkelaar | Heeft een set tabellen (of DDL) en wil snel CRUD opzetten | Tabellconfiguratiepagina: importeren → velden configureren → met één klik genereren |
| Full-stack / zelfstandig ontwikkelaar | Wil een compleet systeem vanaf nul opbouwen (incl. login, rechten, woordenboek) | Pijplijnpagina: bouw een systeem met één zin |
| Teamleider | Wil codeerstijl, coderingsnormen en directorystructuur uniformeren | Systeembeheer: één systeem = één uitvoerconfiguratie, in batch van kracht |
| Platformbeheerder | Wil sjablonen wijzigen zonder code aan te passen | Templatebeheer: online bewerken, na opslaan direct van kracht, met versierollback |

## 3. Welke problemen het oplost

| Pijnpunt | Wat CodeGen doet |
| --- | --- |
| Herhalende CRUD-hel — een hoop boilerplate voor elke tabel | Tabelgebonden schakelaars produceren met één klik een complete front- en back-end, inclusief CRUD, paginering, validatie, import/export |
| Front/back veld- en API-mismatches die eindeloze integratiefixes veroorzaken | Dezelfde tabelmetadata stuurt zowel back-end API's als front-end pagina's aan — nul semantische afwijking |
| Teamconventies moeilijk te uniformeren; iedereen schrijft in zijn eigen stijl | Templates zijn de conventie: 22 FreeMarker-templates centraal beheerd, online bewerkbaar met versierollback |
| Generatoren "overschrijven en verliezen" handgeschreven logica | Marker-block merge-mechanisme: handgeschreven code blijft behouden, alleen gegenereerde regio's worden vervangen |
| Gegenereerde code onbetrouwbaar tot compileertijd | In-memory compileer-zelftest (echte `javac`), structurele contractcontroles, diff-voorbeeld op schijf — niets komt op schijf terecht tenzij het compileert |
| Een nieuw systeem vanaf zero opzetten is tijdrovend | Pijplijn "een systeem met één zin bouwen": DB aanmaken → scaffold → genereren → verpakken → menu's registreren, volledig geautomatiseerd |

---

## 4. Capaciteitenoverzicht

- **Tabellenopname**: DDL-parsing, directe database-import, batch-opname-wizard, automatisch afgeleide kolomconfiguratie (controls / validatie / woordenboeken / relaties).
- **Tabellenvormen**: enkele tabel, boomtabel (tree API + boomgrid), master-detail (opslag in dezelfde transactie + detail-tabs), many-to-many koppeltabellen.
- **Gegenereerde artefacten**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue-pagina's + A4-afdrukpagina's + integratietest-skeletten.
- **Bedrijfsverbeteringen**: relatie-dropdowns, woordenboekvertaling, datamaskering, uniciteitscontroles, formulierkoppeling, gegevensrechten, Excel import/export, goedkeuringsworkflow, betaling / bestand / mobiele capaciteitspakketten.
- **Kwaliteitspoorten**: in-memory compileer-zelftest (echte `javac`), structurele contractcontroles, rechtenconsistentiecontroles, diff-voorbeeld op schijf, echte validatie van front-end artefacten.
- **Veilige landing**: schrijven naar schijf + `.gen-backup` batchbackups, marker-block merge om handgeschreven code te behouden, `gen_output` vierstatengrootboek (gemaakt / samengevoegd / overgeslagen / mislukt), historische rollback.
- **Multi-systeemisolatie**: elk doelproject is een "systeemconfiguratie" (front/back paden, pakketnamen, gateway-prefix, dependency classpath, generatieschakelaar-standaardwaarden) — meerdere projecten parallel bediend.
- **AI-verbeteringen**: een systeem met één zin bouwen (AI-modellering), seed-data AI-verbetering, tabelgebonden AI-aanbevolen configuratie (diff-voorbeeld + aangevinkte toepassing).

---

## 5. Typische scenario's

| Scenario | Beschrijving |
| --- | --- |
| Boomtabel | Hiërarchische gegevens zoals productcategorieën — produceert automatisch tree API, boomgrid en bovenliggende selectie-dropdown |
| Enkele-tabel CRUD | Klanten, producten, enz. — FK-dropdowns + woordenboekvertaling + import/export out of the box |
| Master-detail | Verkooporders + orderregels — opslag in dezelfde transactie, detaillade met dubbele tabs (masterinfo / details) |
| Alleen-lezen / statistieken | Voorraadstromen, enz. — schrijfschakelaars uit, alleen query en export |
| Afdrukdocumenten | Vink de "afdruk" schakelaar aan om een A4-afdrukpagina en route te produceren, met kop + detailtabel |
| Eén-zin systeembouw | Beschrijf het bedrijfsdomein — AI maakt DB & tabellen + scaffold + volledige generatie + menuregistratie in één keer |

> Alle scenario's hebben uitvoerbare demo's (`demo_admin` project) met screenshots — zie `../codegen-server/docs/典型场景操作指南-演示后台demo.md` (Chinees).

---

## 6. Kernworkflow

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Tabellenopname │ → │ Veld/schakelaarconf. │ → │ Eénklik genereer (zelftest→backup→schrijf→     │ → │ Grootboek · Rollback │
│              │   │                    │   │ compileer→menu→route)                              │   │                   │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     controls/validatie     Komt alleen op schijf na alle kwaliteitspoorten;
                    /woordenboeken         handgeschreven code blijft behouden
```

- **Diff-voorbeeld**: gegenereerde artefacten worden bestand-voor-bestand gediffed tegen de huidige schijfstatus; je bevestigt voordat iets wordt geschreven, om accidentele overschrijving te vermijden.
- **Generatiegrootboek**: elk bestand registreert zijn actie, byteaantal, MD5 en backuplocatie; een volledige batch kan worden teruggedraaid.

---

## 7. Een systeem met één zin bouwen (Pijplijn)

Een capaciteit gericht op "een backend vanaf zero opzetten". Nadat je het bedrijfsdomein beschrijft, vordert de pijplijn automatisch door 5 fasen:

| Fase | Betekenis |
| --- | --- |
| `CHECK` | Valideert doelprojectconfiguratie en dependency classpath |
| `WRITE` | Maakt database & tabellen, schrijft scaffold en gegenereerde artefacten |
| `COMPILE` | Backend `mvn package` / front-end build, in-memory compileer-zelftest |
| `MENU` | Produceert en registreert menurechten (dubbele uitlaat: artefact SQL / interne directe schrijf) |
| `ROUTE` | Herboudt front-end routefragmenten |

Elke fasefalen breekt onmiddellijk af met een leesbare fout voor eenvoudige lokalisering.

---

## 8. Systeemarchitectuur

Front-end en back-end gescheiden, twee repositories werken samen:

| Repository | Rol | Stack |
| --- | --- | --- |
| `codegen-server` | Backend: generatie-engine, templates, validatie, grootboek, pijplijn | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Frontend: console (tabelconfig / templates / grootboek / systeembeheer) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (history mode) |

Backend-lagen:

```
controller/    API-laag (Gen* generatieketen / Sys* systeembeheer / pijplijn)
service/impl/  Kernimplementaties (generatieketen, template-engine, merge, validatie, pijplijnexecutie)
util/          Template-engine, modelassemblage, marker-block merge, zelftest-compilatie
config/        Uitvoerconfig, zelftestconfig, template-zaaier
common/        Result / BaseEntity / uitzonderingen / audit
resources/gen-templates/   22 FreeMarker-templates (backend / frontend / shared / test groepen)
```

**Basciscapaciteiten die het doelproject moet leveren** (de generator produceert alleen bedrijfscode; een ontbrekende basis faalt stilzwijgend):

1. MyBatis-Plus paginering-plugin (`PaginationInnerInterceptor`)
2. Basisklassen zoals `Result` / `BaseEntity` / `PageQuery` (pakketprefix moet overeenkomen met `root-package`)
3. Frontend `utils/request.js` met `toPageData` (converteert `Page{records,total}` naar `{list,total}`)
4. Afhankelijkheden `springdoc-openapi-starter-webmvc-ui` (gegenereerde code gebruikt `@Operation`) en `poi-ooxml` (Excel)

---

## 9. Technisch overzicht

| Dimensie | Keuze |
| --- | --- |
| Backend-taal / framework | Java 21 / Spring Boot 3.2.5 |
| Persistentie | MyBatis-Plus 3.5.5 |
| Template-engine | FreeMarker (22 zaaitemplates, online bewerking + versierollback) |
| Beveiliging | JWT-authenticatie |
| Database | MySQL 8 |
| Frontend (generated projects) | Vue 3 + Element Plus + Vite |
| Frontend (platform console) | Vue 2.7 + Element UI |
| Lokale AI | qwen2.5-coder:7b (modellering / aanbevolen configuratie / seed-data verbetering) |
| API-docs | springdoc-openapi (Swagger UI) |

---

## 10. Snelle start

### 1. Maak de database aan (13 platformtabellen + standaardaccount admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Configureer het doelproject

Kopieer de config-template en pas aan naar je lokale paden:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` is uitgesloten door `.gitignore` en zal niet worden gecommit. Belangrijke instellingen:

```yaml
gen:
  output:
    backend-root: /pad/naar/je-doelproject      # schrijfbasis voor artefacten
    frontend-root: /pad/naar/je-doelproject
    frontend-path: demo-web                          # front-end subdirectory voor monorepo's
    backend-path: demo-server/src/main/java/com/demo
    package-name: com.demo
    root-package: com.demo                           # prefix voor basisklasse-imports in artefacten
  selfcheck:
    classpath: /pad/naar/je-doelproject/demo-lib/*
```

Exporteer de zelftest dependency-directory zo:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Start de backend

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

De dienst draait standaard op poort **8120**; API-docs op `/swagger-ui/index.html`.

### 4. Start de frontend

```bash
cd ../codegen-web
npm install
npm run serve
```

De frontend draait standaard op poort **9530**, proxyt `/dev-api` naar backend poort 8120. Standaardaccount `admin / admin123`.

### 5. Belangrijke configuratie-items

| Instelling | Beschrijving |
| --- | --- |
| `gen.output.*` | Artefact-schrijfpaden, pakketnamen, moduletoewijzing |
| `gen.selfcheck.classpath` | Dependency classpath voor in-memory compileer-zelftest, ondersteunt `dir/*` |
| `gen.menu.outlet` | `artifact` produceert alleen menu SQL & routefragmenten; `internal` schrijft ook de eigen `sys_menu` van het platform |

---

## 11. Documentatiekaart

| Document | Inhoud |
| --- | --- |
| `../codegen-server/docs/代码生成器-操作手册.md` (Chinees) | Inzet, 5-minuten start, tabelconfig, batch-opname, templatebeheer, grootboek, API-catalogus, volledige pijplijn (met screenshots) |
| `../codegen-server/docs/典型场景操作指南-演示后台demo.md` (Chinees) | 6 tabellen die 5 typische scenario's dekken, met uitvoerbare demo en echte screenshots |
| `项目介绍.md` | Projectintroductiedocument, dezelfde bron als dit bestand |
| `../codegen-server/docs/代码生成器-Vx.x-规划.md` | Versieplannen en functie-inventarissen van V3.1 → V9.2 |
| `../codegen-web/README.md` | Frontend console snelle start verwijzing |

---

## 12. Versiegeschiedenis

| Versie | Thema / plandocument | Landingscommit |
| --- | --- | --- |
| V1.2 / V1.3 | Eénklik-generatie / batch / rollback; diff-voorbeeld & structuursynchronisatie | — (alleen in `src/api/gen.js` opmerkingen) |
| V2.0 | Dynamische routes (backend-afgeleverde menuboom) | — |
| V3.0 / V3.1 | Multi-systeem grootboek; multi-systeem lus + eerste geautomatiseerde tests | V3.1 (174 regels) |
| V4.2 | Traceerbare, terugdraaibare generatieresultaten (schijfgrootboek) | V4.2 (292 regels) |
| V4.3 | Template-regressie & kwaliteitsguardrails | V4.3 (200 regels) |
| V4.4 | Een-meester-veel-detail, cascade-schrijf volledigheid, template-rijen | V4.4 (154 regels) |
| V4.5 | A4-afdrukdocumenten (nul externe afhankelijkheden) | V4.5 (127 regels) |
| V5.0 | Eén-zin systeembouw: pijplijn + AI-modellering + scaffoldbasis | — |
| V5.1 | DDL-auditkolommen automatische aanvulling | V5.1 (100 regels) |
| V5.2 | Pijplijn seed-data initialisatiestap | V5.2 (107 regels), 84ec8dd |
| V5.5 | Multi-module project generatie afsluiting | af73dd3 |
| V6.0 | Microservice-infrastructuur (Nacos / Gateway / Feign, derde modus "micro") | V6.0 (111 regels), 1deecbd |
| V6.1 | Activiti workflow dubbele motor | V6.1 (100 regels), 02b671e |
| V6.2 | Artefactleveringsvlak (CLI / deploy-artefacten / configuratiebeveiliging) | V6.2 (114 regels), 02b671e |
| V6.3 | Betaling capaciteitspakket (WeChat / Alipay + Mock) | V6.3 (104 regels), 1c4ee6a, 22eede5 |
| V6.4 | Rechten & beveiligingsbasis (tokenpersistentie / BCrypt / API-rechten / loginbescherming) | V6.4 (107 regels), 007a202 |
| V6.5 | Mobiel uni-app (H5 + WeChat Mini Program) | V6.5 (105 regels), 779ac41 |
| V6.6 | Bestand capaciteitspakket | 98f3c09 |
| V6.7 | Artefacttest fabrieksacceptatie (test_enabled tri-state + H2-skeleton) | V6.7 (134 regels), 4e7d318 |
| V6.8 | Artefactonderhoudbaarheid (dryRun / beschermde skip / merkparameterisatie) | V6.8 (141 regels), be6f858 |
| V6.9 | Platformdocumentatiecentrum (backendbasis, frontend afgerond in dezelfde batch) | — |
| V6.10 | Platformobserveerbaarheid: Actuator + in-memory metrisch venster + ingebed monitorpagina | — |
| V7.0 | AI pagina & menu-aanbevelingen (preview / apply) | V7.0 (94 regels), 45f2670 |
| V7.1 | Seed-data AI-verbetering (AI bezit semantiek, regels bezitten structuur) | V7.1 (42 regels), 5ca6e72 |
| V7.2 | SQL-leverbare objecten geschreven naar `db/` na succesvolle pijplijn | eebff88 |
| **V7.3** | **Leveringbetrouwbaarheid: zelftest gecompileerde artefacttests / uniforme SQL-leveringsuitlaat / tabelgebonden AI-aanbevelingen** | **V7.3 (138 regels), 696765b** |
| **V7.4** | Herhaalbare levering: deployeerbaar vanuit een lege database, elke commit gegarandeerd uitvoerbaar | c306e29 |
| **V7.5** | **Projecttaalconfiguratie: lijst van 206 talen / standaardtaal / ingeschakelde talen, injectie van lang & dir in gegenereerde code** | 7a9afa0 + abac56d |
| **V8.0** | **Volledige pagina-i18n: framework van 206 talen (8 professioneel vertaald), tweespoors i18n met OFF zero-diff contract** | 9e5dd57 → 7d57aea |
| **V8.1** | **Kwaliteitsverharding: offline contract-harness (9 modi, OFF zero-diff) + i18n-core unittests + CI-poort + eerlijke README-splitsing (framework 206 / vertaald 8) + check-locale-keys.mjs** | 93a12d5 + e49330b |
| **V9.0** | **Gegenereerde artefacten gemoderniseerd naar Vue 3 + Element Plus + Vite; runtime geverifieerd (40/40 asserts, geslaagde vite build, 14 screenshots incl. ar-SA RTL); 3 runtimefouten gevonden & opgelost** | 93a12d5 + e49330b  |
| **V9.1** | **Onderhoudbaarheidsrefactoring · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9** | 654b8e0 → b0d081e  |
| **V9.2** | **Testcentrum & verificatiehub · Test Center (7 tables: env/suite/case/dataset/run/result/schedule) + Verification Center (tc_run/tc_case_result/tc_artifact, 9 suites S1-S9, single-run 409 lock, `codegen.testcenter/v1`); 373/373 tests; 206/206 locales; output byte-identical** | V9.2 release, **huidige versie** |

---

> **V6.10 Platformobserveerbaarheid**: integreert Actuator + Micrometer, bemonstert elke 5 seconden naar een in-memory ringbuffer (30-minuten venster), plus een nieuwe ingebedde "Systeemmonitor" pagina — backend `/monitor/*` aggregatie API's + frontend ECharts panelen. Alleen `health/info/metrics` endpoints zijn whitelisted; `/actuator/health` is token-vrij terwijl `metrics` enz. auth vereisen; `env/beans/heapdump/threaddump/loggers` worden nooit blootgesteld. Het bemonsteringsvenster is alleen per proces, verloren bij herstart, en niet gedeeld over instanties. Zie `../codegen-server/docs/可观测性说明.md` (Chinees) voor details.

## 13. Projectstatus

- De huidige geplande versielijn heeft **V9.2** bereikt (capaciteiten blijven itereren: AI-modellering, seed-data verbetering, capaciteitspakket-systeem, enz.).
- Een begeleidend demo-project `demo_admin` (backend 8140 / frontend 9040) dekt boom / CRUD / master-detail / alleen-lezen / afdruk scenario's en draait standalone voor verificatie.
- Beide repositories worden gehost op Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. Auteur & Ondersteuning

- **Auteur**: een Chinese programmeur (onafhankelijke ontwikkelaar)
- **E-mail**: `hustxiayuhong@126.com`
- **Technische ondersteuning**: schrijf je e-mail **in het Chinees**, inclusief probleemomschrijving, runtime-omgeving (JDK / MySQL / versie) en reproduciestappen

> Ik lees en beantwoord elke e-mail. Bugrapporten, functieverzoeken en samenwerkingsvragen zijn alle welkom.

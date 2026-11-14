# Majiang · Generátor kódu (CodeGen) — Představení projektu

> Jazyky: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · **Čeština** · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> Víceprojektový generátor kódu pro **Spring Boot + Vue**: vycházejíc ze struktur databázových tabuel produkuje **kompilovatelný, spustitelný** business kód pro front-end a back-end s menu a oprávněními a zabývá se celým doručovacím cyklem zápisem na disk, zálohováním, účetnictvím (ledger) a vrácením zpět.

GitHub: https://github.com/hustxia/CodeGen

> **aktuální verze V9.2 · Testovací centrum & ověřovací centrum · Test Center (7 tables: env/suite/case/dataset/run/result/schedule) + Verification Center (tc_run/tc_case_result/tc_artifact, 9 suites S1-S9, single-run 409 lock, `codegen.testcenter/v1`); 373/373 tests; 206/206 locales; output byte-identical**
> **V9.1 · Refaktoring pro udržovatelnost · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9**
> **V9.0 · Generované artefakty modernizovány na Vue 3 + Element Plus + Vite; ověřeno za běhu (40/40 assertion, úspěšný vite build, 14 snímků vč. ar-SA RTL); opraveny 3 běhové defekty**
> **V8.1** · Posílení kvality: offline kontraktní harness (9 režimů, nulový rozdíl OFF) + unit testy i18n-core + CI brána + čestné rozdělení README (rámec 206 / přeloženo 8) + check-locale-keys.mjs
> **V8.0** · i18n celé stránky: rámec 206 jazyků (8 profesionálně přeloženo), dvoustopá i18n s kontraktem nulového rozdílu při OFF

## 1. Co to je

CodeGen je "tabulkami řízená" kódová továrna. Přiveďte své obchodní tabulky (importem z databáze nebo vložením DDL), nakonfigurujte pole a přepínače generování a klikněte na "Generovat" — pak vygeneruje backendové Entity/Mapper/Service/Controller/DTO/VO, frontendové `api.js` a Vue stránky, menu a routy, dokonce i A4 tiskové stránky a skeleton integračních testů a bezpečně je zapíše do vašeho cílového projektu.

Není to jen "vypukávání kódu" — je to **doručovací pipeline s kontrolami kvality a schopností vrácení zpět**:

```
Převod tabulek (DB import / DDL)
   → Konfigurace polí a přepínačů (s AI doporučeními)
      → Generování jedním kliknutím: autokontrola → záloha → zápis na disk → kompilace/validace → registrace menu → přebudování rout
         → Zaúčtování do hlavní knihy → možné vrácení zpět
```

---

## 2. Kdo jsou uživatelé a jak je používají

| Použivatel | Typická potřeba | Cesta, kterou platforma nabízí |
| --- | --- | --- |
| Backendový vývojář | Má sadu tabulek (nebo DDL) a chce rychle rozjet CRUD | Stránka konfigurace tabulek: import → konfigurace polí → generování jedním kliknutím |
| Full-stack / nezávislý vývojář | Chce od nuly postavit kompletní systém (včetně přihlášení, oprávnění, slovníku) | Stránka Pipeline: postavte systém jednou větou |
| Vedoucí týmu | Chce sjednotit kódový styl, konvence kódu a adresářovou strukturu | Správa systému: jeden systém = jedna výstupní konfigurace, platí dávkově |
| Správce platformy | Chce měnit šablony, aniž by měnil kód | Správa šablon: online úprava, uložení okamžitě platí, podporuje historické vrácení zpět |



## 3. Jaké problémy řeší

| Bolestný bod | Co dělá CodeGen |
| --- | --- |
| Opakované CRUD peklo — hromada boilerplate pro každou tabulku | Přepínače na úrovni tabulky produkují kompletní front-end a back-end jedním kliknutím, včetně CRUD, stránkování, validace, importu/exportu |
| Neshody polí a API mezi front-endem a back-endem způsobující nekonečné integrační opravy | Stejná metadata tabulek řídí jak backendová API, tak frontendové stránky — nulová sémantická odchylka |
| Týmové konvence těžko sjednotitelné; každý píše svým stylem | Šablony jsou konvencí: 22 FreeMarker šablon centrálně spravovaných, editovatelné online s vrácením verzí |
| Generátory "přepíší a ztratí" ručně psanou logiku | Mechanismus slučování bloků markerů: ručně psaný kód je zachován, pouze generované oblasti jsou nahrazeny |
| Generovaný kód nedůvěryhodný až do doby kompilace | Autokontrola kompilací v paměti (skutečný `javac`), kontroly strukturálních smluv, náhled diffů na disk — nic se neuloží na disk, pokud to nekompiluje |
| Sestavení nového systému od nuly je časově náročné | Pipeline "sestav systém jednou větou": vytvoř DB → skeleton → generuj → zabal → registruj menu, plně automatizované |

---

## 4. Přehled kapacit

- **Převod tabulek**: parsování DDL, přímý import z databáze, dávkový průvodce převodem, automaticky odvozená konfigurace sloupců (ovládací prvky / validace / slovníky / relace).
- **Tvary tabulek**: jednoduchá tabulka, stromová tabulka (tree API + stromový grid), master-detail (uložení ve stejné transakci + záložky detailů), spojovací tabulky many-to-many.
- **Generované artefakty**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue stránky + A4 tiskové stránky + skeleton integračních testů.
- **Obchodní vylepšení**: rozbalovací nabídky relací, překlad přes slovník, maskování dat, kontroly jedinečnosti, propojení formulářů, datová oprávnění, Excel import/export, schvalovací workflow, balíčky schopností plateb / souborů / mobilních zařízení.
- **Kontroly kvality**: autokontrola kompilací v paměti (skutečný `javac`), kontroly strukturálních smluv, kontroly konzistence oprávnění, náhled diffů na disk, skutečná validace frontendových artefaktů.
- **Bezpečné umístění**: zápis na disk + dávkové zálohy `.gen-backup`, slučování bloků markerů pro zachování ručně psaného kódu, čtyřstavová hlavní kniha `gen_output` (vytvořeno / sloučeno / přeskočeno / selhalo), historické vrácení zpět.
- **Izolace více systémů**: každý cílový projekt je "konfigurace systému" (cesty front/back, názvy balíčků, gateway předpona, classpath závislostí, výchozí hodnoty přepínačů generování) — více projektů obsluženo paralelně.
- **AI vylepšení**: sestav systém jednou větou (AI modelování), AI vylepšení seed dat, tabulkami řízená AI doporučená konfigurace (náhled diffů + zaškrtnuté použití).

---

## 5. Typické scénáře

| Scénář | Popis |
| --- | --- |
| Stromová tabulka | Hierarchická data jako kategorie produktů — automaticky produkuje tree API, stromový grid a rozbalovací nabídku nadřazené volby |
| CRUD jednoduché tabulky | Zákazníci, produkty atd. — FK rozbalovací nabídky + překlad přes slovník + import/export rovnou připraveno |
| Master-detail | Prodejní objednávky + řádky objednávek — uložení ve stejné transakci, zásuvka detailů se dvěma záložkami (hlavní info / detaily) |
| Jen pro čtení / statistiky | Toky zásob atd. — přepínače zápisu vypnuty, pouze dotaz a export |
| Tiskové dokumenty | Zaškrtněte přepínač "tisk" pro produkci A4 tiskové stránky a routy s hlavičkou + tabulkou detailů |
| Sestavení systému jednou větou | Popište obchodní doménu — AI vytvoří DB a tabulky + skeleton + kompletní generování + registraci menu najednou |

> Všechny scénáře mají spustitelné demo (`demo_admin` projekt) s screenshoty — viz `../codegen-server/docs/典型场景操作指南-演示后台demo.md` (čínština).

---

## 6. Základní pracovní tok

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Převod tabulek │ → │ Konf. polí/přepínačů │ → │ Generování jedním klikem (autokontrola→záloha→zápis→│ → │ Hlavní kniha · Vrácení zpět │
│              │   │                    │   │ kompilace→menu→routy)                             │   │                            │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     ovládací prvky/validace   Na disk se uloží pouze po všech kontrolách kvality;
                    /slovníky                ručně psaný kód je zachován
```

- **Náhled diffů**: generované artefakty jsou diffovány soubor po souboru oproti aktuálnímu stavu na disku; potvrdíte, než se něco zapíše, aby se zabránilo náhodnému přepisování.
- **Hlavní kniha generování**: každý soubor zaznamenává svou akci, počet bajtů, MD5 a umístění zálohy; celá dávka může být vrácena zpět.

---

## 7. Sestav systém jednou větou (Pipeline)

Vysokoúrovňová kapacita zaměřená na "sestavení backendu od nuly". Poté, co popíšete obchodní doménu, pipeline automaticky postupuje 5 fázemi:

| Fáze | Význam |
| --- | --- |
| `CHECK` | Ověřuje konfiguraci cílového projektu a classpath závislostí |
| `WRITE` | Vytváří databázi a tabulky, zapisuje skeleton a generované artefakty |
| `COMPILE` | Backend `mvn package` / frontend build, autokontrola kompilací v paměti |
| `MENU` | Produkuje a registruje oprávnění menu (dva výstupy: artefakt SQL / interní přímý zápis) |
| `ROUTE` | Přebudovává fragmenty frontendových rout |

Selhání kterékoli fáze okamžitě přeruší s čitelnou chybou pro snadnou lokalizaci.

---

## 8. Architektura systému

Front-end a back-end oddělené, dvě repozitáře spolupracují:

| Repozitář | Role | Stack |
| --- | --- | --- |
| `codegen-server` | Backend: generační engine, šablony, validace, hlavní kniha, pipeline | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Frontend: konzole (konf. tabulek / šablony / hlavní kniha / správa systému) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (history mode) |

Backendové vrstvy:

```
controller/    API vrstva (Gen* generační řetězec / Sys* správa systému / pipeline)
service/impl/  Implementace jádra (generační řetězec, šablonový engine, slučování, validace, provádění pipeline)
util/          Šablonový engine, sestavení modelů, slučování bloků markerů, autokontrola kompilace
config/        Konfigurace výstupu, konfigurace autokontroly, osivo šablon
common/        Result / BaseEntity / výjimky / audit
resources/gen-templates/   22 FreeMarker šablon (backend / frontend / shared / test skupiny)
```

**Základní kapacity, které cílový projekt musí poskytovat** (generátor produkuje pouze business kód; chybějící základ selže tiše):

1. MyBatis-Plus stránkovací plugin (`PaginationInnerInterceptor`)
2. Základní třídy jako `Result` / `BaseEntity` / `PageQuery` (předpona balíčku musí odpovídat `root-package`)
3. Frontend `utils/request.js` s `toPageData` (převádí `Page{records,total}` na `{list,total}`)
4. Závislosti `springdoc-openapi-starter-webmvc-ui` (generovaný kód používá `@Operation`) a `poi-ooxml` (Excel)

---

## 9. Přehled technologického stacku

| Dimenz | Volba |
| --- | --- |
| Backend jazyk / rámec | Java 21 / Spring Boot 3.2.5 |
| Perzistence | MyBatis-Plus 3.5.5 |
| Šablonový engine | FreeMarker (22 osivových šablon, online úprava + vrácení verzí) |
| Zabezpečení | Ověřování JWT |
| Databáze | MySQL 8 |
| Frontend (generated projects) | Vue 3 + Element Plus + Vite |
| Frontend (platform console) | Vue 2.7 + Element UI |
| Lokální AI | qwen2.5-coder:7b (modelování / doporučená konfigurace / vylepšení seed dat) |
| API dokumentace | springdoc-openapi (Swagger UI) |

---

## 10. Rychlý start

### 1. Vytvořte databázi (13 platformních tabulek + výchozí účet admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Nakonfigurujte cílový projekt

Zkopírujte šablonu konfigurace a upravte ji podle svých místních cest:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` je vyloučen z `.gitignore` a nebude commitován. Hlavní nastavení:

```yaml
gen:
  output:
    backend-root: /cesta/k/vašemu-cílovému-projektu      # základ zápisu artefaktů
    frontend-root: /cesta/k/vašemu-cílovému-projektu
    frontend-path: demo-web                          # frontend podadresář pro monorepo
    backend-path: demo-server/src/main/java/com/demo
    package-name: com.demo
    root-package: com.demo                           # předpona pro import základních tříd v artefaktech
  selfcheck:
    classpath: /cesta/k/vašemu-cílovému-projektu/demo-lib/*
```

Exportujte adresář závislostí pro autokontrolu takto:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Spusťte backend

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

Služba běží výchozí port **8120**; API dokumentace na `/swagger-ui/index.html`.

### 4. Spusťte frontend

```bash
cd ../codegen-web
npm install
npm run serve
```

Frontend běží výchozí port **9530**, proxy `/dev-api` na backend port 8120. Výchozí účet `admin / admin123`.

### 5. Důležité konfigurační položky

| Nastavení | Popis |
| --- | --- |
| `gen.output.*` | Cesty zápisu artefaktů, názvy balíčků, mapování modulů |
| `gen.selfcheck.classpath` | Classpath závislostí pro autokontrolu kompilací v paměti, podporuje `dir/*` |
| `gen.menu.outlet` | `artifact` produkuje pouze menu SQL a fragmenty rout; `internal` také zapisuje vlastní `sys_menu` platformy |

---

## 11. Mapa dokumentace

| Dokument | Obsah |
| --- | --- |
| `../codegen-server/docs/代码生成器-操作手册.md` (čínština) | Nasazení, 5minutový start, konfigurace tabulek, dávkový převod, správa šablon, hlavní kniha, API katalog, celá pipeline (se screenshoty) |
| `../codegen-server/docs/典型场景操作指南-演示后台demo.md` (čínština) | 6 tabulek pokrývajících 5 typických scénářů, se spustitelným demem a skutečnými screenshoty |
| `项目介绍.md` | Dokument s představením projektu, stejný zdroj jako tento soubor |
| `../codegen-server/docs/代码生成器-Vx.x-规划.md` | Verzní plány a inventáře funkcí z V3.1 → V9.2 |
| `../codegen-web/README.md` | Rychlá referenční příručka pro frontend konzoli |

---

## 12. Historie verzí

| Verze | Téma / plánový dokument | Commit nasazení |
| --- | --- | --- |
| V1.2 / V1.3 | Generování jedním kliknutím / dávkové / vrácení zpět; náhled diffů & synchronizace struktury | — (pouze v komentářích `src/api/gen.js`) |
| V2.0 | Dynamické routy (backend dodaný strom menu) | — |
| V3.0 / V3.1 | Hlavní kniha více systémů; smyčka více systémů + první automatizované testy | V3.1 (174 řádků) |
| V4.2 | Sledovatelné, vrátitelné výsledky generování (disková hlavní kniha) | V4.2 (292 řádků) |
| V4.3 | Regrese šablon & kvalitní zábrany | V4.3 (200 řádků) |
| V4.4 | Jeden-mistr-mnoho-detailů, úplnost kaskádového zápisu, pruhy šablon | V4.4 (154 řádků) |
| V4.5 | A4 tiskové dokumenty (nulové závislosti třetích stran) | V4.5 (127 řádků) |
| V5.0 | Sestavení systému jednou větou: pipeline + AI modelování + základ skeletu | — |
| V5.1 | Automatické doplnění sloupců auditu DDL | V5.1 (100 řádků) |
| V5.2 | Krok inicializace seed dat v pipeline | V5.2 (107 řádků), 84ec8dd |
| V5.5 | Dokončení generování multimodulového projektu | af73dd3 |
| V6.0 | Mikroservisní infrastruktura (Nacos / Gateway / Feign, třetí stav "micro") | V6.0 (111 řádků), 1deecbd |
| V6.1 | Dvojitý engine workflow Activiti | V6.1 (100 řádků), 02b671e |
| V6.2 | Plocha doručení artefaktů (CLI / deploy artefakty / zabezpečení konfigurace) | V6.2 (114 řádků), 02b671e |
| V6.3 | Balíček schopností plateb (WeChat / Alipay + Mock) | V6.3 (104 řádků), 1c4ee6a, 22eede5 |
| V6.4 | Základ oprávnění a zabezpečení (perzistence tokenů / BCrypt / API oprávnění / ochrana přihlášení) | V6.4 (107 řádků), 007a202 |
| V6.5 | Mobilní uni-app (H5 + WeChat Mini Program) | V6.5 (105 řádků), 779ac41 |
| V6.6 | Balíček schopností souborů | 98f3c09 |
| V6.7 | Přijetí testů artefaktů ve výrobě (třístavý `test_enabled` + skeleton H2) | V6.7 (134 řádků), 4e7d318 |
| V6.8 | Udržovatelnost artefaktů (dryRun / chráněné přeskočení / parametrizace značky) | V6.8 (141 řádků), be6f858 |
| V6.9 | Platformní centrum dokumentace (backend základ, frontend dokončen ve stejné dávce) | — |
| V6.10 | Platformní pozorovatelnost: Actuator + metrické okno v paměti + vestavěná stránka monitoru | — |
| V7.0 | AI doporučení stránek a menu (preview / apply) | V7.0 (94 řádků), 45f2670 |
| V7.1 | AI vylepšení seed dat (AI vlastní sémantiku, pravidla vlastní strukturu) | V7.1 (42 řádků), 5ca6e72 |
| V7.2 | SQL doručené objekty zapsané do `db/` po úspěšné pipeline | eebff88 |
| **V7.3** | **Důvěryhodnost doručení: autokontrola kompilované artefakt testy / jednotný SQL výstup doručení / tabulkami řízená AI doporučení** | **V7.3 (138 řádků), 696765b** |
| **V7.4** | Opakovatelné doručení: nasaditelné z prázdné databáze, každý commit zaručeně spustitelný | c306e29 |
| **V7.5** | **Jazyková konfigurace projektu: seznam 206 jazyků / výchozí jazyk / povolené jazyky, vkládání lang a dir do generovaného kódu** | 7a9afa0 + abac56d |
| **V8.0** | **i18n celé stránky: rámec 206 jazyků (8 profesionálně přeloženo), dvoustopá i18n s kontraktem nulového rozdílu při OFF** | 9e5dd57 → 7d57aea |
| **V8.1** | **Posílení kvality: offline kontraktní harness (9 režimů, nulový rozdíl OFF) + unit testy i18n-core + CI brána + čestné rozdělení README (rámec 206 / přeloženo 8) + check-locale-keys.mjs** | 93a12d5 + e49330b |
| **V9.0** | **Generované artefakty modernizovány na Vue 3 + Element Plus + Vite; ověřeno za běhu (40/40 assertion, úspěšný vite build, 14 snímků vč. ar-SA RTL); opraveny 3 běhové defekty** | 93a12d5 + e49330b  |
| **V9.1** | **Refaktoring pro udržovatelnost · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9** | 654b8e0 → b0d081e  |
| **V9.2** | **Testovací centrum & ověřovací centrum · Test Center (7 tables: env/suite/case/dataset/run/result/schedule) + Verification Center (tc_run/tc_case_result/tc_artifact, 9 suites S1-S9, single-run 409 lock, `codegen.testcenter/v1`); 373/373 tests; 206/206 locales; output byte-identical** | V9.2 release, **aktuální verze** |

---

> **V6.10 Platformní pozorovatelnost**: integruje Actuator + Micrometer, vzorkuje každých 5 sekund do kruhového bufferu v paměti (30minutové okno), plus nová vestavěná stránka "Systémový monitor" — backend `/monitor/*` agregace API + frontend ECharts panely. Pouze `health/info/metrics` endpointy jsou whitelisted; `/actuator/health` je bez tokenu, zatímco `metrics` atd. vyžadují auth; `env/beans/heapdump/threaddump/loggers` nejsou nikdy vystaveny. Vzorkovací okno je pouze per-proces, ztrácí se při restartu a není sdíleno mezi instancemi. Viz `../codegen-server/docs/可观测性说明.md` (čínština) pro podrobnosti.

## 13. Stav projektu

- Aktuální plánovaná verzní řada dosáhla **V9.2** (kapacity se neustále iterují: AI modelování, vylepšení seed dat, systém balíčků schopností atd.).
- Doprovodný demo projekt `demo_admin` (backend 8140 / frontend 9040) pokrývá scénáře strom / CRUD / master-detail / jen pro čtení / tisk a běží samostatně pro ověření.
- Oba repozitáře jsou hostovány na Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. Autor & podpora

- **Autor**: čínský programátor (nezávislý vývojář)
- **E-mail**: `hustxiayuhong@126.com`
- **Technická podpora**: napište prosím svůj e-mail **v čínštině**, včetně popisu problému, runtime prostředí (JDK / MySQL / verze) a kroků reprodukce

> Každý e-mail čtu a odpovídám na něj. Chyby, požadavky na funkce a dotazy na spolupráci jsou všechny vítány.

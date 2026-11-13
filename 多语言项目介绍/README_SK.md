# Majiang · Generátor kódu (CodeGen) — Predstavenie projektu

> Jazyky: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · **Slovenčina** · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> Viacprojektový generátor kódu pre **Spring Boot + Vue**: vychádzajúc zo štruktúr databázových tabuliek produkuje **kompilovateľný, spustiteľný** business kód pre front-end a back-end s menu a oprávneniami a zaoberá sa celým doručovacím cyklom zápisom na disk, zálohováním, účtovníctvom (ledger) a vrátením.

GitHub: https://github.com/hustxia/CodeGen

> **aktuálna verzia V9.1 · Refaktoring pre udržateľnosť · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9**
> **V9.0 · Generované artefakty modernizované na Vue 3 + Element Plus + Vite; overené za behu (40/40 assertion, úspešný vite build, 14 snímok vrátane ar-SA RTL); opravené 3 behové defekty**
> **V8.1** · Posilnenie kvality: offline kontraktný harness (9 režimov, nulový rozdiel OFF) + unit testy i18n-core + CI brána + úprimné rozdelenie README (rámec 206 / preložené 8) + check-locale-keys.mjs
> **V8.0** · i18n celej stránky: rámec 206 jazykov (8 profesionálne preložených), dvojstopňová i18n s kontraktom nulového rozdielu pri OFF

## 1. Čo to je

CodeGen je "tabulkami riadená" kódová továreň. Priveďte svoje obchodné tabulky (importom z databázy alebo vložením DDL), nakonfigurujte polia a prepínače generovania a kliknite na "Generovať" — potom vygeneruje backendové Entity/Mapper/Service/Controller/DTO/VO, frontendové `api.js` a Vue stránky, menu a routy, dokonca aj A4 tlačové stránky a skelet integračných testov a bezpečne ich zapíše do vášho cieľového projektu.

Nie je to len "vypúšťanie kódu" — ale je to **doručovací pipeline s kontrolami kvality a schopnosťou vrátenia**:

```
Prijem tabuliek (DB import / DDL)
   → Konfigurácia polí a prepínačov (s AI odporúčaniami)
      → Generovanie jedným kliknutím: autokontrola → záloha → zápis na disk → kompilácia/validácia → registrácia menu → prebudovanie rout
         → Účtovníctvo → vratné
```

---

## 2. Kto sú používatelia a ako ich používajú

| Používateľ | Typická potreba | Cesta, ktorú platforma ponúka |
| --- | --- | --- |
| Back-end vývojár | Má skupinu tabuliek (alebo DDL) a chce rýchlo rozbehnúť CRUD | Stránka konfigurácie tabuliek: import → konfigurácia polí → generovanie jedným kliknutím |
| Full-stack / nezávislý vývojár | Chce od nuly postaviť kompletný systém (vrátane prihlásenia, oprávnení, slovníka) | Stránka Pipeline: postavte systém jednou vetou |
| Vedúci tímu | Chce zjednotiť kódový štýl, konvencie kódu a adresárovú štruktúru | Správa systému: jeden systém = jedna výstupná konfigurácia, platí dávkovo |
| Správca platformy | Chce meniť šablóny bez zmeny kódu | Správa šablón: online úprava, uloženie okamžite platí, podporuje vrátenie verzií |



## 3. Aké problémy rieši

| Bolestivý bod | Čo robí CodeGen |
| --- | --- |
| Opakované CRUD peklo — kopa boilerplate pre každú tabulku | Prepínače na úrovni tabulky produkujú kompletný front-end a back-end jedným kliknutím, vrátane CRUD, stránkovania, validácie, importu/exportu |
| Nezhody polí a API medzi front-endom a back-endom spôsobujú nekonečné integračné opravy | Rovnaká metadata tabuliek riadi backendové API aj frontendové stránky — nulová sémantická odchýlka |
| Tímové konvencie ťažko zjednotiteľné; každý píše svojím štýlom | Šablóny sú konvenciou: 22 FreeMarker šablón centrálne spravovaných, editovateľných online s vrátením verzií |
| Generátory "prepíšu a stratia" ručne písanú logiku | Mechanizmus spájania blokov markerov: ručne písaný kód sa zachová, len generované oblasti sa nahradia |
| Generovaný kód nedôveryhodný až do doby kompilácie | Autokontrola kompiláciou v pamäti (skutočný `javac`), kontroly štrukturálnych zmlúv, náhľad diffov na disk — nič sa neuloží na disk, pokiaľ to nekompiluje |
| Zostavovanie nového systému od nuly je časovo náročné | Pipeline "zostav systém jednou vetou": vytvor DB → kostra → generuj → zabal → registruj menu, plne automatizované |

---

## 4. Prehľad kapacít

- **Prijem tabuliek**: parsovanie DDL, priamy import z databázy, dávkový sprievodca prijmom, automaticky odvodená konfigurácia stĺpcov (ovládacie prvky / validácia / slovníky / relácie).
- **Tvary tabuliek**: jednoduchá tabulka, stromová tabulka (tree API + stromový grid), master-detail (uloženie v tej istej transakcii + záložky detailov), spojovacie tabulky many-to-many.
- **Generované artefakty**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue stránky + A4 tlačové stránky + skelet integračných testov.
- **Obchodné vylepšenia**: rozbaľovacie ponuky relácií, preklad cez slovník, maskovanie údajov, kontroly jedinečnosti, prepojenie formulárov, dátové oprávnenia, Excel import/export, schvaľovací workflow, balíčky schopností platieb / súborov / mobilných zariadení.
- **Kontrola kvality**: autokontrola kompiláciou v pamäti (skutočný `javac`), kontroly štrukturálnych zmlúv, kontroly konzistentnosti oprávnení, náhľad diffov na disk, skutočná validácia frontendových artefaktov.
- **Bezpečné umiestnenie**: zápis na disk + dávkové zálohy `.gen-backup`, spájanie blokov markerov pre zachovanie ručne písaného kódu, štvorkové účtovníctvo `gen_output` (vytvorené / spojené / preskočené / zlyhalo), historické vrátenie.
- **Izolácia viacerých systémov**: každý cieľový projekt je "konfigurácia systému" (cesty front/back, názvy balíčkov, gateway predpona, classpath závislostí, predvolené hodnoty prepínačov generovania) — viacero projektov obsluhovaných paralelne.
- **AI vylepšenia**: zostav systém jednou vetou (AI modelovanie), AI vylepšenie seed dát, tabulkami riadená AI odporúčaná konfigurácia (náhľad diffov + zaškrtnuté použitie).

---

## 5. Typické scénáre

| Scenár | Popis |
| --- | --- |
| Stromová tabulka | Hierarchické údaje ako kategórie produktov — automaticky produkuje tree API, stromový grid a rozbaľovaciu ponuku nadradenej voľby |
| Jednoduchá tabulka CRUD | Zákazníci, produkty atď. — FK rozbaľovacie ponuky + preklad cez slovník + import/export pripravené okamžite |
| Master-detail | Predajné objednávky + riadky objednávok — uloženie v tej istej transakcii, zásuvka detailov s dvoma záložkami (hlavné info / detaily) |
| Len na čítanie / štatistiky | Toky zásob atď. — prepínače zápisu vypnuté, len dotaz a export |
| Tlačové dokumenty | Zaškrtnite prepínač "tlač" pre produkciu A4 tlačovej stránky a routy s hlavičkou + tabuľkou detailov |
| Zostavovanie systému jednou vetou | Popíšte obchodnú doménu — AI vytvorí DB a tabulky + kostru + kompletné generovanie + registráciu menu naraz |

> Všetky scénáre majú spustiteľné demo (`demo_admin` projekt) s screenshotmi — pozri `../codegen-server/docs/典型场景操作指南-演示后台demo.md` (čínština).

---

## 6. Základný pracovný tok

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Prijem tabuliek │ → │ Konf. polí/prepínačov │ → │ Generovanie jedným klikom (autokontrola→záloha→zápis→│ → │ Účtovníctvo · Vrátenie │
│               │   │                       │   │ kompilácia→menu→routy)                         │   │                       │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     ovládacie prvky/validácia   Na disk sa uloží len po všetkých kontrolách kvality;
                    /slovníky                    ručne písaný kód sa zachová
```

- **Náhľad diffov**: generované artefakty sú diffované súbor po súbore oproti aktuálnemu stavu na disku; potvrdíte, než sa niečo zapíše, aby ste predišli náhodnému prepísaniu.
- **Účtovníctvo generovania**: každý súbor zaznamenáva svoju akciu, počet bajtov, MD5 a umiestnenie zálohy; celá dávka môže byť vrátená.

---

## 7. Zostav systém jednou vetou (Pipeline)

Vysokoúrovňová kapacita zameraná na "zostavenie backendu od nuly". Potom čo popíšete obchodnú doménu, pipeline automaticky postupuje 5 fázami:

| Fáza | Význam |
| --- | --- |
| `CHECK` | Overuje konfiguráciu cieľového projektu a classpath závislostí |
| `WRITE` | Vytvára databázu a tabulky, píše kostru a generované artefakty |
| `COMPILE` | Backend `mvn package` / frontend build, autokontrola kompiláciou v pamäti |
| `MENU` | Produkuje a registruje oprávnenia menu (dva výstupy: artefakt SQL / interný priamy zápis) |
| `ROUTE` | Prebudováva fragmenty frontendových rout |

Akékoľvek zlyhanie fázy okamžite preruší s čitateľnou chybou pre jednoduchú lokalizáciu.

---

## 8. Architektúra systému

Front-end a back-end oddelené, dva repozitáre spolupracujú:

| Repozitár | Úloha | Stack |
| --- | --- | --- |
| `codegen-server` | Backend: generačný engine, šablóny, validácia, účtovníctvo, pipeline | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Frontend: konzola (konf. tabuliek / šablóny / účtovníctvo / správa systému) | Vue 2.7 · Element UI · axios · Vue Router (history mode) |

Backendové vrstvy:

```
controller/    API vrstva (Gen* generačný reťazec / Sys* správa systému / pipeline)
service/impl/  Implementácie jadra (generačný reťazec, šablónový engine, spájanie, validácia, vykonávanie pipeline)
util/          Šablónový engine, zostavovanie modelov, spájanie blokov markerov, autokontrola kompilácie
config/        Konfigurácia výstupu, konfigurácia autokontroly, osivo šablón
common/        Result / BaseEntity / výnimky / audit
resources/gen-templates/   22 FreeMarker šablón (backend / frontend / shared / test skupiny)
```

**Základné kapacity, ktoré cieľový projekt musí poskytovať** (generátor produkuje len business kód; chýbajúci základ tichým spôsobom zlyhá):

1. MyBatis-Plus stránkovací plugin (`PaginationInnerInterceptor`)
2. Základné triedy ako `Result` / `BaseEntity` / `PageQuery` (predpona balíčka musí zodpovedať `root-package`)
3. Frontend `utils/request.js` s `toPageData` (prevádza `Page{records,total}` na `{list,total}`)
4. Závislosti `springdoc-openapi-starter-webmvc-ui` (generovaný kód používa `@Operation`) a `poi-ooxml` (Excel)

---

## 9. Prehľad technologického stacku

| Dimenzia | Voľba |
| --- | --- |
| Backend jazyk / rámec | Java 21 / Spring Boot 3.2.5 |
| Perzistencia | MyBatis-Plus 3.5.5 |
| Šablónový engine | FreeMarker (22 osivových šablón, online úprava + vrátenie verzií) |
| Zabezpečenie | Overenie JWT |
| Databáza | MySQL 8 |
| Frontend | Vue 2.7 + Element UI |
| Lokálny AI | qwen2.5-coder:7b (modelovanie / odporúčaná konfigurácia / vylepšenie seed dát) |
| API dokumentácia | springdoc-openapi (Swagger UI) |

---

## 10. Rýchly štart

### 1. Vytvorte databázu (13 platformných tabuliek + predvolený účet admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Nakonfigurujte cieľový projekt

Skopírujte šablónu konfigurácie a upravte ju podľa vašich miestnych ciest:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` je vylúčený z `.gitignore` a nebude commitovaný. Hlavné nastavenia:

```yaml
gen:
  output:
    backend-root: /cesta/k/vášmu-cieľovému-projektu      # základ zápisu artefaktov
    frontend-root: /cesta/k/vášmu-cieľovému-projektu
    frontend-path: demo-web                          # frontend podadresár pre monorepo
    backend-path: demo-server/src/main/java/com/demo
    package-name: com.demo
    root-package: com.demo                           # predpona pre import základných tried v artefaktoch
  selfcheck:
    classpath: /cesta/k/vášmu-cieľovému-projektu/demo-lib/*
```

Exportujte adresár závislostí pre autokontrolu takto:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Spustite backend

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

Služba beží na predvolenom porte **8120**; API dokumentácia na `/swagger-ui/index.html`.

### 4. Spustite frontend

```bash
cd ../codegen-web
npm install
npm run serve
```

Frontend beží na predvolenom porte **9530**, proxy `/dev-api` na backend port 8120. Predvolený účet `admin / admin123`.

### 5. Dôležité konfiguračné položky

| Nastavenie | Popis |
| --- | --- |
| `gen.output.*` | Cesty zápisu artefaktov, názvy balíčkov, mapovanie modulov |
| `gen.selfcheck.classpath` | Classpath závislostí pre autokontrolu kompiláciou v pamäti, podporuje `dir/*` |
| `gen.menu.outlet` | `artifact` produkuje iba menu SQL a fragmenty rout; `internal` tiež zapisuje do vlastnej `sys_menu` platformy |

---

## 11. Mapa dokumentácie

| Dokument | Obsah |
| --- | --- |
| `../codegen-server/docs/代码生成器-操作手册.md` (čínština) | Nasadenie, 5-minútový štart, konfigurácia tabuliek, dávkový prijem, správa šablón, účtovníctvo, API katalóg, celý pipeline (s screenshotmi) |
| `../codegen-server/docs/典型场景操作指南-演示后台demo.md` (čínština) | 6 tabuliek pokrývajúcich 5 typických scénárov, so spustiteľným demom a skutočnými screenshotmi |
| `项目介绍.md` | Dokument s predstavením projektu, rovnaký zdroj ako tento súbor |
| `../codegen-server/docs/代码生成器-Vx.x-规划.md` | Verzné plány a inventáre funkcií od V3.1 → V9.1 |
| `../codegen-web/README.md` | Rýchla referenčná príručka pre frontend konzolu |

---

## 12. História verzií

| Verzia | Téma / plánový dokument | Commit nasadenia |
| --- | --- | --- |
| V1.2 / V1.3 | Generovanie jedným kliknutím / dávkové / vrátenie; náhľad diffov & synchronizácia štruktúry | — (iba v komentároch `src/api/gen.js`) |
| V2.0 | Dynamické routy (backend dodaný strom menu) | — |
| V3.0 / V3.1 | Účtovníctvo viacerých systémov; slučka viacerých systémov + prvé automatizované testy | V3.1 (174 riadkov) |
| V4.2 | Sledovateľné, vratné výsledky generovania (diskové účtovníctvo) | V4.2 (292 riadkov) |
| V4.3 | Regresia šablón & kvalitné zábrany | V4.3 (200 riadkov) |
| V4.4 | Jeden-majster-veľa-detailov, úplnosť kaskádového zápisu, pruhy šablón | V4.4 (154 riadkov) |
| V4.5 | A4 tlačové dokumenty (nulové závislosti tretích strán) | V4.5 (127 riadkov) |
| V5.0 | Zostavovanie systému jednou vetou: pipeline + AI modelovanie + základ kostry | — |
| V5.1 | Automatické doplnenie stĺpcov auditu DDL | V5.1 (100 riadkov) |
| V5.2 | Krok inicializácie seed dát v pipeline | V5.2 (107 riadkov), 84ec8dd |
| V5.5 | Dokončenie generovania multimodulového projektu | af73dd3 |
| V6.0 | Mikroservisná infraštruktúra (Nacos / Gateway / Feign, tretí stav "micro") | V6.0 (111 riadkov), 1deecbd |
| V6.1 | Dvojitý engine workflow Activiti | V6.1 (100 riadkov), 02b671e |
| V6.2 | Plocha doručenia artefaktov (CLI / deploy artefakty / zabezpečenie konfigurácie) | V6.2 (114 riadkov), 02b671e |
| V6.3 | Balíček schopností platieb (WeChat / Alipay + Mock) | V6.3 (104 riadkov), 1c4ee6a, 22eede5 |
| V6.4 | Základ oprávnení a zabezpečenia (perzistencia tokenov / BCrypt / API oprávnenia / ochrana prihlásenia) | V6.4 (107 riadkov), 007a202 |
| V6.5 | Mobilné uni-app (H5 + WeChat Mini Program) | V6.5 (105 riadkov), 779ac41 |
| V6.6 | Balíček schopností súborov | 98f3c09 |
| V6.7 | Prijatie testov artefaktov vo výrobe (test_enabled trojstavý + kostra H2) | V6.7 (134 riadkov), 4e7d318 |
| V6.8 | Udržovateľnosť artefaktov (dryRun / chránené preskočenie / parametrizácia značky) | V6.8 (141 riadkov), be6f858 |
| V6.9 | Platformné centrum dokumentácie (backend základ, frontend dokončený v tej istej dávke) | — |
| V6.10 | Platformná pozorovateľnosť: Actuator + metrické okno v pamäti + vstavaná stránka monitoru | — |
| V7.0 | AI odporúčania strán a menu (preview / apply) | V7.0 (94 riadkov), 45f2670 |
| V7.1 | AI vylepšenie seed dát (AI vlastní sémantiku, pravidlá vlastní štruktúru) | V7.1 (42 riadkov), 5ca6e72 |
| V7.2 | SQL doručené objekty zapísané do `db/` po úspešnom pipeline | eebff88 |
| **V7.3** | **Dôveryhodnosť doručenia: autokontrola kompilované testy artefaktov / jednotný SQL výstup doručenia / tabulkami riadená AI odporúčania** | **V7.3 (138 riadkov), 696765b** |
| **V7.4** | Opakovateľné doručenie: nasaditeľné z prázdnej databázy, každý commit zaručene spustiteľný | c306e29 |
| **V7.5** | **Jazyková konfigurácia projektu: zoznam 206 jazykov / predvolený jazyk / povolené jazyky, vkladanie lang a dir do generovaného kódu** | 7a9afa0 + abac56d |
| **V8.0** | **i18n celej stránky: rámec 206 jazykov (8 profesionálne preložených), dvojstopňová i18n s kontraktom nulového rozdielu pri OFF** | 9e5dd57 → 7d57aea |
| **V8.1** | **Posilnenie kvality: offline kontraktný harness (9 režimov, nulový rozdiel OFF) + unit testy i18n-core + CI brána + úprimné rozdelenie README (rámec 206 / preložené 8) + check-locale-keys.mjs** | 93a12d5 + e49330b |
| **V9.0** | **Generované artefakty modernizované na Vue 3 + Element Plus + Vite; overené za behu (40/40 assertion, úspešný vite build, 14 snímok vrátane ar-SA RTL); opravené 3 behové defekty** | 93a12d5 + e49330b  |
| **V9.1** | **Refaktoring pre udržateľnosť · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9** | 654b8e0 → b0d081e, **aktuálna verzia** |

---

> **V6.10 Platformná pozorovateľnosť**: integruje Actuator + Micrometer, vzorkuje každých 5 sekúnd do kruhového bufferu v pamäti (30-minútové okno), plus nová vstavaná stránka "Systémový monitor" — backend `/monitor/*` agregácie API + frontend ECharts panely. Iba `health/info/metrics` endpointy sú whitelisted; `/actuator/health` je bez tokenu, zatiaľ čo `metrics` atď. vyžadujú auth; `env/beans/heapdump/threaddump/loggers` nie sú nikdy vystavené. Vzorkovacie okno je iba per-proces, stráca sa pri reštarte a nie je zdieľané medzi inštanciami. Pozri `../codegen-server/docs/可观测性说明.md` (čínština) pre podrobnosti.

## 13. Stav projektu

- Aktuálna plánovaná verzná línia dosiahla **V9.1** (kapacity sa neustále iterujú: AI modelovanie, vylepšenie seed dát, systém balíčkov schopností atď.).
- Doprovodný demo projekt `demo_admin` (backend 8140 / frontend 9040) pokrýva scenáre strom / CRUD / master-detail / len na čítanie / tlač a beží samostatne pre overenie.
- Oba repozitáre sú hostované na Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. Autor & podpora

- **Autor**: čínsky programátor (nezávislý vývojár)
- **E-mail**: `hustxiayuhong@126.com`
- **Technická podpora**: prosím napíšte svoj e-mail **v čínštine**, vrátane popisu problému, runtime prostredia (JDK / MySQL / verzia) a krokov reprodukcie

> Každý e-mail čítam a odpovedám na neho. Chyby, požiadavky na funkcie a otázky na spoluprácu sú všetky vítané.

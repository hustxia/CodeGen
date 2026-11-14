# Majiang · Kódgenerátor (CodeGen) — Projektbemutató

> Nyelvek: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · **Magyar** · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> Egy többprojektet kezelő kódgenerátor **Spring Boot + Vue** rendszerekhez: adatbázistábla-szerkezetekből kiindulva **lefordítható, futtatható** üzleti kódot állít elő front-end és back-end oldalra menükkel és jogosultságokkal, valamint kezeli a teljes szállítási ciklust: lemezre írás, biztonsági mentés, naplózás (ledger) és visszaállítás.

GitHub: https://github.com/hustxia/CodeGen

## 1. Mi ez

A CodeGen egy "táblaalapú" kódgyár. Hozza létre üzleti tábláit (adatbázis-importálás vagy DDL beillesztésével), konfigurálja a mezőket és a generációs kapcsolókat, majd kattintson a "Generálás" gombra — a rendszer létrehozza a back-end Entity/Mapper/Service/Controller/DTO/VO osztályait, a front-end `api.js` fájlt és Vue oldalakat, a menüket és route-okat, sőt még A4-es nyomtatási oldalakat és integrációs tesztvázlatokat is, és mindent biztonságosan a célprojektjébe ír.

Nem csak "kódot köpköd ki" — hanem egy **szállítási folyamat minőségi kapukkal és visszaállítási képességgel**:

```
Tábla bevitel (DB import / DDL)
   → Mező és kapcsoló konfiguráció (AI ajánlásokkal)
      → Egykattintásos generálás: önellenőrzés → biztonsági mentés → lemezre írás → fordítás/ellenőrzés → menü regisztrálás → route-ok újbóli építése
         → Naplózás → visszaállítható
```

---

## 2. Kik a felhasználók, és hogyan használják

| Felhasználó | Tipikus igény | Az út, amelyet a platform kínál |
| --- | --- | --- |
| Back-end fejlesztő | Van egy csomó táblája (vagy DDL), és gyorsan fel akar állítani egy CRUD-ot | Táblakonfigurációs oldal: importálás → mezők konfigurálása → egykattintásos generálás |
| Full-stack / független fejlesztő | Nulláról szeretne egy teljes rendszert építeni (bejelentkezéssel, jogosulttságokkal, szótárral) | Folyamat oldal: építsen rendszert egy mondattal |
| Csapatvezető | Szeretné egységesíteni a kódstílust, a kódolási konvenciókat és a könyvtárszerkezetet | Rendszerkezelés: egy rendszer = egy kimeneti konfiguráció, kötegelten lép érvénybe |
| Platform karbantartója | Sablonokat akar módosítani a kód megváltoztatása nélkül | Sablonkezelés: online szerkesztés, mentés után azonnal érvényes, támogatja a verzióvisszaállítást |



## 3. Milyen problémákat old meg

| Fájdalmas pont | Mit tesz a CodeGen |
| --- | --- |
| Ismétlődő CRUD pokol — rengeteg boilerplate minden táblához | Táblaalapú kapcsolók egy kattintással teljes front-end és back-end kódot hoznak létre, CRUD, lapozás, validáció, import/export képességekkel |
| Front/back mező és API eltérések végtelen integrációs hibákat okoznak | Ugyanaz a tábla metaadat vezérli a back-end API-kat és a front-end oldalakat — nulla szemantikai eltérés |
| Csapatkonvenciók nehezen egységesíthetők; mindenki a saját stílusában ír | A sablonok a konvenciók: 22 FreeMarker sablon központilag kezelve, online szerkeszthető verzióvisszaállítással |
| A generátorok "felülírják és elvesztik" a kézzel írt logikát | Marker-blokk egyesítési mechanizmus: a kézzel írt kód megőrződik, csak a generált régiók cserélődnek |
| A generált kód megbízhatatlan a fordítási időszakig | Memóriabeli fordítási önellenőrzés (valódi `javac`), strukturális szerződés-ellenőrzések, diff előnézet lemezen — semmi sem kerül lemezre, hacsak nem fordul le |
| Új rendszer létrehozása nulláról időigényes | Folyamat "rendszer létrehozása egy mondattal": DB létrehozás → állvány → generálás → csomagolás → menük regisztrálás, teljesen automatizált |

---

## 4. Képességek áttekintése

- **Tábla bevitel**: DDL parse-olás, közvetlen adatbázis-importálás, köteges bevitel varázsló, automatikusan levezetett oszlopkonfiguráció (vezérlők / validáció / szótárak / relációk).
- **Táblaformák**: egyszerű tábla, fatábla (tree API + farács), master-detail (azonos tranzakcióban történő mentés + részletek fülek), sok-több kapcsolatú összekötő táblák.
- **Generált műtermékek**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue oldalak + A4-es nyomtatási oldalak + integrációs tesztvázlatok.
- **Üzleti fejlesztések**: relációs legördülő menük, szótárfordítás, adatmaszkolás, egyediség-ellenőrzések, űrlap-kapcsolás, adatjogosultságok, Excel import/export, jóváhagyási munkafolyamat, fizetési / fájl / mobil képességcsomagok.
- **Minőségi kapuk**: memóriabeli fordítási önellenőrzés (valódi `javac`), strukturális szerződés-ellenőrzések, jogosultság-konzisztencia ellenőrzések, diff előnézet lemezen, front-end műtermékek valódi ellenőrzése.
- **Biztonsági landolás**: lemezre írás + `.gen-backup` köteges biztonsági mentések, marker-blokk egyesítés a kézzel írt kód megőrzésére, `gen_output` négyállapotú napló (létrehozva / egyesítve / kihagyva / sikertelen), történelmi visszaállítás.
- **Több rendszer izolálása**: minden célprojekt egy "rendszerkonfiguráció" (front/back útvonalak, csomagnevek, gateway előtag, függőségi classpath, generációs kapcsoló alapértelmezett értékek) — több projekt párhuzamosan kezelhető.
- **AI fejlesztések**: rendszer létrehozása egy mondattal (AI modellezés), seed adat AI fejlesztés, táblaalapú AI ajánlott konfiguráció (diff előnézet + kijelölt alkalmazás).

---

## 5. Tipikus forgatókönyvek

| Forgatókönyv | Leírás |
| --- | --- |
| Fatábla | Hierarchikus adatok, mint például termékkategóriák — automatikusan létrehoz tree API-t, farácsot és szülőválasztó legördülő menüt |
| Egyszerű tábla CRUD | Vevők, termékek stb. — FK legördülő menük + szótárfordítás + import/export közvetlenül használatra készen |
| Master-detail | Értékesítési rendelések + rendelés tételek — azonos tranzakcióban történő mentés, részletek fiók kettős füllel (fő info / részletek) |
| Csak olvasható / statisztika | Készletáramlatok stb. — írási kapcsolók kikapcsolva, csak lekérdezés és export |
| Nyomtatási dokumentumok | Jelölje be a "nyomtatás" kapcsolót A4-es nyomtatási oldal és route létrehozásához, fejléc + részlettáblával |
| Egy mondatban rendszer létrehozás | Írja le az üzleti tartományt — AI létrehozza a DB-t és táblákat + állványt + teljes generálást + menü regisztrálást egy lépésben |

> Minden forgatókönyvnek futtatható demoja van (`demo_admin` projekt) képernyőképekkel — lásd a `docs/典型场景操作指南-演示后台demo.md` fájlt (kínai).

---

## 6. Alapvető munkafolyamat

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Tábla bevitel │ → │ Mező/kapcsoló konfiguráció │ → │ Egykattintás generál (önellenőrzés→mentés→írás→    │ → │ Napló · Visszaállítás │
│              │   │                    │   │ fordítás→menü→route)                             │   │                        │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     vezérlők/validáció     Lemezre csak az összes minőségi kapun túl kerül;
                    /szótárak               kézzel írt kód megőrződik
```

- **Diff előnézet**: a generált műtermékek fájlonként diff-ellenőrzésre kerülnek a jelenlegi lemez állapothoz képest; Ön megerősíti, mielőtt bármit is írna, hogy elkerülje a véletlen felülírásokat.
- **Generációs napló**: minden fájl rögzíti a műveletét, bájtmennyiségét, MD5 értékét és biztonsági mentés helyét; egy teljes köteg visszaállítható.

---

## 7. Rendszer létrehozása egy mondattal (Folyamat)

Egy magas szintű képesség, amelynek célja "backend létrehozása nulláról". Miután leírja az üzleti tartományt, a folyamat automatikusan halad 5 szakaszon keresztül:

| Szakasz | Jelentés |
| --- | --- |
| `CHECK` | Ellenőrzi a célprojekt konfigurációt és függőségi classpath-ot |
| `WRITE` | Létrehozza az adatbázist és a táblákat, írja az állványt és a generált műtermékeket |
| `COMPILE` | Backend `mvn package` / front-end build, memóriabeli fordítási önellenőrzés |
| `MENU` | Létrehozza és regisztrálja a menüjogosultságokat (két kimenet: műtermék SQL / belső közvetlen írás) |
| `ROUTE` | Újjáépíti a front-end route fragmentumokat |

Bármelyik szakasz hibája azonnal leáll egy olvasható hibával a könnyű lokalizáció érdekében.

---

## 8. Rendszerarchitektúra

Front-end és back-end elkülönítve, két repozitório együttműködik:

| Repozitório | Szerep | Verem |
| --- | --- | --- |
| `codegen-server` | Backend: generációs motor, sablonok, validáció, napló, folyamat | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Frontend: konzol (tábla konfiguráció / sablonok / napló / rendszerkezelés) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (history mód) |

Backend rétegek:

```
controller/    API réteg (Gen* generációs lánc / Sys* rendszerkezelés / folyamat)
service/impl/  Magimplementációk (generációs lánc, sablonmotor, egyesítés, validáció, folyamat végrehajtás)
util/          Sablonmotor, modell összeszerelés, marker-blokk egyesítés, önellenőrző fordítás
config/        Kimeneti konfiguráció, önellenőrző konfiguráció, sablon vetőgép
common/        Result / BaseEntity / kivételek / naplózás
resources/gen-templates/   22 FreeMarker sablon (backend / frontend / shared / test csoportok)
```

**Alapvető képességek, amelyeket a célprojektnek biztosítania kell** (a generátor csak üzleti kódot állít elő; hiányzó alap csendes hibát okoz):

1. MyBatis-Plus lapozó plugin (`PaginationInnerInterceptor`)
2. Alapvető osztályok, mint például `Result` / `BaseEntity` / `PageQuery` (csomag előtagnak meg kell egyeznie a `root-package` értékkel)
3. Frontend `utils/request.js` `toPageData` függvénnyel (átalakítja a `Page{records,total}` struktúrát `{list,total}` formátumra)
4. Függőségek `springdoc-openapi-starter-webmvc-ui` (a generált kód `@Operation` annotációt használ) és `poi-ooxml` (Excel)

---

## 9. Technikai verem áttekintése

| Dimenzió | Választás |
| --- | --- |
| Backend nyelv / keretrendszer | Java 21 / Spring Boot 3.2.5 |
| Perzisztencia | MyBatis-Plus 3.5.5 |
| Sablonmotor | FreeMarker (22 vető sablon, online szerkesztés + verzióvisszaállítás) |
| Biztonság | JWT hitelesítés |
| Adatbázis | MySQL 8 |
| Frontend (generated projects) | Vue 3 + Element Plus + Vite |
| Frontend (platform console) | Vue 2.7 + Element UI |
| Helyi AI | qwen2.5-coder:7b (modellezés / ajánlott konfiguráció / seed adat fejlesztés) |
| API dokumentáció | springdoc-openapi (Swagger UI) |

---

## 10. Gyors kezdés

### 1. Hozza létre az adatbázist (13 platformtábla + alapértelmezett fiók admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Konfigurálja a célprojektet

Másolja át a konfigurációs sablont és állítsa be a helyi útvonalaknak megfelelően:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

Az `application-local.yml` fájlt a `.gitignore` kizárja, ezért nem lesz commitolva. Főbb beállítások:

```yaml
gen:
  output:
    backend-root: /útvonal/az/Ön/célprojektjéhez      # műtermékek írási alapja
    frontend-root: /útvonal/az/Ön/célprojektjéhez
    frontend-path: demo-web                          # front-end alkönyvtár monorepo esetén
    backend-path: demo-server/src/main/java/com/demo
    package-name: com.demo
    root-package: com.demo                           # előtag az alapvető osztályok importálásához a műtermékekben
  selfcheck:
    classpath: /útvonal/az/Ön/célprojektjéhez/demo-lib/*
```

Exportálja az önellenőrzés függőségi könyvtárát így:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Indítsa el a backend-et

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

A szolgáltatás alapértelmezés szerint a **8120**-as porton fut; API dokumentáció a `/swagger-ui/index.html` címen.

### 4. Indítsa el a front-end-et

```bash
cd ../codegen-web
npm install
npm run serve
```

A front-end alapértelmezés szerint a **9530**-as porton fut, a `/dev-api` kérést a backend 8120-as portjára proxyzza. Alapértelmezett fiók `admin / admin123`.

### 5. Fontos konfigurációs elemek

| Beállítás | Leírás |
| --- | --- |
| `gen.output.*` | Műtermékek írási útvonalai, csomagnevek, modul-leképezés |
| `gen.selfcheck.classpath` | Függőségi classpath a memóriabeli fordítási önellenőrzéshez, támogatja a `dir/*` formátumot |
| `gen.menu.outlet` | `artifact` csak menü SQL-t és route fragmentumokat állít elő; `internal` a platform saját `sys_menu` táblájába is ír |

---

## 11. Dokumentációs térkép

| Dokumentum | Tartalom |
| --- | --- |
| `docs/代码生成器-操作手册.md` (kínai) | Üzembe helyezés, 5 perces kezdés, tábla konfiguráció, köteges bevitel, sablonkezelés, napló, API katalógus, teljes folyamat (képernyőképekkel) |
| `docs/典型场景操作指南-演示后台demo.md` (kínai) | 6 tábla, amely 5 tipikus forgatókönyvet fed le, futtatható demóval és valódi képernyőképekkel |
| `docs/项目介绍.md` | Projektbemutató dokumentum, ugyanaz a forrás, mint ez a fájl |
| `docs/代码生成器-Vx.x-规划.md` | Verziótervek és funkcióleltárak V3.1 → V7.3 |
| `codegen-web/README.md` | Front-end konzol gyorskezelés referencia |

---

## 12. Verziótörténet

| Verzió | Téma / tervdokumentum | Telepítési commit |
| --- | --- | --- |
| V1.2 / V1.3 | Egykattintásos generálás / köteges / visszaállítás; diff előnézet & szinkronizált szerkezet | — (csak a `src/api/gen.js` megjegyzésekben) |
| V2.0 | Dinamikus route-ok (backend által szállított menüfa) | — |
| V3.0 / V3.1 | Több rendszeres napló; több rendszeres hurok + első automatizált tesztek | V3.1 (174 sor) |
| V4.2 | Nyomon követhető, visszaállítható generációs eredmények (lemez napló) | V4.2 (292 sor) |
| V4.3 | Sablon regresszió & minőségi védőkorlátok | V4.3 (200 sor) |
| V4.4 | Egy-mester-sok-részlet, teljes kaszkádírás, sablon sávok | V4.4 (154 sor) |
| V4.5 | A4-es nyomtatási dokumentumok (nulla külső függőség) | V4.5 (127 sor) |
| V5.0 | Egy mondatban rendszer létrehozás: folyamat + AI modellezés + állvány alap | — |
| V5.1 | DDL audit oszlopok automatikus kiegészítés | V5.1 (100 sor) |
| V5.2 | Folyamat seed adat inicializációs lépés | V5.2 (107 sor), 84ec8dd |
| V5.5 | Többmodulos projekt generálás lezárás | af73dd3 |
| V6.0 | Mikroszolgáltatási infrastruktúra (Nacos / Gateway / Feign, harmadik állapot "micro") | V6.0 (111 sor), 1deecbd |
| V6.1 | Activiti munkafolyamat kettős motor | V6.1 (100 sor), 02b671e |
| V6.2 | Műtermék szállítási felület (CLI / deploy műtermékek / konfigurációs biztonság) | V6.2 (114 sor), 02b671e |
| V6.3 | Fizetési képességcsomag (WeChat / Alipay + Mock) | V6.3 (104 sor), 1c4ee6a, 22eede5 |
| V6.4 | Jogosultság & biztonsági alap (token perzisztencia / BCrypt / API jogosultságok / bejelentkezés védelem) | V6.4 (107 sor), 007a202 |
| V6.5 | Mobil uni-app (H5 + WeChat Mini Program) | V6.5 (105 sor), 779ac41 |
| V6.6 | Fájl képességcsomag | 98f3c09 |
| V6.7 | Műtermék teszt gyári átvétel (test_enabled háromállapot + H2 váz) | V6.7 (134 sor), 4e7d318 |
| V6.8 | Műtermék karbantarthatóság (dryRun / védett kihagyás / márka paraméterezés) | V6.8 (141 sor), be6f858 |
| V6.9 | Platform dokumentációs központ (backend alap, front-end elkészült ugyanabban a kötegben) | — |
| V6.10 | Platform megfigyelhetőség: Actuator + memóriabeli metrikus ablak + beágyazott monitor oldal | — |
| V7.0 | AI oldal & menü ajánlások (preview / apply) | V7.0 (94 sor), 45f2670 |
| V7.1 | Seed adat AI fejlesztés (AI birtokolja a szemantikát, szabályok birtokolják a struktúrát) | V7.1 (42 sor), 5ca6e72 |
| V7.2 | SQL szállított objektumok írva a `db/` könyvtárba sikeres folyamat után | eebff88 |
| **V7.3** | **Szállítási megbízhatóság: önellenőrzés lefordított műtermék tesztek / egységes SQL szállítási kimenet / táblaalapú AI ajánlások** | **V7.3 (138 sor)** |
| **V7.4** | Ismételhető szállítás: üres adatbázisból telepíthető, minden commit garantáltan futtatható | c306e29 |
| **V7.5** | Projekt nyelvi konfiguráció: 206 nyelv listája / alapértelmezett nyelv / engedélyezett nyelvek, lang és dir befecskendezése a generált kódba | 7a9afa0 + abac56d |
| **V8.0** | Teljes oldal i18n: 206 nyelvű keretrendszer (8 szakmailag lefordítva), kétvágányú i18n OFF zero-diff szerződéssel | 9e5dd57 → 7d57aea |
| **V8.1** | Minőségszilárdítás: offline szerződéses harness (9 mód, OFF zero-diff) + i18n-core egységtesztek + CI kapu + őszinte README felosztás (keretrendszer 206 / lefordítva 8) + check-locale-keys.mjs | 93a12d5 + e49330b |
| **V9.0** | A generált artefaktok modernizálva Vue 3 + Element Plus + Vite-re; futásidőben ellenőrizve (40/40 assertion, sikeres vite build, 14 képernyőkép incl. ar-SA RTL); 3 futásidejű hiba javítva | 93a12d5 + e49330b  |
| **V9.1** | **Karbantarthatósági refaktorálás · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9** | 654b8e0 → b0d081e  |
| **V9.2** | **Tesztközpont & ellenőrzési központ · Test Center (7 tables: env/suite/case/dataset/run/result/schedule) + Verification Center (tc_run/tc_case_result/tc_artifact, 9 suites S1-S9, single-run 409 lock, `codegen.testcenter/v1`); 373/373 tests; 206/206 locales; output byte-identical** | V9.2 release, **Jelenlegi verzió** |

---

> **V6.10 Platform megfigyelhetőség**: integrálja az Actuator + Micrometer rendszert, 5 másodpercenként mintát vesz egy memóriabeli körbufferbe (30 perces ablak), plusz egy új beágyazott "Rendszerfigyelő" oldal — backend `/monitor/*` aggregációs API-k + front-end ECharts panelek. Csak `health/info/metrics` végpontok vannak engedélyezve a whitelist-en; `/actuator/health` token nélküli, míg a `metrics` stb. hitelesítést igényel; `env/beans/heapdump/threaddump/loggers` soha nem kerül közzétételre. A mintavételi ablak csak folyamatonkénti, újraindítás esetén elveszik, és nincs megosztva a példányok között. Lásd a `docs/可观测性说明.md` fájlt (kínai) részletekért.

## 13. Projekt állapot

- A jelenlegi tervezett verzióvonal elérte a **V7.3**-at (képességek folyamatosan iterálódnak: AI modellezés, seed adat fejlesztés, képességcsomag rendszer stb.).
- Egy kísérő demo projekt `demo_admin` (backend 8140 / front-end 9040) lefedi a fa / CRUD / master-detail / csak olvasható / nyomtatási forgatókönyveket és önállóan futtatható ellenőrzés céljából.
- Mindkét repozitório a Gitee-en van tárolva: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. Szerző & támogatás

- **Szerző**: egy kínai programozó (független fejlesztő)
- **E-mail**: `hustxiayuhong@126.com`
- **Technikai támogatás**: kérjük, írja meg e-mailjét **kínai nyelven**, beleértve a probléma leírását, a futtatási környezetet (JDK / MySQL / verzió) és a reprodukciós lépéseket

> Minden e-mailt elolvasok és válaszolok rájuk. Hiba jelentések, funkciókérések és együttműködési megkeresések mind welcome.

---

## 15. Összehasonlítás hasonló eszközökkel

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

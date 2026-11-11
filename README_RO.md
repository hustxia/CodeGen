# Majiang · Generator de cod (CodeGen) — Introducere în proiect

> Languages: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · **Română** · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md)

---

> Un generator de cod multi-proiect pentru **Spring Boot + Vue**: pornind de la structurile de tabele din baza de date, produce cod de business front-end și back-end **compilabil, rulabil și gata pentru meniuri și permisiuni**, și gestionează întregul ciclu de livrare: scriere pe disc, backup, evidență (ledger) și rollback.

GitHub: https://github.com/hustxia/CodeGen

## 1. Ce este acesta

CodeGen este o fabrică de cod „condusă de tabele”. Aduceți tabelele dvs. de business (prin import din baza de date sau lipind DDL), configurați câmpurile și comutatoarele de generare, apoi faceți clic pe „Generate” — acesta produce backend-ul Entity/Mapper/Service/Controller/DTO/VO, front-end-ul `api.js` și paginile Vue, meniurile și rutele, până la paginile de tipărire A4 și scheletele de teste de integrare, și le scrie în siguranță în proiectul țintă.

Nu doar „scoate cod” — este o **conductă de livrare (pipeline) cu porți de calitate și capacitate de rollback**:

```
Table intake (DB import / DDL)
   → Field & switch configuration (with AI recommendations)
      → One-click generation: self-check → backup → write to disk → compile/validate → register menu → rebuild routes
         → Ledger bookkeeping → rollback-capable
```

---

## 2. Ce probleme rezolvă

| Punct dureros (Pain point) | Ce face CodeGen |
| --- | --- |
| Iadul CRUD repetitiv — o grămadă de cod boilerplate pentru fiecare tabel | Comutatoarele per tabel produc un front-end și back-end complet cu un singur clic, inclusiv CRUD, paginare, validare, import/export |
| Neregulile dintre câmpurile și API-urile front/back care provoacă remedieri interminabile de integrare | Aceeași metadate de tabel conduc atât API-urile backend cât și paginile frontend — zero derivă semantică |
| Convențiile de echipă greu de unificat; stilul fiecăruia diferă | Șabloanele sunt convenția: 22 de șabloane FreeMarker gestionate central, editabile online cu rollback de versiune |
| Generatoarele „suprascriu și pierd” logica scrisă de mână | Mecanism de îmbinare cu bloc-marcaj: codul scris de mână este păstrat, doar regiunile generate sunt înlocuite |
| Codul generat nu este de încredere până la compilare | Auto-verificare de compilare în memorie (adevăratul `javac`), verificări structurale de contract, previzualizare diff pe disc — nimic nu ajunge pe disc dacă nu compilează |
| Crearea unui sistem nou de la zero este consumatoare de timp | Conducta „construiește un sistem cu o propoziție”: creează DB → scaffold → generează → împachetează → înregistrează meniuri, complet automatizat |

---

## 3. Peisajul capabilităților

- **Preluare tabele (Table intake)**: parsare DDL, import direct din baza de date, expert de preluare în lot, configurare automată a coloanelor (controale / validare / dicționare / relații).
- **Forme de tabel**: tabel simplu, tabel arbore (API arbore + grilă arbore), stăpân-detail (salvare în aceeași tranzacție + tab-uri de detail), tabele de joncțiune many-to-many.
- **Artefacte generate**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + pagini Vue + pagini de tipărire A4 + schelete de teste de integrare.
- **Îmbunătățiri de business**: liste derulante de relații, traducere prin dicționar, mascare date, verificări de unicitate, legătură între formulare, permisiuni de domeniu de date, import/export Excel, flux de aprobare, pachete de capacități de plată / fișiere / mobil.
- **Porți de calitate**: auto-verificare de compilare în memorie (adevăratul `javac`), verificări structurale de contract, verificări de consistență a permisiunilor, previzualizare diff pe disc, validare reală a artefactelor frontend.
- **Aterizare în siguranță**: scriere pe disc + backup-uri în lot `.gen-backup`, îmbinare cu bloc-marcaj pentru a păstra codul scris de mână, ledger în patru stări `gen_output` (created / merged / skipped / failed), rollback istoric.
- **Izolarea multi-sistem**: fiecare proiect țintă este o „configurare de sistem” (căi front/back, nume de pachete, prefix gateway, classpath de dependențe, valori implicite pentru comutatoarele de generare) — mai multe proiecte deservite paralel.
- **Îmbunătățiri AI**: construiește un sistem cu o propoziție (modelare AI), îmbunătățire AI a datelor seed, configurare AI recomandată per tabel (previzualizare diff + aplicare bifată).

---

## 4. Scenarii tipice

| Scenariu | Descriere |
| --- | --- |
| Tabel arbore | Date ierarhice precum categoriile de produse — API arbore, grilă arbore și listă derulantă de selecție a părintelui produse automat |
| CRUD pe tabel simplu | Clienți, produse etc. — liste derulante FK + traducere prin dicționar + import/export gata de utilizat |
| Stăpân-detail | Comenzi de vânzări + articole de comandă — salvare în aceeași tranzacție, sertar de detail cu tab-uri duble (info stăpân / detalii) |
| Doar citire / statistici | Fluxuri de inventar etc. — comutatoarele de scriere dezactivate, doar interogare și export |
| Documente de tipărire | Bifați comutatorul „print” pentru a produce o pagină de tipărire A4 și o rută, cu antet + tabel de detail |
| Construire sistem cu o propoziție | Descrieți domeniul de business — AI creează BD & tabele + scaffold + generare completă + înregistrare meniuri dintr-o singură mișcare |

> Toate scenariile au demo-uri rulabile (proiectul `demo_admin`) cu capturi de ecran — consultați `docs/典型场景操作指南-演示后台demo.md` (chineză).

---

## 5. Fluxul de lucru principal

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Table intake │ → │ Field/switch conf. │ → │ One-click generate (self-check→backup→write→     │ → │ Ledger · Rollback │
│              │   │                    │   │ compile→menu→route)                              │   │                   │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     controls/validation     Lands on disk only after all quality gates pass;      Batch-level traceability
                    /dictionaries           hand-written code is preserved
```

- **Previzualizare diff**: artefactele generate sunt comparate (diff) fișier cu fișier față de starea curentă de pe disc; confirmați înainte ca ceva să fie scris, evitând suprascrierile accidentale.
- **Ledger de generare**: fiecare fișier își înregistrează acțiunea, numărul de octeți, MD5 și locația backup-ului; un lot întreg poate fi readus la starea anterioară (rollback).

---

## 6. Construiți un sistem cu o propoziție (Pipeline)

O capacitate de nivel înalt destinată „creării unui backend de la zero”. După ce descrieți domeniul de business, conducta avansează automat prin 5 etape:

| Etapă (Stage) | Semnificație (Meaning) |
| --- | --- |
| `CHECK` | Validează configurația proiectului țintă și classpath-ul de dependențe |
| `WRITE` | Creează baza de date & tabele, scrie scaffold-ul și artefactele generate |
| `COMPILE` | Backend `mvn package` / build frontend, auto-verificare de compilare în memorie |
| `MENU` | Produce și înregistrează permisiunile de meniu (două ieșiri: SQL artifact / scriere directă internă) |
| `ROUTE` | Reconstruiește fragmentele de rută frontend |

Orice eșec al unei etape se oprește imediat cu o eroare lizibilă, ușor de localizat.

---

## 7. Arhitectura sistemului

Front-end și back-end separate, două depozite care lucrează împreună:

| Depozit (Repository) | Rol | Tehnologie (Stack) |
| --- | --- | --- |
| `codegen-server` | Backend: motor de generare, șabloane, validare, ledger, pipeline | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Frontend: consolă (config tabel / șabloane / ledger / management sistem) | Vue 2.7 · Element UI · axios · Vue Router (history mode) |

Stratificarea backend-ului:

```
controller/    API layer (Gen* generation chain / Sys* system management / pipeline)
service/impl/  Core implementations (generation chain, template engine, merge, validation, pipeline execution)
util/          Template engine, model assembly, marker-block merge, self-check compilation
config/        Output config, self-check config, template seeder
common/        Result / BaseEntity / exceptions / audit
resources/gen-templates/   22 FreeMarker templates (backend / frontend / shared / test groups)
```

**Capacitățile de bază (foundation) pe care proiectul țintă trebuie să le ofere** (generatorul produce doar cod de business; o bază lipsă eșuează silențios):

1. Plugin de paginare MyBatis-Plus (`PaginationInnerInterceptor`)
2. Clase de bază precum `Result` / `BaseEntity` / `PageQuery` (prefixul de pachet trebuie să corespundă cu `root-package`)
3. Frontend `utils/request.js` cu `toPageData` (convertește `Page{records,total}` în `{list,total}`)
4. Dependințele `springdoc-openapi-starter-webmvc-ui` (codul generat folosește `@Operation`) și `poi-ooxml` (Excel)

---

## 8. Tehnologiile dintr-o privire

| Dimensiune | Alegere |
| --- | --- |
| Limbaj / framework backend | Java 21 / Spring Boot 3.2.5 |
| Persistență | MyBatis-Plus 3.5.5 |
| Motor de șabloane | FreeMarker (22 șabloane seed, editare online + rollback de versiune) |
| Securitate | Autentificare JWT |
| Bază de date | MySQL 8 |
| Frontend | Vue 2.7 + Element UI |
| AI local | qwen2.5-coder:7b (modelare / config recomandată / îmbunătățire seed-data) |
| Documentație API | springdoc-openapi (Swagger UI) |

---

## 9. Începere rapidă

### 1. Creați baza de date (13 tabele de platformă + cont implicit admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Configurați proiectul țintă

Copiați șablonul de configurare și ajustați căile locale:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` este exclus de `.gitignore` și nu va fi comitat. Setări cheie:

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

Exportați directorul de dependențe pentru auto-verificare astfel:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Porniți backend-ul

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

Serviciul folosește implicit portul **8120**; documentația API la `/swagger-ui/index.html`.

### 4. Porniți frontend-ul

```bash
cd ../codegen-web
npm install
npm run serve
```

Frontend-ul folosește implicit portul **9530**, proxind `/dev-api` către portul backend 8120. Cont implicit `admin / admin123`.

### 5. Elemente cheie de configurare

| Setare | Descriere |
| --- | --- |
| `gen.output.*` | Căi de scriere artefacte, nume de pachete, mapare module |
| `gen.selfcheck.classpath` | Classpath de dependențe pentru auto-verificarea de compilare în memorie, suportă `dir/*` |
| `gen.menu.outlet` | `artifact` produce doar SQL de meniu & fragmente de rută; `internal` scrie și propriul `sys_menu` al platformei |

---

## 10. Harta documentației

| Document | Conținut |
| --- | --- |
| `docs/代码生成器-操作手册.md` (chineză) | Deployment, start în 5 minute, config tabel, preluare în lot, management șabloane, ledger, catalog API, pipeline complet (cu capturi de ecran) |
| `docs/典型场景操作指南-演示后台demo.md` (chineză) | 6 tabele acoperind 5 scenarii tipice, cu demo rulabil și capturi de ecran din rulare reală |
| `docs/项目介绍.md` | Document de introducere în proiect, aceeași sursă ca acest fișier |
| `docs/代码生成器-Vx.x-规划.md` | Planuri de versiune și inventare de funcționalități de la V3.1 → V7.3 |
| `codegen-web/README.md` | Referință rapidă de pornire pentru consola frontend |

---

## 11. Istoricul versiunilor

| Versiune | Temă / document de plan | Commit de livrare |
| --- | --- | --- |
| V1.2 / V1.3 | Generare / lot / rollback cu un clic; previzualizare diff & sincronizare structură | — (doar în comentariile `src/api/gen.js`) |
| V2.0 | Rute dinamice (arbore de meniu livrat de backend) | — |
| V3.0 / V3.1 | Ledger multi-sistem; buclă multi-sistem + primele teste automatizate | V3.1 (174 linii) |
| V4.2 | Rezultate de generare trasabile și cu rollback (scrie ledger) | V4.2 (292 linii) |
| V4.3 | Regresie șabloane & garduri de calitate | V4.3 (200 linii) |
| V4.4 | Un-stăpân-multe-detail, completitudine scriere în cascadă, lane de șabloane | V4.4 (154 linii) |
| V4.5 | Documente de tipărire A4 (zero dependențe terțe) | V4.5 (127 linii) |
| V5.0 | Construire sistem cu o propoziție: pipeline + modelare AI + bază scaffold | — |
| V5.1 | Completare automată coloane audit DDL | V5.1 (100 linii) |
| V5.2 | Pas de inițializare seed-data în pipeline | V5.2 (107 linii), 84ec8dd |
| V5.5 | Finalizarea generării proiectelor multi-modul | af73dd3 |
| V6.0 | Infrastructură microservicii (Nacos / Gateway / Feign, al treilea mod „micro”) | V6.0 (111 linii), 1deecbd |
| V6.1 | Motor dual flux de lucru Activiti | V6.1 (100 linii), 02b671e |
| V6.2 | Suprafață de livrare artefacte (CLI / artefacte deployment / securitate config) | V6.2 (114 linii), 02b671e |
| V6.3 | Pachet capacitate plată (WeChat / Alipay + Mock) | V6.3 (104 linii), 1c4ee6a, 22eede5 |
| V6.4 | Linie de bază permisiuni & securitate (persistență token / BCrypt / permisiuni API / protecție login) | V6.4 (107 linii), 007a202 |
| V6.5 | Mobile uni-app (H5 + WeChat Mini Program) | V6.5 (105 linii), 779ac41 |
| V6.6 | Pachet capacitate fișiere | 98f3c09 |
| V6.7 | Acceptare fabrică test artefacte (tri-state test_enabled + schelet H2) | V6.7 (134 linii), 4e7d318 |
| V6.8 | Mentenabilitate artefacte (dryRun / skip protejat / parametrizare brand) | V6.8 (141 linii), be6f858 |
| V6.9 | Centru de documentație platformă (bază backend, frontend terminat în același lot) | — |
| V6.10 | Observabilitate platformă: Actuator + fereastră metrici în memorie + pagină monitor înglobată | — |
| V7.0 | Recomandări pagini & meniuri AI (previzualizare / aplicare) | V7.0 (94 linii), 45f2670 |
| V7.1 | Îmbunătățire seed-data AI (AI deține semantica, regulile dețin structura) | V7.1 (42 linii), 5ca6e72 |
| V7.2 | Livrabile SQL scrise în `db/` după pipeline reușit | eebff88 |
| **V7.3** | **Încredere livrare: teste artefacte compilate auto-verificate / ieșire unică livrare SQL / recomandări AI per tabel** | **V7.3 (138 linii)** |
| **V7.4** | Livrare repetabilă: implementabil dintr-o bază de date goală, fiecare commit garantat rulabil | **Versiunea curentă** |

---

> **V6.10 Observabilitate platformă**: integrează Actuator + Micrometer, eșantionând la fiecare 5 secunde într-un buffer inelar în memorie (fereastră de 30 de minute), plus o nouă pagină înglobată „System Monitor” — API-uri de agregare backend `/monitor/*` + panouri ECharts frontend. Doar endpoint-urile `health/info/metrics` sunt pe listă albă; `/actuator/health` este fără token în timp ce `metrics` etc. necesită autentificare; `env/beans/heapdump/threaddump/loggers` nu sunt niciodată expuse. Fereastra de eșantionare este doar per-proces, pierdută la restart și nu este partajată între instanțe. Consultați `docs/可观测性说明.md` (chineză) pentru detalii.

## 12. Starea proiectului

- Linia de versiune planificată curentă a ajuns la **V7.3** (capacitățile continuă să evolueze: modelare AI, îmbunătățire seed-data, sistem de pachete de capacități etc.).
- Un proiect demo însoțitor `demo_admin` (backend 8140 / frontend 9040) acoperă scenariile arbore / CRUD / stăpân-detail / doar-citire / tipărire și rulează independent pentru verificare.
- Ambele depozite sunt găzduite pe Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 13. Autor și suport

- **Autor**: un programator chinez (dezvoltator independent)
- **Email**: `hustxiayuhong@126.com`
- **Suport tehnic**: vă rugăm să scrieți emailul **în chineză**, incluzând descrierea problemei, mediul de rulare (JDK / MySQL / versiune) și pașii de reproducere

> Citesc și răspund la fiecare email. Rapoartele de bug, cererile de funcționalități și solicitările de colaborare sunt toate binevenite.

# Majiang · Generator kodu (CodeGen) — Opis projektu

> Languages: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · **Polski** · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> Wieloprojektowy generator kodu dla **Spring Boot + Vue**: zaczynając od struktury tabel bazy danych, produkuje **kompilowalny, uruchamialny, gotowy do menu i uprawnień** kod biznesowy front-endu i back-endu, i obsługuje pełen cykl dostarczania: zapis na dysk, kopie zapasowe, księgowanie i wycofywanie.

GitHub: https://github.com/hustxia/CodeGen

> **aktualna wersja V9.1 · Refaktoryzacja pod kątem utrzymywalności · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9**
> **V9.0 · Generowane artefakty zmodernizowane do Vue 3 + Element Plus + Vite; zweryfikowane w czasie działania (40/40 asercji, udany vite build, 14 zrzutów ekranu incl. ar-SA RTL); naprawiono 3 defekty runtime**
> **V8.1** · Hardening jakości: offline harness kontraktu (9 trybów, zerowa różnica OFF) + testy jednostkowe i18n-core + bramka CI + uczciwy podział README (framework 206 / przetłumaczone 8) + check-locale-keys.mjs
> **V8.0** · i18n całej strony: framework 206 języków (8 przetłumaczonych profesjonalnie), dwutorowa i18n z kontraktem zerowej różnicy przy OFF

## 1. Czym to jest

CodeGen to „sterowana tabelami” fabryka kodu. Przynieś swoje tabele biznesowe (przez import bazy danych lub wklejając DDL), skonfiguruj pola i przełączniki generowania, a następnie kliknij „Generuj” — produkuje backendowe Entity/Mapper/Service/Controller/DTO/VO, frontendowy `api.js` i strony Vue, menu i trasy, a nawet strony druku A4 oraz szkielety testów integracyjnych, i bezpiecznie zapisuje je w projekcie docelowym.

To nie tylko „wypluwanie kodu” — to **potok dostarczania z bramkami jakości i możliwością wycofania**:

```
Table intake (DB import / DDL)
   → Field & switch configuration (with AI recommendations)
      → One-click generation: self-check → backup → write to disk → compile/validate → register menu → rebuild routes
         → Ledger bookkeeping → rollback-capable
```

---

## 2. Kto są użytkownikami i jak korzystają z platformy
| Użytkownik | Typowa potrzeba | Ścieżka, którą daje platforma |
| --- | --- | --- |
| Deweloper backendowy | Ma zestaw tabel (lub DDL) i chce szybko podnieść CRUD | Strona konfiguracji tabel: import → konfiguracja pól → generowanie jednym kliknięciem |
| Deweloper full-stack / niezależny | Chce od zera postawić cały system (z logowaniem, uprawnieniami, słownikami) | Strona potoku: zbuduj system jednym zdaniem |
| Kierownik zespołu | Chce ujednolicić styl kodu, standardy i strukturę katalogów | Zarządzanie systemem: jeden system = jedna konfiguracja wyjścia, zastosowanie wsadowe |
| Opiekun platformy | Chce zmieniać szablony, nie ruszając kodu | Zarządzanie szablonami: edycja online, zapis wchodzi w życie natychmiast, obsługiwane jest wycofywanie wersji |

## 3. Jakie problemy rozwiązuje

| Problem | Co robi CodeGen |
| --- | --- |
| Piekło powtarzalnego CRUD — stosy kodu szablonowego dla każdej tabeli | Przełączniki na poziomie tabeli produkują kompletny front-end i back-end jednym kliknięciem, włącznie z CRUD, paginacją, walidacją, importem/eksportem |
| Niezgodności pól i API front/back powodujące nieskończone poprawki integracyjne | Te same metadane tabeli napędzają zarówno API backendu, jak i strony front-endu — zerowy dryf semantyczny |
| Konwencje zespołu trudno ujednolicić; każdy ma inny styl | Szablony to konwencja: 22 szablony FreeMarker zarządzane centralnie, edytowalne online z wycofywaniem wersji |
| Generatory „nadpisują i tracą” ręcznie napisaną logikę | Mechanizm scalania bloków-znaczników: ręczny kod jest zachowywany, zastępowane są tylko wygenerowane obszary |
| Wygenerowany kod niebudzący zaufania aż do kompilacji | Samokontrola kompilacją w pamięci (prawdziwy `javac`), sprawdzenia strukturalne kontraktów, podgląd różnic na dysku — nic nie trafia na dysk, dopóki się nie skompiluje |
| Tworzenie nowego systemu od zera zajmuje dużo czasu | Potok „zbuduj system jednym zdaniem”: utwórz BD → szkielet → generuj → spakuj → zarejestruj menu, w pełni zautomatyzowane |

---

## 4. Krajobraz możliwości

- **Pobieranie tabel**: parsowanie DDL, bezpośredni import bazy danych, kreator wsadowego pobierania, automatycznie wyprowadzona konfiguracja kolumn (kontrolki / walidacja / słowniki / relacje).
- **Kształty tabel**: pojedyncza tabela, tabela drzewiasta (API drzewa + siatka drzewa), główna-szczegóły (zapis w tej samej transakcji + karty szczegółów), tabele łączące wiele-do-wielu.
- **Generowane artefakty**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + strony Vue + strony druku A4 + szkielety testów integracyjnych.
- **Rozszerzenia biznesowe**: listy rozwijane relacji, tłumaczenie słowników, maskowanie danych, sprawdzenia unikalności, powiązania formularzy, uprawnienia zakresu danych, import/eksport Excel, przepływ zatwierdzania, pakiety możliwości płatności / plików / mobilnych.
- **Bramki jakości**: samokontrola kompilacją w pamięci (prawdziwy `javac`), sprawdzenia strukturalne kontraktów, sprawdzenia spójności uprawnień, podgląd różnic na dysku, rzeczywista walidacja artefaktów front-endu.
- **Bezpieczne lądowanie**: zapis na dysk + wsadowe kopie `.gen-backup`, scalanie bloków-znaczników w celu zachowania ręcznego kodu, czterostanowa księga `gen_output` (utworzono / scalono / pominięto / nieudane), historyczne wycofywanie.
- **Izolacja wielu systemów**: każdy projekt docelowy to „konfiguracja systemu” (ścieżki front/back, nazwy pakietów, prefiks bramy, ścieżka klas zależności, domyślne przełączniki generowania) — wiele projektów obsługiwanych równolegle.
- **Rozszerzenia AI**: zbuduj system jednym zdaniem (modelowanie AI), wzbogacanie danych nasiennych przez AI, rekomendowana konfiguracja na poziomie tabeli (podgląd różnic + zastosowanie z zaznaczeniem).

---

## 5. Typowe scenariusze

| Scenariusz | Opis |
| --- | --- |
| Tabela drzewiasta | Dane hierarchiczne, takie jak kategorie produktów — automatycznie wyprodukowane API drzewa, siatka drzewa i lista rozwijana wyboru rodzica |
| CRUD pojedynczej tabeli | Klienci, produkty itp. — listy rozwijane kluczy obcych + tłumaczenie słowników + import/eksport out-of-the-box |
| Główna-szczegóły | Zamówienia sprzedaży + pozycje zamówień — zapis w tej samej transakcji, szuflada szczegółów z dwiema kartami (informacje główne / szczegóły) |
| Tylko do odczytu / statystyki | Przepływy zapasów itp. — przełączniki zapisu wyłączone, tylko zapytanie i eksport |
| Drukowanie dokumentów | Zaznacz przełącznik „druk”, aby wyprodukować stronę druku A4 i trasę z nagłówkiem + tabelą szczegółów |
| Budowa systemu jednym zdaniem | Opisz domenę biznesową — AI tworzy BD i tabele + szkielet + pełne generowanie + rejestrację menu za jednym razem |

> Dla wszystkich scenariuszy dostępne są uruchamialne dema (`demo_admin` projekt) ze zrzutami ekranu — zob. `../codegen-server/docs/典型场景操作指南-演示后台demo.md` (chiński).

---

## 6. Główny przepływ pracy

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Table intake │ → │ Field/switch conf. │ → │ One-click generate (self-check→backup→write→     │ → │ Ledger · Rollback │
│              │   │                    │   │ compile→menu→route)                              │   │                   │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     controls/validation     Lands on disk only after all quality gates pass;      Batch-level traceability
                    /dictionaries           hand-written code is preserved
```

- **Podgląd różnic**: wygenerowane artefakty są porównywane plik po pliku z obecnym stanem na dysku; potwierdzasz, zanim cokolwiek zostanie zapisane, unikając przypadkowych nadpisań.
- **Księga generowania**: każdy plik rejestruje swoją akcję, liczbę bajtów, MD5 i lokalizację kopii; cała partia może zostać wycofana.

---

## 7. Zbuduj system jednym zdaniem (Potok)

Możliwość wysokiego poziomu ukierunkowana na „tworzenie backendu od zera”. Po opisaniu domeny biznesowej potok automatycznie przechodzi przez 5 etapów:

| Etap | Znaczenie |
| --- | --- |
| `CHECK` | Sprawdź konfigurację projektu docelowego i ścieżkę klas zależności |
| `WRITE` | Utwórz bazę danych i tabele, zapisz szkielet i wygenerowane artefakty |
| `COMPILE` | Backendowe `mvn package` / budowa front-endu, samokontrola kompilacją w pamięci |
| `MENU` | Wyprodukuj i zarejestruj uprawnienia menu (dwa wyjścia: SQL artefaktu / bezpośredni zapis wewnętrzny) |
| `ROUTE` | Przebuduj fragmenty tras front-endu |

Awaria jakiegokolwiek etapu natychmiast przerywa z czytelnym błędem ułatwiającym lokalizację.

---

## 8. Architektura systemu

Front-end i back-end rozdzielone, dwa repozytoria współpracujące:

| Repozytorium | Rola | Stos |
| --- | --- | --- |
| `codegen-server` | Backend: silnik generowania, szablony, walidacja, księga, potok | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Frontend: konsola (konfiguracja tabel / szablony / księga / zarządzanie systemem) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (tryb history) |

Warstwowanie backendu:

```
controller/    API layer (Gen* generation chain / Sys* system management / pipeline)
service/impl/  Core implementations (generation chain, template engine, merge, validation, pipeline execution)
util/          Template engine, model assembly, marker-block merge, self-check compilation
config/        Output config, self-check config, template seeder
common/        Result / BaseEntity / exceptions / audit
resources/gen-templates/   22 FreeMarker templates (backend / frontend / shared / test groups)
```

**Podstawowe możliwości, które musi zapewnić projekt docelowy** (generator produkuje tylko kod biznesowy; brakująca podstawa kończy się cicho niepowodzeniem):

1. Wtyczka paginacji MyBatis-Plus (`PaginationInnerInterceptor`)
2. Klasy podstawowe takie jak `Result` / `BaseEntity` / `PageQuery` (prefiks pakietu musi pasować do `root-package`)
3. Frontendowe `utils/request.js` z `toPageData` (konwertuje `Page{records,total}` na `{list,total}`)
4. Zależności `springdoc-openapi-starter-webmvc-ui` (wygenerowany kod używa `@Operation`) oraz `poi-ooxml` (Excel)

---

## 9. Przegląd stosu technologicznego

| Wymiar | Wybór |
| --- | --- |
| Język / framework backendu | Java 21 / Spring Boot 3.2.5 |
| Persystencja | MyBatis-Plus 3.5.5 |
| Silnik szablonów | FreeMarker (22 szablonów nasiennych, edycja online + wycofywanie wersji) |
| Bezpieczeństwo | Uwierzytelnianie JWT |
| Baza danych | MySQL 8 |
| Frontend (generated projects) | Vue 3 + Element Plus + Vite |
| Frontend (platform console) | Vue 2.7 + Element UI |
| Lokalne AI | qwen2.5-coder:7b (modelowanie / rekomendowana konfiguracja / wzbogacanie danych nasiennych) |
| Dokumentacja API | springdoc-openapi (Swagger UI) |

---

## 10. Szybki start

### 1. Utwórz bazę danych (13 tabel platformy + domyślne konto admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Skonfiguruj projekt docelowy

Skopiuj szablon konfiguracji i dostosuj do swoich lokalnych ścieżek:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` jest wykluczony przez `.gitignore` i nie zostanie zatwierdzony. Kluczowe ustawienia:

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

Wyeksportuj katalog zależności do samokontroli tak:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Uruchom backend

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

Usługa domyślnie używa portu **8120**; dokumentacja API pod `/swagger-ui/index.html`.

### 4. Uruchom frontend

```bash
cd ../codegen-web
npm install
npm run serve
```

Frontend domyślnie używa portu **9530**, proxyfikując `/dev-api` do backendowego portu 8120. Domyślne konto `admin / admin123`.

### 5. Kluczowe elementy konfiguracji

| Ustawienie | Opis |
| --- | --- |
| `gen.output.*` | Ścieżki zapisu artefaktów, nazwy pakietów, mapowanie modułów |
| `gen.selfcheck.classpath` | Ścieżka klas zależności do samokontroli kompilacją w pamięci, obsługuje `dir/*` |
| `gen.menu.outlet` | `artifact` produkuje tylko SQL menu i fragmenty tras; `internal` zapisuje też własne `sys_menu` platformy |

---

## 11. Mapa dokumentacji

| Dokument | Zawartość |
| --- | --- |
| `../codegen-server/docs/代码生成器-操作手册.md` (chiński) | Wdrożenie, start w 5 minut, konfiguracja tabel, wsadowe pobieranie, zarządzanie szablonami, księga, katalog API, pełny potok (ze zrzutami ekranu) |
| `../codegen-server/docs/典型场景操作指南-演示后台demo.md` (chiński) | 6 tabel pokrywających 5 typowych scenariuszy, z uruchamialnym demo i zrzutami rzeczywistego działania |
| `项目介绍.md` | Dokument wprowadzający do projektu, to samo źródło co ten plik |
| `../codegen-server/docs/代码生成器-Vx.x-规划.md` | Plany wersji i inwentarze funkcji od V3.1 → V9.1 |
| `../codegen-web/README.md` | Referencyjny szybki start konsoli frontendu |

---

## 12. Historia wersji

| Wersja | Temat / dokument planu | Commit wdrożenia |
| --- | --- | --- |
| V1.2 / V1.3 | Generowanie jednym kliknięciem / wsadowe / wycofywanie; podgląd różnic i synchronizacja struktury | — (tylko w komentarzach `src/api/gen.js`) |
| V2.0 | Dynamiczne trasy (drzewo menu dostarczane przez backend) | — |
| V3.0 / V3.1 | Księga wielu systemów; pętla wielu systemów + pierwsze zautomatyzowane testy | V3.1 (174 linie) |
| V4.2 | Możliwe do prześledzenia, wycofywalne wyniki generowania (księga zapisu) | V4.2 (292 linie) |
| V4.3 | Regresja szablonów i zabezpieczenia jakości | V4.3 (200 linii) |
| V4.4 | Jeden-główny-wiele-szczegółów, kompletność zapisu kaskadowego, pasy szablonów | V4.4 (154 linie) |
| V4.5 | Dokumenty druku A4 (zero zależności zewnętrznych) | V4.5 (127 linii) |
| V5.0 | Budowa systemu jednym zdaniem: potok + modelowanie AI + baza szkieletu | — |
| V5.1 | Autouzupełnianie kolumn audytu DDL | V5.1 (100 linii) |
| V5.2 | Krok inicjalizacji danych nasiennych potoku | V5.2 (107 linii), 84ec8dd |
| V5.5 | Zwieńczenie generowania projektu wielomodułowego | af73dd3 |
| V6.0 | Infrastruktura mikrousług (Nacos / Gateway / Feign, trzeci tryb „micro”) | V6.0 (111 linii), 1deecbd |
| V6.1 | Podwójny silnik przepływu pracy Activiti | V6.1 (100 linii), 02b671e |
| V6.2 | Powierzchnia dostarczania artefaktów (CLI / artefakty wdrożeniowe / bezpieczeństwo konfiguracji) | V6.2 (114 linii), 02b671e |
| V6.3 | Pakiet możliwości płatności (WeChat / Alipay + Mock) | V6.3 (104 linie), 1c4ee6a, 22eede5 |
| V6.4 | Baza uprawnień i bezpieczeństwa (trwałość tokena / BCrypt / uprawnienia API / ochrona logowania) | V6.4 (107 linii), 007a202 |
| V6.5 | Mobilne uni-app (H5 + WeChat Mini Program) | V6.5 (105 linii), 779ac41 |
| V6.6 | Pakiet możliwości plików | 98f3c09 |
| V6.7 | Akceptacja fabryki testów artefaktów (trzystanowy test_enabled + szkielet H2) | V6.7 (134 linie), 4e7d318 |
| V6.8 | Możliwość utrzymania artefaktów (dryRun / chronione pomijanie / parametryzacja marki) | V6.8 (141 linii), be6f858 |
| V6.9 | Centrum dokumentacji platformy (baza backendu, frontend ukończony w tej samej partii) | — |
| V6.10 | Obserwowalność platformy: Actuator + okno metryk w pamięci + wbudowana strona monitora | — |
| V7.0 | Rekomendacje stron i menu przez AI (podgląd / zastosowanie) | V7.0 (94 linie), 45f2670 |
| V7.1 | Wzbogacanie danych nasiennych przez AI (AI posiada semantykę, reguły strukturę) | V7.1 (42 linie), 5ca6e72 |
| V7.2 | Deliverable SQL zapisywane do `db/` po udanym potoku | eebff88 |
| **V7.3** | **Wiarygodność dostarczania: testy artefaktów skompilowanych przez samokontrolę / ujednolicony wyjście deliverable SQL / rekomendacje AI na poziomie tabeli** | **V7.3 (138 linii), 696765b** |
| **V7.4** | Powtarzalne dostarczanie: wdrażalne z pustej bazy danych, każdy commit gwarantowanie uruchamialny | c306e29 |
| **V7.5** | **Konfiguracja językowa projektu: lista 206 języków / język domyślny / włączone języki, wstrzykiwanie lang i dir do generowanych plików** | 7a9afa0 + abac56d |
| **V8.0** | **i18n całej strony: framework 206 języków (8 przetłumaczonych profesjonalnie), dwutorowa i18n z kontraktem zerowej różnicy przy OFF** | 9e5dd57 → 7d57aea |
| **V8.1** | **Hardening jakości: offline harness kontraktu (9 trybów, zerowa różnica OFF) + testy jednostkowe i18n-core + bramka CI + uczciwy podział README (framework 206 / przetłumaczone 8) + check-locale-keys.mjs** | 93a12d5 + e49330b |
| **V9.0** | **Generowane artefakty zmodernizowane do Vue 3 + Element Plus + Vite; zweryfikowane w czasie działania (40/40 asercji, udany vite build, 14 zrzutów ekranu incl. ar-SA RTL); naprawiono 3 defekty runtime** | 93a12d5 + e49330b  |
| **V9.1** | **Refaktoryzacja pod kątem utrzymywalności · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9** | 654b8e0 → b0d081e, **aktualna wersja** |

---

> **V6.10 Obserwowalność platformy**: integruje Actuator + Micrometer, próbkując co 5 sekund do bufora pierścieniowego w pamięci (okno 30 minut), plus nowa wbudowana strona „Monitor systemu” — API agregacji backendu `/monitor/*` + panele ECharts frontendu. Na białej liście są tylko punkty końcowe `health/info/metrics`; `/actuator/health` jest bez tokena, podczas gdy `metrics` itp. wymagają uwierzytelnienia; `env/beans/heapdump/threaddump/loggers` nigdy nie są eksponowane. Okno próbkowania jest tylko na poziomie procesu, tracone po restarcie i nie współdzielone między instancjami. Szczegóły zob. `../codegen-server/docs/可观测性说明.md` (chiński).

## 13. Status projektu

- Obecna planowana linia wersji osiągnęła **V9.1** (możliwości stale iterują: modelowanie AI, wzbogacanie danych nasiennych, system pakietów możliwości itp.).
- Towarzyszący projekt demo `demo_admin` (backend 8140 / frontend 9040) obejmuje scenariusze drzewo / CRUD / główna-szczegóły / tylko-do-odczytu / druk i działa samodzielnie do weryfikacji.
- Oba repozytoria są hostowane na Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. Autor i wsparcie

- **Autor**: chiński programista (niezależny deweloper)
- **E-mail**: `hustxiayuhong@126.com`
- **Wsparcie techniczne**: prosimy pisać e-maila **w języku chińskim**, zawierając opis problemu, środowisko uruchomieniowe (JDK / MySQL / wersja) oraz kroki reprodukcji

> Czytam i odpowiadam na każdego e-maila. Zgłoszenia błędów, prośby o funkcje i zapytania o współpracę są mile widziane.

# Majiang · Генератор на код (CodeGen) — Представяне на проекта

> Languages: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · **Български** · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> Многопроектен генератор на код за **Spring Boot + Vue**: започвайки от структурите на таблиците в базата данни, създава фронт-енд и бек-енд бизнес код, който е **компилируем, изпълним и готов за менюта и права**, и поема целия цикъл на доставка: запис на диск, резервно копие, отчитане (ledger) и възстановяване (rollback).

GitHub: https://github.com/hustxia/CodeGen

## 1. Какво е това

CodeGen е „таблично управлявана“ фабрика за код. Донесете своите бизнес таблици (чрез импорт от базата данни или чрез поставяне на DDL), конфигурирайте полетата и превключвателите за генериране, след това натиснете „Generate“ — той създава бек-енд Entity/Mapper/Service/Controller/DTO/VO, фронт-енд `api.js` и Vue страници, менюта и маршрути, дори A4 страници за печат и скелети за тестове за интеграция, и ги записва безопасно в целевия проект.

Това не е просто „изплюване на код“ — това е **конвейер за доставка с порти за качество и възможност за rollback**:

```
Table intake (DB import / DDL)
   → Field & switch configuration (with AI recommendations)
      → One-click generation: self-check → backup → write to disk → compile/validate → register menu → rebuild routes
         → Ledger bookkeeping → rollback-capable
```

---

## 2. Кои са потребителите и как използват платформата
| Потребител | Типична потребност | Пътят, който предоставя платформата |
| --- | --- | --- |
| Бек-енд разработчик | Има набор от таблици (или DDL) и иска бързо да вдигне CRUD | Страница за конфигурация на таблици: импорт → конфигуриране на полетата → генериране с един клик |
| Full-stack / независим разработчик | Иска от нулата да изгради цяла система (с логин, права, речници) | Страница на конвейера: изградете система с едно изречение |
| Ръководител на екип | Иска да унифицира стила на кода, стандартите и структурата на директориите | Управление на системата: една система = една конфигурация на изхода, пакетно прилагане |
| Поддръжник на платформата | Иска да променя шаблоните, без да пипа кода | Управление на шаблоните: онлайн редактиране, записът влиза в сила веднага, поддържа се възстановяване на версии |

## 3. Какви проблеми решава

| Болно място (Pain point) | Какво прави CodeGen |
| --- | --- |
| Адът от повтарящ се CRUD — купчина шаблонен код за всяка таблица | Превключвателите за всяка таблица създават пълен фронт-енд и бек-енд с един клик, включително CRUD, странициране, валидация, импорт/експорт |
| Несъответствия между полета и API на фронт/бек, водещи до безкрайни поправки по интеграцията | Едни и същи метаданни на таблицата управляват както бек-енд API-тата, така и фронт-енд страниците — нулево семантично отклонение |
| Отборните конвенции трудно се унифицират; стилът на всеки е различен | Шаблоните са конвенцията: 22 FreeMarker шаблона, управлявани централно, редактируеми онлайн с възстановяване на версии |
| Генераторите „презаписват и губят“ ръчно написана логика | Механизъм за сливане с маркер-блок: ръчно написаният код се запазва, заменят се само генерираните региони |
| Генерираният код не е надежден до момента на компилация | Самопроверка на компилацията в паметта (истински `javac`), структурни проверки на договора, преглед на diff на диска — нищо не стъпва на диска, освен ако не се компилира |
| Създаването на нова система от нулата отнема време | Конвейер „изгради система с едно изречение“: създай БД → scaffold → генерирай → пакетирай → регистрирай менюта, напълно автоматизирано |

---

## 4. Обхват на възможностите

- **Поемане на таблици (Table intake)**: парсване на DDL, директен импорт от база данни, помощник за пакетно поемане, автоматично извлечена конфигурация на колоните (контроли / валидация / речници / релации).
- **Форми на таблици**: единична таблица, дървовидна таблица (дървовидно API + дървовидна мрежа), главен-детайл (запис в една транзакция + табове за детайли), таблици за връзка many-to-many.
- **Генерирани артефакти**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue страници + A4 страници за печат + скелети за тестове за интеграция.
- **Бизнес подобрения**: падащи менюта за релации, превод чрез речник, маскиране на данни, проверки за уникалност, връзка между формуляри, права за обхват на данни, импорт/експорт на Excel, работен процес за одобрение, пакети с възможности за плащане / файлове / мобилни.
- **Порти за качество**: самопроверка на компилацията в паметта (истински `javac`), структурни проверки на договора, проверки за съгласуваност на правата, преглед на diff на диска, реална валидация на фронт-енд артефакти.
- **Безопасно кацане**: запис на диск + пакетни резервни копия `.gen-backup`, сливане с маркер-блок за запазване на ръчно написания код, ledger в четири състояния `gen_output` (created / merged / skipped / failed), историческо възстановяване.
- **Многoсистемна изолация**: всеки целеви проект е „системна конфигурация“ (пътища за фронт/бек, имена на пакети, префикс на шлюза, classpath на зависимостите, стойности по подразбиране за превключвателите за генериране) — няколко проекта се обслужват паралелно.
- **AI подобрения**: изгради система с едно изречение (AI моделиране), AI подобрение на seed данни, AI препоръчана конфигурация за всяка таблица (преглед на diff + отметнато прилагане).

---

## 5. Типични сценарии

| Сценарий | Описание |
| --- | --- |
| Дървовидна таблица | Йерархични данни като категории продукти — автоматично създадено дървовидно API, дървовидна мрежа и падащо меню за избор на родител |
| CRUD за единична таблица | Клиенти, продукти и т.н. — падащи менюта за чужди ключове + превод чрез речник + импорт/експорт от кутията |
| Главен-детайл | Продажби + артикули на поръчката — запис в една транзакция, чекмедже за детайли с двойни табове (инфо за главния / детайли) |
| Само четене / статистики | Потони на инвентара и т.н. — превключвателите за запис са изключени, само заявка и експорт |
| Документи за печат | Поставете отметка на превключвателя „print“, за да се създаде A4 страница за печат и маршрут, с антетка + таблица с детайли |
| Изграждане на система с едно изречение | Опишете бизнес домейна — AI създава БД и таблици + scaffold + пълно генериране + регистрация на менюта наведнъж |

> Всички сценарии имат работещи демонстрации (проектът `demo_admin`) с екранни снимки — вижте `docs/典型场景操作指南-演示后台demo.md` (на китайски).

---

## 6. Основен работен процес

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Table intake │ → │ Field/switch conf. │ → │ One-click generate (self-check→backup→write→     │ → │ Ledger · Rollback │
│              │   │                    │   │ compile→menu→route)                              │   │                   │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     controls/validation     Lands on disk only after all quality gates pass;      Batch-level traceability
                    /dictionaries           hand-written code is preserved
```

- **Преглед на diff**: генерираните артефакти се сравняват (diff) файл по файл спрямо текущото състояние на диска; потвърждавате, преди да бъде записано нещо, избягвайки случайни презаписвания.
- **Ledger на генерирането**: всеки файл записва своето действие, брой байтове, MD5 и местоположение на резервното копие; цял партид може да бъде възстановен (rollback).

---

## 7. Изградете система с едно изречение (Pipeline)

Възможност от високо ниво, насочена към „създаване на бек-енд от нулата“. След като опишете бизнес домейна, конвейерът напредва автоматично през 5 етапа:

| Етап (Stage) | Значение (Meaning) |
| --- | --- |
| `CHECK` | Валидира конфигурацията на целевия проект и classpath на зависимостите |
| `WRITE` | Създава база данни и таблици, записва scaffold и генерирани артефакти |
| `COMPILE` | Бек-енд `mvn package` / фронт-енд билд, самопроверка на компилацията в паметта |
| `MENU` | Създава и регистрира права за менюта (два изхода: SQL артефакт / директен вътрешен запис) |
| `ROUTE` | Преизгражда фронт-енд фрагментите за маршрути |

Всяка повреда на етап спира незабавно с четима грешка за лесно откриване.

---

## 8. Архитектура на системата

Фронт-енд и бек-енд са разделени, две хранилища работят заедно:

| Хранилище (Repository) | Роля | Стек (Stack) |
| --- | --- | --- |
| `codegen-server` | Бек-енд: двигател за генериране, шаблони, валидация, ledger, конвейер | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Фронт-енд: конзола (конфиг таблица / шаблони / ledger / управление на системата) | Vue 2.7 · Element UI · axios · Vue Router (history mode) |

Слойна структура на бек-енда:

```
controller/    API layer (Gen* generation chain / Sys* system management / pipeline)
service/impl/  Core implementations (generation chain, template engine, merge, validation, pipeline execution)
util/          Template engine, model assembly, marker-block merge, self-check compilation
config/        Output config, self-check config, template seeder
common/        Result / BaseEntity / exceptions / audit
resources/gen-templates/   22 FreeMarker templates (backend / frontend / shared / test groups)
```

**Основни възможности (foundation), които целевият проект трябва да предостави** (генераторът създава само бизнес код; липсваща основа се проваля безшумно):

1. Плъгин за странициране на MyBatis-Plus (`PaginationInnerInterceptor`)
2. Основни класове като `Result` / `BaseEntity` / `PageQuery` (префиксът на пакета трябва да съвпада с `root-package`)
3. Фронт-енд `utils/request.js` с `toPageData` (преобразува `Page{records,total}` в `{list,total}`)
4. Зависимостите `springdoc-openapi-starter-webmvc-ui` (генерираният код използва `@Operation`) и `poi-ooxml` (Excel)

---

## 9. Технологичен стек накратко

| Измерение | Избор |
| --- | --- |
| Език / рамка за бек-енд | Java 21 / Spring Boot 3.2.5 |
| Персистентност | MyBatis-Plus 3.5.5 |
| Двигател за шаблони | FreeMarker (22 seed шаблона, онлайн редактиране + възстановяване на версии) |
| Сигурност | JWT автентикация |
| База данни | MySQL 8 |
| Фронт-енд | Vue 2.7 + Element UI |
| Локален AI | qwen2.5-coder:7b (моделиране / препоръчана конфигурация / подобрение на seed данни) |
| API документация | springdoc-openapi (Swagger UI) |

---

## 10. Бърз старт

### 1. Създайте базата данни (13 платформени таблици + профил по подразбиране admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Конфигурирайте целевия проект

Копирайте шаблона за конфигурация и го настройте спрямо локалните си пътища:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` е изключен от `.gitignore` и няма да бъде комитнат. Ключови настройки:

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

Експортирайте директорията с зависимости за самопроверка така:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Стартирайте бек-енда

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

Услугата използва порт **8120** по подразбиране; API документацията е на `/swagger-ui/index.html`.

### 4. Стартирайте фронт-енда

```bash
cd ../codegen-web
npm install
npm run serve
```

Фронт-ендът използва порт **9530** по подразбиране, проксирайки `/dev-api` към бек-енд порт 8120. Профил по подразбиране `admin / admin123`.

### 5. Ключови елементи за конфигурация

| Настройка | Описание |
| --- | --- |
| `gen.output.*` | Пътища за запис на артефакти, имена на пакети, mapping на модули |
| `gen.selfcheck.classpath` | Classpath на зависимостите за самопроверка на компилацията в паметта, поддържа `dir/*` |
| `gen.menu.outlet` | `artifact` създава само SQL за менюта и фрагменти за маршрути; `internal` записва и собственото `sys_menu` на платформата |

---

## 11. Карта на документацията

| Документ | Съдържание |
| --- | --- |
| `docs/代码生成器-操作手册.md` (на китайски) | Внедряване, старт за 5 минути, конфиг таблица, пакетно поемане, управление на шаблони, ledger, каталог с API, пълен конвейер (с екранни снимки) |
| `docs/典型场景操作指南-演示后台demo.md` (на китайски) | 6 таблици, покриващи 5 типични сценария, с работещо демо и екранни снимки от реално изпълнение |
| `docs/项目介绍.md` | Документ за представяне на проекта, същият източник като този файл |
| `docs/代码生成器-Vx.x-规划.md` | Планове за версии и описи на функционалности от V3.1 → V7.3 |
| `codegen-web/README.md` | Справка за бърз старт на фронт-енд конзолата |

---

## 12. История на версиите

| Версия | Тема / план-документ | Комит на внедряване |
| --- | --- | --- |
| V1.2 / V1.3 | Генериране / партида / rollback с един клик; преглед на diff и синхронизация на структурата | — (само в коментарите на `src/api/gen.js`) |
| V2.0 | Динамични маршрути (дърво от менюта, доставено от бек-енда) | — |
| V3.0 / V3.1 | Многoсистемен ledger; многoсистемен цикъл + първи автоматизирани тестове | V3.1 (174 реда) |
| V4.2 | Проследими, възстановими резултати от генерирането (запис в ledger) | V4.2 (292 реда) |
| V4.3 | Регресия на шаблоните & предпазни мерки за качество | V4.3 (200 реда) |
| V4.4 | Един-главен-много-детайли, пълнота на каскадния запис, ленти за шаблони | V4.4 (154 реда) |
| V4.5 | A4 документи за печат (нула външни зависимости) | V4.5 (127 реда) |
| V5.0 | Изграждане на система с едно изречение: конвейер + AI моделиране + базов scaffold | — |
| V5.1 | Автоматично допълване на одитни колони в DDL | V5.1 (100 реда) |
| V5.2 | Стъпка за инициализация на seed данни в конвейера | V5.2 (107 реда), 84ec8dd |
| V5.5 | Финализиране на генериране за многомодулни проекти | af73dd3 |
| V6.0 | Микросервизна инфраструктура (Nacos / Gateway / Feign, трети режим „micro“) | V6.0 (111 реда), 1deecbd |
| V6.1 | Двоен двигател на работен процес Activiti | V6.1 (100 реда), 02b671e |
| V6.2 | Повърхност за доставка на артефакти (CLI / артефакти за внедряване / сигурност на конфигурацията) | V6.2 (114 реда), 02b671e |
| V6.3 | Пакет възможности за плащане (WeChat / Alipay + Mock) | V6.3 (104 реда), 1c4ee6a, 22eede5 |
| V6.4 | Базова линия за права и сигурност (persistence на token / BCrypt / API права / защита на входа) | V6.4 (107 реда), 007a202 |
| V6.5 | Мобилен uni-app (H5 + WeChat Mini Program) | V6.5 (105 реда), 779ac41 |
| V6.6 | Пакет възможности за файлове | 98f3c09 |
| V6.7 | Приемане от тестова фабрика за артефакти (tri-state test_enabled + H2 скелет) | V6.7 (134 реда), 4e7d318 |
| V6.8 | Поддръжка на артефакти (dryRun / защитен skip / параметризация на марката) | V6.8 (141 реда), be6f858 |
| V6.9 | Център за документация на платформата (бек-енд база, фронт-енд завършен в същия партид) | — |
| V6.10 | Наблюдаемост на платформата: Actuator + прозорец за метрики в паметта + вградена страница за мониторинг | — |
| V7.0 | AI препоръки за страници и менюта (преглед / прилагане) | V7.0 (94 реда), 45f2670 |
| V7.1 | AI подобрение на seed данни (AI притежава семантиката, правилата притежават структурата) | V7.1 (42 реда), 5ca6e72 |
| V7.2 | SQL артефакти, записани в `db/` след успешен конвейер | eebff88 |
| **V7.3** | **Надеждност на доставката: тестове на компилирани артефакти със самопроверка / унифициран изход за доставка на SQL / AI препоръки за всяка таблица** | **V7.3 (138 реда)** |
| **V7.4** | Повторима доставка: внедряем от празна база данни, всеки комит гарантирано изпълним | c306e29 |
| **V7.5** | Езикова конфигурация на проекта: списък от 206 езика / език по подразбиране / включени езици, вмъкване на lang и dir в генерирания код | 7a9afa0 + abac56d |
| **V8.0** | i18n на цялата страница: рамка от 206 езика (8 професионално преведени), двупътна i18n с договор за нулева разлика при OFF | 9e5dd57 → 7d57aea |
| **V8.1** | Втвърдяване на качеството: офлайн стенд за контракт (9 режима, нулева разлика OFF) + unit тестове i18n-core + CI порта + честно разделяне в README (рамка 206 / преведени 8) + check-locale-keys.mjs | 93a12d5 + e49330b |
| **V9.0** | Генерираните артефакти модернизирани към Vue 3 + Element Plus + Vite; проверено по време на изпълнение (40/40 проверки, успешен vite build, 14 екранни снимки incl. ar-SA RTL); коригирани 3 runtime дефекта | 93a12d5 + e49330b  |
| **V9.1** | **Рефакторинг за по-лесна поддръжка · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9** | 654b8e0 → b0d081e, **текуща версия** |

---

> **V6.10 Наблюдаемост на платформата**: интегрира Actuator + Micrometer, семплирайки на всеки 5 секунди в пръстенов буфер в паметта (прозорец от 30 минути), плюс нова вградена страница „System Monitor“ — API за агрегиране на бек-енда `/monitor/*` + ECharts панели на фронт-енда. Само endpoint-ите `health/info/metrics` са в бял списък; `/actuator/health` е без token, докато `metrics` и др. изискват автентикация; `env/beans/heapdump/threaddump/loggers` никога не се разкриват. Прозорецът за семплиране е само за процес, губи се при рестарт и не се споделя между инстанции. Вижте `docs/可观测性说明.md` (на китайски) за подробности.

## 13. Състояние на проекта

- Текущата планирана линия на версиите е достигнала **V9.1** (възможностите продължават да се развиват: AI моделиране, подобрение на seed данни, система от пакети с възможности и т.н.).
- Придружаващ демо проект `demo_admin` (бек-енд 8140 / фронт-енд 9040) покрива сценариите дърво / CRUD / главен-детайл / само четене / печат и работи самостоятелно за проверка.
- И двете хранилища са хоствани в Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. Автор и поддръжка

- **Автор**: китайски програмист (независим разработчик)
- **Имейл**: `hustxiayuhong@126.com`
- **Техническа поддръжка**: моля, пишете имейла си **на китайски**, включвайки описание на проблема, среда за изпълнение (JDK / MySQL / версия) и стъпки за възпроизвеждане

> Чета и отговарям на всеки имейл. Докладите за грешки, заявките за функционалности и запитванията за сътрудничество са добре дошли.

---

## 15. Сравнение с подобни инструменти

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

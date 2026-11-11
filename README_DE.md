# Majiang · Code-Generator (CodeGen) — Projektvorstellung

> Languages: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · **Deutsch** · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

> Sprachen: [中文](README_ZH.md) · [English](README.md) · [日本語](README_JA.md) · [Português](README_PT.md) · [Español](README_ES.md) · [한국어](README_KO.md) · [Français](README_FR.md) · **Deutsch**

---

> Ein Multi-Projekt-Code-Generator für **Spring Boot + Vue**: Ausgehend von Datenbank-Tabellenstrukturen erzeugt er **kompilierbaren, lauffähigen** Frontend- und Backend-Businesscode mit Menü- und Rechteverwaltung, und übernimmt den kompletten Lieferzyklus aus Schreiben auf die Festplatte, Backup, Führung des Protokolls (Ledger) und Rollback.

GitHub: https://github.com/hustxia/CodeGen

## 1. Was ist das

CodeGen ist eine „tabellengetriebene“ Code-Fabrik. Sie bringen Ihre Geschäftstabellen herein (per Datenbank-Import oder durch Einfügen eines DDL-Skripts), konfigurieren Felder und Generierungsschalter und klicken auf „Generieren“ — daraufhin erzeugt er das Backend (Entity/Mapper/Service/Controller/DTO/VO), das Frontend (`api.js` und Vue-Seiten), Menüs und Routen, sogar A4-Druckseiten und Integration-Test-Skelette, und schreibt alles sicher in Ihr Zielprojekt.

Er „spuckt“ nicht nur Code aus, sondern ist eine **Liefer-Pipeline mit Qualitäts-Gates und Rollback-Fähigkeit**:

```
Tabellenaufnahme (DB-Import / DDL)
   → Feld- & Schalterkonfiguration (mit KI-Empfehlungen)
      → Ein-Klick-Generierung: Selbstprüfung → Backup → Auf die Platte schreiben → Kompilieren/Validieren → Menü registrieren → Routen neu aufbauen
         → Protokollbuchung → Rollback-fähig
```

---

## 2. Welche Probleme er löst

| Problem | Was CodeGen dagegen tut |
| --- | --- |
| Wiederholende CRUD-Hölle — ein Haufen Boilerplate für jede Tabelle | Tabellenbezogene Schalter erzeugen mit einem Klick ein komplettes Front- und Backend, inkl. CRUD, Paginierung, Validierung, Import/Export |
| Feld- und API-Diskrepanzen zwischen Front- und Backend führen zu endlosen Integrationsnacharbeiten | Dieselben Tabellenmetadaten treiben sowohl Backend-APIs als auch Frontend-Seiten an — null semantische Abweichung |
| Team-Konventionen schwer vereinheitlichbar; jeder schreibt in seinem eigenen Stil | Templates sind die Konvention: 22 FreeMarker-Templates zentral verwaltet, online editierbar mit Versions-Rollback |
| Generatoren „überschreiben und verlieren“ handgeschriebene Logik | Marker-Block-Merge-Mechanismus: handgeschriebener Code bleibt erhalten, nur generierte Bereiche werden ersetzt |
| Generierter Code ist bis zur Kompilierung nicht vertrauenswürdig | In-Memory-Kompilierungs-Selbstprüfung (echtes `javac`), strukturelle Vertragsprüfungen, Diff-Vorschau auf der Festplatte — nichts landet auf der Platte, solange es nicht kompiliert |
| Das Aufsetzen eines neuen Systems von null ist zeitraubend | Pipeline „Ein System mit einem Satz bauen“: DB anlegen → Scaffold → generieren → packen → Menüs registrieren, vollautomatisch |

---

## 3. Fähigkeiten im Überblick

- **Tabellenaufnahme**: DDL-Parsing, direkter Datenbank-Import, Batch-Aufnahme-Assistent, automatisch abgeleitete Spaltenkonfiguration (Steuerelemente / Validierung / Wörterbücher / Relationen).
- **Tabellenformen**: Einzeltabelle, Baumtabelle (Tree-API + Tree-Grid), Master-Detail (Speicherung in derselben Transaktion + Detail-Tabs), Many-to-Many-Junction-Tabellen.
- **Generierte Artefakte**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue-Seiten + A4-Druckseiten + Integration-Test-Skelette.
- **Business-Erweiterungen**: Relations-Dropdowns, Wörterbuch-Übersetzung, Datenmaskierung, Eindeutigkeitsprüfungen, Formular-Verknüpfung, Datenbereichs-Rechte, Excel-Import/Export, Genehmigungs-Workflow, Zahlungs- / Datei- / Mobile-Fähigkeitspakete.
- **Qualitäts-Gates**: In-Memory-Kompilierungs-Selbstprüfung (echtes `javac`), strukturelle Vertragsprüfungen, Rechte-Konsistenzprüfungen, Diff-Vorschau auf der Festplatte, echte Validierung der Frontend-Artefakte.
- **Sichere Landung**: Schreiben auf die Platte + `.gen-backup`-Batch-Backups, Marker-Block-Merge zur Erhaltung handgeschriebenen Codes, `gen_output`-Vier-Zustände-Protokoll (erstellt / zusammengeführt / übersprungen / fehlgeschlagen), historisches Rollback.
- **Multi-System-Isolation**: Jedes Zielprojekt ist eine „Systemkonfiguration“ (Front-/Backend-Pfade, Paketnamen, Gateway-Präfix, Abhängigkeits-Classpath, Standardwerte der Generierungsschalter) — mehrere Projekte werden parallel bedient.
- **KI-Erweiterungen**: Ein System mit einem Satz bauen (KI-Modellierung), Seed-Daten-KI-Verbesserung, tabellenbezogene KI-Empfehlungskonfiguration (Diff-Vorschau + ausgewählte Übernahme).

---

## 4. Typische Szenarien

| Szenario | Beschreibung |
| --- | --- |
| Baumtabelle | Hierarchische Daten wie Produktkategorien — automatisch erzeugte Tree-API, Tree-Grid und Auswahl-Dropdown für übergeordnete Elemente |
| Einzeltabellen-CRUD | Kunden, Produkte usw. — FK-Dropdowns + Wörterbuch-Übersetzung + Import/Export ab Werk |
| Master-Detail | Verkaufsaufträge + Auftragspositionen — Speicherung in derselben Transaktion, Detail-Drawer mit doppelten Tabs (Master-Infos / Details) |
| Nur-Lesen / Statistik | Bestandsbewegungen usw. — Schreibschalter aus, nur Abfrage und Export |
| Druckdokumente | Den „Drucken“-Schalter aktivieren, um eine A4-Druckseite und Route zu erzeugen, mit Kopfzeile + Detailtabelle |
| System mit einem Satz bauen | Das Business-Domäne beschreiben — KI legt DB & Tabellen an + Scaffold + vollständige Generierung + Menüregistrierung in einem Durchgang |

> Alle Szenarien haben lauffähige Demos (`demo_admin`-Projekt) mit Screenshots — siehe `docs/典型场景操作指南-演示后台demo.md` (Chinesisch).

---

## 5. Kern-Workflow

```
┌─────────────────┐   ┌───────────────────────┐   ┌──────────────────────────────────────────────────┐
│ Tabellenaufnahme │ → │ Feld-/Schalter-Konfig. │ → │ Ein-Klick-Generierung (Selbstprüfung→Backup→     │ → │ Protokoll · Rollback │
│                  │   │                        │   │ Schreiben→Kompilieren→Menü→Route)                │   │                      │
└─────────────────┘   └───────────────────────┘   └──────────────────────────────────────────────────┘
  DB-Import/DDL         Steuerelemente/Validierung  Landet nur nach bestandenen Qualitäts-Gates auf der    Batch-weite Nachverfolgbarkeit
                        /Wörterbücher               Platte; handgeschriebener Code bleibt erhalten
```

- **Diff-Vorschau**: generierte Artefakte werden Datei für Datei gegen den aktuellen Festplattenzustand diffed; Sie bestätigen, bevor etwas geschrieben wird — versehentliche Überschreibungen werden vermieden.
- **Generierungsprotokoll**: jede Datei protokolliert ihre Aktion, Byte-Anzahl, MD5 und Backup-Position; ein ganzer Batch kann zurückgerollt werden.

---

## 6. Ein System mit einem Satz bauen (Pipeline)

Eine High-Level-Fähigkeit, die auf „Aufsetzen eines Backends von Grund auf“ abzielt. Nachdem Sie die Business-Domäne beschrieben haben, schreitet die Pipeline automatisch durch 5 Phasen voran:

| Phase | Bedeutung |
| --- | --- |
| `CHECK` | Konfiguration des Zielprojekts und Abhängigkeits-Classpath validieren |
| `WRITE` | Datenbank & Tabellen anlegen, Scaffold und generierte Artefakte schreiben |
| `COMPILE` | Backend `mvn package` / Frontend-Build, In-Memory-Kompilierungs-Selbstprüfung |
| `MENU` | Menü-Rechte erzeugen und registrieren (zwei Ausgänge: Artefakt-SQL / internes Direchtschreiben) |
| `ROUTE` | Frontend-Routenfragmente neu aufbauen |

Jeder Phasenfehler bricht sofort mit einer lesbaren Fehlermeldung ab, um die Fehlersuche zu erleichtern.

---

## 7. Systemarchitektur

Getrenntes Front- und Backend, zwei Repositories, die zusammenarbeiten:

| Repository | Rolle | Stack |
| --- | --- | --- |
| `codegen-server` | Backend: Generierungs-Engine, Templates, Validierung, Protokoll, Pipeline | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Frontend: Konsole (Tabellenkonfiguration / Templates / Protokoll / Systemverwaltung) | Vue 2.7 · Element UI · axios · Vue Router (History-Mode) |

Backend-Schichtung:

```
controller/    API-Schicht (Gen*-Generierungskette / Sys*-Systemverwaltung / Pipeline)
service/impl/  Kernimplementierungen (Generierungskette, Template-Engine, Merge, Validierung, Pipeline-Ausführung)
util/          Template-Engine, Modell-Assembly, Marker-Block-Merge, Selbstprüfungs-Kompilierung
config/        Ausgabekonfiguration, Selbstprüfungs-Konfiguration, Template-Seeder
common/        Result / BaseEntity / Exceptions / Audit
resources/gen-templates/   22 FreeMarker-Templates (Gruppen: backend / frontend / shared / test)
```

**Basis-Fähigkeiten, die das Zielprojekt selbst mitbringen muss** (der Generator erzeugt nur Business-Code; eine fehlende Basis versagt stillschweigend):

1. MyBatis-Plus-Paginierungs-Plugin (`PaginationInnerInterceptor`)
2. Basis-Klassen wie `Result` / `BaseEntity` / `PageQuery` (Paketpräfix muss mit `root-package` übereinstimmen)
3. Frontend-`utils/request.js` mit `toPageData` (wandelt `Page{records,total}` in `{list,total}` um)
4. Abhängigkeiten `springdoc-openapi-starter-webmvc-ui` (generierter Code nutzt `@Operation`) und `poi-ooxml` (Excel)

---

## 8. Tech-Stack auf einen Blick

| Dimension | Wahl |
| --- | --- |
| Backend-Sprache / Framework | Java 21 / Spring Boot 3.2.5 |
| Persistenz | MyBatis-Plus 3.5.5 |
| Template-Engine | FreeMarker (22 Seed-Templates, Online-Bearbeitung + Versions-Rollback) |
| Sicherheit | JWT-Authentifizierung |
| Datenbank | MySQL 8 |
| Frontend | Vue 2.7 + Element UI |
| Lokale KI | qwen2.5-coder:7b (Modellierung / Empfehlungskonfiguration / Seed-Daten-Verbesserung) |
| API-Dokumentation | springdoc-openapi (Swagger UI) |

---

## 9. Schnellstart

### 1. Datenbank anlegen (13 Plattform-Tabellen + Standardkonto admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Zielprojekt konfigurieren

Konfigurationsvorlage kopieren und an die lokalen Pfade anpassen:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` ist in `.gitignore` ausgeschlossen und wird nicht committet. Zentrale Einstellungen:

```yaml
gen:
  output:
    backend-root: /path/to/your-target-project      # Basispfad für das Schreiben der Artefakte
    frontend-root: /path/to/your-target-project
    frontend-path: demo-web                          # Frontend-Unterverzeichnis bei Monorepos
    backend-path: demo-server/src/main/java/com/demo
    package-name: com.demo
    root-package: com.demo                           # Präfix für Basis-Klassen-Imports in Artefakten
  selfcheck:
    classpath: /path/to/your-target-project/demo-lib/*
```

Das Selbstprüfungs-Abhängigkeitsverzeichnis wird so exportiert:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Backend starten

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

Der Dienst läuft standardmäßig auf Port **8120**; API-Dokumentation unter `/swagger-ui/index.html`.

### 4. Frontend starten

```bash
cd ../codegen-web
npm install
npm run serve
```

Das Frontend läuft standardmäßig auf Port **9530** und proxyt `/dev-api` an den Backend-Port 8120. Standardkonto `admin / admin123`.

### 5. Zentrale Konfigurationselemente

| Einstellung | Beschreibung |
| --- | --- |
| `gen.output.*` | Schreibpfade der Artefakte, Paketnamen, Modul-Mapping |
| `gen.selfcheck.classpath` | Abhängigkeits-Classpath für die In-Memory-Kompilierungs-Selbstprüfung, unterstützt `dir/*` |
| `gen.menu.outlet` | `artifact` erzeugt nur Menü-SQL und Routenfragmente; `internal` schreibt zusätzlich in die eigene `sys_menu`-Tabelle der Plattform |

---

## 10. Dokumentations-Navigation

| Dokument | Inhalt |
| --- | --- |
| `docs/代码生成器-操作手册.md` (Chinesisch) | Deployment, 5-Minuten-Einstieg, Tabellenkonfiguration, Batch-Aufnahme, Template-Verwaltung, Protokoll, API-Katalog, komplette Pipeline (mit Screenshots) |
| `docs/典型场景操作指南-演示后台demo.md` (Chinesisch) | 6 Tabellen, die 5 typische Szenarien abdecken, mit lauffähiger Demo und Real-Run-Screenshots |
| `docs/项目介绍.md` (Chinesisch) | Projektvorstellungsdokument, gleiche Quelle wie diese Datei |
| `docs/代码生成器-Vx.x-规划.md` (Chinesisch) | Versionspläne und Feature-Inventare von V3.1 → V7.3 |
| `codegen-web/README.md` | Schnellstart-Referenz für die Frontend-Konsole |

---

## 11. Versionshistorie

| Version | Thema / Planungsdokument | Umsetzungs-Commit |
| --- | --- | --- |
| V1.2 / V1.3 | Ein-Klick-Generierung / Batch / Rollback; Diff-Vorschau & Struktur-Sync | — (nur in Kommentaren von `src/api/gen.js`) |
| V2.0 | Dynamische Routen (Backend-gelieferter Menübaum) | — |
| V3.0 / V3.1 | Multi-System-Protokoll; Multi-System-Loop + erste automatisierte Tests | V3.1 (174 Zeilen) |
| V4.2 | Nachverfolgbare, rückrollbare Generierungsergebnisse (Schreib-Protokoll) | V4.2 (292 Zeilen) |
| V4.3 | Template-Regression & Qualitäts-Schutzzäune | V4.3 (200 Zeilen) |
| V4.4 | Ein-Master-viele-Detail, Kaskadenschreib-Vervollständigung, Template-Spuren | V4.4 (154 Zeilen) |
| V4.5 | A4-Druckdokumente (null Drittanbieter-Abhängigkeiten) | V4.5 (127 Zeilen) |
| V5.0 | System mit einem Satz bauen: Pipeline + KI-Modellierung + Scaffold-Basis | — |
| V5.1 | Auto-Vervollständigung der DDL-Audit-Spalten | V5.1 (100 Zeilen) |
| V5.2 | Pipeline-Schritt zur Seed-Daten-Initialisierung | V5.2 (107 Zeilen), 84ec8dd |
| V5.5 | Abschluss der Multi-Modul-Projekt-Generierung | af73dd3 |
| V6.0 | Microservice-Infrastruktur (Nacos / Gateway / Feign, dritter Modus „micro“) | V6.0 (111 Zeilen), 1deecbd |
| V6.1 | Activiti-Workflow-Dual-Engine | V6.1 (100 Zeilen), 02b671e |
| V6.2 | Artefakt-Lieferfläche (CLI / Deployment-Artefakte / Konfigurationssicherheit) | V6.2 (114 Zeilen), 02b671e |
| V6.3 | Zahlungs-Fähigkeitspaket (WeChat / Alipay + Mock) | V6.3 (104 Zeilen), 1c4ee6a, 22eede5 |
| V6.4 | Rechte- & Sicherheits-Baseline (Token-Persistenz / BCrypt / API-Rechte / Login-Schutz) | V6.4 (107 Zeilen), 007a202 |
| V6.5 | Mobile uni-app (H5 + WeChat Mini Program) | V6.5 (105 Zeilen), 779ac41 |
| V6.6 | Datei-Fähigkeitspaket | 98f3c09 |
| V6.7 | Artefakt-Test-Abnahme (test_enabled-Dreizustand + H2-Skelett) | V6.7 (134 Zeilen), 4e7d318 |
| V6.8 | Artefakt-Wartbarkeit (dryRun / geschütztes Überspringen / Branding-Parametrisierung) | V6.8 (141 Zeilen), be6f858 |
| V6.9 | Plattform-Dokumentationszentrum (Backend-Basis, Frontend-Abschluss im selben Batch) | — |
| V6.10 | Plattform-Observability: Actuator + In-Memory-Metrik-Fenster + eingebettete Monitoring-Seite | — |
| V7.0 | KI-Seiten- & Menü-Empfehlungen (preview / apply) | V7.0 (94 Zeilen), 45f2670 |
| V7.1 | Seed-Daten-KI-Verbesserung (KI besitzt Semantik, Regeln besitzen Struktur) | V7.1 (42 Zeilen), 5ca6e72 |
| V7.2 | SQL-Lieferobjekte werden nach erfolgreicher Pipeline nach `db/` geschrieben | eebff88 |
| **V7.3** | **Liefer-Vertrauenswürdigkeit: Selbstprüfungs-kompilierte Artefakt-Tests / einheitlicher SQL-Lieferausgang / tabellenbezogene KI-Empfehlungen** | **V7.3 (138 Zeilen)** |
| **V7.4** | Wiederholbare Lieferung: aus leerer Datenbank deploybar, jeder Commit garantiert lauffähig | **Aktuelle Version** |

---

> **V6.10 Plattform-Observability**: integriert Actuator + Micrometer, Sampling alle 5 Sekunden in einen In-Memory-Ringpuffer (30-Minuten-Fenster), plus eine neue eingebettete „System-Monitoring“-Seite — Backend-`/monitor/*`-Aggregations-APIs + Frontend-ECharts-Panels. Nur die Endpunkte `health/info/metrics` sind auf der Whitelist; `/actuator/health` ist tokenfrei, während `metrics` usw. Authentifizierung benötigen; `env/beans/heapdump/threaddump/loggers` werden niemals freigegeben. Das Sampling-Fenster ist rein prozessintern, geht beim Neustart verloren und wird nicht über Instanzen hinweg geteilt. Details siehe `docs/可观测性说明.md` (Chinesisch).

## 12. Projektstatus

- Die aktuelle geplante Versionslinie hat **V7.3** erreicht (Fähigkeiten werden kontinuierlich weiterentwickelt: KI-Modellierung, Seed-Daten-Verbesserung, Fähigkeitspaket-System usw.).
- Ein begleitendes Demo-Projekt `demo_admin` (Backend 8140 / Frontend 9040) deckt die Szenarien Baum / CRUD / Master-Detail / Nur-Lesen / Drucken ab und läuft eigenständig zur Verifikation.
- Beide Repositories sind auf Gitee gehostet: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 13. Autor & Support

- **Autor**: chinesischer Programmierer (unabhängiger Entwickler)
- **E-Mail**: `hustxiayuhong@126.com`
- **Technischer Support**: Bitte schreiben Sie **auf Chinesisch** und nennen Sie Problembeschreibung, Umgebung (JDK / MySQL / Version) sowie Reproduktionsschritte

> Ich lese und beantworte jede E-Mail. Fehlerberichte, Funktionswünsche und Kooperationsanfragen sind willkommen.

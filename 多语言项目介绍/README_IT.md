# Majiang · Generatore di codice (CodeGen) — Introduzione al progetto

> Languages: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · **Italiano** · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> Un generatore di codice multi-progetto per **Spring Boot + Vue**: partendo dalla struttura delle tabelle del database, produce codice di business front-end e back-end **compilabile, eseguibile e pronto per menu e permessi**, e gestisce l'intero ciclo di consegna: scrittura su disco, backup, registrazione a libro mastro e rollback.

GitHub: https://github.com/hustxia/CodeGen

> **Versione attuale V9.1 · Refactoring per manutenibilità · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9**
> **V9.0 · Artefatti generati modernizzati a Vue 3 + Element Plus + Vite; verificati a runtime (40/40 asserzioni, vite build riuscita, 14 screenshot incl. ar-SA RTL); 3 difetti runtime corretti**
> **V8.1** · Rafforzamento qualità: harness di contratto offline (9 modalità, zero-diff OFF) + test unitari i18n-core + gate CI + suddivisione onesta del README (framework 206 / tradotte 8) + check-locale-keys.mjs
> **V8.0** · i18n a pagina intera: framework di 206 lingue (8 tradotte professionalmente), i18n a doppia via con contratto zero-diff in OFF

## 1. Cos'è questo progetto

CodeGen è una "fabbrica di codice" guidata dalle tabelle. Porta le tue tabelle di business (tramite importazione dal database o incollando DDL), configura i campi e gli interruttori di generazione, poi clicca "Genera" — produce il back-end Entity/Mapper/Service/Controller/DTO/VO, il front-end `api.js` e le pagine Vue, i menu e le rotte, persino le pagine di stampa A4 e gli scheletri di integration test, e li scrive in sicurezza nel tuo progetto target.

Non si limita a "sputare codice" — è una **pipeline di consegna con controlli di qualità e capacità di rollback**:

```
Table intake (DB import / DDL)
   → Field & switch configuration (with AI recommendations)
      → One-click generation: self-check → backup → write to disk → compile/validate → register menu → rebuild routes
         → Ledger bookkeeping → rollback-capable
```

---

## 2. Chi sono gli utenti e come lo usano

| Utente | Esigenza tipica | Il percorso offerto dalla piattaforma |
| --- | --- | --- |
| Sviluppatore back-end | Ha un gruppo di tabelle (o DDL) e vuole avviare rapidamente il CRUD | Pagina config tabella: importa → configura campi → Generazione con un clic |
| Sviluppatore full-stack / indipendente | Vuole partire da zero per un sistema completo (con login, permessi, dizionari) | Pagina Pipeline: costruisci un sistema con una frase |
| Responsabile di team | Vuole unificare lo stile di codice, le convenzioni e la struttura delle directory | Gestione sistema: un sistema = una configurazione di output, applicata in batch |
| Manutentore della piattaforma | Vuole modificare i template senza modificare il codice | Gestione template: modifica online, effetto immediato al salvataggio, con rollback delle versioni |

## 3. Quali problemi risolve

| Punto critico | Cosa fa CodeGen |
| --- | --- |
| L'inferno del CRUD ripetitivo — una pila di boilerplate per ogni tabella | Interruttori per tabella producono un front-end e back-end completi con un clic, inclusi CRUD, paginazione, validazione, import/export |
| Disallineamenti tra campi e API di front-end e back-end che causano continue correzioni di integrazione | Gli stessi metadati della tabella guidano sia le API back-end che le pagine front-end — nessuno scarto semantico |
| Convenzioni di team difficili da unificare; lo stile di ciascuno è diverso | I template sono la convenzione: 22 template FreeMarker gestiti centralmente, modificabili online con rollback delle versioni |
| I generatori "sovrascrivono e fanno perdere" la logica scritta a mano | Meccanismo di merge a blocchi-marcatore: il codice scritto a mano è preservato, vengono sostituite solo le regioni generate |
| Il codice generato non è affidabile finché non si compila | Self-check di compilazione in memoria (vero `javac`), controlli strutturali dei contratti, anteprima diff su disco — nulla viene scritto su disco se non compila |
| Realizzare uno nuovo sistema da zero richiede molto tempo | Pipeline "costruisci un sistema con una frase": crea DB → scaffold → genera → pacchetto → registra menu, completamente automatizzato |

---

## 4. Panoramica delle capacità

- **Acquisizione tabelle**: parsing DDL, importazione diretta dal database, procedura guidata di acquisizione batch, configurazione automatica delle colonne (controlli / validazione / dizionari / relazioni).
- **Forme delle tabelle**: tabella singola, tabella ad albero (API ad albero + griglia ad albero), master-detail (salvataggio nella stessa transazione + tab dettagli), tabelle di giunzione many-to-many.
- **Artefatti generati**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + pagine Vue + pagine di stampa A4 + scheletri di integration test.
- **Miglioramenti di business**: dropdown a relazione, traduzione dizionari, mascheramento dati, controlli di unicità, linkage dei form, permessi per ambito dati, import/export Excel, workflow di approvazione, pacchetti capacità pagamento / file / mobile.
- **Controlli di qualità**: self-check di compilazione in memoria (vero `javac`), controlli strutturali dei contratti, controlli di coerenza dei permessi, anteprima diff su disco, validazione reale degli artefatti front-end.
- **Atterraggio sicuro**: scrittura su disco + backup batch `.gen-backup`, merge a blocchi-marcatore per preservare il codice scritto a mano, libro mastro `gen_output` a quattro stati (creato / fuso / saltato / fallito), rollback storico.
- **Isolamento multi-sistema**: ogni progetto target è una "configurazione di sistema" (percorsi front/back, nomi dei pacchetti, prefisso gateway, classpath delle dipendenze, default degli interruttori di generazione) — più progetti serviti in parallelo.
- **Miglioramenti AI**: costruisci un sistema con una frase (modellazione AI), miglioramento seed-data tramite AI, configurazione consigliata per tabella tramite AI (anteprima diff + applicazione confermata).

---

## 5. Scenari tipici

| Scenario | Descrizione |
| --- | --- |
| Tabella ad albero | Dati gerarchici come le categorie di prodotto — API ad albero, griglia ad albero e dropdown di selezione padre prodotti automaticamente |
| CRUD a tabella singola | Clienti, prodotti, ecc. — dropdown a chiave esterna + traduzione dizionari + import/export pronti all'uso |
| Master-detail | Ordini di vendita + voci ordine — salvataggio nella stessa transazione, drawer dei dettagli con doppio tab (info master / dettagli) |
| Sola lettura / statistiche | Flussi di inventario, ecc. — interruttori di scrittura disattivati, solo query ed export |
| Documenti di stampa | Spunta l'interruttore "print" per produrre una pagina di stampa A4 e una rotta, con intestazione + tabella dettagli |
| Costruzione sistema con una frase | Descrivi il dominio di business — l'AI crea DB e tabelle + scaffold + generazione completa + registrazione menu in un'unica soluzione |

> Tutti gli scenari hanno demo eseguibili (progetto `demo_admin`) con screenshot — vedi `../codegen-server/docs/典型场景操作指南-演示后台demo.md` (cinese).

---

## 6. Flusso di lavoro principale

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Table intake │ → │ Field/switch conf. │ → │ One-click generate (self-check→backup→write→     │ → │ Ledger · Rollback │
│              │   │                    │   │ compile→menu→route)                              │   │                   │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     controls/validation     Lands on disk only after all quality gates pass;      Batch-level traceability
                    /dictionaries           hand-written code is preserved
```

- **Anteprima diff**: gli artefatti generati vengono confrontati file per file con lo stato attuale su disco; confermi prima che venga scritto qualcosa, evitando sovrascritture accidentali.
- **Libro mastro di generazione**: ogni file registra la sua azione, il conteggio dei byte, l'MD5 e la posizione del backup; un'intera batch può essere ripristinata.

---

## 7. Costruisci un sistema con una frase (Pipeline)

Una capacità di alto livello volta a "realizzare un back-end da zero". Dopo che descrivi il dominio di business, la pipeline avanza automaticamente attraverso 5 fasi:

| Fase | Significato |
| --- | --- |
| `CHECK` | Valida la configurazione del progetto target e il classpath delle dipendenze |
| `WRITE` | Crea database e tabelle, scrive scaffold e artefatti generati |
| `COMPILE` | Backend `mvn package` / build front-end, self-check di compilazione in memoria |
| `MENU` | Produce e registra i permessi dei menu (due uscite: SQL artefatto / scrittura diretta interna) |
| `ROUTE` | Ricostruisce i frammenti di rotta del front-end |

Un fallimento in qualsiasi fase interrompe immediatamente con un errore leggibile per una facile individuazione.

---

## 8. Architettura di sistema

Front-end e back-end separati, due repository che collaborano:

| Repository | Ruolo | Stack |
| --- | --- | --- |
| `codegen-server` | Backend: motore di generazione, template, validazione, libro mastro, pipeline | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Frontend: console (config tabella / template / libro mastro / gestione sistema) | Vue 2.7 · Element UI · axios · Vue Router (modalità history) |

Livelli del back-end:

```
controller/    API layer (Gen* generation chain / Sys* system management / pipeline)
service/impl/  Core implementations (generation chain, template engine, merge, validation, pipeline execution)
util/          Template engine, model assembly, marker-block merge, self-check compilation
config/        Output config, self-check config, template seeder
common/        Result / BaseEntity / exceptions / audit
resources/gen-templates/   22 FreeMarker templates (backend / frontend / shared / test groups)
```

**Capacità di base che il progetto target deve fornire** (il generatore produce solo codice di business; una base mancante fallisce silenziosamente):

1. Plugin di paginazione MyBatis-Plus (`PaginationInnerInterceptor`)
2. Classi di base come `Result` / `BaseEntity` / `PageQuery` (il prefisso del pacchetto deve corrispondere a `root-package`)
3. Frontend `utils/request.js` con `toPageData` (converte `Page{records,total}` in `{list,total}`)
4. Dipendenze `springdoc-openapi-starter-webmvc-ui` (il codice generato usa `@Operation`) e `poi-ooxml` (Excel)

---

## 9. Stack tecnologico in sintesi

| Dimensione | Scelta |
| --- | --- |
| Linguaggio / framework back-end | Java 21 / Spring Boot 3.2.5 |
| Persistenza | MyBatis-Plus 3.5.5 |
| Motore di template | FreeMarker (22 template seed, modifica online + rollback versioni) |
| Sicurezza | Autenticazione JWT |
| Database | MySQL 8 |
| Frontend | Vue 2.7 + Element UI |
| AI locale | qwen2.5-coder:7b (modellazione / config consigliata / miglioramento seed-data) |
| Documentazione API | springdoc-openapi (Swagger UI) |

---

## 10. Avvio rapido

### 1. Crea il database (13 tabelle di piattaforma + account predefinito admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Configura il progetto target

Copia il template di configurazione e adattalo ai tuoi percorsi locali:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` è escluso da `.gitignore` e non verrà committato. Impostazioni chiave:

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

Esporta la directory delle dipendenze per il self-check così:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Avvia il back-end

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

Il servizio usa di default la porta **8120**; documentazione API su `/swagger-ui/index.html`.

### 4. Avvia il front-end

```bash
cd ../codegen-web
npm install
npm run serve
```

Il front-end usa di default la porta **9530**, con proxy di `/dev-api` alla porta 8120 del back-end. Account predefinito `admin / admin123`.

### 5. Voci di configurazione chiave

| Impostazione | Descrizione |
| --- | --- |
| `gen.output.*` | Percorsi di scrittura artefatti, nomi dei pacchetti, mappatura dei moduli |
| `gen.selfcheck.classpath` | Classpath delle dipendenze per il self-check di compilazione in memoria, supporta `dir/*` |
| `gen.menu.outlet` | `artifact` produce solo SQL menu e frammenti di rotta; `internal` scrive anche il `sys_menu` della piattaforma stessa |

---

## 11. Mappa della documentazione

| Documento | Contenuto |
| --- | --- |
| `../codegen-server/docs/代码生成器-操作手册.md` (cinese) | Distribuzione, avvio in 5 minuti, config tabella, acquisizione batch, gestione template, libro mastro, catalogo API, pipeline completa (con screenshot) |
| `../codegen-server/docs/典型场景操作指南-演示后台demo.md` (cinese) | 6 tabelle che coprono 5 scenari tipici, con demo eseguibile e screenshot di esecuzione reale |
| `项目介绍.md` | Documento di introduzione al progetto, stessa fonte di questo file |
| `../codegen-server/docs/代码生成器-Vx.x-规划.md` | Piani di versione e inventari di funzionalità da V3.1 → V9.1 |
| `../codegen-web/README.md` | Riferimento di avvio rapido per la console front-end |

---

## 12. Cronologia versioni

| Versione | Tema / documento di piano | Commit di rilascio |
| --- | --- | --- |
| V1.2 / V1.3 | Generazione / batch / rollback con un clic; anteprima diff e sincronizzazione strutturale | — (solo nei commenti di `src/api/gen.js`) |
| V2.0 | Rotte dinamiche (albero menu consegnato dal back-end) | — |
| V3.0 / V3.1 | Libro mastro multi-sistema; loop multi-sistema + primi test automatizzati | V3.1 (174 righe) |
| V4.2 | Risultati di generazione tracciabili e con rollback (scrittura libro mastro) | V4.2 (292 righe) |
| V4.3 | Regressione template e guardrail di qualità | V4.3 (200 righe) |
| V4.4 | Un-master-molti-detail, completezza scrittura a cascata, lane template | V4.4 (154 righe) |
| V4.5 | Documenti di stampa A4 (zero dipendenze di terze parti) | V4.5 (127 righe) |
| V5.0 | Costruzione sistema con una frase: pipeline + modellazione AI + base scaffold | — |
| V5.1 | Auto-completamento colonne di audit DDL | V5.1 (100 righe) |
| V5.2 | Passo di inizializzazione seed-data della pipeline | V5.2 (107 righe), 84ec8dd |
| V5.5 | Chiusura generazione progetto multi-modulo | af73dd3 |
| V6.0 | Infrastruttura a microservizi (Nacos / Gateway / Feign, terza modalità "micro") | V6.0 (111 righe), 1deecbd |
| V6.1 | Motore duale workflow Activiti | V6.1 (100 righe), 02b671e |
| V6.2 | Superficie di consegna artefatti (CLI / artefatti di distribuzione / sicurezza config) | V6.2 (114 righe), 02b671e |
| V6.3 | Pacchetto capacità pagamento (WeChat / Alipay + Mock) | V6.3 (104 righe), 1c4ee6a, 22eede5 |
| V6.4 | Baseline permessi e sicurezza (persistenza token / BCrypt / permessi API / protezione login) | V6.4 (107 righe), 007a202 |
| V6.5 | Mobile uni-app (H5 + Mini Program WeChat) | V6.5 (105 righe), 779ac41 |
| V6.6 | Pacchetto capacità file | 98f3c09 |
| V6.7 | Accettazione factory test artefatti (test_enabled tri-state + skeleton H2) | V6.7 (134 righe), 4e7d318 |
| V6.8 | Manutenibilità artefatti (dryRun / skip protetto / parametrizzazione brand) | V6.8 (141 righe), be6f858 |
| V6.9 | Centro documentazione piattaforma (base back-end, front-end completato nella stessa batch) | — |
| V6.10 | Osservabilità piattaforma: Actuator + finestra metriche in memoria + pagina monitor incorporata | — |
| V7.0 | Raccomandazioni pagine e menu AI (anteprima / applica) | V7.0 (94 righe), 45f2670 |
| V7.1 | Miglioramento seed-data AI (l'AI possiede la semantica, le regole possiedono la struttura) | V7.1 (42 righe), 5ca6e72 |
| V7.2 | Deliverable SQL scritti in `db/` dopo pipeline riuscita | eebff88 |
| **V7.3** | **Affidabilità di consegna: test artefatti compilati self-check / uscita unificata consegna SQL / raccomandazioni AI per tabella** | **V7.3 (138 righe), 696765b** |
| **V7.4** | Consegna ripetibile: distribuibile da un database vuoto, ogni commit garantito eseguibile | c306e29 |
| **V7.5** | **Configurazione lingua del progetto: elenco di 206 lingue / lingua predefinita / lingue abilitate, iniezione di lang e dir nei generati** | 7a9afa0 + abac56d |
| **V8.0** | **i18n a pagina intera: framework di 206 lingue (8 tradotte professionalmente), i18n a doppia via con contratto zero-diff in OFF** | 9e5dd57 → 7d57aea |
| **V8.1** | **Rafforzamento qualità: harness di contratto offline (9 modalità, zero-diff OFF) + test unitari i18n-core + gate CI + suddivisione onesta del README (framework 206 / tradotte 8) + check-locale-keys.mjs** | 93a12d5 + e49330b |
| **V9.0** | **Artefatti generati modernizzati a Vue 3 + Element Plus + Vite; verificati a runtime (40/40 asserzioni, vite build riuscita, 14 screenshot incl. ar-SA RTL); 3 difetti runtime corretti** | 93a12d5 + e49330b  |
| **V9.1** | **Refactoring per manutenibilità · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9** | 654b8e0 → b0d081e, **Versione attuale** |

---

> **Osservabilità piattaforma V6.10**: integra Actuator + Micrometer, campionamento ogni 5 secondi in un buffer circolare in memoria (finestra di 30 minuti), più una nuova pagina incorporata "System Monitor" — API di aggregazione back-end `/monitor/*` + pannelli ECharts front-end. Solo gli endpoint `health/info/metrics` sono in whitelist; `/actuator/health` è senza token mentre `metrics` ecc. richiedono auth; `env/beans/heapdump/threaddump/loggers` non vengono mai esposti. La finestra di campionamento è solo per-processo, persa al riavvio e non condivisa tra le istanze. Vedi `../codegen-server/docs/可观测性说明.md` (cinese) per i dettagli.

## 13. Stato del progetto

- La linea di versione pianificata corrente ha raggiunto **V9.1** (le capacità continuano a iterare: modellazione AI, miglioramento seed-data, sistema di pacchetti capacità, ecc.).
- Un progetto demo compagno `demo_admin` (back-end 8140 / front-end 9040) copre scenari albero / CRUD / master-detail / sola lettura / stampa ed è eseguibile in autonomia per la verifica.
- Entrambi i repository sono ospitati su Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. Autore e supporto

- **Autore**: un programmatore cinese (sviluppatore indipendente)
- **Email**: `hustxiayuhong@126.com`
- **Supporto tecnico**: scrivi la tua email **in cinese**, includendo la descrizione del problema, l'ambiente di esecuzione (JDK / MySQL / versione) e i passi per riprodurlo

> Leggo e rispondo a ogni email. Segnalazioni di bug, richieste di funzionalità e proposte di collaborazione sono tutte benvenute.

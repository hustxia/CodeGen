# Majiang · Generator kôda (CodeGen) — Predstavitev projekta

> Jezici: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · **Hrvatski** · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> Generator kôda za više projekata za **Spring Boot + Vue**: polazeći od struktura tablica baze podataka, proizvodi **kompilabilan, pokretan** poslovni kôd za front-end i back-end s izbornicima i ovlastima, te upravlja cijelim ciklusom isporuke pisanjem na disk, sigurnosnom kopijom, knjigovodstvom (ledger) i povratom.

GitHub: https://github.com/hustxia/CodeGen

## 1. Što je ovo

CodeGen je "tvornica kôda vođena tablicama". Uvezite svoje poslovne tablice (putem uvoza iz baze podataka ili lijepljenjem DDL-a), konfigurirajte polja i prekidače generiranja i kliknite na "Generiraj" — tada proizvodi back-end Entity/Mapper/Service/Controller/DTO/VO, front-end `api.js` i Vue stranice, izbornike i rute, čak i A4 stranice za ispis i kosture integracijskih testova, i sve sigurno upisuje u vaš ciljni projekt.

Ne samo da "izbacuje kôd" — već je **proces isporuke s kvalitetnim vratima i sposobnošću povrata**:

```
Prijem tablica (DB import / DDL)
   → Konfiguracija polja i prekidača (s AI preporukama)
      → Generiranje jednim klikom: samoprovjera → sigurnosna kopija → upis na disk → kompilacija/validacija → registracija izbornika → obnova ruta
         → Knjigovodstvena evidencija → povrativo
```

---

## 2. Tko su korisnici i kako ga koriste

| Korisnik | Tipična potreba | Put koji platforma pruža |
| --- | --- | --- |
| Back-end programer | Ima niz tablica (ili DDL) i želi brzo postaviti CRUD | Stranica konfiguracije tablica: uvoz → konfiguracija polja → generiranje jednim klikom |
| Full-stack / neovisni programer | Želi izgraditi potpun sustav od nule (uključujući prijavu, ovlasti, rječnik) | Stranica Procesa: izgradi sustav jednom rečenicom |
| Voditelj tima | Želi ujediniti stil koda, konvencije koda i strukturu direktorija | Upravljanje sustavom: jedan sustav = jedna izlazna konfiguracija, primjenjuje se serijski |
| Održavatelj platforme | Želi mijenjati predloške bez mijenjanja koda | Upravljanje predlošcima: online uređivanje, spremanje stupa na snagu, podržava povrat verzija |



## 3. Koje probleme rješava

| Bolna točka | Što radi CodeGen |
| --- | --- |
| Ponavljajuća CRUD pakao — gomila boilerplatea za svaku tablicu | Prekidači na razini tablice proizvode potpuni front-end i back-end jednim klikom, uključujući CRUD, paginaciju, validaciju, import/export |
| Neusklađenosti polja i API-ja između front-a i back-a koje uzrokuju beskrajne popravke integracija | Ista metapodaci tablica upravlja i back-end API-jima i front-end stranicama — nulta semantička odstupanja |
| Team konvencije teško uskladiti; svatko piše svojim stilom | Predlošci su konvencija: 22 FreeMarker predloška centralizirano upravljana, online urediva s povratom verzija |
| Generatori "prebrišu i izgube" ručno pisani logiku | Mehanizam spajanja marker blokova: ručno pisani kôd se čuva, samo se generirane regije zamjenjuju |
| Generirani kôd nepouzdan do vremena kompilacije | Samoprovjera kompilacijom u memoriji (pravi `javac`), provjere strukturnih ugovora, pregled diff-a na disku — ništa ne pada na disk osim ako se ne kompilira |
| Postavljanje novog sustava od nule oduzima puno vremena | Proces "izgradi sustav jednom rečenicom": stvori DB → kostur → generiraj → pakiraj → registriraj izbornike, potpuno automatizirano |

---

## 4. Pregled mogućnosti

- **Prijem tablica**: DDL parsiranje, izravni uvoz iz baze podataka, vodič za prijem u serijama, automatski izvedena konfiguracija stupaca (kontrole / validacija / rječnici / odnosi).
- **Oblici tablica**: jednostavna tablica, stabla tablica (tree API + stablo rešetke), master-detail (spremanje u istoj transakciji + kartice detalja), tablice za povezivanje više prema više.
- **Generirani artefakti**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue stranice + A4 stranice za ispis + kosturi integracijskih testova.
- **Poslovna poboljšanja**: padajući izbornici odnosa, prijevod putem rječnika, maskiranje podataka, provjere jedinstvenosti, povezivanje obrazaca, ovlasti podataka, Excel import/export, odobravanje tijeka rada, paketi mogućnosti plaćanja / datoteka / mobilnih uređaja.
- **Kvalitetna vrata**: samoprovjera kompilacijom u memoriji (pravi `javac`), provjere strukturnih ugovora, provjere konzistentnosti ovlasti, pregled diff-a na disku, stvarna validacija front-end artefakata.
- **Siguran pad**: upis na disk + sigurnosne kopije serija `.gen-backup`, spajanje marker blokova za očuvanje ručno pisanog kôda, četverostanje knjigovodstva `gen_output` (stvoreno / spojeno / preskočeno / neuspješno), povrat u povijest.
- **Izolacija više sustava**: svaki ciljni projekt je "konfiguracija sustava" (putevi front/back, nazivi paketa, gateway prefiks, dependency classpath, zadane vrijednosti prekidača generiranja) — više projekata servisirano paralelno.
- **AI poboljšanja**: izgradi sustav jednom rečenicom (AI modeliranje), AI poboljšanje seed podataka, AI preporučena konfiguracija na razini tablice (pregled diff-a + označena primjena).

---

## 5. Tipični scenariji

| Scenarij | Opis |
| --- | --- |
| Stabla tablica | Hijerarhijski podaci poput kategorija proizvoda — automatski proizvodi tree API, stablo rešetke i padajući izbornik nadređenog odabira |
| Jednostavna tablica CRUD | Kupci, proizvodi itd. — FK padajući izbornici + prijevod putem rječnika + import/export odmah spremno |
| Master-detail | Prodajni nalozi + stavke naloga — spremanje u istoj transakciji, ladica detalja s dvije kartice (glavne informacije / detalji) |
| Samo za čitanje / statistika | Tokovi zaliha itd. — prekidači za pisanje isključeni, samo upit i export |
| Dokumenti za ispis | Označite prekidač "ispis" za proizvodnju A4 stranice za ispis i rute, s zaglavljem + tablicom detalja |
| Izgradnja sustava jednom rečenicom | Opišite poslovnu domenu — AI stvara DB & tablice + kostur + potpuno generiranje + registraciju izbornika u jednom potezu |

> Svi scenariji imaju pokretne demo primjere (`demo_admin` projekt) sa snimkama zaslona — pogledajte `docs/典型场景操作指南-演示后台demo.md` (kineski).

---

## 6. Osnovni tijek rada

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Prijem tablica │ → │ Konf. polja/prekidača │ → │ Generiraj jednim klikom (samoprovjera→kopija→upis→│ → │ Knjigovodstvo · Povrat │
│              │   │                      │   │ kompilacija→izbornik→ruta)                        │   │                        │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     kontrole/validacija    Na disk pada samo nakon svih kvalitetnih vrata;
                    /rječnici              ručno pisani kôd se čuva
```

- **Pregled diff-a**: generirani artefakti se razlikuju datoteku po datoteku u odnosu na trenutno stanje na disku; vi potvrđujete prije nego što se nešto napiše, kako biste izbjegli slučajne prebrise.
- **Knjigovodstvo generiranja**: svaka datoteka bilježi svoju radnju, broj bajtova, MD5 i lokaciju sigurnosne kopije; cijela serija se može vratiti.

---

## 7. Izgradi sustav jednom rečenicom (Proces)

Mogućnost visoke razine usmjerena na "postavljanje back-end-a od nule". Nakon što opišete poslovnu domenu, proces automatski napreduje kroz 5 faza:

| Faza | Značenje |
| --- | --- |
| `CHECK` | Provjerava konfiguraciju ciljnog projekta i dependency classpath |
| `WRITE` | Stvara bazu podataka & tablice, piše kostur i generirane artefakte |
| `COMPILE` | Back-end `mvn package` / front-end build, samoprovjera kompilacijom u memoriji |
| `MENU` | Proizvodi i registrira ovlasti izbornika (dva izlaza: artefakt SQL / izravni interni upis) |
| `ROUTE` | Obnavlja fragmente front-end ruta |

Svaka pogreška u fazi odmah prekida s čitljivom pogreškom za jednostavno lociranje.

---

## 8. Arhitektura sustava

Front-end i back-end odvojeni, dva repozitorija rade zajedno:

| Repozitorij | Uloga | Stog |
| --- | --- | --- |
| `codegen-server` | Back-end: generacijski motor, predlošci, validacija, knjigovodstvo, proces | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Front-end: konzola (konf. tablica / predlošci / knjigovodstvo / upravljanje sustavom) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (historija način) |

Back-end slojevi:

```
controller/    API sloj (Gen* generacijski lanac / Sys* upravljanje sustavom / proces)
service/impl/  Implementacije jezgre (generacijski lanac, motor predložaka, spajanje, validacija, izvođenje procesa)
util/          Motor predložaka, sastav modela, spajanje marker blokova, samoprovjera kompilacije
config/        Konfiguracija izlaza, konfiguracija samoprovjere, sijač predložaka
common/        Result / BaseEntity / iznimke / revizija
resources/gen-templates/   22 FreeMarker predloška (backend / frontend / shared / test grupe)
```

**Osnovne mogućnosti koje ciljni projekt mora pružiti** (generator proizvodi samo poslovni kôd; nedostajuća osnova neuspješno utječe):

1. MyBatis-Plus plugin za paginaciju (`PaginationInnerInterceptor`)
2. Osnovne klase poput `Result` / `BaseEntity` / `PageQuery` (prefiks paketa mora odgovarati `root-package`)
3. Front-end `utils/request.js` s `toPageData` (pretvara `Page{records,total}` u `{list,total}`)
4. Ovisnosti `springdoc-openapi-starter-webmvc-ui` (generirani kôd koristi `@Operation`) i `poi-ooxml` (Excel)

---

## 9. Pregled tehnološkog stoga

| Dimenzija | Odabir |
| --- | --- |
| Back-end jezik / okvir | Java 21 / Spring Boot 3.2.5 |
| Perzistencija | MyBatis-Plus 3.5.5 |
| Motor predložaka | FreeMarker (22 sijača predložaka, online uređivanje + povrat verzija) |
| Sigurnost | JWT autentifikacija |
| Baza podataka | MySQL 8 |
| Front-end (generated projects) | Vue 3 + Element Plus + Vite |
| Front-end (platform console) | Vue 2.7 + Element UI |
| Lokalni AI | qwen2.5-coder:7b (modeliranje / preporučena konfiguracija / poboljšanje seed podataka) |
| API dokumentacija | springdoc-openapi (Swagger UI) |

---

## 10. Brzi početak

### 1. Stvorite bazu podataka (13 platformskih tablica + zadani račun admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Konfigurirajte ciljni projekt

Kopirajte predložak konfiguracije i prilagodite ga svojim lokalnim putevima:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` je isključen iz `.gitignore` i neće biti commit-ovan. Glavne postavke:

```yaml
gen:
  output:
    backend-root: /put/do/vašeg-ciljnog-projekta      # osnova za upis artefakata
    frontend-root: /put/do/vašeg-ciljnog-projekta
    frontend-path: demo-web                          # poddirektorij front-end-a za monorepo
    backend-path: demo-server/src/main/java/com/demo
    package-name: com.demo
    root-package: com.demo                           # prefiks za import osnovnih klasa u artefaktima
  selfcheck:
    classpath: /put/do/vašeg-ciljnog-projekta/demo-lib/*
```

Izvezite direktorij ovisnosti za samoprovjeru na ovaj način:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Pokrenite back-end

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

Usluga radi prema zadanim postavkama na portu **8120**; API dokumentacija na `/swagger-ui/index.html`.

### 4. Pokrenite front-end

```bash
cd ../codegen-web
npm install
npm run serve
```

Front-end radi prema zadanim postavkama na portu **9530**, prosljeđuje `/dev-api` na port back-end-a 8120. Zadani račun `admin / admin123`.

### 5. Važne konfiguracijske stavke

| Postavka | Opis |
| --- | --- |
| `gen.output.*` | Putevi za upis artefakata, nazivi paketa, mapiranje modula |
| `gen.selfcheck.classpath` | Dependency classpath za samoprovjeru kompilacijom u memoriji, podržava `dir/*` |
| `gen.menu.outlet` | `artifact` proizvodi samo meni SQL & fragmente ruta; `internal` također piše u vlastitu `sys_menu` platforme |

---

## 11. Karta dokumentacije

| Dokument | Sadržaj |
| --- | --- |
| `docs/代码生成器-操作手册.md` (kineski) | Postavljanje, 5-minutni početak, konfiguracija tablica, prijem u serijama, upravljanje predlošcima, knjigovodstvo, API katalog, cijeli proces (sa snimkama zaslona) |
| `docs/典型场景操作指南-演示后台demo.md` (kineski) | 6 tablica koje pokrivaju 5 tipičnih scenarija, s pokretnim demo primjerom i stvarnim snimkama zaslona |
| `docs/项目介绍.md` | Dokument s predstavljanjem projekta, isti izvor kao ova datoteka |
| `docs/代码生成器-Vx.x-规划.md` | Verzijski planovi i popisi funkcionalnosti od V3.1 → V7.3 |
| `codegen-web/README.md` | Referenca za brzi početak na front-end konzoli |

---

## 12. Povijest verzija

| Verzija | Tema / plan dokument | Commit postavljanja |
| --- | --- | --- |
| V1.2 / V1.3 | Generiranje jednim klikom / serija / povrat; pregled diff-a & sinkronizacija strukture | — (samo u komentarima `src/api/gen.js`) |
| V2.0 | Dinamičke rute (meni stablo isporučeno od back-end-a) | — |
| V3.0 / V3.1 | Knjigovodstvo više sustava; petlja više sustava + prvi automatizirani testovi | V3.1 (174 redaka) |
| V4.2 | Praćenivi, povrativi rezultati generiranja (disk knjigovodstvo) | V4.2 (292 redaka) |
| V4.3 | Regresija predložaka & kvaliteta čuvari | V4.3 (200 redaka) |
| V4.4 | Jedan-gospodar-više-detalja, potpunost kaskadnog upisa, trake predložaka | V4.4 (154 redaka) |
| V4.5 | A4 dokumenti za ispis (nula ovisnosti trećih strana) | V4.5 (127 redaka) |
| V5.0 | Izgradnja sustava jednom rečenicom: proces + AI modeliranje + osnova kostura | — |
| V5.1 | Automatsko popunjavanje stupaca revizije DDL-a | V5.1 (100 redaka) |
| V5.2 | Korak inicijalizacije seed podataka u procesu | V5.2 (107 redaka), 84ec8dd |
| V5.5 | Završetak generiranja više-modulnog projekta | af73dd3 |
| V6.0 | Infrastruktura mikroservisa (Nacos / Gateway / Feign, treći način "micro") | V6.0 (111 redaka), 1deecbd |
| V6.1 | Dvostruki motor tijeka rada Activiti | V6.1 (100 redaka), 02b671e |
| V6.2 | Površina isporuke artefakata (CLI / deploy artefakti / sigurnost konfiguracije) | V6.2 (114 redaka), 02b671e |
| V6.3 | Paket mogućnosti plaćanja (WeChat / Alipay + Mock) | V6.3 (104 redaka), 1c4ee6a, 22eede5 |
| V6.4 | Osnova ovlasti i sigurnosti (perzistencija tokena / BCrypt / API ovlasti / zaštita prijave) | V6.4 (107 redaka), 007a202 |
| V6.5 | Mobilni uni-app (H5 + WeChat Mini Program) | V6.5 (105 redaka), 779ac41 |
| V6.6 | Paket mogućnosti datoteka | 98f3c09 |
| V6.7 | Prihvaćanje testova artefakata u tvornici (test_enabled tri-stanje + H2 kostur) | V6.7 (134 redaka), 4e7d318 |
| V6.8 | Održivost artefakata (dryRun / zaštićeni preskok / parametrizacija marke) | V6.8 (141 redaka), be6f858 |
| V6.9 | Središte dokumentacije platforme (back-end osnova, front-end završen u istoj seriji) | — |
| V6.10 | Promatranje platforme: Actuator + metrički prozor u memoriji + ugrađena stranica monitora | — |
| V7.0 | AI preporuke stranica i izbornika (preview / apply) | V7.0 (94 redaka), 45f2670 |
| V7.1 | AI poboljšanje seed podataka (AI posjeduje semantiku, pravila posjeduju strukturu) | V7.1 (42 redaka), 5ca6e72 |
| V7.2 | SQL isporučeni objekti upisani u `db/` nakon uspješnog procesa | eebff88 |
| **V7.3** | **Pouzdanost isporuke: samoprovjera kompilirani testovi artefakata / jedinstveni SQL izlaz isporuke / AI preporuke na razini tablice** | **V7.3 (138 redaka)** |
| **V7.4** | Ponovljiva isporuka: postavljiva iz prazne baze podataka, svaki commit jamčivo pokretan | c306e29 |
| **V7.5** | Jezična konfiguracija projekta: popis 206 jezika / zadani jezik / omogućeni jezici, ubacivanje lang i dir u generirani kod | 7a9afa0 + abac56d |
| **V8.0** | i18n cijele stranice: okvir 206 jezika (8 stručno prevedenih), dvokolosiječna i18n s ugovorom nulte razlike u OFF | 9e5dd57 → 7d57aea |
| **V8.1** | Očvršćivanje kvalitete: offline ugovorni harness (9 načina, nulta razlika OFF) + jedinični testovi i18n-core + CI kapija + poštena podjela README (okvir 206 / prevedenih 8) + check-locale-keys.mjs | 93a12d5 + e49330b |
| **V9.0** | Generirani artefakti modernizirani na Vue 3 + Element Plus + Vite; provjereno u izvođenju (40/40 tvrdnji, uspješan vite build, 14 snimki zaslona incl. ar-SA RTL); popravljene 3 izvedbene greške | 93a12d5 + e49330b  |
| **V9.1** | **Refaktoriranje održivosti · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9** | 654b8e0 → b0d081e, **trenutna verzija** |

---

> **V6.10 Promatranje platforme**: integrira Actuator + Micrometer, uzorkuje svakih 5 sekundi u memorijski kružni međuspremnik (30-minutni prozor), plus nova ugrađena stranica "Monitor sustava" — back-end `/monitor/*` agregacijski API-ji + front-end ECharts ploče. Samo `health/info/metrics` krajnje točke su whitelisted; `/actuator/health` je bez tokena dok `metrics` itd. zahtijevaju auth; `env/beans/heapdump/threaddump/loggers` nikada nisu izloženi. Prozor uzorkovanja je samo po procesu, gubi se prilikom ponovnog pokretanja, i nije dijeljen između instanci. Pogledajte `docs/可观测性说明.md` (kineski) za detalje.

## 13. Status projekta

> Trenutna planirana linija verzija dosegla je **V7.3** (mogućnosti se nastavljaju iterirati: AI modeliranje, poboljšanje seed podataka, sustav paketa mogućnosti itd.).
> Pripadni demo projekt `demo_admin` (back-end 8140 / front-end 9040) pokriva scenarije stabla / CRUD / master-detail / samo za čitanje / ispis i radi samostalno za provjeru.
> Oba repozitorija su hostirana na Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. Autor & podrška

> **Autor**: kineski programer (neovisni razvojni inženjer)
> **E-pošta**: `hustxiayuhong@126.com`
> **Tehnička podrška**: molimo napišite svoju e-poštu **na kineskom**, uključujući opis problema, runtime okruženje (JDK / MySQL / verzija) i korake za reprodukciju

> Čitam i odgovaram na svaku e-poštu. Izvješća o pogreškama, zahtjevi za funkcionalnošću i upiti za suradnju su dobrodošli.

---

## 15. Usporedba sa sličnim alatima

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

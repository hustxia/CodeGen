# Majiang · Generator kode (CodeGen) — Predstavitev projekta

> Jeziki: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · **Slovenščina** · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> Generator kode za več projektov za **Spring Boot + Vue**: izhajajoč iz struktur tabel baz podatkov proizvaja **kompilabilno, izvedljivo** poslovno kodo za front-end in back-end z meniji in pravicami, ter upravlja celoten dostavni cikel pisanjem na disk, varnostno kopijo, knjigovodstvom (ledger) in povratom.

GitHub: https://github.com/hustxia/CodeGen

## 1. Kaj je to

CodeGen je "tovarna kode, ki jo poganjajo tabele". Uvozite svoje poslovne tabele (preko uvoza iz baze podatkov ali lepljenja DDL), konfigurirajte polja in stikala za generiranje ter kliknite na "Generiraj" — nato proizvede back-end Entity/Mapper/Service/Controller/DTO/VO, front-end `api.js` in Vue strani, menije in rute, celo A4 strani za tisk in skelete integracijskih testov, ter vse varno zapiše v vaš ciljni projekt.

Ne samo "izpljuje kodo" — ampak je **dostavni cevovod s kvalitativnimi vrati in zmožnostjo povrata**:

```
Prijem tabel (DB uvoz / DDL)
   → Konfiguracija polj in stikal (z AI priporočili)
      → Generiranje enim klikom: samopreverjanje → varnostna kopija → pisanje na disk → kompilacija/validacija → registracija menija → obnova rut
         → Knjigovodstvena evidenca → povratljivo
```

---

## 2. Kdo so uporabniki in kako jih uporabljajo

| Uporabnik | Tipična potreba | Pot, ki jo ponuja platforma |
| --- | --- | --- |
| Back-end razvijalec | Ima vrsto tabel (ali DDL) in želi hitro vzpostaviti CRUD | Stran konfiguracije tabel: uvoz → konfiguracija polj → generiranje enim klikom |
| Full-stack / neodvisni razvijalec | Želi od nič zgraditi popoln sistem (vključno s prijavo, pravicami, slovarjem) | Stran Cevovoda: zgradi sistem z enim stavkom |
| Vodja ekipe | Želi poenotiti slog kode, konvencije kode in strukturo imenikov | Upravljanje sistema: en sistem = ena izhodna konfiguracija, velja paketno |
| Vzdrževalec platforme | Želi spremeniti predloge, ne da bi spremenil kodo | Upravljanje predlog: spletno urejanje, shranjevanje začne veljati takoj, podpira vračanje različic |



## 3. Katere probleme rešuje

| Bolečina | Kaj počne CodeGen |
| --- | --- |
| Ponavljajoča CRUD pekel — kup boilerplate za vsako tabelo | Stikala na ravni tabele proizvedejo popoln front-end in back-end z enim klikom, vključno s CRUD, paginacijo, validacijo, uvozom/izvozom |
| Neusklajenosti polj in API-jev med front-om in back-om, ki povzročajo neskončne integracijske popravke | Ista metapodatka tabele upravlja tako back-end API-je kot front-end strani — nič semantičnega odstopanja |
| Težko poenotiti timske konvencije; vsak piše v svojem slogu | Predloge so konvencija: 22 FreeMarker predlog osrednjega upravljanja, uredivih na spletu z vračanjem različic |
| Generatorji "prepišejo in izgubijo" ročno napisano logiko | Mehanizem združevanja marker blokov: ročno napisana koda se ohrani, samo generirane regije se zamenjajo |
| Generirana koda nezanesljiva do časa kompilacije | Samopreverjanje s kompilacijo v pomnilniku (pravi `javac`), preverjanja strukturnih pogodb, pregled diff-a na disku — nič ne pade na disk, razen če se kompilira |
| Postavitev novega sistema iz nič zahteva veliko časa | Cevovod "zgradi sistem z enim stavkom": ustvari DB → ogrodje → generiraj → zapakiraj → registriraj menije, popolnoma avtomatizirano |

---

## 4. Pregled zmogljivosti

- **Prijem tabel**: DDL parsiranje, neposredni uvoz iz baze podatkov, čarovnik za serijski prijem, samodejno izvedena konfiguracija stolpcev (kontrole / validacija / slovarji / odnosi).
- **Oblike tabel**: enostavna tabela, drevesna tabela (tree API + drevesni grid), master-detail (hramba v isti transakciji + zavihki za podrobnosti), povezovalne tabele mnogo proti mnogo.
- **Generirani artefakti**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue strani + A4 strani za tisk + skeleti integracijskih testov.
- **Poslovne izboljšave**: spustni meniji odnosov, prevod prek slovarja, maskiranje podatkov, preverjanja edinstvenosti, povezovanje obrazcev, pravice do podatkov, Excel uvoz/izvoz, odobravanje delovnega toka, paketi zmogljivosti plačil / datotek / mobilnih naprav.
- **Kvalitativna vrata**: samopreverjanje s kompilacijo v pomnilniku (pravi `javac`), preverjanja strukturnih pogodb, preverjanja skladnosti pravic, pregled diff-a na disku, dejanska validacija front-end artefaktov.
- **Varna namestitev**: pisanje na disk + varnostne kopije serij `.gen-backup`, združevanje marker blokov za ohranitev ročno napisane kode, štiristanjsko knjigovodstvo `gen_output` (ustvarjeno / združeno / preskočeno / neuspešno), zgodovinski povrat.
- **Izolacija več sistemov**: vsak ciljni projekt je "konfiguracija sistema" (pot front/back, imena paketov, gateway predpona, classpath odvisnosti, privzete vrednosti stikal za generiranje) — več projektov servisirano vzporedno.
- **AI izboljšave**: zgradi sistem z enim stavkom (AI modeliranje), AI izboljšanje seed podatkov, AI priporočena konfiguracija na ravni tabele (pregled diff-a + označena uporaba).

---

## 5. Tipični scenariji

| Scenarij | Opis |
| --- | --- |
| Drevesna tabela | Hierarhični podatki, kot so kategorije izdelkov — samodejno proizvede tree API, drevesni grid in spustni meni za izbiro nadrejenega |
| Enostavna tabela CRUD | Stranke, izdelki itd. — FK spustni meniji + prevod prek slovarja + uvoz/izvoz takoj pripravljen |
| Master-detail | Prodajni nalogi + artikli naročil — hramba v isti transakciji, predal za podrobnosti z dvema zavihkoma (glavne informacije / podrobnosti) |
| Samo za branje / statistika | Tokovi zalog itd. — stikala za pisanje izključena, samo poizvedba in izvoz |
| Dokumenti za tisk | Označite stikalo "tisk" za proizvodnjo A4 strani za tisk in rute, z glavo + tabelo podrobnosti |
| Enostavna gradnja sistema | Opišite poslovno domeno — AI ustvari DB & tabele + ogrodje + popolno generiranje + registracijo menija v enem koraku |

---

## 6. Osnovni delovni tok

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Prijem tabel │ → │ Konf. polj/stikal │ → │ Generiraj enim klikom (samoprevjanje→kopija→pisanje→│ → │ Knjigovodstvo · Povrat │
│             │   │                   │   │ kompilacija→menu→ruta)                             │   │                       │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB uvoz/DDL     kontrole/validacija      Na disk pade šele po vseh kvalitativnih vratih;
                  /slovarji                ročno napisana koda se ohrani
```

---

## 7. Zgradi sistem z enim stavkom (Cevovod)

Visoka zmogljivost, usmerjena v "postavitev backenda iz nič". Ko opišete poslovno domeno, cevovod samodejno napreduje skozi 5 faz:

| Faza | Pomen |
| --- | --- |
| `CHECK` | Preveri konfiguracijo ciljnega projekta in classpath odvisnosti |
| `WRITE` | Ustvari bazo podatkov & tabele, piše ogrodje in generirane artefakte |
| `COMPILE` | Backend `mvn package` / front-end gradnja, samopreverjanje s kompilacijo v pomnilniku |
| `MENU` | Proizvaja in registrira pravice do menija (dva izhoda: artefakt SQL / neposredno interno pisanje) |
| `ROUTE` | Obnovi fragmente front-end rut |

---

## 8. Arhitektura sistema

| Repozitorij | Vloga | Zlaganje |
| --- | --- | --- |
| `codegen-server` | Backend: generacijski motor, predloge, validacija, knjigovodstvo, cevovod | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Frontend: konzola (konf. tabel / predloge / knjigovodstvo / upravljanje sistema) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (history mode) |

## 9. Tehnološko zlaganje

| Dimenzija | Izbira |
| --- | --- |
| Backend jezik / okvir | Java 21 / Spring Boot 3.2.5 |
| Trajnost | MyBatis-Plus 3.5.5 |
| Predlogni motor | FreeMarker (22 semenske predloge, spletno urejanje + vračanje različic) |
| Varnost | JWT avtentikacija |
| Baza podatkov | MySQL 8 |
| Frontend (generated projects) | Vue 3 + Element Plus + Vite |
| Frontend (platform console) | Vue 2.7 + Element UI |
| Lokalni AI | qwen2.5-coder:7b |
| API dokumentacija | springdoc-openapi (Swagger UI) |

## 10. Hitri začetek

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
mvn package -DskipTests
java -jar target/codegen-server.jar
```

```bash
cd ../codegen-web
npm install
npm run serve
```

Backend privzeto zažene vrata **8120**; frontend **9530**. Privzeti račun `admin / admin123`.

## 11. Stanje projekta

- Trenutno načrtovana različica dosega **V9.1**.
- **V9.1** · Refaktoriranje za vzdrževanje · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9
- Oba repozitorija gostujeta na Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

## 12. Avtor

- **Avtor**: kitajski programer (neodvisni razvijalec)
- **E-pošta**: `hustxiayuhong@126.com`
- **Tehnična podpora**: pišite v **kitajščini** z opisom problema, okoljem in koraki za reprodukcijo.

---

## 13. Primerjava s podobnimi orodji

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

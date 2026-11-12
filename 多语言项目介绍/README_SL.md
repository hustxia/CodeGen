# Majiang · Generator kode (CodeGen) — Predstavitev projekta

> Jeziki: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · **Slovenščina** · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> Generator kode za več projektov za **Spring Boot + Vue**: izhajajoč iz struktur tabel baz podatkov proizvaja **kompilabilno, izvedljivo** poslovno kodo za front-end in back-end z meniji in pravicami, ter upravlja celoten dostavni cikel pisanjem na disk, varnostno kopijo, knjigovodstvom (ledger) in povratom.

GitHub: https://github.com/hustxia/CodeGen

> **V8.0 - Full i18n for generated applications (including menus), 206 languages worldwide**
> Once i18n is enabled in project settings and the default / supported languages are picked,
> every generated page (buttons, table headers, field labels, dialog titles and menus) resolves
> through the `$t` runtime. Eight hand-translated packs ship out of the box (zh-CN, en-US,
> ja-JP, ko-KR, fr-FR, de-DE, es-ES, ar-SA); every other locale falls back through
> `current locale -> zh-CN -> inline Chinese`, so nothing ever renders blank. The language
> switcher lists all 206 locales and RTL languages mirror automatically. With the switch off,
> generated output stays byte-for-byte unchanged.

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
| `codegen-web` | Frontend: konzola (konf. tabel / predloge / knjigovodstvo / upravljanje sistema) | Vue 2.7 · Element UI · axios · Vue Router (history mode) |

## 9. Tehnološko zlaganje

| Dimenzija | Izbira |
| --- | --- |
| Backend jezik / okvir | Java 21 / Spring Boot 3.2.5 |
| Trajnost | MyBatis-Plus 3.5.5 |
| Predlogni motor | FreeMarker (22 semenske predloge, spletno urejanje + vračanje različic) |
| Varnost | JWT avtentikacija |
| Baza podatkov | MySQL 8 |
| Frontend | Vue 2.7 + Element UI |
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

> **Version History · V8.0**
> Full i18n for generated applications (including menus), 206 languages — `7d57aea`.

- Trenutno načrtovana različica dosega **V8.0**.
- Oba repozitorija gostujeta na Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

## 12. Avtor

- **Avtor**: kitajski programer (neodvisni razvijalec)
- **E-pošta**: `hustxiayuhong@126.com`
- **Tehnična podpora**: pišite v **kitajščini** z opisom problema, okoljem in koraki za reprodukcijo.

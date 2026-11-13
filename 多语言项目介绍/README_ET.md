# Majiang · Koodigeneraator (CodeGen) — Projekti tutvustus

> Keeled: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · **Eesti** · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> Mitmeprojektiline koodigeneraator **Spring Boot + Vue** jaoks: lähtudes andmebaasi tabeli struktuuridest toodab see **kompileeritavat, käivitatavat** ärikoodi esi- ja tagaküljele menüüde ja õigustega, ning haldab kogu tarneahelat: ketas kirjutamine, varukoopia, pearaamat (ledger) ja tagasiminek.

GitHub: https://github.com/hustxia/CodeGen

> **praegune versioon V9.1 · Hooldatavuse refaktoreerimine · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9**
> **V9.0 · **
> **V8.1** · 
> **V8.0** · 

## 1. Mis see on

CodeGen on "tabelipõhine" kooditehas. Tooge oma äritabelid sisse (andmebaasi importimine või DDL kleepimine), konfigureerige väljad ja generatsioonilülitid ning klõpsake "Generaator" — siis toodab see tagakülje Entity/Mapper/Service/Controller/DTO/VO, esikülje `api.js` ja Vue lehed, menüüd ja marsruudid, isegi A4 trükkimislehed ja integratsioonitestide skeletid, ning kirjutab kõik ohutult teie sihtprojekti.

See ei ole lihtsalt "koodi pritsimine" — see on **tarnevõrk kvaliteediväravate ja tagasiminkvõimega**.

## 2. Kes on kasutajad ja kuidas nad seda kasutavad

| Kasutaja | Tüüpiline vajadus | Tee, mille platvorm pakub |
| --- | --- | --- |
| Back-end arendaja | Käes on hulk tabeleid (või DDL) ja tahab kiiresti üles seada CRUD-i | Tabeli konfiguratsiooni leht: import → väljade konfigureerimine → ühe klõpsuga genereerimine |
| Full-stack / sõltumatu arendaja | Tahab nullist ehitada täieliku süsteemi (sisaldades sisselogimist, õigusi, sõnastikku) | Toru leht: ehitage süsteem ühe lausega |
| Meeskonna juht | Tahab ühtlustada koodistiili, koodi konventsioone ja kataloogistruktuuri | Süsteemi haldamine: üks süsteem = üks väljundkonfiguratsioon, kehtib partii haaval |
| Platvormi hooldaja | Tahab muuta malle ilma koodi muutmata | Mallide haldamine: võrgus redigeerimine, salvestamine jõustub kohe, toetab versioonitagasiminekut |



## 3. Milliseid probleeme see lahendab

| Valupunkt | Mida CodeGen teeb |

| --- | --- |
| Korduv CRUD põrgu — hunnik boilerplate'i iga tabeli jaoks | Tabelitasemelised lülitid toodavad ühe klõpsuga täieliku esi- ja tagakülje, sealhulgas CRUD, leheküljendus, valideerimine, import/eksport |
| Esikülje ja tagakülje väljade ning API-vahelised lahknevused | Sama tabeli metaandmed juhivad nii tagakülje API-sid kui ka esikülje lehti — null semantilist kõrvalekallet |
| Teamsihtnorne on raske ühtlustada; igaüks kirjutab oma stiilis | Mallid on sihtnorm: 22 FreeMarker-malli tsentraalselt haldatud, võrgus redigeeritavad koos versioonitagasiminekuga |
| Generaatorid "kirjutavad üle ja kaotavad" käsitsi kirjutatud loogika | Markerplokkide ühendamise mehhanism: käsitsi kirjutatud kood säilitatakse, ainult genereeritud piirkonnad asendatakse |
| Genereeritud kood on enne kompileerimise aega ebausaldusväärne | Mälu sees kompileerimise enesekontroll (tõeline `javac`), struktuuride lepingu kontrollid, diff-eelvaate ketas — midagi ei jõua kettale välja, kui see ei kompileeri |
| Uue süsteemi ehitamine nullist on aeganõudev | Toru "ehita süsteem ühe lausega": loo DB → raamistik → genereeri → paki → registreeri menüüd, täiesti automatiseeritud |

## 4. Võimaluste ülevaade

- **Tabeli vastuvõtt**: DDL-i parsimine, otsene andmebaasi importimine, partii vastuvõtt veebimeister, automaatselt tuletatud veeru konfiguratsioon (kontrollid / valideerimine / sõnastikud / seosed).
- **Tabeli kujud**: lihtne tabel, puutabel (tree API + puugrid), master-detail (salvestamine samas tehingus + detailide vahelehed), mitme-mitme seostabelid.
- **Genereeritud artefaktid**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue lehed + A4 trükkimislehed + integratsioonitestide skeletid.
- **Äri täiendused**: seose rippmenüüd, sõnastikuga tõlkimine, andmete varjamine, ainulaadsuse kontrollid, vormi ühendamine, andmeõigused, Excel import/eksport, heakskiidu töövoog, makse / fail / mobiil võimekus pakett.
- **Kvaliteediväravad**: mälu sees kompileerimise enesekontroll (tõeline `javac`), struktuuride lepingu kontrollid, õiguste järjepidevuse kontrollid, diff-eelvaate ketas, esikülje artefaktide tegelik valideerimine.
- **Turvaline maandumine**: ketas kirjutamine + `.gen-backup` partii varukoopiad, markerplokkide ühendamine käsitsi kirjutatud koodi säilitamiseks, `gen_output` neljaseisuline pearaamat (loodud / ühendatud / vahele jäetud / nurjunud), ajalooline tagasiminek.
- **Mitme süsteemi eraldamine**: iga sihtprojekt on "süsteemi konfiguratsioon" (esi/tagakülje teed, paketinimed, gateway eesliide, sõltuvuse classpath, generatsioonilülitite vaikeväärtused).
- **AI täiendused**: ehita süsteem ühe lausega (AI modelleerimine), seemne andmete AI täiendamine, tabelitasemelised AI soovitatud konfiguratsioonid.

## 5. Tüüpilised stsenaariumid

| Stsenaarium | Kirjeldus |
| --- | --- |
| Puutabel | Hierarhilised andmed nagu tootekategooriad — automaatselt toodab tree API-d, puugrid ja ülempiiri valiku rippmenüü |
| Lihtsa tabeli CRUD | Kliendid, tooted jne. — FK rippmenüüd + sõnastikuga tõlkimine + import/eksport kohe kasutamiseks valmis |
| Master-detail | Müügitellimused + tellimuse read — salvestamine samas tehingus, detailide sahtel kahe vahelehega |
| Ainult lugemiseks / statistika | Lao vood jne. — kirjutamislülitid välja, ainult päring ja eksport |
| Trükkimisdokumendid | Märkige "trükk" lüliti A4 trükkimislehe ja marsruudi tootmiseks |
| Ühe lausega süsteemi ehitamine | Kirjeldage ärivaldkonda — AI loob DB ja tabelid + raamistik + täielik generatsioon + menüü registreerimine korraga |

---

## 6. Põhiline töövoog

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Tabeli vastuvõtt │ → │ Välj/lüliti konf. │ → │ Ühe klõpsuga genereeri (enesekontroll→koopia→kirjuta→│ → │ Pearaamat · Tagasiminek │
│              │   │                    │   │ kompileeri→menüü→marsruut)                      │   │                         │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL    kontrollid/valideerimine   Kettale jõuab välja alles pärast kõiki kvaliteediväravaid;
                   /sõnastikud                käsitsi kirjutatud kood säilitatakse
```

---

## 7. Ehita süsteem ühe lausega (Toru)

| Etapp | Tähendus |
| --- | --- |
| `CHECK` | Kontrollib sihtprojekti konfiguratsiooni ja sõltuvuse classpath'i |
| `WRITE` | Loob andmebaasi ja tabelid, kirjutab raamistiku ja genereeritud artefaktid |
| `COMPILE` | Tagakülje `mvn package` / esikülje ehitamine, mälu sees kompileerimise enesekontroll |
| `MENU` | Toodab ja registreerib menüü õigused (kaks väljundit: artefakt SQL / sisene otsene kirjutamine) |
| `ROUTE` | Üles ehitab esikülje marsruudi fragmendid |

---

## 8. Süsteemi arhitektuur

| Repositoorium | Roll | Stakk |
| --- | --- | --- |
| `codegen-server` | Tagakülg: generatsioonimootor, mallid, valideerimine, pearaamat, toru | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Esikülg: konsool (tabeli konf. / mallid / pearaamat / süsteemi haldamine) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (history mode) |

## 9. Tehnoloogiline stakk

| Mõõde | Valik |
| --- | --- |
| Tagakülje keel / raamistik | Java 21 / Spring Boot 3.2.5 |
| Püsivus | MyBatis-Plus 3.5.5 |
| Mallimootor | FreeMarker (22 seemne malli, võrgus redigeerimine + versioonitagasiminek) |
| Turvalisus | JWT autentimine |
| Andmebaas | MySQL 8 |
| Esikülg (generated projects) | Vue 3 + Element Plus + Vite |
| Esikülg (platform console) | Vue 2.7 + Element UI |
| Kohalik AI | qwen2.5-coder:7b |
| API dokumentatsioon | springdoc-openapi (Swagger UI) |

## 10. Kiire algus

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql

cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml

mvn package -DskipTests
java -jar target/codegen-server.jar

cd ../codegen-web
npm install
npm run serve
```

Tagakülg vaikimisi pordile **8120**, esikülg pordile **9530**. Vaikimisi konto `admin / admin123`.

## 11. Projekti olek

> **Version History · V9.1**
> **V9.1** · Hooldatavuse refaktoreerimine · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9 — `654b8e0 → b0d081e`.
>  — `93a12d5 + e49330b`.

- Praegune planeeritud versioonirida on jõudnud **V9.1**-ni.
- Mõlemad repositooriumid hostitakse Gitees: `https://gitee.com/hustxiayuhong/CodeGen.git`

## 12. Autor

- **Autor**: hiina programmeerija (sõltumatu arendaja)
- **E-post**: `hustxiayuhong@126.com`
- **Tehniline tugi**: palun kirjutage oma e-kiri **hiina keeles**, sealhulgas probleemi kirjeldus, runtime keskkond ja reprodutseerimise sammud.

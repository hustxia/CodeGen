# Majiang · Koodigeneraattori (CodeGen) — Projektin esittely

> Kielet: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · **Suomi** · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> Moniprojektinen koodigeneraattori **Spring Boot + Vue**: lähtökohtana tietokantataulujen rakenteet, se tuottaa **käännettävää, ajettavaa** liiketoimintakoodia etu- ja takapäähän valikkoineen ja oikeuksin, ja vastaa koko toimitussyklistä: levylle kirjoittaminen, varmuuskopiointi, kirjanpito (ledger) ja palautus.

GitHub: https://github.com/hustxia/CodeGen

## 1. Mikä tämä on

CodeGen on "taulukohtainen" kooditehdas. Tuo liiketoimintataulusi (tietokannan tuonti tai DDL-liittäminen), konfiguroi kentät ja generaatiokytkimet ja napsauta "Generoi" — niin se tuottaa takapään Entity/Mapper/Service/Controller/DTO/VO:n, etupään `api.js`-tiedoston ja Vue-sivut, valikot ja reitit, jopa A4-tulostussivut ja integraatiotestiluurungot, ja kirjoittaa kaiken turvallisesti kohdeprojektiisi.

Se ei vain "sylje koodia" — se on **toimitusputki laatuporteilla ja palautuskyvyllä**:

```
Taulukontuloa (DB-tuonti / DDL)
   → Kenttä- ja kytkinkonfiguraatio (AI-suosituksilla)
      → Ykliksgenerointi: itsetarkistus → varmuuskopiointi → levylle kirjoitus → kääntäminen/validointi → rekisteröi valikko → rakenna reitit uudelleen
         → Kirjanpitomerkintä → palautettavissa
```

---

## 2. Ketkä ovat käyttäjiä ja miten he käyttävät sitä

| Käyttäjä | Tyypillinen tarve | Polku jonka alusta tarjoaa |
| --- | --- | --- |
| Back-end-kehittäjä | Sillä on joukko tauluja (tai DDL) ja se haluaa nopeasti pystyttää CRUD:n | Taulukon konfigurointisivu: tuo → määritä kentät → generoi yhdellä klikkauksella |
| Full-stack / itsenäinen kehittäjä | Haluaa rakentaa täydellisen järjestelmän tyhjästä (ml. kirjautuminen, oikeudet, sanakirja) | Putkisivu: rakenna järjestelmä yhdellä lauseella |
| Tiiminjohtaja | Haluaa yhtenäistää koodaustyylin, koodausstandardit ja hakemistorakenteen | Järjestelmähallinta: yksi järjestelmä = yksi tulostuskonfiguraatio, eräkäsittelyssä voimaan |
| Alustan ylläpitäjä | Haluaa muuttaa malleja muuttamatta koodia | Mallinhallinta: muokkaa verkossa, tallennus tulee voimaan välittömästi, versionpalautus tuettuna |

## 3. Mitä ongelmia se ratkaisee

| Kipukohta | Mitä CodeGen tekee |
| --- | --- |
| Toistuva CRUD-helvetti — kasa boilerplateä jokaiselle taululle | Taulukohtaiset kytkimet tuottavat kokonaisen etu- ja takapään yhdellä klikkauksella, mukaan lukien CRUD, sivutus, validointi, tuonti/vienti |
| Etu/takapään kenttä- ja API-häiriöt, jotka aiheuttavat loputtomia integraatiokorjauksia | Sama taulukkometadata ohjaa sekä takapään API-rajapintoja että etupään sivuja — nolla semantista poikkeamaa |
| Tiimin käytännöt vaikea yhtenäistää; jokainen kirjoittaa omalla tyylillään | Mallit ovat käytäntö: 22 FreeMarker-mallia keskitetysti hallittu, muokattavissa verkossa versionpalautuksella |
| Generaattorit "yhkäävät ja menettävät" käsinkirjoitetun logiikan | Marker-block -yhdistämismekanismi: käsinkirjoitettu kode säilytetään, vain generoidut alueet korvataan |
| Generoitu koodi epäluotettava vasta käännösaikaan asti | Muistin sisäinen käännöksen itsetarkistus (oikea `javac`), rakenteelliset sopimustarkistukset, diff-esikatselu levylle — mikään ei laskeudu levylle ellei se käänny |
| Uuden järjestelmän rakentaminen tyhjästä on aikaa vievää | Putki "rakenna järjestelmä yhdellä lauseella": luo DB → teline → generoi → pakkaa → rekisteröi valikot, täysin automatisoidusti |

---

## 4. Ominaisuudet yleiskatsaus

- **Taulukontuloa**: DDL-parsinta, suora tietokannan tuonti, erätulonti-ohjattu toiminto, automaattisesti johdettu sarakekonfiguraatio (ohjaukset / validointi / sanakirjat / suhteet).
- **Taulukkomuodot**: yksittäinen taulu, puutaulu (tree API + puuruudukko), master-detail (tallentaminen samassa tapahtumassa + yksityiskohtavälilehdet), moni-moneen yhdistystaulut.
- **Generoidut artefaktit**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue-sivut + A4-tulostussivut + integraatiotestiluurungot.
- **Liiketoiminnan parannukset**: suhteiden avattavat luettelot, sanakirjatulostus, datan peittäminen, ainutlaatuisuustarkistukset, lomakkeen yhdistäminen, datan käyttöoikeudet, Excel tuonti/vienti, hyväksyntätyönkulku, maksu / tiedosto / mobiili ominaisuuspaketit.
- **Laatuportit**: muistin sisäinen käännöksen itsetarkistus (oikea `javac`), rakenteelliset sopimustarkistukset, oikeuksien johdonmukaisuustarkistukset, diff-esikatselu levylle, etupään artefaktien todellinen validointi.
- **Turvallinen laskeutuminen**: kirjoitus levylle + `.gen-backup` erävarmuuskopiot, marker-block -yhdistäminen käsinkirjoitetun koodin säilyttämiseksi, `gen_output` nelitilainen kirjanpito (luotu / yhdistetty / ohitettu / epäonnistunut), historiallinen palautus.
- **Monijärjestelmäeristys**: jokainen kohde projekti on "järjestelmäkonfiguraatio" (etu/takapään polut, paketinimet, gateway-etuliite, riippuvuusclasspath, generaatiokytkimen oletusarvot) — useita projekteja palvellaan rinnakkain.
- **AI-parannukset**: rakenna järjestelmä yhdellä lauseella (AI-mallinnus), siemen datan AI-parannus, taulukohtainen AI-suositettu konfiguraatio (diff-esikatselu + valittu soveltaminen).

---

## 5. Tyypilliset skenaariot

| Skenaario | Kuvaus |
| --- | --- |
| Puutaulu | Hierarkinen data kuten tuoteryhmät — tuottaa automaattisesti tree API:n, puuruudukon ja ylävalinnan avattavan luettelon |
| Yksittäisen taulun CRUD | Asiakkaat, tuotteet jne. — FK-avattavat luettelot + sanakirjatulostus + tuonti/vienti valmiina |
| Master-detail | Myyntitilaukset + tilausrivit — tallentaminen samassa tapahtumassa, yksityiskohtalaatikko kahdella välilehdellä (pääsivu / yksityiskohdat) |
| Vain luku / tilastot | Varastovirrat jne. — kirjoituskytkimet pois päältä, vain kysely ja vienti |
| Tulostusasiakirjat | Valitse "tulosta" kytkin tuottaaksesi A4-tulostussivun ja reitin, otsikko + yksityiskohtataulu |
| Yksi lause järjestelmän rakentamiseksi | Kuvaa liiketoiminta-alue — AI luo DB:n & taulut + telineen + täyden generoinnin + valikkorekisteröinnin yhdellä kertaa |

> Kaikissa skenaarioissa on ajettavia demoja (`demo_admin`-projekti) kuvakaappauksilla — katso `docs/典型场景操作指南-演示后台demo.md` (kiina).

---

## 6. Perustyönkulku

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Taulukontuloa │ → │ Kenttä/kytkinkonf. │ → │ Ykliksgeneroi (itsetarkistus→varmuuskopio→kirjoita→  │ → │ Kirjanpito · Palautus │
│              │   │                    │   │ käännä→valikko→reitti)                             │   │                      │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB-tuonti/DDL    ohjaukset/validointi     Laskeutuu levylle vasta kun kaikki laatuportit ohitettu;
                   /sanakirjat              käsinkirjoitettu koodi säilytetään
```

- **Diff-esikatselu**: generoidut artefaktit diffätään tiedosto kerrallaan verrattuna nykyiseen levytilaan; vahvistat ennen kuin mitään kirjoitetaan, välttääksesi tahattomat ylikirjoitukset.
- **Generointikirjanpito**: jokainen tiedosto tallentaa toiminnon, tavumäärän, MD5:n ja varmuuskopion sijainnin; koko erä voidaan palauttaa.

---

## 7. Rakenna järjestelmä yhdellä lauseella (Putki)

Korkean tason ominaisuus, joka kohdistuu "takapään rakentamiseen tyhjästä". Kun kuvaat liiketoiminta-alueen, putki etenee automaattisesti 5 vaiheessa:

| Vaihe | Merkitys |
| --- | --- |
| `CHECK` | Vahvistaa kohde projektin konfiguraation ja riippuvuusclasspathin |
| `WRITE` | Luo tietokannan & taulut, kirjoittaa telineen ja generoidut artefaktit |
| `COMPILE` | Takapää `mvn package` / etupään rakenne, muistin sisäinen käännöksen itsetarkistus |
| `MENU` | Tuottaa ja rekisteröi valikko-oikeudet (kaksi ulostuloa: artefakti SQL / sisäinen suora kirjoitus) |
| `ROUTE` | Rakentaa uudelleen etupään reittifragmentit |

Mikä tahansa vaiheen virhe keskeyttää välittömästi luettavan virheen avulla helppoa paikantamista varten.

---

## 8. Järjestelmäarkkitehtuuri

Etu- ja takapää eristetty, kaksi säilöä toimii yhdessä:

| Säilö | Rooli | Pino |
| --- | --- | --- |
| `codegen-server` | Takapää: generaatiomoottori, mallit, validointi, kirjanpito, putki | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Etupää: konsoli (taulukokonfig / mallit / kirjanpito / järjestelmähallinta) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (history mode) |

Takapään kerrokset:

```
controller/    API-kerros (Gen* generaatioketju / Sys* järjestelmähallinta / putki)
service/impl/  Toteutukset (generaatioketju, mallimoottori, yhdistäminen, validointi, putken suoritus)
util/          Mallimoottori, mallin kokoonpano, marker-block -yhdistäminen, itsetarkistuskääntäminen
config/        Tulostuskonfig, itsetarkistuskonfig, mallinsiemen
common/        Result / BaseEntity / poikkeukset / auditointi
resources/gen-templates/   22 FreeMarker-mallia (backend / frontend / shared / test ryhmät)
```

**Perusominaisuudet, jotka kohde projektin on tarjottava** (generaattori tuottaa vain liiketoimintakoodia; puuttuva perusta epäonnistuu hiljaisesti):

1. MyBatis-Plus sivutuslaajennus (`PaginationInnerInterceptor`)
2. Perusluokat kuten `Result` / `BaseEntity` / `PageQuery` (paketin etuliitteen on vastattava `root-package`)
3. Etupää `utils/request.js` funktiolla `toPageData` (muuntaa `Page{records,total}` muotoon `{list,total}`)
4. Riippuvuudet `springdoc-openapi-starter-webmvc-ui` (generoitu koodi käyttää `@Operation`) ja `poi-ooxml` (Excel)

---

## 9. Teknologiapino yleiskatsaus

| Ulottuvuus | Valinta |
| --- | --- |
| Takapään kieli / kehys | Java 21 / Spring Boot 3.2.5 |
| Pysyvyys | MyBatis-Plus 3.5.5 |
| Mallimoottori | FreeMarker (22 siemenmallia, verkkomuokkaus + versionpalautus) |
| Turvallisuus | JWT-todentaminen |
| Tietokanta | MySQL 8 |
| Etupää (generated projects) | Vue 3 + Element Plus + Vite |
| Etupää (platform console) | Vue 2.7 + Element UI |
| Paikallinen AI | qwen2.5-coder:7b (mallinnus / suositettu konfiguraatio / siemen datan parannus) |
| API-dokumentaatio | springdoc-openapi (Swagger UI) |

---

## 10. Nopea alku

### 1. Luo tietokanta (13 alustataulua + oletustili admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Konfiguroi kohde projekti

Kopioi konfiguraatiomalli ja muokkaa paikallisiin polkuihin sopivaksi:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` on poissuljettu `.gitignore`-tiedostossa eikä sitä commitoida. Tärkeimmät asetukset:

```yaml
gen:
  output:
    backend-root: /polku/kohteesi-projektiin      # artefaktien kirjoitusperusta
    frontend-root: /polku/kohteesi-projektiin
    frontend-path: demo-web                          # etupään alikansio monorepoissa
    backend-path: demo-server/src/main/java/com/demo
    package-name: com.demo
    root-package: com.demo                           # etuliite perusluokkien tuontiin artefakteissa
  selfcheck:
    classpath: /polku/kohteesi-projektiin/demo-lib/*
```

Vie itsetarkistus riippuvuuskansio näin:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Käynnistä takapää

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

Palvelin käyttää oletusarvoisesti porttia **8120**; API-dokumentaatio osoitteessa `/swagger-ui/index.html`.

### 4. Käynnistä etupää

```bash
cd ../codegen-web
npm install
npm run serve
```

Etupää käyttää oletusarvoisesti porttia **9530**, välittää `/dev-api` takapään porttiin 8120. Oletustili `admin / admin123`.

### 5. Tärkeät konfigurointikohteet

| Asetus | Kuvaus |
| --- | --- |
| `gen.output.*` | Artefaktien kirjoituspolut, paketinimet, moduulien kartoitus |
| `gen.selfcheck.classpath` | Riippuvuusclasspath muistin sisäiseen käännöksen itsetarkistukseen, tukee `dir/*` |
| `gen.menu.outlet` | `artifact` tuottaa vain valikon SQL:n & reittifragmentit; `internal` kirjoittaa myös alustan oman `sys_menu`-taulun |

---

## 11. Dokumentaatiokartta

| Asiakirja | Sisältö |
| --- | --- |
| `docs/代码生成器-操作手册.md` (kiina) | Asennus, 5 minuutin aloitus, taulukkonfig, erätulonti, mallinhallinta, kirjanpito, API-luettelo, täysi putki (kuvakaappauksilla) |
| `docs/典型场景操作指南-演示后台demo.md` (kiina) | 6 taulua jotka kattavat 5 tyypillistä skenaariota, ajettava demo ja todelliset kuvakaappaukset |
| `docs/项目介绍.md` | Projektin esittely asiakirja, sama lähde kuin tämä tiedosto |
| `docs/代码生成器-Vx.x-规划.md` | Versiosuunnitelmat ja omaisuusinventaariot V3.1 → V7.3 |
| `codegen-web/README.md` | Etupääkonsolin nopea aloitusviite |

---

## 12. Versiohistoria

| Versio | Teema / suunnitteludokumentti | Laskeutumiscommit |
| --- | --- | --- |
| V1.2 / V1.3 | Ykliksgenerointi / erä / palautus; diff-esikatselu & rakenne sync | — (vain `src/api/gen.js` kommenteissa) |
| V2.0 | Dynaamiset reitit (takapään toimittama valikkopuu) | — |
| V3.0 / V3.1 | Monijärjestelmäkirjanpito; monijärjestelmäluuppi + ensimmäiset automatisoidut testit | V3.1 (174 riviä) |
| V4.2 | Jäljitettävät, palautettavat generaatiotulokset (levykirjanpito) | V4.2 (292 riviä) |
| V4.3 | Malliregressio & laatuvarot | V4.3 (200 riviä) |
| V4.4 | Yksi-mestari-many-yksityiskohta, kaskadikirjoitus täydellisyys, mallikaistat | V4.4 (154 riviä) |
| V4.5 | A4-tulostusasiakirjat (nolla kolmannen osapuolen riippuvuutta) | V4.5 (127 riviä) |
| V5.0 | Yksi lause järjestelmän rakentamiseksi: putki + AI-mallinnus + telineperusta | — |
| V5.1 | DDL auditointisarakkeet automaattinen täydentäminen | V5.1 (100 riviä) |
| V5.2 | Putki siemendatan alustusaskel | V5.2 (107 riviä), 84ec8dd |
| V5.5 | Monimoduliprojektin generoinnin viimeistely | af73dd3 |
| V6.0 | Mikropalveluinfrastruktuuri (Nacos / Gateway / Feign, kolmas tila "micro") | V6.0 (111 riviä), 1deecbd |
| V6.1 | Activiti työnkulku kaksoismoottori | V6.1 (100 riviä), 02b671e |
| V6.2 | Artefaktien toimituspinta (CLI / deploy-artefaktit / konfiguraatioturvallisuus) | V6.2 (114 riviä), 02b671e |
| V6.3 | Maksuominaisuuspaketti (WeChat / Alipay + Mock) | V6.3 (104 riviä), 1c4ee6a, 22eede5 |
| V6.4 | Oikeudet & turvallisuusperusta (token persistenssi / BCrypt / API-oikeudet / kirjautumissuoja) | V6.4 (107 riviä), 007a202 |
| V6.5 | Mobiili uni-app (H5 + WeChat Mini Program) | V6.5 (105 riviä), 779ac41 |
| V6.6 | Tiedosto-ominaisuuspaketti | 98f3c09 |
| V6.7 | Artefaktitestin tehdasvastaanotto (test_enabled kolmipitoisuus + H2-luuranko) | V6.7 (134 riviä), 4e7d318 |
| V6.8 | Artefaktien ylläpidettävyys (dryRun / suojattu ohitus / tuotemerkin parametrisointi) | V6.8 (141 riviä), be6f858 |
| V6.9 | Alustadokumentointikeskus (takapääperusta, etupää valmis samassa erässä) | — |
| V6.10 | Alustan havaittavuus: Actuator + muistin sisäinen metrinen ikkuna + upotettu monitorsivu | — |
| V7.0 | AI sivu & valikkosuositukset (preview / apply) | V7.0 (94 riviä), 45f2670 |
| V7.1 | Siemendatan AI-parannus (AI hallitsee semantiikkaa, säännöt hallitsevat rakennetta) | V7.1 (42 riviä), 5ca6e72 |
| V7.2 | SQL-toimitusobjektit kirjoitettu kohteeseen `db/` putken onnistuneen jälkeen | eebff88 |
| **V7.3** | **Toimitusluotettavuus: itsetarkistus käännetyt artefaktitestit / yhtenäinen SQL-toimitusulostulo / taulukohtaiset AI-suositukset** | **V7.3 (138 riviä)** |
| **V7.4** | Toistettava toimitus: deployoitavissa tyhjästä tietokannasta, jokainen commit taattu ajettavaksi | c306e29 |
| **V7.5** | Projektin kieliasetukset: 206 kielen luettelo / oletuskieli / käytössä olevat kielet, lang- ja dir-kenttien lisäys generoituihin tiedostoihin | 7a9afa0 + abac56d |
| **V8.0** | Koko sivun i18n: 206 kielen kehys (8 ammattimaisesti käännetty), kaksiraiteinen i18n OFF zero-diff-sopimuksella | 9e5dd57 → 7d57aea |
| **V8.1** | Laadun vahvistus: offline-sopimustesti (9 tilaa, OFF zero-diff) + i18n-core-yksikkötestit + CI-portti + rehellinen README-jako (kehys 206 / käännetty 8) + check-locale-keys.mjs | 93a12d5 + e49330b |
| **V9.0** | Generoidut artefaktit päivitetty Vue 3 + Element Plus + Viteen; ajonaikaisesti todennettu (40/40 assertiota, onnistunut vite build, 14 kuvakaappausta incl. ar-SA RTL); 3 ajonaikaista virhettä korjattu | 93a12d5 + e49330b  |
| **V9.1** | **Ylläpidettävyyden refaktorointi · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9** | 654b8e0 → b0d081e  |
| **V9.2** | **Testikeskus & varmennuskeskus · Test Center (7 tables: env/suite/case/dataset/run/result/schedule) + Verification Center (tc_run/tc_case_result/tc_artifact, 9 suites S1-S9, single-run 409 lock, `codegen.testcenter/v1`); 373/373 tests; 206/206 locales; output byte-identical** | V9.2 release  |
| **V9.3** | **Generoitujen artefaktien automatisoidut testit · Generated-artifact automated testing: S10 (mvn test + Surefire), S11 (API / TestContainers), S12 (E2E / Playwright); GEN trigger + TC_GEN_ROOT; 12-step pipeline green; real artifact run PASSED 20/20, run/case/artifact persisted** | V9.3 release, **nykyinen versio** |

---

> **V6.10 Alustan havaittavuus**: integroi Actuator + Micrometer, näyttelee joka 5. sekunti muistin sisäiseen rengaspuskuriin (30 minuutin ikkuna), sekä uusi upotettu "Järjestelmävalvonta"-sivu — takapään `/monitor/*` aggregointi API-rajapinnat + etupään ECharts-paneelit. Vain `health/info/metrics` päätepisteet ovat sallittujen listalla; `/actuator/health` on vapaa tokeneista kun taas `metrics` jne. vaativat todennuksen; `env/beans/heapdump/threaddump/loggers` ei koskaan paljasteta. Näyteikkuna on vain prosessikohtainen, menetetään uudelleenkäynnistyksessä, eikä sitä jaeta instanssien välillä. Katso `docs/可观测性说明.md` (kiina) lisätietoja varten.

## 13. Projektin tila

- Nykyinen suunniteltu versiolinja on saavuttanut **V9.3** (ominaisuudet jatkavat iterointia: AI-mallinnus, siemendatan parannus, ominaisuuspakettijärjestelmä jne.).
- Mukana oleva demo-projekti `demo_admin` (takapää 8140 / etupää 9040) kattaa puun / CRUD:n / master-detail:n / vain luku / tulostus skenaariot ja toimii itsenäisesti verifiointia varten.
- Molemmat säilöt isännöidään Giteessä: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. Tekijä & tuki

- **Tekijä**: kiinalainen ohjelmoija (itsenäinen kehittäjä)
- **Sähköposti**: `hustxiayuhong@126.com`
- **Tekninen tuki**: kirjoita sähköpostisi **kiinaksi**, mukaan lukien ongelman kuvaus, ajoympäristö (JDK / MySQL / versio) ja toistettavuusvaiheet

> Luen ja vastaan jokaiseen sähköpostiin. Vikatilanteet, ominaisuuspyynnöt ja yhteistyöehdotukset ovat kaikki tervetulleita.

---

## 15. Vertailu samankaltaisiin työkaluihin

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

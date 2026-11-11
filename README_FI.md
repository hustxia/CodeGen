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

## 2. Mitä ongelmia se ratkaisee

| Kipukohta | Mitä CodeGen tekee |
| --- | --- |
| Toistuva CRUD-helvetti — kasa boilerplateä jokaiselle taululle | Taulukohtaiset kytkimet tuottavat kokonaisen etu- ja takapään yhdellä klikkauksella, mukaan lukien CRUD, sivutus, validointi, tuonti/vienti |
| Etu/takapään kenttä- ja API-häiriöt, jotka aiheuttavat loputtomia integraatiokorjauksia | Sama taulukkometadata ohjaa sekä takapään API-rajapintoja että etupään sivuja — nolla semantista poikkeamaa |
| Tiimin käytännöt vaikea yhtenäistää; jokainen kirjoittaa omalla tyylillään | Mallit ovat käytäntö: 22 FreeMarker-mallia keskitetysti hallittu, muokattavissa verkossa versionpalautuksella |
| Generaattorit "yhkäävät ja menettävät" käsinkirjoitetun logiikan | Marker-block -yhdistämismekanismi: käsinkirjoitettu kode säilytetään, vain generoidut alueet korvataan |
| Generoitu koodi epäluotettava vasta käännösaikaan asti | Muistin sisäinen käännöksen itsetarkistus (oikea `javac`), rakenteelliset sopimustarkistukset, diff-esikatselu levylle — mikään ei laskeudu levylle ellei se käänny |
| Uuden järjestelmän rakentaminen tyhjästä on aikaa vievää | Putki "rakenna järjestelmä yhdellä lauseella": luo DB → teline → generoi → pakkaa → rekisteröi valikot, täysin automatisoidusti |

---

## 3. Ominaisuudet yleiskatsaus

- **Taulukontuloa**: DDL-parsinta, suora tietokannan tuonti, erätulonti-ohjattu toiminto, automaattisesti johdettu sarakekonfiguraatio (ohjaukset / validointi / sanakirjat / suhteet).
- **Taulukkomuodot**: yksittäinen taulu, puutaulu (tree API + puuruudukko), master-detail (tallentaminen samassa tapahtumassa + yksityiskohtavälilehdet), moni-moneen yhdistystaulut.
- **Generoidut artefaktit**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue-sivut + A4-tulostussivut + integraatiotestiluurungot.
- **Liiketoiminnan parannukset**: suhteiden avattavat luettelot, sanakirjatulostus, datan peittäminen, ainutlaatuisuustarkistukset, lomakkeen yhdistäminen, datan käyttöoikeudet, Excel tuonti/vienti, hyväksyntätyönkulku, maksu / tiedosto / mobiili ominaisuuspaketit.
- **Laatuportit**: muistin sisäinen käännöksen itsetarkistus (oikea `javac`), rakenteelliset sopimustarkistukset, oikeuksien johdonmukaisuustarkistukset, diff-esikatselu levylle, etupään artefaktien todellinen validointi.
- **Turvallinen laskeutuminen**: kirjoitus levylle + `.gen-backup` erävarmuuskopiot, marker-block -yhdistäminen käsinkirjoitetun koodin säilyttämiseksi, `gen_output` nelitilainen kirjanpito (luotu / yhdistetty / ohitettu / epäonnistunut), historiallinen palautus.
- **Monijärjestelmäeristys**: jokainen kohde projekti on "järjestelmäkonfiguraatio" (etu/takapään polut, paketinimet, gateway-etuliite, riippuvuusclasspath, generaatiokytkimen oletusarvot) — useita projekteja palvellaan rinnakkain.
- **AI-parannukset**: rakenna järjestelmä yhdellä lauseella (AI-mallinnus), siemen datan AI-parannus, taulukohtainen AI-suositettu konfiguraatio (diff-esikatselu + valittu soveltaminen).

---

## 4. Tyypilliset skenaariot

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

## 5. Perustyönkulku

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

## 6. Rakenna järjestelmä yhdellä lauseella (Putki)

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

## 7. Järjestelmäarkkitehtuuri

Etu- ja takapää eristetty, kaksi säilöä toimii yhdessä:

| Säilö | Rooli | Pino |
| --- | --- | --- |
| `codegen-server` | Takapää: generaatiomoottori, mallit, validointi, kirjanpito, putki | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Etupää: konsoli (taulukokonfig / mallit / kirjanpito / järjestelmähallinta) | Vue 2.7 · Element UI · axios · Vue Router (history mode) |

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

## 8. Teknologiapino yleiskatsaus

| Ulottuvuus | Valinta |
| --- | --- |
| Takapään kieli / kehys | Java 21 / Spring Boot 3.2.5 |
| Pysyvyys | MyBatis-Plus 3.5.5 |
| Mallimoottori | FreeMarker (22 siemenmallia, verkkomuokkaus + versionpalautus) |
| Turvallisuus | JWT-todentaminen |
| Tietokanta | MySQL 8 |
| Etupää | Vue 2.7 + Element UI |
| Paikallinen AI | qwen2.5-coder:7b (mallinnus / suositettu konfiguraatio / siemen datan parannus) |
| API-dokumentaatio | springdoc-openapi (Swagger UI) |

---

## 9. Nopea alku

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

## 10. Dokumentaatiokartta

| Asiakirja | Sisältö |
| --- | --- |
| `docs/代码生成器-操作手册.md` (kiina) | Asennus, 5 minuutin aloitus, taulukkonfig, erätulonti, mallinhallinta, kirjanpito, API-luettelo, täysi putki (kuvakaappauksilla) |
| `docs/典型场景操作指南-演示后台demo.md` (kiina) | 6 taulua jotka kattavat 5 tyypillistä skenaariota, ajettava demo ja todelliset kuvakaappaukset |
| `docs/项目介绍.md` | Projektin esittely asiakirja, sama lähde kuin tämä tiedosto |
| `docs/代码生成器-Vx.x-规划.md` | Versiosuunnitelmat ja omaisuusinventaariot V3.1 → V7.3 |
| `codegen-web/README.md` | Etupääkonsolin nopea aloitusviite |

---

## 11. Versiohistoria

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
| **V7.4** | Toistettava toimitus: deployoitavissa tyhjästä tietokannasta, jokainen commit taattu ajettavaksi | **Nykyinen versio** |

---

> **V6.10 Alustan havaittavuus**: integroi Actuator + Micrometer, näyttelee joka 5. sekunti muistin sisäiseen rengaspuskuriin (30 minuutin ikkuna), sekä uusi upotettu "Järjestelmävalvonta"-sivu — takapään `/monitor/*` aggregointi API-rajapinnat + etupään ECharts-paneelit. Vain `health/info/metrics` päätepisteet ovat sallittujen listalla; `/actuator/health` on vapaa tokeneista kun taas `metrics` jne. vaativat todennuksen; `env/beans/heapdump/threaddump/loggers` ei koskaan paljasteta. Näyteikkuna on vain prosessikohtainen, menetetään uudelleenkäynnistyksessä, eikä sitä jaeta instanssien välillä. Katso `docs/可观测性说明.md` (kiina) lisätietoja varten.

## 12. Projektin tila

- Nykyinen suunniteltu versiolinja on saavuttanut **V7.3** (ominaisuudet jatkavat iterointia: AI-mallinnus, siemendatan parannus, ominaisuuspakettijärjestelmä jne.).
- Mukana oleva demo-projekti `demo_admin` (takapää 8140 / etupää 9040) kattaa puun / CRUD:n / master-detail:n / vain luku / tulostus skenaariot ja toimii itsenäisesti verifiointia varten.
- Molemmat säilöt isännöidään Giteessä: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 13. Tekijä & tuki

- **Tekijä**: kiinalainen ohjelmoija (itsenäinen kehittäjä)
- **Sähköposti**: `hustxiayuhong@126.com`
- **Tekninen tuki**: kirjoita sähköpostisi **kiinaksi**, mukaan lukien ongelman kuvaus, ajoympäristö (JDK / MySQL / versio) ja toistettavuusvaiheet

> Luen ja vastaan jokaiseen sähköpostiin. Vikatilanteet, ominaisuuspyynnöt ja yhteistyöehdotukset ovat kaikki tervetulleita.

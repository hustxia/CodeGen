# Majiang · కోడ్ జెనరేటర్ (CodeGen) — ప్రాజెక్ట్ పరిచయం

> భాషలు: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · **తెలుగు** · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> **Spring Boot + Vue** కోసం బహుళ-ప్రాజెక్ట్ కోడ్ జెనరేటర్: డేటాబేస్ పట్టిక నిర్మాణాల నుండి ప్రారంభించి, **సంకలనం చేయదగిన, అమలు చేయదగిన** మెనూలు మరియు హక్కులతో ముందు మరియు వెనుక చివర వ్యాపార కోడ్‌ను ఉత్పత్తి చేస్తుంది, మరియు డిస్క్‌కు వ్రాయడం, బ్యాకప్, లెడ్జర్ మరియు రోల్‌బ్యాక్ యొక్క పూర్తి డెలివరీ చక్రాన్ని నిర్వహిస్తుంది.

GitHub: https://github.com/hustxia/CodeGen

## 1. ఇది ఏమిటి

CodeGen అనేది "పట్టిక-ఆధారిత" కోడ్ ఫ్యాక్టరీ. మీ వ్యాపార పట్టికలను తీసుకురండి (డేటాబేస్ దిగుమతి లేదా DDL పేస్ట్ చేయడం ద్వారా), ఫీల్డ్‌లు మరియు జనరేషన్ స్విచ్‌లను కాన్ఫిగర్ చేయండి, తర్వాత "జనరేట్" క్లిక్ చేయండి — అప్పుడు అది వెనుక చివర Entity/Mapper/Service/Controller/DTO/VO, ముందు చివర `api.js` మరియు Vue పేజీలు, మెనూలు మరియు రూట్‌లు, A4 ప్రింట్ పేజీలు మరియు ఇంటిగ్రేషన్ టెస్ట్ అస్థిపంజరాలను కూడా ఉత్పత్తి చేస్తుంది, మరియు అన్నింటినీ మీ లక్ష్య ప్రాజెక్ట్‌లోకి సురక్షితంగా వ్రాస్తుంది.

ఇది కేవలం "కోడ్ బయటకు పంపడం" కాదు — ఇది **నాణ్యత గేట్లు మరియు రోల్‌బ్యాక్ సామర్థ్యం కలిగిన డెలివరీ పైప్‌లైన్**:

```
పట్టిక తీసుకోవడం (DB దిగుమతి / DDL)
   → ఫీల్డ్ & స్విచ్ కాన్ఫిగరేషన్ (AI సిఫార్సులతో)
      → ఒక-క్లిక్ జనరేషన్: స్వీయ-తనిఖీ → బ్యాకప్ → డిస్క్‌కు వ్రాయండి → కంపైల్/వాలిడేట్ → మెను రిజిస్టర్ → రూట్‌లు పునర్నిర్మాణం
         → లెడ్జర్ బుక్-కీపింగ్ → రోల్‌బ్యాక్ చేయదగినది
```

## 2. వినియోగదారులు ఎవరు, ఎలా ఉపయోగిస్తారు

| వినియోగదారు | సాధారణ డిమాండ్ | ప్లాట్‌ఫారమ్ ఇచ్చే మార్గం |
| --- | --- | --- |
| బ్యాక్-ఎండ్ డెవలపర్ | చేతిలో కొన్ని పట్టికలు (లేదా DDL) ఉన్నాయి, వేగంగా CRUD పెంచాలనుకుంటున్నారు | పట్టిక కాన్ఫిగ్ పేజీ: దిగుమతి → ఫీల్డ్‌లను కాన్ఫిగర్ చేయండి → ఒక క్లిక్‌లో జనరేట్ |
| ఫుల్-స్టాక్ / స్వతంత్ర డెవలపర్ | శూన్యం నుండి పూర్తి సిస్టమ్ (లాగిన్, హక్కులు, డిక్షనరీ సహా) నిర్మించాలనుకుంటున్నారు | పైప్‌లైన్ పేజీ: ఒక వాక్యంతో సిస్టమ్ బిల్డ్ |
| టీమ్ లీడ్ | కోడ్ శైలి, కోడ్ ప్రమాణాలు, డైరెక్టరీ నిర్మాణాన్ని ఏకీకృతం చేయాలనుకుంటున్నారు | సిస్టమ్ మేనేజ్‌మెంట్: ఒక సిస్టమ్ = ఒక అవుట్‌పుట్ కాన్ఫిగరేషన్, బ్యాచ్‌గా అమల్లోకి వస్తుంది |
| ప్లాట్‌ఫారమ్ నిర్వాహకుడు | కోడ్ మార్చకుండా టెంప్లేట్లను మార్చాలనుకుంటున్నారు | టెంప్లేట్ మేనేజ్‌మెంట్: ఆన్‌లైన్‌లో సవరించండి, సేవ్ చేసినప్పుడు వెంటనే అమల్లోకి వస్తుంది, చారిత్రక రోల్‌బ్యాక్‌ను మద్దతు ఇస్తుంది |

## 3. ఏ సమస్యలు పరిష్కరిస్తుంది

| బాధాకరమైన పాయింట్ | CodeGen ఏమి చేస్తుంది |
| --- | --- |
| పునరావృత CRUD నరకం — ప్రతి పట్టికకు బాయిలర్‌ప్లేట్ కుప్ప | పట్టిక-స్థాయి స్విచ్‌లు ఒక క్లిక్‌తో పూర్తి ముందు & వెనుక చివరను ఉత్పత్తి చేస్తాయి, CRUD, పేజినేషన్, వాలిడేషన్, ఇంపోర్ట్/ఎక్స్‌పోర్ట్‌తో సహా |
| ముందు/వెనుక ఫీల్డ్ & API మిస్‌మ్యాచ్‌లు | అదే పట్టిక మెటాడేటా వెనుక చివర APIలు మరియు ముందు చివర పేజీలు రెండింటినీ నడిపిస్తుంది — జీరో సెమాంటిక్ విచలనం |
| టీమ్ సంప్రదాయాలు ఏకం చేయడం కష్టం; అందరూ తమ శైలిలో వ్రాస్తారు | టెంప్లేట్లు సంప్రదాయం: 22 FreeMarker టెంప్లేట్లు కేంద్రంగా నిర్వహించబడతాయి, ఆన్‌లైన్‌లో సవరించబడతాయి మరియు వెర్షన్ రోల్‌బ్యాక్‌తో |
| జెనరేటర్లు "ఓవర్‌రైట్ చేసి కోల్పోతారు" చేతితో వ్రాసిన లాజిక్ | మార్కర్-బ్లాక్ మెర్జ్ మెకానిజం: చేతితో వ్రాసిన కోడ్ సంరక్షించబడుతుంది, మాత్రమే గెరరేటెడ్ ప్రాంతాలు భర్తీ చేయబడతాయి |
| జనరేటెడ్ కోడ్ కంపైల్ సమయం వరకు నమ్మదగనిది | మెమరీలో కంపైల్ స్వీయ-తనిఖీ (నిజమైన `javac`), స్ట్రక్చరల్ కాంట్రాక్ట్ తనిఖీలు, డిస్క్‌పై డిఫ్ ప్రివ్యూ — కంపైల్ కాకపోతే ఏమీ డిస్క్‌లో దిగదు |
| కొత్త సిస్టమ్‌ను శూన్యం నుండి ఏర్పాటు చేయడం సమయం తీసుకుంటుంది | పైప్‌లైన్ "ఒక వాక్యంతో సిస్టమ్ బిల్డ్": DB → స్కాఫోల్డ్ → జనరేట్ → ప్యాక్ → మెనూలు రిజిస్టర్, పూర్తిగా ఆటోమేటెడ్ |

## 4. సామర్థ్యాల అవలోకనం

- **పట్టిక తీసుకోవడం**: DDL పార్సింగ్, డైరెక్ట్ డేటాబేస్ దిగుమతి, బ్యాచ్ టేక్ విజార్డ్, ఆటో-డెరైవ్డ్ కాలమ్ కాన్ఫిగరేషన్ (కంట్రోల్స్ / వాలిడేషన్ / డిక్షనరీలు / రిలేషన్స్).
- **పట్టిక రూపాలు**: ఒక్క పట్టిక, ట్రీ పట్టిక (ట్రీ API + ట్రీ గ్రిడ్), మాస్టర్-డీటైల్ (అదే ట్రాన్సాక్షన్‌లో సేవ్ + డీటైల్ ట్యాబ్‌లు), మెనీ-టు-మెనీ జంక్షన్ పట్టికలు.
- **జనరేటెడ్ ఆర్టిఫ్యాక్ట్‌లు**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue పేజీలు + A4 ప్రింట్ పేజీలు + ఇంటిగ్రేషన్ టెస్ట్ అస్థిపంజరాలు.
- **వ్యాపార మెరుగుదలలు**: రిలేషన్ డ్రాప్‌డౌన్‌లు, డిక్షనరీ అనువాదం, డేటా మాస్కింగ్, యూనిక్‌నెస్ తనిఖీలు, ఫారమ్ లింకేజ్, డేటా స్కోప్ హక్కులు, Excel ఇంపోర్ట్/ఎక్స్‌పోర్ట్, అప్రూవల్ వర్క్‌ఫ్లో, పేమెంట్ / ఫైల్ / మొబైల్ సామర్థ్యం ప్యాక్‌లు.
- **నాణ్యత గేట్లు**: మెమరీలో కంపైల్ స్వీయ-తనిఖీ (నిజమైన `javac`), స్ట్రక్చరల్ కాంట్రాక్ట్ తనిఖీలు, హక్కులు కన్సిస్టెన్సీ తనిఖీలు, డిస్క్‌పై డిఫ్ ప్రివ్యూ, ముందు చివర ఆర్టిఫ్యాక్ట్‌ల నిజమైన వాలిడేషన్.
- **సురక్షిత ల్యాండింగ్**: డిస్క్‌కు వ్రాయడం + `.gen-backup` బ్యాచ్ బ్యాకప్‌లు, మార్కర్-బ్లాక్ మెర్జ్ చేతితో వ్రాసిన కోడ్‌ను సంరక్షించడం, `gen_output` నాలుగు-స్థితి లెడ్జర్ (సృష్టించబడింది / విలీనం చేయబడింది / దాటవేయబడింది / విఫలమైంది), చారిత్రక రోల్‌బ్యాక్.
- **బహుళ సిస్టమ్ ఐసోలేషన్**: ప్రతి లక్ష్య ప్రాజెక్ట్ ఒక "సిస్టమ్ కాన్ఫిగరేషన్" (ముందు/వెనుక మార్గాలు, ప్యాకేజ్ పేర్లు, గేట్‌వే ప్రిఫిక్స్, డిపెండెన్సీ క్లాస్‌పాత్, జనరేషన్ స్విచ్ డిఫాల్ట్‌లు).
- **AI మెరుగుదలలు**: ఒక వాక్యంతో సిస్టమ్ బిల్డ్ (AI మోడలింగ్), సీడ్ డేటా AI మెరుగుదల, పట్టిక-స్థాయి AI సిఫార్సు చేయబడిన కాన్ఫిగరేషన్.

## 5. విలక్షణ దృశ్యాలు

| దృశ్యం | వివరణ |
| --- | --- |
| ట్రీ పట్టిక | ఉత్పత్తి వర్గాలు వంటి క్రమానుగత డేటా — ట్రీ API, ట్రీ గ్రిడ్ మరియు పేరెంట్ సెలెక్ట్ డ్రాప్‌డౌన్‌ను ఆటోమేటిక్‌గా ఉత్పత్తి చేస్తుంది |
| ఒక్క పట్టిక CRUD | కస్టమర్లు, ఉత్పత్తులు మొదలైనవి — FK డ్రాప్‌డౌన్‌లు + డిక్షనరీ అనువాదం + ఇంపోర్ట్/ఎక్స్‌పోర్ట్ అవుట్ ఆఫ్ ద బాక్స్ |
| మాస్టర్-డీటైల్ | అమ్మకాల ఆర్డర్‌లు + ఆర్డర్ ఐటెమ్‌లు — అదే ట్రాన్సాక్షన్‌లో సేవ్, డీటైల్ డ్రాయర్ డ్యూయల్ ట్యాబ్‌లతో (మాస్టర్ ఇన్ఫో / డీటైల్స్) |
| రీడ్-ఓన్లీ / స్టాట్స్ | ఇన్వెంటరీ ఫ్లోస్ మొదలైనవి — రైట్ స్విచ్‌లు ఆఫ్, కేవలం క్వెరీ మరియు ఎక్స్‌పోర్ట్ |
| ప్రింట్ డాక్యుమెంట్‌లు | A4 ప్రింట్ పేజీ మరియు రూట్‌ను ఉత్పత్తి చేయడానికి "ప్రింట్" స్విచ్‌ను టిక్ చేయండి |
| ఒక వాక్యంతో సిస్టమ్ బిల్డ్ | వ్యాపార డొమైన్‌ను వివరించండి — AI ఒకేసారి DB & పట్టికలు + స్కాఫోల్డ్ + పూర్తి జనరేషన్ + మెను రిజిస్ట్రేషన్‌ను సృష్టిస్తుంది |

## 6. ప్రాథమిక పనిఫ్లో

```
పట్టిక తీసుకోవడం → ఫీల్డ్/స్విచ్ కాన్ఫిగరేషన్ → ఒక క్లిక్ జనరేట్ → లెడ్జర్ · రోల్‌బ్యాక్
DB దిగుమతి/DDL   కంట్రోల్స్/వాలిడేషన్   అన్ని నాణ్యత గేట్ల తర్వాత మాత్రమే డిస్క్‌లో దిగుతుంది
                 /డిక్షనరీలు            చేతితో వ్రాసిన కోడ్ సంరక్షించబడుతుంది
```

## 7. ఒక వాక్యంతో సిస్టమ్ బిల్డ్ (పైప్‌లైన్)

| దశ | అర్థం |
| --- | --- |
| `CHECK` | లక్ష్య ప్రాజెక్ట్ కాన్ఫిగరేషన్ మరియు డిపెండెన్సీ క్లాస్‌పాత్‌ను ధృవీకరిస్తుంది |
| `WRITE` | డేటాబేస్ & పట్టికలను సృష్టిస్తుంది, స్కాఫోల్డ్ మరియు జనరేటెడ్ ఆర్టిఫ్యాక్ట్‌లను వ్రాస్తుంది |
| `COMPILE` | వెనుక చివర `mvn package` / ముందు చివర బిల్డ్, మెమరీలో కంపైల్ స్వీయ-తనిఖీ |
| `MENU` | మెను హక్కులను ఉత్పత్తి చేస్తుంది మరియు రిజిస్టర్ చేస్తుంది (రెండు అవుట్‌లెట్‌లు: ఆర్టిఫ్యాక్ట్ SQL / అంతర్గత డైరెక్ట్ రైట్) |
| `ROUTE` | ముందు చివర రూట్ ఫ్రాగ్‌మెంట్‌లను పునర్నిర్మిస్తుంది |

## 8. సిస్టమ్ ఆర్కిటెక్చర్

| రిపోజిటరీ | పాత్ర | స్టాక్ |
| --- | --- | --- |
| `codegen-server` | వెనుక చివర: జనరేషన్ ఇంజిన్, టెంప్లేట్లు, వాలిడేషన్, లెడ్జర్, పైప్‌లైన్ | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | ముందు చివర: కన్సోల్ (పట్టిక కాన్ఫిగ్ / టెంప్లేట్లు / లెడ్జర్ / సిస్టమ్ మేనేజ్‌మెంట్) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (హిస్టరీ మోడ్) |

## 9. సాంకేతిక స్టాక్

| పరిమాణం | ఎంపిక |
| --- | --- |
| వెనుక చివర భాష / ఫ్రేమ్‌వర్క్ | Java 21 / Spring Boot 3.2.5 |
| పెర్సిస్టెన్స్ | MyBatis-Plus 3.5.5 |
| టెంప్లేట్ ఇంజిన్ | FreeMarker (22 సీడ్ టెంప్లేట్లు, ఆన్‌లైన్ ఎడిటింగ్ + వెర్షన్ రోల్‌బ్యాక్) |
| భద్రత | JWT అథెంటికేషన్ |
| డేటాబేస్ | MySQL 8 |
| ముందు చివర (generated projects) | Vue 3 + Element Plus + Vite |
| ముందు చివర (platform console) | Vue 2.7 + Element UI |
| స్థానిక AI | qwen2.5-coder:7b |
| API డాక్యుమెంటేషన్ | springdoc-openapi (Swagger UI) |

## 10. త్వరిత ప్రారంభం

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

వెనుక చివర డిఫాల్ట్ పోర్ట్ **8120**, ముందు చివర పోర్ట్ **9530**. డిఫాల్ట్ ఖాతా `admin / admin123`.

## 11. ప్రాజెక్ట్ స్థితి

- ప్రస్తుతం ప్రణాళిక చేయబడిన వెర్షన్ లైన్ **V9.2** ను చేరుకుంది.
- **V9.2** · పరీక్షా కేంద్రం & ధృవీకరణ కేంద్రం · Test Center (7 tables: env/suite/case/dataset/run/result/schedule) + Verification Center (tc_run/tc_case_result/tc_artifact, 9 suites S1-S9, single-run 409 lock, `codegen.testcenter/v1`); 373/373 tests; 206/206 locales; output byte-identical
- **V9.2** · నిర్వహణ సౌలభ్య పునర్నిర్మాణం · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9
- రెండు రిపోజిటరీలు Gitee లో హోస్ట్ చేయబడ్డాయి: `https://gitee.com/hustxiayuhong/CodeGen.git`

## 12. రచయిత

- **రచయిత**: ఒక చైనీస్ ప్రోగ్రామర్ (స్వతంత్ర డెవలపర్)
- **ఇమెయిల్**: `hustxiayuhong@126.com`
- **సాంకేతిక మద్దతు**: దయచేసి మీ ఇమెయిల్‌ను **చైనీస్** లో వ్రాయండి, సమస్య వివరణ, రన్‌టైమ్ పర్యావరణం (JDK / MySQL / వెర్షన్) మరియు పునరుత్పత్తి దశలను చేర్చండి.

---

## 13. సారూప్య సాధనాలతో పోలిక

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

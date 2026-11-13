# Majiang · குறியீடு உருவாக்கி (CodeGen) — திட்ட அறிமுகம்

> மொழிகள்: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · **தமிழ்** · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> **Spring Boot + Vue** க்கான பல திட்ட குறியீடு உருவாக்கி: தரவுத்தள அட்டவணை அமைப்புகளிலிருந்து தொடங்கி, **தொகுக்கக்கூடிய, இயக்கக்கூடிய** முன் மற்றும் பின் இறுதி வணிக குறியீட்டை மெனுக்கள் மற்றும் உரிமைகளுடன் உருவாக்குகிறது, மற்றும் வட்டில் எழுதுதல், காப்புப்பிரதி, லெட்ஜர் மற்றும் திரும்பப்பெறுதல் ஆகிய முழுமையான வழங்கல் சுழற்சியை கையாளுகிறது.

GitHub: https://github.com/hustxia/CodeGen

## 1. இது என்ன

CodeGen என்பது "அட்டவணைகளால் இயக்கப்படும்" ஒரு குறியீடு தொழிற்சாலை. உங்கள் வணிக அட்டவணைகளை உள்ளே கொண்டு வாருங்கள் (தரவுத்தள இறக்குமதி மூலம் அல்லது ஒரு DDL ஐ ஒட்டுவதன் மூலம்), புலங்கள் மற்றும் உருவாக்க சுவிட்சுகளை கட்டமைக்கவும், பின்னர் "உருவாக்கு" என்பதை கிளிக் செய்யவும் — பின்னர் அது பின் இறுதி Entity/Mapper/Service/Controller/DTO/VO, முன் இறுதி `api.js` மற்றும் Vue பக்கங்கள், மெனுக்கள் மற்றும் வழிகள், A4 அச்சிடும் பக்கங்கள் மற்றும் ஒருங்கிணைப்பு சோதனை எலும்புக்கூடுகள் கூட உருவாக்குகிறது, மற்றும் அனைத்தையும் உங்கள் இலக்கு திட்டத்தில் பாதுகாப்பாக எழுதுகிறது.

இது "குறியீட்டை வெளியேற்றுவது" மட்டும் இல்லை — இது **தரவாயில்கள் மற்றும் திரும்பப்பெறும் திறன் கொண்ட வழங்கல் பைப்லைன்**:

```
அட்டவணை உள்ளீடு (DB இறக்குமதி / DDL)
   → புலம் மற்றும் சுவிட்ச் கட்டமைப்பு (AI பரிந்துரைகளுடன்)
      → ஒரே கிளிக்கில் உருவாக்கு: சுய-சோதனை → காப்புப்பிரதி → வட்டில் எழுது → தொகுக்கு/சரிபார் → மெனு பதிவு → வழிகள் மீண்டும் உருவாக்கு
         → லெட்ஜர் பதிவு → திரும்பப்பெறக்கூடியது
```

## 2. பயனர்கள் யார், எப்படி பயன்படுத்துவார்கள்

| பயனர் | பொதுவான தேவை | தளம் வழங்கும் பாதை |
| --- | --- | --- |
| பின் இறுதி டெவலப்பர் | கையில் சில அட்டவணைகள் (அல்லது DDL) உள்ளன, விரைவாக CRUD ஐ எழுப்ப விரும்புகிறார் | அட்டவணை கட்டமைப்பு பக்கம்: இறக்குமதி → புலங்களை கட்டமை → ஒரே கிளிக்கில் உருவாக்கு |
| முழு அச்சு / சுயாதீன டெவலப்பர் | பூஜ்ஜியத்திலிருந்து முழுமையான அமைப்பை (உள்நுழைவு, உரிமைகள், அகராதி உள்ளிட்டவை) உருவாக்க விரும்புகிறார் | பைப்லைன் பக்கம்: ஒரு வாக்கியத்தில் அமைப்பு உருவாக்கு |
| குழு தலைவர் | குறியீடு பாணி, குறியீடு மரபுகள், அடைவு அமைப்பு ஆகியவற்றை ஒருங்கிணைக்க விரும்புகிறார் | அமைப்பு மேலாண்மை: ஒரு அமைப்பு = ஒரு வெளியீடு கட்டமைப்பு, தொகுதியாக நடைமுறைக்கு வரும் |
| பிளாட்ஃபார்ம் பராமரிப்பாளர் | குறியீட்டை மாற்றாமல் டெம்ப்ளேட்டுகளை மாற்ற விரும்புகிறார் | டெம்ப்ளேட் மேலாண்மை: ஆன்லைனில் திருத்தம், சேமிக்கும்போது உடனடியாக நடைமுறைக்கு வரும், வரலாற்று திரும்பப்பெறலை ஆதரிக்கிறது |

## 3. என்ன பிரச்சனைகளை தீர்க்கிறது

| பிரச்சனை | CodeGen என்ன செய்கிறது |
| --- | --- |
| மீண்டும் மீண்டும் CRUD நரகம் — ஒவ்வொரு அட்டவணைக்கும் குவியலான boilerplate | அட்டவணை மட்ட சுவிட்சுகள் ஒரே கிளிக்கில் முழுமையான முன் மற்றும் பின் இறுதியை உருவாக்குகின்றன, CRUD, பேஜினேஷன், சரிபார்ப்பு, இறக்குமதி/ஏற்றுமதி உட்பட |
| முன்/பின் புலம் மற்றும் API பொருத்தமின்மை | ஒரே அட்டவணை மெட்டாடேட்டா பின் இறுதி API கள் மற்றும் முன் இறுதி பக்கங்கள் இரண்டையும் இயக்குகிறது — பூஜ்ஜிய சொற்பொருள் விலகல் |
| குழு மரபுகள் ஒருங்கிணைக்க கடினம்; ஒவ்வொருவரும் தங்கள் பாணியில் எழுதுகிறார்கள் | டெம்ப்ளேட்டுகள் மரபு: 22 FreeMarker டெம்ப்ளேட்டுகள் மையமாக நிர்வகிக்கப்படுகின்றன, ஆன்லைனில் திருத்தப்படுகின்றன மற்றும் பதிப்பு திரும்பப்பெறலுடன் |
| ஜெனரேட்டர்கள் "எழுதி இழக்கிறார்கள்" கையால் எழுதப்பட்ட லாஜிக் | மார்கர்-பிளாக் மெர்ஜ் பொறிமுறை: கையால் எழுதப்பட்ட குறியீடு பாதுகாக்கப்படுகிறது, உருவாக்கப்பட்ட பகுதிகள் மட்டும் மாற்றப்படுகின்றன |
| உருவாக்கப்பட்ட குறியீடு தொகுக்கும் நேரம் வரை நம்பகமாக இல்லை | நினைவகத்தில் தொகுக்கும் சுய-சோதனை (உண்மையான `javac`), கட்டமைப்பு ஒப்பந்த சோதனைகள், வட்டில் diff முன்னோட்டம் — தொகுக்கவில்லையெனில் எதுவும் வட்டில் எழுதப்படாது |
| புதிய அமைப்பை பூஜ்ஜியத்திலிருந்து அமைவிப்பது நேரத்தை எடுக்கும் | பைப்லைன் "ஒரு வாக்கியத்தில் அமைப்பு உருவாக்கு": DB → ஸ்காஃபோல்ட் → உருவாக்கு → பேக் → மெனுக்கள் பதிவு, முழுமையாக தானியங்கி |

## 4. திறன்கள் கண்ணோட்டம்

- **அட்டவணை உள்ளீடு**: DDL பார்சிங், நேரடி தரவுத்தள இறக்குமதி, தொகுதி உள்ளீடு வழிகாட்டி, தானாக பெறப்பட்ட நெடுவரிசை கட்டமைப்பு (கட்டுப்பாடுகள் / சரிபார்ப்பு / அகராதிகள் / உறவுகள்).
- **அட்டவணை வடிவங்கள்**: ஒற்றை அட்டவணை, மர அட்டவணை (tree API + tree grid), மாஸ்டர்-டீடெயில் (ஒரே பரிவர்த்தனையில் சேமிப்பு + விவர தாவல்கள்), பல-பல இணைப்பு அட்டவணைகள்.
- **உருவாக்கப்பட்ட கலைப்பொருட்கள்**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue பக்கங்கள் + A4 அச்சிடும் பக்கங்கள் + ஒருங்கிணைப்பு சோதனை எலும்புக்கூடுகள்.
- **வணிக மேம்பாடுகள்**: உறவு கீழ்தோன்றல்கள், அகராதி மொழிபெயர்ப்பு, தரவு மறைப்பு, தனித்தன்மை சோதனைகள், படிவ இணைப்பு, தரவு உரிமைகள், Excel இறக்குமதி/ஏற்றுமதி, ஒப்புதல் பணி ஓட்டம், கட்டணம் / கோப்பு / மொபைல் திறன் பேக்குகள்.
- **தரவாயில்கள்**: நினைவகத்தில் தொகுக்கும் சுய-சோதனை (உண்மையான `javac`), கட்டமைப்பு ஒப்பந்த சோதனைகள், உரிமை ஒருங்கிணைப்பு சோதனைகள், வட்டில் diff முன்னோட்டம், முன் இறுதி கலைப்பொருட்களின் உண்மையான சரிபார்ப்பு.
- **பாதுகாப்பான இறங்குமுகம்**: வட்டில் எழுதுதல் + `.gen-backup` தொகுதி காப்புப்பிரதிகள், கையால் எழுதப்பட்ட குறியீட்டை பாதுகாக்க மார்கர்-பிளாக் மெர்ஜ், `gen_output` நான்கு-நிலை லெட்ஜர் (உருவாக்கப்பட்டது / இணைக்கப்பட்டது / தவிர்க்கப்பட்டது / தோல்வியடைந்தது), வரலாற்று திரும்பப்பெறுதல்.
- **பல அமைப்பு தனிமைப்படுத்தல்**: ஒவ்வொரு இலக்கு திட்டமும் ஒரு "அமைப்பு கட்டமைப்பு" (முன்/பின் பாதைகள், பேக்கேஜ் பெயர்கள், கேட்வே முன்னொட்டு, சார்பு classpath, உருவாக்க சுவிட்ச் இயல்புநிலைகள்).
- **AI மேம்பாடுகள்**: ஒரு வாக்கியத்தில் அமைப்பு உருவாக்கு (AI மாதிரி), விதை தரவு AI மேம்பாடு, அட்டவணை மட்ட AI பரிந்துரைக்கப்பட்ட கட்டமைப்பு.

## 5. பொதுவான காட்சிகள்

| காட்சி | விளக்கம் |
| --- | --- |
| மர அட்டவணை | தயாரிப்பு வகைகள் போன்ற படிநிலை தரவு — தானாக tree API, tree grid மற்றும் மேல்நிலை தேர்வு கீழ்தோன்றலை உருவாக்குகிறது |
| ஒற்றை அட்டவணை CRUD | வாடிக்கையாளர்கள், தயாரிப்புகள் போன்றவை — FK கீழ்தோன்றல்கள் + அகராதி மொழிபெயர்ப்பு + இறக்குமதி/ஏற்றுமதி உடனடியாக பயன்படுத்த தயார் |
| மாஸ்டர்-டீடெயில் | விற்பனை ஆர்டர்கள் + ஆர்டர் பொருட்கள் — ஒரே பரிவர்த்தனையில் சேமிப்பு, விவர டிராயர் இரண்டு தாவல்களுடன் (முக்கிய தகவல் / விவரங்கள்) |
| படிக்க மட்டும் / புள்ளியியல் | சரக்கு ஓட்டங்கள் போன்றவை — எழுதும் சுவிட்சுகள் அணைக்கப்படுகின்றன, வினவல் மற்றும் ஏற்றுமதி மட்டும் |
| அச்சிடும் ஆவணங்கள் | A4 அச்சிடும் பக்கம் மற்றும் வழியை உருவாக்க "அச்சிடு" சுவிட்சை தேர்ந்தெடுக்கவும் |
| ஒரு வாக்கியத்தில் அமைப்பு உருவாக்கு | வணிக களத்தை விவரிக்கவும் — AI ஒரே நேரத்தில் DB & அட்டவணைகள் + ஸ்காஃபோல்ட் + முழு உருவாக்கம் + மெனு பதிவு உருவாக்குகிறது |

## 6. அடிப்படை பணி ஓட்டம்

```
அட்டவணை உள்ளீடு → புலம்/சுவிட்ச் கட்டமைப்பு → ஒரே கிளிக்கில் உருவாக்கு → லெட்ஜர் · திரும்பப்பெறுதல்
DB இறக்குமதி/DDL  கட்டுப்பாடுகள்/சரிபார்ப்பு   அனைத்து தரவாயில்களுக்குப் பிறகு மட்டும் வட்டில் இறங்குகிறது
                /அகராதிகள்                  கையால் எழுதப்பட்ட குறியீடு பாதுகாக்கப்படுகிறது
```

## 7. ஒரு வாக்கியத்தில் அமைப்பு உருவாக்கு (பைப்லைன்)

| நிலை | பொருள் |
| --- | --- |
| `CHECK` | இலக்கு திட்ட கட்டமைப்பு மற்றும் சார்பு classpath ஐ சரிபார்க்கிறது |
| `WRITE` | தரவுத்தளம் & அட்டவணைகளை உருவாக்குகிறது, ஸ்காஃபோல்ட் மற்றும் உருவாக்கப்பட்ட கலைப்பொருட்களை எழுதுகிறது |
| `COMPILE` | பின் இறுதி `mvn package` / முன் இறுதி உருவாக்கம், நினைவகத்தில் தொகுக்கும் சுய-சோதனை |
| `MENU` | மெனு உரிமைகளை உருவாக்குகிறது மற்றும் பதிவு செய்கிறது (இரண்டு வெளியீடுகள்: கலைப்பொருள் SQL / உள்ளக நேரடி எழுதுதல்) |
| `ROUTE` | முன் இறுதி வழி துண்டுகளை மீண்டும் உருவாக்குகிறது |

## 8. அமைப்பு கட்டிடக்கலை

| களஞ்சியம் | பங்கு | அடுக்கு |
| --- | --- | --- |
| `codegen-server` | பின் இறுதி: உருவாக்கி இயந்திரம், டெம்ப்ளேட்டுகள், சரிபார்ப்பு, லெட்ஜர், பைப்லைன் | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | முன் இறுதி: கன்சோல் (அட்டவணை கட்டமைப்பு / டெம்ப்ளேட்டுகள் / லெட்ஜர் / அமைப்பு மேலாண்மை) | Vue 2.7 · Element UI · axios · Vue Router (வரலாறு முறை) |

## 9. தொழில்நுட்ப அடுக்கு

| பரிமாணம் | தேர்வு |
| --- | --- |
| பின் இறுதி மொழி / கட்டமைப்பு | Java 21 / Spring Boot 3.2.5 |
| நிலைப்புத்தன்மை | MyBatis-Plus 3.5.5 |
| டெம்ப்ளேட் இயந்திரம் | FreeMarker (22 விதை டெம்ப்ளேட்டுகள், ஆன்லைன் திருத்தம் + பதிப்பு திரும்பப்பெறல்) |
| பாதுகாப்பு | JWT அங்கீகாரம் |
| தரவுத்தளம் | MySQL 8 |
| முன் இறுதி | Vue 2.7 + Element UI |
| உள்ளூர் AI | qwen2.5-coder:7b |
| API ஆவணங்கள் | springdoc-openapi (Swagger UI) |

## 10. விரைவான தொடக்கம்

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

பின் இறுதி இயல்புநிலை போர்ட் **8120**, முன் இறுதி போர்ட் **9530**. இயல்புநிலை கணக்கு `admin / admin123`.

## 11. திட்ட நிலை

- தற்போதைய திட்டமிடப்பட்ட பதிப்பு வரி **V9.1** ஐ அடைந்தது.
- **V9.1** · பராமரிப்புத்திறன் மறுசீரமைப்பு · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9
- இரண்டு களஞ்சியங்களும் Gitee இல் ஹோஸ்ட் செய்யப்படுகின்றன: `https://gitee.com/hustxiayuhong/CodeGen.git`

## 12. ஆசிரியர்

- **ஆசிரியர்**: ஒரு சீன புரோகிராமர் (சுயாதீன டெவலப்பர்)
- **மின்னஞ்சல்**: `hustxiayuhong@126.com`
- **தொழில்நுட்ப ஆதரவு**: உங்கள் மின்னஞ்சலை **சீன மொழியில்** எழுதவும், பிரச்சனை விளக்கம், இயக்க சூழல் (JDK / MySQL / பதிப்பு) மற்றும் மறுஉற்பத்தி படிகள் ஆகியவற்றை உள்ளடக்கவும்.

---

## 13. ஒத்த கருவிகளுடன் ஒப்பீடு

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

# Majiang · ម៉ាស៊ីនបង្កើតកូដ (CodeGen) — សេចក្តីផ្តើមគម្រោង

> ភាសា: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · **ខ្មែរ** · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> ម៉ាស៊ីនបង្កើតកូដសម្រាប់ពហុគម្រោងសម្រាប់ **Spring Boot + Vue**: ចាប់ផ្តើមពីរចនាសម្ព័ន្ធតារាងមូលដ្ឋានទិន្នន័យ វាបង្កើតកូដអាជីវកម្មផ្នែកខាងមុខ និងខាងក្រោយ ដែល **អាចចងក្រង និងអាចប្រតិបត្តិបាន** ជាមួយនឹងម៉ឺនុយ និងសិទ្ធិ ហើយគ្រប់គ្រងវដ្តនៃការផ្តល់ពេញលេញនៃការសរសេរទៅឌីស ចម្លងបម្រុង សៀវភៅគណនី (ledger) និងការត្រឡប់មកវិញ។

GitHub: https://github.com/hustxia/CodeGen

## ១។ តើនេះជាអ្វី

CodeGen គឺជារោងចក្រកូដ "ជំរុញដោយតារាង"។ នាំតារាងអាជីវកម្មរបស់អ្នកចូល (តាមរយៈការនាំចូលពីមូលដ្ឋានទិន្នន័យ ឬដោយបិទភ្ជាប់ DDL) កំណត់រចនាសម្ព័ន្ធវាល និងកុងតាក់បង្កើត ហើយចុច "បង្កើត" — បន្ទាប់មកវាបង្កើត Entity/Mapper/Service/Controller/DTO/VO ផ្នែកខាងក្រោយ, `api.js` និងទំព័រ Vue ផ្នែកខាងមុខ ម៉ឺនុយ និងផ្លូវ សូម្បីតែទំព័របោះពុម្ព A4 និងគ្រោងតេស្តសមាហរណកម្ម ហើយសរសេរអ្វីៗទាំងអស់ដោយសុវត្ថិភាពទៅក្នុងគម្រោងគោលដៅរបស់អ្នក។

វាមិនមែនគ្រាន់តែ "បញ្ចេញកូដ" នោះទេ — វាគឺជា **បំពង់ផ្តល់ជាមួយនឹងច្រកទ្វារគុណភាព និងសមត្ថភាពត្រឡប់មកវិញ**។

## ២។ តើអ្នកប្រើប្រាស់ជានរណា និងពួកគេប្រើប្រាស់វាយ៉ាងដូចម្តី

| អ្នកប្រើប្រាស់ | តម្រូវការធម្មតា | ផ្លូវដែលវេទិកាផ្តល់ឱ្យ |
| --- | --- | --- |
| អ្នកអភិវឌ្ឍន៍ផ្នែកខាងក្រោយ | មានតារាងមួយចំនួន (ឬ DDL) និងចង់បង្កើត CRUD បានលឿន | ទំព័រកំណត់រចនាសម្ព័ន្ធតារាង: ការនាំចូល → កំណត់រចនាសម្ព័ន្ធវាល → បង្កើតដោយចុចតែម្តង |
| អ្នកអភិវឌ្ឍន៍ Full-stack / ឯករាជ្យ | ចង់បង្កើតប្រព័ន្ធពេញលេញមួយពីសូន្យ (រួមមានការចូល, សិទ្ធិ, វចនានុក្រម) | ទំព័របំពង់: បង្កើតប្រព័ន្ធដោយប្រយោគមួយ |
| អ្នកដឹកនាំក្រុម | ចង់ធ្វើឱ្យរចនាបទកូដ, ក្រមកូដ និងរចនាសម្ព័ន្ធថតឯកសារមានឯកតតភាព | ការគ្រប់គ្រងប្រព័ន្ធ: ប្រព័ន្ធមួយ = ការកំណត់រចនាសម្ព័ន្ធការផ្តល់ចេញមួយ, មានប្រសិទ្ធភាពជាក្រុម |
| អ្នកថែទាំវេទិកា | ចង់ផ្លាស់ប្តូរម៉ូដែលដោយមិនផ្លាស់ប្តូរកូដ | ការគ្រប់គ្រងម៉ូដែល: ការកែសម្រួលតាមអ៊ីនធឺណែត, រក្សាទុកហើយមានប្រសិទ្ធភាពភ្លាមៗ, គាំទ្រការត្រឡប់មកវិញនូវកំណែ |

## ៣។ តើវាដោះស្រាយបញ្ហាអ្វីខ្លះ

| ចំណុចឈឺចាប់ | អ្វីដែល CodeGen ធ្វើ |
| --- | --- |
| នរក CRUD ដដែលៗ — បណ្តុំ boilerplate សម្រាប់តារាងនីមួយៗ | កុងតាក់កម្រិតតារាងបង្កើតផ្នែកខាងមុខ និងខាងក្រោយពេញលេញដោយចុចតែម្តង រួមទាំង CRUD, ទំព័រ, ការផ្ទៀងផ្ទាត់, នាំចូល/នាំចេញ |
| ភាពមិនត្រូវគ្នារវាងវាល និង API រវាងផ្នែកមុខ និងក្រោយ | មេតាទិន្នន័យតារាងដូចគ្នា គ្រប់គ្រងទាំង API ផ្នែកខាងក្រោយ និងទំព័រផ្នែកខាងមុខ — សូន្យភាពខុសគ្នា semantic |
| វិធានក្រុមពិបាកនឹងឯកភាព; មនុស្សគ្រប់គ្នាសរសេរតាមរបៀបរបស់ខ្លួន | ម៉ូដែលគឺជាវិធានក្រុម: 22 ម៉ូដែល FreeMarker ដែលគ្រប់គ្រងកណ្តាល អាចកែសម្រួលតាមអ៊ីនធឺណែត ជាមួយនឹងការត្រឡប់មកវិញនូវកំណែ |
| ម៉ាស៊ីនបង្កើត "សរសេរជាន់ និងបាត់បង់" ឡូស៊ីកដែលសរសេរដោយដៃ | យន្តការបញ្ចូលគ្នា block ម៉ាកឃើរ: កូដដែលសរសេរដោយដៃត្រូវបានរក្សាទុក មានតែតំបន់ដែលបង្កើតប៉ុណ្ណោះដែលត្រូវជំនួស |
| កូដដែលបង្កើតមិនគួរឱ្យទុកចិត្តរហូតដល់ពេលចងក្រង | ការធ្វើតេស្តដោយខ្លួនឯងក្នុងសតិ (ពិត `javac`), ការពិនិត្យកិច្ចសន្យារចនាសម្ព័ន្ធ, ការមើលជាមុន diff នៅលើឌីស — គ្មានអ្វីដែលមកដល់ឌីសទេលុះត្រាតែវាចងក្រង |
| ការបង្កើតប្រព័ន្ធថ្មីពីសូន្យចំណាយពេល | បំពង់ "បង្កើតប្រព័ន្ធដោយប្រយោគមួយ": បង្កើត DB → scaffolding → បង្កើត → ខ្ចប់ → ចុះឈ្មោះម៉ឺនុយ ទាំងអស់ដោយស្វ័យប្រវត្តិ |

## ៤។ ទិដ្ឋភាពសមត្ថភាព

- **ការទទួលតារាង**: ការញ្ជា DDL, ការនាំចូលដោយផ្ទាល់ពីមូលដ្ឋានទិន្នន័យ, អ្នកណែនាំការទទួលបាច់, កំណត់រចនាសម្ព័ន្ធជួរដែលទាញយកដោយស្វ័យប្រវត្តិ (ការគ្រប់គ្រង / ការផ្ទៀងផ្ទាត់ / វចនានុក្រម / ទំនាក់ទំនង).
- **ទម្រង់តារាង**: តារាងតែមួយ, តារាងឈើ (tree API + ជាត្រីឈើ), មេស្ទើ-ព័ត៌មានលម្អិត (រក្សាទុកក្នុងប្រតិបត្តិការដូចគ្នា + ផ្ទាំងព័ត៌មានលម្អិត), តារាងភ្ជាប់ច្រើនទៅច្រើន.
- **វត្ថុបង្កើត**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + ទំព័រ Vue + ទំព័របោះពុម្ព A4 + គ្រោងតេស្តសមាហរណកម្ម.
- **ការកែលម្អអាជីវកម្ម**: បិទបញ្ជីធ្លាក់ទំនាក់ទំនង, ការបកប្រែវចនានុក្រម, ការបិទបាំងទិន្នន័យ, ការពិនិត្យភាពពិសេស, ការភ្ជាប់ទម្រង់, សិទ្ធិវិសាលទិន្នន័យ, Excel នាំចូល/នាំចេញ, លំហូរការងារអនុម័ត, កញ្ចប់សមត្ថភាពបង់ប្រាក់ / ឯកសារ / ទូរសព្ទ.
- **ច្រកទ្វារគុណភាព**: ការធ្វើតេស្តដោយខ្លួនឯងក្នុងសតិ (ពិត `javac`), ការពិនិត្យកិច្ចសន្យារចនាសម្ព័ន្ធ, ការពិនិត្យភាពស្របគ្នានៃសិទ្ធិ, ការមើលជាមុន diff នៅលើឌីស, ការផ្ទៀងផ្ទាត់ពិតនៃវត្ថុបង្កើតផ្នែកមុខ.
- **ការចុះចតដោយសុវត្ថិភាព**: ការសរសេរទៅឌីស + ការចម្លងបម្រុងបាច់ `.gen-backup`, ការបញ្ចូលគ្នា block ម៉ាកឃើរដើម្បីរក្សាកូដដែលសរសេរដោយដៃ, សៀវភៅគណនីបួនស្ថានភាព `gen_output` (បង្កើត / បញ្ចូលគ្នា / រំលង / បរាជ័យ), ការត្រឡប់មកវិញជាប្រវត្តិសាស្រ្ត.
- **ភាពឯករបស់ប្រព័ន្ធច្រើន**: គម្រោងគោលដៅនីមួយៗគឺជា "កំណត់រចនាសម្ព័ន្ធប្រព័ន្ធ" (ផ្លូវមុខ/ក្រោយ, ឈ្មោះកញ្ចប់, បុព្វបទ gateway, classpath អាស្រ័យ, តម្លៃដើមកំណត់កុងតាក់បង្កើត) — បម្រើប្រព័ន្ធច្រើនប្រព័ន្ធក្នុងពេលដំណាលគ្នា.
- **ការកែលម្អ AI**: បង្កើតប្រព័ន្ធដោយប្រយោគមួយ (ម៉ូដែល AI), ការកែលម្អ AI ទិន្នន័យគ្រាប់ពូជ, កំណត់រចនាសម្ព័ន្ធ AI ដែលណែនាំកម្រិតតារាង.

## ៥។ សេណារីយ៉ូទូទៅ

| សេណារីយ៉ូ | ការពិពណ៌នា |
| --- | --- |
| តារាងឈើ | ទិន្នន័យឋានានុក្រមដូចជាប្រភេទផលិតផល — បង្កើតដោយស្វ័យប្រវត្តិ tree API, ជាត្រីឈើ និងបិទបញ្ជីធ្លាក់ជម្រើសមេ |
| CRUD តារាងតែមួយ | អតិថិជន, ផលិតផលជាដើម — បិទបញ្ជីធ្លាក់ FK + ការបកប្រែវចនានុក្រម + នាំចូល/នាំចេញរួចរាល់ហើយ |
| មេស្ទើ-ព័ត៌មានលម្អិត | លក្ខណះបញ្ជាទិញ + ធាតុបញ្ជាទិញ — រក្សាទុកក្នុងប្រតិបត្តិការដូចគ្នា ប្រអប់ព័ត៌មានលម្អិតជាមួយផ្ទាំងពីរ |
| អានបានតែប៉ុណ្ណោះ / ស្ថិតិ | លំហូរស្តុកជាដើម — កុងតាក់សរសេរបិទ មានតែសំណួរ និងនាំចេញ |
| ឯកសារបោះពុម្ព | ជ្រើសកុងតាក់ "បោះពុម្ព" ដើម្បីបង្កើតទំព័របោះពុម្ព A4 និងផ្លូវ |
| ការបង្កើតប្រព័ន្ធដោយប្រយោគមួយ | ពិពណ៌នាដែនអាជីវកម្ម — AI បង្កើត DB & តារាង + scaffolding + ការបង្កើតពេញលេញ + ការចុះឈ្មោះម៉ឺនុយនៅពេលតែមួយ |

## ៦។ លំហូរការងារមូលដ្ឋាន

```
ការទទួលតារាង → កំណត់រចនាសម្ព័ន្ធវាល/កុងតាក់ → បង្កើតចុចម្តង → សៀវភៅគណនី · ត្រឡប់មកវិញ
DB នាំចូល/DDL    ការគ្រប់គ្រង/ផ្ទៀងផ្ទាត់   មកដល់ឌីសលុះត្រាតែច្រកទ្វារគុណភាពទាំងអស់ឆ្លង;
                /វចនានុក្រម                កូដដែលសរសេរដោយដៃត្រូវបានរក្សាទុក
```

## ៧។ បង្កើតប្រព័ន្ធដោយប្រយោគមួយ (បំពង់)

| ដំណាក់កាល | អត្ថន័យ |
| --- | --- |
| `CHECK` | ផ្ទៀងផ្ទាត់កំណត់រចនាសម្ព័ន្ធគម្រោងគោលដៅ និង classpath អាស្រ័យ |
| `WRITE` | បង្កើតមូលដ្ឋានទិន្នន័យ & តារាង សរសេរ scaffolding និងវត្ថុបង្កើត |
| `COMPILE` | ក្រោយ `mvn package` / មុខ build, ការធ្វើតេស្តដោយខ្លួនឯងក្នុងសតិ |
| `MENU` | បង្កើត និងចុះឈ្មោះសិទ្ធិម៉ឺនុយ (ច្រកពីរ: artefact SQL / សរសេរផ្ទៃក្នុងដោយផ្ទាល់) |
| `ROUTE` | បង្កើតឡើងវិញនូវបំណែកផ្លូវមុខ |

## ៨។ ស្ថាបត្យកម្មប្រព័ន្ធ

| Repository | តួនាទី | Stack |
| --- | --- | --- |
| `codegen-server` | ក្រោយ: ម៉ាស៊ីនបង្កើត, ម៉ូដែល, ការផ្ទៀងផ្ទាត់, សៀវភៅគណនី, បំពង់ | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | មុខ: កុងសូល (កំណត់រចនាសម្ព័ន្ធតារាង / ម៉ូដែល / សៀវភៅគណនី / គ្រប់គ្រងប្រព័ន្ធ) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (history mode) |

## ៩។ Stack បច្ចេកទេស

| វិមាត្រ | ជម្រើស |
| --- | --- |
| ភាសា/ក្របខណ្ឌក្រោយ | Java 21 / Spring Boot 3.2.5 |
| ភាពជាប់បាន | MyBatis-Plus 3.5.5 |
| ម៉ាស៊ីនម៉ូដែល | FreeMarker (22 ម៉ូដែលគ្រាប់ពូជ, កែសម្រួលតាមអ៊ីនធឺណែត + ត្រឡប់មកវិញកំណែ) |
| សុវត្ថិភាព | ការផ្ទៀងផ្ទាត់ JWT |
| មូលដ្ឋានទិន្នន័យ | MySQL 8 |
| មុខ (generated projects) | Vue 3 + Element Plus + Vite |
| មុខ (platform console) | Vue 2.7 + Element UI |
| AI ក្នុងស្រុក | qwen2.5-coder:7b |
| ឯកសារ API | springdoc-openapi (Swagger UI) |

## ១០។ ចាប់ផ្តើមរហ័ស

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

ក្រោយចាប់ផ្តើមនៅ port **8120**, មុខនៅ port **9530**. គណនីដើម `admin / admin123`.

## ១១។ ស្ថានភាពគម្រោង

- ខ្សែបន្ទាត់កំណែដែលបានគ្រោងទុកបច្ចុប្បន្នបានឈានដល់ **V9.1**.
- **V9.1** · ការរៀបចំឡើងវិញដើម្បីងាយថែទាំ · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9
- Repository ទាំងពីរត្រូវបាន host នៅ Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

## ១២។ អ្នកនិពន្ធ

- **អ្នកនិពន្ធ**: អ្នកសរសេរកម្មវិធីចិន (អ្នកអភិវឌ្ឍន៍ឯករាជ្យ)
- **អ៊ីមែល**: `hustxiayuhong@126.com`
- **ជំនួយបច្ចេកទេស**: សូមសរសេរអ៊ីមែលរបស់អ្នកជាភាសាចិន រួមបញ្ចូលទាំងការពិពណ៌នាបញ្ហា បរិស្ថាន runtime (JDK / MySQL / កំណែ) និងជំហានដើម្បីផលិតឡើងវិញ.

---

## 13. ការប្រៀបធៀបជាមួយឧបករណ៍ស្រដៀងគ្នា

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

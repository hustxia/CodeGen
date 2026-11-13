# Majiang · კოდის გენერატორი (CodeGen) — პროექტის წარდგენა

> ენები: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · **ქართული**

---

> **Spring Boot + Vue**-ისთვის განკუთვნილი მრავალპროექტული კოდის გენერატორი: მონაცემთა ბაზის ცხრილის სტრუქტურებიდან გამომდინარე, იგი ქმნის **კომპილირებად, გასაშვებ** ბიზნეს კოდს წინა და უკანა ნაწილისთვის მენიუებითა და უფლებებით, და მართავს მიწოდების სრულ ციკლს დისკზე ჩაწერიდან, სარეზერვო ასლიდან, ლეჯერიდან და უკან დაბრუნებიდან.

GitHub: https://github.com/hustxia/CodeGen

## 1. რა არის ეს

CodeGen არის "ცხრილებით მართული" კოდის ქარხანა. შემოიტანეთ თქვენი ბიზნეს ცხრილები (მონაცემთა ბაზიდან იმპორტით ან DDL-ის ჩასმით), დააკონფიგურირეთ ველები და გენერაციის გადამრთველები, შემდეგ დააჭირეთ "გენერირება" — მაშინ ის ქმნის back-end Entity/Mapper/Service/Controller/DTO/VO, front-end `api.js` და Vue გვერდებს, მენიუს და მარშრუტებს, თუნდაც A4 ბეჭდვის გვერდებს და ინტეგრაციის ტესტის ჩონჩხებს, და ყველაფერს უსაფრთხოდ წერს თქვენს სამიზნე პროექტში.

ეს მხოლოდ "კოდის გამოფრქვევა" არ არის — ეს არის **მიწოდების მილსადენი ხარისხის კარიბჭეებითა და უკან დაბრუნების უნარით**.

## 2. ვინ არიან მომხმარებლები და როგორ გამოიყენებენ

| მომხმარებელი | ტიპური მოთხოვნა | გზა, რომელსაც პლატფორმა გთავაზობთ |
| --- | --- | --- |
| Back-end დეველოპერი | ხელთ აქვს რამდენიმე ცხრილი (ან DDL) და სწრაფად სურს CRUD-ის ამოქმედება | ცხრილის კონფიგურაციის გვერდი: იმპორტი → ველების კონფიგურაცია → ერთი დაჭერით გენერირება |
| სრული სთექი / დამოუკიდებელი დეველოპერი | სრული სისტემის ნულიდან აშენება სურს (შესვლა, უფლებები, ლექსიკონი) | მილსადენის გვერდი: ერთი წინადადებით სისტემის აშენება |
| გუნდის ხელმძღვანელი | სურს კოდის სტილის, კოდის სტანდარტების, საქაღალდის სტრუქტურის გაერთიანება | სისტემის მართვა: ერთი სისტემა = ერთი გამოსატანი კონფიგურაცია, სერიით ძალაში შესვლა |
| პლატფორმის შემსახურებელი | სურს შაბლონების შეცვლა კოდის შეცვლის გარეშე | შაბლონების მართვა: ონლაინ რედაქტირებადი, შენახვისას მყისიერად ძალაში, ისტორიული უკან დაბრუნების მხარდაჭერა |

## 3. რა პრობლემებს წყვეტს

| ტკივილის წერტილი | რა აკეთებს CodeGen |
| --- | --- |
| განმეორებადი CRUD ჯოჯოხეთი — boilerplate-ის გროვა თითოეული ცხრილისთვის | ცხრილის დონის გადამრთველები ერთი დაჭერით ქმნიან სრულ წინა და უკანა ნაწილს, CRUD, პაგინაცია, ვალიდაცია, იმპორტ/ექსპორტის ჩათვლით |
| წინა/უკანა ველისა და API-ის შეუსაბამობები | იგივე ცხრილის მეტამონაცემები მართავს როგორც back-end API-ებს, ასევე front-end გვერდებს — ნულოვანი სემანტიკური გადახრა |
| გუნდური კონვენციების ერთიანობა რთულია | შაბლონები არის კონვენცია: 22 FreeMarker შაბლონი ცენტრალურად მართული, ონლაინ რედაქტირებადი ვერსიის უკან დაბრუნებით |
| გენერატორები "იწერენ და კარგავენ" ხელით დაწერილ ლოგიკას | მარკერ-ბლოკის შერწყმის მექანიზმი: ხელით დაწერილი კოდი დაცულია, მხოლოდ გენერირებული რეგიონები იცვლება |
| გენერირებული კოდი არასანდოა კომპილაციის დრომდე | მეხსიერებაში კომპილაციის თვითშემოწმება (ნამდვილი `javac`), სტრუქტურული ხელშეკრულების შემოწმება, დისკზე diff გადახედვა — არაფერი ხვდება დისკზე სანამ არ კომპილირდება |
| ახალი სისტემის აშენება ნულიდან დროს მოითხოვს | მილსადენი "სისტემა ერთი წინადადებით ააშენე": DB შექმნა → ხარაჩო → გენერირება → შეფუთვა → მენიუს რეგისტრაცია, სრულად ავტომატური |

## 4. შესაძლებლობების მიმოხილვა

- **ცხრილის მიღება**: DDL პარსინგი, პირდაპირი იმპორტი მონაცემთა ბაზიდან, სერიის მიღების ოსტატი, სვეტის კონფიგურაცია ავტომატურად (კონტროლი / ვალიდაცია / ლექსიკონები / ურთიერთობები).
- **ცხრილის ფორმები**: ერთი ცხრილი, ხის ცხრილი (tree API + ხის გრილი), master-detail (ერთ ტრანზაქციაში შენახვა + დეტალების ჩანართები), მრავალი-მრავალი დამაკავშირებელი ცხრილი.
- **გენერირებული არტეფაქტები**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue გვერდები + A4 ბეჭდვის გვერდები + ინტეგრაციის ტესტის ჩონჩხები.
- **ბიზნეს გაუმჯობესებები**: ურთიერთობის ვარდების სია, ლექსიკონის თარგმანი, მონაცემთა დაფარვა, უნიკალურობის შემოწმება, ფორმის დაკავშირება, მონაცემთა სფეროს უფლებები, Excel იმპორტ/ექსპორტი, დამტკიცების workflow, გადახდის / ფაილის / მობილური შესაძლებლობის პაკეტები.
- **ხარისხის კარიბჭეები**: მეხსიერებაში კომპილაციის თვითშემოწმება (ნამდვილი `javac`), სტრუქტურული ხელშეკრულების შემოწმება, უფლებების თანმიმდევრულობის შემოწმება, დისკზე diff გადახედვა, front-end არტეფაქტების რეალური ვალიდაცია.
- **უსაფრთხო დაშვება**: დისკზე ჩაწერა + `.gen-backup` სერიის სარეზერვო ასლები, მარკერ-ბლოკის შერწყმა ხელით დაწერილი კოდის დაცვისთვის, `gen_output` ოთხმდგომარეობიანი ლეჯერი (შექმნილი / შერწყმული / გამოტოვებული / წარუმატებელი), ისტორიული უკან დაბრუნება.
- **მრავალ სისტემური იზოლაცია**: თითოეული სამიზნე პროექტი არის "სისტემის კონფიგურაცია" (წინა/უკანა გზები, პაკეტის სახელები, gateway პრეფიქსი, დამოკიდებულების classpath, გენერაციის გადამრთველის ნაგულისხმევი მნიშვნელობები).
- **AI გაუმჯობესებები**: სისტემა ერთი წინადადებით ააშენე (AI მოდელირება), seed მონაცემების AI გაუმჯობესება, ცხრილის დონის AI რეკომენდებული კონფიგურაცია.

## 5. ტიპიური სცენარები

| სცენარი | აღწერა |
| --- | --- |
| ხის ცხრილი | იერარქიული მონაცემები, როგორიცაა პროდუქტის კატეგორიები — ავტომატურად ქმნის tree API, ხის გრილი და მშობლის შერჩევის ვარდების სია |
| ერთი ცხრილის CRUD | კლიენტები, პროდუქტები და ა.შ. — FK ვარდების სია + ლექსიკონის თარგმანი + იმპორტ/ექსპორტი მზად |
| master-detail | გაყიდვის შეკვეთები + შეკვეთის ნივთები — ერთ ტრანზაქციაში შენახვა, დეტალების უჯრა ორი ჩანართით |
| მხოლოდ წაკითხვა / სტატისტიკა | მარაგის ნაკადები და ა.შ. — წერის გადამრთველები გამორთული, მხოლოდ მოთხოვნა და ექსპორტი |
| ბეჭდვის დოკუმენტები | მონიშნეთ "ბეჭდვა" გადამრთველი A4 ბეჭდვის გვერდისა და მარშრუტის შესაქმნელად |
| ერთი წინადადებით სისტემის აშენება | აღწერეთ ბიზნეს დომენი — AI ქმნის DB & ცხრილებს + ხარაჩოს + სრულ გენერირებას + მენიუს რეგისტრაციას ერთ ნაბიჯში |

## 6. ძირითადი სამუშაო ნაკადი

```
ცხრილის მიღება → ველი/გადამრთველი კონფ. → ერთი დაჭერით გენერირება → ლეჯერი · უკან დაბრუნება
DB იმპორტი/DDL  კონტროლი/ვალიდაცია    დისკზე ხვდება მხოლოდ ყველა ხარისხის კარიბჭის გავლით;
               /ლექსიკონები             ხელით დაწერილი კოდი დაცულია
```

## 7. ერთი წინადადებით სისტემის აშენება (მილსადენი)

| ეტაპი | მნიშვნელობა |
| --- | --- |
| `CHECK` | ამოწმებს სამიზნე პროექტის კონფიგურაციას და დამოკიდებულების classpath-ს |
| `WRITE` | ქმნის მონაცემთა ბაზას & ცხრილებს, წერს ხარაჩოს და გენერირებულ არტეფაქტებს |
| `COMPILE` | Back-end `mvn package` / front-end build, მეხსიერებაში კომპილაციის თვითშემოწმება |
| `MENU` | ქმნის და რეგისტრირებს მენიუს უფლებებს (ორი გასასვლელი: არტეფაქტი SQL / შიდა პირდაპირი ჩაწერა) |
| `ROUTE` | ხელახლა აშენებს front-end მარშრუტის ფრაგმენტებს |

## 8. სისტემის არქიტექტურა

| რეპოზიტორია | როლი | სტეკი |
| --- | --- | --- |
| `codegen-server` | Back-end: გენერაციის ძრავა, შაბლონები, ვალიდაცია, ლეჯერი, მილსადენი | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Front-end: კონსოლი (ცხრილის კონფ. / შაბლონები / ლეჯერი / სისტემის მართვა) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (history mode) |

## 9. ტექნოლოგიური სტეკი

| განზომილება | არჩევანი |
| --- | --- |
| Back-end ენა / ჩარჩო | Java 21 / Spring Boot 3.2.5 |
| მდგრადობა | MyBatis-Plus 3.5.5 |
| შაბლონის ძრავა | FreeMarker (22 სათესლე შაბლონი, ონლაინ რედაქტირება + ვერსიის უკან დაბრუნება) |
| უსაფრთხოება | JWT ავთენტიფიკაცია |
| მონაცემთა ბაზა | MySQL 8 |
| Front-end (generated projects) | Vue 3 + Element Plus + Vite |
| Front-end (platform console) | Vue 2.7 + Element UI |
| ლოკალური AI | qwen2.5-coder:7b |
| API დოკუმენტაცია | springdoc-openapi (Swagger UI) |

## 10. სწრაფი დაწყება

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

Back-end ნაგულისხმევი პორტი **8120**, front-end პორტი **9530**. ნაგულისხმევი ანგარიში `admin / admin123`.

## 11. პროექტის სტატუსი

- მიმდინარე დაგეგმილი ვერსიის ხაზმა მიაღწია **V9.1**.
- **V9.1** · მოვლადობის რეფაქტორინგი · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9
- ორივე რეპოზიტორია განთავსებულია Gitee-ზე: `https://gitee.com/hustxiayuhong/CodeGen.git`

## 12. ავტორი

- **ავტორი**: ჩინელი პროგრამისტი (დამოუკიდებელი დეველოპერი)
- **ელ. ფოსტა**: `hustxiayuhong@126.com`
- **ტექნიკური მხარდაჭერა**: გთხოვთ დაწეროთ თქვენი ელ. ფოსტა **ჩინურად**, პრობლემის აღწერილობა, runtime გარემო (JDK / MySQL / ვერსია) და რეპროდუქციის ნაბიჯები ჩათვლით.

---

## 13. შედარება მსგავს ინსტრუმენტებთან

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

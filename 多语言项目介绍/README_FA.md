# Majiang · تولیدکننده کد (CodeGen) — معرفی پروژه

> Languages: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · **فارسی** · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> یک تولیدکننده کد چندپروژه‌ای برای **Spring Boot + Vue**: با شروع از ساختار جداول پایگاه داده، کد تجاری فرانت‌اند و بک‌اند **قابل کامپایل، قابل اجرا و آماده منو و دسترسی** تولید می‌کند و کل چرخه تحویل شامل نوشتن روی دیسک، پشتیبان‌گیری، دفترداری و بازگشت (rollback) را مدیریت می‌کند.

GitHub: https://github.com/hustxia/CodeGen

> **نسخه فعلی V9.3 · آزمون خودکار خروجی‌های تولیدشده · Generated-artifact automated testing: S10 (mvn test + Surefire), S11 (API / TestContainers), S12 (E2E / Playwright); GEN trigger + TC_GEN_ROOT; 12-step pipeline green; real artifact run PASSED 20/20, run/case/artifact persisted**
> **V9.2 release · مرکز آزمون و مرکز تأیید · Test Center (7 tables: env/suite/case/dataset/run/result/schedule) + Verification Center (tc_run/tc_case_result/tc_artifact, 9 suites S1-S9, single-run 409 lock, `codegen.testcenter/v1`); 373/373 tests; 206/206 locales; output byte-identical**
> **V9.1 · بازآرایی برای نگهداریپذیری · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9**
> **V9.0 · خروجی‌های تولیدی به Vue 3 + Element Plus + Vite مدرن شدند؛ در زمان اجرا راستی‌آزمایی شد (۴۰/۴۰ ادعا، vite build موفق، ۱۴ اسکرین‌شات شامل ar-SA RTL)؛ ۳ عیب زمان اجرا برطرف شد**
> **V8.1** · سخت‌سازی کیفیت: هارنس قرارداد آفلاین (۹ حالت، صفر دیف OFF) + تست واحد i18n-core + گیت CI + تفکیک صادقانه README (چارچوب ۲۰۶ / ترجمه‌شده ۸) + check-locale-keys.mjs
> **V8.0** · i18n تمام‌صفحه: چارچوب ۲۰۶ زبانه (۸ زبان ترجمه حرفه‌ای)، i18n دورخط با قرارداد صفر دیف در OFF

## ۱. این چیست؟

CodeGen یک کارخانه کد «مبتنی بر جدول» است. جداول تجاری خود را وارد کنید (از طریق واردات پایگاه داده یا الصاق DDL)، فیلدها و کلیدهای تولید را پیکربندی کنید، سپس روی «تولید» کلیک کنید — تولیدکننده Entity/Mapper/Service/Controller/DTO/VO بک‌اند، `api.js` و صفحات Vue فرانت‌اند، منوها و مسیرها، حتی صفحات چاپ A4 و اسکلت‌های تست یکپارچه‌سازی را تولید کرده و آن‌ها را با خیالی آسوده در پروژه مقصد می‌نویسد.

این فقط «کد پرتاب نمی‌کند» — بلکه یک **خط لوله تحویل با دروازه‌های کیفیت و قابلیت بازگشت** است:

```
Table intake (DB import / DDL)
   → Field & switch configuration (with AI recommendations)
      → One-click generation: self-check → backup → write to disk → compile/validate → register menu → rebuild routes
         → Ledger bookkeeping → rollback-capable
```

---

## ۲. چه کسانی کاربر هستند و چگونه استفاده می‌کنند؟

| کاربر | نیاز نمونه | مسیری که پلتفرم ارائه می‌دهد |
| --- | --- | --- |
| توسعه‌دهنده بک‌اند | مجموعه‌ای از جداول (یا DDL) دارد و می‌خواهد سریعاً CRUD راه بیندازد | صفحه پیکربندی جدول: واردات → پیکربندی فیلد → تولید با یک کلیک |
| توسعه‌دهنده فول‌استک / مستقل | می‌خواهد از صفر یک سیستم کامل (با ورود، دسترسی، فرهنگ‌ها) بسازد | صفحه خط لوله: ساخت سیستم با یک جمله |
| مسئول تیم | می‌خواهد سبک کد، قراردادهای کد و ساختار پوشه را یکپارچه کند | مدیریت سیستم: یک سیستم = یک مجموعه پیکربندی خروجی، اعمال دسته‌ای |
| نگهداری پلتفرم | می‌خواهد الگوها را بدون تغییر کد ویرایش کند | مدیریت الگو: ویرایش آنلاین، بلافاصله پس از ذخیره اعمال می‌شود، با پشتیبانی بازگشت نسخه |

## ۳. چه مشکلاتی را حل می‌کند؟

| نقطه درد | آنچه CodeGen انجام می‌دهد |
| --- | --- |
| جحیم CRUD تکراری — یک انبوه کد استاندارد برای هر جدول | کلیدهای هر جدول در یک کلیک یک فرانت‌اند و بک‌اند کامل تولید می‌کنند، شامل CRUD، صفحه‌بندی، اعتبارسنجی، واردات/صادرات |
| عدم تطابق فیلدها و API بین فرانت و بک که اصلاحات یکپارچه‌سازی بی‌پایان ایجاد می‌کند | همان فراداده جدول هم APIهای بک‌اند و هم صفحات فرانت‌اند را هدایت می‌کند — بدون هیچ انحراف معنایی |
| قراردادهای تیمی سخت یکپارچه می‌شوند؛ سبک هر کس متفاوت است | الگوها همان قرارداد هستند: ۲۲ الگوی FreeMarker مدیریت متمرکز، قابل ویرایش آنلاین با بازگشت نسخه |
| تولیدکننده‌ها کد دست‌نویس را «بازنویسی و از دست می‌دهند» | مکانیزم ادغام بلوک-نشانه‌گذاری: کد دست‌نویس حفظ می‌شود، فقط نواحی تولیدشده جایگزین می‌شوند |
| کد تولیدشده تا زمان کامپایل قابل اعتماد نیست | خودبررسی کامپایل در حافظه (واقعی `javac`)، بررسی‌های ساختاری قرارداد، پیش‌نمایش تفاوت روی دیسک — چیزی روی دیسک نمی‌نشیند مگر اینکه کامپایل شود |
| ساختن یک سیستم جدید از صفر زمان‌بر است | خط لوله «ساخت سیستم با یک جمله»: ایجاد پایگاه داده → scaffold → تولید → بسته‌بندی → ثبت منوها، کاملاً خودکار |

---

## ۴. نمای کلی قابلیت‌ها

- **ورود جدول**: تجزیه DDL، واردات مستقیم پایگاه داده، جادوگر ورود دسته‌ای، پیکربندی ستون مشتق‌شده خودکار (کنترل‌ها / اعتبارسنجی / فرهنگ‌ها / روابط).
- **شکل‌های جدول**: جدول تکی، جدول درختی (API درختی + شبکه درختی)، استاد-جزئیات (ذخیره هم‌تراکنش + زبانه‌های جزئیات)، جداول پیوندی چندبه‌چند.
- **مصنوعات تولیدشده**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + صفحات Vue + صفحات چاپ A4 + اسکلت‌های تست یکپارچه‌سازی.
- **بهبودهای تجاری**: کشویی رابطه، ترجمه فرهنگ، پوشش داده، بررسی‌های یکتایی، پیوند فرم، دسترسی‌های محدوده داده، واردات/صادرات Excel، جریان کاری تأیید، بسته‌های قابلیت پرداخت / فایل / موبایل.
- **دروازه‌های کیفیت**: خودبررسی کامپایل در حافظه (واقعی `javac`)، بررسی‌های ساختاری قرارداد، بررسی‌های سازگاری دسترسی، پیش‌نمایش تفاوت روی دیسک، اعتبارسنجی واقعی مصنوعات فرانت‌اند.
- **فرود ایمن**: نوشتن روی دیسک + پشتیبان‌گیری دسته‌ای `.gen-backup`، ادغام بلوک-نشانه‌گذاری برای حفظ کد دست‌نویس، دفترچه چهارحالته `gen_output` (ایجادشده / ادغام‌شده / ردشده / شکست‌خورده)، بازگشت تاریخی.
- **انزوای چندسیستمی**: هر پروژه مقصد یک «پیکربندی سیستم» است (مسیرهای فرانت/بک، نام‌های بسته، پیشوند دروازه، classpath وابستگی، پیش‌فرض‌های کلید تولید) — چندین پروژه به موازات ارائه می‌شوند.
- **بهبودهای هوش مصنوعی**: ساخت سیستم با یک جمله (مدل‌سازی AI)، بهبود داده‌های‌بذر با AI، پیکربندی توصیه‌شده به‌ازای هر جدول توسط AI (پیش‌نمایش تفاوت + اعمال علامت‌خورده).

---

## ۵. سناریوهای معمول

| سناریو | توضیح |
| --- | --- |
| جدول درختی | داده‌های سلسله‌مراتبی مانند دسته‌بندی محصولات — API درختی، شبکه درختی و کشویی انتخاب والد تولیدشده خودکار |
| CRUD جدول تکی | مشتریان، محصولات و غیره — کشویی FK + ترجمه فرهنگ + واردات/صادرات آماده به‌کار |
| استاد-جزئیات | سفارشات فروش + موارد سفارش — ذخیره هم‌تراکنش، کشوی جزئیات با دو زبانه (اطلاعات استاد / جزئیات) |
| فقط‌خواندنی / آمار | جریان‌های موجودی و غیره — کلیدهای نوشتن خاموش، فقط پرس‌وجو و صادرات |
| چاپ اسناد | کلید «چاپ» را علامت بزنید تا یک صفحه چاپ A4 و مسیر تولید شود، با سرصفحه + جدول جزئیات |
| ساخت سیستم با یک جمله | حوزه تجاری را توصیف کنید — AI پایگاه داده و جداول + scaffold + تولید کامل + ثبت منو را یکجا ایجاد می‌کند |

> تمام سناریوها دموهای اجرایی (پروژه `demo_admin`) با تصاویر دارند — ببینید `../codegen-server/docs/典型场景操作指南-演示后台demo.md` (چینی).

---

## ۶. جریان کاری اصلی

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Table intake │ → │ Field/switch conf. │ → │ One-click generate (self-check→backup→write→     │ → │ Ledger · Rollback │
│              │   │                    │   │ compile→menu→route)                              │   │                   │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     controls/validation     Lands on disk only after all quality gates pass;      Batch-level traceability
                    /dictionaries           hand-written code is preserved
```

- **پیش‌نمایش تفاوت**: مصنوعات تولیدشده فایل‌به‌فایل در برابر وضعیت فعلی دیسک مقایسه می‌شوند؛ پیش از نوشتن هر چیزی تأیید می‌کنید، تا از بازنویسی‌های تصادفی جلوگیری شود.
- **دفترچه تولید**: هر فایل عمل، تعداد بایت، MD5 و محل پشتیبان خود را ثبت می‌کند؛ کل یک دسته قابل بازگشت است.

---

## ۷. ساخت سیستم با یک جمله (خط لوله)

یک قابلیت سطح‌بالا با هدف «ساخت بک‌اند از صفر». پس از توصیف حوزه تجاری، خط لوله به‌طور خودکار از ۵ مرحله پیش می‌رود:

| مرحله | معنا |
| --- | --- |
| `CHECK` | اعتبارسنجی پیکربندی پروژه مقصد و classpath وابستگی |
| `WRITE` | ایجاد پایگاه داده و جداول، نوشتن scaffold و مصنوعات تولیدشده |
| `COMPILE` | بک‌اند `mvn package` / ساخت فرانت‌اند، خودبررسی کامپایل در حافظه |
| `MENU` | تولید و ثبت دسترسی‌های منو (دو خروجی: SQL مصنوف / نوشتن مستقیم داخلی) |
| `ROUTE` | بازسازی قطعات مسیر فرانت‌اند |

شکست در هر مرحله بلافاصله با یک خطای خوانا متوقف می‌شود تا یافتن آن آسان باشد.

---

## ۸. معماری سیستم

فرانت‌اند و بک‌اند جدا شده‌اند، دو مخزن با هم کار می‌کنند:

| مخزن | نقش | پشته |
| --- | --- | --- |
| `codegen-server` | بک‌اند: موتور تولید، الگوها، اعتبارسنجی، دفترچه، خط لوله | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | فرانت‌اند: کنسول (پیکربندی جدول / الگوها / دفترچه / مدیریت سیستم) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (حالت history) |

لایه‌بندی بک‌اند:

```
controller/    API layer (Gen* generation chain / Sys* system management / pipeline)
service/impl/  Core implementations (generation chain, template engine, merge, validation, pipeline execution)
util/          Template engine, model assembly, marker-block merge, self-check compilation
config/        Output config, self-check config, template seeder
common/        Result / BaseEntity / exceptions / audit
resources/gen-templates/   22 FreeMarker templates (backend / frontend / shared / test groups)
```

**قابلیت‌های پایه‌ای که پروژه مقصد باید فراهم کند** (تولیدکننده فقط کد تجاری تولید می‌کند؛ پایه ناقص بی‌صدا شکست می‌خورد):

۱. افزونه صفحه‌بندی MyBatis-Plus (`PaginationInnerInterceptor`)
۲. کلاس‌های پایه مانند `Result` / `BaseEntity` / `PageQuery` (پیشوند بسته باید با `root-package` مطابقت داشته باشد)
۳. `utils/request.js` فرانت‌اند با `toPageData` (تبدیل `Page{records,total}` به `{list,total}`)
۴. وابستگی‌های `springdoc-openapi-starter-webmvc-ui` (کد تولیدشده از `@Operation` استفاده می‌کند) و `poi-ooxml` (Excel)

---

## ۹. نمای کلی پشته فناوری

| بُعد | انتخاب |
| --- | --- |
| زبان/چارچوب بک‌اند | Java 21 / Spring Boot 3.2.5 |
| پایداری | MyBatis-Plus 3.5.5 |
| موتور الگو | FreeMarker (۲۲ الگوی بذر، ویرایش آنلاین + بازگشت نسخه) |
| امنیت | احراز هویت JWT |
| پایگاه داده | MySQL 8 |
| فرانت‌اند (generated projects) | Vue 3 + Element Plus + Vite |
| فرانت‌اند (platform console) | Vue 2.7 + Element UI |
| AI محلی | qwen2.5-coder:7b (مدل‌سازی / پیکربندی توصیه‌شده / بهبود داده‌های‌بذر) |
| مستندات API | springdoc-openapi (Swagger UI) |

---

## ۱۰. شروع سریع

### ۱. ایجاد پایگاه داده (۱۳ جدول پلتفرم + حساب پیش‌فرض admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### ۲. پیکربندی پروژه مقصد

کپی الگوی پیکربندی و تنظیم آن با مسیرهای محلی خود:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` توسط `.gitignore` مستثنی شده و کامیت نخواهد شد. تنظیمات کلیدی:

```yaml
gen:
  output:
    backend-root: /path/to/your-target-project      # write-to-disk base for artifacts
    frontend-root: /path/to/your-target-project
    frontend-path: demo-web                          # frontend subdirectory for monorepos
    backend-path: demo-server/src/main/java/com/demo
    package-name: com.demo
    root-package: com.demo                           # prefix for foundation-class imports in artifacts
  selfcheck:
    classpath: /path/to/your-target-project/demo-lib/*
```

دایرکتوری وابستگی خودبررسی را این‌گونه صادر کنید:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### ۳. راه‌اندازی بک‌اند

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

سرویس به‌طور پیش‌فرض روی پورت **8120** است؛ مستندات API در `/swagger-ui/index.html`.

### ۴. راه‌اندازی فرانت‌اند

```bash
cd ../codegen-web
npm install
npm run serve
```

فرانت‌اند به‌طور پیش‌فرض روی پورت **9530** است، و `/dev-api` را به پورت ۸۱۲۰ بک‌اند پروکسی می‌کند. حساب پیش‌فرض `admin / admin123`.

### ۵. موارد پیکربندی کلیدی

| تنظیم | توضیح |
| --- | --- |
| `gen.output.*` | مسیرهای نوشتن مصنوف، نام‌های بسته، نگاشت ماژول |
| `gen.selfcheck.classpath` | classpath وابستگی برای خودبررسی کامپایل در حافظه، پشتیبانی از `dir/*` |
| `gen.menu.outlet` | `artifact` فقط SQL منو و قطعات مسیر تولید می‌کند؛ `internal` همچنین `sys_menu` خود پلتفرم را می‌نویسد |

---

## ۱۱. نقشه مستندات

| سند | محتوا |
| --- | --- |
| `../codegen-server/docs/代码生成器-操作手册.md` (چینی) | استقرار، شروع ۵ دقیقه‌ای، پیکربندی جدول، ورود دسته‌ای، مدیریت الگو، دفترچه، فهرست API، خط لوله کامل (با تصاویر) |
| `../codegen-server/docs/典型场景操作指南-演示后台demo.md` (چینی) | ۶ جدول پوشش‌دهنده ۵ سناریوی معمول، با دموی اجرایی و تصاویر اجرای واقعی |
| `项目介绍.md` | سند معرفی پروژه، همان منبع این فایل |
| `../codegen-server/docs/代码生成器-Vx.x-规划.md` | برنامه‌های نسخه و فهرست ویژگی‌ها از V3.1 → V9.3 |
| `../codegen-web/README.md` | مرجع شروع سریع کنسول فرانت‌اند |

---

## ۱۲. تاریخچه نسخه‌ها

| نسخه | تم / سند برنامه | کامیت فرود |
| --- | --- | --- |
| V1.2 / V1.3 | تولید / دسته‌ای / بازگشت با یک کلیک؛ پیش‌نمایش تفاوت و همگام‌سازی ساختار | — (فقط در نظرات `src/api/gen.js`) |
| V2.0 | مسیرهای پویا (درخت منوی تحویل‌شده توسط بک‌اند) | — |
| V3.0 / V3.1 | دفترچه چندسیستمی؛ حلقه چندسیستمی + نخستین تست‌های خودکار | V3.1 (۱۷۴ خط) |
| V4.2 | نتایج تولید ردیابی‌پذیر و بازگشت‌پذیر (دفترچه نوشتن) | V4.2 (۲۹۲ خط) |
| V4.3 | رگرسیون الگو و نگه‌دارهای کیفیت | V4.3 (۲۰۰ خط) |
| V4.4 | یک‌استاد-چندجزئیات، کامل بودن نوشتن آبشاری، خطوط الگو | V4.4 (۱۵۴ خط) |
| V4.5 | اسناد چاپ A4 (بدون وابستگی شخص ثالث) | V4.5 (۱۲۷ خط) |
| V5.0 | ساخت سیستم با یک جمله: خط لوله + مدل‌سازی AI + پایه scaffold | — |
| V5.1 | تکمیل خودکار ستون‌های حسابرسی DDL | V5.1 (۱۰۰ خط) |
| V5.2 | گام مقداردهی اولیه داده‌های‌بذر خط لوله | V5.2 (۱۰۷ خط)، 84ec8dd |
| V5.5 | جمع‌بندی تولید پروژه چندماژولی | af73dd3 |
| V6.0 | زیرساخت ریزخدمات (Nacos / Gateway / Feign، حالت سوم «micro») | V6.0 (۱۱۱ خط)، 1deecbd |
| V6.1 | موتور دوگانه جریان کاری Activiti | V6.1 (۱۰۰ خط)، 02b671e |
| V6.2 | سطح تحویل مصنوف (CLI / مصنوفات استقرار / امنیت پیکربندی) | V6.2 (۱۱۴ خط)، 02b671e |
| V6.3 | بسته قابلیت پرداخت (WeChat / Alipay + Mock) | V6.3 (۱۰۴ خط)، 1c4ee6a، 22eede5 |
| V6.4 | خط پایه دسترسی و امنیت (پایداری توکن / BCrypt / دسترسی‌های API / حفاظت ورود) | V6.4 (۱۰۷ خط)، 007a202 |
| V6.5 | موبایل uni-app (H5 + برنامه کوچک WeChat) | V6.5 (۱۰۵ خط)، 779ac41 |
| V6.6 | بسته قابلیت فایل | 98f3c09 |
| V6.7 | پذیرش کارخانه تست مصنوف (tri-state test_enabled + اسکلت H2) | V6.7 (۱۳۴ خط)، 4e7d318 |
| V6.8 | نگهداری‌پذیری مصنوف (dryRun / skip محافظت‌شده / پارامترسازی برند) | V6.8 (۱۴۱ خط)، be6f858 |
| V6.9 | مرکز مستندات پلتفرم (پایه بک‌اند، فرانت‌اند در همان دسته تمام شد) | — |
| V6.10 | قابلیت مشاهده پلتفرم: Actuator + پنجره متریک در حافظه + صفحه مانیتور تعبیه‌شده | — |
| V7.0 | توصیه‌های صفحه و منو توسط AI (پیش‌نمایش / اعمال) | V7.0 (۹۴ خط)، 45f2670 |
| V7.1 | بهبود داده‌های‌بذر با AI (AI معنا را دارد، قوانین ساختار را دارند) | V7.1 (۴۲ خط)، 5ca6e72 |
| V7.2 | تحویل‌های SQL پس از موفقیت خط لوله در `db/` نوشته می‌شوند | eebff88 |
| **V7.3** | **اعتمادپذیری تحویل: تست‌های مصنوف کامپایل‌شده خودبررسی‌شده / خروجی یکپارچه تحویل SQL / توصیه‌های AI به‌ازای هر جدول** | **V7.3 (۱۳۸ خط), 696765b** |
| **V7.4** | تحویل تکرارپذیر: قابل استقرار از یک پایگاه داده خالی، هر کامیت تضمین‌شده اجرایی | c306e29 |
| **V7.5** | **پیکربندی زبان پروژه: فهرست ۲۰۶ زبان / زبان پیش‌فرض / زبان‌های فعال، تزریق lang و dir در خروجی تولیدی** | 7a9afa0 + abac56d |
| **V8.0** | **i18n تمام‌صفحه: چارچوب ۲۰۶ زبانه (۸ زبان ترجمه حرفه‌ای)، i18n دورخط با قرارداد صفر دیف در OFF** | 9e5dd57 → 7d57aea |
| **V8.1** | **سخت‌سازی کیفیت: هارنس قرارداد آفلاین (۹ حالت، صفر دیف OFF) + تست واحد i18n-core + گیت CI + تفکیک صادقانه README (چارچوب ۲۰۶ / ترجمه‌شده ۸) + check-locale-keys.mjs** | 93a12d5 + e49330b |
| **V9.0** | **خروجی‌های تولیدی به Vue 3 + Element Plus + Vite مدرن شدند؛ در زمان اجرا راستی‌آزمایی شد (۴۰/۴۰ ادعا، vite build موفق، ۱۴ اسکرین‌شات شامل ar-SA RTL)؛ ۳ عیب زمان اجرا برطرف شد** | 93a12d5 + e49330b  |
| **V9.1** | **بازآرایی برای نگهداریپذیری · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9** | 654b8e0 → b0d081e  |
| **V9.2** | **مرکز آزمون و مرکز تأیید · Test Center (7 tables: env/suite/case/dataset/run/result/schedule) + Verification Center (tc_run/tc_case_result/tc_artifact, 9 suites S1-S9, single-run 409 lock, `codegen.testcenter/v1`); 373/373 tests; 206/206 locales; output byte-identical** | V9.2 release  |
| **V9.3** | **آزمون خودکار خروجی‌های تولیدشده · Generated-artifact automated testing: S10 (mvn test + Surefire), S11 (API / TestContainers), S12 (E2E / Playwright); GEN trigger + TC_GEN_ROOT; 12-step pipeline green; real artifact run PASSED 20/20, run/case/artifact persisted** | V9.3 release, **نسخه فعلی** |

---

> **V6.10 قابلیت مشاهده پلتفرم**: یکپارچه‌سازی Actuator + Micrometer، نمونه‌برداری هر ۵ ثانیه در یک بافر حلقوی در حافظه (پنجره ۳۰ دقیقه‌ای)، به‌علاوه یک صفحه تعبیه‌شده جدید «مانیتور سیستم» — APIهای تجمیعی `/monitor/*` بک‌اند + پنل‌های ECharts فرانت‌اند. فقط نقاط پایانی `health/info/metrics` در لیست سفید هستند؛ `/actuator/health` بدون توکن است در حالی که `metrics` و غیره نیاز به احراز هویت دارند؛ `env/beans/heapdump/threaddump/loggers` هرگز افشا نمی‌شوند. پنجره نمونه‌برداری فقط به‌ازای هر فرآیند است، در راه‌اندازی مجدد از دست می‌رود و بین نمونه‌ها به اشتراک گذاشته نمی‌شود. برای جزئیات ببینید `../codegen-server/docs/可观测性说明.md` (چینی).

## ۱۳. وضعیت پروژه

- خط نسخه برنامه‌ریزی‌شده فعلی به **V9.3** رسیده است (قابلیت‌ها همچنان تکرار می‌شوند: مدل‌سازی AI، بهبود داده‌های‌بذر، سیستم بسته قابلیت و غیره).
- یک پروژه دموی همراه `demo_admin` (بک‌اند ۸۱۴۰ / فرانت‌اند ۹۰۴۰) سناریوهای درختی / CRUD / استاد-جزئیات / فقط‌خواندنی / چاپ را پوشش می‌دهد و برای تأیید به‌طور مستقل اجرا می‌شود.
- هر دو مخزن روی Gitee میزبانی می‌شوند: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## ۱۴. نویسنده و پشتیبانی

- **نویسنده**: یک برنامه‌نویس چینی (توسعه‌دهنده مستقل)
- **ایمیل**: `hustxiayuhong@126.com`
- **پشتیبانی فنی**: لطفاً ایمیل خود را **به چینی** بنویسید، شامل توصیف مشکل، محیط اجرا (JDK / MySQL / نسخه) و مراحل بازتولید

> من هر ایمیل را می‌خوانم و به آن پاسخ می‌دهم. گزارش‌های باگ، درخواست‌های ویژگی و پرس‌وجوهای همکاری همگی خوش‌آمد هستند.


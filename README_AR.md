# ما جيانغ · مولّد الأكواد (CodeGen) — مقدمة المشروع

> Languages: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · **العربية** · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

> اللغات: [中文](README_ZH.md) · [English](README.md) · [日本語](README_JA.md) · [Português](README_PT.md) · [Español](README_ES.md) · [한국어](README_KO.md) · [Français](README_FR.md) · [Deutsch](README_DE.md) · [हिन्दी](README_HI.md) · **العربية** · [বাংলা](README_BN.md) · [Русский](README_RU.md) · [اردو](README_UR.md)

---

> مولّد أكواد متعدد المشاريع موجّه إلى **Spring Boot + Vue**: انطلاقًا من بنية جداول قاعدة البيانات، ينتج أكواد أعمال للواجهة الأمامية والخلفية **قابلة للترجمة والتشغيل وجاهزة بالقوائم والصلاحيات**، ويتولّى حلقة التسليم الكاملة من الكتابة على القرص والنسخ الاحتياطي والتسجيل في السجل والتراجع.

عنوان GitHub: https://github.com/hustxia/CodeGen

## 1. ما هذا

CodeGen هو «مصنع أكواد قائم على الجداول». كل ما عليك هو إدخال جداول أعمالك (عن طريق الاستيراد من قاعدة البيانات أو لصق تعليمة DDL)، وضبط الحقول ومفاتيح التوليد، ثم النقر على «توليد» — فيتولى المولّد إنتاج كل شيء: كيانات الواجهة الخلفية Entity/Mapper/Service/Controller/DTO/VO، وملف `api.js` وصفحات Vue في الواجهة الأمامية، والقوائم والمسارات، بل وصفحات طباعة A4 وهياكل اختبارات التكامل، ثم يكتبها بأمان في مشروعك الهدف.

إنه لا يكتفي بـ«إخراج الأكواد»، بل هو **خط تسليم مزوّد ببوابات جودة وقدرة على التراجع**:

```
Table intake (DB import / DDL)
   → Field & switch configuration (with AI recommendations)
      → One-click generation: self-check → backup → write to disk → compile/validate → register menu → rebuild routes
         → Ledger bookkeeping → rollback-capable
```

---

## 2. من هم المستخدمون وكيف يستخدمونه

| المستخدم | المطلب النموذجي | المسار الذي يوفّره المنصّة |
| --- | --- | --- |
| مطوّر الواجهة الخلفية | لديه مجموعة من الجداول (أو DDL) ويريد إطلاق CRUD سريع | صفحة إعداد الجداول: الاستيراد → ضبط الحقول → التوليد بنقرة واحدة |
| مطوّر متكامل / مستقل | يريد بناء نظام كامل من الصفر (يشمل تسجيل الدخول والصلاحيات والقواميس) | صفحة خط الأنابيب: إنشاء نظام بجملة واحدة |
| مسؤول الفريق | يريد توحيد أسلوب الكود وقواعده وهيكل الدلائل | إدارة النظام: نظام واحد = مجموعة واحدة من إعدادات الإخراج، تُطبَّق على دفعات |
| صيانة المنصّة | يريد تعديل القوالب دون تعديل الكود | إدارة القوالب: تحرير عبر الإنترنت، يسري فور الحفظ، مع دعم التراجع التاريخي |

## 3. ما المشكلات التي يحلّها

| نقطة الألم | ما يفعله CodeGen |
| --- | --- |
| جحيم CRUD المتكرر — كومة من الأكواد النموذجية لكل جدول | مفاتيح على مستوى الجدول تنتج واجهة أمامية وخلفية كاملة بنقرة واحدة، بما يشمل الإضافة والحذف والتعديل والاستعلام والترقيم والتحقق والاستيراد/التصدير |
| عدم تطابق الحقول وواجهات API بين الواجهتين يؤدي إلى تعديلات لا تنتهي أثناء التكامل | البيانات الوصفية للجدول نفسه تُشغّل واجهات الخلفية وصفحات الواجهة الأمامية معًا — صفر انحراف في الدلالات |
| صعوبة توحيد معايير الفريق؛ أسلوب كل شخص مختلف | القوالب هي المعيار: 22 قالب FreeMarker تُدار مركزيًا، مع تحرير عبر الإنترنت وتراجع بين الإصدارات |
| المولّدات «تستبدل فتفقد» الأكواد المكتوبة يدويًا | آلية دمج الكتل المعلَّمة (marker block): الكود المكتوب يدويًا يُحفظ، ولا تُستبدل إلا المناطق المولَّدة |
| أكواد مولَّدة لا تُوثق حتى لحظة الترجمة | فحص ذاتي بالترجمة في الذاكرة (`javac` حقيقي)، وفحوصات عقود البنية، ومعاينة diff على القرص — لا شيء يُكتب على القرص ما لم يُترجم بنجاح |
| بناء هيكل نظام جديد من الصفر مستهلك للوقت | خط الأنابيب «أنشئ نظامًا بجملة واحدة»: إنشاء قاعدة البيانات ← الهيكل الأساسي ← التوليد ← الحزم ← تسجيل القوائم، بشكل آلي بالكامل |

---

## 4. خريطة القدرات الشاملة

- **استقبال الجداول**: تحليل DDL، واستيراد مباشر من قاعدة البيانات، ومعالج استقبال دفعي، واشتقاق تلقائي لإعدادات الأعمدة (عناصر التحكم / التحقق / القواميس / الارتباطات).
- **أشكال الجداول**: جدول مفرد، جدول شجري (واجهة tree + شبكة شجرية)، جدول رئيسي-فرعي (حفظ في معاملة واحدة + تبويبات تفاصيل)، جداول وسيطة متعدد-إلى-متعدد.
- **نواتج التوليد**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + صفحات Vue + صفحات طباعة A4 + هياكل اختبارات التكامل.
- **تحسينات الأعمال**: قوائم منسدلة للارتباطات، وترجمة القواميس، وإخفاء البيانات الحساسة، وفحص التفرد، وترابط النماذج، وصلاحيات نطاق البيانات، واستيراد/تصدير Excel، وسير عمل الموافقات، وحزم قدرات الدفع / الملفات / الهاتف المحمول.
- **بوابات الجودة**: فحص ذاتي بالترجمة في الذاكرة (`javac` حقيقي)، وفحوصات عقود البنية، وفحص اتساق الصلاحيات، ومعاينة diff على القرص، وتحقق حقيقي من نواتج الواجهة الأمامية.
- **هبوط آمن**: الكتابة على القرص + نسخ احتياطي دفعي في `.gen-backup`، ودمج الكتل المعلَّمة للحفاظ على الكود اليدوي، وسجل `gen_output` بأربع حالات (أُنشئ / دُمج / تُخُطّي / فشل)، وتراجع تاريخي.
- **عزل الأنظمة المتعددة**: كل مشروع هدف هو «إعدادات نظام» (مسارات الواجهتين، أسماء الحزم، بادئة البوابة، classpath التبعيات، القيم الافتراضية لمفاتيح التوليد) — يمكن خدمة عدة مشاريع بالتوازي.
- **تحسينات الذكاء الاصطناعي**: إنشاء نظام بجملة واحدة (نمذجة بالذكاء الاصطناعي)، وتعزيز بيانات البذر بالذكاء الاصطناعي، وإعدادات موصى بها لكل جدول بالذكاء الاصطناعي (معاينة diff + تطبيق بانتقاء).

---

## 5. سيناريوهات نموذجية

| السيناريو | الوصف |
| --- | --- |
| جدول شجري | بيانات هرمية مثل تصنيفات المنتجات — واجهة tree وشبكة شجرية وقائمة منسدلة لاختيار الأب تُنتج تلقائيًا |
| CRUD لجدول مفرد | العملاء والمنتجات وما شابه — قوائم منسدلة للمفاتيح الأجنبية + ترجمة قواميس + استيراد/تصدير جاهزة من أول استخدام |
| جدول رئيسي-فرعي | أوامر البيع + بنود الطلب — حفظ في معاملة واحدة، ودرج تفاصيل بتبويبين (معلومات الرئيسي / التفاصيل) |
| قراءة فقط / إحصاءات | حركات المخزون وما شابه — تُغلق مفاتيح الكتابة ويُبقى الاستعلام والتصدير فقط |
| مستندات الطباعة | ضع علامة على مفتاح «الطباعة» لإنتاج صفحة طباعة A4 ومسارها، مع ترويسة + جدول تفاصيل |
| إنشاء نظام بجملة واحدة | صِف مجال الأعمال — فيُنشئ الذكاء الاصطناعي قاعدة البيانات والجداول + الهيكل الأساسي + توليدًا كاملًا + تسجيل القوائم دفعة واحدة |

> جميع السيناريوهات المذكورة لها عروض توضيحية قابلة للتشغيل (مشروع `demo_admin`) ولقطات شاشة مرافقة — راجع `docs/典型场景操作指南-演示后台demo.md` (الصينية).

---

## 6. سير العمل الأساسي

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Table intake │ → │ Field/switch conf. │ → │ One-click generate (self-check→backup→write→     │ → │ Ledger · Rollback │
│              │   │                    │   │ compile→menu→route)                              │   │                   │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     controls/validation     Lands on disk only after all quality gates pass;      Batch-level traceability
                    /dictionaries           hand-written code is preserved
```

- **معاينة الفروقات**: تُقارَن النواتج المولَّدة بالحالة الحالية على القرص ملفًا بملف، ولا يُكتب شيء إلا بعد تأكيدك، تفاديًا للاستبدال غير المقصود.
- **سجل التوليد**: يُسجَّل لكل ملف إجراءه وحجمه بالبايت وبصمة MD5 وموقعه الاحتياطي؛ ويمكن التراجع عن دفعة كاملة.

---

## 7. إنشاء نظام بجملة واحدة (خط الأنابيب / Pipeline)

قدرة متقدمة تستهدف «بناء هيكل نظام إداري من الصفر». بعد وصفك لمجال الأعمال، يتقدّم خط الأنابيب تلقائيًا عبر 5 مراحل:

| المرحلة | المعنى |
| --- | --- |
| `CHECK` | التحقق من إعدادات المشروع الهدف ومن classpath التبعيات |
| `WRITE` | إنشاء قاعدة البيانات والجداول، وكتابة الهيكل الأساسي والنواتج المولَّدة |
| `COMPILE` | `mvn package` للخلفية / بناء الواجهة الأمامية، مع فحص ذاتي بالترجمة في الذاكرة |
| `MENU` | إنتاج صلاحيات القوائم وتسجيلها (مخرجان: SQL في النواتج / كتابة داخلية مباشرة) |
| `ROUTE` | إعادة بناء أجزاء مسارات الواجهة الأمامية |

أي فشل في مرحلة ما يوقف العملية فورًا مع رسالة خطأ مقروءة تسهّل تحديد الموقع.

---

## 8. بنية النظام

فصل بين الواجهتين الأمامية والخلفية، بمستودعين يعملان معًا:

| المستودع | الدور | حزمة التقنيات |
| --- | --- | --- |
| `codegen-server` | الخلفية: محرك التوليد، القوالب، التحقق، السجل، خط الأنابيب | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | الواجهة الأمامية: لوحة التحكم (إعداد الجداول / القوالب / السجل / إدارة النظام) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (history) |

طبقات الواجهة الخلفية:

```
controller/    API layer (Gen* generation chain / Sys* system management / pipeline)
service/impl/  Core implementations (generation chain, template engine, merge, validation, pipeline execution)
util/          Template engine, model assembly, marker-block merge, self-check compilation
config/        Output config, self-check config, template seeder
common/        Result / BaseEntity / exceptions / audit
resources/gen-templates/   22 FreeMarker templates (backend / frontend / shared / test groups)
```

**قدرات الأساس التي يجب أن يوفرها المشروع الهدف بنفسه** (المولّد ينتج أكواد الأعمال فقط؛ وغياب الأساس يؤدي إلى أعطال صامتة):

1. إضافة ترقيم الصفحات في MyBatis-Plus (`PaginationInnerInterceptor`)
2. أصناف الأساس مثل `Result` / `BaseEntity` / `PageQuery` (يجب أن تطابق بادئة الحزمة قيمة `root-package`)
3. دالة `toPageData` في `utils/request.js` بالواجهة الأمامية (تحوّل `Page{records,total}` إلى `{list,total}`)
4. التبعيتان `springdoc-openapi-starter-webmvc-ui` (الكود المولَّد يستخدم `@Operation`) و`poi-ooxml` (لملفات Excel)

---

## 9. حزمة التقنيات في لمحة

| البُعد | الاختيار |
| --- | --- |
| لغة/إطار الخلفية | Java 21 / Spring Boot 3.2.5 |
| طبقة التخزين | MyBatis-Plus 3.5.5 |
| محرك القوالب | FreeMarker (22 قالبًا أساسيًا، مع تحرير عبر الإنترنت + تراجع بين الإصدارات) |
| الأمان | مصادقة JWT |
| قاعدة البيانات | MySQL 8 |
| الواجهة الأمامية (generated projects) | Vue 3 + Element Plus + Vite |
| الواجهة الأمامية (platform console) | Vue 2.7 + Element UI |
| الذكاء الاصطناعي المحلي | qwen2.5-coder:7b (النمذجة / الإعدادات الموصى بها / تعزيز بيانات البذر) |
| توثيق الواجهات | springdoc-openapi (Swagger UI) |

---

## 10. البدء السريع

### 1. إنشاء قاعدة البيانات (13 جدول منصة + حساب افتراضي admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. إعداد المشروع الهدف

انسخ قالب الإعدادات وعدّله حسب مسارات جهازك:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

الملف `application-local.yml` مستثنى عبر `.gitignore` ولن يُرفع إلى المستودع. أهم بنود الإعداد:

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

وهكذا تُصدَّر دليل تبعيات الفحص الذاتي:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. تشغيل الواجهة الخلفية

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

المنفذ الافتراضي للخدمة هو **8120**، وتوثيق الواجهات على `/swagger-ui/index.html`.

### 4. تشغيل الواجهة الأمامية

```bash
cd ../codegen-web
npm install
npm run serve
```

المنفذ الافتراضي للواجهة الأمامية هو **9530**، مع تمرير `/dev-api` إلى المنفذ 8120 في الخلفية. الحساب الافتراضي `admin / admin123`.

### 5. بنود الإعداد الأساسية

| الإعداد | الوصف |
| --- | --- |
| `gen.output.*` | مسارات كتابة النواتج على القرص، وأسماء الحزم، وربط الوحدات |
| `gen.selfcheck.classpath` | classpath التبعيات لفحص الترجمة في الذاكرة، ويدعم `dir/*` |
| `gen.menu.outlet` | القيمة `artifact` تُنتج SQL القوائم وأجزاء المسارات فقط؛ أما `internal` فتكتب أيضًا في `sys_menu` للمنصة نفسها |

---

## 11. دليل التوثيق

| المستند | المحتوى |
| --- | --- |
| `docs/代码生成器-操作手册.md` (الصينية) | النشر، البدء في خمس دقائق، إعداد الجداول، الاستقبال الدفعي، إدارة القوالب، السجل، دليل الواجهات، مسار خط الأنابيب الكامل (مع لقطات شاشة فعلية) |
| `docs/典型场景操作指南-演示后台demo.md` (الصينية) | 6 جداول تغطي 5 سيناريوهات نموذجية، مع عرض توضيحي قابل للتشغيل ولقطات من تشغيل حقيقي |
| `docs/项目介绍.md` (الصينية) | مستند مقدمة المشروع، من المصدر نفسه الذي صدر عنه هذا الملف |
| `docs/代码生成器-Vx.x-规划.md` (الصينية) | خطط الإصدارات وجرد الميزات من V3.1 إلى V7.3 |
| `codegen-web/README.md` | مرجع البدء السريع للوحة تحكم الواجهة الأمامية |

---

## 12. سجل الإصدارات

| الإصدار | موضوع الإصدار / مستند الخطة | كومِت التنفيذ |
| --- | --- | --- |
| V1.2 / V1.3 | التوليد بنقرة واحدة / الدفعات / التراجع؛ معاينة diff ومزامنة البنية | — (يظهر فقط في تعليقات `src/api/gen.js`) |
| V2.0 | المسارات الديناميكية (شجرة القوائم تُرسَل من الخلفية) | — |
| V3.0 / V3.1 | سجل متعدد الأنظمة؛ حلقة متعددة الأنظمة + أول اختبارات آلية | V3.1 (174 سطرًا) |
| V4.2 | نتائج توليد قابلة للتتبع والتراجع (سجل الكتابة على القرص) | V4.2 (292 سطرًا) |
| V4.3 | انحدار القوالب ودرابزين الجودة | V4.3 (200 سطر) |
| V4.4 | رئيسي واحد وعدة تفاصيل، اكتمال الكتابة التتابعية، تخصيص مسارات للقوالب | V4.4 (154 سطرًا) |
| V4.5 | مستندات طباعة A4 (صفر تبعيات خارجية) | V4.5 (127 سطرًا) |
| V5.0 | إنشاء نظام بجملة واحدة: خط الأنابيب + النمذجة بالذكاء الاصطناعي + هيكل أساسي | — |
| V5.1 | استكمال أعمدة التدقيق في DDL تلقائيًا | V5.1 (100 سطر) |
| V5.2 | خطوة تهيئة بيانات البذر في خط الأنابيب | V5.2 (107 أسطر)، 84ec8dd |
| V5.5 | استكمال توليد المشاريع متعددة الوحدات | af73dd3 |
| V6.0 | بنية الخدمات المصغرة (Nacos / Gateway / Feign، والوضع الثالث micro) | V6.0 (111 سطرًا)، 1deecbd |
| V6.1 | محرك مزدوج لسير عمل Activiti | V6.1 (100 سطر)، 02b671e |
| V6.2 | واجهة تسليم النواتج (CLI / عناصر النشر / أمان الإعدادات) | V6.2 (114 سطرًا)، 02b671e |
| V6.3 | حزمة قدرات الدفع (WeChat / Alipay + Mock) | V6.3 (104 أسطر)، 1c4ee6a و22eede5 |
| V6.4 | خط أساس أمان الصلاحيات (تخزين token دائم / BCrypt / صلاحيات الواجهات / حماية تسجيل الدخول) | V6.4 (107 أسطر)، 007a202 |
| V6.5 | uni-app للهاتف المحمول (H5 + WeChat Mini Program) | V6.5 (105 أسطر)، 779ac41 |
| V6.6 | حزمة قدرات الملفات | 98f3c09 |
| V6.7 | اختبار النواتج عند التسليم (ثلاث حالات لـ test_enabled + هيكل H2) | V6.7 (134 سطرًا)، 4e7d318 |
| V6.8 | قابلية صيانة النواتج المولَّدة (dryRun / تخطٍ محمي / معلمة العلامة التجارية) | V6.8 (141 سطرًا)، be6f858 |
| V6.9 | مركز توثيق المنصة (أساس الخلفية، واكتملت الواجهة الأمامية في الدفعة نفسها) | — |
| V6.10 | قابلية رصد المنصة: Actuator + نافذة مقاييس في الذاكرة + صفحة مراقبة مدمجة | — |
| V7.0 | توصيات صفحات وقوائم بالذكاء الاصطناعي (preview / apply) | V7.0 (94 سطرًا)، 45f2670 |
| V7.1 | تعزيز بيانات البذر بالذكاء الاصطناعي (الذكاء الاصطناعي يدير الدلالات والقواعد تدير البنية) | V7.1 (42 سطرًا)، 5ca6e72 |
| V7.2 | كتابة مخرجات SQL إلى `db/` بعد نجاح خط الأنابيب | eebff88 |
| **V7.3** | **موثوقية التسليم: اختبار نواتج الترجمة ذاتية الفحص / مخرج موحد لتسليم SQL / توصيات ذكاء اصطناعي على مستوى الجدول** | **V7.3 (138 سطرًا)** |
| **V7.4** | **تسليم قابل للتكرار: نشر ممكن من قاعدة بيانات فارغة، وضمان قابلية التشغيل مع كل كومِت** | c306e29 |
| **V7.5** | إعداد لغة المشروع: قائمة 206 لغة / اللغة الافتراضية / اللغات المفعلة، حقن lang و dir في المولَّد | 7a9afa0 + abac56d |
| **V8.0** | تدويل كامل الصفحة: إطار 206 لغة (8 مترجمة احترافياً)، i18n ثنائي المسار مع عقد فرق صفري عند OFF | 9e5dd57 → 7d57aea |
| **V8.1** | تحصين الجودة: حزام عقد offline (9 أوضاع، فرق صفري OFF) + اختبارات وحدة i18n-core + بوابة CI + تقسيم صادق في README (إطار 206 / مترجمة 8) + check-locale-keys.mjs | 93a12d5 + e49330b |
| **V9.0** | تحديث المخرجات المولدة إلى Vue 3 + Element Plus + Vite؛ تم التحقق وقت التشغيل (40/40 تأكيدات، vite build ناجح، 14 لقطة شاشة تشمل ar-SA RTL)؛ إصلاح 3 عيوب تشغيل | 93a12d5 + e49330b  |
| **V9.1** | **إعادة هيكلة لقابلية الصيانة · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9** | 654b8e0 → b0d081e, **الإصدار الحالي** |

---

> **V6.10 قابلية رصد المنصة**: تكامل مع Actuator + Micrometer بأخذ عينات كل 5 ثوانٍ في مخزن حلقي داخل الذاكرة (نافذة مدتها 30 دقيقة)، مع صفحة «مراقبة النظام» مدمجة جديدة — واجهات تجميع `/monitor/*` في الخلفية + لوحات ECharts في الواجهة الأمامية. نقاط النهاية المسموحة في القائمة البيضاء هي `health/info/metrics` فقط؛ و`/actuator/health` متاح دون token بينما تتطلب `metrics` وما شابه مصادقة؛ أما `env/beans/heapdump/threaddump/loggers` فلا تُعرَض أبدًا. نافذة العينات داخل العملية فقط، وتُفقد عند إعادة التشغيل، ولا تُشارك بين النسخ المتعددة. التفاصيل في `docs/可观测性说明.md` (الصينية).

## 13. حالة المشروع

- وصل خط الإصدارات المخطط حاليًا إلى **V9.1**، وتواصل الميزات في التحديث المستمر (النمذجة بالذكاء الاصطناعي، تعزيز بيانات البذر، منظومة حزم القدرات وغيرها).
- أُنشئ مشروع عرض توضيحي مرافق `demo_admin` (خلفية على المنفذ 8140 / واجهة أمامية على 9040)، يغطي سيناريوهات الشجرة / CRUD / الرئيسي-الفرعي / القراءة فقط / الطباعة كلها، ويمكن تشغيله والتحقق منه بشكل مستقل.
- كلا المستودعين مستضافان على Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. المؤلف والدعم الفني

- **المؤلف**: مبرمج صيني (مطور مستقل)
- **البريد الإلكتروني**: `hustxiayuhong@126.com`
- **الدعم الفني**: يُرجى كتابة البريد **باللغة الصينية** مع وصف المشكلة وبيئة التشغيل (JDK / MySQL / الإصدار) وخطوات إعادة إنتاج المشكلة

> أقرأ كل رسالة وأرد عليها. نرحب ببلاغات الأخطاء والاقتراحات وطلبات التعاون.

---

## 15. المقارنة مع الأدوات المشابهة

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

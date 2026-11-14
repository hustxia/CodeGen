# Majiang · מחולל קוד (CodeGen) — מבוא לפרויקט

> שפות: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · **עברית** · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> מחולל קוד רב-פרויקטים עבור **Spring Boot + Vue**: מתחיל ממבני טבלאות מסד נתונים, הוא מייצר קוד עסקי **ניתן להרכבה, ניתן להפעלה** עבור חזית ועורף עם תפריטים והרשאות, ומטפל במחזור המסירה המלא: כתיבה לדיסק, גיבוי, ספר חשבונות (ledger) וביטול.

GitHub: https://github.com/hustxia/CodeGen

## 1. מה זה

CodeGen הוא "מפעל קוד מונע טבלאות". הבא את טבלאות העסק שלך (דרך ייבוא ממסד נתונים או על ידי הדבקת DDL), קבע שדות ומתגי יצירה ולחץ על "צור" — אז הוא מייצר את ה-backend Entity/Mapper/Service/Controller/DTO/VO, את ה-frontend `api.js` ודפי Vue, תפריטים ומסלולים, אפילו דפי הדפסה A4 ושלדי בדיקות אינטגרציה, וכותב הכל בבטחה לפרויקט היעד שלך.

זה לא רק "מוציא קוד" — זהו **צנרת מסירה עם שערי איכות ויכולת ביטול**:

```
קלט טבלאות (ייבוא DB / DDL)
   → תצורת שדות ומתגים (עם המלצות AI)
      → יצירה בלחיצה אחת: בדיקה עצמית → גיבוי → כתיבה לדיסק → הרכבה/אימות → רישום תפריט → בנייה מחדש של מסלולים
         → רישום בספר החשבונות → ניתן לביטול
```

---

## 2. מי המשתמשים וכיצד הם ישתמשו

| משתמש | צורך טיפוסי | הנתיב שהפלטפורמה מספקת |
| --- | --- | --- |
| מפתח Backend | יש לו ערימה של טבלאות (או DDL) והוא רוצה להקים CRUD במהירות | דף תצורת טבלה: ייבוא → הגדרת שדות → יצירה בלחיצה אחת |
| מפתח Full-stack / עצמאי | רוצה לבנות מערכת שלמה מאפס (כולל התחברות, הרשאות, מילונים) | דף הצנרת: בנה מערכת במשפט אחד |
| ראש צוות | רוצה לאחד סגנון קוד, תקני קוד ומבנה תיקיות | ניהול מערכת: מערכת אחת = ערכת תצורת פלט אחת, מיושמת בצובר |
| מתחזק פלטפורמה | רוצה לשנות תבניות מבלי לשנות קוד | ניהול תבניות: עריכה מקוונת, נכנסת לתוקף מיד בשמירה, עם תמיכה בחזרת גרסאות |

## 3. אילו בעיות זה פותר

| נקודת כאב | מה עושה CodeGen |
| --- | --- |
| גיהינום CRUD חוזר — ערימה של קוד boilerplate לכל טבלה | מתגים ברמת הטבלה מייצרים חזית ועורף מלאים בלחיצה אחת, כולל CRUD, דף, אימות, ייבוא/ייצוא |
| אי התאמות שדות ו-API בין חזית לעורף הגורמות לתיקוני אינטגרציה אינסופיים | אותם מטאנתוני טבלה מניעים גם API-ים של העורף וגם דפי החזית — אפס סטייה סמנטית |
| קשה לאחד מוסכמות צוות; כולם כותבים בסגנון אחר | תבניות הן המוסכמה: 22 תבניות FreeMarker המנוהלות באופן מרכזי, ניתנות לעריכה מקוונת עם חזרת גרסאות |
| מחוללים "משכתבים ומאבדים" לוגיקה שנכתבה ביד | מנגנון מיזוג בלוקי סמן: קוד שנכתב ביד נשמר, רק אזורים שנוצרו מוחלפים |
| קוד שנוצר לא אמין עד לזמן ההרכבה | בדיקה עצמית עם הרכבה בזיכרון (`javac` אמיתי), בדיקות חוזים מבניים, תצוגה מקדימה של diff על הדיסק — שום דבר לא נוחת על הדיסק אלא אם הוא מורכב |
| הקמת מערכת חדשה מאפס גוזלת זמן | צנרת "בנה מערכת במשפט אחד": צור DB → שלד → צור → ארז → רשום תפריטים, אוטומטי לחלוטין |

---

## 4. סקירת יכולות

- **קלט טבלאות**: ניתוח DDL, ייבוא ישיר ממסד נתונים, אשף קלט אצווה, תצורת עמודה הנגזרת אוטומטית (בקרות / אימות / מילונים / יחסים).
- **צורות טבלאות**: טבלה פשוטה, טבלת עץ (tree API + רשת עץ), master-detail (שמירה באותו טרנזקציה + לשוניות פרטים), טבלאות חיבור רבים לרבים.
- **ארטיפקטים שנוצרו**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + דפי Vue + דפי הדפסה A4 + שלדי בדיקות אינטגרציה.
- **שיפורים עסקיים**: תפריטים נפתחים של יחסים, תרגום דרך מילון, מיסוך נתונים, בדיקות ייחודיות, חיבור טופס, הרשאות נתונים, ייבוא/ייצוא Excel, זרימת עבודה לאישור, חבילות יכולות של תשלום / קובץ / נייד.
- **שערי איכות**: בדיקה עצמית עם הרכבה בזיכרון (`javac` אמיתי), בדיקות חוזים מבניים, בדיקות עקביות הרשאות, תצוגה מקדימה של diff על הדיסק, אימות ממשי של ארטיפקטים של חזית.
- **נחיתה בטוחה**: כתיבה לדיסק + גיבויים אצוותיים `.gen-backup`, מיזוג בלוקי סמן לשמירת קוד שנכתב ביד, ספר חשבונות בעל ארבעה מצבים `gen_output` (נוצר / מוזג / הודלג / נכשל), ביטול היסטורי.
- **בידוד רב מערכות**: כל פרויקט יעד הוא "תצורת מערכת" (נתיבי חזית/עורף, שמות חבילות, קידומת gateway, classpath תלויות, ערכי ברירת מחדל של מתגי יצירה).
- **שיפוי AI**: בנה מערכת במשפט אחד (מודלים של AI), שיפור AI של נתוני זרע, תצורה מומלצת על ידי AI ברמת הטבלה.

---

## 5. תרחישים טיפוסיים

| תרחיש | תיאור |
| --- | --- |
| טבלת עץ | נתונים היררכיים כגון קטגוריות מוצרים — מייצר אוטומטית tree API, רשת עץ ותפריט נפתח לבחירת עליון |
| CRUD של טבלה פשוטה | לקוחות, מוצרים וכו' — תפריטים נפתחים FK + תרגום דרך מילון + ייבוא/ייצוא מוכן לשימוש |
| Master-detail | הזמנות מכירה + שורות הזמנה — שמירה באותו טרנזקציה, מגירת פרטים עם שני לשוניות (מידע ראשי / פרטים) |
| קריאה בלבד / סטטיסטיקה | זרימת מלאי וכו' — מתגי כתיבה כבויים, רק שאילתה וייצוא |
| מסמכי הדפסה | סמן את מתג "הדפסה" לייצר דף הדפסה A4 ומסלול, עם כותרת + טבלת פרטים |
| בניית מערכת במשפט אחד | תאר את תחום העסק — AI יוצר DB וטבלאות + שלד + יצירה מלאה + רישום תפריטים בצעד אחד |

---

## 6. זרימת עבודה בסיסית

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ קלט טבלאות │ → │ תצורת שדות/מתגים │ → │ יצירה בלחיצה אחת (בדיקה עצמית→גיבוי→כתיבה→     │ → │ ספר חשבונות · ביטול │
│             │   │                   │   │ הרכבה→תפריט→מסלול)                             │   │                    │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  ייבוא DB/DDL     בקרות/אימות          נוחת על הדיסק רק אחרי כל שערי האיכות;
                   /מילונים            קוד שנכתב ביד נשמר
```

---

## 7. בנה מערכת במשפט אחד (צנרת)

| שלב | משמעות |
| --- | --- |
| `CHECK` | מאמת את תצורת פרויקט היעד ו-classpath התלויות |
| `WRITE` | יוצר מסד נתונים וטבלאות, כותב שלד וארטיפקטים שנוצרו |
| `COMPILE` | עורף `mvn package` / בניית חזית, בדיקה עצמית עם הרכבה בזיכרון |
| `MENU` | מייצר ורושם הרשאות תפריט (שני יציאות: SQL של ארטיפקט / כתיבה ישירה פנימית) |
| `ROUTE` | בונה מחדש fragmentים של מסלולי חזית |

---

## 8. ארכיטקטורת מערכת

| מאגר | תפקיד | מחסנית |
| --- | --- | --- |
| `codegen-server` | עורף: מנוע יצירה, תבניות, אימות, ספר חשבונות, צנרת | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | חזית: קונסולה (תצורת טבלה / תבניות / ספר חשבונות / ניהול מערכת) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (היסטוריה) |

## 9. מחסנית טכנולוגית

| מימד | בחירה |
| --- | --- |
| שפת / מסגרת עורף | Java 21 / Spring Boot 3.2.5 |
| עמידות | MyBatis-Plus 3.5.5 |
| מנוע תבניות | FreeMarker (22 תבניות זרע, עריכה מקוונת + חזרת גרסאות) |
| אבטחה | אימות JWT |
| מסד נתונים | MySQL 8 |
| חזית (generated projects) | Vue 3 + Element Plus + Vite |
| חזית (platform console) | Vue 2.7 + Element UI |
| AI מקומי | qwen2.5-coder:7b |
| תיעוד API | springdoc-openapi (Swagger UI) |

## 10. התחלה מהירה

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

העורף ברירת מחדל לפורט **8120**, החזית לפורט **9530**. חשבון ברירת מחדל `admin / admin123`.

## 11. סטטוס פרויקט

- קו הגרסה המתוכנן הנוכחי הגיע ל-**V9.3**.
- **V9.3** · בדיקות אוטומטיות של תוצרים שנוצרו · Generated-artifact automated testing: S10 (mvn test + Surefire), S11 (API / TestContainers), S12 (E2E / Playwright); GEN trigger + TC_GEN_ROOT; 12-step pipeline green; real artifact run PASSED 20/20, run/case/artifact persisted
- **V9.2** · מרכז בדיקות ומרכז אימות · Test Center (7 tables: env/suite/case/dataset/run/result/schedule) + Verification Center (tc_run/tc_case_result/tc_artifact, 9 suites S1-S9, single-run 409 lock, `codegen.testcenter/v1`); 373/373 tests; 206/206 locales; output byte-identical
- **V9.1** · רפקטור לתחזוקתיות · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9
- שני המאגרים מתארחים ב-Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

## 12. מחבר

- **מחבר**: מתכנת סיני (מפתח עצמאי)
- **דוא"ל**: `hustxiayuhong@126.com`
- **תמיכה טכנית**: אנא כתוב את הדוא"ל שלך **בסינית**, כולל תיאור הבעיה, סביבת הריצה ושלבי השכפול.

---

## 13. השוואה עם כלים דומים

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

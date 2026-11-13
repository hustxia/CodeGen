// 一次性维护脚本：把版本历史 V7.5/V8.0/V8.1/V9.0 同步进 47 个语言 README
// 用法: node sync-version-rows.mjs   (在 codegen-server 根目录执行)
import fs from 'fs'

const R75 = '7a9afa0 + abac56d'
const R80 = '9e5dd57 → 7d57aea'
const R81 = '93a12d5 + e49330b'
const R90 = '93a12d5 + e49330b'
const R74 = 'c306e29'

// 每语言: c=当前版本短语(从原 V7.4 行提取, 这里是兜底), v75/v80/v81/v90 = 各版本描述
const T = {
  README_ZH: {
    c: '当前版本',
    v75: '项目语言配置：206 语言清单 / 默认语言 / 启用语言，生成物 lang 与 dir 注入',
    v80: '全页面国际化：206 语言框架（8 种精译），双轨制 i18n、OFF 逐字节零 diff 契约',
    v81: '质量加固：离线契约 harness（9 模式 OFF 零 diff）+ i18n-core 单测 + CI 卡点 + README 诚实拆分（框架 206 / 精译 8）+ check-locale-keys.mjs',
    v90: '生成物现代化：Vue 3 + Element Plus + Vite；运行时验证（40/40 断言、vite build 成功、14 张截图含 ar-SA RTL）；修复 3 个运行时缺陷',
  },
  README_ZH_TW: {
    c: '目前版本',
    v75: '專案語言設定：206 語言清單 / 預設語言 / 啟用語言，生成物 lang 與 dir 注入',
    v80: '全頁面國際化：206 語言框架（8 種精譯），雙軌制 i18n、OFF 逐位元組零 diff 契約',
    v81: '品質加固：離線契約 harness（9 模式 OFF 零 diff）+ i18n-core 單元測試 + CI 關卡 + README 誠實拆分（框架 206 / 精譯 8）+ check-locale-keys.mjs',
    v90: '生成物現代化：Vue 3 + Element Plus + Vite；執行期驗證（40/40 斷言、vite build 成功、14 張截圖含 ar-SA RTL）；修復 3 個執行期缺陷',
  },
  README_JA: {
    c: '現行バージョン',
    v75: 'プロジェクト言語設定：206 言語リスト / デフォルト / 有効言語、生成物への lang・dir 注入',
    v80: '全ページ i18n：206 言語フレームワーク（8 言語は精訳）、OFF ゼロ diff 契約のデュアルトラック i18n',
    v81: '品質強化：オフライン契約ハーネス（9 モード OFF ゼロ diff）+ i18n-core 単体テスト + CI ゲート + README の正直な分割（フレームワーク 206 / 精訳 8）+ check-locale-keys.mjs',
    v90: '生成物を Vue 3 + Element Plus + Vite に近代化；ランタイム検証済み（40/40 アサーション、vite build 成功、ar-SA RTL 含む 14 スクリーンショット）；ランタイムで発見した 3 件の欠陥を修正',
  },
  README_KO: {
    c: '현재 버전',
    v75: '프로젝트 언어 설정: 206개 언어 목록 / 기본 언어 / 사용 언어, 생성물에 lang·dir 주입',
    v80: '전 페이지 i18n: 206개 언어 프레임워크(8개 정밀 번역), OFF 제로 diff 계약의 듀얼 트랙 i18n',
    v81: '품질 강화: 오프라인 계약 하네스(9 모드 OFF 제로 diff) + i18n-core 단위 테스트 + CI 게이트 + README 정직 분할(프레임워크 206 / 정밀 8) + check-locale-keys.mjs',
    v90: '생성물을 Vue 3 + Element Plus + Vite로 현대화; 런타임 검증 완료(40/40 어설션, vite build 성공, ar-SA RTL 포함 14장 스크린샷); 런타임 결함 3건 수정',
  },
  README_DE: {
    c: 'Aktuelle Version',
    v75: 'Projekt-Sprachkonfiguration: 206-Sprachenliste / Standardsprache / aktivierte Sprachen, Injektion von lang & dir in die generierten Artefakte',
    v80: 'Vollseiten-i18n: 206-Sprachen-Framework (8 professionell übersetzt), Zwei-Track-i18n mit OFF Zero-Diff-Vertrag',
    v81: 'Qualitätshärtung: Offline-Contract-Harness (9 Modi, OFF Zero-Diff) + i18n-Core-Unit-Tests + CI-Gate + ehrliche README-Aufteilung (Framework 206 / übersetzt 8) + check-locale-keys.mjs',
    v90: 'Generierte Artefakte auf Vue 3 + Element Plus + Vite modernisiert; zur Laufzeit verifiziert (40/40 Assertions, erfolgreicher vite build, 14 Screenshots inkl. ar-SA RTL); 3 Laufzeitfehler gefunden & behoben',
  },
  README_FR: {
    c: 'Version actuelle',
    v75: "Configuration linguistique du projet : liste de 206 langues / langue par défaut / langues activées, injection de lang & dir dans les artefacts générés",
    v80: 'i18n pleine page : framework 206 langues (8 traduites professionnellement), i18n à double voie avec contrat zéro diff en mode OFF',
    v81: 'Durcissement qualité : harnais de contrat hors ligne (9 modes, zéro diff OFF) + tests unitaires i18n-core + porte CI + lecture honnête du README (framework 206 / traduites 8) + check-locale-keys.mjs',
    v90: "Artefacts générés modernisés vers Vue 3 + Element Plus + Vite ; vérifiés à l'exécution (40/40 assertions, vite build réussi, 14 captures dont ar-SA RTL) ; 3 défauts d'exécution corrigés",
  },
  README_ES: {
    c: 'Versión actual',
    v75: 'Configuración de idiomas del proyecto: lista de 206 idiomas / idioma predeterminado / idiomas habilitados, inyección de lang y dir en los generados',
    v80: 'i18n de página completa: marco de 206 idiomas (8 traducidos profesionalmente), i18n de doble vía con contrato de dif cero en OFF',
    v81: 'Reforzamiento de calidad: arnés de contrato fuera de línea (9 modos, dif cero en OFF) + pruebas unitarias i18n-core + compuerta CI + división honesta del README (marco 206 / traducidos 8) + check-locale-keys.mjs',
    v90: 'Artefactos generados modernizados a Vue 3 + Element Plus + Vite; verificados en tiempo de ejecución (40/40 aserciones, vite build exitoso, 14 capturas incl. ar-SA RTL); 3 defectos de ejecución corregidos',
  },
  README_RU: {
    c: 'текущая версия',
    v75: 'Языковая конфигурация проекта: список 206 языков / язык по умолчанию / включённые языки, вставка lang и dir в генерируемые файлы',
    v80: 'Полностраничная i18n: каркас на 206 языков (8 переведены профессионально), двухконтурная i18n с контрактом нулевой разницы при OFF',
    v81: 'Укрепление качества: офлайн-стенд контракта (9 режимов, нулевая разница OFF) + юнит-тесты i18n-core + CI-шлюз + честное разделение в README (каркас 206 / переведено 8) + check-locale-keys.mjs',
    v90: 'Генерируемые артефакты переведены на Vue 3 + Element Plus + Vite; проверено в рантайме (40/40 проверок, успешный vite build, 14 скриншотов вкл. ar-SA RTL); исправлены 3 рантайм-дефекта',
  },
  README_PT: {
    c: 'Versão atual',
    v75: 'Configuração de idiomas do projeto: lista de 206 idiomas / idioma padrão / idiomas habilitados, injeção de lang e dir nos gerados',
    v80: 'i18n de página inteira: framework de 206 idiomas (8 traduzidos profissionalmente), i18n de via dupla com contrato de diff zero em OFF',
    v81: 'Endurecimento de qualidade: arnês de contrato offline (9 modos, diff zero OFF) + testes unitários i18n-core + portão de CI + divisão honesta do README (framework 206 / traduzidos 8) + check-locale-keys.mjs',
    v90: 'Artefatos gerados modernizados para Vue 3 + Element Plus + Vite; verificados em execução (40/40 asserções, vite build com sucesso, 14 capturas incl. ar-SA RTL); 3 defeitos de execução corrigidos',
  },
  README_IT: {
    c: 'Versione attuale',
    v75: 'Configurazione lingua del progetto: elenco di 206 lingue / lingua predefinita / lingue abilitate, iniezione di lang e dir nei generati',
    v80: 'i18n a pagina intera: framework di 206 lingue (8 tradotte professionalmente), i18n a doppia via con contratto zero-diff in OFF',
    v81: 'Rafforzamento qualità: harness di contratto offline (9 modalità, zero-diff OFF) + test unitari i18n-core + gate CI + suddivisione onesta del README (framework 206 / tradotte 8) + check-locale-keys.mjs',
    v90: 'Artefatti generati modernizzati a Vue 3 + Element Plus + Vite; verificati a runtime (40/40 asserzioni, vite build riuscita, 14 screenshot incl. ar-SA RTL); 3 difetti runtime corretti',
  },
  README_NL: {
    c: 'huidige versie',
    v75: 'Projecttaalconfiguratie: lijst van 206 talen / standaardtaal / ingeschakelde talen, injectie van lang & dir in gegenereerde code',
    v80: 'Volledige pagina-i18n: framework van 206 talen (8 professioneel vertaald), tweespoors i18n met OFF zero-diff contract',
    v81: 'Kwaliteitsverharding: offline contract-harness (9 modi, OFF zero-diff) + i18n-core unittests + CI-poort + eerlijke README-splitsing (framework 206 / vertaald 8) + check-locale-keys.mjs',
    v90: 'Gegenereerde artefacten gemoderniseerd naar Vue 3 + Element Plus + Vite; runtime geverifieerd (40/40 asserts, geslaagde vite build, 14 screenshots incl. ar-SA RTL); 3 runtimefouten gevonden & opgelost',
  },
  README_PL: {
    c: 'aktualna wersja',
    v75: 'Konfiguracja językowa projektu: lista 206 języków / język domyślny / włączone języki, wstrzykiwanie lang i dir do generowanych plików',
    v80: 'i18n całej strony: framework 206 języków (8 przetłumaczonych profesjonalnie), dwutorowa i18n z kontraktem zerowej różnicy przy OFF',
    v81: 'Hardening jakości: offline harness kontraktu (9 trybów, zerowa różnica OFF) + testy jednostkowe i18n-core + bramka CI + uczciwy podział README (framework 206 / przetłumaczone 8) + check-locale-keys.mjs',
    v90: 'Generowane artefakty zmodernizowane do Vue 3 + Element Plus + Vite; zweryfikowane w czasie działania (40/40 asercji, udany vite build, 14 zrzutów ekranu incl. ar-SA RTL); naprawiono 3 defekty runtime',
  },
  README_SV: {
    c: 'aktuell version',
    v75: 'Projektspråkskonfiguration: lista över 206 språk / standardspråk / aktiverade språk, injicering av lang och dir i genererad kod',
    v80: 'Helsides-i18n: ramverk med 206 språk (8 professionellt översatta), tvåspårig i18n med OFF zero-diff-kontrakt',
    v81: 'Kvalitetshärdning: offline kontrakts-harness (9 lägen, OFF zero-diff) + i18n-core enhetstester + CI-grind + ärlig README-fördelning (ramverk 206 / översatta 8) + check-locale-keys.mjs',
    v90: 'Genererade artefakter moderniserade till Vue 3 + Element Plus + Vite; verifierade i drift (40/40 asserteringar, lyckad vite build, 14 skärmbilder inkl. ar-SA RTL); 3 runtime-fel åtgärdade',
  },
  README_DA: {
    c: 'nuværende version',
    v75: 'Projekt-sprogkonfiguration: liste over 206 sprog / standardsprog / aktiverede sprog, injektion af lang & dir i genereret kode',
    v80: 'Fuldside-i18n: framework med 206 sprog (8 professionelt oversat), tosporet i18n med OFF zero-diff-kontrakt',
    v81: 'Kvalitetshærdning: offline kontrakt-harness (9 tilstande, OFF zero-diff) + i18n-core enhedstests + CI-port + ærlig README-opdeling (framework 206 / oversat 8) + check-locale-keys.mjs',
    v90: 'Genererede artefakter moderniseret til Vue 3 + Element Plus + Vite; runtime-verificeret (40/40 assertions, vellykket vite build, 14 skærmbilleder inkl. ar-SA RTL); 3 runtime-fejl rettet',
  },
  README_NO: {
    c: 'gjeldende versjon',
    v75: 'Prosjektspråkkonfigurasjon: liste over 206 språk / standardspråk / aktiverte språk, injisering av lang & dir i generert kode',
    v80: 'Fullside-i18n: rammeverk med 206 språk (8 profesjonelt oversatt), tosporet i18n med OFF zero-diff-kontrakt',
    v81: 'Kvalitetsherdning: offline kontrakt-harness (9 moduser, OFF zero-diff) + i18n-core enhetstester + CI-port + ærlig README-inndeling (rammeverk 206 / oversatt 8) + check-locale-keys.mjs',
    v90: 'Genererte artefakter modernisert til Vue 3 + Element Plus + Vite; runtime-verifisert (40/40 asserts, vellykket vite build, 14 skjermbilder inkl. ar-SA RTL); 3 runtime-feil rettet',
  },
  README_FI: {
    c: 'nykyinen versio',
    v75: 'Projektin kieliasetukset: 206 kielen luettelo / oletuskieli / käytössä olevat kielet, lang- ja dir-kenttien lisäys generoituihin tiedostoihin',
    v80: 'Koko sivun i18n: 206 kielen kehys (8 ammattimaisesti käännetty), kaksiraiteinen i18n OFF zero-diff-sopimuksella',
    v81: 'Laadun vahvistus: offline-sopimustesti (9 tilaa, OFF zero-diff) + i18n-core-yksikkötestit + CI-portti + rehellinen README-jako (kehys 206 / käännetty 8) + check-locale-keys.mjs',
    v90: 'Generoidut artefaktit päivitetty Vue 3 + Element Plus + Viteen; ajonaikaisesti todennettu (40/40 assertiota, onnistunut vite build, 14 kuvakaappausta incl. ar-SA RTL); 3 ajonaikaista virhettä korjattu',
  },
  README_CS: {
    c: 'aktuální verze',
    v75: 'Jazyková konfigurace projektu: seznam 206 jazyků / výchozí jazyk / povolené jazyky, vkládání lang a dir do generovaného kódu',
    v80: 'i18n celé stránky: rámec 206 jazyků (8 profesionálně přeloženo), dvoustopá i18n s kontraktem nulového rozdílu při OFF',
    v81: 'Posílení kvality: offline kontraktní harness (9 režimů, nulový rozdíl OFF) + unit testy i18n-core + CI brána + čestné rozdělení README (rámec 206 / přeloženo 8) + check-locale-keys.mjs',
    v90: 'Generované artefakty modernizovány na Vue 3 + Element Plus + Vite; ověřeno za běhu (40/40 assertion, úspěšný vite build, 14 snímků vč. ar-SA RTL); opraveny 3 běhové defekty',
  },
  README_SK: {
    c: 'aktuálna verzia',
    v75: 'Jazyková konfigurácia projektu: zoznam 206 jazykov / predvolený jazyk / povolené jazyky, vkladanie lang a dir do generovaného kódu',
    v80: 'i18n celej stránky: rámec 206 jazykov (8 profesionálne preložených), dvojstopňová i18n s kontraktom nulového rozdielu pri OFF',
    v81: 'Posilnenie kvality: offline kontraktný harness (9 režimov, nulový rozdiel OFF) + unit testy i18n-core + CI brána + úprimné rozdelenie README (rámec 206 / preložené 8) + check-locale-keys.mjs',
    v90: 'Generované artefakty modernizované na Vue 3 + Element Plus + Vite; overené za behu (40/40 assertion, úspešný vite build, 14 snímok vrátane ar-SA RTL); opravené 3 behové defekty',
  },
  README_HR: {
    c: 'trenutna verzija',
    v75: 'Jezična konfiguracija projekta: popis 206 jezika / zadani jezik / omogućeni jezici, ubacivanje lang i dir u generirani kod',
    v80: 'i18n cijele stranice: okvir 206 jezika (8 stručno prevedenih), dvokolosiječna i18n s ugovorom nulte razlike u OFF',
    v81: 'Očvršćivanje kvalitete: offline ugovorni harness (9 načina, nulta razlika OFF) + jedinični testovi i18n-core + CI kapija + poštena podjela README (okvir 206 / prevedenih 8) + check-locale-keys.mjs',
    v90: 'Generirani artefakti modernizirani na Vue 3 + Element Plus + Vite; provjereno u izvođenju (40/40 tvrdnji, uspješan vite build, 14 snimki zaslona incl. ar-SA RTL); popravljene 3 izvedbene greške',
  },
  README_RO: {
    c: 'versiunea curentă',
    v75: 'Configurarea lingvistică a proiectului: lista de 206 limbi / limba implicită / limbile activate, injectarea lang și dir în codul generat',
    v80: 'i18n pe pagina întreagă: cadru cu 206 limbi (8 traduse profesional), i18n pe două căi cu contract de diferență zero în OFF',
    v81: 'Întărirea calității: harness offline de contract (9 moduri, diferență zero OFF) + teste unitare i18n-core + poartă CI + împărțire onestă în README (cadru 206 / traduse 8) + check-locale-keys.mjs',
    v90: 'Artefactele generate modernizate la Vue 3 + Element Plus + Vite; verificate la runtime (40/40 aserțiuni, vite build reușit, 14 capturi incl. ar-SA RTL); 3 defecte de runtime reparate',
  },
  README_BG: {
    c: 'текуща версия',
    v75: 'Езикова конфигурация на проекта: списък от 206 езика / език по подразбиране / включени езици, вмъкване на lang и dir в генерирания код',
    v80: 'i18n на цялата страница: рамка от 206 езика (8 професионално преведени), двупътна i18n с договор за нулева разлика при OFF',
    v81: 'Втвърдяване на качеството: офлайн стенд за контракт (9 режима, нулева разлика OFF) + unit тестове i18n-core + CI порта + честно разделяне в README (рамка 206 / преведени 8) + check-locale-keys.mjs',
    v90: 'Генерираните артефакти модернизирани към Vue 3 + Element Plus + Vite; проверено по време на изпълнение (40/40 проверки, успешен vite build, 14 екранни снимки incl. ar-SA RTL); коригирани 3 runtime дефекта',
  },
  README_UK: {
    c: 'поточна версія',
    v75: 'Мовна конфігурація проєкту: список 206 мов / типова мова / увімкнені мови, вставлення lang і dir у згенерований код',
    v80: 'Повносторінкова i18n: каркас на 206 мов (8 професійно перекладено), двоконтурна i18n з контрактом нульової різниці в режимі OFF',
    v81: 'Зміцнення якості: офлайн-стенд контракту (9 режимів, нульова різниця OFF) + юніт-тести i18n-core + CI-шлюз + чесний розподіл у README (каркас 206 / перекладено 8) + check-locale-keys.mjs',
    v90: 'Згенеровані артефакти переведено на Vue 3 + Element Plus + Vite; перевірено в рантаймі (40/40 перевірок, успішний vite build, 14 скріншотів incl. ar-SA RTL); виправлено 3 рантайм-дефекти',
  },
  README_TR: {
    c: 'mevcut sürüm',
    v75: 'Proje dil yapılandırması: 206 dillik liste / varsayılan dil / etkin diller, üretilen koda lang ve dir enjeksiyonu',
    v80: "Tam sayfa i18n: 206 dilli çerçeve (8'i profesyonelce çevrildi), OFF sıfır fark sözleşmeli çift hatlı i18n",
    v81: 'Kalite sertleştirme: çevrimdışı sözleşme düzeneği (9 mod, OFF sıfır fark) + i18n-core birim testleri + CI kapısı + dürüst README ayrımı (çerçeve 206 / çevrili 8) + check-locale-keys.mjs',
    v90: "Üretilen artefaktlar Vue 3 + Element Plus + Vite'a modernize edildi; çalışma zamanında doğrulandı (40/40 iddia, başarılı vite build, ar-SA RTL dahil 14 ekran görüntüsü); 3 çalışma zamanı hatası giderildi",
  },
  README_EL: {
    c: 'τρέχουσα έκδοση',
    v75: 'Γλωσσική διαμόρφωση έργου: λίστα 206 γλωσσών / προεπιλεγμένη γλώσσα / ενεργοποιημένες γλώσσες, έγχυση lang και dir στον παραγόμενο κώδικα',
    v80: 'i18n πλήρους σελίδας: πλαίσιο 206 γλωσσών (8 επαγγελματικά μεταφρασμένες), i18n δύο τροχιών με σύμβαση μηδενικής διαφοράς σε OFF',
    v81: 'Ενίσχυση ποιότητας: offline harness συμβολαίου (9 λειτουργίες, μηδενική διαφορά OFF) + unit tests i18n-core + πύλη CI + ειλικρινής διαίρεση README (πλαίσιο 206 / μεταφρασμένες 8) + check-locale-keys.mjs',
    v90: 'Τα παραγόμενα τεχνουργήματα εκσυγχρονίστηκαν σε Vue 3 + Element Plus + Vite; επαληθευμένα κατά την εκτέλεση (40/40 ισχυρισμοί, επιτυχές vite build, 14 στιγμιότυπα incl. ar-SA RTL); διορθώθηκαν 3 σφάλματα εκτέλεσης',
  },
  README_HE: {
    c: 'הגרסה הנוכחית',
    v75: 'תצורת שפת הפרויקט: רשימת 206 שפות / שפת ברירת מחדל / שפות מופעלות, הזרקת lang ו-dir לקוד שנוצר',
    v80: 'i18n לעמוד מלא: מסגרת של 206 שפות (8 מתורגמות מקצועית), i18n דו-מסלולית עם חוזה אפס הפרש ב-OFF',
    v81: 'חיזוק איכות: מנגנון חוזה אופליין (9 מצבים, אפס הפרש ב-OFF) + בדיקות יחידה i18n-core + שער CI + פיצול כן ב-README (מסגרת 206 / מתורגמות 8) + check-locale-keys.mjs',
    v90: 'ה-artifacts שנוצרו שודרגו ל-Vue 3 + Element Plus + Vite; אומתו בזמן ריצה (40/40 טענות, vite build מוצלח, 14 צילומי מסך incl. ar-SA RTL); תוקנו 3 באגים בזמן ריצה',
  },
  README_FA: {
    c: 'نسخه فعلی',
    v75: 'پیکربندی زبان پروژه: فهرست ۲۰۶ زبان / زبان پیش‌فرض / زبان‌های فعال، تزریق lang و dir در خروجی تولیدی',
    v80: 'i18n تمام‌صفحه: چارچوب ۲۰۶ زبانه (۸ زبان ترجمه حرفه‌ای)، i18n دورخط با قرارداد صفر دیف در OFF',
    v81: 'سخت‌سازی کیفیت: هارنس قرارداد آفلاین (۹ حالت، صفر دیف OFF) + تست واحد i18n-core + گیت CI + تفکیک صادقانه README (چارچوب ۲۰۶ / ترجمه‌شده ۸) + check-locale-keys.mjs',
    v90: 'خروجی‌های تولیدی به Vue 3 + Element Plus + Vite مدرن شدند؛ در زمان اجرا راستی‌آزمایی شد (۴۰/۴۰ ادعا، vite build موفق، ۱۴ اسکرین‌شات شامل ar-SA RTL)؛ ۳ عیب زمان اجرا برطرف شد',
  },
  README_UR: {
    c: 'موجودہ ورژن',
    v75: 'پروجیکٹ کی زبان کنفیگریشن: ۲۰۶ زبانوں کی فہرست / ڈیفالٹ زبان / فعال زبانیں، تیار شدہ کوڈ میں lang اور dir کا داخلہ',
    v80: 'مکمل صفحہ i18n: ۲۰۶ زبانوں کا فریم ورک (۸ پیشہ ورانہ ترجمہ شدہ)، OFF صفر فرق معاہدے کے ساتھ دوہرا i18n',
    v81: 'معیار کی مضبوطی: آف لائن معاہدہ ہارنس (۹ موڈ، OFF صفر فرق) + i18n-core یونٹ ٹیسٹ + CI گیٹ + ایماندار README تقسیم (فریم ورک ۲۰۶ / ترجمہ شدہ ۸) + check-locale-keys.mjs',
    v90: 'تیار شدہ artifacts کو Vue 3 + Element Plus + Vite پر جدید بنایا گیا؛ رن ٹائم پر تصدیق شدہ (۴۰/۴۰، کامیاب vite build، ۱۴ اسکرین شاٹس بشمول ar-SA RTL)؛ ۳ رن ٹائم خرابیاں درست کی گئیں',
  },
  README_UR_ROMA: {
    c: 'maujooda version',
    v75: 'Project ki zubaan configuration: 206 zabanoon ki list / default zubaan / enabled zabanein, generated code mein lang aur dir injection',
    v80: 'Full-page i18n: 206 zabanoon ka framework (8 professional tarjuma shuda), OFF zero-diff contract ke saath dual-track i18n',
    v81: 'Quality hardening: offline contract harness (9 modes, OFF zero-diff) + i18n-core unit tests + CI gate + imandaar README taqseem (framework 206 / tarjuma 8) + check-locale-keys.mjs',
    v90: 'Generated artifacts ko Vue 3 + Element Plus + Vite par modernize kiya; runtime par verify shuda (40/40 assertions, kamyab vite build, 14 screenshots incl. ar-SA RTL); 3 runtime defects theek kiye',
  },
  README_HI: {
    c: 'वर्तमान संस्करण',
    v75: 'प्रोजेक्ट भाषा कॉन्फ़िगरेशन: 206 भाषाओं की सूची / डिफ़ॉल्ट भाषा / सक्षम भाषाएँ, जनरेट कोड में lang और dir इंजेक्शन',
    v80: 'पूर्ण पेज i18n: 206 भाषाओं का फ्रेमवर्क (8 पेशेवर रूप से अनूदित), OFF शून्य-डिफ़ संधि वाली दोहरी-ट्रैक i18n',
    v81: 'गुणवत्ता सुदृढ़ीकरण: ऑफ़लाइन अनुबंध हार्नेस (9 मोड, OFF शून्य-डिफ़) + i18n-core यूनिट टेस्ट + CI गेट + ईमानदार README विभाजन (फ्रेमवर्क 206 / अनूदित 8) + check-locale-keys.mjs',
    v90: 'जनरेट किए गए आर्टिफैक्ट्स Vue 3 + Element Plus + Vite पर आधुनिक किए गए; रनटाइम पर सत्यापित (40/40 अभिकथन, सफल vite build, ar-SA RTL सहित 14 स्क्रीनशॉट); 3 रनटाइम दोष ठीक किए गए',
  },
  README_BN: {
    c: 'বর্তমান সংস্করণ',
    v75: 'প্রজেক্ট ভাষা কনফিগারেশন: ২০৬টি ভাষার তালিকা / ডিফল্ট ভাষা / সক্রিয় ভাষা, জেনারেট করা কোডে lang ও dir ইনজেকশন',
    v80: 'সম্পূর্ণ পেজ i18n: ২০৬ ভাষার ফ্রেমওয়ার্ক (৮টি পেশাদারভাবে অনূদিত), OFF শূন্য-ডিফ চুক্তিসহ দ্বৈত-ট্র্যাক i18n',
    v81: 'মানোন্নয়ন: অফলাইন চুক্তি হার্নেস (৯ মোড, OFF শূন্য-ডিফ) + i18n-core ইউনিট টেস্ট + CI গেট + সৎ README বিভাজন (ফ্রেমওয়ার্ক ২০৬ / অনূদিত ৮) + check-locale-keys.mjs',
    v90: 'জেনারেট করা আর্টিফ্যাক্ট Vue 3 + Element Plus + Vite-এ আধুনিকীকৃত; রানটাইমে যাচাইকৃত (৪০/৪০ অ্যাসারশন, সফল vite build, ar-SA RTL সহ ১৪টি স্ক্রিনশট); ৩টি রানটাইম ত্রুটি সংশোধিত',
  },
  README_TA: {
    c: 'தற்போதைய பதிப்பு',
    v75: 'திட்ட மொழி அமைப்பு: 206 மொழிகள் பட்டியல் / இயல்புநிலை மொழி / இயக்கப்பட்ட மொழிகள், உருவாக்கப்பட்ட குறியீட்டில் lang & dir செலுத்துதல்',
    v80: 'முழு பக்க i18n: 206 மொழி கட்டமைப்பு (8 தொழில்முறை மொழிபெயர்ப்பு), OFF பூஜ்ஜிய-வேறுபாடு ஒப்பந்தத்துடன் இருவழி i18n',
    v81: 'தர வலுவூட்டல்: ஆஃப்லைன் ஒப்பந்த ஹார்னஸ் (9 முறைகள், OFF பூஜ்ஜிய-வேறுபாடு) + i18n-core யூனிட் சோதனைகள் + CI கேட் + நேர்மையான README பிரிவு (கட்டமைப்பு 206 / மொழிபெயர்ப்பு 8) + check-locale-keys.mjs',
    v90: 'உருவாக்கப்பட்ட ஆர்டிஃபேக்ட்கள் Vue 3 + Element Plus + Vite ஆக நவீனமாக்கப்பட்டன; இயக்க நேரத்தில் சரிபார்க்கப்பட்டது (40/40 உறுதிப்பாடுகள், வெற்றிகரமான vite build, ar-SA RTL உட்பட 14 ஸ்கிரீன்ஷாட்கள்); 3 இயக்க குறைபாடுகள் சரிசெய்யப்பட்டன',
  },
  README_TE: {
    c: 'ప్రస్తుత వెర్షన్',
    v75: 'ప్రాజెక్ట్ భాష కాన్ఫిగరేషన్: 206 భాషల జాబితా / డిఫాల్ట్ భాష / ప్రారంభించిన భాషలు, జనరేట్ చేసిన కోడ్‌లో lang & dir ఇన్జెక్షన్',
    v80: 'పూర్తి పేజీ i18n: 206 భాషల ఫ్రేమ్‌వర్క్ (8 వృత్తిపరంగా అనువదించబడింది), OFF జీరో-డిఫ్ ఒప్పందంతో ద్విమార్గ i18n',
    v81: 'నాణ్యత సుధారణ: ఆఫ్‌లైన్ ఒప్పంద హార్నెస్ (9 మోడ్‌లు, OFF జీరో-డిఫ్) + i18n-core యూనిట్ టెస్ట్‌లు + CI గేట్ + నిజాయితీ README విభజన (ఫ్రేమ్‌వర్క్ 206 / అనువదించిన 8) + check-locale-keys.mjs',
    v90: 'జనరేట్ చేసిన ఆర్టిఫ్యాక్ట్‌లు Vue 3 + Element Plus + Vite కు ఆధునీకరించబడ్డాయి; రన్‌టైమ్‌లో ధృవీకరించబడింది (40/40 అసర్షన్లు, విజయవంతమైన vite build, ar-SA RTL తో సహా 14 స్క్రీన్‌షాట్‌లు); 3 రన్‌టైమ్ లోపాలు సరిచేయబడ్డాయి',
  },
  README_ML: {
    c: 'നിലവിലെ പതിപ്പ്',
    v75: 'പ്രോജക്റ്റ് ഭാഷ കോൺഫിഗറേഷൻ: 206 ഭാഷകളുടെ പട്ടിക / സ്ഥിര ഭാഷ / പ്രവർത്തനക്ഷമമാക്കിയ ഭാഷകൾ, ജനറേറ്റ് ചെയ്ത കോഡിൽ lang & dir ഇൻജക്ഷൻ',
    v80: 'പൂർണ്ണ പേജ് i18n: 206 ഭാഷാ ഫ്രെയിംവർക്ക് (8 പ്രൊഫഷണൽ വിവർത്തനം), OFF സീറോ-ഡിഫ് കരാറുള്ള ഇരട്ട-ട്രാക്ക് i18n',
    v81: 'ഗുണനിലവാര ശക്തിപ്പെടുത്തൽ: ഓഫ്‌ലൈൻ കരാർ ഹാർനസ് (9 മോഡുകൾ, OFF സീറോ-ഡിഫ്) + i18n-core യൂണിറ്റ് ടെസ്റ്റുകൾ + CI ഗേറ്റ് + സത്യസന്ധമായ README വിഭജനം (ഫ്രെയിംവർക്ക് 206 / വിവർത്തനം 8) + check-locale-keys.mjs',
    v90: 'ജനറേറ്റ് ചെയ്ത ആർട്ടിഫാക്ടുകൾ Vue 3 + Element Plus + Vite ലേക്ക് ആധുനികവൽക്കരിച്ചു; റൺടൈമിൽ സ്ഥിരീകരിച്ചു (40/40 അസെർഷനുകൾ, വിജയകരമായ vite build, ar-SA RTL ഉൾപ്പെടെ 14 സ്ക്രീൻഷോട്ടുകൾ); 3 റൺടൈം വൈകല്യങ്ങൾ ശരിയാക്കി',
  },
  README_ID: {
    c: 'versi saat ini',
    v75: 'Konfigurasi bahasa proyek: daftar 206 bahasa / bahasa default / bahasa yang diaktifkan, injeksi lang & dir ke hasil yang dibuat',
    v80: 'i18n halaman penuh: framework 206 bahasa (8 diterjemahkan profesional), i18n jalur ganda dengan kontrak zero-diff saat OFF',
    v81: 'Penguatan kualitas: harness kontrak offline (9 mode, zero-diff OFF) + unit test i18n-core + gate CI + pembagian README yang jujur (framework 206 / diterjemahkan 8) + check-locale-keys.mjs',
    v90: 'Artefak yang dihasilkan dimodernisasi ke Vue 3 + Element Plus + Vite; terverifikasi saat runtime (40/40 assertion, vite build sukses, 14 tangkapan layar incl. ar-SA RTL); 3 defect runtime diperbaiki',
  },
  README_TH: {
    c: 'เวอร์ชันปัจจุบัน',
    v75: 'การตั้งค่าภาษาของโปรเจกต์: รายการ 206 ภาษา / ภาษาเริ่มต้น / ภาษาที่เปิดใช้, ฉีด lang และ dir ลงในโค้ดที่สร้าง',
    v80: 'i18n ทั้งหน้า: เฟรมเวิร์ก 206 ภาษา (แปลมืออาชีพ 8 ภาษา), i18n สองแทร็กพร้อมสัญญา zero-diff เมื่อ OFF',
    v81: 'เสริมความแข็งแกร่งด้านคุณภาพ: offline contract harness (9 โหมด, OFF zero-diff) + unit test i18n-core + CI gate + แบ่ง README อย่างตรงไปตรงมา (เฟรมเวิร์ก 206 / แปลแล้ว 8) + check-locale-keys.mjs',
    v90: 'สิ่งที่สร้างได้รับการปรับให้ทันสมัยเป็น Vue 3 + Element Plus + Vite; ตรวจสอบขณะรันจริง (40/40 assertions, vite build สำเร็จ, ภาพหน้าจอ 14 รูป รวม ar-SA RTL); แก้ไขข้อบกพร่อง runtime 3 จุด',
  },
  README_VI: {
    c: 'phiên bản hiện tại',
    v75: 'Cấu hình ngôn ngữ dự án: danh sách 206 ngôn ngữ / ngôn ngữ mặc định / các ngôn ngữ bật, chèn lang & dir vào mã được tạo',
    v80: 'i18n toàn trang: khung 206 ngôn ngữ (8 bản dịch chuyên nghiệp), i18n hai luồng với hợp đồng zero-diff khi OFF',
    v81: 'Tăng cường chất lượng: harness hợp đồng offline (9 chế độ, zero-diff OFF) + unit test i18n-core + cổng CI + phân tách README trung thực (khung 206 / đã dịch 8) + check-locale-keys.mjs',
    v90: 'Artifact được tạo hiện đại hóa lên Vue 3 + Element Plus + Vite; đã xác minh lúc runtime (40/40 assertion, vite build thành công, 14 ảnh chụp incl. ar-SA RTL); sửa 3 lỗi runtime',
  },
  README_AR: {
    c: 'الإصدار الحالي',
    v75: 'إعداد لغة المشروع: قائمة 206 لغة / اللغة الافتراضية / اللغات المفعلة، حقن lang و dir في المولَّد',
    v80: 'تدويل كامل الصفحة: إطار 206 لغة (8 مترجمة احترافياً)، i18n ثنائي المسار مع عقد فرق صفري عند OFF',
    v81: 'تحصين الجودة: حزام عقد offline (9 أوضاع، فرق صفري OFF) + اختبارات وحدة i18n-core + بوابة CI + تقسيم صادق في README (إطار 206 / مترجمة 8) + check-locale-keys.mjs',
    v90: 'تحديث المخرجات المولدة إلى Vue 3 + Element Plus + Vite؛ تم التحقق وقت التشغيل (40/40 تأكيدات، vite build ناجح، 14 لقطة شاشة تشمل ar-SA RTL)؛ إصلاح 3 عيوب تشغيل',
  },
  README_ET: { c: 'praegune versioon', v75: '', v80: '', v81: '', v90: '' },
  README_HY: { c: '', v75: '', v80: '', v81: '', v90: '' },
  README_KA: { c: '', v75: '', v80: '', v81: '', v90: '' },
  README_KM: { c: '', v75: '', v80: '', v81: '', v90: '' },
  README_MY: { c: '', v75: '', v80: '', v81: '', v90: '' },
  README_SW: { c: '', v75: '', v80: '', v81: '', v90: '' },
  README_SL: { c: '', v75: '', v80: '', v81: '', v90: '' },
  README_TL: { c: '', v75: '', v80: '', v81: '', v90: '' },
}

// 11 个无版本表的老翻译文件：只把正文 bullet 的 V7.3 → V9.0
const BULLET_ONLY = ['README_ET.md', 'README_HE.md', 'README_HY.md', 'README_KA.md', 'README_KM.md', 'README_MY.md', 'README_SL.md', 'README_SW.md', 'README_TA.md', 'README_TE.md', 'README_TL.md']

let tableDone = 0, bulletDone = 0, skipped = []
for (const [key, tr] of Object.entries(T)) {
  const file = key + '.md'
  if (!fs.existsSync(file)) { skipped.push(file); continue }
  let content = fs.readFileSync(file, 'utf8')

  if (BULLET_ONLY.includes(file)) {
    // 正文 bullet：**V7.3** → **V9.0**（仅限以 - 开头的行，避免误伤表格）
    const before = content
    content = content.replace(/^(\s*-\s[^\n]*?)\*\*V7\.3\*\*([^\n]*)$/gm, '$1**V9.0**$2')
    if (content !== before) { fs.writeFileSync(file, content); bulletDone++ }
    else skipped.push(file + '(bullet 未命中)')
    continue
  }

  // 1) 定位 V7.4 行
  const lines = content.split('\n')
  const idx = lines.findIndex(l => /^\|.*V7\.4.*\|/.test(l))
  if (idx === -1) { skipped.push(file + '(无 V7.4 行)'); continue }

  // 2) 解析 V7.4 行，规范化三列，摘出「当前版本」短语
  const cells = lines[idx].split('|').map(s => s.trim())
  // cells: ['', '**V7.4**', desc, third, '']
  const desc = cells[2] || ''
  let currentPhrase = tr.c
  const thirdRaw = (cells[3] || '').replace(/\*/g, '').trim()
  if (thirdRaw) currentPhrase = thirdRaw
  lines[idx] = `| **V7.4** | ${desc} | ${R74} |`

  // 3) 追加 V7.5 ~ V9.0
  const newRows = [
    `| **V7.5** | ${tr.v75} | ${R75} |`,
    `| **V8.0** | ${tr.v80} | ${R80} |`,
    `| **V8.1** | ${tr.v81} | ${R81} |`,
    `| **V9.0** | ${tr.v90} | ${R90}, **${currentPhrase}** |`,
  ]
  lines.splice(idx + 1, 0, ...newRows)

  // 4) 正文 bullet 里的 V7.3 → V9.0（仅 bullet 行）
  let out = lines.join('\n')
  out = out.replace(/^(\s*-\s[^\n]*?)\*\*V7\.3\*\*([^\n]*)$/gm, '$1**V9.0**$2')

  fs.writeFileSync(file, out)
  tableDone++
}

console.log(`版本表更新: ${tableDone} 个文件; bullet 更新: ${bulletDone} 个; 跳过: ${skipped.length}`)
skipped.forEach(s => console.log('  SKIP', s))

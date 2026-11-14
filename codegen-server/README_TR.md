# Majiang · Kod Üretici (CodeGen) — Proje Tanıtımı

> Languages: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · **Türkçe** · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> **Spring Boot + Vue** için çok projeli bir kod üreticisi: veritabanı tablo yapılarından başlayarak, **derlenebilir, çalıştırılabilir, menü ve yetki hazır** ön yüz ve arka uç iş kodunu üretir; diske yazma, yedekleme, kayıt tutma ve geri alma işlemlerinden oluşan tüm teslimat döngüsünü yönetir.

GitHub: https://github.com/hustxia/CodeGen

## 1. Bu Nedir

CodeGen, "tablo güdümlü" bir kod fabrikasıdır. İş tablolarınızı getirin (veritabanı içe aktarımı veya DDL yapıştırma yoluyla), alanları ve üretim anahtarlarını yapılandırın, ardından "Üret"e tıklayın — arka uç Entity/Mapper/Service/Controller/DTO/VO, ön yüz `api.js` ve Vue sayfaları, menüler ve rotalar, hatta A4 yazdırma sayfaları ve entegrasyon testi iskeletleri üretir ve bunları hedef projenize güvenle yazar.

Yalnızca "kod çıkarmak" değil — **kalite kapıları ve geri alma yeteneği olan bir teslimat hattıdır**:

```
Table intake (DB import / DDL)
   → Field & switch configuration (with AI recommendations)
      → One-click generation: self-check → backup → write to disk → compile/validate → register menu → rebuild routes
         → Ledger bookkeeping → rollback-capable
```

---

## 2. Kullanıcılar kimler ve platformayı nasıl kullanıyor
| Kullanıcı | Tipik ihtiyaç | Platformanın sunduğu yol |
| --- | --- | --- |
| Arka uç geliştirici | Elinde bir dizi tablo (veya DDL) var ve hızlıca CRUD çıkarmak istiyor | Tablo yapılandırma sayfası: içe aktarma → alan yapılandırması → tek tıklamayla üretim |
| Full-stack / bağımsız geliştirici | Sıfırdan, tam bir sistem (giriş, yetki, sözlükler dahil) kurmak istiyor | Hat sayfası: tek cümleyle sistem kurma |
| Takım lideri | Kod tarzını, standartları ve dizin yapısını birleştirmek istiyor | Sistem yönetimi: bir sistem = bir çıkış yapılandırması, toplu uygulama |
| Platform bakımcısı | Kodu değiştirmeden şablonları değiştirmek istiyor | Şablon yönetimi: çevrimiçi düzenleme, kaydetme anında geçerli olur, sürüm geri alma desteklenir |

## 3. Hangi Sorunları Çözer

| Sorun | CodeGen'in Yaptığı |
| --- | --- |
| Tekrarlı CRUD cehennemi — her tablo için bir yığın şablon kod | Tablo başına anahtarlar tek tıklamayla eksiksiz bir ön ve arka uç üretir; CRUD, sayfalama, doğrulama, içe/dışa aktarma dahil |
| Ön/arka alan ve API uyuşmazlıkları yüzünden bitmek bilmeyen entegrasyon düzeltmeleri | Aynı tablo meta verileri hem arka uç API'lerini hem de ön yüz sayfalarını besler — anlamsal kayma sıfır |
| Ekip kurallarını birleştirmek zor; herkesin tarzı farklı | Şablonlar kuraldır: merkezi olarak yönetilen 22 FreeMarker şablonu, sürüm geri almayla çevrimiçi düzenlenebilir |
| Üreticiler "üzerine yazıp kaybeder" elle yazılmış mantığı | İşaret-blok birleştirme mekanizması: elle yazılan kod korunur, yalnızca üretilen bölgeler değiştirilir |
| Üretilen kod derleme anına kadar güvenilmez | Bellekte derleme öz denetimi (gerçek `javac`), yapısal sözleşme kontrolleri, disk üzeri fark önizlemesi — derlenmedikçe hiçbir şey diske yazılmaz |
| Sıfırdan yeni bir sistem iskeleti çıkarmak zaman alıcı | Hattın "tek cümleyle sistem kurma": DB oluştur → iskelet → üret → paketle → menü kaydet, tam otomatik |

---

## 4. Yetenek Haritası

- **Tablo alımı**: DDL ayrıştırma, doğrudan veritabanı içe aktarımı, toplu alım sihirbazı, otomatik türetilen sütun yapılandırması (denetimler / doğrulama / sözlükler / ilişkiler).
- **Tablo şekilleri**: tek tablo, ağaç tablo (ağaç API + ağaç ızgara), ana-ayrıntı (aynı işlemde kaydetme + ayrıntı sekmeleri), çoka-çok bağlantı tabloları.
- **Üretilen çıktılar**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue sayfaları + A4 yazdırma sayfaları + entegrasyon testi iskeletleri.
- **İş geliştirmeleri**: ilişki açılır menüleri, sözlük çevirisi, veri maskeleme, benzersizlik kontrolleri, form bağlantılama, veri kapsamı yetkileri, Excel içe/dışa aktarma, onay iş akışı, ödeme / dosya / mobil yetenek paketleri.
- **Kalite kapıları**: bellekte derleme öz denetimi (gerçek `javac`), yapısal sözleşme kontrolleri, yetki tutarlılık kontrolleri, disk üzeri fark önizlemesi, ön yüz çıktılarının gerçek doğrulaması.
- **Güvenli iniş**: diske yazma + `.gen-backup` toplu yedekler, elle yazılan kodu korumak için işaret-blok birleştirme, `gen_output` dört durumlu defteri (oluşturuldu / birleştirildi / atlandı / başarısız), geçmiş geri alma.
- **Çok sistemli yalıtım**: her hedef proje bir "sistem yapılandırmasıdır" (ön/arka yollar, paket adları, ağ geçidi öneki, bağımlılık sınıf yolu, üretim-anahtarı varsayılanları) — birden çok proje paralel olarak sunulur.
- **Yapay zeka geliştirmeleri**: tek cümleyle sistem kurma (yapay zeka modelleme), tohum-veri yapay zeka geliştirmesi, tablo başına yapay zeka önerilen yapılandırması (fark önizleme + işaretli uygulama).

---

## 5. Tipik Senaryolar

| Senaryo | Açıklama |
| --- | --- |
| Ağaç tablo | Ürün kategorileri gibi hiyerarşik veriler — otomatik üretilen ağaç API, ağaç ızgara ve üst-seçim açılır menüsü |
| Tek tablo CRUD | Müşteriler, ürünler vb. — yabancı anahtar açılır menüleri + sözlük çevirisi + hazır içe/dışa aktarma |
| Ana-ayrıntı | Satış siparişleri + sipariş kalemleri — aynı işlemde kaydetme, çift sekmeli (ana bilgi / ayrıntılar) ayrıntı çekmecesi |
| Salt okunur / istatistik | Stok akışları vb. — yazma anahtarları kapalı, yalnızca sorgu ve dışa aktarma |
| Yazdırma belgeleri | "Yazdır" anahtarını işaretleyin, başlık + ayrıntı tablosuyla A4 yazdırma sayfası ve rotası üretilir |
| Tek cümleyle sistem kurma | İş alanını tarif edin — yapay zeka tek seferde DB ve tabloları + iskeleti + tam üretimi + menü kaydını oluşturur |

> Tüm senaryoların çalıştırılabilir demoları (`demo_admin` projesi) ekran görüntüleriyle mevcuttur — bkz. `docs/典型场景操作指南-演示后台demo.md` (Çince).

---

## 6. Temel İş Akışı

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Table intake │ → │ Field/switch conf. │ → │ One-click generate (self-check→backup→write→     │ → │ Ledger · Rollback │
│              │   │                    │   │ compile→menu→route)                              │   │                   │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     controls/validation     Lands on disk only after all quality gates pass;      Batch-level traceability
                    /dictionaries           hand-written code is preserved
```

- **Fark önizleme**: üretilen çıktılar, mevcut disk durumuna karşı dosya dosya farkı alınır; herhangi bir şey yazılmadan önce siz onaylarsınız, kazara üzerine yazmalar önlenir.
- **Üretim defteri**: her dosya eylemini, bayt sayısını, MD5'i ve yedek konumunu kaydeder; tüm bir toplu işlem geri alınabilir.

---

## 7. Tek Cümleyle Sistem Kurma (Hat)

"Sıfırdan arka uç iskeleti çıkarmaya" yönelik üst düzey bir yetenektir. İş alanını tarif ettikten sonra hat, otomatik olarak 5 aşamadan ilerler:

| Aşama | Anlamı |
| --- | --- |
| `CHECK` | Hedef proje yapılandırmasını ve bağımlılık sınıf yolunu doğrula |
| `WRITE` | Veritabanı ve tablolar oluştur, iskeleti ve üretilen çıktıları yaz |
| `COMPILE` | Arka uç `mvn package` / ön yüz derlemesi, bellekte derleme öz denetimi |
| `MENU` | Menü yetkileri üret ve kaydet (çift çıkış: artefakt SQL / dahili doğrudan yazım) |
| `ROUTE` | Ön yüz rota parçacıklarını yeniden oluştur |

Herhangi bir aşama başarısızlığı, kolayca konumlandırılabilmesi için okunabilir bir hata ile hemen durdurulur.

---

## 8. Sistem Mimarisi

Ön yüz ve arka uç ayrılmış, birlikte çalışan iki depo:

| Depo | Rol | Yığın |
| --- | --- | --- |
| `codegen-server` | Arka uç: üretim motoru, şablonlar, doğrulama, defter, hat | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Ön yüz: konsol (tablo yapılandırma / şablonlar / defter / sistem yönetimi) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (history modu) |

Arka uç katmanlaması:

```
controller/    API layer (Gen* generation chain / Sys* system management / pipeline)
service/impl/  Core implementations (generation chain, template engine, merge, validation, pipeline execution)
util/          Template engine, model assembly, marker-block merge, self-check compilation
config/        Output config, self-check config, template seeder
common/        Result / BaseEntity / exceptions / audit
resources/gen-templates/   22 FreeMarker templates (backend / frontend / shared / test groups)
```

**Hedef projenin sağlaması gereken temel yetenekler** (üretici yalnızca iş kodunu üretir; eksik bir temel sessizce başarısız olur):

1. MyBatis-Plus sayfalama eklentisi (`PaginationInnerInterceptor`)
2. `Result` / `BaseEntity` / `PageQuery` gibi temel sınıflar (paket öneki `root-package` ile eşleşmeli)
3. `toPageData` içeren ön yüz `utils/request.js` (`Page{records,total}` öğesini `{list,total}` haline dönüştürür)
4. Bağımlılıklar `springdoc-openapi-starter-webmvc-ui` (üretilen kod `@Operation` kullanır) ve `poi-ooxml` (Excel)

---

## 9. Teknik Yığına Bakış

| Boyut | Seçim |
| --- | --- |
| Arka uç dil / çatı | Java 21 / Spring Boot 3.2.5 |
| Kalıcılık | MyBatis-Plus 3.5.5 |
| Şablon motoru | FreeMarker (22 tohum şablonu, çevrimiçi düzenleme + sürüm geri alma) |
| Güvenlik | JWT kimlik doğrulaması |
| Veritabanı | MySQL 8 |
| Ön yüz (generated projects) | Vue 3 + Element Plus + Vite |
| Ön yüz (platform console) | Vue 2.7 + Element UI |
| Yerel yapay zeka | qwen2.5-coder:7b (modelleme / önerilen yapılandırma / tohum-veri geliştirmesi) |
| API belgeleri | springdoc-openapi (Swagger UI) |

---

## 10. Hızlı Başlangıç

### 1. Veritabanını oluşturun (13 platform tablosu + varsayılan hesap admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Hedef projeyi yapılandırın

Yapılandırma şablonunu kopyalayın ve yerel yollarınıza göre ayarlayın:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` `.gitignore` tarafından hariç tutulur ve commit edilmez. Temel ayarlar:

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

Öz denetim bağımlılık dizinini şu şekilde dışa aktarın:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Arka ucu başlatın

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

Servis varsayılan olarak **8120** portunu kullanır; API belgeleri `/swagger-ui/index.html` adresindedir.

### 4. Ön ucu başlatın

```bash
cd ../codegen-web
npm install
npm run serve
```

Ön uç varsayılan olarak **9530** portunu kullanır, `/dev-api` adresini arka uç 8120 portuna vekilleştirir. Varsayılan hesap `admin / admin123`.

### 5. Temel yapılandırma öğeleri

| Ayar | Açıklama |
| --- | --- |
| `gen.output.*` | Artefakt yazma yolları, paket adları, modül eşlemesi |
| `gen.selfcheck.classpath` | Bellekte derleme öz denetimi için bağımlılık sınıf yolu, `dir/*` destekler |
| `gen.menu.outlet` | `artifact` yalnızca menü SQL'i ve rota parçacıkları üretir; `internal` ayrıca platformun kendi `sys_menu` öğesini yazar |

---

## 11. Belgelendirme Haritası

| Belge | İçerik |
| --- | --- |
| `docs/代码生成器-操作手册.md` (Çince) | Dağıtım, 5 dakikalık başlangıç, tablo yapılandırma, toplu alım, şablon yönetimi, defter, API kataloğu, tam hat (ekran görüntüleriyle) |
| `docs/典型场景操作指南-演示后台demo.md` (Çince) | 5 tipik senaryoyu kapsayan 6 tablo, çalıştırılabilir demo ve gerçek çalışma ekran görüntüleriyle |
| `docs/项目介绍.md` | Proje tanıtım belgesi, bu dosyayla aynı kaynak |
| `docs/代码生成器-Vx.x-规划.md` | V3.1 → V7.3 arasındaki sürüm planları ve özellik envanterleri |
| `codegen-web/README.md` | Ön yüz konsol hızlı başlangıç referansı |

---

## 12. Sürüm Geçmişi

| Sürüm | Tema / plan belgesi | İniş commit'i |
| --- | --- | --- |
| V1.2 / V1.3 | Tek tıklama üretim / toplu / geri alma; fark önizleme ve yapı senkronizasyonu | — (yalnızca `src/api/gen.js` yorumlarında) |
| V2.0 | Dinamik rotalar (arka uçtan teslim edilen menü ağacı) | — |
| V3.0 / V3.1 | Çok sistemli defter; çok sistemli döngü + ilk otomatik testler | V3.1 (174 satır) |
| V4.2 | İzlenebilir, geri alınabilir üretim sonuçları (yazma defteri) | V4.2 (292 satır) |
| V4.3 | Şablon regresyonu ve kalite koruyucuları | V4.3 (200 satır) |
| V4.4 | Tek ana çok ayrıntı, basamaklı yazım bütünlüğü, şablon şeritleri | V4.4 (154 satır) |
| V4.5 | A4 yazdırma belgeleri (sıfır üçüncü taraf bağımlılık) | V4.5 (127 satır) |
| V5.0 | Tek cümleyle sistem kurma: hat + yapay zeka modelleme + iskelet tabanı | — |
| V5.1 | DDL denetim sütunları otomatik tamamlama | V5.1 (100 satır) |
| V5.2 | Hat tohum-veri başlatma adımı | V5.2 (107 satır), 84ec8dd |
| V5.5 | Çok modüllü proje üretimi tamamlama | af73dd3 |
| V6.0 | Mikro hizmet altyapısı (Nacos / Gateway / Feign, üçüncü mod "micro") | V6.0 (111 satır), 1deecbd |
| V6.1 | Activiti iş akışı çift motor | V6.1 (100 satır), 02b671e |
| V6.2 | Artefakt teslimat yüzeyi (CLI / dağıtım artefaktları / yapılandırma güvenliği) | V6.2 (114 satır), 02b671e |
| V6.3 | Ödeme yetenek paketi (WeChat / Alipay + Mock) | V6.3 (104 satır), 1c4ee6a, 22eede5 |
| V6.4 | Yetki ve güvenlik temeli (token kalıcılığı / BCrypt / API yetkileri / giriş koruması) | V6.4 (107 satır), 007a202 |
| V6.5 | Mobil uni-app (H5 + WeChat Mini Program) | V6.5 (105 satır), 779ac41 |
| V6.6 | Dosya yetenek paketi | 98f3c09 |
| V6.7 | Artefakt test fabrikası kabulü (test_enabled üç durum + H2 iskeleti) | V6.7 (134 satır), 4e7d318 |
| V6.8 | Artefakt sürdürülebilirliği (dryRun / korunan atlama / marka parametreleştirme) | V6.8 (141 satır), be6f858 |
| V6.9 | Platform belgelendirme merkezi (arka uç tabanı, ön uç aynı toplu işte tamamlandı) | — |
| V6.10 | Platform gözlemlenebilirliği: Actuator + bellekte metrik penceresi + gömülü izleme sayfası | — |
| V7.0 | Yapay zeka sayfa ve menü önerileri (önizleme / uygulama) | V7.0 (94 satır), 45f2670 |
| V7.1 | Tohum-veri yapay zeka geliştirmesi (yapay zeka semantiğe sahip, kurallar yapıya) | V7.1 (42 satır), 5ca6e72 |
| V7.2 | Başarılı hatın ardından SQL teslimatları `db/` dizinine yazılır | eebff88 |
| **V7.3** | **Teslimat güvenilirliği: öz denetimli derlenen artefakt testleri / birleşik SQL teslimat çıkışı / tablo başına yapay zeka önerileri** | **V7.3 (138 satır)** |
| **V7.4** | Tekrarlanabilir teslimat: boş bir veritabanından dağıtılabilir, her commit çalıştırılabilir garanti edilir | c306e29 |
| **V7.5** | Proje dil yapılandırması: 206 dillik liste / varsayılan dil / etkin diller, üretilen koda lang ve dir enjeksiyonu | 7a9afa0 + abac56d |
| **V8.0** | Tam sayfa i18n: 206 dilli çerçeve (8'i profesyonelce çevrildi), OFF sıfır fark sözleşmeli çift hatlı i18n | 9e5dd57 → 7d57aea |
| **V8.1** | Kalite sertleştirme: çevrimdışı sözleşme düzeneği (9 mod, OFF sıfır fark) + i18n-core birim testleri + CI kapısı + dürüst README ayrımı (çerçeve 206 / çevrili 8) + check-locale-keys.mjs | 93a12d5 + e49330b |
| **V9.0** | Üretilen artefaktlar Vue 3 + Element Plus + Vite'a modernize edildi; çalışma zamanında doğrulandı (40/40 iddia, başarılı vite build, ar-SA RTL dahil 14 ekran görüntüsü); 3 çalışma zamanı hatası giderildi | 93a12d5 + e49330b  |
| **V9.1** | **Bakım yapılabilirlik refaktörü · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9** | 654b8e0 → b0d081e  |
| **V9.2** | **Test merkezi & doğrulama merkezi · Test Center (7 tables: env/suite/case/dataset/run/result/schedule) + Verification Center (tc_run/tc_case_result/tc_artifact, 9 suites S1-S9, single-run 409 lock, `codegen.testcenter/v1`); 373/373 tests; 206/206 locales; output byte-identical** | V9.2 release, **mevcut sürüm** |

---

> **V6.10 Platform gözlemlenebilirliği**: Actuator + Micrometer'i entegre eder, her 5 saniyede bir bellekteki halka tampona (30 dakikalık pencere) örnekler, ayrıca yeni bir gömülü "Sistem İzleyici" sayfası — arka uç `/monitor/*` toplama API'leri + ön yüz ECharts panelleri. Yalnızca `health/info/metrics` uç noktaları beyaz listeye alınır; `/actuator/health` tokensizken `metrics` vb. kimlik doğrulama gerektirir; `env/beans/heapdump/threaddump/loggers` asla açığa çıkmaz. Örnekleme penceresi yalnızca süreç başına olup yeniden başlatmada kaybolur ve örnekler arasında paylaşılmaz. Ayrıntılar için bkz. `docs/可观测性说明.md` (Çince).

## 13. Proje Durumu

- Mevcut planlanan sürüm hattı **V9.2** seviyesine ulaştı (yetenekler sürekli yinelemede: yapay zeka modelleme, tohum-veri geliştirmesi, yetenek-paketi sistemi vb.).
- Eşlik eden demo projesi `demo_admin` (arka uç 8140 / ön uç 9040), ağaç / CRUD / ana-ayrıntı / salt okunur / yazdırma senaryolarını kapsar ve doğrulama için bağımsız çalışır.
- Her iki depo da Gitee üzerinde barındırılır: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. Yazar & Destek

- **Yazar**: bir Çinli programcı (bağımsız geliştirici)
- **E-posta**: `hustxiayuhong@126.com`
- **Teknik destek**: lütfen e-postanızı **Çince** yazın; sorun açıklaması, çalışma ortamı (JDK / MySQL / sürüm) ve yeniden üretme adımları dahil olmak üzere

> Her e-postayı okur ve yanıtlarım. Hata bildirimleri, özellik talepleri ve iş birliği soruları memnuniyetle karşılanır.

---

## 15. Benzer Araçlarla Karşılaştırma

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

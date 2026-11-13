# Majiang · Pembuat Kode (CodeGen) — Pengantar Proyek

> Languages: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · **Bahasa Indonesia** · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> Sebuah pembuat kode multi-proyek untuk **Spring Boot + Vue**: mulai dari struktur tabel basis data, menghasilkan kode bisnis front-end dan back-end **yang dapat dikompilasi, dapat dijalankan, dan siap dengan menu serta izin**, serta menangani seluruh siklus pengiriman berupa penulisan ke disk, pencadangan, pembukuan, dan rollback.

GitHub: https://github.com/hustxia/CodeGen

> **versi saat ini V9.1 · Refaktor untuk kemudahan pemeliharaan · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9**
> **V9.0 · Artefak yang dihasilkan dimodernisasi ke Vue 3 + Element Plus + Vite; terverifikasi saat runtime (40/40 assertion, vite build sukses, 14 tangkapan layar incl. ar-SA RTL); 3 defect runtime diperbaiki**
> **V8.1** · Penguatan kualitas: harness kontrak offline (9 mode, zero-diff OFF) + unit test i18n-core + gate CI + pembagian README yang jujur (framework 206 / diterjemahkan 8) + check-locale-keys.mjs
> **V8.0** · i18n halaman penuh: framework 206 bahasa (8 diterjemahkan profesional), i18n jalur ganda dengan kontrak zero-diff saat OFF

## 1. Apa Ini

CodeGen adalah pabrik kode "berbasis tabel". Bawa tabel bisnis Anda (melalui impor basis data atau menempelkan DDL), konfigurasikan bidang dan sakelar pembuatan, lalu klik "Generate" — ia menghasilkan Entity/Mapper/Service/Controller/DTO/VO back-end, `api.js` dan halaman Vue front-end, menu dan rute, bahkan halaman cetak A4 dan kerangka pengujian integrasi, lalu menuliskannya dengan aman ke proyek target Anda.

Ini bukan sekadar "memuntahkan kode" — ini adalah sebuah **pipeline pengiriman dengan gerbang kualitas dan kemampuan rollback**:

```
Table intake (DB import / DDL)
   → Field & switch configuration (with AI recommendations)
      → One-click generation: self-check → backup → write to disk → compile/validate → register menu → rebuild routes
         → Ledger bookkeeping → rollback-capable
```

---

## 2. Siapa Penggunanya, dan Bagaimana Mereka Menggunakannya

| Pengguna | Kebutuhan khas | Jalur yang diberikan platform |
| --- | --- | --- |
| Pengembang back-end | Memiliki sekumpulan tabel (atau DDL) dan ingin segera menjalankan CRUD | Halaman konfigurasi tabel: impor → konfigurasi bidang → hasilkan satu klik |
| Pengembang full-stack / independen | Ingin membangun sistem lengkap dari nol (termasuk login, izin, kamus) | Halaman pipeline: bangun sistem dengan satu kalimat |
| Ketua tim | Ingin menyatukan gaya kode, konvensi kode, dan struktur direktori | Manajemen sistem: satu sistem = satu konfigurasi keluaran, berlaku secara batch |
| Pengelola platform | Ingin mengubah template tanpa mengubah kode | Manajemen template: edit daring, berlaku saat disimpan, mendukung rollback riwayat |
---

## 3. Masalah Apa yang Diselesaikannya

| Titik masalah | Apa yang dilakukan CodeGen |
| --- | --- |
| Neraka CRUD berulang — tumpukan kode standar untuk setiap tabel | Sakelar per tabel menghasilkan front-end dan back-end lengkap dalam satu klik, termasuk CRUD, paginasi, validasi, impor/ekspor |
| Ketidakcocokan bidang dan API front/back yang menyebabkan perbaikan integrasi tak berujung | Metadata tabel yang sama menggerakkan API back-end dan halaman front-end — tanpa penyimpangan semantik |
| Konvensi tim sulit diseragamkan; gaya setiap orang berbeda | Template adalah konvensinya: 22 template FreeMarker dikelola terpusat, dapat diedit daring dengan rollback versi |
| Pembuat kode "menimpa dan menghilangkan" logika tulisan tangan | Mekanisme penggabungan blok-penanda: kode tulisan tangan dipertahankan, hanya region yang dihasilkan yang diganti |
| Kode yang dihasilkan tidak dapat dipercaya hingga waktu kompilasi | Pemeriksaan mandiri kompilasi di memori (nyata `javac`), pemeriksaan kontrak struktural, pratinjau diff di disk — tidak ada yang mendarat di disk kecuali terkompilasi |
| Membangun sistem baru dari nol memakan waktu | Pipeline "bangun sistem dengan satu kalimat": buat DB → scaffold → hasilkan → kemas → daftarkan menu, sepenuhnya otomatis |

---

## 4. Lanskap Kemampuan

- **Masukan tabel**: penguraian DDL, impor basis data langsung, panduan masukan batch, konfigurasi kolom turunan otomatis (kontrol / validasi / kamus / relasi).
- **Bentuk tabel**: tabel tunggal, tabel pohon (API pohon + grid pohon), master-detail (simpan transaksi-sama + tab detail), tabel penghubung banyak-ke-banyak.
- **Artefak yang dihasilkan**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + halaman Vue + halaman cetak A4 + kerangka pengujian integrasi.
- **Peningkatan bisnis**: dropdown relasi, terjemahan kamus, masking data, pemeriksaan keunikan, penautan formulir, izin cakupan data, impor/ekspor Excel, alur kerja persetujuan, paket kapabilitas pembayaran / file / seluler.
- **Gerbang kualitas**: pemeriksaan mandiri kompilasi di memori (nyata `javac`), pemeriksaan kontrak struktural, pemeriksaan konsistensi izin, pratinjau diff di disk, validasi nyata artefak front-end.
- **Pendaratan aman**: tulis ke disk + pencadangan batch `.gen-backup`, penggabungan blok-penanda untuk mempertahankan kode tulisan tangan, buku besar empat-status `gen_output` (dibuat / digabung / dilewati / gagal), rollback historis.
- **Isolasi multi-sistem**: setiap proyek target adalah sebuah "konfigurasi sistem" (jalur front/back, nama paket, awalan gateway, classpath dependensi, default sakelar pembuatan) — beberapa proyek dilayani secara paralel.
- **Peningkatan AI**: bangun sistem dengan satu kalimat (pemodelan AI), peningkatan data-bibit AI, konfigurasi yang direkomendasikan per tabel oleh AI (pratinjau diff + terapkan bertanda).

---

## 5. Skenario Tipikal

| Skenario | Deskripsi |
| --- | --- |
| Tabel pohon | Data hierarkis seperti kategori produk — API pohon, grid pohon, dan dropdown pemilihan induk yang dihasilkan otomatis |
| CRUD tabel tunggal | Pelanggan, produk, dll. — dropdown FK + terjemahan kamus + impor/ekspor siap pakai |
| Master-detail | Pesanan penjualan + item pesanan — simpan transaksi-sama, laci detail dengan dua tab (info master / detail) |
| Hanya-baca / statistik | Aliran inventaris, dll. — sakelar tulis dimatikan, hanya kueri dan ekspor |
| Cetak dokumen | Centang sakelar "cetak" untuk menghasilkan halaman cetak A4 dan rute, dengan tajuk + tabel detail |
| Bangun sistem satu kalimat | Jelaskan domain bisnis — AI membuat DB & tabel + scaffold + pembuatan penuh + pendaftaran menu sekaligus |

> Semua skenario memiliki demo yang dapat dijalankan (proyek `demo_admin`) dengan tangkapan layar — lihat `../codegen-server/docs/典型场景操作指南-演示后台demo.md` (dalam bahasa Mandarin).

---

## 6. Alur Kerja Inti

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Table intake │ → │ Field/switch conf. │ → │ One-click generate (self-check→backup→write→     │ → │ Ledger · Rollback │
│              │   │                    │   │ compile→menu→route)                              │   │                   │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     controls/validation     Lands on disk only after all quality gates pass;      Batch-level traceability
                    /dictionaries           hand-written code is preserved
```

- **Pratinjau diff**: artefak yang dihasilkan dibandingkan file-per-file terhadap keadaan disk saat ini; Anda mengonfirmasi sebelum sesuatu ditulis, menghindari penimpaan tak sengaja.
- **Buku besar pembuatan**: setiap file mencatat tindakan, jumlah byte, MD5, dan lokasi cadangannya; seluruh batch dapat di-rollback.

---

## 7. Bangun Sistem dengan Satu Kalimat (Pipeline)

Sebuah kemampuan tingkat tinggi yang ditujukan untuk "membuat back-end dari awal". Setelah Anda menjelaskan domain bisnis, pipeline maju secara otomatis melalui 5 tahap:

| Tahap | Makna |
| --- | --- |
| `CHECK` | Validasi konfigurasi proyek target dan classpath dependensi |
| `WRITE` | Buat basis data & tabel, tulis scaffold dan artefak yang dihasilkan |
| `COMPILE` | Back-end `mvn package` / build front-end, pemeriksaan mandiri kompilasi di memori |
| `MENU` | Hasilkan dan daftarkan izin menu (dua saluran: SQL artefak / tulis langsung internal) |
| `ROUTE` | Bangun ulang fragmen rute front-end |

Kegagalan pada tahap apa pun menghentikan seketika dengan kesalahan yang dapat dibaca untuk memudahkan penemuan.

---

## 8. Arsitektur Sistem

Front-end dan back-end terpisah, dua repositori bekerja bersama:

| Repositori | Peran | Tumpukan |
| --- | --- | --- |
| `codegen-server` | Back-end: mesin pembuatan, template, validasi, buku besar, pipeline | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Front-end: konsol (konfigurasi tabel / template / buku besar / manajemen sistem) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (mode history) |

Pelapisan back-end:

```
controller/    API layer (Gen* generation chain / Sys* system management / pipeline)
service/impl/  Core implementations (generation chain, template engine, merge, validation, pipeline execution)
util/          Template engine, model assembly, marker-block merge, self-check compilation
config/        Output config, self-check config, template seeder
common/        Result / BaseEntity / exceptions / audit
resources/gen-templates/   22 FreeMarker templates (backend / frontend / shared / test groups)
```

**Kapabilitas dasar yang harus disediakan proyek target** (pembuat kode hanya menghasilkan kode bisnis; dasar yang hilang gagal secara diam-diam):

1. Plugin paginasi MyBatis-Plus (`PaginationInnerInterceptor`)
2. Kelas dasar seperti `Result` / `BaseEntity` / `PageQuery` (awalan paket harus cocok dengan `root-package`)
3. `utils/request.js` front-end dengan `toPageData` (mengonversi `Page{records,total}` menjadi `{list,total}`)
4. Dependensi `springdoc-openapi-starter-webmvc-ui` (kode yang dihasilkan menggunakan `@Operation`) dan `poi-ooxml` (Excel)

---

## 9. Tumpukan Teknologi Sekilas

| Dimensi | Pilihan |
| --- | --- |
| Bahasa/kerangka back-end | Java 21 / Spring Boot 3.2.5 |
| Ketekunan | MyBatis-Plus 3.5.5 |
| Mesin template | FreeMarker (22 template bibit, pengeditan daring + rollback versi) |
| Keamanan | Autentikasi JWT |
| Basis data | MySQL 8 |
| Front-end (generated projects) | Vue 3 + Element Plus + Vite |
| Front-end (platform console) | Vue 2.7 + Element UI |
| AI lokal | qwen2.5-coder:7b (pemodelan / konfigurasi yang direkomendasikan / peningkatan data-bibit) |
| Dokumentasi API | springdoc-openapi (Swagger UI) |

---

## 10. Mulai Cepat

### 1. Buat basis data (13 tabel platform + akun default admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Konfigurasikan proyek target

Salin template konfigurasi dan sesuaikan dengan jalur lokal Anda:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` dikecualikan oleh `.gitignore` dan tidak akan di-commit. Pengaturan kunci:

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

Ekspor direktori dependensi pemeriksaan-mandiri seperti ini:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Mulai back-end

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

Layanan menggunakan port **8120** secara default; dokumentasi API di `/swagger-ui/index.html`.

### 4. Mulai front-end

```bash
cd ../codegen-web
npm install
npm run serve
```

Front-end menggunakan port **9530** secara default, mem-proksi `/dev-api` ke port 8120 back-end. Akun default `admin / admin123`.

### 5. Item konfigurasi kunci

| Pengaturan | Deskripsi |
| --- | --- |
| `gen.output.*` | Jalur tulis artefak, nama paket, pemetaan modul |
| `gen.selfcheck.classpath` | Classpath dependensi untuk pemeriksaan mandiri kompilasi di memori, mendukung `dir/*` |
| `gen.menu.outlet` | `artifact` hanya menghasilkan SQL menu & fragmen rute; `internal` juga menulis `sys_menu` milik platform |

---

## 11. Peta Dokumentasi

| Dokumen | Konten |
| --- | --- |
| `../codegen-server/docs/代码生成器-操作手册.md` (dalam bahasa Mandarin) | Penyebaran, mulai 5 menit, konfigurasi tabel, masukan batch, manajemen template, buku besar, katalog API, pipeline penuh (dengan tangkapan layar) |
| `../codegen-server/docs/典型场景操作指南-演示后台demo.md` (dalam bahasa Mandarin) | 6 tabel mencakup 5 skenario tipikal, dengan demo yang dapat dijalankan dan tangkapan layar jalan nyata |
| `项目介绍.md` | Dokumen pengantar proyek, sumber yang sama dengan file ini |
| `../codegen-server/docs/代码生成器-Vx.x-规划.md` | Rencana versi dan inventaris fitur dari V3.1 → V9.1 |
| `../codegen-web/README.md` | Referensi mulai cepat konsol front-end |

---

## 12. Riwayat Versi

| Versi | Tema / dokumen rencana | Commit pendaratan |
| --- | --- | --- |
| V1.2 / V1.3 | Pembuatan / batch / rollback satu klik; pratinjau diff & sinkronisasi struktur | — (hanya di komentar `src/api/gen.js`) |
| V2.0 | Rute dinamis (pohon menu yang dikirim back-end) | — |
| V3.0 / V3.1 | Buku besar multi-sistem; loop multi-sistem + pengujian otomatis pertama | V3.1 (174 baris) |
| V4.2 | Hasil pembuatan yang dapat dilacak dan di-rollback (tulis buku besar) | V4.2 (292 baris) |
| V4.3 | Regresi template & pagar kualitas | V4.3 (200 baris) |
| V4.4 | Satu-master-banyak-detail, kelengkapan tulis berantai, jalur template | V4.4 (154 baris) |
| V4.5 | Dokumen cetak A4 (nol dependensi pihak ketiga) | V4.5 (127 baris) |
| V5.0 | Bangun sistem satu kalimat: pipeline + pemodelan AI + basis scaffold | — |
| V5.1 | Pelengkapan otomatis kolom audit DDL | V5.1 (100 baris) |
| V5.2 | Langkah inisialisasi data-bibit pipeline | V5.2 (107 baris), 84ec8dd |
| V5.5 | Penyelesaian pembuatan proyek multi-modul | af73dd3 |
| V6.0 | Infrastruktur layanan-mikro (Nacos / Gateway / Feign, mode ketiga "micro") | V6.0 (111 baris), 1deecbd |
| V6.1 | Mesin ganda alur kerja Activiti | V6.1 (100 baris), 02b671e |
| V6.2 | Permukaan pengiriman artefak (CLI / artefak penyebaran / keamanan konfig) | V6.2 (114 baris), 02b671e |
| V6.3 | Paket kapabilitas pembayaran (WeChat / Alipay + Mock) | V6.3 (104 baris), 1c4ee6a, 22eede5 |
| V6.4 | Garis dasar izin & keamanan (ketekunan token / BCrypt / izin API / perlindungan login) | V6.4 (107 baris), 007a202 |
| V6.5 | Mobile uni-app (H5 + Mini Program WeChat) | V6.5 (105 baris), 779ac41 |
| V6.6 | Paket kapabilitas file | 98f3c09 |
| V6.7 | Penerimaan pabrik pengujian artefak (tri-state test_enabled + kerangka H2) | V6.7 (134 baris), 4e7d318 |
| V6.8 | Kemudahan pemeliharaan artefak (dryRun / skip terlindungi / parametrisasi merek) | V6.8 (141 baris), be6f858 |
| V6.9 | Pusat dokumentasi platform (basis back-end, front-end selesai di batch yang sama) | — |
| V6.10 | Observabilitas platform: Actuator + jendela metrik di memori + halaman monitor tertanam | — |
| V7.0 | Rekomendasi halaman & menu AI (pratinjau / terapkan) | V7.0 (94 baris), 45f2670 |
| V7.1 | Peningkatan data-bibit AI (AI memiliki semantik, aturan memiliki struktur) | V7.1 (42 baris), 5ca6e72 |
| V7.2 | Hasil SQL ditulis ke `db/` setelah pipeline berhasil | eebff88 |
| **V7.3** | **Keandalan pengiriman: pengujian artefak terkompilasi pemeriksaan-mandiri / saluran pengiriman SQL terpadu / rekomendasi AI per tabel** | **V7.3 (138 baris), 696765b** |
| **V7.4** | Pengiriman yang dapat diulang: dapat diterapkan dari basis data kosong, setiap commit terjamin dapat dijalankan | c306e29 |
| **V7.5** | **Konfigurasi bahasa proyek: daftar 206 bahasa / bahasa default / bahasa yang diaktifkan, injeksi lang & dir ke hasil yang dibuat** | 7a9afa0 + abac56d |
| **V8.0** | **i18n halaman penuh: framework 206 bahasa (8 diterjemahkan profesional), i18n jalur ganda dengan kontrak zero-diff saat OFF** | 9e5dd57 → 7d57aea |
| **V8.1** | **Penguatan kualitas: harness kontrak offline (9 mode, zero-diff OFF) + unit test i18n-core + gate CI + pembagian README yang jujur (framework 206 / diterjemahkan 8) + check-locale-keys.mjs** | 93a12d5 + e49330b |
| **V9.0** | **Artefak yang dihasilkan dimodernisasi ke Vue 3 + Element Plus + Vite; terverifikasi saat runtime (40/40 assertion, vite build sukses, 14 tangkapan layar incl. ar-SA RTL); 3 defect runtime diperbaiki** | 93a12d5 + e49330b  |
| **V9.1** | **Refaktor untuk kemudahan pemeliharaan · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9** | 654b8e0 → b0d081e, **versi saat ini** |

---

> **V6.10 Observabilitas platform**: mengintegrasikan Actuator + Micrometer, pengambilan sampel setiap 5 detik ke buffer cincin di memori (jendela 30 menit), plus halaman "System Monitor" tertanam baru — API agregasi `/monitor/*` back-end + panel ECharts front-end. Hanya titik akhir `health/info/metrics` yang masuk daftar putih; `/actuator/health` bebas token sementara `metrics` dll. memerlukan autentikasi; `env/beans/heapdump/threaddump/loggers` tidak pernah diekspos. Jendela pengambilan sampel hanya per-proses, hilang saat restart, dan tidak dibagikan antar instance. Lihat `../codegen-server/docs/可观测性说明.md` (dalam bahasa Mandarin) untuk detail.

## 13. Status Proyek

- Garis versi yang direncanakan saat ini telah mencapai **V9.1** (kapabilitas terus beriterasi: pemodelan AI, peningkatan data-bibit, sistem paket-kapabilitas, dll.).
- Proyek demo pendamping `demo_admin` (back-end 8140 / front-end 9040) mencakup skenario pohon / CRUD / master-detail / hanya-baca / cetak dan berjalan mandiri untuk verifikasi.
- Kedua repositori dihosting di Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. Penulis & Dukungan

- **Penulis**: seorang programmer Tiongkok (pengembang independen)
- **Email**: `hustxiayuhong@126.com`
- **Dukungan teknis**: harap tulis email Anda **dalam bahasa Mandarin**, termasuk deskripsi masalah, lingkungan runtime (JDK / MySQL / versi), dan langkah-langkah reproduksi

> Saya membaca dan membalas setiap email. Laporan bug, permintaan fitur, dan pertanyaan kolaborasi semuanya dipersilakan.

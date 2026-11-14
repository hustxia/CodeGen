# Majiang · Trình tạo mã (CodeGen) — Giới thiệu dự án

> Languages: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · **Tiếng Việt** · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> Một công cụ sinh mã đa dự án cho **Spring Boot + Vue**: bắt đầu từ cấu trúc bảng cơ sở dữ liệu, tạo ra mã nghiệp vụ **có thể biên dịch, chạy được, sẵn sàng menu và quyền** ở cả front-end và back-end, đồng thời xử lý toàn bộ quy trình giao hàng: ghi đĩa, sao lưu, ghi sổ và khôi phục (rollback).

GitHub: https://github.com/hustxia/CodeGen

## 1. Đây là gì

CodeGen là một "nhà máy mã" theo hướng dẫn bởi bảng dữ liệu. Đưa các bảng nghiệp vụ của bạn vào (qua nhập CSDL hoặc dán DDL), cấu hình trường và các công tắc sinh mã, sau đó nhấn "Generate" — nó sẽ tạo ra Entity/Mapper/Service/Controller/DTO/VO ở back-end, `api.js` và các trang Vue ở front-end, menu và route, thậm chí cả trang in A4 và khung kiểm thử tích hợp, rồi ghi an toàn vào dự án đích của bạn.

Nó không chỉ "nhả ra mã" — mà là một **quy trình giao hàng có cổng chất lượng và khả năng rollback**:

```
Table intake (DB import / DDL)
   → Field & switch configuration (with AI recommendations)
      → One-click generation: self-check → backup → write to disk → compile/validate → register menu → rebuild routes
         → Ledger bookkeeping → rollback-capable
```

---

## 2. Người dùng là ai, và họ dùng thế nào

| Người dùng | Nhu cầu điển hình | Đường dẫn nền tảng cung cấp |
| --- | --- | --- |
| Lập trình viên back-end | Có một loạt bảng (hoặc DDL) và muốn nhanh chóng dựng CRUD | Trang cấu hình bảng: nhập → cấu hình trường → sinh một cú nhấp |
| Lập trình viên full-stack / độc lập | Muốn xây dựng trọn vẹn một hệ thống từ con số 0 (gồm đăng nhập, quyền, từ điển) | Trang pipeline: dựng hệ thống bằng một câu |
| Trưởng nhóm | Muốn thống nhất phong cách mã, quy tắc viết mã, cấu trúc thư mục | Quản lý hệ thống: một hệ thống = một cấu hình đầu ra, áp dụng hàng loạt |
| Người bảo trì nền tảng | Muốn đổi template mà không đổi mã | Quản lý template: chỉnh sửa trực tuyến, lưu là có hiệu lực, hỗ trợ rollback lịch sử |
## 3. Những vấn đề nó giải quyết

| Điểm đau | CodeGen làm gì |
| --- | --- |
| "Địa ngục" CRUD lặp lại — hàng đống mã khuôn mẫu cho mỗi bảng | Công tắc từng bảng sinh toàn bộ front/back-end chỉ bằng một cú nhấp, bao gồm CRUD, phân trang, xác thực, nhập/xuất |
| Front/back-end lệch trường & API gây sửa lỗi tích hợp vô tận | Cùng một siêu dữ liệu bảng điều khiển cả API back-end và trang front-end — không trôi dạt ngữ nghĩa |
| Quy ước nhóm khó thống nhất; mỗi người một phong cách | Template chính là quy ước: 22 template FreeMarker quản lý tập trung, chỉnh sửa trực tuyến kèm rollback phiên bản |
| Trình sinh "ghi đè rồi mất" logic viết tay | Cơ chế merge khối đánh dấu: giữ lại mã viết tay, chỉ thay thế vùng được sinh |
| Mã sinh ra chưa tin cậy cho đến lúc biên dịch | Tự kiểm tra biên dịch trong bộ nhớ (dùng `javac` thật), kiểm tra hợp đồng cấu trúc, xem trước diff trên đĩa — không biên dịch thì không ghi đĩa |
| Dựng hệ thống mới từ con số 0 tốn thời gian | Pipeline "dựng hệ thống bằng một câu": tạo DB → scaffold → sinh → đóng gói → đăng ký menu, tự động hoàn toàn |

---

## 4. Bản đồ năng lực

- **Nạp bảng**: phân tích DDL, nhập trực tiếp từ CSDL, trình hướng dẫn nhập hàng loạt, tự suy cấu hình cột (điều khiển / xác thực / từ điển / quan hệ).
- **Hình dạng bảng**: bảng đơn, bảng cây (API cây + lưới cây), bảng cha-con (lưu cùng giao dịch + tab chi tiết), bảng trung gian nhiều-nhiều.
- **Sản phẩm sinh ra**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + trang Vue + trang in A4 + khung kiểm thử tích hợp.
- **Tăng cường nghiệp vụ**: dropdown quan hệ, dịch từ điển, che dữ liệu, kiểm tra duy nhất, liên kết form, quyền phạm vi dữ liệu, nhập/xuất Excel, quy trình phê duyệt, gói năng lực thanh toán / tệp / di động.
- **Cổng chất lượng**: tự kiểm tra biên dịch trong bộ nhớ (dùng `javac` thật), kiểm tra hợp đồng cấu trúc, kiểm tra nhất quán quyền, xem trước diff trên đĩa, xác thực thật sự các sản phẩm front-end.
- **Hạ cánh an toàn**: ghi đĩa + sao lưu hàng loạt `.gen-backup`, merge khối đánh dấu để giữ mã viết tay, sổ `gen_output` 4 trạng thái (tạo / gộp / bỏ qua / lỗi), rollback lịch sử.
- **Cách ly đa hệ thống**: mỗi dự án đích là một "cấu hình hệ thống" (đường dẫn front/back, tên package, tiền tố gateway, classpath phụ thuộc, mặc định công tắc sinh) — phục vụ nhiều dự án song song.
- **Tăng cường AI**: dựng hệ thống bằng một câu (mô hình hóa AI), tăng cường dữ liệu hạt giống bằng AI, cấu hình đề xuất AI từng bảng (xem trước diff + áp dụng có chọn).

---

## 5. Kịch bản điển hình

| Kịch bản | Mô tả |
| --- | --- |
| Bảng cây | Dữ liệu phân cấp như danh mục sản phẩm — tự động sinh API cây, lưới cây và dropdown chọn cha |
| CRUD bảng đơn | Khách hàng, sản phẩm,... — dropdown khóa ngoại + dịch từ điển + nhập/xuất có sẵn |
| Cha-con | Đơn bán hàng + chi tiết đơn — lưu cùng giao dịch, ngăn kéo chi tiết hai tab (thông tin chính / chi tiết) |
| Chỉ đọc / thống kê | Luân chuyển tồn kho,... — tắt công tắc ghi, chỉ truy vấn và xuất |
| In tài liệu | Tích chọn công tắc "print" để sinh trang in A4 và route, kèm header + bảng chi tiết |
| Dựng hệ thống một câu | Mô tả miền nghiệp vụ — AI tạo DB & bảng + scaffold + sinh đầy đủ + đăng ký menu trong một lần |

> Mọi kịch bản đều có demo chạy được (dự án `demo_admin`) kèm ảnh chụp màn hình — xem `docs/典型场景操作指南-演示后台demo.md` (tiếng Trung).

---

## 6. Quy trình cốt lõi

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Table intake │ → │ Field/switch conf. │ → │ One-click generate (self-check→backup→write→     │ → │ Ledger · Rollback │
│              │   │                    │   │ compile→menu→route)                              │   │                   │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     controls/validation     Lands on disk only after all quality gates pass;      Batch-level traceability
                    /dictionaries           hand-written code is preserved
```

- **Xem trước diff**: các sản phẩm sinh ra được so sánh từng tệp với trạng thái đĩa hiện tại; bạn xác nhận trước khi ghi, tránh ghi đè ngoài ý muốn.
- **Sổ sinh mã**: mỗi tệp ghi lại hành động, số byte, MD5 và vị trí sao lưu; toàn bộ lô có thể rollback.

---

## 7. Dựng hệ thống bằng một câu (Pipeline)

Một năng lực cao cấp nhắm đến "dựng back-end từ đầu". Sau khi bạn mô tả miền nghiệp vụ, pipeline tự động tiến qua 5 giai đoạn:

| Giai đoạn | Ý nghĩa |
| --- | --- |
| `CHECK` | Xác thực cấu hình dự án đích và classpath phụ thuộc |
| `WRITE` | Tạo CSDL & bảng, ghi scaffold và các sản phẩm sinh ra |
| `COMPILE` | Back-end `mvn package` / build front-end, tự kiểm tra biên dịch trong bộ nhớ |
| `MENU` | Sinh và đăng ký quyền menu (hai đầu ra: SQL sản phẩm / ghi trực tiếp nội bộ) |
| `ROUTE` | Xây lại các mảnh route front-end |

Bất kỳ giai đoạn nào thất bại sẽ dừng ngay lập tức với lỗi dễ đọc để dễ định vị.

---

## 8. Kiến trúc hệ thống

Tách biệt front-end và back-end, hai kho lưu trữ phối hợp:

| Kho | Vai trò | Stack |
| --- | --- | --- |
| `codegen-server` | Back-end: engine sinh, template, xác thực, sổ, pipeline | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Front-end: bảng điều khiển (cấu hình bảng / template / sổ / quản lý hệ thống) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (chế độ history) |

Phân lớp back-end:

```
controller/    API layer (Gen* generation chain / Sys* system management / pipeline)
service/impl/  Core implementations (generation chain, template engine, merge, validation, pipeline execution)
util/          Template engine, model assembly, marker-block merge, self-check compilation
config/        Output config, self-check config, template seeder
common/        Result / BaseEntity / exceptions / audit
resources/gen-templates/   22 FreeMarker templates (backend / frontend / shared / test groups)
```

**Năng lực nền tảng mà dự án đích phải cung cấp** (trình sinh chỉ tạo mã nghiệp vụ; thiếu nền tảng sẽ thất bại âm thầm):

1. Plugin phân trang MyBatis-Plus (`PaginationInnerInterceptor`)
2. Các lớp nền tảng như `Result` / `BaseEntity` / `PageQuery` (tiền tố package phải khớp `root-package`)
3. `utils/request.js` front-end có `toPageData` (chuyển `Page{records,total}` thành `{list,total}`)
4. Các phụ thuộc `springdoc-openapi-starter-webmvc-ui` (mã sinh dùng `@Operation`) và `poi-ooxml` (Excel)

---

## 9. Tổng quan công nghệ

| Khía cạnh | Lựa chọn |
| --- | --- |
| Ngôn ngữ / framework back-end | Java 21 / Spring Boot 3.2.5 |
| Persistence | MyBatis-Plus 3.5.5 |
| Template engine | FreeMarker (22 template hạt giống, chỉnh sửa trực tuyến + rollback phiên bản) |
| Bảo mật | Xác thực JWT |
| Cơ sở dữ liệu | MySQL 8 |
| Front-end (generated projects) | Vue 3 + Element Plus + Vite |
| Front-end (platform console) | Vue 2.7 + Element UI |
| Local AI | qwen2.5-coder:7b (mô hình hóa / cấu hình đề xuất / tăng cường dữ liệu hạt giống) |
| API docs | springdoc-openapi (Swagger UI) |

---

## 10. Bắt đầu nhanh

### 1. Tạo cơ sở dữ liệu (13 bảng nền tảng + tài khoản mặc định admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Cấu hình dự án đích

Sao chép mẫu cấu hình và chỉnh sửa theo đường dẫn local của bạn:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` bị loại trừ bởi `.gitignore` và sẽ không được commit. Các cài đặt chính:

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

Xuất thư mục phụ thuộc tự kiểm tra như sau:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Khởi chạy back-end

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

Dịch vụ mặc định dùng port **8120**; tài liệu API tại `/swagger-ui/index.html`.

### 4. Khởi chạy front-end

```bash
cd ../codegen-web
npm install
npm run serve
```

Front-end mặc định dùng port **9530**, proxy `/dev-api` tới port 8120 của back-end. Tài khoản mặc định `admin / admin123`.

### 5. Các mục cấu hình chính

| Cài đặt | Mô tả |
| --- | --- |
| `gen.output.*` | Đường dẫn ghi sản phẩm, tên package, ánh xạ module |
| `gen.selfcheck.classpath` | Classpath phụ thuộc cho tự kiểm tra biên dịch trong bộ nhớ, hỗ trợ `dir/*` |
| `gen.menu.outlet` | `artifact` chỉ sinh SQL menu & mảnh route; `internal` còn ghi `sys_menu` của nền tảng |

---

## 11. Bản đồ tài liệu

| Tài liệu | Nội dung |
| --- | --- |
| `docs/代码生成器-操作手册.md` (tiếng Trung) | Triển khai, bắt đầu 5 phút, cấu hình bảng, nhập hàng loạt, quản lý template, sổ, danh mục API, pipeline đầy đủ (có ảnh) |
| `docs/典型场景操作指南-演示后台demo.md` (tiếng Trung) | 6 bảng bao phủ 5 kịch bản điển hình, kèm demo chạy được và ảnh chụp thực tế |
| `docs/项目介绍.md` | Tài liệu giới thiệu dự án, cùng nguồn với tệp này |
| `docs/代码生成器-Vx.x-规划.md` | Kế hoạch phiên bản và danh mục tính năng từ V3.1 → V7.3 |
| `codegen-web/README.md` | Tham khảo khởi động nhanh bảng điều khiển front-end |

---

## 12. Lịch sử phiên bản

| Phiên bản | Chủ đề / tài liệu kế hoạch | Commit triển khai |
| --- | --- | --- |
| V1.2 / V1.3 | Sinh một cú nhấp / hàng loạt / rollback; xem trước diff & đồng bộ cấu trúc | — (chỉ trong bình luận `src/api/gen.js`) |
| V2.0 | Route động (cây menu do back-end cung cấp) | — |
| V3.0 / V3.1 | Sổ đa hệ thống; vòng lặp đa hệ thống + các test tự động đầu tiên | V3.1 (174 dòng) |
| V4.2 | Kết quả sinh có thể truy vết, rollback được (ghi sổ) | V4.2 (292 dòng) |
| V4.3 | Hồi quy template & hàng rào bảo vệ chất lượng | V4.3 (200 dòng) |
| V4.4 | Một-chính-nhiều-phụ, hoàn thiện ghi cascade, làn template | V4.4 (154 dòng) |
| V4.5 | Tài liệu in A4 (không phụ thuộc bên thứ ba) | V4.5 (127 dòng) |
| V5.0 | Dựng hệ thống một câu: pipeline + mô hình hóa AI + cơ sở scaffold | — |
| V5.1 | Tự động bổ sung cột audit DDL | V5.1 (100 dòng) |
| V5.2 | Bước khởi tạo dữ liệu hạt giống pipeline | V5.2 (107 dòng), 84ec8dd |
| V5.5 | Hoàn thiện sinh dự án đa module | af73dd3 |
| V6.0 | Hạ tầng microservice (Nacos / Gateway / Feign, chế độ thứ ba "micro") | V6.0 (111 dòng), 1deecbd |
| V6.1 | Dual engine quy trình Activiti | V6.1 (100 dòng), 02b671e |
| V6.2 | Bề mặt giao hàng artifact (CLI / artifact triển khai / bảo mật cấu hình) | V6.2 (114 dòng), 02b671e |
| V6.3 | Gói năng lực thanh toán (WeChat / Alipay + Mock) | V6.3 (104 dòng), 1c4ee6a, 22eede5 |
| V6.4 | Đường cơ sở quyền & bảo mật (persist token / BCrypt / quyền API / bảo vệ đăng nhập) | V6.4 (107 dòng), 007a202 |
| V6.5 | Mobile uni-app (H5 + WeChat Mini Program) | V6.5 (105 dòng), 779ac41 |
| V6.6 | Gói năng lực tệp | 98f3c09 |
| V6.7 | Chấp nhận nhà máy test artifact (test_enabled ba trạng thái + khung H2) | V6.7 (134 dòng), 4e7d318 |
| V6.8 | Khả năng bảo trì artifact (dryRun / skip được bảo vệ / tham số hóa thương hiệu) | V6.8 (141 dòng), be6f858 |
| V6.9 | Trung tâm tài liệu nền tảng (cơ sở back-end, front-end hoàn thành cùng lô) | — |
| V6.10 | Khả năng quan sát nền tảng: Actuator + cửa sổ metric trong bộ nhớ + trang giám sát nhúng | — |
| V7.0 | Đề xuất trang & menu AI (xem trước / áp dụng) | V7.0 (94 dòng), 45f2670 |
| V7.1 | Tăng cường dữ liệu hạt giống bằng AI (AI nắm ngữ nghĩa, quy tắc nắm cấu trúc) | V7.1 (42 dòng), 5ca6e72 |
| V7.2 | Sản phẩm SQL được ghi vào `db/` sau khi pipeline thành công | eebff88 |
| **V7.3** | **Độ tin cậy giao hàng: test artifact đã biên dịch tự kiểm / đầu ra SQL thống nhất / đề xuất AI từng bảng** | **V7.3 (138 dòng)** |
| **V7.4** | Giao hàng có thể lặp lại: triển khai từ CSDL rỗng, mọi commit đảm bảo chạy được | c306e29 |
| **V7.5** | Cấu hình ngôn ngữ dự án: danh sách 206 ngôn ngữ / ngôn ngữ mặc định / các ngôn ngữ bật, chèn lang & dir vào mã được tạo | 7a9afa0 + abac56d |
| **V8.0** | i18n toàn trang: khung 206 ngôn ngữ (8 bản dịch chuyên nghiệp), i18n hai luồng với hợp đồng zero-diff khi OFF | 9e5dd57 → 7d57aea |
| **V8.1** | Tăng cường chất lượng: harness hợp đồng offline (9 chế độ, zero-diff OFF) + unit test i18n-core + cổng CI + phân tách README trung thực (khung 206 / đã dịch 8) + check-locale-keys.mjs | 93a12d5 + e49330b |
| **V9.0** | Artifact được tạo hiện đại hóa lên Vue 3 + Element Plus + Vite; đã xác minh lúc runtime (40/40 assertion, vite build thành công, 14 ảnh chụp incl. ar-SA RTL); sửa 3 lỗi runtime | 93a12d5 + e49330b  |
| **V9.1** | **Tái cấu trúc để dễ bảo trì · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9** | 654b8e0 → b0d081e  |
| **V9.2** | **Trung tâm kiểm thử & trung tâm xác minh · Test Center (7 tables: env/suite/case/dataset/run/result/schedule) + Verification Center (tc_run/tc_case_result/tc_artifact, 9 suites S1-S9, single-run 409 lock, `codegen.testcenter/v1`); 373/373 tests; 206/206 locales; output byte-identical** | V9.2 release  |
| **V9.3** | **Kiểm thử tự động sản phẩm được sinh ra · Generated-artifact automated testing: S10 (mvn test + Surefire), S11 (API / TestContainers), S12 (E2E / Playwright); GEN trigger + TC_GEN_ROOT; 12-step pipeline green; real artifact run PASSED 20/20, run/case/artifact persisted** | V9.3 release, **phiên bản hiện tại** |

---

> **V6.10 Khả năng quan sát nền tảng**: tích hợp Actuator + Micrometer, lấy mẫu mỗi 5 giây vào bộ đệm vòng trong bộ nhớ (cửa sổ 30 phút), cộng thêm trang "System Monitor" nhúng mới — API tổng hợp back-end `/monitor/*` + panel ECharts front-end. Chỉ các endpoint `health/info/metrics` được whitelist; `/actuator/health` không cần token trong khi `metrics` v.v. yêu cầu xác thực; `env/beans/heapdump/threaddump/loggers` không bao giờ được phơi bày. Cửa sổ lấy mẫu chỉ trong một tiến trình, mất khi khởi động lại, và không chia sẻ giữa các instance. Xem `docs/可观测性说明.md` (tiếng Trung) để biết chi tiết.

## 13. Trạng thái dự án

- Dòng phiên bản đã lên kế hoạch hiện tại đã đạt **V9.3** (năng lực tiếp tục lặp: mô hình hóa AI, tăng cường dữ liệu hạt giống, hệ thống gói năng lực,...).
- Dự án demo đi kèm `demo_admin` (back-end 8140 / front-end 9040) bao phủ các kịch bản cây / CRUD / cha-con / chỉ đọc / in, chạy độc lập để xác minh.
- Cả hai kho đều được host trên Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. Tác giả & Hỗ trợ

- **Tác giả**: một lập trình viên người Trung Quốc (nhà phát triển độc lập)
- **Email**: `hustxiayuhong@126.com`
- **Hỗ trợ kỹ thuật**: vui lòng viết email **bằng tiếng Trung**, bao gồm mô tả vấn đề, môi trường chạy (JDK / MySQL / phiên bản) và các bước tái hiện

> Tôi đọc và trả lời mọi email. Báo cáo lỗi, yêu cầu tính năng và thắc mắc hợp tác đều được chào đón.

---

## 15. So sánh với các công cụ tương tự

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

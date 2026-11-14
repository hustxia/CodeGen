# Majiang · ตัวสร้างโค้ด (CodeGen) — แนะนำโปรเจกต์

> Languages: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · **ไทย** · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> เครื่องมือสร้างโค้ดแบบหลายโปรเจกต์สำหรับ **Spring Boot + Vue**: เริ่มต้นจากโครงสร้างตารางฐานข้อมูล สร้างโค้ดธุรกิจฝั่งหน้าและหลังที่ **คอมไพล์ได้ รันได้ พร้อมเมนูและสิทธิ์** และจัดการวงจรส่งมอบทั้งหมด ได้แก่ เขียนลงดิสก์ สำรองข้อมูล จดบันทึก และย้อนกลับ (rollback)

GitHub: https://github.com/hustxia/CodeGen

## 1. นี่คืออะไร

CodeGen คือ "โรงงานโค้ด" ที่ขับเคลื่อนด้วยตาราง นำตารางธุรกิจของคุณเข้ามา (ผ่านการนำเข้าฐานข้อมูลหรือวาง DDL) กำหนดค่าฟิลด์และสวิตช์การสร้าง จากนั้นคลิก "Generate" — มันจะสร้าง Entity/Mapper/Service/Controller/DTO/VO ฝั่งหลัง `api.js` และหน้า Vue ฝั่งหน้า เมนูและเส้นทาง รวมถึงหน้าพิมพ์ A4 และโครงสร้างการทดสอบแบบบูรณาการ แล้วเขียนลงโปรเจกต์เป้าหมายอย่างปลอดภัย

มันไม่ได้แค่ "คายโค้ดออกมา" — แต่เป็น **ท่อส่งมอบที่มีประตูคุณภาพและความสามารถในการย้อนกลับ**:

```
Table intake (DB import / DDL)
   → Field & switch configuration (with AI recommendations)
      → One-click generation: self-check → backup → write to disk → compile/validate → register menu → rebuild routes
         → Ledger bookkeeping → rollback-capable
```

---

## 2. ผู้ใช้คือใคร และจะใช้งานอย่างไร

| ผู้ใช้ | ความต้องการ typical | เส้นทางที่แพลตฟอร์มให้ |
| --- | --- | --- |
| นักพัฒนาแบ็กเอนด์ | มีตารางจำนวนหนึ่ง (หรือ DDL) ต้องการรัน CRUD อย่างรวดเร็ว | หน้าตั้งค่าตาราง: นำเข้า → กำหนดฟิลด์ → สร้างในคลิกเดียว |
| นักพัฒนา Full-stack / อิสระ | ต้องการสร้างระบบสมบูรณ์จากศูนย์ (รวมเข้าสู่ระบบ สิทธิ์ พจนานุกรม) | หน้าท่อ: สร้างระบบด้วยประโยคเดียว |
| หัวหน้าทีม | ต้องการรวมสไตล์โค้ด กฎโค้ด และโครงสร้างไดเรกทอรีให้เป็นหนึ่ง | การจัดการระบบ: หนึ่งระบบ = หนึ่งชุดการตั้งค่าผลลัพธ์ มีผลบังคับใช้แบบกลุ่ม |
| ผู้ดูแลแพลตฟอร์ม | ต้องการแก้ไขเทมเพลตโดยไม่แก้โค้ด | เทมเพลต: แก้ไขออนไลน์ มีผลทันทีที่บันทึก รองรับการย้อนประวัติ |
---

## 3. ปัญหาที่มันแก้ไข

| จุดเจ็บปวด | สิ่งที่ CodeGen ทำ |
| --- | --- |
| CRUD ซ้ำซาก — โค้ดแม่แบบเต็มไปหมดสำหรับทุกตาราง | สวิตช์รายตารางสร้างหน้า-หลังเต็มรูปแบบในคลิกเดียว รวมถึง CRUD การแบ่งหน้า การตรวจสอบ การนำเข้า/ส่งออก |
| ฟิลด์และ API หน้า-หลังไม่ตรงกัน ทำให้แก้บั๊กการเชื่อมต่อไม่รู้จบ | ข้อมูลเมตาของตารางเดียวกันขับเคลื่อนทั้ง API หลังบ้านและหน้าบ้าน — ไม่มีความคลาดเคลื่อนทางความหมาย |
| กฎหมายทีมยากที่จะรวมเป็นหนึ่ง สไตล์แต่ละคนต่างกัน | เทมเพลตคือกฎหมาย: เทมเพลต FreeMarker 22 อันจัดการแบบรวมศูนย์ แก้ไขออนไลน์พร้อมย้อนเวอร์ชัน |
| ตัวสร้าง "เขียนทับแล้วหาย" ตรรกะที่เขียนด้วยมือ | กลไกผสานบล็อกเครื่องหมาย: รักษาโค้ดที่เขียนมือไว้ แทนที่เฉพาะส่วนที่สร้างเท่านั้น |
| โค้ดที่สร้างยังไม่น่าเชื่อถือจนกว่าจะคอมไพล์ | ตรวจสอบตนเองคอมไพล์ในหน่วยความจำ (`javac` จริง) ตรวจสอบสัญญาโครงสร้าง ดูตัวอย่าง diff บนดิสก์ — ไม่คอมไพล์ก็ไม่ลงดิสก์ |
| สร้างระบบใหม่จากศูนย์ใช้เวลานาน | ท่อ "สร้างระบบด้วยประโยคเดียว": สร้าง DB → scaffold → สร้าง → แพ็กเกจ → ลงทะเบียนเมนู อัตโนมัติเต็มรูปแบบ |

---

## 4. ภาพรวมความสามารถ

- **การรับตาราง**: แยกวิเคราะห์ DDL นำเข้าฐานข้อมูลโดยตรง ตัวช่วยนำเข้าแบบกลุ่ม อนุมานการตั้งค่าคอลัมน์อัตโนมัติ (ควบคุม / ตรวจสอบ / พจนานุกรม / ความสัมพันธ์)
- **รูปแบบตาราง**: ตารางเดี่ยว ตารางแบบต้นไม้ (API ต้นไม้ + กริดต้นไม้) ตารางแม่-ลูก (บันทึกธุรกรรมเดียว + แท็บรายละเอียด) ตารางเชื่อมโยงหลายต่อหลาย
- **ผลลัพธ์ที่สร้าง**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + หน้า Vue + หน้าพิมพ์ A4 + โครงสร้างการทดสอบแบบบูรณาการ
- **การเสริมธุรกิจ**: ดรอปดาวน์ความสัมพันธ์ แปลพจนานุกรม ปกปิดข้อมูล ตรวจสอบความ unique เชื่อมโยงฟอร์ม สิทธิ์ขอบเขตข้อมูล นำเข้า/ส่งออก Excel กระบวนการอนุมัติ แพ็กเกจความสามารถชำระเงิน / ไฟล์ / มือถือ
- **ประตูคุณภาพ**: ตรวจสอบตนเองคอมไพล์ในหน่วยความจำ (`javac` จริง) ตรวจสอบสัญญาโครงสร้าง ตรวจสอบความสอดคล้องของสิทธิ์ ดูตัวอย่าง diff บนดิสก์ ตรวจสอบผลลัพธ์ฝั่งหน้าจริง
- **ลงจอดอย่างปลอดภัย**: เขียนลงดิสก์ + สำรองแบบกลุ่ม `.gen-backup` ผสานบล็อกเครื่องหมายเพื่อรักษาโค้ดที่เขียนมือ บันทึก `gen_output` สี่สถะ (สร้าง / ผสาน / ข้าม / ล้มเหลว) ย้อนกลับประวัติ
- **แยกโดเมนหลายระบบ**: แต่ละโปรเจกต์เป้าหมายคือ "การตั้งค่าระบบ" (เส้นทางหน้า/หลัง ชื่อแพ็กเกจ คำนำหน้าเกตเวย์ classpath ของการพึ่งพา ค่าดั้งเดิมสวิตช์สร้าง) — รองรับหลายโปรเจกต์ขนานกัน
- **การเสริม AI**: สร้างระบบด้วยประโยคเดียว (สร้างโมเดล AI) เสริมข้อมูลเมล็ดพันธุ์ด้วย AI คำแนะนำการตั้งค่ารายตารางโดย AI (ดูตัวอย่าง diff + ติ๊กเพื่อใช้)

---

## 5. กรณีใช้งานทั่วไป

| สถานการณ์ | คำอธิบาย |
| --- | --- |
| ตารางต้นไม้ | ข้อมูลลำดับชั้นเช่น หมวดหมู่สินค้า — สร้าง API ต้นไม้ กริดต้นไม้ และดรอปดาวน์เลือกผู้ปกครองอัตโนมัติ |
| CRUD ตารางเดี่ยว | ลูกค้า สินค้า ฯลฯ — ดรอปดาวน์ FK + แปลพจนานุกรม + นำเข้า/ส่งออก พร้อมใช้งาน |
| แม่-ลูก | ออเดอร์ขาย + รายการออเดอร์ — บันทึกธุรกรรมเดียว ลิ้นชักรายละเอียดสองแท็บ (ข้อมูลหลัก / รายละเอียด) |
| อ่านอย่างเดียว / สถิติ | กระแสสต็อก ฯลฯ — ปิดสวิตช์เขียน ค้นหาและส่งออกเท่านั้น |
| พิมพ์เอกสาร | ติ๊กสวิตช์ "print" เพื่อสร้างหน้าพิมพ์ A4 และเส้นทาง พร้อมส่วนหัว + ตารางรายละเอียด |
| สร้างระบบประโยคเดียว | อธิบายโดเมนธุรกิจ — AI สร้าง DB และตาราง + scaffold + สร้างเต็ม + ลงทะเบียนเมนูในครั้งเดียว |

> ทุกสถานการณ์มีเดโมที่รันได้ (โปรเจกต์ `demo_admin`) พร้อมภาพหน้าจอ — ดู `docs/典型场景操作指南-演示后台demo.md` (จีน)

---

## 6. กระบวนการทำงานหลัก

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Table intake │ → │ Field/switch conf. │ → │ One-click generate (self-check→backup→write→     │ → │ Ledger · Rollback │
│              │   │                    │   │ compile→menu→route)                              │   │                   │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     controls/validation     Lands on disk only after all quality gates pass;      Batch-level traceability
                    /dictionaries           hand-written code is preserved
```

- **ดูตัวอย่าง diff**: ผลลัพธ์ที่สร้างจะถูกเปรียบเทียบรายไฟล์กับสถานะบนดิสก์ปัจจุบัน คุณยืนยันก่อนเขียน เพื่อหลีกเลี่ยงการเขียนทับโดยไม่ได้ตั้งใจ
- **บันทึกลงบัญชีการสร้าง**: ทุกไฟล์บันทึกการกระทำ จำนวนไบต์ MD5 และตำแหน่งสำรอง สามารถย้อนกลับทั้งล็อตได้

---

## 7. สร้างระบบด้วยประโยคเดียว (Pipeline)

ความสามารถระดับสูงที่มุ่งสู่ "สร้างแบ็กเอนด์จากศูนย์" หลังจากคุณอธิบายโดเมนธุรกิจ ท่อจะดำเนินไปอัตโนมัติผ่าน 5 ขั้นตอน:

| ขั้นตอน | ความหมาย |
| --- | --- |
| `CHECK` | ตรวจสอบการตั้งค่าโปรเจกต์เป้าหมายและ classpath ของการพึ่งพา |
| `WRITE` | สร้างฐานข้อมูลและตาราง เขียน scaffold และผลลัพธ์ที่สร้าง |
| `COMPILE` | แบ็กเอนด์ `mvn package` / สร้างฝั่งหน้า ตรวจสอบตนเองคอมไพล์ในหน่วยความจำ |
| `MENU` | สร้างและลงทะเบียนสิทธิ์เมนู (สองทางออก: SQL ผลลัพธ์ / เขียนโดยตรงภายใน) |
| `ROUTE` | สร้างเศษเส้นทางฝั่งหน้าใหม่ |

ขั้นตอนใดล้มเหลวจะหยุดทันทีพร้อมข้อผิดพลาดที่อ่านง่าย เพื่อหาตำแหน่งได้ง่าย

---

## 8. สถาปัตยกรรมระบบ

แยกหน้า-หลัง สองคลังทำงานร่วมกัน:

| คลัง | บทบาท | Stack |
| --- | --- | --- |
| `codegen-server` | แบ็กเอนด์: เอนจินสร้าง เทมเพลต การตรวจสอบ บัญชี ท่อ | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | ฝั่งหน้า: คอนโซล (ตั้งค่าตาราง / เทมเพลต / บัญชี / จัดการระบบ) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (โหมด history) |

การแบ่งเลเยอร์แบ็กเอนด์:

```
controller/    API layer (Gen* generation chain / Sys* system management / pipeline)
service/impl/  Core implementations (generation chain, template engine, merge, validation, pipeline execution)
util/          Template engine, model assembly, marker-block merge, self-check compilation
config/        Output config, self-check config, template seeder
common/        Result / BaseEntity / exceptions / audit
resources/gen-templates/   22 FreeMarker templates (backend / frontend / shared / test groups)
```

**ความสามารถพื้นฐานที่โปรเจกต์เป้าหมายต้องมี** (ตัวสร้างสร้างเฉพาะโค้ดธุรกิจ ถ้าไม่มีพื้นฐานจะล้มเหลวแบบเงียบ):

1. ปลั๊กอินแบ่งหน้า MyBatis-Plus (`PaginationInnerInterceptor`)
2. คลาสพื้นฐานเช่น `Result` / `BaseEntity` / `PageQuery` (คำนำหน้าแพ็กเกจต้องตรงกับ `root-package`)
3. `utils/request.js` ฝั่งหน้าที่มี `toPageData` (แปลง `Page{records,total}` เป็น `{list,total}`)
4. การพึ่งพา `springdoc-openapi-starter-webmvc-ui` (โค้ดที่สร้างใช้ `@Operation`) และ `poi-ooxml` (Excel)

---

## 9. ภาพรวมเทคโนโลยี

| มิติ | การเลือก |
| --- | --- |
| ภาษา / เฟรมเวิร์กแบ็กเอนด์ | Java 21 / Spring Boot 3.2.5 |
| Persistence | MyBatis-Plus 3.5.5 |
| Template engine | FreeMarker (เทมเพลตเมล็ดพันธุ์ 22 อัน แก้ไขออนไลน์ + ย้อนเวอร์ชัน) |
| ความปลอดภัย | ยืนยันตัวตน JWT |
| ฐานข้อมูล | MySQL 8 |
| ฝั่งหน้า (generated projects) | Vue 3 + Element Plus + Vite |
| ฝั่งหน้า (platform console) | Vue 2.7 + Element UI |
| Local AI | qwen2.5-coder:7b (สร้างโมเดล / คำแนะนำตั้งค่า / เสริมข้อมูลเมล็ดพันธุ์) |
| API docs | springdoc-openapi (Swagger UI) |

---

## 10. เริ่มต้นอย่างรวดเร็ว

### 1. สร้างฐานข้อมูล (ตารางแพลตฟอร์ม 13 ตาราง + บัญชีเริ่มต้น admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. กำหนดค่าโปรเจกต์เป้าหมาย

คัดลอกเทมเพลตการตั้งค่าและปรับเป็นเส้นทาง local ของคุณ:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` ถูกยกเว้นโดย `.gitignore` และจะไม่ถูกคอมมิต การตั้งค่าหลัก:

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

ส่งออกไดเรกทอรีการพึ่งพาสำหรับตรวจสอบตนเองดังนี้:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. เริ่มแบ็กเอนด์

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

บริการใช้พอร์ตเริ่มต้น **8120**; เอกสาร API ที่ `/swagger-ui/index.html`

### 4. เริ่มฝั่งหน้า

```bash
cd ../codegen-web
npm install
npm run serve
```

ฝั่งหน้าใช้พอร์ตเริ่มต้น **9530** ส่งผ่าน `/dev-api` ไปยังพอร์ต 8120 ของแบ็กเอนด์ บัญชีเริ่มต้น `admin / admin123`

### 5. รายการกำหนดค่าหลัก

| การตั้งค่า | คำอธิบาย |
| --- | --- |
| `gen.output.*` | เส้นทางเขียนผลลัพธ์ ชื่อแพ็กเกจ การจับคู่โมดูล |
| `gen.selfcheck.classpath` | classpath ของการพึ่งพาสำหรับตรวจสอบตนเองคอมไพล์ในหน่วยความจำ รองรับ `dir/*` |
| `gen.menu.outlet` | `artifact` สร้างเฉพาะ SQL เมนูและเศษเส้นทาง; `internal` เขียน `sys_menu` ของแพลตฟอร์มด้วย |

---

## 11. แผนที่เอกสาร

| เอกสาร | เนื้อหา |
| --- | --- |
| `docs/代码生成器-操作手册.md` (จีน) | การ deploy เริ่มต้น 5 นาที ตั้งค่าตาราง นำเข้าแบบกลุ่ม จัดการเทมเพลต บัญชี แคตตาล็อก API ท่อเต็ม (มีภาพ) |
| `docs/典型场景操作指南-演示后台demo.md` (จีน) | ตาราง 6 ตารางครอบคลุม 5 สถานการณ์ typical พร้อมเดโมรันได้และภาพหน้าจอจริง |
| `docs/项目介绍.md` | เอกสารแนะนำโปรเจกต์ แหล่งเดียวกับไฟล์นี้ |
| `docs/代码生成器-Vx.x-规划.md` | แผนเวอร์ชันและรายการฟีเจอร์จาก V3.1 → V7.3 |
| `codegen-web/README.md` | อ้างอิงเริ่มต้นด่วนคอนโซลฝั่งหน้า |

---

## 12. ประวัติเวอร์ชัน

| เวอร์ชัน | ธีม / เอกสารแผน | Commit ที่ลงจอด |
| --- | --- | --- |
| V1.2 / V1.3 | สร้างคลิกเดียว / แบบกลุ่ม / ย้อนกลับ; ดูตัวอย่าง diff & sync โครงสร้าง | — (มีเฉพาะในคอมเมนต์ `src/api/gen.js`) |
| V2.0 | เส้นทางแบบไดนามิก (ต้นไม้เมนูจากแบ็กเอนด์) | — |
| V3.0 / V3.1 | บัญชีแบบหลายระบบ; วงจรหลายระบบ + การทดสอบอัตโนมัติแรก | V3.1 (174 บรรทัด) |
| V4.2 | ผลลัพธ์การสร้างที่ตรวจสอบย้อนกลับได้ (เขียนบัญชี) | V4.2 (292 บรรทัด) |
| V4.3 | การถดถอยเทมเพลตและแผงกันคุณภาพ | V4.3 (200 บรรทัด) |
| V4.4 | แม่หนึ่งลูกหลาย ความสมบูรณ์การเขียน cascade เลนเทมเพลต | V4.4 (154 บรรทัด) |
| V4.5 | เอกสารพิมพ์ A4 (ไม่พึ่งพาบุคคลที่สาม) | V4.5 (127 บรรทัด) |
| V5.0 | สร้างระบบประโยคเดียว: ท่อ + สร้างโมเดล AI + ฐาน scaffold | — |
| V5.1 | เติมคอลัมน์ audit DDL อัตโนมัติ | V5.1 (100 บรรทัด) |
| V5.2 | ขั้นตอนเริ่มต้นข้อมูลเมล็ดพันธุ์ของท่อ | V5.2 (107 บรรทัด), 84ec8dd |
| V5.5 | สรุปการสร้างโปรเจกต์หลายโมดูล | af73dd3 |
| V6.0 | โครงสร้างพื้นฐานไมโครเซอร์วิส (Nacos / Gateway / Feign โหมดที่สาม "micro") | V6.0 (111 บรรทัด), 1deecbd |
| V6.1 | เอนจินคู่กระบวนการ Activiti | V6.1 (100 บรรทัด), 02b671e |
| V6.2 | ผิวการส่งมอบ artifact (CLI / artifact deploy / ความปลอดภัยการตั้งค่า) | V6.2 (114 บรรทัด), 02b671e |
| V6.3 | แพ็กเกจความสามารถชำระเงิน (WeChat / Alipay + Mock) | V6.3 (104 บรรทัด), 1c4ee6a, 22eede5 |
| V6.4 | เส้นฐานสิทธิ์และความปลอดภัย (persist token / BCrypt / สิทธิ์ API / ป้องกันล็อกอิน) | V6.4 (107 บรรทัด), 007a202 |
| V6.5 | Mobile uni-app (H5 + WeChat Mini Program) | V6.5 (105 บรรทัด), 779ac41 |
| V6.6 | แพ็กเกจความสามารถไฟล์ | 98f3c09 |
| V6.7 | ยอมรับโรงงานทดสอบ artifact (test_enabled สามสถะ + โครงสร้าง H2) | V6.7 (134 บรรทัด), 4e7d318 |
| V6.8 | ความสามารถบำรุงรักษา artifact (dryRun / ข้ามที่ป้องกัน / parametric แบรนด์) | V6.8 (141 บรรทัด), be6f858 |
| V6.9 | ศูนย์เอกสารแพลตฟอร์ม (ฐานแบ็กเอนด์ ฝั่งหน้าทำเสร็จในล็อตเดียวกัน) | — |
| V6.10 | ความสามารถสังเกตได้ของแพลตฟอร์ม: Actuator + หน้าต่าง metric ในหน่วยความจำ + หน้าจอมอนิเตอร์แบบฝัง | — |
| V7.0 | คำแนะนำหน้าและเมนู AI (ดูตัวอย่าง / ใช้) | V7.0 (94 บรรทัด), 45f2670 |
| V7.1 | เสริมข้อมูลเมล็ดพันธุ์ด้วย AI (AI ถือความหมาย กฎถือโครงสร้าง) | V7.1 (42 บรรทัด), 5ca6e72 |
| V7.2 | ผลลัพธ์ SQL ถูกเขียนลง `db/` หลังท่อสำเร็จ | eebff88 |
| **V7.3** | **ความน่าเชื่อถือการส่งมอบ: ทดสอบ artifact ที่คอมไพล์แล้วแบบตรวจสอบตนเอง / ทางออก SQL แบบรวม / คำแนะนำ AI รายตาราง** | **V7.3 (138 บรรทัด)** |
| **V7.4** | ส่งมอบที่ทำซ้ำได้: deploy จากฐานข้อมูลว่างได้ ทุก commit การันตีว่ารันได้ | c306e29 |
| **V7.5** | การตั้งค่าภาษาของโปรเจกต์: รายการ 206 ภาษา / ภาษาเริ่มต้น / ภาษาที่เปิดใช้, ฉีด lang และ dir ลงในโค้ดที่สร้าง | 7a9afa0 + abac56d |
| **V8.0** | i18n ทั้งหน้า: เฟรมเวิร์ก 206 ภาษา (แปลมืออาชีพ 8 ภาษา), i18n สองแทร็กพร้อมสัญญา zero-diff เมื่อ OFF | 9e5dd57 → 7d57aea |
| **V8.1** | เสริมความแข็งแกร่งด้านคุณภาพ: offline contract harness (9 โหมด, OFF zero-diff) + unit test i18n-core + CI gate + แบ่ง README อย่างตรงไปตรงมา (เฟรมเวิร์ก 206 / แปลแล้ว 8) + check-locale-keys.mjs | 93a12d5 + e49330b |
| **V9.0** | สิ่งที่สร้างได้รับการปรับให้ทันสมัยเป็น Vue 3 + Element Plus + Vite; ตรวจสอบขณะรันจริง (40/40 assertions, vite build สำเร็จ, ภาพหน้าจอ 14 รูป รวม ar-SA RTL); แก้ไขข้อบกพร่อง runtime 3 จุด | 93a12d5 + e49330b  |
| **V9.1** | **การปรับโครงสร้างเพื่อความสามารถในการดูแลรักษา · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9** | 654b8e0 → b0d081e  |
| **V9.2** | **ศูนย์ทดสอบและศูนย์ตรวจสอบ · Test Center (7 tables: env/suite/case/dataset/run/result/schedule) + Verification Center (tc_run/tc_case_result/tc_artifact, 9 suites S1-S9, single-run 409 lock, `codegen.testcenter/v1`); 373/373 tests; 206/206 locales; output byte-identical** | V9.2 release, **เวอร์ชันปัจจุบัน** |

---

> **V6.10 ความสามารถสังเกตได้ของแพลตฟอร์ม**: รวม Actuator + Micrometer ตัวอย่างทุก 5 วินาทีลงบัฟเฟอร์วงแหวนในหน่วยความจำ (หน้าต่าง 30 นาที) พร้อมหน้า "System Monitor" แบบฝังใหม่ — API รวมฝั่งหลัง `/monitor/*` + แผง ECharts ฝั่งหน้า เปิดเฉพาะ endpoint `health/info/metrics`; `/actuator/health` ไม่ต้องใช้ token ในขณะที่ `metrics` ฯลฯ ต้องตรวจสอบสิทธิ์; `env/beans/heapdump/threaddump/loggers` ไม่ถูกเปิดเผยอย่างเด็ดขาด หน้าต่างตัวอย่างมีเฉพาะต่อโปรเซส หายเมื่อรีสตาร์ท และไม่แชร์ข้าม instance ดู `docs/可观测性说明.md` (จีน) สำหรับรายละเอียด

## 13. สถานะโปรเจกต์

- สายเวอร์ชันที่วางแผนปัจจุบันถึง **V9.2** แล้ว (ความสามารถยังคงiterate: สร้างโมเดล AI เสริมข้อมูลเมล็ดพันธุ์ ระบบแพ็กเกจความสามารถ ฯลฯ)
- โปรเจกต์เดโมตัวอย่าง `demo_admin` (แบ็กเอนด์ 8140 / ฝั่งหน้า 9040) ครอบคลุมสถานการณ์ต้นไม้ / CRUD / แม่-ลูก / อ่านอย่างเดียว / พิมพ์ และรันได้เองเพื่อตรวจสอบ
- ทั้งสองคลังโฮสต์บน Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. ผู้แต่งและการสนับสนุน

- **ผู้แต่ง**: โปรแกรมเมอร์ชาวจีน (นักพัฒนาอิสระ)
- **อีเมล**: `hustxiayuhong@126.com`
- **การสนับสนุนทางเทคนิค**: โปรดเขียนอีเมล **เป็นภาษาจีน** รวมถึงคำอธิบายปัญหา สภาพแวดล้อมรัน (JDK / MySQL / เวอร์ชัน) และขั้นตอนการจำลอง

> ผู้อ่านและตอบทุกอีเมล รายงานบั๊ก คำขอฟีเจอร์ และการติดต่อร่วมมือ ยินดีต้อนรับทั้งหมด

---

## 15. การเปรียบเทียบกับเครื่องมือที่คล้ายคลึงกัน

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

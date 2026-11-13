# 마장(码匠) · 코드 생성기(CodeGen) 프로젝트 소개

> Languages: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · **한국어** · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

> 언어: [中文](README_ZH.md) · [English](README.md) · [日本語](README_JA.md) · [Português](README_PT.md) · [Español](README_ES.md) · **한국어** · [Français](README_FR.md) · [Deutsch](README_DE.md) · [हिन्दी](README_HI.md) · [العربية](README_AR.md) · [বাংলা](README_BN.md) · [Русский](README_RU.md) · [اردو](README_UR.md)

> **Spring Boot + Vue** 대상 멀티 프로젝트 코드 생성기: 데이터베이스 테이블 구조에서 출발하여 **컴파일 가능하고 실행 가능하며 메뉴·권한까지 갖춘** 프론트엔드/백엔드 비즈니스 코드를 생성하고, 디스크 기록, 백업, 대장(台账) 기록, 롤백까지 아우르는 완전한 전달 루프를 책임집니다.

---
GitHub 주소: https://github.com/hustxia/CodeGen


## 1. 이것은 무엇인가

CodeGen는 "테이블을 원천으로 하는" 코드 팩토리입니다. 비즈니스 테이블만 가져오면(DB 연결 임포트 또는 DDL 붙여넣기) 필드와 생성 스위치를 설정하고 "생성"을 클릭하는 것만으로 백엔드 Entity/Mapper/Service/Controller/DTO/VO, 프론트엔드 `api.js`와 Vue 페이지, 메뉴와 라우팅, 심지어 A4 인쇄 페이지와 통합 테스트 스켈레톤까지 모두 생성해 주고, 안전하게 대상 프로젝트에 기록합니다.

단순히 "코드를 뱉는" 것이 아니라 **품질 게이트와 롤백 능력을 갖춘 전달 파이프라인**입니다:

```
테이블 연결 (DB 임포트 / DDL)
   → 필드 및 생성 스위치 설정 (AI 추천 포함)
      → 원클릭 생성: 자가 검증 → 백업 → 디스크 기록 → 컴파일/검증 → 메뉴 등록 → 라우팅 재구축
         → 대장 기록 → 롤백 가능
```

---

## 2. 사용자는 누구이며 어떻게 쓰는가

| 사용자 | 전형적인 요구 | 플랫폼이 제공하는 경로 |
| --- | --- | --- |
| 백엔드 개발자 | 손에 여러 테이블(또는 DDL)이 있고 CRUD를 빠르게 띄우고 싶음 | 테이블 설정 페이지: 임포트 → 필드 설정 → 원클릭 생성 |
| 풀스택／독립 개발자 | 제로부터 완전한 시스템(로그인·권한·사전 포함)을 구축하고 싶음 | 파이프라인 페이지: 한 문장으로 시스템 구축 |
| 팀 책임자 | 코드 스타일, 코딩 규약, 디렉터리 구조를 통일하고 싶음 | 시스템 관리: 1시스템 = 1출력 설정, 일괄 적용 |
| 플랫폼 관리자 | 코드를 바꾸지 않고 템플릿을 변경하고 싶음 | 템플릿 관리: 온라인 편집, 저장 즉시 적용, 이력 롤백 지원 |
## 3. 어떤 문제를 해결하는가

| 고통 포인트 | CodeGen의 접근 |
| --- | --- |
| 반복되는 CRUD 지옥 — 테이블마다 대량의 보일러플레이트를 손으로 작성해야 함 | 테이블 단위 스위치로 CRUD, 페이징, 검증, 임포트/익스포트를 포함한 완전한 프론트/백엔드를 한 번에 산출 |
| 프론트/백엔드 필드·인터페이스 불일치로 연동 수정 반복 | 동일한 테이블 메타데이터가 백엔드 API와 프론트엔드 페이지를 동시에 구동하여 필드 의미 편차 0 |
| 팀 코딩 규약 통일이 어렵고 사람마다 스타일이 제각각 | 템플릿이 곧 규약 — 22개 FreeMarker 템플릿 중앙 관리, 온라인 편집 및 버전 롤백 지원 |
| 생성기의 "덮어쓰면 소실" — 손으로 작성한 로직이 지워짐 | 마커 블록(marker block) 병합 메커니즘: 손작성 코드는 보존되고 생성 영역만 교체 |
| 생성물을 신뢰할 수 없어 컴파일해야 문제 발견 | 메모리 컴파일 자가 검증(실제 `javac`), 구조 계약 검사, 디스크 diff 미리보기 — 컴파일 실패 시 디스크 기록 금지 |
| 신규 시스템을 0부터 스캐폴딩하는 데 시간 소모 | 파이프라인 "한 문장으로 시스템 구축": DB 생성 → 스캐폴드 → 생성 → 패키징 → 메뉴 등록 전 과정 자동화 |

---

## 4. 핵심 능력 전경

- **테이블 연결**: DDL 파싱, DB 테이블 직접 임포트, 일괄 인수 마법사, 컬럼 설정 자동 도출(컨트롤 / 검증 / 사전(딕셔너리) / 연관).
- **테이블 형태**: 단일 테이블, 트리 테이블(tree API + 트리 그리드), 마스터-디테일(동일 트랜잭션 저장 + 상세 탭), 다대다 중간 테이블.
- **생성 산출물**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue 페이지 + A4 인쇄 페이지 + 통합 테스트 스켈레톤.
- **비즈니스 강화**: 연관 드롭다운, 사전(딕셔너리) 번역, 마스킹, 유일성 검증, 폼 연동, 데이터 권한, Excel 임포트/익스포트, 승인 워크플로, 결제 / 파일 / 모바일 기능 팩.
- **품질 게이트**: 메모리 컴파일 자가 검증(실제 `javac`), 구조 계약 검사, 권한 일관성 검사, 디스크 diff 미리보기, 프론트엔드 산출물 실검증.
- **안전한 적용**: 디스크 기록 + `.gen-backup` 배치 백업, 마커 블록 병합으로 손작성 코드 보존, `gen_output` 4상태 대장(신규 / 병합 / 건너뜀 / 실패), 이력 롤백.
- **멀티 시스템 격리**: 대상 프로젝트마다 하나의 "시스템 설정"(프론트/백엔드 경로, 패키지명, 게이트웨이 프리픽스, 의존성 classpath, 생성 스위치 기본값)으로 구성되어 여러 프로젝트를 병행 지원.
- **AI 강화**: 한 문장으로 시스템 구축(AI 모델링), 시드 데이터 AI 강화, 테이블 단위 AI 추천 설정(diff 미리보기 + 체크 적용).

---

## 5. 대표 시나리오

| 시나리오 | 설명 |
| --- | --- |
| 트리 테이블 | 상품 분류 등 계층형 데이터 — tree API, 트리 그리드, 상위 선택 드롭다운 자동 산출 |
| 단일 테이블 CRUD | 고객, 상품 등 — 외래키 드롭다운 + 사전 번역 + 임포트/익스포트 기본 제공 |
| 마스터-디테일 | 판매 주문 + 주문 상세 — 동일 트랜잭션 저장, 상세 드로어 듀얼 탭(마스터 정보 / 상세) |
| 읽기 전용 / 통계 | 재고 흐름 등 — 추가·수정·삭제 스위치를 끄고 조회와 익스포트만 유지 |
| 인쇄 문서 | "인쇄" 스위치를 체크하면 A4 인쇄 페이지와 라우트 산출, 헤더 + 상세 테이블 지원 |
| 한 문장으로 시스템 구축 | 비즈니스 도메인을 기술하면 AI가 DB·테이블 생성 + 스캐폴드 + 전량 생성 + 메뉴 등록을 한 번에 완수 |

> 위 시나리오는 모두 실행 가능한 데모(`demo_admin` 프로젝트)와 스크린샷이 준비되어 있습니다. 자세한 내용은 `docs/典型场景操作指南-演示后台demo.md`(중국어)를 참고하세요.

---

## 6. 핵심 워크플로

```
┌──────────┐   ┌──────────────┐   ┌───────────────────────────────────────┐
│ 테이블 연결 │ → │ 필드/스위치 설정 │ → │ 원클릭 생성 (자가검증→백업→기록→컴파일→메뉴→라우팅)│ → │ 대장 · 롤백 │
└──────────┘   └──────────────┘   └───────────────────────────────────────┘
  DB 임포트/DDL     컨트롤/검증/사전        모든 품질 게이트 통과 시에만 기록, 손작성 코드 보존          배치 단위 추적
```

- **차이 미리보기**: 생성물과 디스크 현황을 파일 단위로 diff 하여, 확인 후 기록함으로써 실수로 인한 덮어쓰기를 방지합니다.
- **생성 대장**: 파일마다 동작, 바이트 수, MD5, 백업 위치를 기록하며, 배치 전체를 롤백할 수 있습니다.

---

## 7. 한 문장으로 시스템 구축 (Pipeline)

"0부터 백엔드를 구축"하기 위한 고급 기능입니다. 비즈니스 도메인을 기술하면 파이프라인이 5단계를 자동으로 진행합니다:

| 단계 | 의미 |
| --- | --- |
| `CHECK` | 대상 프로젝트 설정과 의존성 classpath 검증 |
| `WRITE` | DB·테이블 생성, 스캐폴드 및 생성물 기록 |
| `COMPILE` | 백엔드 `mvn package` / 프론트엔드 빌드, 메모리 컴파일 자가 검증 |
| `MENU` | 메뉴 권한 산출 및 등록 (듀얼 아울렛: artifact SQL / internal 직접 기록) |
| `ROUTE` | 프론트엔드 라우트 조각 재구축 |

어느 단계에서든 실패하면 즉시 중단되고 이해하기 쉬운 오류를 제공하여 원인 파악이 용이합니다.

---

## 8. 시스템 아키텍처

프론트/백엔드 분리 구조이며, 두 리포지토리가 협업합니다:

| 리포지토리 | 역할 | 기술 스택 |
| --- | --- | --- |
| `codegen-server` | 백엔드: 생성 엔진, 템플릿, 검증, 대장, 파이프라인 | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | 프론트엔드: 콘솔(테이블 설정 / 템플릿 / 대장 / 시스템 관리) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router(history 모드) |

백엔드 레이어 구성:

```
controller/   인터페이스 계층 (Gen* 생성 체인 / Sys* 시스템 관리 / 파이프라인)
service/impl/ 핵심 구현 (생성 체인, 템플릿 엔진, 병합, 검증, 파이프라인 실행)
util/         템플릿 엔진, 모델 조립, 마커 블록 병합, 자가검증 컴파일
config/       출력 설정, 자가검증 설정, 템플릿 시더
common/       Result / BaseEntity / 예외 / 감사(audit)
resources/gen-templates/   22개 FreeMarker 템플릿 (backend / frontend / shared / test 그룹)
```

**대상 프로젝트가 반드시 갖추어야 할 기반(base) 능력** (생성기는 비즈니스 코드만 산출하며, 기반이 없으면 조용히 오동작합니다):

1. MyBatis-Plus 페이징 플러그인 (`PaginationInnerInterceptor`)
2. `Result` / `BaseEntity` / `PageQuery` 등 기반 클래스 (패키지 프리픽스가 `root-package`와 일치해야 함)
3. 프론트엔드 `utils/request.js`의 `toPageData` (`Page{records,total}`를 `{list,total}`로 변환)
4. 의존성 `springdoc-openapi-starter-webmvc-ui` (생성물이 `@Operation` 사용) 및 `poi-ooxml` (Excel)

---

## 9. 기술 스택 한눈에 보기

| 차원 | 선택 |
| --- | --- |
| 백엔드 언어 / 프레임워크 | Java 21 / Spring Boot 3.2.5 |
| 퍼시스턴스 계층 | MyBatis-Plus 3.5.5 |
| 템플릿 엔진 | FreeMarker (22개 시드 템플릿, 온라인 편집 + 버전 롤백 지원) |
| 보안 | JWT 인증 |
| 데이터베이스 | MySQL 8 |
| 프론트엔드 (generated projects) | Vue 3 + Element Plus + Vite |
| 프론트엔드 (platform console) | Vue 2.7 + Element UI |
| 로컬 AI | qwen2.5-coder:7b (모델링 / 추천 설정 / 시드 데이터 강화) |
| API 문서 | springdoc-openapi (Swagger UI) |

---

## 10. 빠른 시작

### 1. DB 생성 (13개 플랫폼 테이블 + 기본 계정 admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. 대상 프로젝트 설정

설정 템플릿을 복사하고 본인 환경의 경로에 맞게 수정합니다:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml`은 `.gitignore`에 의해 제외되어 커밋되지 않습니다. 주요 설정 항목:

```yaml
gen:
  output:
    backend-root: /path/to/your-target-project      # 생성물 기록 기준 경로
    frontend-root: /path/to/your-target-project
    frontend-path: demo-web                          # monorepo일 때 프론트엔드 하위 디렉터리
    backend-path: demo-server/src/main/java/com/demo
    package-name: com.demo
    root-package: com.demo                           # 생성물이 기반 클래스를 import할 때의 프리픽스
  selfcheck:
    classpath: /path/to/your-target-project/demo-lib/*
```

자가검증용 의존성 디렉터리는 아래와 같이 내보냅니다:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. 백엔드 시작

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

서비스 기본 포트는 **8120**이며, API 문서는 `/swagger-ui/index.html`입니다.

### 4. 프론트엔드 시작

```bash
cd ../codegen-web
npm install
npm run serve
```

프론트엔드 기본 포트는 **9530**이며, `/dev-api`를 백엔드 8120으로 프록시합니다. 기본 계정은 `admin / admin123`입니다.

### 5. 핵심 설정 항목

| 설정 | 설명 |
| --- | --- |
| `gen.output.*` | 생성물 기록 경로, 패키지명, 모듈 매핑 |
| `gen.selfcheck.classpath` | 자가검증 메모리 컴파일용 의존성 classpath, `dir/*` 지원 |
| `gen.menu.outlet` | `artifact`는 메뉴 SQL과 라우트 조각만 산출; `internal`은 플랫폼 자체 `sys_menu`에도 기록 |

---

## 11. 문서 내비게이션

| 문서 | 내용 |
| --- | --- |
| `docs/代码生成器-操作手册.md` (중국어) | 배포, 5분 시작, 테이블 설정, 일괄 인수, 템플릿 관리, 대장, API 카탈로그, 파이프라인 전 과정 (실제 스크린샷 포함) |
| `docs/典型场景操作指南-演示后台demo.md` (중국어) | 6개 테이블로 5가지 대표 시나리오 커버, 실행 가능한 데모와 실제 실행 스크린샷 포함 |
| `docs/项目介绍.md` (중국어) | 본 문서와 동일한 원문의 프로젝트 소개 문서 |
| `docs/代码生成器-Vx.x-规划.md` (중국어) | V3.1 → V7.3 각 버전 계획 및 기능 목록 |
| `codegen-web/README.md` | 프론트엔드 콘솔 빠른 시작 참고 |

---

## 12. 버전 이력

| 버전 | 버전 테마 / 계획 문서 | 적용 커밋 |
| --- | --- | --- |
| V1.2 / V1.3 | 원클릭 생성 / 일괄 / 롤백; diff 미리보기와 구조 동기화 | — (`src/api/gen.js` 주석에만 흔적) |
| V2.0 | 다이내믹 라우팅 (백엔드가 메뉴 트리 전송) | — |
| V3.0 / V3.1 | 멀티 시스템 대장; 멀티 시스템 루프 + 최초 자동화 테스트 | V3.1 (174줄) |
| V4.2 | 생성 결과 추적·롤백 가능 (기록 대장) | V4.2 (292줄) |
| V4.3 | 템플릿 회귀 및 품질 가드레일 | V4.3 (200줄) |
| V4.4 | 일대다(1마스터 다중 디테일), 캐스케이드 기록 완전화, 템플릿 분도 | V4.4 (154줄) |
| V4.5 | A4 인쇄 문서 (서드파티 의존성 0) | V4.5 (127줄) |
| V5.0 | 한 문장으로 시스템 구축: 파이프라인 + AI 모델링 + 스캐폴드 기반 | — |
| V5.1 | DDL 감사 컬럼 자동 보완 | V5.1 (100줄) |
| V5.2 | 파이프라인 시드 데이터 초기화 단계 | V5.2 (107줄), 84ec8dd |
| V5.5 | 멀티 모듈 프로젝트 생성 마무리 | af73dd3 |
| V6.0 | 마이크로서비스 인프라 (Nacos / Gateway / Feign, 제3상태 micro) | V6.0 (111줄), 1deecbd |
| V6.1 | Activiti 워크플로 듀얼 엔진 | V6.1 (100줄), 02b671e |
| V6.2 | 산출물 전달 면 (CLI / 배포물 / 설정 보안) | V6.2 (114줄), 02b671e |
| V6.3 | 결제 기능 팩 (위챗 / 알리페이 + Mock) | V6.3 (104줄), 1c4ee6a, 22eede5 |
| V6.4 | 권한·보안 기준선 (token 영속화 / BCrypt / API 권한 / 로그인 보호) | V6.4 (107줄), 007a202 |
| V6.5 | 모바일 uni-app (H5 + 위챗 미니프로그램) | V6.5 (105줄), 779ac41 |
| V6.6 | 파일 기능 팩 | 98f3c09 |
| V6.7 | 산출물 테스트 출고 (test_enabled 3상태 + H2 스켈레톤) | V6.7 (134줄), 4e7d318 |
| V6.8 | 생성물 유지보수성 (dryRun / 보호 건너뜀 / 브랜드 파라미터화) | V6.8 (141줄), be6f858 |
| V6.9 | 플랫폼 문서 센터 (백엔드 기반, 프론트엔드 마무리 동일 배치 진행) | — |
| V6.10 | 플랫폼 관측성(Observability): Actuator + 메모리 메트릭 윈도우 + 내장 모니터링 페이지 | — |
| V7.0 | AI 페이지·메뉴 추천 (preview / apply) | V7.0 (94줄), 45f2670 |
| V7.1 | 시드 데이터 AI 강화 (AI는 의미론, 규칙은 구조 담당) | V7.1 (42줄), 5ca6e72 |
| V7.2 | 파이프라인 성공 후 SQL 전달물 `db/`에 기록 | eebff88 |
| **V7.3** | **전달 신뢰성: 자가검증 컴파일 산출물 테스트 / 통합 SQL 전달 아울렛 / 테이블 단위 AI 추천** | **V7.3 (138줄)** |
| **V7.4** | 반복 가능한 전달: 빈 DB에서도 배포 가능, 모든 커밋이 실행 가능함을 보장  ** | c306e29 |
| **V7.5** | 프로젝트 언어 설정: 206개 언어 목록 / 기본 언어 / 사용 언어, 생성물에 lang·dir 주입 | 7a9afa0 + abac56d |
| **V8.0** | 전 페이지 i18n: 206개 언어 프레임워크(8개 정밀 번역), OFF 제로 diff 계약의 듀얼 트랙 i18n | 9e5dd57 → 7d57aea |
| **V8.1** | 품질 강화: 오프라인 계약 하네스(9 모드 OFF 제로 diff) + i18n-core 단위 테스트 + CI 게이트 + README 정직 분할(프레임워크 206 / 정밀 8) + check-locale-keys.mjs | 93a12d5 + e49330b |
| **V9.0** | 생성물을 Vue 3 + Element Plus + Vite로 현대화; 런타임 검증 완료(40/40 어설션, vite build 성공, ar-SA RTL 포함 14장 스크린샷); 런타임 결함 3건 수정 | 93a12d5 + e49330b  |
| **V9.1** | **유지보수성 리팩터링 · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9** | 654b8e0 → b0d081e, **현재 버전** |

---

> **V6.10 플랫폼 관측성**: Actuator + Micrometer를 도입하여 5초 간격으로 샘플링해 메모리 링 버퍼에 기록(30분 윈도우 유지)하고, 내장 "시스템 모니터링" 페이지를 신설했습니다 — 백엔드 `/monitor/*` 집계 API + 프론트엔드 ECharts 패널. 엔드포인트는 화이트리스트의 `health/info/metrics`만 허용되며, `/actuator/health`는 토큰 불필요, `metrics` 등은 인증 필요; `env/beans/heapdump/threaddump/loggers`는 절대 노출되지 않습니다. 샘플링 윈도우는 프로세스 내에만 존재하며, 재시작 시 소실되고 다중 인스턴스 간 공유되지 않습니다. 자세한 내용은 `docs/可观测性说明.md`(중국어)를 참고하세요.

## 13. 프로젝트 현황

- 현재 계획 버전 라인은 **V9.1**에 도달했으며, 기능은 지속적으로 반복 개선 중입니다 (AI 모델링, 시드 데이터 강화, 기능 팩 체계 등).
- 동반 데모 프로젝트 `demo_admin`(백엔드 8140 / 프론트엔드 9040)이 준비되어 있으며, 트리 / CRUD / 마스터-디테일 / 읽기 전용 / 인쇄 전 시나리오를 커버하고 독립적으로 실행·검증할 수 있습니다.
- 두 리포지토리 모두 Gitee에 호스팅되어 있습니다: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. 작성자 및 기술 지원

- **작성자**: 중국인 프로그래머(개인 개발자)
- **이메일**: `hustxiayuhong@126.com`
- **기술 지원**: 메일은 **중국어**로 보내 주시고, 문제 설명과 실행 환경(JDK / MySQL / 버전), 재현 절차를 함께 적어 주세요

> 모든 메일에 답변드립니다. 버그 신고, 기능 제안, 협업 문의 모두 환영합니다.

---

## 15. 유사 도구와의 비교

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

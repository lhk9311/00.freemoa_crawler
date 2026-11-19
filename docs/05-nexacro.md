# 05. 넥사크로 N 화면 연동

## 구조

| 구분 | 담당 | 파일 |
|---|---|---|
| 넥사크로 XML 읽기/쓰기 | 서버 | `nexacro/NxXmlReader`, `NxXmlWriter`, `NxDataset`, `NxRequest` |
| 화면용 API (`/nx/**`) | 서버 | `screen/ProjectScreenController` |
| 화면용 에러 변환 | 서버 | `screen/NexacroExceptionHandler` |
| 조회 SQL | 서버 | `project/query/*`, `mapper/project/ProjectQueryMapper.xml` |
| CORS | 서버 | `config/WebConfig`, `application.yml`의 `nexacro.allowed-origins` |
| Dataset 정의 | 화면 | `nexacro/datasets.xml` |
| 화면 스크립트 | 화면 | `nexacro/frmProjectList.js` |

### 왜 X-API 라이브러리를 안 썼나
실무에서는 투비소프트의 X-API(또는 UI Adapter) jar로 `PlatformData`, `DataSet` 객체를 다룬다.
이 jar는 Maven 중앙 저장소에 없고 라이선스가 걸려 있어서, 여기서는 같은 XML 형식을 직접 읽고 쓰는 작은 모듈을 만들었다.
X-API가 내부에서 하는 일이 결국 이것이다: **요청 XML → Parameter/Dataset 객체, 결과 객체 → 응답 XML.**

## API 목록

| URL | 인자 (strArgument) | 받는 Dataset |
|---|---|---|
| `POST /nx/skills` | 없음 | `ds_skill` |
| `POST /nx/projects/search` | `keyword`, `skillKey`, `region`, `minCost`, `openOnly`, `page`, `pageSize` | `ds_list`, `ds_page` |
| `POST /nx/crawl` | `keyword`, `maxPages` | `ds_result` |

실패하면 `ErrorCode = -1`, `ErrorMsg = 메시지`. HTTP 상태는 항상 200이다 (넥사크로는 ErrorCode로 판단).

## 넥사크로 없이 먼저 서버 확인 (명령 프롬프트)

```bat
curl -X POST http://localhost:8080/nx/skills

curl -X POST http://localhost:8080/nx/projects/search ^
  -H "Content-Type: text/xml" ^
  -d "<Root xmlns=\"http://www.nexacroplatform.com/platform/dataset\"><Parameters><Parameter id=\"keyword\">java</Parameter></Parameters></Root>"
```
응답에 `<Dataset id="ds_list">`와 `<Row>`들이 보이면 서버 쪽은 끝.

## Studio 작업 순서

### 1. 서버 주소 등록 (TypeDefinition)
Project Explorer > `TypeDefinition` 더블클릭 > **Services** 탭 > 추가
| PrefixID | Type | URL |
|---|---|---|
| `svc` | JSP | `http://localhost:8080/` |

스크립트의 `"svc::nx/skills"`가 `http://localhost:8080/nx/skills`로 바뀐다.
서버 주소가 바뀌어도 여기 한 곳만 고치면 된다.

### 2. Form 만들기
`frmProjectList` 이름으로 Form 생성

### 3. Dataset 4개 추가
Invisible Objects 영역에 Dataset 4개 추가 → id를 `ds_list`, `ds_page`, `ds_skill`, `ds_result`로 지정 →
각각 Dataset Editor > **Source** 탭에 `nexacro/datasets.xml`의 해당 `<ColumnInfo>` 붙여넣기

### 4. 컴포넌트 배치

조회 영역
| 컴포넌트 | id | 설정 |
|---|---|---|
| Edit | `edt_keyword` | 제목 검색어 |
| Combo | `cbo_skill` | innerdataset=`ds_skill`, codecolumn=`skillKey`, datacolumn=`label` |
| Edit | `edt_region` | 예: 서울 |
| Edit | `edt_minCost` | 최소 예산(만원) |
| CheckBox | `chk_openOnly` | text=모집중만 |
| Button | `btn_search` | text=조회 |

목록 영역
| 컴포넌트 | id | 설정 |
|---|---|---|
| Grid | `grd_list` | binddataset=`ds_list` → Grid 우클릭 > **Create Format** 으로 컬럼 자동 생성 |
| Static | `sta_total` | 총 건수 표시 |
| Button | `btn_prev`, `btn_next` | 이전 / 다음 |
| Static | `sta_page` | 1 / 5 |

수집 영역
| 컴포넌트 | id | 설정 |
|---|---|---|
| Edit | `edt_crawlKeyword` | 수집 검색어 |
| Edit | `edt_crawlPages` | 페이지 수 (1~30) |
| Button | `btn_crawl` | text=수집 |

### 5. 스크립트 붙여넣기 + 이벤트 연결
`nexacro/frmProjectList.js` 내용을 Form의 **Script** 탭에 붙여넣고,
Properties 창 > Events 탭에서 연결:
| 대상 | 이벤트 | 함수 |
|---|---|---|
| Form | onload | `Form_onload` |
| btn_search | onclick | `btn_search_onclick` |
| btn_prev / btn_next | onclick | `btn_prev_onclick` / `btn_next_onclick` |
| btn_crawl | onclick | `btn_crawl_onclick` |

### 6. 실행
Spring Boot 앱을 먼저 실행한 뒤 Studio에서 실행(QuickView 또는 브라우저 실행).

## CORS 체크리스트 (브라우저로 실행할 때)
화면 주소와 API 주소의 포트가 다르면 CORS 대상이다.

1. 브라우저 주소창의 화면 주소 확인 (예: `http://localhost:4098`)
2. `application.yml`의 `nexacro.allowed-origins`에 **똑같이** 들어 있는지 확인
   - `localhost`와 `127.0.0.1`은 서로 **다른 출처**다
   - 포트가 다르면 다른 출처다
3. 막히면 브라우저 F12 > Console에 `blocked by CORS policy`가 찍힌다.
   넥사크로 콜백에는 원인 없이 통신 실패(errorCode < 0)로만 온다.
4. Network 탭에서 `OPTIONS` 요청(preflight)이 먼저 나가고, 그 응답에
   `Access-Control-Allow-Origin` 헤더가 있는지 확인

넥사크로 전용 실행기(NRE)로 실행하면 브라우저가 아니라서 CORS 검사가 없다.

## SQL 포인트 (ProjectQueryMapper.xml)
| 기법 | 쓰인 곳 |
|---|---|
| `<sql>` + `<include>` | 목록과 건수가 같은 WHERE를 공유 |
| `<where>` + `<if>` 동적 SQL | 입력한 조건만 붙음 |
| 상관 서브쿼리 + `EXISTS` | 기술스택 조건 (JOIN과 달리 중복 행이 안 생김) |
| 스칼라 서브쿼리 + `LISTAGG` | 공고별 기술 목록을 한 칸에 "Java, Oracle, Spring" |
| `OFFSET ... FETCH NEXT` | Oracle 12c+ 페이징 |
| `&gt;=` | XML 안에서 `>`, `<`는 이스케이프해야 함 |

## 보안 포인트
| 항목 | 위치 |
|---|---|
| XXE 방어 (DOCTYPE 금지) | `NxXmlReader` |
| 응답 XML 이스케이프 (`<script>` → `&lt;script&gt;`) | `NxXmlWriter` |
| 예상 못 한 에러는 일반 메시지만, 상세는 로그로 | `NexacroExceptionHandler` |
| CORS 허용 출처를 명시 (`*` 금지) | `WebConfig` |

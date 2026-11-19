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
| 화면 전체 (레이아웃 + Dataset + 스크립트) | 화면 | `nexacro/frmProjectList.xfdl` (Studio로 Import) |

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

## Studio 작업 순서 (실제로 한 순서)

화면은 `nexacro/frmProjectList.xfdl` 한 파일에 레이아웃, Dataset, 스크립트가 모두 들어 있다.
컴포넌트를 하나씩 배치하지 않고 이 파일을 Import 해서 쓴다.

### 1. 프로젝트 생성
Start Page > **New** > Application
| 단계 | 값 |
|---|---|
| Project Name | `freemoa_nx` |
| Location | `C:\nexacro\projects\` (기본값) |
| Screen | Desktop, 1280 x 720 (기본값) |
| Frame | Full (기본값) → **Finish** |

### 2. 서버 주소 등록 (TypeDefinition > Services)
Project Explorer > `TypeDefinition` > **Services** 더블클릭 > 아래 User Service 표의 `+` > Type 목록에서 **JSP** 선택
| PrefixID | Type | URL |
|---|---|---|
| `svc` | JSP | `http://localhost:8080/` |

입력 후 **OK**.
스크립트의 `"svc::nx/skills"`가 `http://localhost:8080/nx/skills`로 바뀐다.
서버 주소가 바뀌어도 여기 한 곳만 고치면 된다.

주의: `TypeDefinition` 아래 **Objects**를 열면 컴포넌트 목록 창이 뜬다. 서비스는 **Services**다.

### 3. 폼 Import
Project Explorer > `Base` 우클릭 > **Import** > 파일 이름에
`C:\Users\lhk93\IdeaProjects\freemoa-crawler\nexacro\frmProjectList.xfdl` 입력 > 열기

Output 창에 `File Import : Success 1, Fail 0`, `Successfully generated file : ...frmProjectList.xfdl.js`가 나오면 성공.
`frmProjectList` 더블클릭 → Design 탭에 조회 조건, 그리드가 보이고 아래 Invisible Object에 `ds_list`, `ds_page`, `ds_skill`, `ds_result`가 있으면 정상.

Import는 **복사**다. 이후 Studio에서 고치는 파일은
`C:\nexacro\projects\freemoa_nx\Base\frmProjectList.xfdl`이고, 저장소의 `nexacro/frmProjectList.xfdl`과 자동 동기화되지 않는다.
화면을 고쳤으면 저장소 쪽으로 다시 복사해서 커밋한다.

Source 탭에 XML을 다시 붙여넣을 필요는 없다.

### 4. 처음 띄울 폼 지정 (빈 화면 문제)
Project Explorer > `Application Information` > `Applications` > `Application_Desktop` > `mainframe` > **WorkFrame** 선택 >
Properties 필터에 `formurl` 입력 > 값을 `Base::frmProjectList.xfdl`로 변경 > **Ctrl+Shift+S** (모두 저장)

| 증상 | 원인 |
|---|---|
| 폼 소스는 멀쩡한데 실행하면 빈 화면 | WorkFrame의 `formurl` 기본값이 비어 있는 `FrameBase::Form_Work.xfdl` |

실행 버튼은 앱 전체를 띄우고, 앱은 WorkFrame이 가리키는 폼을 보여준다.
마법사가 만든 기본값이 빈 폼이라, 이걸 안 바꾸면 내 폼이 아니라 빈 폼이 뜬다.

### 5. 실행
Spring Boot 앱을 먼저 실행한다 (콘솔에 `Started FreemoaCrawlerApplication`).

| 방식 | 단축키 | 띄우는 것 |
|---|---|---|
| Launch (실행) | Ctrl+F5 | 앱 전체. WorkFrame `formurl`의 폼 |
| QuickView | Ctrl+F6 | 지금 편집 중인 폼 하나만 |

Run Configuration 창이 뜨면 그대로 **Run**.
기본값 Local Web Server(`http://127.0.0.1:4099/`) + Nexacro Runtime Environment로 실행되며, 브라우저가 아닌 별도 창으로 뜬다.
Output의 `favicon.ico 404`는 무시해도 된다.

### 6. 확인
| 확인 | 정상 결과 | 의미 |
|---|---|---|
| 기술 콤보 펼치기 | `JAVA (5)` 같은 항목 | `/nx/skills` 통신 성공 (Form_onload) |
| 조회 버튼 | `총 N건`, 그리드 채워짐 | `/nx/projects/search` 성공 |
| 에러 alert | 없음 | 있으면 문구 + Output 빨간 로그 확인 |

### 참고: 폼 구성
| 영역 | 컴포넌트 id |
|---|---|
| 조회 조건 | `edt_keyword`, `cbo_skill`(innerdataset=`ds_skill`, codecolumn=`skillKey`, datacolumn=`label`), `edt_region`, `edt_minCost`, `chk_openOnly`, `btn_search` |
| 건수 / 페이징 | `sta_total`, `btn_prev`, `sta_page`, `btn_next` |
| 목록 | `grd_list` (binddataset=`ds_list`) |
| 수집 | `edt_crawlKeyword`, `edt_crawlPages`, `btn_crawl` |

이벤트는 xfdl 안에 이미 연결돼 있다 (Form `onload=Form_onload`, 각 버튼 `onclick`).

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

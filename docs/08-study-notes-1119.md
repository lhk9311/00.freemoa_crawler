# 08. 학습 노트 (11/19)

구현하면서 나온 질문과 답을 정리했다. 설계 이유는 01~07, 오류 사례는 06에 있다.

## 1. DB 설계

### 자연키 vs 대리키
| | 대리키 (surrogate key) | 자연키 (natural key) |
|---|---|---|
| 값의 출처 | DB가 만들어 줌 (시퀀스) | 데이터에 원래 있던 값 |
| 예 | `SEQ_MEMBER.NEXTVAL` | 주민번호, 사업자번호, 프리모아 공고번호 |
| 이 프로젝트 | `TB_SKILL`, `TB_CRAWL_RUN` | `TB_PROJECT.PROJ_IDX` |

공고를 시퀀스 PK로 하면 같은 공고를 수집할 때마다 새 행이 생긴다. 공고번호를 PK로 하면 같은 공고는 1행만 존재할 수 있다.

자연키는 **값이 절대 안 바뀌고, 유일하고, 개인정보가 아닐 때만** 쓴다. 하나라도 걸리면 대리키를 PK로 두고 그 값에는 UNIQUE 제약만 건다.

### MERGE (upsert)
```sql
MERGE INTO TB_PROJECT T
USING DUAL
   ON (T.PROJ_IDX = #{projIdx})       -- 기준키: 있냐 없냐를 이 값으로 판단
WHEN MATCHED THEN UPDATE SET ...      -- 있으면 최신 값으로 덮어씀
WHEN NOT MATCHED THEN INSERT ...      -- 없으면 추가
```
- `ON` 절 컬럼(`PROJ_IDX`)은 UPDATE할 수 없다 (`ORA-38104`).
- `FIRST_CRAWLED_AT`은 UPDATE에서 빼서 최초 수집일을 보존한다.

### 왜 다시 수집하면 UPDATE하나
공고 내용은 계속 바뀐다. `APPLY_COUNT`(지원자 수)가 대표적이고, 예산, 마감일, 본문도 수정될 수 있다. UPDATE를 안 하면 DB에 처음 값이 영원히 남는다.

과거 값은 덮어써지면 사라지므로 `TB_PROJECT_SNAPSHOT`에 수집할 때마다 따로 쌓는다.
- `TB_PROJECT`: 지금 상태 (공고당 1행)
- `TB_PROJECT_SNAPSHOT`: 시점별 기록 (수집할 때마다 행 추가)

"현재 값 테이블 + 이력 테이블"은 회원/회원 변경 이력, 상품 가격/가격 이력처럼 실무에서 자주 쓰는 패턴이다.

### N:M 매핑과 복합 PK 인덱스
공고 1건에 기술 N개, 기술 1개에 공고 N건 → 중간 테이블 `TB_PROJECT_SKILL(PROJ_IDX, SKILL_ID)`.
기술을 `"PHP,MySQL"` 문자열 한 칸에 넣으면 `LIKE '%JAVA%'`가 되어 느리고 `JAVASCRIPT`까지 걸린다.

복합 PK는 **앞 컬럼으로 먼저 정렬된 인덱스**다. 전화번호부가 (성, 이름) 순서라 "김씨"는 빨리 찾지만 "이름이 철수인 사람"은 처음부터 다 넘겨야 하는 것과 같다.

| 조회 | WHERE | 쓰는 인덱스 |
|---|---|---|
| 공고 → 기술 | `PROJ_IDX = ?` | PK (선두 컬럼) |
| 기술 → 공고 | `SKILL_ID = ?` | 별도 인덱스 `IX_PROJECT_SKILL_SKILL` 필요 |

FK 컬럼에 인덱스가 없으면 Oracle은 부모 행을 지울 때 자식 테이블 전체에 락을 건다. 그래서 "FK에는 인덱스"가 거의 규칙이다.

실행 계획 확인: DBeaver에서 쿼리 선택 후 Ctrl+Shift+E.

## 2. 데이터가 들어오는 흐름

### proj_idx는 어디서 오나
공식 API가 아니다. 프리모아 목록 화면이 내부적으로 부르는 AJAX 주소(`POST /m4a/s41a`)를 F12 Network 탭에서 찾아 똑같이 부른다. `proj_idx`는 그 JSON 안에 있는, 프리모아가 붙인 공고번호다.

공식 API가 아니라서 언제든 바뀔 수 있다 → 프리모아 형식을 아는 코드를 `FreemoaApiClient`, `FreemoaListResponse` 두 곳에만 모아 둔다.

### 요청 이후 흐름
```
글자 덩어리(String)  ──Jackson──▶  Item(값은 전부 String)  ──Converter──▶  Project(숫자·날짜)  ──MyBatis──▶  DB 행
```
| 단계 | 코드 | 하는 일 |
|---|---|---|
| 응답 | `response.body()` | 자바 입장에선 아직 의미 없는 긴 문자열 |
| 파싱 | `objectMapper.readValue(text, FreemoaListResponse.class)` | `@JsonProperty("proj_idx")` 이름대로 record에 담음. 선언 안 한 필드(`client_id`)는 버림 |
| 변환 | `ProjectConverter.convert()` | `"48588"`→48588, `"0"`→null(미입력), 날짜 파싱, 기술 쉼표 분리 |
| 저장 | `projectMapper.merge(p)` | `#{projIdx}` 자리에 `p.projIdx()` 값을 바인딩 |

### 직접 확인하는 방법
| 보고 싶은 것 | 보는 곳 |
|---|---|
| 프리모아 원본 요청/응답 | Chrome F12 > Network > Fetch/XHR > 프리모아 목록 페이지 번호 클릭 > `s41a` |
| 브라우저 없이 재현 | cmd에서 curl (`-H`로 헤더, `-d`로 폼 데이터, `-v`로 오간 헤더) |
| 서버가 프리모아를 부르는 것 | IntelliJ 콘솔 로그, 디버거 (F12에는 안 보임) |
| 실제 실행된 SQL | 콘솔의 `==> Preparing` / `==> Parameters` |

F12는 **그 브라우저 탭이 보낸 요청만** 보여준다. 우리 서버가 프리모아를 부르는 건 서버 간 통신이라 F12에 안 나온다. 프리모아 페이지에서 보이는 `collect` 요청은 방문 통계(구글 애널리틱스 등)다.

### 파싱 방법은 여러 가지
원리(문자열 → 구조 파악 → 객체)는 같고, 형식과 방식에 따라 도구가 다르다.

| 형식 | 도구 | 어디서 |
|---|---|---|
| JSON | Jackson, Gson | 웹 API 대부분 |
| XML | DOM, SAX, StAX, JAXB | 공공기관, 넥사크로 |
| HTML | Jsoup | API 없이 화면을 긁을 때 |
| CSV / 엑셀 | OpenCSV / Apache POI | 파일 연계, 업로드 |
| 고정길이 전문 | 바이트 단위로 자르기 | 은행·카드사 연계 (EUC-KR은 한글 2바이트) |

| JSON 읽는 방식 | 언제 |
|---|---|
| 데이터 바인딩 `readValue(text, X.class)` | 구조를 알 때 (이 프로젝트) |
| 트리 `readTree(text).path("DATA")` | 일부만 꺼내거나 구조가 들쭉날쭉할 때 |
| 스트리밍 `JsonParser` | 메모리에 다 못 올리는 큰 파일 |

이 프로젝트에도 세 가지가 섞여 있다: Jackson 바인딩(`FreemoaApiClient`), DOM(`NxXmlReader`), StAX(`NxXmlWriter`).

## 3. 넥사크로

| 개념 | 내용 |
|---|---|
| `svc::nx/skills` | TypeDefinition > Services에 등록한 `svc`(http://localhost:8080/)로 바뀜 |
| Launch(Ctrl+F5) vs QuickView(Ctrl+F6) | 앱 전체(WorkFrame `formurl`의 폼) vs 지금 편집 중인 폼 하나 |
| xfdl vs xfdl.js | 편집용 원본 vs 실행용. `.java`와 `.class` 관계. 저장(Generate)해야 js가 바뀜 |
| 화면이 안 바뀔 때 | Generate 했나 → 브라우저 캐시(Ctrl+Shift+R) → 서비스 CacheLevel(개발 중엔 none) |
| 인자 없는 transaction | GET으로 나감. 데이터를 바꾸는 요청은 인자를 1개 이상 보내서 POST로 |
| NRE vs 브라우저 | NRE(QuickView)는 브라우저가 아니라 CORS 검사가 없음. 브라우저에서만 CORS 문제가 드러남 |

## 4. 수집과 배치

### 전체 수집은 사실상 배치다
| 배치의 특징 | 전체 수집 |
|---|---|
| 사람이 결과를 안 기다림 | 화면은 접수만, 수집은 `crawl-1` 스레드 |
| 대량을 쪼개서 처리 | 페이지(10건) 단위로 받고 저장 |
| 실행 이력 | `TB_CRAWL_RUN` |
| 중간 실패해도 처리분은 남김 | 페이지마다 커밋, PARTIAL |
| 중복 실행 방지 | `UX_CRAWL_RUN_RUNNING` 유니크 인덱스 |

| Spring Batch | 이 프로젝트 |
|---|---|
| Job | 수집 1회 |
| JobRepository | `TB_CRAWL_RUN` |
| Chunk (읽기 → 가공 → 쓰기) | 1페이지: API → Converter → Writer |
| JobLauncher | `CrawlJobLauncher` |
| 재시작 지점 | 없음. 대신 MERGE라서 처음부터 다시 돌려도 안전 (멱등성) |

### 증분 배치 설정
```yaml
crawler:
  incremental:
    enabled: true                  # false면 배치 클래스 자체가 빈으로 안 만들어짐
    cron: "0 0 9-18 * * MON-FRI"   # 평일 9~18시 매시 정각 (하루 10번)
    max-pages: 10
    stop-after-idle-pages: 2       # 새 공고 0건 페이지가 2번 연속이면 멈춤
```
스프링 cron은 6자리(초 분 시 일 월 요일)다. 리눅스 crontab은 5자리(분부터).

| 주기 | cron |
|---|---|
| 매일 매시 정각 | `0 0 * * * *` |
| 평일 9~18시 30분마다 | `0 0/30 9-18 * * MON-FRI` |
| 매일 아침 8시 | `0 0 8 * * *` |

배치는 서버가 켜져 있을 때만 돈다. 로그 스레드 이름이 `[scheduling-1]`이면 배치가 실행한 것이다.

### 외부 API 호출의 기본 세트: 타임아웃 + 재시도 + 백오프
| 설정 | 위치 | 값 |
|---|---|---|
| 연결 타임아웃 | `CrawlerConfig` HttpClient | 5초 |
| 요청 타임아웃 | `FreemoaApiClient.REQUEST_TIMEOUT` | 10초 |
| 재시도 | `ProjectCollector.fetchWithRetry` | 네트워크 오류만 최대 3번, 2초·4초 대기 |

타임아웃이 없으면 상대가 응답을 안 줄 때 스레드가 영원히 기다린다. 재시도는 "다시 하면 결과가 달라질 수 있는 오류"(타임아웃, 연결 끊김)에만 한다. 404나 파싱 실패는 다시 해도 같다.

## 5. 디버깅 습관
- **스택트레이스 읽기:** 맨 윗줄(무슨 에러) → 내 패키지(`com.lhk.freemoa`)가 처음 나오는 줄(어디서) → `Caused by:`(근본 원인). `java.base/...`는 대부분 건너뛴다.
- **"고쳤는데 같은 에러":** 스택트레이스의 줄 번호, 메서드 이름이 지금 코드와 맞는지 먼저 본다. 안 맞으면 옛 코드가 도는 것 (재빌드, 재시작, IntelliJ 디스크 리로드).
- **CORS 에러:** 다른 원인을 가리는 껍데기인 경우가 많다. Console 문구 전체와 실제 HTTP 메서드를 본다. 같은 설정인데 한 요청만 실패하면 두 요청의 차이를 비교한다.
- **"처리 중 오류가 발생했습니다":** 일부러 일반 메시지만 화면에 보낸다. 원인은 항상 서버 로그에 있다.
- **백그라운드 작업:** 화면 요청이 실패해도 작업은 계속 돌 수 있다. 이력 테이블로 실제 상태를 확인한다.
- **MyBatis 로그 `<== Updates: 1`:** 영향받은 행 수다. MERGE는 INSERT든 UPDATE든 1이다.
- **`COUNT(*)`의 `<== Total: 1`:** 결과가 1행이라는 뜻이지 건수가 1이라는 뜻이 아니다.

## 6. Git: 패치로 주고받기
```bash
git am 0001-xxx.patch 0002-yyy.patch   # 커밋 메시지·작성자까지 포함해 커밋으로 적용
```
| 상황 | 명령 |
|---|---|
| 적용 취소, 시작 전으로 | `git am --abort` |
| 이 패치는 건너뛰기 (이미 들어간 경우) | `git am --skip` |
| 충돌 직접 고친 뒤 계속 | `git add` 후 `git am --continue` |

`git apply`는 파일만 바꾸고 커밋은 안 한다. `git am`은 커밋까지 만든다. 멈춘 상태(`.git/rebase-apply`)를 정리하지 않으면 다음 `git am`이 안 된다.

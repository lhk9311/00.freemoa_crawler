# 09. 디버깅 실습 + Postman

코드를 바꾸지 않고 도구로 동작을 확인하는 실습이다.

## 1. IntelliJ 디버거로 수집 흐름 따라가기

### 준비
- 서버를 **Debug**(벌레 아이콘, Shift+F9)로 실행한다. Run으로 실행하면 브레이크포인트가 무시된다.
- 브레이크포인트: 줄 번호 옆 여백 클릭 → 빨간 점. 모든 브레이크포인트 목록은 Ctrl+Shift+F8.

### 단축키
| 키 | 동작 |
|---|---|
| F8 | Step Over: 다음 줄로 (메서드 안으로 안 들어감) |
| F7 | Step Into: 메서드 안으로 들어감 |
| Shift+F8 | Step Out: 지금 메서드를 끝내고 호출한 곳으로 |
| F9 | Resume: 다음 브레이크포인트까지 진행 |
| Alt+F8 | Evaluate Expression: 멈춘 상태에서 아무 식이나 실행 |
| Alt+F9 | Run to Cursor: 커서 줄까지 진행 |

### 실습 1. 공고 1건이 DB에 들어가기까지
`crawl.http` 또는 Postman으로 `POST /api/crawl?keyword=java&maxPages=1`을 보내고, 아래 순서로 멈춘다.

| 순서 | 위치 | 볼 것 |
|---|---|---|
| 1 | `CrawlController.crawl()` 첫 줄 | 요청 파라미터 `keyword`, `maxPages` |
| 2 | `FreemoaApiClient.fetchList()`의 `response.body()` 다음 줄 | Variables에서 `response.body()` → 프리모아가 준 JSON 원문 |
| 3 | `ProjectConverter.convert()` 첫 줄 | `item` (전부 String) |
| 4 | `ProjectConverter.convert()` return 줄 | 반환될 `Project` (숫자, 날짜로 바뀐 값) |
| 5 | `ProjectWriter.saveOne()` 첫 줄 | DB에 들어가기 직전 `project` |
| 6 | `CrawlService`의 `totals.add(...)` 다음 줄 | 페이지 단위 누적 건수 `totals` |

Debug 창 왼쪽 Frames(호출 스택)를 클릭하면, 지금 메서드를 누가 불렀는지 거슬러 올라가 볼 수 있다. 스택트레이스를 살아 있는 상태로 보는 것과 같다.

### 실습 2. 조건부 브레이크포인트
공고가 수천 건이라 조건 없이 걸면 매번 멈춘다. 빨간 점 우클릭 → Condition.

| 위치 | 조건 | 의미 |
|---|---|---|
| `ProjectConverter.convert()` 첫 줄 | `item.deadline() != null && item.deadline().startsWith("0000")` | 마감일 없는 공고에서만 멈춤 |
| `ProjectConverter.convert()` 첫 줄 | `"48588".equals(item.projIdx())` | 특정 공고에서만 멈춤 |
| `CrawlService`의 `totals.idlePages = ...` 다음 줄 | `totals.idlePages >= 1` | 증분 수집에서 새 공고 없는 페이지가 나온 순간 |

멈춘 상태에서 Alt+F8 → `item.title()` 입력 → 그 공고 제목이 나온다.

### 실습 3. 스레드 바꿔 보기
화면에서 **전체 수집**을 누르고 `ProjectWriter.saveOne()`에서 멈춘다.
- Debug 창 상단 스레드 목록에 `crawl-1`이 보인다. 요청을 받은 `http-nio-8080-exec-N`은 이미 응답(202)을 주고 끝났다.
- 정각에 배치가 돌 때 멈추면 `scheduling-1`이 보인다.
- 백그라운드에서 오래 멈춰 있으면 그동안 수집이 진행되지 않는다. 실습이 끝나면 브레이크포인트를 끄고(Ctrl+F8) F9.

### 실습 4. 값 바꿔서 흐름 바꾸기
멈춘 상태에서 Variables 창의 변수 우클릭 → Set Value.
- `CrawlService.execute()`에서 파라미터 `stopAfterIdlePages`를 1로 바꾸면, 증분 수집이 새 공고 없는 페이지 1번 만에 멈추는 걸 볼 수 있다.
- `item` 같은 record는 불변이라 필드 값을 바꿀 수 없다. 대신 Alt+F8로 `item.applyCount()` 같은 값을 꺼내 볼 수는 있다.

## 2. Postman

### 컬렉션 가져오기
1. Postman 실행 → 좌측 **Import** → `postman/freemoa-crawler.postman_collection.json` 선택
2. 컬렉션 이름 클릭 → **Variables** 탭에서 `baseUrl` 확인 (`http://localhost:8080`)
3. 서버를 켠 상태에서 요청을 하나씩 **Send**

### 들어 있는 요청
| 폴더 | 요청 | 확인할 것 |
|---|---|---|
| 0. 상태 확인 | DB 연결 확인 | 200 |
| 1. 수집 API (JSON) | 수동 수집, 페이지 초과(400), 전체 수집 접수(202), 중복(409), 진행 상황, 증분 수집 | 상태 코드별 의미 |
| 2. 넥사크로 화면 API (XML) | 기술 콤보(GET), 공고 조회, 수집 이력, 잘못된 요청 | XML 요청을 직접 만들어 보내기, `ErrorCode` |

### 실습 포인트
- **변수**: "전체 수집 접수"를 보내면 응답의 `runId`가 컬렉션 변수에 저장된다(Tests 탭 스크립트). 그 다음 "진행 상황 조회"는 URL의 `{{runId}}`로 그 값을 쓴다. 요청끼리 값을 넘기는 방법이다.
- **Tests 탭**: 각 요청에 응답 검증 스크립트가 들어 있다. Send 후 아래 **Test Results**에서 통과/실패를 본다.
- **Collection Runner**: 컬렉션 우클릭 → Run → 순서대로 전부 실행. 간단한 회귀 테스트처럼 쓸 수 있다.
- **넥사크로 XML 직접 보내기**: "공고 조회"의 Body를 열어 `<Parameter id="keyword">`를 바꿔 보낸다. 넥사크로 화면 없이 서버만 따로 확인하는 방법이다.
- **Console** (좌측 하단): 실제로 나간 요청 헤더와 본문을 볼 수 있다.

### `.http` 파일, curl, Postman 비교
| | `http/crawl.http` | curl | Postman |
|---|---|---|---|
| 위치 | IntelliJ 안 | 터미널 | 별도 앱 |
| 공유 | 저장소에 같이 커밋 | 명령어 복사 | 컬렉션 JSON 내보내기 |
| 변수, 검증 스크립트 | 일부 지원 | 없음 | 강함 |
| 실무 | 개발자 개인 확인 | 서버에서 빠른 확인, 장애 대응 | 팀 공유, QA, 외부 연동 테스트 |

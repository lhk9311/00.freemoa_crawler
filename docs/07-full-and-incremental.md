# 07. 전체 수집 + 증분 배치

## 목표
- 처음 한 번은 **전체**를 받는다 (400페이지 이상, 수 분).
- 그 뒤로는 **새 공고만** 주기적으로 받는다 (평소 1~2페이지).
- 보는 쪽(화면)은 DB에 쌓인 전체에서 조건으로 거른다.

## 수집 종류 3가지

| 종류 | 시작 방법 | RUN_TYPE | 페이지 | 응답 |
|---|---|---|---|---|
| 수동 | `POST /api/crawl?maxPages=3`, 화면 수집 버튼 | MANUAL | 지정 (1~30) | 끝날 때까지 기다림 (200) |
| 전체 | `POST /api/crawl/all` | FULL | 마지막 페이지까지 (상한 1000) | 바로 runId (202), 백그라운드 |
| 증분 | 스케줄러, `POST /api/crawl/incremental` | INCREMENTAL | 새 공고가 끊길 때까지 (상한 10) | 끝날 때까지 기다림 (200) |

세 가지 모두 `CrawlService.execute()` 하나로 돈다. 차이는 "몇 페이지까지, 언제 멈추나"뿐이다.

## 1. 페이지 단위 저장 (공통 변경)

```
기존: 전부 수집 → 한꺼번에 저장
변경: 1페이지 수집 → 저장 → 진행 건수 갱신 → 2페이지 수집 → 저장 → ...
```
- 400페이지째에서 실패해도 앞에서 받은 건 남는다. 이때 상태는 FAIL이 아니라 PARTIAL.
- `ProjectCollector.collectEachPage(keyword, maxPages, handler)`가 페이지마다 `handler.onPage(page, projects)`를 부른다.
- `handler`가 `false`를 돌려주면 거기서 멈춘다 → 증분 수집의 멈춤 조건이 이걸로 구현된다.
- 페이지가 밀려서 같은 공고가 두 번 오는 걸 막으려고, 수집 전체 기준 `Set<Long> seen`으로 한 번 더 거른다.

### Java 포인트: 람다와 effectively final
```java
int inserted = 0;
collector.collectEachPage(..., (page, projects) -> {
    inserted += 10;   // 컴파일 에러: 람다 안에서 바깥 지역변수를 다시 대입할 수 없다
    return true;
});
```
그래서 `Totals`라는 작은 객체를 만들고 그 안의 필드를 바꾼다. 변수(참조)는 그대로이고 객체 내용만 바뀌니까 허용된다.

## 2. 전체 수집 (백그라운드)

```
요청 스레드 (http-nio-8080-exec-N)          수집 스레드 (crawl-1)
crawlRunService.start(FULL) -> runId
executor.execute(작업) ───────────────────▶ crawlService.execute(runId, ...)
return 202 + runId                            페이지마다 저장, 진행 건수 갱신
                                              finish()
```

| 파일 | 역할 |
|---|---|
| `CrawlJobLauncher` | 이력을 RUNNING으로 만들고 작업을 스레드 풀에 넘김 |
| `CrawlAsyncConfig` | 전용 스레드 풀 (1개, 대기열 0, 이름 `crawl-`) |
| `CrawlRunService.progress()` | 페이지마다 건수 갱신 (REQUIRES_NEW라 바로 다른 세션에서 보임) |
| `CrawlRunRecovery` | 서버 시작 시 남은 RUNNING을 FAIL로 정리 |
| `GET /api/crawl/runs/{runId}` | 진행 상황 조회 |

- **왜 202:** 200은 "처리 끝", 202 Accepted는 "접수만 됨, 처리는 나중에"라는 뜻이다.
- **왜 start()는 요청 스레드에서 하나:** runId를 바로 돌려주고, 이미 실행 중이면 409를 바로 주기 위해서다.
- **왜 @Async가 아니라 TaskExecutor인가:** 같은 클래스 안에서 `@Async` 메서드를 `this.xxx()`로 부르면 프록시를 안 거쳐서 비동기로 안 돈다. `@Transactional` self-invocation 함정과 같은 원리다.
- **왜 CrawlRunRecovery가 필요한가:** 수집 도중 서버를 끄면 `finish()`가 안 불려 RUNNING이 남는다. 그러면 유니크 인덱스 때문에 다음 수집이 전부 409로 막힌다. 서버가 1대일 때만 맞는 방법이다.

## 3. 증분 배치 (@Scheduled)

### 멈춤 조건
목록은 최신 공고가 앞에 온다. 1페이지부터 보다가 **새 공고(inserted)가 0건인 페이지가 2번 연속** 나오면 멈춘다.
```
1페이지: 신규 3건 → 계속 (연속 0)
2페이지: 신규 0건 → 계속 (연속 1)
3페이지: 신규 0건 → 멈춤 (연속 2)
```
1번이 아니라 2번 연속인 이유는, 한 페이지가 우연히 전부 기존 공고여도 바로 멈추지 않게 하려는 여유다. 상한(`max-pages: 10`)은 서버가 오래 꺼져 있다가 켜졌을 때 한 번에 너무 많이 가지 않게 하는 안전장치다.

### 설정 (application.yml)
```yaml
crawler:
  incremental:
    enabled: true                  # false면 배치 클래스 자체가 빈으로 안 만들어짐
    cron: "0 0 9-18 * * MON-FRI"   # 평일 9~18시 매시 정각
    max-pages: 10
    stop-after-idle-pages: 2
```

| cron | 의미 |
|---|---|
| `0 0 9-18 * * MON-FRI` | 평일 9~18시 매시 정각 |
| `0 */10 * * * *` | 10분마다 (테스트용) |
| `0 30 8 * * *` | 매일 8시 30분 |

스프링 cron은 6자리다 (초 분 시 일 월 요일). 리눅스 crontab은 5자리(분부터)라 헷갈리기 쉽다.

### 다른 수집과 겹치면
`crawlRunService.start()`가 `UX_CRAWL_RUN_RUNNING`에 걸려 `CrawlAlreadyRunningException`이 난다. 배치는 "이번 회차 건너뜀" 로그만 남기고 다음 시각에 다시 돈다.

### 한계
| 한계 | 내용 |
|---|---|
| 기존 공고 변경 | 멈춤 조건 때문에 뒤쪽 공고의 지원자 수, 마감 변경은 갱신되지 않는다 → 하루 1번 갱신 작업으로 보완 예정 |
| 정렬 순서 | 프리모아 목록이 진짜 최신 등록순이 아니면 멈춤 조건이 틀린다 → 등록일(POSTED_AT) 순서를 확인해야 함 |
| 실행 환경 | 서버가 켜져 있을 때만 돈다 |
| 서버 여러 대 | 각자 돌아서 하나는 매번 건너뛴다 → 실무에선 ShedLock 같은 분산 락, 또는 배치 전용 서버 |

### Spring Batch를 안 쓴 이유
이 규모(하루 수십 번, 몇 페이지)에서는 `@Scheduled`로 충분하다. Spring Batch는 재시작 지점, 청크 단위 커밋, 실행 이력 테이블 같은 기능이 필요할 때 쓴다. 그런데 이력(TB_CRAWL_RUN)과 건별 커밋은 이미 직접 만들어 두었다.

## 4. 화면 (넥사크로)

하단 수집 영역에 버튼 2개와 진행 상황 표시를 붙였다.

| 버튼 | 서버 API | 동작 |
|---|---|---|
| 수집 실행 | `/nx/crawl` | 기존. 페이지 수만큼, 끝나면 결과 표시 |
| 새 공고만 | `/nx/crawl/incremental` | 증분 수집. 보통 금방 끝나서 결과를 기다림 |
| 전체 수집 | `/nx/crawl/full` → `/nx/crawl/run` 반복 | 접수 후 3초마다 진행 상황을 물어봄 (폴링) |

### 폴링 흐름
```
[전체 수집] 클릭
  └─ /nx/crawl/full  → ds_run (runId, RUNNING) 즉시 응답
       └─ setTimer(1, 3000)
            └─ 3초마다 Form_ontimer → /nx/crawl/run (runId) → sta_runStatus 갱신
                 └─ status가 RUNNING이 아니면 killTimer, 버튼 다시 켜기, 목록 새로고침
```
- 앞의 폴링 응답이 오기 전에 다음 타이머가 울리면 건너뛴다 (`this.polling` 표시). 서버가 느릴 때 요청이 쌓이지 않게 하려고.
- 폴링이 실패하면 타이머부터 멈춘다. 안 그러면 3초마다 alert가 뜬다.

### 넥사크로 GET/POST 함정
인자가 없는 transaction은 GET으로 나간다 (06 트러블슈팅 11번). 조회(`/nx/skills`)는 GET이어도 괜찮지만, 데이터를 바꾸는 수집 요청은 POST여야 한다.
그래서 수집 버튼은 서버가 쓰지 않는 인자(`type=FULL`, `type=INCREMENTAL`)라도 항상 하나 이상 보낸다.

### Studio에 반영
Studio 프로젝트의 폼(`C:\nexacro\projects\freemoa_nx\Base\frmProjectList.xfdl`)은 Import 때 복사된 것이라 저장소 파일이 바뀌어도 자동으로 안 바뀐다.
Studio에서 폼 탭을 닫고 → 저장소의 `nexacro/frmProjectList.xfdl`을 위 경로에 덮어쓰기 → 폼을 다시 연다.

## 5. 로직 비교: 전체 수집 vs 신규 수집

### 공통 루프 (`CrawlService.execute()`)
받아오는 건 10개씩, 저장은 1건씩이다.
```
페이지 = 1
반복:
  ① 프리모아에 페이지 요청 → 공고 10개 (JSON)
  ② 10개를 하나씩 변환 (실패한 건은 경고 로그 후 건너뜀)
  ③ 저장: 1건씩, 건마다 트랜잭션 따로
       공고 1건 = MERGE + 기술 연결(DELETE 후 INSERT) + 스냅샷 INSERT → 커밋
  ④ TB_CRAWL_RUN 진행 건수 갱신
  ⑤ 멈출지 판단          ← 수집 종류마다 여기만 다름
  ⑥ 1초 쉬고 다음 페이지
끝: TB_CRAWL_RUN에 SUCCESS / PARTIAL / FAIL
```
- ①이 10개 단위인 건 프리모아가 한 페이지에 10건씩 주기 때문이다.
- ③은 한 행씩이라 10개 중 1개가 실패해도 나머지 9개는 커밋된다.

### 차이점
| | 전체 수집 (FULL) | 신규 수집 (INCREMENTAL) |
|---|---|---|
| 시작 | 사람이 버튼 / API | 스케줄러 (평일 9~18시 정각) |
| 실행 스레드 | `crawl-1` (백그라운드) | `scheduling-1` |
| 응답 | runId 즉시, 진행은 폴링 | 끝날 때까지 기다림 (보통 수 초) |
| ⑤ 멈추는 조건 | 마지막 페이지 도달 | 새 공고(INSERT) 0건 페이지가 2번 연속 |
| 페이지 상한 | 1000 (`full-max-pages`) | 10 (`incremental.max-pages`) |
| 보통 페이지 수 | 약 437 | 1~2 |

신규 수집의 멈춤 예:
```
1페이지: 신규 3, 갱신 7  → 계속 (연속 0)
2페이지: 신규 0, 갱신 10 → 계속 (연속 1)
3페이지: 신규 0, 갱신 10 → 멈춤 (연속 2)
```
목록이 최신순이라 새 공고가 끊기면 그 뒤는 이미 아는 공고로 본다.

### 이전 배치 과제, Spring Batch와 비교
| | 이전 배치 과제 (Tasklet) | 이번 수집 | Spring Batch Chunk |
|---|---|---|---|
| 읽기 | DB에서 대상 조회 | 외부 API에서 10개씩 | Reader가 N개씩 |
| 쓰기 | 한 행씩 UPDATE | 한 건씩 MERGE (+ 기술, 스냅샷) | Writer가 N개 한 번에 |
| 커밋 단위 | Tasklet 단위 또는 건별 | 건별 | Chunk(N개) 단위 |
| 이력 | Spring Batch 메타 테이블 | `TB_CRAWL_RUN` 직접 설계 | `BATCH_JOB_EXECUTION` 등 |

이번 구조는 읽기는 Chunk처럼 10개씩, 커밋은 그보다 잘게 1건씩이다.
1건 실패가 나머지에 영향을 주지 않는 대신 커밋 횟수가 많아 조금 느리다. 이 규모(수천 건)에서는 안전한 쪽을 택했다.

## DB 변경
`db/03_alter_crawl_run_type.sql`: `TB_CRAWL_RUN.RUN_TYPE` 컬럼 + CHECK 제약조건.
NOT NULL 컬럼을 행이 있는 테이블에 추가하려면 DEFAULT가 필요하다 (기존 행은 MANUAL로 채워짐).

## 확인
```sql
-- 수집 종류별 이력
SELECT RUN_ID, RUN_TYPE, STATUS, PAGE_COUNT, FETCHED_CNT, INSERTED_CNT, UPDATED_CNT,
       STARTED_AT, ENDED_AT, ERROR_MSG
  FROM TB_CRAWL_RUN
 ORDER BY RUN_ID DESC;
```

프리모아 목록이 최신 등록순인지는 06 문서의 curl로 1페이지를 받아서, `INS_TIME` 값이 위에서 아래로 줄어드는지 보면 된다.

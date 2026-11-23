# 03. Spring 이전 + MERGE INTO 저장

## 1. 조립 코드가 어디로 갔나 (IoC / DI)

| 2단계 `PlainCrawlerMain` | 3단계 `CrawlerConfig` |
|---|---|
| `HttpClient httpClient = HttpClient.newBuilder()...` | `@Bean HttpClient freemoaHttpClient()` |
| `ObjectMapper objectMapper = new ObjectMapper();` | 스프링 부트가 이미 빈으로 만들어 둠 → 파라미터로 받기만 함 |
| `new FreemoaApiClient(httpClient, objectMapper, "https://...")` | `@Bean freemoaApiClient(HttpClient, ObjectMapper, CrawlerProperties)` |
| `new ProjectConverter(skillParser)` | `@Bean projectConverter(SkillParser)` |
| `new ProjectCollector(apiClient, converter, Duration.ofSeconds(1))` | `@Bean projectCollector(..., CrawlerProperties)` |
| 생성 순서를 **내가** 맞춰야 함 | 파라미터 타입을 보고 **스프링이** 순서를 정함 |
| URL, 딜레이가 코드에 박혀 있음 | `application.yml`의 `crawler.*` |

**IoC(제어의 역전)**: 객체를 만들고 연결하는 "제어권"이 내 코드에서 스프링 컨테이너로 넘어갔다.
**DI(의존성 주입)**: 필요한 객체를 내가 만들지 않고 생성자 파라미터로 받는다.

클래스들(`FreemoaApiClient` 등)은 2단계에서 **한 줄도 안 바뀌었다.**
처음부터 생성자로 의존 객체를 받게 짜뒀기 때문에, 조립하는 쪽만 바꾸면 됐다.

### @Bean vs @Component
| | `@Bean` (CrawlerConfig) | `@Service` / `@Component` (ProjectSaveService) |
|---|---|---|
| 붙이는 곳 | 설정 클래스의 메서드 | 클래스 자체 |
| 객체 생성 | 내가 `new` 해서 반환 | 스프링이 생성자를 호출 |
| 쓰는 때 | 외부 라이브러리 클래스, 생성 로직이 복잡할 때 | 내가 만든 클래스 |

## 2. MERGE INTO

```sql
MERGE INTO TB_PROJECT T
USING DUAL
   ON (T.PROJ_IDX = :projIdx)
WHEN MATCHED     THEN UPDATE SET T.TITLE = :title, ... , T.LAST_CRAWLED_AT = SYSDATE
WHEN NOT MATCHED THEN INSERT (...) VALUES (...)
```

SELECT로 확인한 다음 INSERT 또는 UPDATE를 하면 SQL이 2번 나가고,
그 사이에 다른 요청이 같은 행을 넣으면 PK 중복 에러가 날 수 있다. MERGE는 한 문장으로 처리한다.

| 주의점 | 내용 |
|---|---|
| ON 절 컬럼은 UPDATE 불가 | `ORA-38104` |
| `FIRST_CRAWLED_AT` | UPDATE SET에 넣지 않아서 최초 수집일이 보존됨 |
| CLOB 바인딩 | `#{summary, jdbcType=CLOB}` |
| MERGE 안의 시퀀스 | INSERT가 안 일어나도 번호가 소모될 수 있음 → SKILL_ID 중간이 비는 건 정상 |

## 3. 트랜잭션 경계를 어디에 두었나

```
CrawlService.crawl()                  ← @Transactional 없음
 ├─ projectCollector.collect()        ← HTTP, 페이지당 1초 대기 (느림)
 └─ projectSaveService.saveAll()      ← @Transactional 없음 (반복만 담당)
      └─ projectWriter.saveOne()      ← @Transactional (공고 1건 = 트랜잭션 1개)
```
`CrawlService`에 `@Transactional`을 붙이면 HTTP 수집 내내 DB 커넥션을 붙잡는다.
커넥션 풀(HikariCP 기본 10개)이 바닥나면 다른 요청이 전부 멈춘다.

3단계 처음에는 `saveAll` 전체에 `@Transactional`을 붙였지만,
4단계에서 공고 1건 단위(`ProjectWriter.saveOne`)로 쪼갰다. 이유는 [04-transaction.md](04-transaction.md) 참고.

## 4. 실무에서 만나는 함정들 (코드 주석에도 있음)
- **IN 절 1000개 제한** (`ORA-01795`): `selectExistingProjIdx`
- **빈 리스트로 IN ()**: SQL 문법 에러 → `saveAll` 첫 줄에서 막음
- **페이지 밀림 중복**: 수집 도중 새 공고가 올라오면 같은 공고가 다음 페이지에 또 나온다 → `Set.add` 반환값으로 처리
- **`${}` vs `#{}`**: `#{}`는 바인딩(안전), `${}`는 문자열 그대로 붙임(SQL 인젝션 위험)

## 5. 확인용 SQL (DBeaver)
```sql
-- 수집된 공고
SELECT PROJ_IDX, TITLE, COST_MAX, DEADLINE, FIRST_CRAWLED_AT, LAST_CRAWLED_AT
  FROM TB_PROJECT
 ORDER BY POSTED_AT DESC;

-- 기술스택별 공고 수
SELECT S.SKILL_NAME, COUNT(*) AS CNT
  FROM TB_PROJECT_SKILL PS
  JOIN TB_SKILL S ON S.SKILL_ID = PS.SKILL_ID
 GROUP BY S.SKILL_NAME
 ORDER BY CNT DESC;

-- 같은 요청을 두 번 실행한 뒤: FIRST_CRAWLED_AT은 그대로, LAST_CRAWLED_AT만 바뀌었는지
SELECT PROJ_IDX, FIRST_CRAWLED_AT, LAST_CRAWLED_AT
  FROM TB_PROJECT
 WHERE FIRST_CRAWLED_AT <> LAST_CRAWLED_AT;
```

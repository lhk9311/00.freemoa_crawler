# 11. 통계 차트 + JPA 일부 적용

## 1. 통계 화면

- 화면: 서버 실행 후 http://localhost:8080/stats.html
- 데이터: `GET /api/stats` (StatsController → StatsService → StatsMapper.xml)
- 화면: 기술·지역은 HTML 막대 목록(이름 · 막대 · 비율 · 건수 한 줄), 예산·월별은 Apache ECharts 세로 막대.
  Spring Boot는 `src/main/resources/static/` 아래 파일을 그대로 내보내서 컨트롤러가 필요 없다.

| 화면 | SQL 포인트 |
|---|---|
| 기술별 요구 비율 (상위 15) | GROUP BY 기술키, 전체 공고 수를 인라인 뷰로 CROSS JOIN, 평균 지원자 AVG |
| 지역별 비율 | `RATIO_TO_REPORT(COUNT(*)) OVER ()` |
| 예산 구간 | 인라인 뷰에서 CASE로 구간·정렬 순서를 만들고 바깥에서 GROUP BY |
| 월별 신규 | 최근 12개월 GROUP BY 월, `ADD_MONTHS(TRUNC(SYSDATE,'MM'), -11)` |

### 화면 설계에서 정한 것
| 결정 | 이유 |
|---|---|
| 강조색 1개(teal) + 회색 | 한 차트에 시리즈가 하나라 색을 여러 개 쓸 이유가 없다. "기타", "협의"처럼 덜 중요한 항목만 회색 |
| 도넛 대신 순위 막대 목록 | 비율끼리 비교하려면 같은 기준선에서 시작하는 막대가 더 정확하게 읽힌다 |
| 월별 차트에서 누적선 제거 | 막대(신규)와 선(누적)은 크기 단위가 달라서 y축이 두 개 필요했다. 축이 두 개면 어느 값이 어느 축인지 헷갈린다. 누적(최근 12개월 합계)은 위쪽 숫자 카드로 옮김 |
| 숫자 카드 4개 | 한 숫자로 끝나는 정보(전체 건수, 1위 기술)는 차트보다 숫자 하나가 빠르게 읽힌다 |
| 표로 보기 | 색을 구분하기 어려운 사람, 정확한 숫자가 필요한 사람을 위한 대체 수단 |
| 다크 모드 | OS 설정을 따라감. 색은 어두운 배경 기준으로 다시 골라 대비 확인 |

### 윈도우 함수
일반 집계(COUNT, SUM)는 GROUP BY로 행을 **줄인다**. 윈도우 함수는 행을 줄이지 않고 각 행 옆에 **전체(또는 구간) 기준 값**을 붙인다.

```sql
-- 지역별 건수 + 전체 대비 비율
SELECT REGION, COUNT(*) AS CNT,
       ROUND(RATIO_TO_REPORT(COUNT(*)) OVER () * 100, 1) AS PCT
  FROM TB_PROJECT
 GROUP BY REGION;
```
결과 모양 (숫자는 예시):

| REGION | CNT | PCT |
|---|---|---|
| 서울 | 2100 | 48.2 |
| 경기 | 900 | 20.7 |
| ... | | |

| 함수 | 결과 |
|---|---|
| `RATIO_TO_REPORT(x) OVER ()` | 전체 합 대비 비율 (0~1) |
| `SUM(x) OVER (ORDER BY 월)` | 처음부터 이 행까지 누적 |
| `RANK() OVER (ORDER BY x DESC)` | 순위 (동점 같은 순위, 다음 순위 건너뜀) |
| `ROW_NUMBER() OVER (PARTITION BY 그룹 ORDER BY x)` | 그룹마다 1부터 번호 (그룹별 1등 뽑기에 자주 씀) |

`OVER ()` 안이 비어 있으면 결과 전체가 기준, `PARTITION BY`를 쓰면 그룹마다 따로 계산한다.

### 직접 해 볼 SQL
```sql
-- 지역별로 지원자가 가장 많이 몰린 공고 1건씩 (ROW_NUMBER + PARTITION BY)
SELECT REGION, TITLE, APPLY_COUNT
  FROM (SELECT REGION, TITLE, APPLY_COUNT,
               ROW_NUMBER() OVER (PARTITION BY REGION ORDER BY APPLY_COUNT DESC) AS RN
          FROM TB_PROJECT)
 WHERE RN = 1
 ORDER BY APPLY_COUNT DESC;
```

## 2. JPA 일부 적용

수집 이력 **목록 조회만** JPA로 만들었다. 저장(쓰기)은 그대로 MyBatis다.

| API | 기술 | 파일 |
|---|---|---|
| `GET /api/crawl/runs/{runId}` (1건) | MyBatis | `CrawlRunMapper.xml` |
| `GET /api/crawl/history?runType=&size=` (목록 + 종류별 요약) | JPA | `history/` 패키지 |

### 구성
| 파일 | 역할 |
|---|---|
| `CrawlRunEntity` | `TB_CRAWL_RUN`과 매핑한 엔티티. `@Immutable`로 조회 전용 |
| `CrawlRunRepository` | 인터페이스만 만들면 스프링이 구현체를 만들어 줌 |
| `RunTypeSummary` | 필요한 값만 받는 인터페이스 프로젝션 |
| `CrawlHistoryService`, `CrawlHistoryController` | 조회 API |

### 쿼리를 만드는 세 가지 방법
```java
// 1) 기본 제공: findById, findAll, count ...
crawlRunRepository.findById(25L);

// 2) 메서드 이름으로 생성 (쿼리 메서드)
List<CrawlRunEntity> findByRunTypeOrderByRunIdDesc(String runType, Pageable pageable);
// -> select ... where run_type=? order by run_id desc fetch first ? rows only

// 3) JPQL 직접 작성 (엔티티/필드 이름을 쓴다, 테이블/컬럼 아님)
@Query("select r.runType as runType, count(r) as runCount ... from CrawlRunEntity r group by r.runType")
List<RunTypeSummary> summarizeByRunType();
```
콘솔에서 `org.hibernate.SQL` 로그로 JPA가 만든 SQL을, `org.hibernate.orm.jdbc.bind`로 바인딩 값을 볼 수 있다. MyBatis 로그(`==> Preparing`)와 나란히 비교해 보자.

### 설정에서 꼭 볼 것
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: none      # JPA가 테이블을 만들거나 바꾸지 못하게
    open-in-view: false
```
`ddl-auto`를 `create`나 `update`로 두면 엔티티 모양대로 테이블을 지우거나 바꾼다. 테이블을 SQL로 직접 관리하는 프로젝트(SI 대부분)에서는 `none` 또는 `validate`로 둔다.

### MyBatis vs JPA
| | MyBatis | JPA |
|---|---|---|
| SQL | 직접 작성 (XML) | 대부분 자동 생성 |
| 복잡한 조회 (통계, 윈도우 함수, 힌트) | 강함 | JPQL로 어렵고 결국 네이티브 SQL |
| 단순 CRUD | 반복 SQL이 많음 | 거의 코드 없음 |
| 결과 매핑 | 컬럼 → 필드 (resultMap, camelCase) | 엔티티 매핑 |
| DB 변경 영향 | SQL 직접 수정 | 엔티티만 수정 (방언은 Hibernate가 처리) |
| 국내 SI | 표준에 가까움 | 신규 서비스, 자사 서비스에서 많이 씀 |

한 프로젝트에서 둘을 같이 쓰는 경우도 흔하다. 단순 CRUD는 JPA, 복잡한 조회·대량 처리는 MyBatis(또는 QueryDSL, JdbcTemplate)로 나눈다.
같은 테이블에 두 기술이 동시에 **쓰면** 1차 캐시(영속성 컨텍스트)와 실제 DB 값이 어긋날 수 있다. 이 프로젝트는 쓰기 = MyBatis, 이력 조회 = JPA로 역할을 나눴다.

### 트랜잭션은 어떻게 되나
JPA를 추가하면 스프링 부트가 트랜잭션 매니저를 `DataSourceTransactionManager`에서 `JpaTransactionManager`로 바꾼다.
`JpaTransactionManager`는 같은 DB 연결을 MyBatis에도 넘겨주기 때문에, 기존 `@Transactional`(REQUIRES_NEW 포함)은 그대로 동작한다.

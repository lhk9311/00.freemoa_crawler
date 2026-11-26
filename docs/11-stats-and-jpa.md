# 11. 통계 화면 + JPA 일부 적용

## 1. 통계 화면 (백엔드 기술 수요)

- 화면: 넥사크로 `nexacro/frmStats.xfdl`. 공고 목록 화면의 [통계 보기] 버튼으로 새 창이 열린다
- 데이터 (넥사크로 XML)
  - `/nx/stats`: 숫자 카드, 언어 / 프레임워크 / DB / 인프라·서버 순위, 지역 비율, 기술 콤보 목록
  - `/nx/stats/together` (인자 `tech=Java`): Java 공고에서 같이 요구된 기술 순위
- 같은 데이터를 JSON으로도 낸다: `GET /api/stats`, `GET /api/stats/together?tech=Java` (Postman, 테스트용)
- 11/23: 예산 구간, 월별 신규 공고를 빼고 백엔드 개발자가 궁금한 기술 위주로 바꿨다
- 11/26: 처음엔 Spring Boot `static/stats.html`(HTML + 차트 라이브러리)로 만들었다가, 화면 기술을 넥사크로 하나로 통일했다

### 흐름
```
frmStats.xfdl  Form_onload
  └─ transaction("stats", "svc::nx/stats", ...)           인자가 없어서 GET
       └─ StatsScreenController.stats()
            └─ StatsService.getStats()
                 ├─ StatsMapper.countProjects()          전체 공고 수
                 ├─ StatsMapper.selectProjectSkills()    공고 x 기술 태그 원본 (수만 줄)
                 │    └─ TechStatsCalculator             자바로 묶기
                 │         ├─ BackendTechCatalog.find()  "php 8.5" -> PHP (언어)
                 │         ├─ 공고별로 기술 이름 중복 제거
                 │         └─ 기술별 공고 수, 평균 지원자, 평균 예산
                 └─ StatsMapper.selectRegionStats()      지역 비율 (윈도우 함수)
       <- ds_kpi, ds_lang, ds_fw, ds_db, ds_infra, ds_region, ds_tech
  └─ fnCallback("stats") -> 숫자 카드 채우기 -> 1위 언어로 fnLoadTogether()
       └─ transaction("together", "svc::nx/stats/together", ..., "tech='Java'")
```

### 넥사크로에서 막대 그리기
차트 컴포넌트 없이 Grid 셀 하나로 막대를 그린다.
```xml
<Cell col="2" text="bind:bar" displaytype="progressbarcontrol"/>
```
`progressbarcontrol`은 0~100 값을 막대 길이로 그린다. 비율(pct)을 그대로 넣으면 1등이 30%일 때 막대가 30%까지만 차서
차이가 잘 안 보인다. 그래서 서버(`StatsScreenController.bar()`)가 목록 안 1등을 100으로 맞춘 값(bar)을 따로 계산해서 보낸다.

묶음 그리드 4개는 이벤트 함수 하나(`grdGroup_oncellclick`)를 같이 쓴다. 누른 그리드의 Dataset은 `obj.getBindDataset()`으로 꺼낸다.

### 왜 기술 묶기를 SQL이 아니라 자바에서 하나
프리모아 기술 태그는 사람이 직접 적어서 `PHP`, `php 8.5`, `php7`처럼 버전만 다른 표기가 많다.
같은 공고에 `PHP`와 `php 8.5`가 같이 달려 있으면, SQL에서 SKILL_KEY별로 COUNT 하면 그 공고의 PHP가 두 번 세어진다.

"버전을 떼면 같은 기술"이라는 규칙과 "PHP는 언어, MySQL은 DB"라는 분류표는 자바(`BackendTechCatalog`)에 두었다.
그래서 SQL은 원본(공고 x 태그)만 가져오고, 묶기는 `TechStatsCalculator`가 한다.
DB도 스프링도 안 쓰는 순수 자바라 단위 테스트(`TechStatsCalculatorTest`)로 확인할 수 있다.

SQL로 하려면 분류표를 테이블(`TB_SKILL_GROUP` 같은)로 만들고 조인하면 된다. 분류가 자주 바뀌거나 화면에서 관리해야 하면 그쪽이 맞다.

| 화면 | 어디서 계산 |
|---|---|
| 묶음별 기술 순위 (비율, 평균 지원자, 평균 최대예산) | `TechStatsCalculator.groupStats()` |
| 백엔드 기술을 요구한 공고 수 | `TechStatsCalculator.countBackendProjects()` |
| 같이 요구되는 기술 | `TechStatsCalculator.together()` |
| 지역별 비율 | SQL `RATIO_TO_REPORT(COUNT(*)) OVER ()` |

### 분류표에 기술 추가하기
`BackendTechCatalog`의 static 블록에 한 줄 넣는다. 별칭은 `SkillParser.toKey` 모양(공백 없음, 대문자)으로 적는다.
```java
add(TechGroup.DATABASE, "Cassandra", "CASSANDRA");
```
분류표에 없는 기술(React, jQuery 등)은 묶음 카드에는 안 나오고, "같이 요구되는 기술"에서 기타(회색)로만 보인다.
한글 태그(제안요청, 유지보수 등)는 분류표에 없으면 기술이 아니라고 보고 뺀다.

### 화면 설계에서 정한 것
| 결정 | 이유 |
|---|---|
| 막대 색은 넥사크로 기본 테마 그대로 | 시리즈가 하나라 색을 여러 개 쓸 이유가 없다 |
| 도넛 대신 순위 막대 목록 | 비율끼리 비교하려면 같은 기준선에서 시작하는 막대가 더 정확하게 읽힌다 |
| 기술 이름을 누르면 "같이 요구되는 기술"이 바뀜 | 순위에서 바로 "그럼 Java 공고는 뭘 같이 원하지?"로 이어서 볼 수 있게 |
| 평균 지원자, 평균 예산은 묶음 그리드에선 마우스 올림(tooltiptext), 같이 요구되는 기술 그리드에선 컬럼 | 좁은 그리드에 숫자가 많으면 읽기 어렵다 |
| 숫자 카드 4개 | 한 숫자로 끝나는 정보(전체 건수, 1위 언어)는 차트보다 숫자 하나가 빠르게 읽힌다 |

### 윈도우 함수

SI 통계·집계 화면에서 정말 많이 쓰고 면접에도 나온다.

#### GROUP BY랑 뭐가 다르냐

**GROUP BY는 행을 줄인다.**
```sql
SELECT REGION, COUNT(*) FROM TB_PROJECT GROUP BY REGION;
```
| REGION | COUNT |
|---|---|
| 서울 | 2100 |
| 경기 | 900 |

공고 4,000건이 지역 수만큼으로 줄었다.

**윈도우 함수는 행을 안 줄이고, 옆에 값을 붙인다.**
```sql
SELECT REGION, TITLE, APPLY_COUNT,
       COUNT(*) OVER (PARTITION BY REGION) AS 지역공고수
  FROM TB_PROJECT;
```
| REGION | TITLE | APPLY_COUNT | 지역공고수 |
|---|---|---|---|
| 서울 | 쇼핑몰 구축 | 12 | 2100 |
| 서울 | 앱 개발 | 5 | 2100 |
| 경기 | ERP 유지보수 | 3 | 900 |

공고 4,000건이 그대로 있고, 각 행 옆에 "내 지역 공고는 총 몇 건"이 붙었다. (숫자는 예시)

#### 문법 읽는 법

```sql
함수() OVER (PARTITION BY 그룹 ORDER BY 정렬)
```
| 부분 | 뜻 |
|---|---|
| `OVER` | "이건 윈도우 함수다" 표시 |
| `PARTITION BY REGION` | 지역별로 따로따로 계산 (GROUP BY처럼 묶되, 행은 안 줄임) |
| `ORDER BY ...` | 그 그룹 안에서의 순서 (순위, 누적을 계산할 때) |
| `OVER ()` 괄호가 비어 있음 | 전체를 하나로 보고 계산 |

#### 자주 쓰는 4개

| 함수 | 결과 | SI 예시 |
|---|---|---|
| `ROW_NUMBER()` | 1, 2, 3, 4 (무조건 다른 번호) | 그룹별 1등만 뽑기, 페이징 |
| `RANK()` | 1, 2, 2, 4 (동점은 같은 순위, 다음 순위 건너뜀) | 매출 순위 |
| `SUM(x) OVER (ORDER BY 월)` | 누적 합계 | 월별 누적 매출 |
| `RATIO_TO_REPORT(x) OVER ()` | 전체 대비 비율 0~1 (Oracle 전용) | 지역별 비율 (통계 화면에 쓴 것) |

통계 화면에 쓴 예:
```sql
-- 지역별 건수 + 전체 대비 비율
SELECT REGION, COUNT(*) AS CNT,
       ROUND(RATIO_TO_REPORT(COUNT(*)) OVER () * 100, 1) AS PCT
  FROM TB_PROJECT
 GROUP BY REGION;
```
| REGION | CNT | PCT |
|---|---|---|
| 서울 | 2100 | 48.2 |
| 경기 | 900 | 20.7 |

GROUP BY로 먼저 지역별 건수를 만들고, 그 결과 전체를 기준으로 비율을 붙인 것이다.

#### 제일 많이 쓰는 패턴: 그룹별 1등 뽑기

"지역별로 지원자가 제일 많은 공고 1건씩"
```sql
SELECT REGION, TITLE, APPLY_COUNT
  FROM (
        SELECT REGION, TITLE, APPLY_COUNT,
               ROW_NUMBER() OVER (PARTITION BY REGION        -- ① 지역별로 나눠서
                                  ORDER BY APPLY_COUNT DESC) -- ② 지원자 많은 순으로
                 AS RN                                       -- ③ 1, 2, 3... 번호 붙이고
          FROM TB_PROJECT
       )
 WHERE RN = 1                                                -- ④ 1번만 남기기
 ORDER BY APPLY_COUNT DESC;
```
- 안쪽에서 번호를 붙이고, 바깥에서 `RN = 1`만 거른다.
- **왜 한 번 감싸나:** 윈도우 함수 결과(`RN`)는 같은 SELECT의 WHERE에서 바로 못 쓴다.
  WHERE가 먼저 실행되고 윈도우 함수는 나중에 계산되기 때문이다. 그래서 인라인 뷰(서브쿼리)로 감싼다.

SI에서 "사원별 최근 이력 1건", "제품별 최신 가격", "고객별 마지막 주문" 같은 화면이 다 이 모양이다.
docs/05의 ROWNUM 페이징과 같이 기억해 둔다.

#### 실습 (DBeaver)

1. 위 "그룹별 1등 뽑기" 쿼리를 그대로 실행한다. 지역마다 1건씩 나오면 성공.
2. `ROW_NUMBER()`를 `RANK()`로, `WHERE RN = 1`을 `WHERE RN <= 2`로 바꿔서 실행한다.
   지원자 수가 같은 공고가 있으면 3건 이상 나오는 지역이 생긴다. 왜 그런지 위 "자주 쓰는 4개" 표를 보고 생각해 본다.
3. 혼자 써 보기: **분야(CATEGORY)별로 예산(COST_MAX)이 제일 큰 공고 1건씩** 뽑는다.
   "그룹별 1등 뽑기"에서 컬럼 두 개만 바꾸면 된다. COST_MAX가 null인 공고는 `WHERE COST_MAX IS NOT NULL`로 뺀다.

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

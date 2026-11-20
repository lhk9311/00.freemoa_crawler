# 10. 테스트 코드 + API 문서 (JUnit, Mockito, REST Docs)

## 테스트 목록

| 테스트 | 종류 | DB 필요 | 무엇을 |
|---|---|---|---|
| `SkillParserTest` | 단위 | X | 기술스택 문자열 정리 |
| `ProjectConverterTest` | 단위 | X | JSON 샘플 → Project 변환, 0000-00-00 |
| `NxXmlTest` | 단위 | X | 넥사크로 XML 읽기/쓰기, XXE, 이스케이프 |
| `CrawlServiceStopConditionTest` | 단위 (Mockito) | X | 증분 수집 멈춤 조건 |
| `CrawlControllerDocsTest` | 웹 계층 (MockMvc) + 문서 | X | 수집 API 응답, REST Docs 생성 |
| `TransactionLabTest` | 통합 (@SpringBootTest) | O | 트랜잭션 전파, 롤백 규칙 실험 |

실행: 테스트 클래스나 `src/test/java` 폴더 우클릭 → Run. 전체는 Maven 탭 → Lifecycle → `test`.

## 1. 단위 테스트 + Mockito (`CrawlServiceStopConditionTest`)

### 왜 가짜(Mock)를 쓰나
멈춤 조건만 확인하고 싶은데, 진짜로 돌리면 프리모아 요청과 Oracle 저장이 필요하다. 느리고, 원하는 상황(1페이지 신규 3건, 2페이지 0건...)을 만들 수도 없다.
그래서 **검사할 대상(CrawlService)만 진짜**로 두고, 주변(수집기, 저장, 이력)은 시키는 대로 답하는 가짜로 바꾼다.

```java
@ExtendWith(MockitoExtension.class)   // @Mock 필드를 가짜 객체로 만들어 줌
class CrawlServiceStopConditionTest {
    @Mock ProjectCollector projectCollector;     // 가짜 수집기
    @Mock ProjectSaveService projectSaveService; // 가짜 저장
    @Mock CrawlRunService crawlRunService;       // 가짜 이력
```

### given - when - then
```java
// given: 페이지별 신규 건수 시나리오
givenPages(3, 0, 0, 7);
// when: 실행
CrawlResult result = crawlService.crawlIncremental();
// then: 3페이지에서 멈췄나
assertThat(visitedPages).containsExactly(1, 2, 3);
verify(projectSaveService, times(3)).saveAll(eq(RUN_ID), any());
```

| Mockito | 뜻 |
|---|---|
| `when(x.method()).thenReturn(값)` | 이 호출엔 이 값을 돌려줘 |
| `thenAnswer(invocation -> ...)` | 호출 인자를 보고 답을 계산해서 돌려줘 |
| `doAnswer(...).when(x).method(...)` | 위와 같음 (콜백을 실행해야 할 때 이 형태가 편함) |
| `verify(x, times(3)).method(...)` | 실제로 3번 불렸는지 확인 |
| `any()`, `eq(값)`, `anyString()` | 인자 매칭 |

### 테스트 케이스 고르는 법
| 케이스 | 이유 |
|---|---|
| 신규 0건 2번 연속 → 멈춤 | 정상 동작 |
| 중간에 신규가 끼면 연속 횟수 초기화 | "연속"의 의미 확인 |
| 첫 페이지부터 0건 | 시작 지점 |
| NO_STOP | 다른 모드에 영향 없는지 |
| maxPages 상한 | 상한이 멈춤 조건보다 먼저 걸리는 경우 |
| stopAfterIdlePages = 1 | 경계값 |

## 2. 웹 계층 테스트 + REST Docs (`CrawlControllerDocsTest`)

### @WebMvcTest
컨트롤러만 띄운다. DB, MyBatis, 크롤러는 안 뜬다. 서비스는 `@MockitoBean`(스프링 빈 자리에 가짜를 넣음)으로 대신한다.
`MockMvc`로 실제 HTTP 요청처럼 호출하고 상태 코드, JSON 값을 검증한다.

### 문서가 만들어지는 흐름
```
mvn test
  └─ CrawlControllerDocsTest 통과
       └─ target/generated-snippets/crawl-full/http-request.adoc, http-response.adoc, response-fields.adoc ...
mvn package (prepare-package 단계)
  └─ src/docs/asciidoc/api.adoc 이 위 조각들을 include
       └─ target/generated-docs/api.html
            └─ static/docs/api.html 로 복사 → 서버 실행 후 http://localhost:8080/docs/api.html
```
IntelliJ: Maven 탭 → Lifecycle → `package` 더블클릭. DB가 필요한 `TransactionLabTest`는 환경변수 `DB_PASSWORD`가 없으면 자동으로 건너뛴다.
`target/generated-docs/api.html`을 브라우저로 바로 열어도 된다.

### 일부러 깨 보기
`CrawlResult`에 필드를 하나 추가하고 테스트를 돌리면 실패한다.
```
Fields with the following paths were not documented: [새필드]
```
문서에 안 적은 필드가 응답에 생겼다는 뜻이다. 문서와 실제 API가 어긋나는 걸 테스트가 막는다.

### REST Docs vs Swagger(springdoc)
| | Swagger | REST Docs |
|---|---|---|
| 문서 만드는 법 | 운영 코드에 어노테이션 (`@Operation` 등) | 테스트 코드에서 생성 |
| 문서와 실제가 어긋나면 | 틀린 문서가 그대로 나감 | 테스트 실패, 문서 생성 안 됨 |
| 화면에서 바로 호출 | 됨 (Try it out) | 안 됨 (읽는 문서) |
| 운영 코드 | 어노테이션이 섞임 | 깨끗함 |
| 들이는 노력 | 적음 | 테스트를 짜야 함 |
| 실무 | 국내 SI, 빠른 협업 | 테스트 문화가 있는 서비스 회사 |

실무에서는 외부 업체나 프론트 팀에 넘길 API 명세를 REST Docs HTML로 배포하거나, 둘을 섞어(REST Docs로 OpenAPI 스펙 생성 → Swagger UI로 보기) 쓰기도 한다.

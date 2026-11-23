# 13. 학습 노트 (11/23)

docs를 처음부터 다시 보면서 나온 질문과 답. Java 기본기 위주.

## 1. DB 설계

### 쉼표 문자열 대신 N:M 정규화
> 쉼표로 이어진 값은 중간 테이블로 쪼개서 N:M으로 정규화하고, 원본 문자열은 다시 계산할 수 있게 따로 남긴다.

- `"PHP, MySQL"` 그대로 두면 `LIKE '%Java%'`로 찾아야 한다 → `JavaScript`까지 걸리고, 앞에 `%`가 붙어서 인덱스도 못 탄다.
- 원문(`SKILL_RAW`)을 남기는 이유: 쪼개는 규칙(`SkillParser`)은 틀릴 수 있다. 원문이 있으면 다시 수집하지 않고 DB에서 다시 계산할 수 있다.
- 실무 예: 사용자-권한, 상품-태그, 학생-과목.

### 식별 관계 / 비식별 관계
> 부모를 가리키는 FK 컬럼이 자식의 PK에 포함되면 식별 관계, PK 밖의 일반 컬럼이면 비식별 관계.

| | 식별 | 비식별 |
|---|---|---|
| 부모 키 위치 | 자식 PK 안 | 자식 일반 컬럼 |
| ERD 선 | 실선 | 점선 |
| 예 | 주문 → 주문상세 (PK: 주문번호 + 순번) | 회원 → 주문 (주문번호만으로 구분) |
| 단점 | 자손으로 갈수록 PK가 길어짐 | 부모 키로 찾으려면 FK 인덱스 따로 필요 |

- N:M 중간 테이블은 기본이 식별 관계다. 양쪽 FK 두 개를 묶어 PK로 쓴다. 연결에 속성이 붙거나 JPA를 쓰면 별도 ID를 PK로 둔다(비식별).
- DBeaver 아이콘: 열쇠 = PK, 네모 안 화살표 = FK. 둘이 겹쳐 있으면 PK이면서 FK(식별 관계).

## 2. 크롤링 흐름 (전체 그림은 12번)

- 출발점은 우리 프로그램이다. 우리가 프리모아에 "데이터 줘"라고 요청하러 나간다.
- 프리모아 DB에 직접 붙는 게 아니라, 프리모아 웹 서버에 요청하면 웹 서버가 자기 DB를 조회해서 JSON으로 준다.
- **10건씩 가져와서, 1건씩 저장 확정(커밋)한다.**
  - 가져오는 단위(10건)는 프리모아가 정한 것
  - 커밋 단위(1건)는 우리가 정한 것
- 실무 연계·배치의 공통 뼈대: **읽기(FROM) → 변환 → 쓰기(TO)**. 새 업무는 FROM, 받는 형태, TO 세 칸부터 채운다.

## 3. 트랜잭션 범위

> 커밋은 가장 바깥 `@Transactional` 메서드가 끝날 때 일어난다.

- 트랜잭션 메서드가 다른 트랜잭션 메서드를 부르면 기본값(REQUIRED)은 바깥에 합류한다 → 커밋은 바깥이 끝날 때 한 번.
- `REQUIRES_NEW`는 바깥과 상관없이 자기가 끝날 때 바로 커밋한다 (수집 이력).

범위 정하는 질문 4개:
1. 같이 성공하거나 같이 실패해야 하나? → 한 트랜잭션 (이체, 주문, 공고 1건의 SQL 4개)
2. 서로 독립적인 건이 여러 개인가? → 나눈다 (공고 4,370건)
3. 오래 걸리는 게 섞여 있나? → 트랜잭션 밖으로 (외부 API 호출)
4. 실패해도 남아야 하는 기록인가? → 따로, REQUIRES_NEW (이력, 감사 로그)

실무 기본값: **서비스 메서드 1개 = 트랜잭션 1개.** Controller, Mapper에는 안 건다. 조회 전용은 `readOnly = true`.
대량 배치는 100~1,000건씩 묶는 중간값(Chunk)으로 타협하기도 한다.

이 프로젝트에서 트랜잭션이 걸린 곳:
| 무엇 | 위치 |
|---|---|
| 공고 1건 저장 | `ProjectWriter.saveOne` (`@Transactional`) |
| 기술 마스터 등록 | `SkillService` (REQUIRES_NEW) |
| 수집 이력 시작/진행/종료 | `CrawlRunService` (REQUIRES_NEW) |
| 반복만 하는 곳 | `ProjectSaveService.saveAll` (일부러 안 붙임) |

찾는 법: Ctrl+Shift+F로 `@Transactional` 검색.

## 4. Java 기본기

### record
값만 담는 클래스를 짧게 쓰는 문법. 필드는 전부 `private final`, 생성자·getter·equals·toString 자동.
- getter 이름은 `title()` (`getTitle()` 아님)
- setter 없음 → 불변. 한 번 만들면 못 바꾼다.
- 컴팩트 생성자 `public Project { ... }`: 만들 때 검사·보정 (`requireNonNull`, `List.copyOf`)

### int vs Integer
> null을 담으려고 Integer를 쓰고, 그 값을 쓰기 전에 null 검사로 방어한다.

| | int | Integer |
|---|---|---|
| null | 못 담음 (기본값 0) | 담을 수 있음 |
| 예 | `applyCount` (없으면 0명이 맞음) | `costMax` (null = 예산 미입력, 0원과 다름) |

- 방어: `hasBudget()` = `costMax != null`. `filter(Project::hasBudget)` 없이 `mapToInt`하면 NPE.
- `Integer`가 null인데 `int`로 받으면 NullPointerException.
- 반대로 절대 null이면 안 되는 값(`title`)은 생성 시점에 `requireNonNull`로 막는다.

### 예외 전환
| | 체크 예외 | 언체크 예외 |
|---|---|---|
| 부모 | `Exception` | `RuntimeException` |
| 예 | `IOException`, `InterruptedException` | `NullPointerException`, `IllegalArgumentException` |
| 컴파일러 | try-catch나 throws 강제 | 강제 안 함 |

> 체크 예외는 잡아서 의미 있는 이름의 런타임 예외로 바꾸고, 원인은 cause에 담아 둔다.

```java
} catch (IOException e) {
    throw new FreemoaApiException("프리모아 요청 실패 (page=" + page + ")", e);   // e = cause
}
```
- 위쪽 코드가 전부 `throws IOException`을 달 필요가 없어진다.
- 로그에 `Caused by:`로 원래 원인이 남는다.
- `@Transactional`은 기본적으로 언체크 예외에서만 롤백한다.

### InterruptedException
> 잡으면 `Thread.currentThread().interrupt()`로 중단 표시를 복구한다. 그냥 무시하면 안 된다.

catch하는 순간 "그만해" 표시가 지워진다. 복구하지 않으면 위쪽 코드가 중단 요청을 모르고 계속 돈다. `Thread.sleep()` 쓸 때마다 나오는 패턴.

### Map 기초
> Map = 컬럼이 2개(KEY, VALUE)이고 KEY가 PK인 작은 테이블. key는 찾을 때 쓰는 이름표.

```java
Map<String, String> byKey = new LinkedHashMap<>();
//  ↑ KEY 타입, VALUE 타입      ↑ 실제 상자 종류     ↑ <> 왼쪽과 같은 타입이라 생략, () 빈 상자 생성
```
- `Map`은 사용법 약속(인터페이스), `LinkedHashMap`은 실제 상자(구현체). `List` / `ArrayList`도 같은 관계.
- `< >` = 제네릭. "이 상자엔 이 타입만" 정하는 것.

| 메서드 | SQL 느낌 |
|---|---|
| `put(k, v)` | MERGE (있으면 덮어씀) |
| `putIfAbsent(k, v)` | 없을 때만 INSERT (처음 값 유지) |
| `get(k)` | KEY로 SELECT (없으면 null) |
| `containsKey(k)` | EXISTS |
| `values()` | VALUE 전체 |

| 상자 | 꺼낼 때 순서 |
|---|---|
| `HashMap` | 뒤죽박죽 |
| `LinkedHashMap` | 넣은 순서 |
| `TreeMap` | KEY 정렬 순서 |

Map을 쓰는 두 방식:
| | 방식 A | 방식 B |
|---|---|---|
| Map 하나 = | DB 행 1개 | 2컬럼 표 전체 |
| key = | 컬럼 이름 (`"TITLE"`) | 행을 구분하는 값 (`"MYSQL"`) |
| 예 | MyBatis `resultType="map"`, SI의 `Map<String, Object>` 파라미터 | SkillParser, 기술 ID 캐시, dedupe |

### SkillParser 한 줄씩
```java
Map<String, String> byKey = new LinkedHashMap<>();   // KEY: 비교용(MYSQL), VALUE: 표시용(MySQL)
for (String token : raw.split(",")) {                 // 쉼표로 자르기
    String name = token.trim();                       // 앞뒤 공백 제거
    if (name.isEmpty()) continue;                     // ,, 로 생긴 빈 칸은 건너뜀
    byKey.putIfAbsent(toKey(name), name);             // 처음 보는 키만 넣음 -> 중복 제거, 첫 표기 유지
}
return List.copyOf(byKey.values());                   // 넣은 순서대로 VALUE만
```
- 중복 제거는 Map(KEY 중복 불가)이, 순서 유지는 Linked가 한다.
- Set이 아니라 Map인 이유: 비교는 대문자 키로, 표시는 원래 표기로 하려고 둘을 같이 들고 있어야 해서.
- `ProjectSaveService.dedupe`는 `put`이라 "나중 것 유지, 위치는 처음 자리". 메서드 하나 차이로 결과가 달라진다.

### flatMap
리스트 안의 리스트를 한 겹으로 펴는 것. 이중 for문의 안쪽 for와 같다.
```
map     -> [Java, Spring], [Java, React]   (상자 2개 그대로)
flatMap -> Java, Spring, Java, React       (상자 뜯어서 내용물만)
```

## 5. 도구

### jshell
> 프로그램 전체를 띄우지 않고, 코드 한 줄만 바로 실행해서 결과를 보는 연습장.

- IntelliJ Terminal에서 `jshell`. 안 되면 Tools > JShell Console. 종료는 `/exit`.
- 기본기 공부할 때 짐작 대신 확인하는 용도. 실무에서 직접 켜는 일은 드물다.

| 같은 목적, 실무에서 쓰는 것 | 언제 |
|---|---|
| 디버거 Evaluate (Alt+F8) | 멈춘 시점의 실제 값으로 식 실행. 제일 많이 씀 |
| JUnit 테스트 | 확인한 걸 코드로 남겨 두기 (`SkillParserTest`) |
| IntelliJ Scratch 파일 | 프로젝트 밖에서 잠깐 짜서 돌려 보기 |

### git am이 멈췄을 때
- IntelliJ 위에 **Rebasing main**이 뜨면 `git status` 첫 줄 확인. `You are in the middle of an am session`이면 git am이 멈춘 상태.
- git am도 내부적으로 `.git/rebase-apply` 폴더를 써서 IntelliJ가 rebase로 표시한다.
- `--abort`(취소), `--skip`(이 패치 건너뜀), `--continue`(고친 뒤 계속) 중 하나로 끝내야 다음 작업이 된다.
- `does not match index` = 그 파일을 로컬에서 수정 중이라 덮어쓸 수 없음. 패치 받기 전에 `git status`로 수정 중인 파일이 없는지 확인한다.

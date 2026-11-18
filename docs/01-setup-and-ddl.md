# 01. 프로젝트 뼈대 + DDL

## ERD
```
TB_PROJECT (공고)  1 ──< N  TB_PROJECT_SKILL  N >── 1  TB_SKILL (기술스택)
     │
     1
     │
     N
TB_PROJECT_SNAPSHOT (시점별 상태)  N >── 1  TB_CRAWL_RUN (수집 이력)
```

## 설계하면서 내린 결정과 이유

### 1. TB_PROJECT의 PK는 시퀀스가 아니라 프리모아 proj_idx (자연키)
- 같은 공고가 매번 수집되므로 "이미 있으면 UPDATE, 없으면 INSERT"가 필요하다.
- 그 판단 기준이 proj_idx라서 PK로 쓰면 MERGE INTO 기준키가 바로 생긴다.
- 반대로 TB_SKILL은 외부 ID가 없으니 시퀀스(대리키)를 쓰고, 중복은 UNIQUE(SKILL_KEY)로 막는다.

### 2. 기술스택은 문자열 그대로 두지 않고 N:M으로 정규화
- 원본: `"PHP, php 8.5, Linux 8.1, MySQL, Apache"` 같은 콤마 문자열.
- 문자열 그대로면 "Java 공고 몇 건?"을 `LIKE '%Java%'`로 찾아야 하는데,
  `JavaScript`까지 걸리고 인덱스도 못 탄다.
- 원문은 `SKILL_RAW`에 남겨둔다. 정규화 로직이 틀렸을 때 다시 계산할 수 있게.

### 3. SUMMARY는 VARCHAR2가 아니라 CLOB
- VARCHAR2 최대 길이는 기본 **4000 바이트**다 (CHAR로 선언해도 바이트 한도는 그대로).
- 한글은 UTF-8에서 글자당 3바이트라 약 1,333자를 넘으면 `ORA-12899` 에러가 난다.

### 4. "현재 상태"와 "이력"을 분리
- TB_PROJECT: 공고당 1행, 항상 최신 값.
- TB_PROJECT_SNAPSHOT: 수집할 때마다 1행씩 쌓임 → 지원자 수 추이 분석(LAG)용.
- 한 테이블에 다 넣으면 "최신 값 조회"가 매번 서브쿼리가 되어 복잡하고 느려진다.

### 5. 의뢰인 이메일(client_id)은 저장하지 않음
- 프리모아 API 응답에는 있지만 이 프로젝트 목적에 필요 없는 개인정보다.
- 필요 없는 개인정보는 애초에 수집하지 않는 게 가장 확실한 보호 방법이다.

### 6. 비밀번호는 환경변수로
- `application.yml`에는 `${DB_PASSWORD}`만 두고 실제 값은 실행 환경에서 주입한다.

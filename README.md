# freemoa-crawler

프리모아 프로젝트 공고를 수집해서 Oracle에 저장하고, 엑셀로 내려받는 학습용 프로젝트.
목표는 기능 개수가 아니라 **Java 기본기 / SQL / Git** 보강.

## 기술 스택
- Java 17, Spring Boot 3.5, Maven
- MyBatis, Oracle AI Database 26ai Free (FREEPDB1)

## 실행 방법

### 1. 테이블 생성
DBeaver에서 `freemoa` 계정으로 접속 → `db/01_create_tables.sql` 열기 → `Alt + X`

### 2. DB 비밀번호 환경변수 설정 (IntelliJ)
`Run > Edit Configurations... > FreemoaCrawlerApplication > Environment variables`
```
DB_PASSWORD=freemoa계정비밀번호
```
> 비밀번호를 application.yml에 직접 쓰면 Git에 올라가는 순간 유출된다.

### 3. 실행 후 확인
브라우저에서 http://localhost:8080/health/db
```json
{
  "ping": "ok",
  "tables": ["TB_CRAWL_RUN", "TB_PROJECT", "TB_PROJECT_SKILL", "TB_PROJECT_SNAPSHOT", "TB_SKILL"]
}
```

## 진행 단계
| 단계 | 내용 | 브랜치 |
|---|---|---|
| 1 | 프로젝트 뼈대 + DDL | `feature/01-setup` |
| 2 | 순수 Java 수집기 (Spring 없이) | `feature/02-plain-java-crawler` |
| 3 | Spring으로 이전 (DI 체감) + MERGE 저장 | `feature/03-spring-merge` |
| 4 | 트랜잭션 (수집 이력, 부분 실패) | `feature/04-transaction` |
| 5 | 엑셀 다운로드 | `feature/05-excel` |
| 6 | SQL 연습 (서브쿼리, 윈도우 함수, 프로시저) | `feature/06-sql` |

단계별 설계 설명은 `docs/` 폴더 참고.

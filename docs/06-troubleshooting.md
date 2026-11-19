# 06. 트러블슈팅 기록

실제로 막혔던 순서대로 정리했다. 각 항목은 증상 → 원인 → 해결 → 배운 점 순서다.

## 요약

| # | 증상 | 원인 한 줄 | 분류 |
|---|---|---|---|
| 1 | `ORA-01017` | `DB_PASSWORD` 환경변수 없음 | 설정 |
| 2 | `ORA-02289` 시퀀스 없음 | 테이블을 SYSTEM 스키마에 만듦 | DB |
| 3 | `ORA-01031` 권한 없음 | GRANT 미실행 + DBeaver 현재 스키마가 SYSTEM | DB |
| 4 | `ORA-12514` | 접속 정보의 서비스 이름이 틀림 | DB |
| 5 | `PKIX path building failed` | JDK가 프리모아 인증서 체인을 신뢰 못 함 | 네트워크 |
| 6 | 프리모아 API `404` | 브라우저가 보내는 헤더 2개 누락 | HTTP |
| 7 | 고친 코드가 반영 안 됨 | IntelliJ가 열린 탭의 옛 내용으로 덮어씀 | 도구 |
| 8 | `ORA-17004` / 부적합한 열 유형 `1111` | YAML에서 따옴표 없는 `NULL`이 null로 읽힘 | 설정 |
| 9 | `git push` rejected (non-fast-forward) | 로컬 main이 원격 main보다 뒤처짐 | Git |
| 10 | 넥사크로 실행 시 빈 화면 | WorkFrame `formurl`이 빈 폼을 가리킴 | 화면 |
| 11 | 브라우저 실행 시 `[skills] FAILED` (CORS error) | 넥사크로가 인자 없는 요청을 GET으로 보냄, 서버는 POST만 허용 | CORS |
| 12 | 전체 수집 클릭 시 "처리 중 오류"인데 수집은 계속 돎 | MyBatis 별칭 `long`/`int`가 래퍼 타입이라 record 생성자를 못 찾음 | MyBatis |

---

## 1. ORA-01017: invalid username/password

- **원인**: `application.yml`은 `password: ${DB_PASSWORD}`인데, 환경변수를 넣지 않고 실행했다.
- **해결**: IntelliJ Run Configuration > Environment variables에 `DB_PASSWORD=...` 추가.
- **주의**: "Store as project file"을 체크하면 `.run/` 아래 파일로 저장돼 git에 올라갈 수 있다. 체크하지 않는다.
- **배운 점**: 비밀번호는 코드나 yml에 직접 쓰지 않고 환경변수로 주입한다.

## 2. ORA-02289: sequence does not exist

- **원인**: DDL을 `system` 계정 편집기에서 실행해서 객체가 SYSTEM 스키마에 생겼다. 앱은 `freemoa`로 접속하니 안 보인다.
- **해결**: SYSTEM 쪽 객체를 드롭하고 `freemoa` 접속에서 DDL을 다시 실행했다.
- **배운 점**: Oracle에서는 사용자 = 스키마다. 어느 계정으로 실행했는지가 곧 어디에 생기는지다.

## 3. ORA-01031: insufficient privileges

- **원인 1**: `GRANT CREATE TABLE, CREATE SEQUENCE ... TO freemoa`가 실제로 실행되지 않았다.
- **원인 2**: DBeaver 툴바의 현재 스키마가 여전히 SYSTEM이었다.
- **해결**: system 편집기에서 GRANT를 실행하고, 툴바 스키마를 FREEMOA로 바꿨다. `ALTER SESSION SET CURRENT_SCHEMA = FREEMOA`도 같은 효과다.

## 4. ORA-12514: listener does not currently know of service

- **원인**: DBeaver 접속의 Database 칸에 잘못된 이름을 넣었다.
- **해결**: 접속 방식을 Service Name으로 하고 `FREEPDB1`을 입력했다.
- **배운 점**: 26ai Free는 CDB(`FREE`) 안에 PDB(`FREEPDB1`)가 있고, 사용자 테이블은 PDB에 만든다.

## 5. SSL: PKIX path building failed

- **증상**: Java `HttpClient`로 `https://www.freemoa.net` 호출 시 인증서 검증 실패. 브라우저에서는 잘 열린다.
- **시도했다가 안 된 것**: `-Djavax.net.ssl.trustStoreType=Windows-ROOT`, AIA 자동 다운로드 옵션.
- **해결**: JDK 기본 `cacerts`를 복사해서 별도 truststore를 만들고, 프리모아 인증서를 추가했다.

```bat
copy "%JAVA_HOME%\lib\security\cacerts" C:\Users\lhk93\freemoa-truststore.p12
keytool -importcert -alias freemoa -file freemoa.cer ^
  -keystore C:\Users\lhk93\freemoa-truststore.p12 -storepass changeit
```

Run Configuration > VM options:
```
-Djavax.net.ssl.trustStore=C:\Users\lhk93\freemoa-truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit
```

- **VM options로 넣는 이유**: truststore 경로는 JVM이 시작할 때 읽는 시스템 속성이라, 프로그램 인자나 환경변수가 아니라 `-D` 옵션으로 준다.
- **하지 말 것**: 인증서 검증을 끄는 코드(모든 인증서 신뢰 TrustManager). 동작은 하지만 보안 구멍이다.

## 6. 프리모아 API 404

- **증상**: 브라우저 F12에서 본 URL과 파라미터 그대로 Java로 POST했는데 404.
- **진단**: curl로 헤더 유무만 바꿔서 비교했다. 코드 문제인지, 서버가 막는 건지를 코드 밖에서 분리하는 방법이다.

```bat
:: 1) 헤더 없음 → 404
curl -s -o nul -w "%{http_code}" -X POST https://www.freemoa.net/m4a/s41a ^
  -d "sS=&page=1&mp=0&lp=0&st=stayAll&st2=&st3="

:: 2) 헤더 추가 → 200
curl -s -o nul -w "%{http_code}" -X POST https://www.freemoa.net/m4a/s41a ^
  -H "X-Requested-With: XMLHttpRequest" ^
  -H "Referer: https://www.freemoa.net/m4/s41" ^
  -d "sS=&page=1&mp=0&lp=0&st=stayAll&st2=&st3="
```

- **원인**: 서버가 AJAX 요청(`X-Requested-With`)이고 목록 페이지에서 온 요청(`Referer`)일 때만 응답한다. 크롤링 차단이 아니라 요청 형태 검사다.
- **해결**: `FreemoaApiClient`에 두 헤더를 추가했다.
- **배운 점**: 브라우저에서 되는데 코드에서 안 되면, F12 Network 탭의 Request Headers를 그대로 비교한다.

## 7. 고친 코드가 반영 안 됨 (IntelliJ 덮어쓰기)

- **증상**: 헤더를 추가했는데 에러 스택의 줄 번호가 예전 그대로(69번 줄)였다.
- **원인**: 파일이 IntelliJ 탭에 열린 상태에서 바깥에서 수정하면, IntelliJ가 메모리의 옛 내용으로 다시 저장할 수 있다. 실제 파일 크기를 비교해서 확인했다 (4684 vs 5226 bytes).
- **해결**: 수정본을 다시 반영하고 IntelliJ에서 **Ctrl+Alt+Y** (Reload from Disk) 후 다시 빌드했다.
- **배운 점**: 줄 번호가 안 맞으면 "옛 코드가 돌고 있다"부터 의심한다.

## 8. ORA-17004 / 부적합한 열 유형: 1111

- **증상**: 기술스택이 없는 공고(`skillRaw = null`)를 저장할 때 실패. 7건 실패로 PARTIAL 종료.
- **원인**: MyBatis는 null을 바인딩할 때 `jdbc-type-for-null` 설정 타입을 쓴다. yml에 따옴표 없이 `NULL`이라고 쓰면 YAML이 이걸 문자열이 아니라 null 값으로 읽는다. 설정이 없는 것처럼 되어 기본값 `OTHER`(타입 코드 1111)로 바인딩했고, Oracle 드라이버가 거부했다.

```yaml
mybatis:
  configuration:
    jdbc-type-for-null: NULL     # X  YAML의 null로 읽힘
    jdbc-type-for-null: 'NULL'   # O  문자열 "NULL" → JdbcType.NULL
```

- **해결**: 작은따옴표로 감쌌다. 그 뒤 run 10이 SUCCESS (fetched 20, inserted 7, updated 13, failed 0).
- **배운 점**: YAML에서 `null`, `NULL`, `~`, `true`, `yes`, `on` 같은 값은 따옴표가 없으면 문자열이 아니다. 에러 메시지의 `1111`은 `java.sql.Types.OTHER`다.

## 9. git push rejected (non-fast-forward)

- **원인**: GitHub에서 PR을 머지했거나 웹에서 README를 고쳐서, 원격 main에 로컬에 없는 커밋이 있었다.
- **해결**:
```bash
git switch main
git pull origin main
git merge feature/xxx
git push origin main
```
- **하지 말 것**: `git push --force`. 원격의 커밋을 지운다.
- **배운 점**: push가 거절되면 먼저 pull로 원격 변경을 받아온다.

## 10. 넥사크로 실행 시 빈 화면

- **증상**: 폼 Design/Source는 정상인데 실행하면 빈 창만 뜬다.
- **원인**: 실행(Launch)은 앱 전체를 띄우고, 앱은 mainframe > WorkFrame의 `formurl` 폼을 보여준다. 프로젝트 마법사의 기본값이 비어 있는 `FrameBase::Form_Work.xfdl`이다.
- **해결**: WorkFrame의 `formurl`을 `Base::frmProjectList.xfdl`로 바꾸고 저장했다. 자세한 위치는 [05-nexacro.md](05-nexacro.md)의 4번에 있다.
- **배운 점**: Launch(Ctrl+F5)는 앱 전체, QuickView(Ctrl+F6)는 지금 연 폼 하나다.

## 11. 브라우저 실행 시 [skills] FAILED (CORS error)

- **증상**: Chrome(`http://127.0.0.1:4098`)으로 띄우면 기술 콤보 로딩만 실패. 같은 화면의 조회(`/nx/projects/search`)는 정상. QuickView(NRE)에서는 둘 다 정상.
- **Console 메시지**
  ```
  Access to fetch at 'http://localhost:8080/nx/skills' from origin 'http://127.0.0.1:4098'
  has been blocked by CORS policy: Response to preflight request doesn't pass access control check:
  No 'Access-Control-Allow-Origin' header is present on the requested resource.
  GET http://localhost:8080/nx/skills net::ERR_FAILED
  ```
- **원인**: 마지막 줄의 `GET`. 넥사크로는 보낼 Dataset도 인자도 없는 transaction(`skills`)을 GET으로 보낸다. 인자가 있는 조회는 POST다.
  - 서버는 `@PostMapping("/skills")`, CORS는 `allowedMethods("POST", "OPTIONS")`라서, 사전 요청(OPTIONS)의 `Access-Control-Request-Method: GET`을 거절했다.
  - 거절된 사전 요청에는 `Access-Control-Allow-Origin`이 없으니 브라우저는 "CORS 에러"로만 보여준다.
  - NRE는 브라우저가 아니라 CORS 검사를 안 하고, 서버에는 405가 났겠지만 콘솔에 묻혔다.
- **해결**
  - `ProjectScreenController`: `@RequestMapping(value = "/skills", method = {RequestMethod.GET, RequestMethod.POST})`
  - `WebConfig`: `.allowedMethods("GET", "POST", "OPTIONS")`
- **배운 점**: CORS 에러는 다른 원인(메서드 불일치, 404, 서버 에러)을 가리는 껍데기인 경우가 많다. Console 문구 전체와 실제 메서드를 먼저 본다. 같은 설정인데 한 요청만 실패하면 두 요청의 차이(메서드, 헤더, 경로)를 비교한다.

## 12. 전체 수집 클릭 시 "처리 중 오류가 발생했습니다"인데 수집은 계속 돎

- **증상**: 화면에는 `[crawlFull] 처리 중 오류가 발생했습니다`. 그런데 IntelliJ 로그에는 페이지별 저장이 계속 찍힌다.
- **원인**: 접수(RUNNING 기록, 백그라운드 시작)까지는 성공했고, 그 다음 화면에 돌려줄 이력 1행을 조회하는 `selectRun`이 실패했다.
  `CrawlRunMapper.xml`의 `<constructor>`에 `javaType="long"`, `javaType="int"`로 적었는데, MyBatis 별칭에서 이 둘은 래퍼 타입이다.

  | 별칭 | 실제 타입 |
  |---|---|
  | `long` | `java.lang.Long` |
  | `_long` | `long` |
  | `int` | `java.lang.Integer` |
  | `_int` | `int` |

  `CrawlRunView` record는 기본형 `long`, `int`를 받으므로 `(Long, String, ..., Integer)` 생성자를 찾다가 실패한다.
- **해결**: `_long`, `_int`로 수정.
- **배운 점**
  - 일반 메시지("처리 중 오류")는 의도된 것이다. 상세 내용은 서버 로그에만 남긴다 (내부 정보 노출 방지). 그래서 원인은 항상 서버 로그에서 찾는다.
  - 백그라운드 작업은 화면 요청이 실패해도 계속 돈다. "실패"라고 떴다고 작업 자체가 실패한 건 아닐 수 있다. 이력 테이블로 실제 상태를 확인한다.
  - MyBatis 로그의 `<== Updates: 1`은 영향받은 행 수다. MERGE는 INSERT든 UPDATE든 1이다. 신규/갱신 구분은 우리 로그(`공고 저장 완료: 신규 N건, 갱신 N건`)를 본다.

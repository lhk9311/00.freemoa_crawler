package com.lhk.freemoa.crawler.run;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집 이력(TB_CRAWL_RUN) 기록 전담.
 *
 * 왜 REQUIRES_NEW인가
 * - 이력은 "수집이 실패했다"는 사실까지 반드시 남아야 한다.
 * - 만약 누군가 호출하는 쪽에 @Transactional을 붙이고, 그 트랜잭션이 롤백되면
 *   REQUIRED(기본값)로는 이력 INSERT/UPDATE까지 같이 롤백돼서 "실패 기록"이 사라진다.
 * - REQUIRES_NEW는 바깥 트랜잭션을 잠시 멈추고, 새 트랜잭션을 열어 즉시 커밋한다.
 *   -> 바깥이 롤백되든 말든 이력은 살아남는다.
 * - 지금 호출부(CrawlService)에는 트랜잭션이 없어서 REQUIRED여도 결과가 같다.
 *   그래도 "이력은 독립적으로 커밋되어야 한다"는 의도를 코드에 박아두는 방어적 설계다.
 *   실제 차이는 src/test의 TransactionLabTest에서 직접 확인할 수 있다.
 *
 * 왜 별도 클래스인가 (CrawlService 안의 private 메서드로 두면 안 되는 이유)
 * - @Transactional은 스프링이 만든 "프록시"가 메서드 호출을 가로채서 트랜잭션을 연다.
 * - 같은 클래스 안에서 this.method()로 부르면 프록시를 거치지 않아서 @Transactional이 무시된다.
 *   (self-invocation 함정. 이것도 TransactionLabTest에 있다)
 */
@Service
public class CrawlRunService {

    /** ERROR_MSG는 VARCHAR2(4000 BYTE). 한글 3바이트 기준 1000자면 3000바이트로 안전 */
    private static final int MAX_ERROR_LENGTH = 1000;

    private final CrawlRunMapper crawlRunMapper;

    public CrawlRunService(CrawlRunMapper crawlRunMapper) {
        this.crawlRunMapper = crawlRunMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long start(String keyword) {
        long runId = crawlRunMapper.selectNextRunId();
        try {
            crawlRunMapper.insertRunning(runId, keyword);
        } catch (DuplicateKeyException e) {
            throw new CrawlAlreadyRunningException("이미 진행 중인 수집이 있습니다. 끝난 뒤 다시 시도하세요.", e);
        }
        return runId;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(long runId, CrawlStatus status, int pageCount, int fetched,
                       int inserted, int updated, int failed, String errorMsg) {
        crawlRunMapper.updateFinish(runId, status, pageCount, fetched, inserted, updated, failed, truncate(errorMsg));
    }

    private static String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH);
    }
}

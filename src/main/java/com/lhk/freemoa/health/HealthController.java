package com.lhk.freemoa.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 브라우저에서 http://localhost:8080/health/db 로 DB 연결과 테이블 생성 여부를 확인한다.
 */
@RestController
public class HealthController {

    private final HealthMapper healthMapper;

    // 생성자 주입: 스프링이 HealthMapper 빈을 찾아서 넣어준다 (생성자가 하나면 @Autowired 생략 가능)
    public HealthController(HealthMapper healthMapper) {
        this.healthMapper = healthMapper;
    }

    @GetMapping("/health/db")
    public Map<String, Object> db() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ping", healthMapper.selectPing());
        result.put("tables", healthMapper.selectMyTables());
        return result;
    }
}

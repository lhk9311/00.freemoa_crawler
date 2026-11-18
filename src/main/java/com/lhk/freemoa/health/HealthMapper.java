package com.lhk.freemoa.health;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * DB 연결 확인용 매퍼.
 * 인터페이스만 선언하면 MyBatis가 구현체(프록시)를 만들어 스프링 빈으로 등록한다.
 * 실제 SQL은 resources/mapper/HealthMapper.xml 에 있다 (namespace = 이 인터페이스의 풀네임).
 */
@Mapper
public interface HealthMapper {

    String selectPing();

    List<String> selectMyTables();
}

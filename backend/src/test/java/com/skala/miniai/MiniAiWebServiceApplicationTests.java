package com.skala.miniai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 스프링 컨텍스트가 뜨는지만 확인한다.
 *
 * <p>{@code @ActiveProfiles("test")} 가 {@code application-test.properties} 를 얹어
 * 인메모리 H2 로 돌린다. 덕분에 <b>DB가 없어도 {@code ./gradlew build} 가 통과한다</b> —
 * clone 직후 팀원이 막히지 않게 하려는 것이다.
 */
@SpringBootTest
@ActiveProfiles("test")
class MiniAiWebServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}

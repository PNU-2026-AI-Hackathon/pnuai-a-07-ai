package com.safework;

import com.safework.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 스프링 컨텍스트가 뜨는지 확인한다.
 *
 * IntegrationTest 를 상속해 테스트 컨테이너 DB 를 쓴다. 상속하지 않으면
 * application.yaml 기본값(localhost:5432)을 따라가서, 개발자 로컬에 DB 가
 * 떠 있는지에 따라 결과가 달라진다.
 */
@DisplayName("애플리케이션 기동")
class SafeworkBackendApplicationTests extends IntegrationTest {

	@Test
	@DisplayName("컨텍스트가 정상적으로 로드된다")
	void contextLoads() {
	}
}

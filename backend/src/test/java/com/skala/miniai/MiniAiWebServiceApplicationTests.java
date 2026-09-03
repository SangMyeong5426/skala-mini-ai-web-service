package com.skala.miniai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * DB 없이 컨텍스트·Swagger·프런트엔드 CORS 설정을 확인한다.
 *
 * <p>{@code @ActiveProfiles("test")} 가 {@code application-test.properties} 를 얹어
 * 인메모리 H2 로 돌린다. 덕분에 <b>DB가 없어도 {@code ./gradlew build} 가 통과한다</b> —
 * clone 직후 팀원이 막히지 않게 하려는 것이다.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MiniAiWebServiceApplicationTests {

	@Autowired
	MockMvc mvc;

	@Test
	void openApiAndSwaggerAreAvailable() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.openapi").isNotEmpty());
		mvc.perform(get("/v3/api-docs/swagger-config"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value("/v3/api-docs"));
		mvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", "/swagger-ui/index.html"));
		mvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk());
	}

	@Test
	void frontendCanSendJsonAndReadLocation() throws Exception {
		mvc.perform(options("/api/trips")
				.header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
				.andExpect(header().string("Access-Control-Allow-Headers", "content-type"))
				.andExpect(header().string("Access-Control-Expose-Headers", "Location"));
	}

	@Test
	void otherOriginsAreRejected() throws Exception {
		mvc.perform(options("/api/trips")
				.header("Origin", "https://untrusted.example")
				.header("Access-Control-Request-Method", "POST"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

}

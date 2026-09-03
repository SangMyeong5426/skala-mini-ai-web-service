package com.skala.miniai.domain.ai;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.skala.miniai.common.Json;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;

/** 로그인부터 202 접수·폴링·답변까지 챗봇의 실제 HTTP 계약을 한 번에 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ChatbotApiTest {

    @Autowired MockMvc mvc;
    @Autowired Json json;

    @Test
    void chatbotHttpFlowSupportsRepresentativeQuestionsAndFollowUp() throws Exception {
        mvc.perform(get("/api/ai-jobs/999")).andExpect(status().isUnauthorized());
        SessionAuth auth = signUpAndLogin();

        assertAnswer(auth, question("20000mAh 보조배터리 기내 되나요?", "[]"),
                "NEED_MORE_INFO", "배터리 라벨에 표시된 정격 Wh는 얼마인가요?");
        assertAnswer(auth, question("120ml 화장품 기내 반입되나요?", "[]"), "CHECKED_OK", null);
        assertAnswer(auth, question("날 길이 7cm 가위 기내 반입되나요?", "[]"), "CHECKED_OK", null);
        assertAnswer(auth, question("삼각대 가져가도 되나요?", "[]"), "ASK_AIRLINE", null);

        String previousBattery = """
                [{"itemId":null,"detectionId":null,"name":"보조배터리","qty":1,
                  "attributes":{"capacityMl":null,"batteryWh":null,"batteryMah":20000,"bladeCm":null}}]
                """;
        JsonNode followUp = assertAnswer(auth, question("100Wh예요", previousBattery), "CABIN_OK", null);
        assertThat(followUp.path("output").path("results").get(0)
                .path("attributes").path("batteryWh").asInt()).isEqualTo(100);

        mvc.perform(post("/api/ai-jobs")
                        .session(auth.session()).cookie(auth.cookies())
                        .header("X-CSRF-TOKEN", auth.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobType\":\"RULE_CHECK\",\"tripId\":null,\"input\":{\"foo\":\"bar\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    private SessionAuth signUpAndLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String loginId = "chatbot_" + suffix;
        MvcResult bootstrap = mvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk()).andReturn();
        String csrf = body(bootstrap).path("csrfToken").asText();
        Cookie[] cookies = bootstrap.getResponse().getCookies();

        mvc.perform(post("/api/auth/signup")
                        .cookie(cookies).header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"챗봇점검","loginId":"%s","password":"testpass123",
                                 "email":"chatbot-%s@test.local"}
                                """.formatted(loginId, suffix)))
                .andExpect(status().isCreated());

        MvcResult login = mvc.perform(post("/api/auth/login")
                        .cookie(cookies).header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"%s\",\"password\":\"testpass123\"}".formatted(loginId)))
                .andExpect(status().isOk()).andReturn();

        return new SessionAuth((MockHttpSession) login.getRequest().getSession(false), cookies, csrf);
    }

    private JsonNode assertAnswer(SessionAuth auth, String request, String verdict, String followUp) throws Exception {
        MvcResult accepted = mvc.perform(post("/api/ai-jobs")
                        .session(auth.session()).cookie(auth.cookies())
                        .header("X-CSRF-TOKEN", auth.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        long jobId = body(accepted).path("jobId").asLong();
        JsonNode completed = await(auth, jobId);
        assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.path("output").path("results").get(0).path("verdict").asText())
                .isEqualTo(verdict);
        JsonNode actualFollowUp = completed.path("output").path("followUpQuestion");
        if (followUp == null) assertThat(actualFollowUp.isNull()).isTrue();
        else assertThat(actualFollowUp.asText()).isEqualTo(followUp);
        return completed;
    }

    private JsonNode await(SessionAuth auth, long jobId) throws Exception {
        for (int i = 0; i < 100; i++) {
            MvcResult result = mvc.perform(get("/api/ai-jobs/{jobId}", jobId)
                            .session(auth.session()).cookie(auth.cookies()))
                    .andExpect(status().isOk()).andReturn();
            JsonNode body = body(result);
            if (!"PENDING".equals(body.path("status").asText())) return body;
            Thread.sleep(20);
        }
        throw new AssertionError("챗봇 작업이 2초 안에 끝나지 않았습니다: " + jobId);
    }

    private String question(String question, String items) {
        return """
                {"jobType":"RULE_CHECK","tripId":null,"input":{
                  "transport":"FLIGHT","airline":null,"question":%s,"items":%s}}
                """.formatted(json.write(question), items);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private record SessionAuth(MockHttpSession session, Cookie[] cookies, String csrf) { }
}

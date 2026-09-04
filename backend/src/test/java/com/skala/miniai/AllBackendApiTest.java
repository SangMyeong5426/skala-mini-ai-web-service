package com.skala.miniai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.skala.miniai.common.Json;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;

/** 공개된 백엔드 API를 실제 인증·DB·비동기 경계로 한 번씩 통과시키는 회귀 테스트. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AllBackendApiTest {

    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);

    @DynamicPropertySource
    static void isolate(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url",
                () -> "jdbc:h2:mem:all-api-" + RUN_ID + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        properties.add("app.upload.dir", () -> "build/test-uploads/" + RUN_ID);
    }

    @Autowired MockMvc mvc;
    @Autowired Json json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void everyBackendApiWorksThroughOneUserLifecycle() throws Exception {
        mvc.perform(get("/api/trips")).andExpect(status().isUnauthorized());
        SessionAuth auth = signUpAndLogin();

        read(auth, get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));
        read(auth, get("/api/calendar").queryParam("from", "not-a-date").queryParam("to", "2026-10-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.field").value("from"));
        read(auth, get("/api/rules").queryParam("transport", "PLANE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("transport"));
        read(auth, get("/api/trips/1/placements"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        MvcResult createdTrip = write(auth, post("/api/trips"), """
                {"origin":"서울","destination":"도쿄","countryCode":"JP",
                 "startDate":"2026-10-01","endDate":"2026-10-04","purpose":"TOUR",
                 "transport":"FLIGHT","airline":"대한항공","departureAirport":"ICN",
                 "arrivalAirport":"NRT","bagType":"CARRY_ON","bagEmptyG":3200,
                 "weightLimitG":10000,"note":"통합 API 검증"}
                """).andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();
        long tripId = body(createdTrip).path("tripId").asLong();

        read(auth, get("/api/trips"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trips[0].tripId").value(tripId));
        read(auth, get("/api/trips/{tripId}", tripId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.destination").value("도쿄"));
        write(auth, patch("/api/trips/{tripId}", tripId), "{\"origin\":\"   \"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.field").value("origin"));
        write(auth, patch("/api/trips/{tripId}", tripId), "{\"origin\":\"서울\\n\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.origin").value("서울"));
        write(auth, patch("/api/trips/{tripId}", tripId), "{\"status\":\"CONFIRMED\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));

        MvcResult createdItinerary = write(auth, post("/api/trips/{tripId}/itineraries", tripId), """
                {"kind":"FLIGHT","title":"도쿄행","place":"인천공항","code":"KE001",
                 "startAt":"2026-10-01T01:00:00Z","endAt":"2026-10-01T03:00:00Z","note":null}
                """).andExpect(status().isCreated()).andReturn();
        long itineraryId = body(createdItinerary).path("itineraryId").asLong();
        read(auth, get("/api/trips/{tripId}/itineraries", tripId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.itineraries[0].code").value("KE001"));
        write(auth, patch("/api/trips/{tripId}/itineraries/{id}", tripId, itineraryId),
                "{\"title\":\"   \"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.field").value("title"));
        write(auth, patch("/api/trips/{tripId}/itineraries/{id}", tripId, itineraryId),
                "{\"title\":\"도쿄 출국\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("도쿄 출국"));
        read(auth, get("/api/calendar").queryParam("from", "2026-10-01").queryParam("to", "2026-10-31"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trips[0].tripId").value(tripId));

        MvcResult createdItem = write(auth, post("/api/trips/{tripId}/items", tripId), """
                {"name":"여권","category":"DOCUMENT","qty":1,"priority":"REQUIRED","recommendation":null}
                """).andExpect(status().isCreated()).andReturn();
        long itemId = body(createdItem).path("itemId").asLong();
        read(auth, get("/api/trips/{tripId}/items", tripId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].name").value("여권"));
        write(auth, patch("/api/trips/{tripId}/items/{itemId}", tripId, itemId),
                "{\"checkStatus\":\"PREPARED\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.checkStatus").value("PREPARED"));

        read(auth, get("/api/trips/{tripId}/packing-layout", tripId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unplaced[0].itemId").value(itemId));
        write(auth, put("/api/trips/{tripId}/packing-layout", tripId), "{\"placements\":[null]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        write(auth, put("/api/trips/{tripId}/packing-layout", tripId), """
                {"placements":[{"itemId":%d,"compartment":"MAIN_LEFT",
                  "posX":0.1,"posY":0.2,"posZ":0.3,"rotated":false}]}
                """.formatted(itemId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.placements[0].itemId").value(itemId));

        byte[] image = new byte[] { 1, 2, 3, 4 };
        MockMultipartFile file = new MockMultipartFile("files", "bag.jpg", "image/jpeg", image);
        MvcResult uploaded = mvc.perform(multipart("/api/trips/{tripId}/photos", tripId)
                        .file(file).param("bagKind", "CABIN")
                        .session(auth.session()).cookie(auth.cookies()).header("X-CSRF-TOKEN", auth.csrf()))
                .andExpect(status().isCreated()).andReturn();
        JsonNode uploadedBody = body(uploaded);
        long photoId = uploadedBody.path("photos").get(0).path("photoId").asLong();
        String fileUrl = uploadedBody.path("photos").get(0).path("fileUrl").asText();
        read(auth, get("/api/trips/{tripId}/photos", tripId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photos[0].photoId").value(photoId));
        read(auth, get(fileUrl)).andExpect(status().isOk()).andExpect(content().bytes(image));

        JsonNode bagJob = createAndAwait(auth, "BAG_CHECK", tripId, "null");
        assertThat(bagJob.path("output").path("detections").size()).isEqualTo(8);
        MvcResult detections = read(auth, get("/api/trips/{tripId}/detections", tripId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.detections.length()").value(8)).andReturn();
        long detectionId = body(detections).path("detections").get(0).path("detectionId").asLong();
        write(auth, patch("/api/trips/{tripId}/detections/{id}", tripId, detectionId),
                "{\"name\":\"USB 충전기\",\"qty\":2,\"category\":\"ELECTRONIC\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("USB 충전기"));

        JsonNode recommendation = createAndAwait(auth, "PACKING_LIST", tripId, "null");
        assertThat(recommendation.path("output").path("items")).isNotEmpty();
        JsonNode weight = createAndAwait(auth, "WEIGHT_ESTIMATE", tripId, "null");
        assertThat(weight.path("output").path("limitG").asInt()).isEqualTo(10000);

        JsonNode chatbot = createAndAwait(auth, "RULE_CHECK", null, """
                {"transport":"FLIGHT","airline":null,
                 "question":"120ml 화장품 기내 반입되나요?","items":[]}
                """);
        assertThat(chatbot.path("output").path("results").get(0).path("verdict").asText())
                .isEqualTo("CHECKED_OK");
        assertThat(chatbot.path("output").path("answer").asText()).contains("위탁수하물");

        jdbc.update("insert into transport_rules "
                + "(transport, keyword, verdict, condition_note, description, source_url, checked_at) "
                + "values ('FLIGHT', '보조배터리', 'CABIN_OK', '100Wh 이하', "
                + "'기내 반입 기준', 'https://example.test/rule', DATE '2026-09-03')");
        // 순서로 집지 않는다. 규정 마스터에는 data.sql 이 넣은 공식 규정도 함께 있고,
        // RuleEngine 이 그 표로 판정하므로 테스트가 규정표를 독차지할 수 없다.
        read(auth, get("/api/rules").queryParam("transport", "FLIGHT").queryParam("keyword", "보조배터리"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rules[*].sourceUrl")
                        .value(hasItem("https://example.test/rule")));
        read(auth, get("/api/trips/{tripId}/inspection", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readiness.prepared").isArray())
                .andExpect(jsonPath("$.weight.limitG").value(10000));

        mvc.perform(withAuth(auth, delete("/api/trips/{tripId}/packing-layout", tripId), true))
                .andExpect(status().isNoContent());
        mvc.perform(withAuth(auth, delete("/api/trips/{tripId}/items/{itemId}", tripId, itemId), true))
                .andExpect(status().isNoContent());
        mvc.perform(withAuth(auth, delete("/api/trips/{tripId}/photos/{photoId}", tripId, photoId), true))
                .andExpect(status().isNoContent());
        mvc.perform(withAuth(auth,
                        delete("/api/trips/{tripId}/itineraries/{id}", tripId, itineraryId), true))
                .andExpect(status().isNoContent());
        mvc.perform(withAuth(auth, delete("/api/trips/{tripId}", tripId), true))
                .andExpect(status().isNoContent());
        read(auth, get("/api/trips/{tripId}", tripId)).andExpect(status().isNotFound());

        mvc.perform(withAuth(auth, post("/api/auth/logout"), true)).andExpect(status().isNoContent());
        mvc.perform(get("/api/trips").session(auth.session()).cookie(auth.cookies()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void csrfAndUserOwnershipBoundariesAreEnforced() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        SessionAuth owner = createAccount("owner_" + suffix);
        MvcResult created = write(owner, post("/api/trips"), """
                {"origin":"서울","destination":"도쿄","countryCode":"JP",
                 "startDate":"2026-10-01","endDate":"2026-10-04","purpose":"TOUR",
                 "transport":"FLIGHT","bagType":"CARRY_ON"}
                """).andExpect(status().isCreated()).andReturn();
        long tripId = body(created).path("tripId").asLong();
        long jobId = createAndAwait(owner, "RULE_CHECK", null, """
                {"transport":"FLIGHT","airline":null,
                 "question":"노트북 기내 반입되나요?","items":[]}
                """).path("jobId").asLong();

        SessionAuth other = createAccount("other_" + suffix);
        read(other, get("/api/trips/{tripId}", tripId)).andExpect(status().isNotFound());
        write(other, patch("/api/trips/{tripId}", tripId), "{\"origin\":\"부산\"}")
                .andExpect(status().isNotFound());
        read(other, get("/api/ai-jobs/{jobId}", jobId)).andExpect(status().isNotFound());

        mvc.perform(withAuth(owner, patch("/api/trips/{tripId}", tripId), false)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"origin\":\"부산\"}"))
                .andExpect(status().isForbidden());
    }

    private SessionAuth signUpAndLogin() throws Exception {
        MvcResult bootstrap = mvc.perform(get("/api/auth/session")).andExpect(status().isOk()).andReturn();
        String csrf = body(bootstrap).path("csrfToken").asText();
        Cookie[] cookies = bootstrap.getResponse().getCookies();
        String loginId = "allapi_" + RUN_ID;

        mvc.perform(post("/api/auth/signup").cookie(cookies).header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nickname":"API검증","loginId":"%s","password":"testpass123",
                                 "email":"long-%s@test.local"}
                                """.formatted(" ".repeat(57) + "abcd", RUN_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("loginId"));

        mvc.perform(post("/api/auth/signup").cookie(cookies).header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nickname":" x ","loginId":"bad_%s","password":"testpass123",
                                 "email":"bad-%s@test.local"}
                                """.formatted(RUN_ID, RUN_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("nickname"));

        mvc.perform(post("/api/auth/signup").cookie(cookies).header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nickname":"API검증","loginId":"  %s  ","password":"testpass123",
                                 "email":"all-api-%s@test.local"}
                                """.formatted(loginId.toUpperCase(), RUN_ID)))
                .andExpect(status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.user.loginId").value(loginId));

        mvc.perform(post("/api/auth/login").cookie(cookies).header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"invalid-id!\",\"password\":\"testpass123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("loginId"));

        MvcResult login = mvc.perform(post("/api/auth/login").cookie(cookies).header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"%s\",\"password\":\"testpass123\"}".formatted(loginId)))
                .andExpect(status().isOk()).andReturn();
        return new SessionAuth((MockHttpSession) login.getRequest().getSession(false), cookies, csrf);
    }

    private SessionAuth createAccount(String suffix) throws Exception {
        MvcResult bootstrap = mvc.perform(get("/api/auth/session")).andExpect(status().isOk()).andReturn();
        String csrf = body(bootstrap).path("csrfToken").asText();
        Cookie[] cookies = bootstrap.getResponse().getCookies();
        String loginId = "e2e_" + suffix;

        mvc.perform(post("/api/auth/signup").cookie(cookies).header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nickname":"격리검증","loginId":"%s","password":"testpass123",
                                 "email":"%s@test.local"}
                                """.formatted(loginId, loginId)))
                .andExpect(status().isCreated());
        MvcResult login = mvc.perform(post("/api/auth/login").cookie(cookies).header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"%s\",\"password\":\"testpass123\"}".formatted(loginId)))
                .andExpect(status().isOk()).andReturn();
        return new SessionAuth((MockHttpSession) login.getRequest().getSession(false), cookies, csrf);
    }

    private JsonNode createAndAwait(SessionAuth auth, String jobType, Long tripId, String input) throws Exception {
        String request = "{\"jobType\":\"%s\",\"tripId\":%s,\"input\":%s}"
                .formatted(jobType, tripId == null ? "null" : tripId, input);
        MvcResult accepted = write(auth, post("/api/ai-jobs"), request)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        long jobId = body(accepted).path("jobId").asLong();

        for (int i = 0; i < 100; i++) {
            MvcResult result = read(auth, get("/api/ai-jobs/{jobId}", jobId))
                    .andExpect(status().isOk()).andReturn();
            JsonNode response = body(result);
            if (!"PENDING".equals(response.path("status").asText())) {
                assertThat(response.path("status").asText()).isEqualTo("COMPLETED");
                return response;
            }
            Thread.sleep(20);
        }
        throw new AssertionError(jobType + " 작업이 2초 안에 끝나지 않았습니다.");
    }

    private org.springframework.test.web.servlet.ResultActions write(
            SessionAuth auth, MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(withAuth(auth, request, true)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private org.springframework.test.web.servlet.ResultActions read(
            SessionAuth auth, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(withAuth(auth, request, false));
    }

    private MockHttpServletRequestBuilder withAuth(
            SessionAuth auth, MockHttpServletRequestBuilder request, boolean csrf) {
        request.session(auth.session()).cookie(auth.cookies());
        if (csrf) request.header("X-CSRF-TOKEN", auth.csrf());
        return request;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private record SessionAuth(MockHttpSession session, Cookie[] cookies, String csrf) { }
}

package com.skala.miniai.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import com.skala.miniai.common.ApiException;
import com.skala.miniai.common.Codes;
import com.skala.miniai.common.Json;
import com.skala.miniai.domain.ai.AiJob;
import com.skala.miniai.domain.ai.AiJobDtos;
import com.skala.miniai.domain.ai.AiJobRepository;
import com.skala.miniai.domain.ai.AiJobService;
import com.skala.miniai.domain.checklist.ChecklistService;
import com.skala.miniai.domain.trip.TripDtos;
import com.skala.miniai.domain.trip.TripService;

/**
 * 챗봇에 붙인 사진이 <b>여행 사진과 같은 취급</b>을 받는지 본다 (S-09).
 *
 * <p>07 이 TBD 로 남겼던 "챗봇 사진의 별도 저장·연결 흐름" 을 <b>자동 등록</b>으로 정한 결과다.
 * 별도 저장소를 두지 않고 {@code trip_photos} → {@code detected_objects} → 내 목록까지
 * {@code BAG_CHECK} 와 같은 길을 지난다.
 *
 * <p>{@code AiClient} 를 갈아 끼우지 않는다 — 실제 {@code MockAiClient} 와 규칙 엔진을 그대로
 * 태워야 "사진 → 등록 → 판정" 이 이어지는지 확인할 수 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ChatbotPhotoAttachmentTest {

    @Autowired AiJobService aiJobService;
    @Autowired AiJobRepository jobs;
    @Autowired ChecklistService checklist;
    @Autowired TripService trips;
    @Autowired JdbcTemplate jdbc;
    @Autowired Json json;

    private Long tripId;
    private Long photoId;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from item_placements");
        jdbc.update("delete from item_detections");
        jdbc.update("delete from item_rule_checks");
        jdbc.update("delete from checklist_items");
        jdbc.update("delete from ai_jobs");
        jdbc.update("delete from detected_objects");
        jdbc.update("delete from trip_photos");
        jdbc.update("delete from trip_itineraries");
        jdbc.update("delete from trips");
        jdbc.update("delete from users");
        jdbc.update("insert into users (login_id, email, password_hash, nickname, created_at) "
                + "values ('chatphoto', 'chatphoto@skala.dev', 'x', '사진첨부', current_timestamp)");
        Long userId = jdbc.queryForObject("select id from users where login_id = 'chatphoto'", Long.class);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(userId, null, List.of()));

        tripId = trips.create(new TripDtos.CreateRequest(
                "서울", "도쿄", "JP",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 4),
                Codes.Purpose.TOUR, Codes.Transport.FLIGHT,
                null, null, null, Codes.BagType.CARRY_ON, 3200, 10000, null)).tripId();

        // 파일은 필요 없다. Mock 인식은 파일을 열지 않는다 — 여기서 보려는 것은 등록·판정 경로다.
        jdbc.update("insert into trip_photos (trip_id, file_path, bag_kind, uploaded_at) "
                + "values (?, 'trips/x/chat.jpg', 'CABIN', current_timestamp)", tripId);
        photoId = jdbc.queryForObject("select id from trip_photos where trip_id = ?", Long.class, tripId);
    }

    private JsonNode ask(String question, Long... photoIds) {
        StringBuilder ids = new StringBuilder();
        for (Long id : photoIds) ids.append(ids.isEmpty() ? "" : ",").append(id);
        return json.read("""
                {"transport":"FLIGHT","airline":null,"question":"%s","items":[],"photoIds":[%s]}
                """.formatted(question, ids));
    }

    private AiJob run(Long trip, JsonNode input) {
        Long jobId = aiJobService.create(
                new AiJobDtos.CreateRequest(Codes.JobType.RULE_CHECK, trip, input)).jobId();
        for (int i = 0; i < 100; i++) {
            AiJob job = jobs.findById(jobId).orElseThrow();
            if (job.getStatus() != Codes.JobStatus.PENDING) return job;
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("작업 " + jobId + " 이 5초 안에 끝나지 않았다");
    }

    @Test
    void 붙인_사진의_물품이_내_목록에_자동_등록되고_함께_판정된다() {
        assertThat(checklist.list(tripId).items()).isEmpty();

        AiJob job = run(tripId, ask("20000mAh 보조배터리 기내 되나요?", photoId));
        assertThat(job.getStatus()).isEqualTo(Codes.JobStatus.COMPLETED);

        // ① 사진 인식 결과가 저장됐다.
        assertThat(jdbc.queryForObject(
                "select count(*) from detected_objects where photo_id = ?", Integer.class, photoId))
                .isPositive();

        // ② 승인 없이 내 목록에 PREPARED 로 들어갔다 (여행 사진과 같은 정책).
        var items = checklist.list(tripId).items();
        assertThat(items).isNotEmpty();
        assertThat(items).allSatisfy(item -> {
            assertThat(item.source()).isEqualTo(Codes.ItemSource.PHOTO);
            assertThat(item.checkStatus()).isEqualTo(Codes.CheckStatus.PREPARED);
        });

        // ③ 사진에서 나온 물품이 판정 대상에 함께 들어갔다.
        JsonNode results = json.read(job.getOutputPayload()).path("results");
        assertThat(results).isNotEmpty();
        JsonNode fromPhoto = null;
        for (JsonNode r : results) {
            if (!r.path("detectionId").isNull()) fromPhoto = r;
        }
        assertThat(fromPhoto).as("사진에서 나온 결과가 있어야 한다").isNotNull();
        // 사진 물품은 내 목록 항목과 연결돼 있어야 반입 판정이 그 항목에 남는다.
        assertThat(fromPhoto.path("itemId").isNull()).isFalse();
    }

    /**
     * 사진에는 용량도 배터리 정격도 보이지 않는다. 규칙 엔진이 그 자리에서 되물어야 한다 —
     * 챗봇이 대화형인 이유다.
     */
    @Test
    void 사진만으로는_속성을_알_수_없어_되묻는다() {
        AiJob job = run(tripId, ask("20000mAh 보조배터리 기내 되나요?", photoId));
        JsonNode output = json.read(job.getOutputPayload());

        JsonNode battery = null;
        for (JsonNode r : output.path("results")) {
            if (!r.path("detectionId").isNull() && "보조배터리".equals(r.path("ruleKeyword").asText(""))) {
                battery = r;
            }
        }
        assertThat(battery).as("사진에서 나온 보조배터리").isNotNull();
        assertThat(battery.path("verdict").asText()).isEqualTo("NEED_MORE_INFO");
        assertThat(battery.path("missingInfo").asText()).isEqualTo("배터리 정격(Wh)");
        assertThat(battery.path("attributes").path("batteryWh").isNull()).isTrue();
        assertThat(output.path("followUpQuestion").asText()).isNotBlank();
    }

    /** 같은 사진을 다시 붙여도 목록이 불어나면 안 된다 (07 「같은 BAG_CHECK 재처리」와 같은 규약). */
    @Test
    void 같은_사진을_다시_붙여도_목록이_불어나지_않는다() {
        run(tripId, ask("가위 기내 되나요?", photoId));
        int first = checklist.list(tripId).items().size();

        run(tripId, ask("가위 기내 되나요?", photoId));
        assertThat(checklist.list(tripId).items()).hasSize(first);
    }

    /**
     * {@code trip_photos.trip_id} 와 {@code checklist_items.trip_id} 가 NOT NULL 이다.
     * 여행 없이 묻는 챗봇에는 사진을 저장할 곳도 등록할 곳도 없다.
     */
    @Test
    void 여행_없이_묻는_질문에는_사진을_붙일_수_없다() {
        assertThatThrownBy(() -> aiJobService.create(new AiJobDtos.CreateRequest(
                Codes.JobType.RULE_CHECK, null, ask("이거 되나요?", photoId))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("여행을 먼저 선택");
    }

    /** 07 「로그인과 AI 작업의 경계」 — 남의 사진을 가리키면 없는 것과 똑같이 답한다. */
    @Test
    void 남의_사진은_붙일_수_없다() {
        assertThatThrownBy(() -> aiJobService.create(new AiJobDtos.CreateRequest(
                Codes.JobType.RULE_CHECK, tripId, ask("이거 되나요?", photoId + 9999))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("찾을 수 없습니다");
    }

    /** 직전 결과에서 07 이 허용한 다섯 칸만 골라 다음 턴의 {@code items} 로 만든다. */
    private String asFollowUpItems(JsonNode results) {
        StringBuilder sb = new StringBuilder("[");
        for (JsonNode r : results) {
            if (sb.length() > 1) sb.append(',');
            sb.append("{\"itemId\":").append(r.path("itemId").isNull() ? "null" : r.path("itemId").asLong())
                    .append(",\"detectionId\":")
                    .append(r.path("detectionId").isNull() ? "null" : r.path("detectionId").asLong())
                    .append(",\"name\":\"").append(r.path("name").asText()).append('"')
                    .append(",\"qty\":").append(r.path("qty").asInt(1))
                    .append(",\"attributes\":").append(json.write(r.path("attributes"))).append('}');
        }
        return sb.append(']').toString();
    }

    private AiJob runJson(String inputJson) {
        return run(tripId, json.read(inputJson));
    }

    /**
     * 되묻기 → 사용자 답변 → 판정 완료가 <b>끝나야</b> 한다. 이 PR 의 핵심 흐름이다.
     *
     * <p>같은 물품·같은 규정을 다시 판정하면 {@code item_rule_checks} 가 INSERT 가 아니라
     * MERGE 로 가는데, {@code decided_at} 을 {@code @PrePersist} 에서만 채우던 탓에
     * {@code null} 로 UPDATE 되어 NOT NULL 제약에 걸렸다 — 두 번째 작업이 {@code FAILED} 였다.
     */
    @Test
    void 되묻기에_답하면_판정이_끝난다() {
        AiJob first = run(tripId, ask("20000mAh 보조배터리 기내 되나요?", photoId));
        assertThat(first.getStatus()).isEqualTo(Codes.JobStatus.COMPLETED);

        JsonNode results = json.read(first.getOutputPayload()).path("results");
        AiJob second = runJson("""
                {"transport":"FLIGHT","airline":null,"question":"100Wh예요","items":%s}
                """.formatted(asFollowUpItems(results)));

        assertThat(second.getStatus())
                .as("같은 물품을 다시 판정하면 decided_at 제약에 걸려 FAILED 가 되던 경로다")
                .isEqualTo(Codes.JobStatus.COMPLETED);
        assertThat(second.getErrorMessage()).isNull();
    }

    /**
     * 같은 사진을 다시 붙이면 이전 인식 행이 지워지고 새 id 로 다시 생긴다.
     * 그 사이 <b>서버가 돌려준 {@code detectionId}</b> 가 사라지면 다음 턴에 서버가 스스로
     * 거절한다 — 소유권 검사가 없는 id 를 {@code 404} 로 막기 때문이다.
     */
    @Test
    void 사진을_다시_붙여도_돌려준_인식_id_가_유효하다() {
        AiJob first = run(tripId, ask("이거 기내 되나요?", photoId));
        JsonNode firstResults = json.read(first.getOutputPayload()).path("results");

        AiJob second = runJson("""
                {"transport":"FLIGHT","airline":null,"question":"다시 봐주세요","items":%s,"photoIds":[%d]}
                """.formatted(asFollowUpItems(firstResults), photoId));
        assertThat(second.getStatus()).isEqualTo(Codes.JobStatus.COMPLETED);

        for (JsonNode r : json.read(second.getOutputPayload()).path("results")) {
            if (r.path("detectionId").isNull()) continue;
            Integer alive = jdbc.queryForObject(
                    "select count(*) from detected_objects where id = ?", Integer.class,
                    r.path("detectionId").asLong());
            assertThat(alive).as("서버가 돌려준 detectionId 가 살아 있어야 다음 턴이 이어진다").isEqualTo(1);
        }
    }

    /**
     * 07 이 <b>"사진에서 나온 물품도 함께 판정한다"</b> 고 적었다. 한도를 넘는다고 조용히
     * 버리면 목록에는 있는데 판정에서만 빠진 물품이 생기고, 응답에는 아무 안내도 없다.
     */
    @Test
    void 물품이_한도를_넘으면_조용히_버리지_않고_실패한다() {
        StringBuilder items = new StringBuilder("[");
        for (int i = 0; i < 50; i++) {
            if (i > 0) items.append(',');
            items.append("{\"itemId\":null,\"detectionId\":null,\"name\":\"물건").append(i)
                    .append("\",\"qty\":1,\"attributes\":")
                    .append("{\"capacityMl\":null,\"batteryWh\":null,\"batteryMah\":null,\"bladeCm\":null}}");
        }
        AiJob job = runJson("""
                {"transport":"FLIGHT","airline":null,"question":"이거 다 되나요?","items":%s,"photoIds":[%d]}
                """.formatted(items.append(']'), photoId));

        assertThat(job.getStatus()).isEqualTo(Codes.JobStatus.FAILED);
    }

    /**
     * 사진을 보고 <i>"이거 기내 되나요?"</i> 라고 물으면 질문이 물품을 말하지 않는다.
     * 그때도 <b>인식된 물품에는 규정 키워드가 붙어야</b> 07 에 적은 "사진 → 속성 확인" 흐름이 산다.
     */
    @Test
    void 질문이_물품을_말하지_않아도_사진_물품에_규정이_붙는다() {
        AiJob job = run(tripId, ask("이거 기내 되나요?", photoId));
        assertThat(job.getStatus()).isEqualTo(Codes.JobStatus.COMPLETED);

        JsonNode output = json.read(job.getOutputPayload());
        JsonNode battery = null;
        for (JsonNode r : output.path("results")) {
            if ("보조배터리".equals(r.path("name").asText(""))) battery = r;
        }
        assertThat(battery).as("사진에서 인식된 보조배터리").isNotNull();
        assertThat(battery.path("ruleKeyword").asText())
                .as("질문이 물품을 말하지 않아도 규정 키워드가 붙어야 한다")
                .isEqualTo("보조배터리");
        assertThat(battery.path("verdict").asText()).isEqualTo("NEED_MORE_INFO");
        assertThat(battery.path("missingInfo").asText()).isEqualTo("배터리 정격(Wh)");
        assertThat(output.path("followUpQuestion").asText()).isNotBlank();
    }

    /**
     * {@code input_payload} 는 <b>실제로 판정한 입력</b>이어야 한다. 사진 물품이 빠져 있으면
     * 답이 이상할 때 무엇을 보고 판정했는지 재구성할 수 없다.
     */
    @Test
    void 기록에_사진_물품이_함께_남는다() {
        AiJob job = run(tripId, ask("이거 기내 되나요?", photoId));

        JsonNode input = json.read(job.getInputPayload());
        JsonNode output = json.read(job.getOutputPayload());
        assertThat(input.path("items")).isNotEmpty();
        assertThat(input.path("items").size())
                .as("계약이 요구하는 items↔results 개수 일치가 기록에서도 성립해야 한다")
                .isEqualTo(output.path("results").size());
    }
}

package com.skala.miniai.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.skala.miniai.common.Codes;
import com.skala.miniai.common.Json;
import com.skala.miniai.domain.ai.AiClient;
import com.skala.miniai.domain.ai.AiJob;
import com.skala.miniai.domain.ai.AiJobRepository;
import com.skala.miniai.domain.ai.AiJobService;
import com.skala.miniai.domain.checklist.ChecklistDtos;
import com.skala.miniai.domain.checklist.ChecklistService;
import com.skala.miniai.domain.trip.TripDtos;
import com.skala.miniai.domain.trip.TripService;

/**
 * curl 로는 눈에 띄지 않는 두 가지를 잡아 둔다 — <b>실패 경로</b>와 <b>재시도 멱등성</b>.
 *
 * <p>두 테스트 다 실제로 한 번씩 깨졌던 동작이다. 손으로 확인할 때는 성공 경로만 보게 되고,
 * 여기서 막지 않으면 다음 리팩터링에 조용히 되돌아온다.
 *
 * <p>{@code @Transactional} 을 붙이지 않는다. 이 테스트가 확인하려는 것이 <b>커밋 경계</b>라
 * 테스트가 트랜잭션을 감싸면 검증 대상이 사라진다.
 */
@SpringBootTest
@ActiveProfiles("test")
class AiJobAndChecklistTest {

    @MockitoBean
    AiClient aiClient;

    @Autowired AiJobService aiJobService;
    @Autowired AiJobRepository jobs;
    @Autowired ChecklistService checklist;
    @Autowired TripService trips;
    @Autowired JdbcTemplate jdbc;
    @Autowired Json json;

    private Long tripId;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from item_placements");
        jdbc.update("delete from item_detections");
        jdbc.update("delete from item_rule_checks");
        jdbc.update("delete from checklist_items");
        jdbc.update("delete from ai_jobs");
        jdbc.update("delete from trip_itineraries");
        jdbc.update("delete from trips");
        jdbc.update("delete from users");
        jdbc.update("insert into users (id, login_id, email, password_hash, nickname, created_at) "
                + "values (1, 'tester', 'test@skala.dev', 'x', '테스트', current_timestamp)");
        // CurrentUser 는 세션에서 읽는다. HTTP 없이 부르는 테스트라 컨텍스트를 직접 채운다.
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(1L, null, java.util.List.of()));

        tripId = trips.create(new TripDtos.CreateRequest(
                "서울", "도쿄", "JP",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 4),
                Codes.Purpose.TOUR, Codes.Transport.FLIGHT,
                null, null, null, Codes.BagType.CARRY_ON, 3200, 10000, null)).tripId();
    }

    /**
     * AI 호출이 실패하면 작업은 {@code FAILED} 여야 한다.
     *
     * <p>실패 표시를 같은 트랜잭션에서 하면, 예외가 DB 작업에서 났을 때 그 트랜잭션이
     * rollback-only 라 <b>실패 표시까지 되돌아간다.</b> 그러면 작업이 {@code PENDING} 에 남고
     * 화면은 06 의 폴링 규약대로 끝없이 폴링한다.
     */
    @Test
    void aiJobBecomesFailedWhenClientThrows() {
        given(aiClient.run(any(), any())).willThrow(new IllegalStateException("모델 호출 실패"));

        Long jobId = aiJobService.create(new AiJobDtosFixture().packingList(tripId)).jobId();

        AiJob job = awaitSettled(jobId);
        assertThat(job.getStatus()).isEqualTo(Codes.JobStatus.FAILED);
        assertThat(job.getErrorMessage()).contains("내 체크리스트는 유지됩니다");
    }

    /**
     * <b>DB 제약 위반으로 실패해도</b> {@code FAILED} 여야 한다. 이쪽이 진짜 어려운 경우다.
     *
     * <p>모델 호출 예외는 트랜잭션을 더럽히지 않아서 같은 트랜잭션에서 실패를 표시해도 살아남는다.
     * 그런데 예외가 <b>DB 작업에서</b> 나면 그 트랜잭션은 rollback-only 가 되어 실패 표시까지
     * 함께 되돌아간다. 그래서 실패 표시를 별도 트랜잭션으로 뺐다.
     *
     * <p>여기서는 Mock 이 컬럼 한도를 넘는 물품 이름을 돌려주게 해 INSERT 를 실패시킨다.
     * 07 도 {@code name} 을 100자로 제한하므로, 모델이 규격을 어기면 실제로 이 경로다.
     */
    @Test
    void aiJobBecomesFailedWhenDatabaseRejectsResult() {
        jdbc.update("insert into trip_photos (trip_id, file_path, bag_kind, uploaded_at) "
                + "values (?, 'demo/x.jpg', 'CABIN', current_timestamp)", tripId);
        Long photoId = jdbc.queryForObject(
                "select id from trip_photos where trip_id = ?", Long.class, tripId);

        String tooLong = "충".repeat(200);
        given(aiClient.run(any(), any())).willReturn(json.read(
                "{\"detections\":[{\"photoId\":" + photoId + ",\"name\":\"" + tooLong + "\",\"qty\":1,"
                + "\"confidence\":0.93,\"confidenceLevel\":\"HIGH\","
                + "\"missingInfo\":null,\"labelText\":null}],\"failedPhotoIds\":[]}"));

        Long jobId = aiJobService.create(new com.skala.miniai.domain.ai.AiJobDtos.CreateRequest(
                Codes.JobType.BAG_CHECK, tripId, null)).jobId();

        assertThat(awaitSettled(jobId).getStatus())
                .as("DB 오류로 실패해도 PENDING 에 갇히면 화면이 끝없이 폴링한다")
                .isEqualTo(Codes.JobStatus.FAILED);
    }

    /**
     * 같은 추천 후보를 두 번 채택해도 항목은 하나다.
     *
     * <p>06: "후보의 {@code acceptedItemId} 가 이미 있으면 같은 항목을 200 으로 반환한다.
     * 재시도 본문의 이름·수량으로 기존 항목을 다시 덮어쓰거나 완료 상태를 되돌리지 않는다."
     */
    @Test
    void acceptingSameCandidateTwiceKeepsOneItem() {
        given(aiClient.run(any(), any())).willReturn(json.read("""
                {"items":[{"name":"변환 플러그","category":"ELECTRONIC","qty":1,
                           "priority":"REQUIRED","reason":"어댑터가 필요합니다.",
                           "source":"AI","acceptedItemId":null}],
                 "tips":[],"weatherSource":"SEASONAL","weatherAsOf":"2026-09-03"}"""));

        Long jobId = aiJobService.create(new AiJobDtosFixture().packingList(tripId)).jobId();
        assertThat(awaitSettled(jobId).getStatus()).isEqualTo(Codes.JobStatus.COMPLETED);

        var request = new ChecklistDtos.CreateRequest(
                "변환 플러그", Codes.Category.ELECTRONIC, 1, Codes.Priority.REQUIRED,
                new ChecklistDtos.RecommendationRef(jobId, 0));

        ChecklistService.Added first = checklist.add(tripId, request);
        assertThat(first.created()).isTrue();

        ChecklistService.Added second = checklist.add(tripId, request);
        assertThat(second.created()).as("재채택은 새로 만들지 않는다 — 200 이어야 한다").isFalse();
        assertThat(second.item().itemId()).isEqualTo(first.item().itemId());

        assertThat(checklist.list(tripId).items())
                .as("같은 후보로 항목이 두 개 생기면 안 된다")
                .hasSize(1);
    }


    /**
     * 사진 인식 물품은 <b>승인 없이</b> 내 목록에 {@code PREPARED} 로 등록된다 (06 개정).
     *
     * <p>개정 전에는 S-04 에서 사용자가 승인해야 목록에 들어갔다. 그 게이트가 사라졌으므로
     * BAG_CHECK 가 끝나는 것만으로 목록이 채워져야 한다. 신뢰도가 LOW 여도 등록한다 —
     * 등록을 막는 대신 화면이 "확인 필요" 를 표시한다.
     */
    @Test
    void photoDetectionsAreRegisteredWithoutApproval() {
        jdbc.update("insert into trip_photos (trip_id, file_path, bag_kind, uploaded_at) "
                + "values (?, 'demo/x.jpg', 'CABIN', current_timestamp)", tripId);
        Long photoId = jdbc.queryForObject(
                "select id from trip_photos where trip_id = ?", Long.class, tripId);

        given(aiClient.run(any(), any())).willReturn(json.read(
                "{\"detections\":["
                + "{\"photoId\":" + photoId + ",\"name\":\"충전기\",\"qty\":1,\"confidence\":0.93,"
                + "\"confidenceLevel\":\"HIGH\",\"missingInfo\":null,\"labelText\":null},"
                + "{\"photoId\":" + photoId + ",\"name\":\"검정 파우치\",\"qty\":1,\"confidence\":0.43,"
                + "\"confidenceLevel\":\"LOW\",\"missingInfo\":null,\"labelText\":null}],"
                + "\"failedPhotoIds\":[]}"));

        Long jobId = aiJobService.create(new com.skala.miniai.domain.ai.AiJobDtos.CreateRequest(
                Codes.JobType.BAG_CHECK, tripId, null)).jobId();
        assertThat(awaitSettled(jobId).getStatus()).isEqualTo(Codes.JobStatus.COMPLETED);

        var items = checklist.list(tripId).items();
        assertThat(items).as("LOW 신뢰도도 등록된다").hasSize(2);
        assertThat(items).allSatisfy(i -> {
            assertThat(i.checkStatus()).isEqualTo(Codes.CheckStatus.PREPARED);
            assertThat(i.source()).isEqualTo(Codes.ItemSource.PHOTO);
        });
        assertThat(checklist.list(tripId).completionRate())
                .as("자동 등록 물품은 완료 집계에 들어간다")
                .isEqualByComparingTo(new java.math.BigDecimal("1.000"));
    }

    /**
     * 같은 작업을 다시 돌려도 항목이 늘지 않고, 사용자가 되돌린 준비 상태를 덮어쓰지 않는다.
     *
     * <p>06 4·5번: "같은 완료 처리 재시도는 항목을 다시 생성하거나 사용자의 수정·삭제를
     * 되돌리지 않는다", "이미 반영된 연결을 다시 읽는 것만으로 사용자가 바꾼 UNCHECKED 를
     * 되돌리지 않는다."
     */
    @Test
    void reanalysisDoesNotDuplicateOrOverwriteUserEdits() {
        jdbc.update("insert into trip_photos (trip_id, file_path, bag_kind, uploaded_at) "
                + "values (?, 'demo/x.jpg', 'CABIN', current_timestamp)", tripId);
        Long photoId = jdbc.queryForObject(
                "select id from trip_photos where trip_id = ?", Long.class, tripId);

        String output = "{\"detections\":[{\"photoId\":" + photoId + ",\"name\":\"충전기\",\"qty\":1,"
                + "\"confidence\":0.93,\"confidenceLevel\":\"HIGH\","
                + "\"missingInfo\":null,\"labelText\":null}],\"failedPhotoIds\":[]}";
        given(aiClient.run(any(), any())).willReturn(json.read(output));

        Long first = aiJobService.create(new com.skala.miniai.domain.ai.AiJobDtos.CreateRequest(
                Codes.JobType.BAG_CHECK, tripId, null)).jobId();
        awaitSettled(first);

        Long itemId = checklist.list(tripId).items().get(0).itemId();
        // 사용자가 "아직 안 챙겼다" 로 되돌린다.
        checklist.update(tripId, itemId, new ChecklistDtos.UpdateRequest(
                null, null, null, null, Codes.CheckStatus.UNCHECKED));

        Long second = aiJobService.create(new com.skala.miniai.domain.ai.AiJobDtos.CreateRequest(
                Codes.JobType.BAG_CHECK, tripId, null)).jobId();
        awaitSettled(second);

        var items = checklist.list(tripId).items();
        assertThat(items).as("재분석이 항목을 또 만들면 안 된다").hasSize(1);
        assertThat(items.get(0).checkStatus())
                .as("사용자가 되돌린 UNCHECKED 를 재분석이 뒤집으면 안 된다")
                .isEqualTo(Codes.CheckStatus.UNCHECKED);
    }

    /**
     * 작업이 끝날 때까지 기다린다.
     *
     * <p>러너는 {@code @Async} + {@code AFTER_COMMIT} 이라 접수 트랜잭션이 커밋된 뒤
     * 다른 스레드에서 돈다. 직접 호출해도 프록시가 비동기로 넘겨 버리므로,
     * <b>실제 동작 그대로</b> 상태가 바뀔 때까지 폴링한다 — 화면이 하는 일과 같다.
     */
    private AiJob awaitSettled(Long jobId) {
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
        throw new AssertionError("작업 " + jobId + " 이 5초 안에 끝나지 않았다 — PENDING 에 갇혔다");
    }

    /** 요청 본문을 만드는 잡일을 테스트 본문에서 걷어낸다. */
    private static final class AiJobDtosFixture {
        com.skala.miniai.domain.ai.AiJobDtos.CreateRequest packingList(Long tripId) {
            return new com.skala.miniai.domain.ai.AiJobDtos.CreateRequest(
                    Codes.JobType.PACKING_LIST, tripId, null);
        }
    }
}

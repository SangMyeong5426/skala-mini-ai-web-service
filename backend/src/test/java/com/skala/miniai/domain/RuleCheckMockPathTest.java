package com.skala.miniai.domain;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.skala.miniai.common.Codes;
import com.skala.miniai.common.Json;
import com.skala.miniai.domain.ai.AiJob;
import com.skala.miniai.domain.ai.AiJobDtos;
import com.skala.miniai.domain.ai.AiJobRepository;
import com.skala.miniai.domain.ai.AiJobService;

/**
 * 규칙 엔진이 판정을 바꾼 뒤 <b>답변 쪽이 그 판정과 어긋나지 않는지</b> 본다.
 *
 * <p>리뷰에서 재현된 회귀다. 시드에 FLIGHT 규정만 있어서 {@code transport=TRAIN} 질문은
 * {@code ASK_AIRLINE} 이 되고 {@code missingInfo} 가 비는데, Mock 픽스처의 Wh 되묻기가 그대로
 * 남아 계약 검증이 막았다 — <b>202 로 접수된 정상 요청이 폴링 끝에 {@code FAILED} 로 끝났다.</b>
 *
 * <p>{@code AiClient} 를 갈아 끼우지 않는다. 실제 {@code MockAiClient} 와 규칙 엔진을 그대로
 * 태워야 두 경로가 어긋나는 지점을 잡을 수 있다. 발표 데모가 {@code AI_PROVIDER=mock} 이라
 * 이 경로가 기본이다.
 */
@SpringBootTest
@ActiveProfiles("test")
class RuleCheckMockPathTest {

    @Autowired AiJobService aiJobService;
    @Autowired AiJobRepository jobs;
    @Autowired JdbcTemplate jdbc;
    @Autowired Json json;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from item_rule_checks");
        jdbc.update("delete from ai_jobs");
        jdbc.update("delete from users");
        jdbc.update("insert into users (login_id, email, password_hash, nickname, created_at) "
                + "values ('rulepath', 'rulepath@skala.dev', 'x', '규칙경로', current_timestamp)");
        Long userId = jdbc.queryForObject("select id from users where login_id = 'rulepath'", Long.class);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(userId, null, List.of()));
    }

    private AiJob ask(String transport, String question) {
        JsonNode input = json.read("""
                {"transport":"%s","airline":null,"question":"%s","items":[]}
                """.formatted(transport, question));
        Long jobId = aiJobService.create(
                new AiJobDtos.CreateRequest(Codes.JobType.RULE_CHECK, null, input)).jobId();
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
    void 규정이_없는_이동수단_질문도_실패하지_않는다() {
        AiJob job = ask("TRAIN", "20000mAh 보조배터리 가져가도 되나요?");

        assertThat(job.getStatus())
                .as("엔진이 ASK_AIRLINE 으로 바꿨는데 픽스처의 되묻기가 남아 FAILED 가 되던 경로다")
                .isEqualTo(Codes.JobStatus.COMPLETED);

        JsonNode output = json.read(job.getOutputPayload());
        JsonNode result = output.path("results").path(0);
        assertThat(result.path("verdict").asText()).isEqualTo("ASK_AIRLINE");
        assertThat(result.path("missingInfo").isNull()).isTrue();
        // 되물을 것이 없으면 질문을 남기지 않는다 — 남기면 화면의 대화가 끝나지 않는다.
        assertThat(output.path("followUpQuestion").isNull()).isTrue();
        // 규정을 못 찾았으면 07 이 정한 문장을 쓴다. 픽스처의 "mAh만으로…" 는 규정을 찾은 척한다.
        assertThat(result.path("reason").asText()).isEqualTo("해당 규정을 찾지 못했습니다. 항공사에 확인하세요.");
        assertThat(result.path("sourceUrl").isNull()).isTrue();
    }

    /** 규정이 있는 경우는 예전 그대로여야 한다 — 되묻기가 살아 있어야 대화가 이어진다. */
    @Test
    void 규정이_있으면_되묻기가_그대로_남는다() {
        AiJob job = ask("FLIGHT", "20000mAh 보조배터리 기내 되나요?");

        assertThat(job.getStatus()).isEqualTo(Codes.JobStatus.COMPLETED);
        JsonNode output = json.read(job.getOutputPayload());
        assertThat(output.path("results").path(0).path("verdict").asText()).isEqualTo("NEED_MORE_INFO");
        assertThat(output.path("followUpQuestion").asText()).isNotBlank();
    }

    /**
     * 규정을 못 찾았는데 {@code answer} 가 구체적인 허용·금지를 말하면, 같은 응답 안에서
     * 결과와 안내가 충돌한다. <b>시드를 고치지 않아도</b> 재현되는 문제라 07 「알려진 한계」의
     * 규정표 드리프트와는 별개다.
     */
    @Test
    void 규정을_못_찾으면_답변도_미확인_안내로_바꾼다() {
        AiJob job = ask("TRAIN", "100Wh 보조배터리 가져가도 되나요?");

        assertThat(job.getStatus()).isEqualTo(Codes.JobStatus.COMPLETED);
        JsonNode output = json.read(job.getOutputPayload());
        assertThat(output.path("results").path(0).path("verdict").asText()).isEqualTo("ASK_AIRLINE");
        assertThat(output.path("results").path(0).path("ruleId").isNull()).isTrue();

        String answer = output.path("answer").asText();
        assertThat(answer)
                .as("규정을 못 찾았는데 '위탁수하물로 부칠 수 없습니다' 같은 확정 안내가 남으면 안 된다")
                .contains("찾지 못했습니다")
                .doesNotContain("위탁수하물로 부칠 수 없습니다");
        // 07: answer 는 이 문장으로 맺는다.
        assertThat(answer).endsWith("최종 반입 여부는 출발 당일 항공사와 보안검색기관의 판단을 따릅니다.");
    }

    /** 규정을 찾은 질문의 답변은 건드리지 않는다 — 위 정리가 과하게 덮지 않는지 함께 본다. */
    @Test
    void 규정을_찾으면_답변을_그대로_둔다() {
        AiJob job = ask("FLIGHT", "120ml 화장품 기내 반입되나요?");

        JsonNode output = json.read(job.getOutputPayload());
        assertThat(output.path("results").path(0).path("ruleId").isNull()).isFalse();
        assertThat(output.path("answer").asText()).doesNotContain("찾지 못했습니다");
    }
}

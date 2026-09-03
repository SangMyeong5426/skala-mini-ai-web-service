package com.skala.miniai.domain.ai;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.skala.miniai.common.Codes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * AI 작업 (AI-Ready 원칙 2 · 3).
 *
 * <p><b>상태를 DB 에 둬야</b> 응답이 느린 AI 를 비동기로 처리할 수 있다.
 * 지금은 Mock 이 채우고 나중에 LLM·비전 모델이 같은 자리를 채운다 —
 * <b>실제 AI 를 붙일 때 이 테이블은 바뀌지 않는다.</b>
 *
 * <p>{@code JSONB} 는 {@code @JdbcTypeCode(SqlTypes.JSON)} + {@code String} 이다.
 * 없으면 {@code found [jsonb], but expecting [varchar(255)]} 로 기동이 막힌다.
 * {@code hypersistence-utils} 같은 라이브러리는 넣지 않는다.
 *
 * <p>{@code tripId} 가 nullable 인 이유는 챗봇(UC-08)이 여행 없이도 쓰는 보조 흐름이라
 * {@code RULE_CHECK} 작업이 여행 없이 생길 수 있기 때문이다.
 */
@Entity
@Table(name = "ai_jobs")
public class AiJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "trip_id")
    private Long tripId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Codes.JobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 30)
    private Codes.JobType jobType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_payload", nullable = false)
    private String inputPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_payload")
    private String outputPayload;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    protected AiJob() { }

    public AiJob(Long userId, Long tripId, Codes.JobType jobType, String inputPayload) {
        this.userId = userId;
        this.tripId = tripId;
        this.jobType = jobType;
        this.inputPayload = inputPayload;
        this.status = Codes.JobStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (status == null) status = Codes.JobStatus.PENDING;
    }

    public void complete(String outputPayload, String modelName) {
        this.status = Codes.JobStatus.COMPLETED;
        this.outputPayload = outputPayload;
        this.modelName = modelName;
        this.completedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void fail(String errorMessage) {
        this.status = Codes.JobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getTripId() { return tripId; }
    public Codes.JobStatus getStatus() { return status; }
    public Codes.JobType getJobType() { return jobType; }
    public String getInputPayload() { return inputPayload; }
    public String getOutputPayload() { return outputPayload; }
    public String getModelName() { return modelName; }
    public String getErrorMessage() { return errorMessage; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }

    /** 추천 채택이 후보의 {@code acceptedItemId} 만 갱신할 때 쓴다 (06 · 05-erd 저장 규약). */
    public void replaceOutputPayload(String outputPayload) {
        this.outputPayload = outputPayload;
    }

    /**
     * 챗봇에 붙인 사진에서 나온 물품을 {@code items} 에 이어 붙인 뒤 기록을 맞춘다.
     *
     * <p>접수 시점에는 아직 인식을 돌리지 않아 사진 물품을 넣을 수 없다. 그대로 두면
     * {@code GET /api/ai-jobs/{id}} 의 {@code input} 이 <b>실제로 판정한 것과 달라</b>,
     * 답이 이상할 때 무엇을 보고 판정했는지 재구성할 수 없다.
     */
    public void replaceInputPayload(String inputPayload) {
        this.inputPayload = inputPayload;
    }
}

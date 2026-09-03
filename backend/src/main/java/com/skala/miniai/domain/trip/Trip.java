package com.skala.miniai.domain.trip;

import java.time.LocalDate;
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
 * 여행 (UC-02).
 *
 * <p>연관을 {@code @ManyToOne} 이 아니라 <b>FK 컬럼 그대로</b> 들고 있다.
 * {@code spring.jpa.open-in-view=false} 라 컨트롤러에서 지연 로딩이 터지는 것을 막고,
 * 서비스가 필요한 것만 명시적으로 조회하게 하기 위해서다 (AGENTS.md "지연 로딩").
 *
 * <p>{@code CHAR(2)}·{@code CHAR(3)} 은 {@code @JdbcTypeCode(SqlTypes.CHAR)} 가 없으면
 * {@code found [bpchar], but expecting [varchar(255)]} 로 기동이 막힌다.
 */
@Entity
@Table(name = "trips")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 출발지·도착지는 <b>이동수단과 무관하게 필수</b>다. 공항 코드로 대신하지 않는다. */
    @Column(nullable = false, length = 100)
    private String origin;

    @Column(nullable = false, length = 100)
    private String destination;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Codes.Purpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Codes.Transport transport;

    @Column(length = 50)
    private String airline;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "departure_airport", length = 3)
    private String departureAirport;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "arrival_airport", length = 3)
    private String arrivalAirport;

    @Enumerated(EnumType.STRING)
    @Column(name = "bag_type", length = 20)
    private Codes.BagType bagType;

    /** 무게 산정의 시작값. 명세 F-10 산정식의 "빈 가방 무게 범위". */
    @Column(name = "bag_empty_g")
    private Integer bagEmptyG;

    @Column(name = "weight_limit_g")
    private Integer weightLimitG;

    @Column(columnDefinition = "text")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Codes.TripStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Trip() { }

    public Trip(Long userId, String origin, String destination) {
        this.userId = userId;
        this.origin = origin;
        this.destination = destination;
        this.status = Codes.TripStatus.DRAFT;
    }

    /** DB 기본값이 {@code now()} 지만 INSERT 직후 응답에 {@code createdAt} 을 실어야 해서 여기서 채운다. */
    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (status == null) status = Codes.TripStatus.DRAFT;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public String getCountryCode() { return countryCode; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public Codes.Purpose getPurpose() { return purpose; }
    public Codes.Transport getTransport() { return transport; }
    public String getAirline() { return airline; }
    public String getDepartureAirport() { return departureAirport; }
    public String getArrivalAirport() { return arrivalAirport; }
    public Codes.BagType getBagType() { return bagType; }
    public Integer getBagEmptyG() { return bagEmptyG; }
    public Integer getWeightLimitG() { return weightLimitG; }
    public String getNote() { return note; }
    public Codes.TripStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setOrigin(String v) { this.origin = v; }
    public void setDestination(String v) { this.destination = v; }
    public void setCountryCode(String v) { this.countryCode = v; }
    public void setStartDate(LocalDate v) { this.startDate = v; }
    public void setEndDate(LocalDate v) { this.endDate = v; }
    public void setPurpose(Codes.Purpose v) { this.purpose = v; }
    public void setTransport(Codes.Transport v) { this.transport = v; }
    public void setAirline(String v) { this.airline = v; }
    public void setDepartureAirport(String v) { this.departureAirport = v; }
    public void setArrivalAirport(String v) { this.arrivalAirport = v; }
    public void setBagType(Codes.BagType v) { this.bagType = v; }
    public void setBagEmptyG(Integer v) { this.bagEmptyG = v; }
    public void setWeightLimitG(Integer v) { this.weightLimitG = v; }
    public void setNote(String v) { this.note = v; }
    public void setStatus(Codes.TripStatus v) { this.status = v; }
}

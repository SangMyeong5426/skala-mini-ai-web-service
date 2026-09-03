package com.skala.miniai.domain.user;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 시드 사용자 한 명만 쓴다. 인증 흐름은 이번 데모에서 구현하지 않는다.
 *
 * <p>{@code passwordHash} 는 스키마에 자리만 있고 아무도 읽지 않는다.
 * 나중에 로그인을 붙일 때 마이그레이션이 없도록 미리 둔 것이다 (docs/05-erd.md).
 */
@Entity
@Table(name = "users")
public class User {

    /** {@code GENERATED ALWAYS AS IDENTITY} → IDENTITY 다. AUTO 면 시퀀스를 찾다가 기동에 실패한다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected User() { }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getNickname() { return nickname; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}

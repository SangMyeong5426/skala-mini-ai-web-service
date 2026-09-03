package com.skala.miniai.domain.user;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.security.crypto.password.PasswordEncoder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 회원. <b>로그인 필수</b>다 (docs/01-service-plan.md · 06-api-spec.md).
 *
 * <p>{@code id} 와 {@code loginId} 를 나눈 이유는 역할이 다르기 때문이다 —
 * {@code id} 는 URL·외래키가 쓰는 내부 식별자고, {@code loginId} 는 사람이 치는 값이다.
 * 나중에 아이디 변경을 허용해도 기존 데이터가 따라 바뀌지 않는다.
 *
 * <p>{@code passwordHash} 는 <b>BCrypt 해시만</b> 담는다. 원문은 어디에도 저장하지 않고,
 * 해시조차 응답·로그·AI 입력에 내보내지 않는다 — 그래서 getter 를 두지 않고
 * 대조는 {@link #matches} 로만 한다.
 */
@Entity
@Table(name = "users")
public class User {

    /** {@code GENERATED ALWAYS AS IDENTITY} → IDENTITY 다. AUTO 면 시퀀스를 찾다가 기동에 실패한다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소문자로 정규화해 저장한다. 대소문자만 다른 중복 가입을 막는다. */
    @Column(name = "login_id", nullable = false, unique = true, length = 30)
    private String loginId;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected User() { }

    private User(String loginId, String email, String passwordHash, String nickname) {
        this.loginId = loginId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
    }

    /**
     * 가입. <b>이미 해시된 비밀번호만</b> 받는다 — 엔터티가 원문을 만지지 않게 해서
     * 실수로 평문이 저장되는 경로를 없앤다. 해싱은 {@code AuthService} 가 한다.
     */
    public static User signUp(String loginId, String email, String passwordHash, String nickname) {
        return new User(loginId, email, passwordHash, nickname);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /** 해시를 밖으로 꺼내지 않고 여기서만 대조한다. */
    public boolean matches(String rawPassword, PasswordEncoder encoder) {
        return encoder.matches(rawPassword, passwordHash);
    }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getEmail() { return email; }
    public String getNickname() { return nickname; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}

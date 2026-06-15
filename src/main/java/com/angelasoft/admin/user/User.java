package com.angelasoft.admin.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(nullable = false)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    /** 활동수첩(앱) 회원가입 완료 여부. 소셜 인증만으로는 false. */
    @Column(nullable = false)
    private boolean appRegistered;

    private String baptismalName;

    private String phoneNumber;

    private LocalDate birthDate;

    @Column(length = 10)
    private String gender;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private User(String email, String name, AuthProvider provider, String providerId, UserRole role) {
        this.email = email;
        this.name = name;
        this.provider = provider;
        this.providerId = providerId;
        this.role = role;
        this.appRegistered = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /** 소셜 재로그인 시 providerId·role만 갱신한다. 이름은 앱 회원가입 폼에서만 설정. */
    public void updateProfile(String providerId, UserRole role) {
        this.providerId = providerId;
        this.role = role;
        this.updatedAt = LocalDateTime.now();
    }

    /** 앱 회원가입 폼 제출 시 프로필을 채우고 가입 완료로 표시한다. */
    public void completeAppRegistration(
            String name,
            String baptismalName,
            String phoneNumber,
            LocalDate birthDate,
            String gender
    ) {
        this.name = name;
        this.baptismalName = baptismalName;
        this.phoneNumber = phoneNumber;
        this.birthDate = birthDate;
        this.gender = gender;
        this.appRegistered = true;
        this.updatedAt = LocalDateTime.now();
    }
}

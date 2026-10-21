package com.angelasoft.admin.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 관리자 콘솔(AngelaAdmin)의 단일 관리자 계정.
 *
 * 비밀번호 평문은 어디에도 저장하지 않고 BCrypt 해시(ADMIN_PASSWORD_HASH)만 설정으로 받는다.
 * 해시가 비어 있으면 관리자 로그인은 비활성화된다.
 */
@Slf4j
@Component
public class AdminCredentials {

    private final String username;
    private final String passwordHash;
    private final PasswordEncoder passwordEncoder;

    public AdminCredentials(
            @Value("${app.admin.username}") String username,
            @Value("${app.admin.password-hash}") String passwordHash,
            PasswordEncoder passwordEncoder
    ) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.passwordEncoder = passwordEncoder;
        if (!isEnabled()) {
            log.warn("[AdminLogin] ADMIN_USERNAME 또는 ADMIN_PASSWORD_HASH 가 비어 있어 관리자 로그인이 비활성화됨");
        }
    }

    private boolean isEnabled() {
        return !username.isBlank() && !passwordHash.isBlank();
    }

    public boolean matches(String inputUsername, String inputPassword) {
        if (!isEnabled() || inputUsername == null || inputPassword == null) {
            return false;
        }
        // 아이디가 틀려도 BCrypt 비교를 수행해 응답 시간으로 아이디 존재 여부를 알 수 없게 한다.
        boolean usernameMatches = MessageDigest.isEqual(
                username.getBytes(StandardCharsets.UTF_8),
                inputUsername.getBytes(StandardCharsets.UTF_8));
        boolean passwordMatches = passwordEncoder.matches(inputPassword, passwordHash);
        return usernameMatches && passwordMatches;
    }
}

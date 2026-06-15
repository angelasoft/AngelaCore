package com.angelasoft.admin.auth;

import com.angelasoft.admin.user.User;
import com.angelasoft.admin.user.UserRole;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

@Getter
public class UserPrincipal implements OAuth2User {

    private final Long userId;
    private final String email;
    private final String name;
    private final String providerId;
    private final UserRole role;
    private final boolean appRegistered;
    private final Map<String, Object> attributes;

    public UserPrincipal(User user, Map<String, Object> attributes) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.name = user.getName();
        this.providerId = user.getProviderId();
        this.role = user.getRole();
        this.appRegistered = user.isAppRegistered();
        this.attributes = attributes;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /**
     * OAuth2 principal 식별자.
     *
     * 활동수첩은 이름(name)을 회원가입 폼에서 직접 입력받으므로 소셜 인증 직후엔 비어 있다.
     * 따라서 principal 이름으로 name을 쓰면 안 되고, 항상 비어있지 않은 안정적인 식별자
     * (이메일 → providerId → userId)를 반환한다.
     */
    @Override
    public String getName() {
        if (email != null && !email.isBlank()) {
            return email;
        }
        if (providerId != null && !providerId.isBlank()) {
            return providerId;
        }
        return String.valueOf(userId);
    }
}

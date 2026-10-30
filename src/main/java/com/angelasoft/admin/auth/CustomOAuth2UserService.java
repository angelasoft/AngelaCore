package com.angelasoft.admin.auth;

import com.angelasoft.admin.user.AuthProvider;
import com.angelasoft.admin.user.User;
import com.angelasoft.admin.user.UserRole;
import com.angelasoft.admin.user.UserRepository;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    /** 앱이 이 오류 코드를 받으면 error_description 을 그대로 보여 준다. */
    static final String PROVIDER_MISMATCH = "provider_mismatch";

    private final UserRepository userRepository;

    @Value("${app.admin.emails:}")
    private String adminEmails;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId().toUpperCase();
        AuthProvider provider = AuthProvider.valueOf(registrationId);
        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.create(provider, oAuth2User.getAttributes());

        return new UserPrincipal(resolveUser(provider, userInfo), oAuth2User.getAttributes());
    }

    /**
     * 처음 가입할 때 쓴 소셜 계정(인증 수단 + 그 서비스의 회원 번호)으로만 로그인할 수 있다.
     * 같은 이메일이 다른 소셜 계정으로 가입되어 있으면 로그인을 거부한다.
     * 단, 앱 회원가입을 마치기 전인 계정은 새로 인증한 소셜 계정으로 바꿔 준다.
     */
    User resolveUser(AuthProvider provider, OAuth2UserInfo userInfo) {
        String email = userInfo.getEmail();
        if (email == null || "null".equals(email)) {
            throw new OAuth2AuthenticationException("OAuth2 provider did not return email.");
        }
        String providerId = userInfo.getProviderId();
        UserRole role = resolveRole(email);

        User user = userRepository.findByProviderAndProviderId(provider, providerId)
                .or(() -> userRepository.findByEmail(email).map(existing -> {
                    if (existing.isAppRegistered()) {
                        throw providerMismatch(existing, provider);
                    }
                    existing.relinkBeforeRegistration(provider, providerId);
                    return existing;
                }))
                .orElseGet(() -> User.builder()
                        .email(email)
                        .name("")
                        .provider(provider)
                        .providerId(providerId)
                        .role(role)
                        .build());
        user.updateRole(role);
        return userRepository.save(user);
    }

    private static OAuth2AuthenticationException providerMismatch(User existing, AuthProvider attempted) {
        String message = existing.getProvider() == attempted
                ? "이 이메일은 다른 " + attempted.getLabel() + " 계정으로 이미 가입되어 있습니다.\n"
                        + "처음 가입한 " + attempted.getLabel() + " 계정으로 로그인해 주세요."
                : "이미 " + existing.getProvider().getLabel() + " 계정으로 가입되어 있습니다.\n"
                        + existing.getProvider().getLabel() + "로 로그인해 주세요.";
        return new OAuth2AuthenticationException(new OAuth2Error(PROVIDER_MISMATCH, message, null), message);
    }

    private UserRole resolveRole(String email) {
        Set<String> admins = Arrays.stream(adminEmails.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        return admins.contains(email.toLowerCase()) ? UserRole.ADMIN : UserRole.USER;
    }
}

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
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Value("${app.admin.emails:}")
    private String adminEmails;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId().toUpperCase();
        AuthProvider provider = AuthProvider.valueOf(registrationId);
        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.create(provider, oAuth2User.getAttributes());

        if (userInfo.getEmail() == null || "null".equals(userInfo.getEmail())) {
            throw new OAuth2AuthenticationException("OAuth2 provider did not return email.");
        }
        UserRole role = resolveRole(userInfo.getEmail());

        User user = userRepository.findByEmail(userInfo.getEmail())
                .map(existing -> {
                    existing.updateProfile(userInfo.getProviderId(), role);
                    return existing;
                })
                .orElseGet(() -> User.builder()
                        .email(userInfo.getEmail())
                        .name("")
                        .provider(provider)
                        .providerId(userInfo.getProviderId())
                        .role(role)
                        .build());

        User saved = userRepository.save(user);
        return new UserPrincipal(saved, oAuth2User.getAttributes());
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

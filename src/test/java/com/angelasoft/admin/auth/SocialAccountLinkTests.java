package com.angelasoft.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.angelasoft.admin.user.AuthProvider;
import com.angelasoft.admin.user.User;
import com.angelasoft.admin.user.UserRepository;
import com.angelasoft.core.AngelaCoreApplication;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

/** 처음 가입한 소셜 계정으로만 로그인되고, 같은 이메일의 다른 소셜 계정은 거부된다. */
@SpringBootTest(classes = AngelaCoreApplication.class, properties = {
		"KAKAO_CLIENT_ID=test",
		"KAKAO_CLIENT_SECRET=test",
		"NAVER_CLIENT_ID=test",
		"NAVER_CLIENT_SECRET=test",
		"JWT_SECRET=test-jwt-secret-key-must-be-at-least-32-bytes-long"
})
class SocialAccountLinkTests {

	@Autowired
	private CustomOAuth2UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Test
	void registeredAccountLogsInOnlyWithItsOwnSocialAccount() {
		String email = UUID.randomUUID() + "@test.local";
		User user = userService.resolveUser(AuthProvider.KAKAO, kakao("1001", email));
		user.completeAppRegistration("단원", null, null, null, null);
		userRepository.save(user);

		assertThat(userService.resolveUser(AuthProvider.KAKAO, kakao("1001", email)).getId()).isEqualTo(user.getId());

		assertThatThrownBy(() -> userService.resolveUser(AuthProvider.NAVER, naver("N-1", email)))
				.isInstanceOf(OAuth2AuthenticationException.class)
				.hasMessageContaining("이미 카카오 계정으로 가입되어 있습니다")
				.satisfies(e -> assertThat(((OAuth2AuthenticationException) e).getError().getErrorCode())
						.isEqualTo(CustomOAuth2UserService.PROVIDER_MISMATCH));

		assertThatThrownBy(() -> userService.resolveUser(AuthProvider.KAKAO, kakao("2002", email)))
				.hasMessageContaining("다른 카카오 계정으로 이미 가입되어 있습니다");

		User reloaded = userRepository.findById(user.getId()).orElseThrow();
		assertThat(reloaded.getProvider()).isEqualTo(AuthProvider.KAKAO);
		assertThat(reloaded.getProviderId()).isEqualTo("1001");
	}

	@Test
	void unfinishedSignUpCanSwitchToAnotherSocialAccount() {
		String email = UUID.randomUUID() + "@test.local";
		User started = userService.resolveUser(AuthProvider.NAVER, naver("N-9", email));

		User switched = userService.resolveUser(AuthProvider.KAKAO, kakao("3003", email));

		assertThat(switched.getId()).isEqualTo(started.getId());
		assertThat(switched.getProvider()).isEqualTo(AuthProvider.KAKAO);
		assertThat(switched.getProviderId()).isEqualTo("3003");
	}

	private static OAuth2UserInfo kakao(String id, String email) {
		return OAuth2UserInfoFactory.create(AuthProvider.KAKAO,
				Map.of("id", id, "kakao_account", Map.of("email", email)));
	}

	private static OAuth2UserInfo naver(String id, String email) {
		return OAuth2UserInfoFactory.create(AuthProvider.NAVER,
				Map.of("response", Map.of("id", id, "email", email)));
	}
}

package com.angelasoft.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.angelasoft.admin.security.JwtProvider;
import com.angelasoft.admin.user.AuthProvider;
import com.angelasoft.admin.user.User;
import com.angelasoft.admin.user.UserRepository;
import com.angelasoft.admin.user.UserRole;
import com.angelasoft.core.AngelaCoreApplication;
import com.angelasoft.notification.push.PushToken;
import com.angelasoft.notification.push.PushTokenRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 기기 푸시 토큰 등록·재할당·삭제. 테스트에는 Firebase 키가 없어 푸시는 보내지 않는다. */
@SpringBootTest(classes = AngelaCoreApplication.class, properties = {
		"KAKAO_CLIENT_ID=test",
		"KAKAO_CLIENT_SECRET=test",
		"NAVER_CLIENT_ID=test",
		"NAVER_CLIENT_SECRET=test",
		"JWT_SECRET=test-jwt-secret-key-must-be-at-least-32-bytes-long"
})
@AutoConfigureMockMvc
class PushTokenTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private PushTokenRepository tokenRepository;

	@Autowired
	private NotificationService notificationService;

	@Test
	void tokenIsRegisteredMovedToLatestUserAndRemovedOnLogout() throws Exception {
		User first = newUser("첫째");
		User second = newUser("둘째");
		String token = "fcm-" + UUID.randomUUID();

		register(first, token, "android").andExpect(status().isNoContent());
		register(first, token, "ANDROID").andExpect(status().isNoContent());
		assertThat(tokenRepository.findByToken(token)).get().extracting(PushToken::getUserId).isEqualTo(first.getId());

		register(second, token, "ANDROID").andExpect(status().isNoContent());
		assertThat(tokenRepository.findByToken(token)).get().extracting(PushToken::getUserId).isEqualTo(second.getId());
		assertThat(tokenRepository.findByUserIdIn(List.of(first.getId()))).isEmpty();

		notificationService.notify(List.of(second.getId()), NotificationType.values()[0], "제목", "내용", null);

		mockMvc.perform(delete("/app/notifications/push-tokens").param("token", token)
						.header("Authorization", "Bearer " + bearer(first)))
				.andExpect(status().isNoContent());
		assertThat(tokenRepository.findByToken(token)).isPresent();

		mockMvc.perform(delete("/app/notifications/push-tokens").param("token", token)
						.header("Authorization", "Bearer " + bearer(second)))
				.andExpect(status().isNoContent());
		assertThat(tokenRepository.findByToken(token)).isEmpty();

		register(second, " ", "ANDROID").andExpect(status().isBadRequest());
		register(second, token, "WINDOWS").andExpect(status().isBadRequest());
	}

	private org.springframework.test.web.servlet.ResultActions register(User user, String token, String platform)
			throws Exception {
		return mockMvc.perform(post("/app/notifications/push-tokens")
				.header("Authorization", "Bearer " + bearer(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\": \"%s\", \"platform\": \"%s\"}".formatted(token, platform)));
	}

	private User newUser(String name) {
		User user = User.builder()
				.email(UUID.randomUUID() + "@test.local")
				.name("")
				.provider(AuthProvider.KAKAO)
				.providerId(UUID.randomUUID().toString())
				.role(UserRole.USER)
				.build();
		user.completeAppRegistration(name, null, null, null, null);
		return userRepository.save(user);
	}

	private String bearer(User user) {
		return jwtProvider.createAccessToken(user.getId(), user.getEmail(), user.getName(), UserRole.USER);
	}
}

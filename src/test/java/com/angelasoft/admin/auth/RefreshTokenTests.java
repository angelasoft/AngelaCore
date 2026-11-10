package com.angelasoft.admin.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.angelasoft.admin.user.AuthProvider;
import com.angelasoft.admin.user.User;
import com.angelasoft.admin.user.UserRepository;
import com.angelasoft.admin.user.UserRole;
import com.angelasoft.core.AngelaCoreApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 앱 로그인 유지: 갱신 토큰 회전, 재사용 감지, 로그아웃. */
@SpringBootTest(classes = AngelaCoreApplication.class, properties = {
		"KAKAO_CLIENT_ID=test",
		"KAKAO_CLIENT_SECRET=test",
		"NAVER_CLIENT_ID=test",
		"NAVER_CLIENT_SECRET=test",
		"JWT_SECRET=test-jwt-secret-key-must-be-at-least-32-bytes-long"
})
@AutoConfigureMockMvc
class RefreshTokenTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RefreshTokenService refreshTokenService;

	@Test
	void refreshRotatesTokensAndReuseRevokesTheSession() throws Exception {
		User user = newUser();
		String first = refreshTokenService.issue(user.getId());

		JsonNode refreshed = json(refresh(first)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.registered").value(true)));
		String access = refreshed.get("accessToken").asText();
		String second = refreshed.get("refreshToken").asText();

		// 새 접근 토큰으로 API 를 쓸 수 있다.
		mockMvc.perform(get("/app/auth/me").header("Authorization", "Bearer " + access))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(user.getId()));

		// 이미 쓴 갱신 토큰이 다시 오면 탈취로 보고, 새로 받은 토큰까지 끊는다.
		refresh(first).andExpect(status().isUnauthorized());
		refresh(second).andExpect(status().isUnauthorized());
	}

	@Test
	void logoutRevokesTheRefreshToken() throws Exception {
		User user = newUser();
		String token = refreshTokenService.issue(user.getId());

		mockMvc.perform(post("/app/auth/logout").contentType(MediaType.APPLICATION_JSON)
						.content("{\"refreshToken\": \"%s\"}".formatted(token)))
				.andExpect(status().isNoContent());
		refresh(token).andExpect(status().isUnauthorized());

		refresh("unknown-token").andExpect(status().isUnauthorized());
		mockMvc.perform(post("/app/auth/token/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void refreshFailsWhenTheUserIsGone() throws Exception {
		User user = newUser();
		String token = refreshTokenService.issue(user.getId());
		userRepository.delete(user);

		refresh(token).andExpect(status().isUnauthorized());
	}

	private ResultActions refresh(String token) throws Exception {
		return mockMvc.perform(post("/app/auth/token/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\": \"%s\"}".formatted(token)));
	}

	private JsonNode json(ResultActions result) throws Exception {
		return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
	}

	private User newUser() {
		User user = User.builder()
				.email(UUID.randomUUID() + "@test.local")
				.name("")
				.provider(AuthProvider.KAKAO)
				.providerId(UUID.randomUUID().toString())
				.role(UserRole.USER)
				.build();
		user.completeAppRegistration("단원", null, null, null, null);
		return userRepository.save(user);
	}
}

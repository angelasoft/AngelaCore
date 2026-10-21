package com.angelasoft.admin.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.angelasoft.core.AngelaCoreApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// htpasswd -bnBC 12 "" 'correct-password' 로 만든 해시 ($2y$ 형식도 받는지 함께 확인)
@SpringBootTest(classes = AngelaCoreApplication.class, properties = {
		"KAKAO_CLIENT_ID=test",
		"KAKAO_CLIENT_SECRET=test",
		"NAVER_CLIENT_ID=test",
		"NAVER_CLIENT_SECRET=test",
		"JWT_SECRET=test-jwt-secret-key-must-be-at-least-32-bytes-long",
		"ADMIN_USERNAME=admin",
		"ADMIN_PASSWORD_HASH=$2y$12$EiavtcEJuG/vGPPUoPAKZujlZF6WFnn6T9VZwLxLl6Ss9APqWys7q"
})
@AutoConfigureMockMvc
class AuthControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private String body(String username, String password) {
		return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
	}

	@Test
	void loginWithCorrectPasswordReturnsTokenUsableForMe() throws Exception {
		String response = mockMvc.perform(post("/auth/login")
						.with(r -> { r.setRemoteAddr("10.0.0.1"); return r; })
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("admin", "correct-password")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("관리자"))
				.andReturn().getResponse().getContentAsString();

		JsonNode json = objectMapper.readTree(response);
		mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + json.get("token").asText()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"));
	}

	@Test
	void wrongPasswordOrUsernameIsRejected() throws Exception {
		mockMvc.perform(post("/auth/login")
						.with(r -> { r.setRemoteAddr("10.0.0.2"); return r; })
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("admin", "wrong-password")))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/auth/login")
						.with(r -> { r.setRemoteAddr("10.0.0.2"); return r; })
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("someone", "correct-password")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void repeatedFailuresAreBlockedEvenWithCorrectPassword() throws Exception {
		for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES; i++) {
			mockMvc.perform(post("/auth/login")
							.with(r -> { r.setRemoteAddr("10.0.0.3"); return r; })
							.contentType(MediaType.APPLICATION_JSON)
							.content(body("admin", "wrong-password")))
					.andExpect(status().isUnauthorized());
		}
		mockMvc.perform(post("/auth/login")
						.with(r -> { r.setRemoteAddr("10.0.0.3"); return r; })
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("admin", "correct-password")))
				.andExpect(status().isTooManyRequests());
	}
}

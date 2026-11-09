package com.angelasoft.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** 로그인 없이 열리는 개인정보처리방침 페이지. */
@SpringBootTest(classes = AngelaCoreApplication.class, properties = {
		"KAKAO_CLIENT_ID=test",
		"KAKAO_CLIENT_SECRET=test",
		"NAVER_CLIENT_ID=test",
		"NAVER_CLIENT_SECRET=test",
		"JWT_SECRET=test-jwt-secret-key-must-be-at-least-32-bytes-long"
})
@AutoConfigureMockMvc
class LegalPagesTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void privacyPolicyIsPublic() throws Exception {
		mockMvc.perform(get("/privacy"))
				.andExpect(status().isOk())
				.andExpect(forwardedUrl("/privacy.html"));
		String html = mockMvc.perform(get("/privacy.html"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(html).contains("개인정보처리방침", "angelasoft@angelasoft.com");
	}
}

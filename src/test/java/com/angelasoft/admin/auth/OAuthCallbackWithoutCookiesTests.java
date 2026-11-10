package com.angelasoft.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.angelasoft.core.AngelaCoreApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 카카오톡 앱으로 인증하면 로그인을 시작한 인앱 브라우저가 아닌 다른 브라우저로 돌아와 쿠키가 없을 수 있다.
 * 그래도 401 에러 페이지가 아니라 앱 딥링크로 돌아가야 한다.
 */
@SpringBootTest(classes = AngelaCoreApplication.class, properties = {
		"KAKAO_CLIENT_ID=test",
		"KAKAO_CLIENT_SECRET=test",
		"NAVER_CLIENT_ID=test",
		"NAVER_CLIENT_SECRET=test",
		"JWT_SECRET=test-jwt-secret-key-must-be-at-least-32-bytes-long"
})
@AutoConfigureMockMvc
class OAuthCallbackWithoutCookiesTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void callbackWithoutCookiesStillReturnsToTheApp() throws Exception {
		String start = mockMvc.perform(get("/app/auth/oauth/kakao"))
				.andExpect(status().is3xxRedirection())
				.andReturn().getResponse().getRedirectedUrl();
		assertThat(start).isEqualTo("/oauth2/authorization/kakao?mode=mobile");

		String kakao = mockMvc.perform(get("/oauth2/authorization/kakao").param("mode", "mobile"))
				.andExpect(status().is3xxRedirection())
				.andReturn().getResponse().getRedirectedUrl();
		String state = UriComponentsBuilder.fromUriString(kakao).build().getQueryParams().getFirst("state");
		assertThat(state).isNotBlank();

		// 쿠키 없이 돌아온 콜백. 테스트용 인가 코드라 토큰 교환은 실패하지만, 실패도 앱으로 돌려보내야 한다.
		String callback = mockMvc.perform(get("/login/oauth2/code/kakao")
						.param("code", "test-code")
						.param("state", java.net.URLDecoder.decode(state, java.nio.charset.StandardCharsets.UTF_8)))
				.andExpect(status().is3xxRedirection())
				.andReturn().getResponse().getRedirectedUrl();
		assertThat(callback).startsWith("angela://oauth/callback?error=");
	}

	@Test
	void unknownStateFromPhoneBrowserGoesBackToTheApp() throws Exception {
		String callback = mockMvc.perform(get("/login/oauth2/code/kakao")
						.param("code", "test-code")
						.param("state", "unknown")
						.header("User-Agent", "Mozilla/5.0 (Linux; Android 14; SM-F971N) Chrome/130 Mobile"))
				.andExpect(status().is3xxRedirection())
				.andReturn().getResponse().getRedirectedUrl();
		assertThat(callback).startsWith("angela://oauth/callback?error=");

		// PC 브라우저(관리자 웹)는 그대로 401.
		mockMvc.perform(get("/login/oauth2/code/kakao")
						.param("code", "test-code")
						.param("state", "unknown")
						.header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) Chrome/130"))
				.andExpect(status().isUnauthorized());
	}
}

package com.angelasoft.activity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.angelasoft.core.AngelaCoreApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = AngelaCoreApplication.class, properties = {
		"KAKAO_CLIENT_ID=test",
		"KAKAO_CLIENT_SECRET=test",
		"NAVER_CLIENT_ID=test",
		"NAVER_CLIENT_SECRET=test",
		"JWT_SECRET=test-jwt-secret-key-must-be-at-least-32-bytes-long"
})
@AutoConfigureMockMvc
class ActivityControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsSeededActivitiesInReportOrderWithoutLogin() throws Exception {
		mockMvc.perform(get("/app/activities"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(40))
				.andExpect(jsonPath("$[0].name").value("묵주기도"))
				.andExpect(jsonPath("$[1].name").value("미사영성체"))
				.andExpect(jsonPath("$[33].name").value("입교권면"))
				.andExpect(jsonPath("$[34].name").value("행동단원모집"))
				.andExpect(jsonPath("$[39].name").value("예비자돌봄"));
	}
}

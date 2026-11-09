package com.angelasoft.presidium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.angelasoft.admin.security.JwtProvider;
import com.angelasoft.admin.user.AuthProvider;
import com.angelasoft.admin.user.User;
import com.angelasoft.admin.user.UserRepository;
import com.angelasoft.admin.user.UserRole;
import com.angelasoft.core.AngelaCoreApplication;
import com.angelasoft.region.ParishRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 쁘레시디움 정보와 이번 주 주회합 차수. */
@SpringBootTest(classes = AngelaCoreApplication.class, properties = {
		"KAKAO_CLIENT_ID=test",
		"KAKAO_CLIENT_SECRET=test",
		"NAVER_CLIENT_ID=test",
		"NAVER_CLIENT_SECRET=test",
		"JWT_SECRET=test-jwt-secret-key-must-be-at-least-32-bytes-long"
})
@AutoConfigureMockMvc
class PresidiumProfileTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ParishRepository parishRepository;

	@Autowired
	private JwtProvider jwtProvider;

	@Test
	void meetingNumberCountsWeeklyMeetingsFromApprovalDate() {
		LocalDate approved = LocalDate.of(2026, 1, 7); // 수요일
		// 승인일 당일이 회합 요일이면 그 주가 제1차.
		assertThat(PresidiumProfileService.meetingNumber(approved, DayOfWeek.WEDNESDAY, LocalDate.of(2026, 1, 5))).isEqualTo(1);
		assertThat(PresidiumProfileService.meetingNumber(approved, DayOfWeek.WEDNESDAY, LocalDate.of(2026, 1, 14))).isEqualTo(2);
		// 회합 요일이 승인일보다 앞이면 다음 주부터 센다.
		assertThat(PresidiumProfileService.meetingNumber(approved, DayOfWeek.MONDAY, LocalDate.of(2026, 1, 8))).isNull();
		assertThat(PresidiumProfileService.meetingNumber(approved, DayOfWeek.MONDAY, LocalDate.of(2026, 1, 12))).isEqualTo(1);
		// 한 해 뒤 같은 주.
		assertThat(PresidiumProfileService.meetingNumber(approved, DayOfWeek.WEDNESDAY, LocalDate.of(2027, 1, 6))).isEqualTo(53);
		// 요일을 정하지 않으면 승인일이 든 주가 제1차.
		assertThat(PresidiumProfileService.meetingNumber(approved, null, LocalDate.of(2026, 1, 11))).isEqualTo(1);
		assertThat(PresidiumProfileService.meetingNumber(null, DayOfWeek.WEDNESDAY, LocalDate.of(2026, 1, 11))).isNull();
	}

	@Test
	void onlyOfficersEditProfileAndEveryoneSeesIt() throws Exception {
		String president = token(newUser("단장"));
		String member = token(newUser("단원"));
		long parishId = parishRepository.findAll().get(0).getId();
		String suffix = UUID.randomUUID().toString().substring(0, 6);
		long presidiumId = objectMapper.readTree(call(post("/app/presidiums"), president, """
				{"parishId": %d, "curiaName": "정보Cu%s", "name": "정보Pr%s"}
				""".formatted(parishId, suffix, suffix)).andReturn().getResponse().getContentAsString())
				.at("/presidium/id").asLong();
		call(post("/app/presidiums/" + presidiumId + "/join"), member, null).andExpect(status().isOk());

		call(get("/app/presidiums/me/profile"), member, null)
				.andExpect(jsonPath("$.approvedDate").doesNotExist())
				.andExpect(jsonPath("$.canEdit").value(false));

		String body = """
				{"foundedDate": "2025-12-01", "approvedDate": "2026-01-07", "meetingDay": "WEDNESDAY",
				 "meetingTime": "19:30", "meetingPlace": "본당 교육관 2층"}
				""";
		call(put("/app/presidiums/me/profile"), member, body).andExpect(status().isForbidden());
		call(put("/app/presidiums/me/profile"), president, body)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.meetingDayLabel").value("수요일"))
				.andExpect(jsonPath("$.meetingTime").value("19:30"))
				.andExpect(jsonPath("$.thisWeekMeetingNumber").isNumber());

		call(get("/app/presidiums/me/profile"), member, null)
				.andExpect(jsonPath("$.meetingPlace").value("본당 교육관 2층"))
				.andExpect(jsonPath("$.approvedDate").value("2026-01-07"));

		call(put("/app/presidiums/me/profile"), president, """
				{"foundedDate": "2026-02-01", "approvedDate": "2026-01-07"}
				""").andExpect(status().isBadRequest());
	}

	private ResultActions call(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
		request.header("Authorization", "Bearer " + token);
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
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

	private String token(User user) {
		return jwtProvider.createAccessToken(user.getId(), user.getEmail(), user.getName(), UserRole.USER);
	}
}

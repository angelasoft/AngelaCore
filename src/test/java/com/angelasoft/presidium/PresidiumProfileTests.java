package com.angelasoft.presidium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.util.Set;
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

	private static Integer number(LocalDate approved, DayOfWeek day, LocalDate today) {
		return PresidiumProfileService.meetingCount(approved, day, 0, Set.of(), today).thisWeekNumber();
	}

	@Test
	void meetingNumberCountsWeeklyMeetingsFromApprovalDate() {
		LocalDate approved = LocalDate.of(2026, 1, 7); // 수요일
		// 승인일 당일이 회합 요일이면 그 주가 제1차.
		assertThat(number(approved, DayOfWeek.WEDNESDAY, LocalDate.of(2026, 1, 5))).isEqualTo(1);
		assertThat(number(approved, DayOfWeek.WEDNESDAY, LocalDate.of(2026, 1, 14))).isEqualTo(2);
		// 회합 요일이 승인일보다 앞이면 다음 주부터 센다.
		assertThat(number(approved, DayOfWeek.MONDAY, LocalDate.of(2026, 1, 8))).isNull();
		assertThat(number(approved, DayOfWeek.MONDAY, LocalDate.of(2026, 1, 12))).isEqualTo(1);
		// 한 해 뒤 같은 주.
		assertThat(number(approved, DayOfWeek.WEDNESDAY, LocalDate.of(2027, 1, 6))).isEqualTo(53);
		// 요일을 정하지 않으면 승인일이 든 주가 제1차.
		assertThat(number(approved, null, LocalDate.of(2026, 1, 11))).isEqualTo(1);
		assertThat(number(null, DayOfWeek.WEDNESDAY, LocalDate.of(2026, 1, 11))).isNull();
	}

	@Test
	void recessWeeksAreSkippedAndOffsetIsAdded() {
		LocalDate approved = LocalDate.of(2026, 1, 7); // 수요일, 1/5 주가 제1차
		Set<LocalDate> recesses = Set.of(
				LocalDate.of(2026, 1, 12), // 2주차 휴회
				LocalDate.of(2025, 12, 29)); // 첫 회합 전 휴회는 세지 않는다
		LocalDate thirdWeek = LocalDate.of(2026, 1, 21);

		var count = PresidiumProfileService.meetingCount(approved, DayOfWeek.WEDNESDAY, 0, recesses, thirdWeek);
		assertThat(count.thisWeekNumber()).isEqualTo(2);
		assertThat(count.thisWeekRecess()).isFalse();

		// 이번 주가 휴회면 차수 없이 다음 회합 차수.
		var recessWeek = PresidiumProfileService.meetingCount(
				approved, DayOfWeek.WEDNESDAY, 0, recesses, LocalDate.of(2026, 1, 14));
		assertThat(recessWeek.thisWeekNumber()).isNull();
		assertThat(recessWeek.thisWeekRecess()).isTrue();
		assertThat(recessWeek.nextNumber()).isEqualTo(2);

		// 보정값은 마지막에 더한다.
		assertThat(PresidiumProfileService.meetingCount(approved, DayOfWeek.WEDNESDAY, 100, recesses, thirdWeek)
				.thisWeekNumber()).isEqualTo(102);
		assertThat(PresidiumProfileService.meetingCount(approved, DayOfWeek.WEDNESDAY, -5, recesses, thirdWeek)
				.thisWeekNumber()).isNull();
	}

	@Test
	void officersRegisterAndRemoveRecessWeeks() throws Exception {
		String president = token(newUser("단장"));
		String member = token(newUser("단원"));
		createPresidiumAndJoin(president, member);

		call(post("/app/presidiums/me/profile/recesses"), member, """
				{"date": "2026-12-23", "reason": "성탄 휴회"}
				""").andExpect(status().isForbidden());

		String added = call(post("/app/presidiums/me/profile/recesses"), president, """
				{"date": "2026-12-23", "reason": "성탄 휴회"}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recesses[0].weekStart").value("2026-12-21"))
				.andExpect(jsonPath("$.recesses[0].weekEnd").value("2026-12-27"))
				.andExpect(jsonPath("$.recesses[0].reason").value("성탄 휴회"))
				.andReturn().getResponse().getContentAsString();

		// 같은 주의 다른 날짜는 중복.
		call(post("/app/presidiums/me/profile/recesses"), president, """
				{"date": "2026-12-25"}
				""").andExpect(status().isConflict());

		long recessId = objectMapper.readTree(added).at("/recesses/0/id").asLong();
		call(delete("/app/presidiums/me/profile/recesses/" + recessId), member, null).andExpect(status().isForbidden());
		call(delete("/app/presidiums/me/profile/recesses/" + recessId), president, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recesses").isEmpty());

		call(put("/app/presidiums/me/profile"), president, """
				{"approvedDate": "2026-01-07", "meetingNumberOffset": 9999}
				""").andExpect(status().isBadRequest());
		call(put("/app/presidiums/me/profile"), president, """
				{"approvedDate": "2026-01-07", "meetingNumberOffset": 120}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.meetingNumberOffset").value(120));
	}

	private void createPresidiumAndJoin(String president, String member) throws Exception {
		long parishId = parishRepository.findAll().get(0).getId();
		String suffix = UUID.randomUUID().toString().substring(0, 6);
		long presidiumId = objectMapper.readTree(call(post("/app/presidiums"), president, """
				{"parishId": %d, "curiaName": "휴회Cu%s", "name": "휴회Pr%s"}
				""".formatted(parishId, suffix, suffix)).andReturn().getResponse().getContentAsString())
				.at("/presidium/id").asLong();
		call(post("/app/presidiums/" + presidiumId + "/join"), member, null).andExpect(status().isOk());
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

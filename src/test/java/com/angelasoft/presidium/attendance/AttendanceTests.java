package com.angelasoft.presidium.attendance;

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
import com.angelasoft.presidium.MeetingCalendar;
import com.angelasoft.region.ParishRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
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

/** 주회합·평의회 출석부. */
@SpringBootTest(classes = AngelaCoreApplication.class, properties = {
		"KAKAO_CLIENT_ID=test",
		"KAKAO_CLIENT_SECRET=test",
		"NAVER_CLIENT_ID=test",
		"NAVER_CLIENT_SECRET=test",
		"JWT_SECRET=test-jwt-secret-key-must-be-at-least-32-bytes-long"
})
@AutoConfigureMockMvc
class AttendanceTests {

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
	void presidiumAttendanceOpensOnMeetingDayFromMeetingTime() {
		LocalDate friday = LocalDate.of(2026, 11, 13);
		LocalTime eight = LocalTime.of(20, 0);

		var before = AttendanceWindow.presidium(DayOfWeek.FRIDAY, eight, Set.of(), friday.atTime(19, 59));
		assertThat(before.open()).isFalse();
		assertThat(before.meetingDate()).isEqualTo(friday);

		assertThat(AttendanceWindow.presidium(DayOfWeek.FRIDAY, eight, Set.of(), friday.atTime(20, 0)).open()).isTrue();
		assertThat(AttendanceWindow.presidium(DayOfWeek.FRIDAY, eight, Set.of(), friday.atTime(23, 59)).open()).isTrue();

		var saturday = AttendanceWindow.presidium(DayOfWeek.FRIDAY, eight, Set.of(), friday.plusDays(1).atTime(9, 0));
		assertThat(saturday.open()).isFalse();
		assertThat(saturday.meetingDate()).isEqualTo(friday.plusWeeks(1));

		// 휴회 주에는 열리지 않고 다음 회합으로 넘어간다.
		var recess = AttendanceWindow.presidium(
				DayOfWeek.FRIDAY, eight, Set.of(LocalDate.of(2026, 11, 9)), friday.atTime(21, 0));
		assertThat(recess.open()).isFalse();
		assertThat(recess.meetingDate()).isEqualTo(friday.plusWeeks(1));

		assertThat(AttendanceWindow.presidium(null, eight, Set.of(), friday.atTime(21, 0)).configured()).isFalse();
	}

	@Test
	void councilMeetsOnNthWeekdayOfEachMonth() {
		// 2026년 9월 일요일: 6, 13, 20, 27
		assertThat(MeetingCalendar.councilDateIn(YearMonth.of(2026, 9), 3, DayOfWeek.SUNDAY)).isEqualTo(LocalDate.of(2026, 9, 20));
		assertThat(MeetingCalendar.councilDateIn(YearMonth.of(2026, 9), 5, DayOfWeek.SUNDAY)).isEqualTo(LocalDate.of(2026, 9, 27));
		assertThat(MeetingCalendar.councilLabel(3, DayOfWeek.SUNDAY)).isEqualTo("셋째 주 일요일");

		LocalTime two = LocalTime.of(14, 0);
		assertThat(AttendanceWindow.council(3, DayOfWeek.SUNDAY, two, LocalDateTime.of(2026, 9, 20, 14, 0)).open()).isTrue();
		assertThat(AttendanceWindow.council(3, DayOfWeek.SUNDAY, two, LocalDateTime.of(2026, 9, 20, 13, 59)).open()).isFalse();
		// 이번 달 회합이 지났으면 다음 달 셋째 주 일요일 (10/18).
		assertThat(AttendanceWindow.council(3, DayOfWeek.SUNDAY, two, LocalDateTime.of(2026, 9, 21, 10, 0)).meetingDate())
				.isEqualTo(LocalDate.of(2026, 10, 18));
	}

	@Test
	void officersRecordAttendanceAndEveryoneSeesIt() throws Exception {
		User president = newUser("단장");
		User member2 = newUser("둘째");
		User member3 = newUser("셋째");
		String presidentToken = token(president);
		String memberToken = token(member2);
		long parishId = parishRepository.findAll().get(0).getId();
		String suffix = UUID.randomUUID().toString().substring(0, 6);
		long presidiumId = objectMapper.readTree(call(post("/app/presidiums"), presidentToken, """
				{"parishId": %d, "curiaName": "출석Cu%s", "name": "출석Pr%s"}
				""".formatted(parishId, suffix, suffix)).andReturn().getResponse().getContentAsString())
				.at("/presidium/id").asLong();
		call(post("/app/presidiums/" + presidiumId + "/join"), memberToken, null).andExpect(status().isOk());
		call(post("/app/presidiums/" + presidiumId + "/join"), token(member3), null).andExpect(status().isOk());

		// 회합 요일·시간을 넣기 전에는 열리지 않는다.
		call(get("/app/presidiums/me/attendance?kind=PRESIDIUM"), presidentToken, null)
				.andExpect(jsonPath("$.configured").value(false))
				.andExpect(jsonPath("$.open").value(false));

		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
		call(put("/app/presidiums/me/profile"), presidentToken, """
				{"meetingDay": "%s", "meetingTime": "00:00", "meetingPlace": "교육관 304호"}
				""".formatted(today.getDayOfWeek())).andExpect(status().isOk());

		call(get("/app/presidiums/me/attendance?kind=PRESIDIUM"), memberToken, null)
				.andExpect(jsonPath("$.open").value(true))
				.andExpect(jsonPath("$.canEdit").value(false))
				.andExpect(jsonPath("$.roster.length()").value(3))
				.andExpect(jsonPath("$.roster[0].officerLabel").value("단장"))
				.andExpect(jsonPath("$.roster[0].present").doesNotExist());

		String body = "{\"presentUserIds\": [%d, %d]}".formatted(president.getId(), member2.getId());
		call(put("/app/presidiums/me/attendance/today?kind=PRESIDIUM"), memberToken, body).andExpect(status().isForbidden());
		call(put("/app/presidiums/me/attendance/today?kind=PRESIDIUM"), presidentToken, body)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recorded").value(true))
				.andExpect(jsonPath("$.roster[0].present").value(true))
				.andExpect(jsonPath("$.month.officerPresent").value(1))
				.andExpect(jsonPath("$.month.officerTotal").value(1))
				.andExpect(jsonPath("$.month.memberPresent").value(1))
				.andExpect(jsonPath("$.month.memberTotal").value(2));

		// 다시 저장하면 고쳐 쓴다 (중복 없음).
		String fixed = call(put("/app/presidiums/me/attendance/today?kind=PRESIDIUM"), presidentToken,
				"{\"presentUserIds\": [%d, %d, %d]}".formatted(president.getId(), member2.getId(), member3.getId()))
				.andExpect(jsonPath("$.history.length()").value(1))
				.andExpect(jsonPath("$.history[0].presentCount").value(3))
				.andExpect(jsonPath("$.history[0].totalCount").value(3))
				.andReturn().getResponse().getContentAsString();

		long sessionId = objectMapper.readTree(fixed).at("/history/0/id").asLong();
		call(get("/app/presidiums/me/attendance/" + sessionId), memberToken, null)
				.andExpect(jsonPath("$.records.length()").value(3))
				.andExpect(jsonPath("$.records[0].displayName").value(org.hamcrest.Matchers.startsWith("단장")));

		// 평의회: 4간부만 명단에 오르고, Cu 월례회의 일정이 있어야 열린다.
		call(get("/app/presidiums/me/attendance?kind=COUNCIL"), presidentToken, null)
				.andExpect(jsonPath("$.configured").value(false));
		int week = Math.min((today.getDayOfMonth() - 1) / 7 + 1, MeetingCalendar.LAST_WEEK);
		if (week == 4 && today.plusWeeks(1).getMonth() != today.getMonth()) {
			week = MeetingCalendar.LAST_WEEK;
		}
		String council = """
				{"weekOfMonth": %d, "day": "%s", "time": "00:00", "place": "본당 대성전"}
				""".formatted(week, today.getDayOfWeek());
		call(put("/app/presidiums/me/profile/council"), memberToken, council).andExpect(status().isForbidden());
		call(put("/app/presidiums/me/profile/council"), presidentToken, council)
				.andExpect(jsonPath("$.council.place").value("본당 대성전"))
				.andExpect(jsonPath("$.council.nextDate").value(today.toString()));
		call(get("/app/presidiums/me/attendance?kind=COUNCIL"), presidentToken, null)
				.andExpect(jsonPath("$.open").value(true))
				.andExpect(jsonPath("$.roster.length()").value(1));
		call(put("/app/presidiums/me/attendance/today?kind=COUNCIL"), presidentToken,
				"{\"presentUserIds\": [%d]}".formatted(member2.getId()))
				.andExpect(status().isBadRequest());
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

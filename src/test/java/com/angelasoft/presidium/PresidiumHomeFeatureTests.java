package com.angelasoft.presidium;

import static org.hamcrest.Matchers.hasSize;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 쁘레시디움 홈 카드 기능: 봉사 일정, 활동보고 제출·통계. */
@SpringBootTest(classes = AngelaCoreApplication.class, properties = {
		"KAKAO_CLIENT_ID=test",
		"KAKAO_CLIENT_SECRET=test",
		"NAVER_CLIENT_ID=test",
		"NAVER_CLIENT_SECRET=test",
		"JWT_SECRET=test-jwt-secret-key-must-be-at-least-32-bytes-long"
})
@AutoConfigureMockMvc
class PresidiumHomeFeatureTests {

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

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

	private String president;
	private String member;
	private Long presidentId;
	private Long memberId;

	@BeforeEach
	void setUp() throws Exception {
		User u1 = newUser("단장");
		User u2 = newUser("단원");
		president = token(u1);
		member = token(u2);
		presidentId = u1.getId();
		memberId = u2.getId();

		long parishId = parishRepository.findAll().get(0).getId();
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		long presidiumId = json(call(post("/app/presidiums"), president, """
				{"parishId": %d, "curiaName": "홈Cu%s", "name": "홈Pr%s"}
				""".formatted(parishId, suffix, suffix))).at("/presidium/id").asLong();
		call(post("/app/presidiums/" + presidiumId + "/join"), member, null).andExpect(status().isOk());
	}

	@Test
	void officersManageNoticesAndListIsNewestFirstWithPaging() throws Exception {
		call(post("/app/presidiums/me/notices"), member, notice("단원 공지")).andExpect(status().isForbidden());

		for (int i = 1; i <= 4; i++) {
			call(post("/app/presidiums/me/notices"), president, notice("공지" + i)).andExpect(status().isCreated());
		}

		JsonNode firstPage = json(call(get("/app/presidiums/me/notices?page=0&size=3"), member, null)
				.andExpect(jsonPath("$.items", hasSize(3)))
				.andExpect(jsonPath("$.items[0].title").value("공지4"))
				.andExpect(jsonPath("$.items[2].title").value("공지2"))
				.andExpect(jsonPath("$.hasNext").value(true)));
		call(get("/app/presidiums/me/notices?page=1&size=3"), member, null)
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].title").value("공지1"))
				.andExpect(jsonPath("$.hasNext").value(false));

		long id = firstPage.at("/items/0/id").asLong();
		call(put("/app/presidiums/me/notices/" + id), president, notice("수정된 공지"))
				.andExpect(jsonPath("$.title").value("수정된 공지"));
		call(put("/app/presidiums/me/notices/" + id), member, notice("x")).andExpect(status().isForbidden());
		call(delete("/app/presidiums/me/notices/" + id), member, null).andExpect(status().isForbidden());

		call(delete("/app/presidiums/me/notices/" + id), president, null).andExpect(status().isNoContent());
		call(get("/app/presidiums/me/notices/" + id), member, null).andExpect(status().isNotFound());

		call(post("/app/presidiums/me/notices"), president, "{\"title\": \" \", \"content\": \"내용\"}")
				.andExpect(status().isBadRequest());
	}

	@Test
	void officersManageSchedulesAndUpcomingAndPastAreOrdered() throws Exception {
		call(post("/app/presidiums/me/schedules"), member, schedule(TODAY.plusDays(1), "10:00", "본당"))
				.andExpect(status().isForbidden());

		for (int days : new int[] {10, 1, 5, 2, -1, -3}) {
			call(post("/app/presidiums/me/schedules"), president, schedule(TODAY.plusDays(days), "14:00", "장소" + days))
					.andExpect(status().isCreated());
		}

		call(get("/app/presidiums/me/schedules/upcoming?limit=3"), member, null)
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[0].serviceDate").value(TODAY.plusDays(1).toString()))
				.andExpect(jsonPath("$[0].serviceTime").value("14:00"))
				.andExpect(jsonPath("$[1].place").value("장소2"))
				.andExpect(jsonPath("$[2].place").value("장소5"));

		call(get("/app/presidiums/me/schedules/past?page=0&size=1"), member, null)
				.andExpect(jsonPath("$.items[0].place").value("장소-1"))
				.andExpect(jsonPath("$.hasNext").value(true));
		call(get("/app/presidiums/me/schedules/past?page=1&size=1"), member, null)
				.andExpect(jsonPath("$.items[0].place").value("장소-3"))
				.andExpect(jsonPath("$.hasNext").value(false));

		long id = json(call(get("/app/presidiums/me/schedules/upcoming?limit=1"), president, null)).get(0).get("id").asLong();
		call(put("/app/presidiums/me/schedules/" + id), president, schedule(TODAY.plusDays(1), "09:30", "성당 마당"))
				.andExpect(jsonPath("$.place").value("성당 마당"))
				.andExpect(jsonPath("$.serviceTime").value("09:30"));
		call(put("/app/presidiums/me/schedules/" + id), member, schedule(TODAY, "09:30", "x")).andExpect(status().isForbidden());

		call(delete("/app/presidiums/me/schedules/" + id), president, null).andExpect(status().isNoContent());
		call(get("/app/presidiums/me/schedules/" + id), member, null).andExpect(status().isNotFound());

		call(post("/app/presidiums/me/schedules"), president, """
				{"serviceDate": "%s", "serviceTime": "10:00", "place": " ", "content": "내용"}
				""".formatted(TODAY)).andExpect(status().isBadRequest());
	}

	@Test
	void submittedReportsAreSummedByActivityMemberAndMonth() throws Exception {
		JsonNode activities = json(mockMvc.perform(get("/app/activities")));
		long rosary = activities.get(0).get("id").asLong();
		long mass = activities.get(1).get("id").asLong();

		call(post("/app/presidiums/me/activity-reports"), president, """
				{"items": [{"activityId": %d, "count": 5}, {"activityId": %d, "count": 3}, {"activityId": %d, "count": 0}]}
				""".formatted(rosary, mass, mass))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.totalCount").value(8))
				.andExpect(jsonPath("$.reportDate").value(TODAY.toString()));
		call(post("/app/presidiums/me/activity-reports"), member, """
				{"items": [{"activityId": %d, "count": 2}]}
				""".formatted(rosary)).andExpect(status().isCreated());

		String month = YearMonth.from(TODAY).toString();
		call(get("/app/presidiums/me/activity-stats?month=" + month), member, null)
				.andExpect(jsonPath("$.totalCount").value(10))
				.andExpect(jsonPath("$.activities[0].activityId").value(rosary))
				.andExpect(jsonPath("$.activities[0].count").value(7))
				.andExpect(jsonPath("$.activities[1].count").value(3))
				.andExpect(jsonPath("$.members[0].userId").value(presidentId))
				.andExpect(jsonPath("$.members[0].count").value(8))
				.andExpect(jsonPath("$.members[1].count").value(2));

		call(get("/app/presidiums/me/activity-stats?month=" + month + "&userId=" + memberId), president, null)
				.andExpect(jsonPath("$.totalCount").value(2))
				.andExpect(jsonPath("$.members", hasSize(0)));

		call(get("/app/presidiums/me/activity-stats/yearly?year=" + TODAY.getYear()), member, null)
				.andExpect(jsonPath("$.months", hasSize(12)))
				.andExpect(jsonPath("$.months[" + (TODAY.getMonthValue() - 1) + "].count").value(10));

		call(post("/app/presidiums/me/activity-reports"), member, "{\"items\": []}").andExpect(status().isBadRequest());
		call(post("/app/presidiums/me/activity-reports"), member, "{\"items\": [{\"activityId\": 999999, \"count\": 1}]}")
				.andExpect(status().isBadRequest());
	}

	private static String notice(String title) {
		return """
				{"title": "%s", "content": "공지 내용입니다."}
				""".formatted(title);
	}

	private static String schedule(LocalDate date, String time, String place) {
		return """
				{"serviceDate": "%s", "serviceTime": "%s", "place": "%s", "content": "봉사 내용"}
				""".formatted(date, time, place);
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

	private ResultActions call(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
		request.header("Authorization", "Bearer " + token);
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private JsonNode json(ResultActions result) throws Exception {
		return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
	}
}

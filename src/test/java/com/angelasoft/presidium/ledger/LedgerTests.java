package com.angelasoft.presidium.ledger;

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

/** 회계 장부: 전월 이월금 → 줄마다 잔액 → 월계·누계·지출 내역 → 다음 달 이월금 추천. */
@SpringBootTest(classes = AngelaCoreApplication.class, properties = {
		"KAKAO_CLIENT_ID=test",
		"KAKAO_CLIENT_SECRET=test",
		"NAVER_CLIENT_ID=test",
		"NAVER_CLIENT_SECRET=test",
		"JWT_SECRET=test-jwt-secret-key-must-be-at-least-32-bytes-long"
})
@AutoConfigureMockMvc
class LedgerTests {

	private static final String OCT = "/app/presidiums/me/ledgers/2026-10";
	private static final String NOV = "/app/presidiums/me/ledgers/2026-11";

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

	@BeforeEach
	void setUp() throws Exception {
		president = token(newUser("단장"));
		member = token(newUser("단원"));
		long parishId = parishRepository.findAll().get(0).getId();
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		long presidiumId = json(call(post("/app/presidiums"), president, """
				{"parishId": %d, "curiaName": "회계Cu%s", "name": "회계Pr%s"}
				""".formatted(parishId, suffix, suffix))).at("/presidium/id").asLong();
		call(post("/app/presidiums/" + presidiumId + "/join"), member, null).andExpect(status().isOk());
	}

	@Test
	void ledgerComputesRunningBalanceMonthlyAndCumulativeTotals() throws Exception {
		call(get(OCT), member, null)
				.andExpect(jsonPath("$.exists").value(false))
				.andExpect(jsonPath("$.canEdit").value(false));

		String carryover = "{\"income\": 1344000, \"expense\": 1209000, \"balance\": 135000}";
		call(post(OCT), member, carryover).andExpect(status().isForbidden());
		call(post(OCT), president, carryover)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.carryover.balance").value(135000));
		call(post(OCT), president, carryover).andExpect(status().isConflict());

		// 회합일: 지출 먼저, 수입 다음. 줄마다 잔액이 계산된다.
		call(post(OCT + "/entries"), president, entry("2026-10-02", "EXPENSE", "FLOWER", null, 5000))
				.andExpect(jsonPath("$.entries[0].balance").value(130000))
				.andExpect(jsonPath("$.entries[0].description").value("꽃 값"));
		call(post(OCT + "/entries"), president, entry("2026-10-02", "INCOME", null, "제612차 비밀헌금", 32000))
				.andExpect(jsonPath("$.entries[1].balance").value(162000));
		call(post(OCT + "/entries"), president, entry("2026-10-08", "EXPENSE", "DONATION", null, 125000));
		JsonNode ledger = json(call(post(OCT + "/entries"), president,
				entry("2026-10-08", "EXPENSE", "OTHER", "회합 다과", 3000))
				.andExpect(jsonPath("$.entries", hasSize(4)))
				.andExpect(jsonPath("$.entries[3].balance").value(34000))
				.andExpect(jsonPath("$.monthly.income").value(32000))
				.andExpect(jsonPath("$.monthly.expense").value(133000))
				.andExpect(jsonPath("$.cumulative.income").value(1376000))
				.andExpect(jsonPath("$.cumulative.expense").value(1342000))
				.andExpect(jsonPath("$.cumulative.balance").value(34000))
				.andExpect(jsonPath("$.expenseBreakdown.donation").value(125000))
				.andExpect(jsonPath("$.expenseBreakdown.flower").value(5000))
				.andExpect(jsonPath("$.expenseBreakdown.other").value(3000))
				.andExpect(jsonPath("$.finalBalance").value(34000)));

		// 단원은 조회만 된다.
		call(get(OCT), member, null).andExpect(jsonPath("$.finalBalance").value(34000));
		call(post(OCT + "/entries"), member, entry("2026-10-09", "INCOME", null, "x", 1000))
				.andExpect(status().isForbidden());

		// 잘못된 입력
		call(post(OCT + "/entries"), president, entry("2026-11-01", "INCOME", null, "다음 달", 1000))
				.andExpect(status().isBadRequest());
		call(post(OCT + "/entries"), president, entry("2026-10-09", "EXPENSE", null, null, 1000))
				.andExpect(status().isBadRequest());
		call(post(OCT + "/entries"), president, entry("2026-10-09", "EXPENSE", "OTHER", " ", 1000))
				.andExpect(status().isBadRequest());
		call(post(OCT + "/entries"), president, entry("2026-10-09", "INCOME", null, "비밀헌금", 0))
				.andExpect(status().isBadRequest());

		// 중간 줄을 고치거나 지우면 아래 잔액이 다시 계산된다.
		long flowerId = ledger.at("/entries/0/id").asLong();
		call(put(OCT + "/entries/" + flowerId), president, entry("2026-10-02", "EXPENSE", "FLOWER", null, 10000))
				.andExpect(jsonPath("$.entries[0].balance").value(125000))
				.andExpect(jsonPath("$.finalBalance").value(29000));
		long donationId = ledger.at("/entries/2/id").asLong();
		call(delete(OCT + "/entries/" + donationId), president, null)
				.andExpect(jsonPath("$.entries", hasSize(3)))
				.andExpect(jsonPath("$.finalBalance").value(154000));

		// 다음 달은 아직 장부가 없고, 10월 누계를 이월금으로 추천한다.
		call(get(NOV), president, null)
				.andExpect(jsonPath("$.exists").value(false))
				.andExpect(jsonPath("$.canEdit").value(true))
				.andExpect(jsonPath("$.suggestedCarryover.income").value(1376000))
				.andExpect(jsonPath("$.suggestedCarryover.expense").value(1222000))
				.andExpect(jsonPath("$.suggestedCarryover.balance").value(154000));

		call(post(NOV + "/entries"), president, entry("2026-11-01", "INCOME", null, "헌금", 1000))
				.andExpect(status().isNotFound());
	}

	private static String entry(String date, String type, String category, String description, long amount) {
		return """
				{"date": "%s", "type": "%s", "category": %s, "description": %s, "amount": %d}
				""".formatted(
				date, type,
				category == null ? "null" : "\"" + category + "\"",
				description == null ? "null" : "\"" + description + "\"",
				amount);
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

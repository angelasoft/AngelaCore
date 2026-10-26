package com.angelasoft.presidium.governance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@SpringBootTest(classes = AngelaCoreApplication.class, properties = {
		"KAKAO_CLIENT_ID=test",
		"KAKAO_CLIENT_SECRET=test",
		"NAVER_CLIENT_ID=test",
		"NAVER_CLIENT_SECRET=test",
		"JWT_SECRET=test-jwt-secret-key-must-be-at-least-32-bytes-long"
})
@AutoConfigureMockMvc
class PresidiumGovernanceTests {

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

	private String founder;
	private String member2;
	private String member3;
	private Long member2Id;
	private Long member3Id;

	@BeforeEach
	void setUp() throws Exception {
		User u1 = newUser("단장");
		User u2 = newUser("둘째");
		User u3 = newUser("셋째");
		founder = token(u1);
		member2 = token(u2);
		member3 = token(u3);
		member2Id = u2.getId();
		member3Id = u3.getId();

		long parishId = parishRepository.findAll().get(0).getId();
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		JsonNode created = json(call(post("/app/presidiums"), founder, """
				{"parishId": %d, "curiaName": "테스트Cu%s", "name": "테스트Pr%s", "sessionNumber": 1}
				""".formatted(parishId, suffix, suffix))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.officerRole").value("PRESIDENT"))
				.andExpect(jsonPath("$.grade").value("ACTIVE")));
		long presidiumId = created.at("/presidium/id").asLong();

		call(post("/app/presidiums/" + presidiumId + "/join"), member2, null).andExpect(status().isOk());
		call(post("/app/presidiums/" + presidiumId + "/join"), member3, null).andExpect(status().isOk());
	}

	@Test
	void founderIsPresidentAndMajorityVoteAppointsOfficer() throws Exception {
		// 이미 가입한 단원은 다른 쁘레시디움을 만들 수 없다.
		long parishId = parishRepository.findAll().get(0).getId();
		call(post("/app/presidiums"), member2, """
				{"parishId": %d, "curiaName": "다른Cu", "name": "다른Pr"}
				""".formatted(parishId)).andExpect(status().isConflict());

		// 간부가 아니면 투표를 시작할 수 없다.
		call(post("/app/presidiums/me/elections"), member2, """
				{"position": "VICE_PRESIDENT", "candidateUserId": %d}
				""".formatted(member2Id)).andExpect(status().isForbidden());

		JsonNode election = json(call(post("/app/presidiums/me/elections"), founder, """
				{"position": "VICE_PRESIDENT", "candidateUserId": %d}
				""".formatted(member2Id))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.eligibleVoterCount").value(3))
				.andExpect(jsonPath("$.requiredVotes").value(2)));
		long electionId = election.get("id").asLong();

		// 투표 시작 알림이 시작한 사람을 뺀 활동단원에게 간다.
		call(get("/app/notifications/unread-count"), member3, null).andExpect(jsonPath("$.count").value(1));
		call(get("/app/notifications"), member3, null).andExpect(jsonPath("$[0].type").value("ELECTION_STARTED"));

		call(post("/app/presidiums/me/elections/" + electionId + "/votes"), founder, "{\"approve\": true}")
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.yesCount").value(1));
		call(post("/app/presidiums/me/elections/" + electionId + "/votes"), member3, "{\"approve\": true}")
				.andExpect(jsonPath("$.status").value("APPOINTED"));
		call(post("/app/presidiums/me/elections/" + electionId + "/votes"), member2, "{\"approve\": true}")
				.andExpect(status().isConflict());

		call(get("/app/presidiums/me"), member2, null)
				.andExpect(jsonPath("$.officerRole").value("VICE_PRESIDENT"))
				.andExpect(jsonPath("$.officerLabel").value("부단장"));
		call(get("/app/notifications"), member2, null)
				.andExpect(jsonPath("$[0].type").value("ELECTION_RESULT"))
				.andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.containsString("부단장으로 임명")));
	}

	@Test
	void requestsNeedMajorityOfOfficersAndWithdrawalRemovesMember() throws Exception {
		appointViceByVote();

		// 협조단원 신청: 간부 2명 중 과반(2명)이 승인해야 처리된다.
		long auxId = json(call(post("/app/presidiums/me/requests"), member3, "{\"type\": \"AUXILIARY\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.requiredApprovals").value(2))).get("id").asLong();
		call(get("/app/notifications"), founder, null).andExpect(jsonPath("$[0].type").value("MEMBERSHIP_REQUEST"));
		call(get("/app/notifications"), member2, null).andExpect(jsonPath("$[0].type").value("MEMBERSHIP_REQUEST"));
		call(post("/app/presidiums/me/requests"), member3, "{\"type\": \"WITHDRAWAL\"}").andExpect(status().isConflict());

		call(post("/app/presidiums/me/requests/" + auxId + "/decision"), founder, "{\"approve\": true}")
				.andExpect(jsonPath("$.status").value("PENDING"));
		call(post("/app/presidiums/me/requests/" + auxId + "/decision"), member2, "{\"approve\": true}")
				.andExpect(jsonPath("$.status").value("APPROVED"));
		call(get("/app/presidiums/me"), member3, null).andExpect(jsonPath("$.grade").value("AUXILIARY"));

		// 간부는 후임 임명 전에는 신청할 수 없다.
		call(post("/app/presidiums/me/requests"), member2, "{\"type\": \"WITHDRAWAL\"}").andExpect(status().isBadRequest());

		// 퇴단 신청: 한 명이 반려하면 과반 승인이 불가능해져 반려된다.
		long rejectedId = json(call(post("/app/presidiums/me/requests"), member3, "{\"type\": \"WITHDRAWAL\"}"))
				.get("id").asLong();
		call(post("/app/presidiums/me/requests/" + rejectedId + "/decision"), founder, "{\"approve\": false}")
				.andExpect(jsonPath("$.status").value("REJECTED"));

		long withdrawalId = json(call(post("/app/presidiums/me/requests"), member3, "{\"type\": \"WITHDRAWAL\"}"))
				.get("id").asLong();
		call(post("/app/presidiums/me/requests/" + withdrawalId + "/decision"), founder, "{\"approve\": true}");
		call(post("/app/presidiums/me/requests/" + withdrawalId + "/decision"), member2, "{\"approve\": true}")
				.andExpect(jsonPath("$.status").value("APPROVED"));

		call(get("/app/presidiums/me"), member3, null).andExpect(jsonPath("$.joined").value(false));
		call(get("/app/notifications"), member3, null)
				.andExpect(jsonPath("$[0].type").value("MEMBERSHIP_REQUEST_RESULT"))
				.andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.containsString("퇴단 처리")));
		call(get("/app/presidiums/me/members"), founder, null).andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void appApisReturn401WithoutLogin() throws Exception {
		mockMvc.perform(get("/app/notifications")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/app/presidiums/me/members")).andExpect(status().isUnauthorized());
	}

	private void appointViceByVote() throws Exception {
		long electionId = json(call(post("/app/presidiums/me/elections"), founder, """
				{"position": "VICE_PRESIDENT", "candidateUserId": %d}
				""".formatted(member2Id))).get("id").asLong();
		call(post("/app/presidiums/me/elections/" + electionId + "/votes"), founder, "{\"approve\": true}");
		call(post("/app/presidiums/me/elections/" + electionId + "/votes"), member2, "{\"approve\": true}")
				.andExpect(jsonPath("$.status").value("APPOINTED"));
	}

	private User newUser(String name) {
		User user = User.builder()
				.email(UUID.randomUUID() + "@test.local")
				.name("")
				.provider(AuthProvider.KAKAO)
				.providerId(UUID.randomUUID().toString())
				.role(UserRole.USER)
				.build();
		user.completeAppRegistration(name, "세례명", null, null, null);
		return userRepository.save(user);
	}

	private String token(User user) {
		return jwtProvider.createAccessToken(user.getId(), user.getEmail(), user.getName(), UserRole.USER);
	}

	private ResultActions call(
			org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
			String token,
			String body
	) throws Exception {
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

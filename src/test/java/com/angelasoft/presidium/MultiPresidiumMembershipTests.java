package com.angelasoft.presidium;

import static org.hamcrest.Matchers.hasSize;
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
import com.angelasoft.presidium.governance.MembershipLookup;
import com.angelasoft.region.ParishRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 어른 쁘레시디움은 1곳, 소년소녀 쁘레시디움은 여러 곳 가입하고, 고른 쁘레시디움별로 데이터가 나뉜다. */
@SpringBootTest(classes = AngelaCoreApplication.class, properties = {
		"KAKAO_CLIENT_ID=test",
		"KAKAO_CLIENT_SECRET=test",
		"NAVER_CLIENT_ID=test",
		"NAVER_CLIENT_SECRET=test",
		"JWT_SECRET=test-jwt-secret-key-must-be-at-least-32-bytes-long"
})
@AutoConfigureMockMvc
class MultiPresidiumMembershipTests {

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
	void adultJoinsOneAdultAndManyJuniorPresidiums() throws Exception {
		String leader = token(newUser("어른단원"));
		String other = token(newUser("다른단장"));
		long parishId = parishRepository.findAll().get(0).getId();

		long adult = create(leader, parishId, "어른Pr", null).andExpect(status().isCreated())
				.andExpect(jsonPath("$.presidium.type").value("ADULT"))
				.andExpect(jsonPath("$.presidium.typeLabel").value("어른"))
				.andReturn().getResponse().getContentAsString().transform(this::presidiumId);
		long juniorA = create(leader, parishId, "소년A", "JUNIOR").andExpect(status().isCreated())
				.andExpect(jsonPath("$.presidium.type").value("JUNIOR"))
				.andReturn().getResponse().getContentAsString().transform(this::presidiumId);
		create(leader, parishId, "소년B", "JUNIOR").andExpect(status().isCreated());
		create(leader, parishId, "두번째어른", "ADULT").andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("어른 쁘레시디움은 한 곳만")));

		long otherAdult = create(other, parishId, "다른어른", null).andReturn().getResponse()
				.getContentAsString().transform(this::presidiumId);
		long otherJunior = create(other, parishId, "다른소년", "JUNIOR").andReturn().getResponse()
				.getContentAsString().transform(this::presidiumId);
		call(post("/app/presidiums/" + otherAdult + "/join"), leader, null, null).andExpect(status().isConflict());
		call(post("/app/presidiums/" + otherJunior + "/join"), leader, null, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.grade").value("ACTIVE"));

		call(get("/app/presidiums/mine"), leader, null, null)
				.andExpect(jsonPath("$", hasSize(4)))
				.andExpect(jsonPath("$[0].presidium.id").value(adult))
				.andExpect(jsonPath("$[1].presidium.type").value("JUNIOR"));

		call(get("/app/presidiums/me"), leader, null, null)
				.andExpect(jsonPath("$.presidium.id").value(adult));
		call(get("/app/presidiums/me"), leader, juniorA, null)
				.andExpect(jsonPath("$.presidium.id").value(juniorA))
				.andExpect(jsonPath("$.officerRole").value("PRESIDENT"));
		call(get("/app/presidiums/me/notices"), leader, otherAdult, null).andExpect(status().isForbidden());

		call(post("/app/presidiums/me/notices"), leader, juniorA, """
				{"title": "소년 공지", "content": "소년 쁘레시디움 공지입니다."}
				""").andExpect(status().isCreated());
		call(get("/app/presidiums/me/notices"), leader, juniorA, null)
				.andExpect(jsonPath("$.items", hasSize(1)));
		call(get("/app/presidiums/me/notices"), leader, adult, null)
				.andExpect(jsonPath("$.items", hasSize(0)));
	}

	private ResultActions create(String token, long parishId, String name, String type) throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 6);
		String typeJson = type == null ? "" : ", \"type\": \"%s\"".formatted(type);
		return call(post("/app/presidiums"), token, null, """
				{"parishId": %d, "curiaName": "Cu%s", "name": "%s%s"%s}
				""".formatted(parishId, suffix, name, suffix, typeJson));
	}

	private long presidiumId(String json) {
		try {
			return objectMapper.readTree(json).at("/presidium/id").asLong();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private ResultActions call(MockHttpServletRequestBuilder request, String token, Long presidiumId, String body)
			throws Exception {
		request.header("Authorization", "Bearer " + token);
		if (presidiumId != null) {
			request.header(MembershipLookup.PRESIDIUM_HEADER, presidiumId);
		}
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

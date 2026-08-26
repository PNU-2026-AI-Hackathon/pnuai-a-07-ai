package com.safework.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** 테스트에서 반복되는 인증·JSON 처리를 모아둔 헬퍼. */
public class ApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MockMvc mockMvc;

    public ApiClient(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    /** 회원가입하고 토큰을 돌려준다. 테스트마다 다른 이메일을 써야 충돌하지 않는다. */
    public String registerAndGetToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(Map.of(
                                "email", email, "password", "test1234", "name", "테스트"))))
                .andReturn();

        JsonNode body = json(result);
        if (!body.has("accessToken")) {
            // 여기서 죽으면 원인 파악이 어려우므로 상태코드와 본문을 그대로 드러낸다.
            throw new IllegalStateException("회원가입 실패 status=%d body=%s"
                    .formatted(result.getResponse().getStatus(), body));
        }
        return body.get("accessToken").asText();
    }

    public MvcResult postJson(String path, String token, Object body) throws Exception {
        return mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andReturn();
    }

    public MvcResult patchJson(String path, String token, Object body) throws Exception {
        return mockMvc.perform(patch(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andReturn();
    }

    public MvcResult putJson(String path, String token, Object body) throws Exception {
        return mockMvc.perform(put(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andReturn();
    }

    /** 비밀번호 변경 후 실제로 로그인이 되는지 확인할 때 쓴다. */
    public MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(Map.of("email", email, "password", password))))
                .andReturn();
    }

    public MvcResult getWithToken(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn();
    }

    /**
     * 쿼리 파라미터가 있는 GET.
     *
     * 한글 값을 URL 에 직접 이어붙이면 안 된다. MockMvc 는 퍼센트 인코딩을 디코딩하지 않아
     * "%EC%A0%9C%EC%A1%B0%EC%97%85" 이 그대로 값으로 들어가 조회 결과가 비어 버린다.
     */
    public MvcResult getWithParams(String path, String token, Map<String, String> params)
            throws Exception {
        var request = get(path).header("Authorization", "Bearer " + token);
        params.forEach(request::param);
        return mockMvc.perform(request).andReturn();
    }

    public static JsonNode json(MvcResult result) throws Exception {
        return MAPPER.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** 테스트에서 자주 쓰는 제조업 사업장 하나를 만들고 id 를 돌려준다. */
    public long createManufacturingWorkplace(String token) throws Exception {
        MvcResult result = postJson("/api/workplaces", token, Map.of(
                "name", "테스트금속", "industry", "제조업", "subIndustry", "금속가공",
                "sizeClass", "5인 미만", "region", "부산", "employeeCount", 4));
        return json(result).get("id").asLong();
    }
}

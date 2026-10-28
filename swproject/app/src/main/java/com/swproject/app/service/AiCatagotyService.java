package com.swproject.app.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AiCatagotyService {

    @Value("${API}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private static final List<String> CATEGORIES = List.of(
            "식비", "교통", "쇼핑", "구독", "문화/여가", "의료/건강", "통신", "미분류"
    );

    // 적요 목록을 한 번에 넘기면, 같은 순서로 카테고리 목록을 반환 (배치 호출)
    public List<String> classifyBatch(List<String> descriptions) {
        if (descriptions.isEmpty()) return List.of();

        String prompt = buildPrompt(descriptions);
        String modelText = callApi(prompt);
        return parseCategories(modelText, descriptions.size());
    }

    // 제공사마다 달라지는 부분은 이 메서드 하나 (현재: Claude)
    private String callApi(String prompt) {
        Map<String, Object> body = Map.of(
                "model", "claude-haiku-4-5-20251001",
                "max_tokens", 1024,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");
        headers.set("content-type", "application/json");

        String json = restTemplate.postForObject(
                "https://api.anthropic.com/v1/messages", new HttpEntity<>(body, headers), String.class);
        try {
            // Jackson 3 에서는 asText() 가 asString() 으로 이름이 바뀜
            return jsonMapper.readTree(json).path("content").get(0).path("text").asString();
        } catch (Exception e) {
            return "";
        }
    }

    private String buildPrompt(List<String> descriptions) {
        StringBuilder sb = new StringBuilder();
        sb.append("아래는 가계부 거래 내역의 적요(가맹점명/내용) 목록이다.\n");
        sb.append("각 항목을 다음 카테고리 중 하나로 분류하라: ").append(String.join(", ", CATEGORIES)).append("\n");
        sb.append("확실하지 않으면 '미분류'로 분류하라.\n");
        sb.append("반드시 JSON 배열 형식으로만 답하라. 다른 설명 문장은 절대 포함하지 마라.\n");
        sb.append("예시 형식: [\"식비\", \"교통\", \"미분류\"]\n\n");
        sb.append("목록:\n");
        for (int i = 0; i < descriptions.size(); i++) {
            sb.append(i + 1).append(". ").append(descriptions.get(i)).append("\n");
        }
        return sb.toString();
    }

    // 모델이 준 텍스트에서 JSON 배열을 뽑아 카테고리 목록으로 변환
    private List<String> parseCategories(String text, int expectedSize) {
        try {
            int start = text.indexOf('[');
            int end = text.lastIndexOf(']');
            JsonNode arrayNode = jsonMapper.readTree(text.substring(start, end + 1));

            List<String> result = new ArrayList<>();
            for (int i = 0; i < arrayNode.size(); i++) {
                result.add(arrayNode.get(i).asString());
            }
            while (result.size() < expectedSize) result.add("미분류");
            return result;

        } catch (Exception e) {
            // 파싱 실패 시 전부 미분류로 처리 (앱이 죽지 않도록)
            List<String> fallback = new ArrayList<>();
            for (int i = 0; i < expectedSize; i++) fallback.add("미분류");
            return fallback;
        }
    }
}
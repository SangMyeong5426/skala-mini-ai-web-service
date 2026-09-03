package com.skala.miniai.domain.ai;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.skala.miniai.common.Json;

/**
 * OpenAI Chat Completions 호출부. <b>여기만 HTTP 를 안다.</b>
 *
 * <p>SDK 를 넣지 않고 {@code RestClient} 로 직접 부른다. 의존성이 하나 늘면 팀원이
 * {@code ./gradlew build} 를 다시 돌려야 하고, 우리가 쓰는 것은 엔드포인트 하나뿐이다
 * (AGENTS.md: "{@code hypersistence-utils} 같은 라이브러리를 넣지 않는다" 와 같은 이유).
 *
 * <p><b>Structured Outputs 를 쓴다.</b> {@code response_format} 에 JSON Schema 를 {@code strict}
 * 로 주면 모델이 그 모양을 벗어난 답을 내지 못한다. 07 이 "출력은 JSON 객체 하나뿐" 이라고
 * 프롬프트로 부탁한 것을 API 차원에서 강제하는 것이라, 코드펜스·설명 문장을 벗겨낼 일이 없다.
 *
 * <p>모델명·키·온도·토큰 한도는 전부 {@code .env} 에서 읽는다 (AI-Ready 원칙 4).
 * <b>키는 어떤 로그·예외 메시지에도 남기지 않는다.</b>
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
public class OpenAiChatApi {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatApi.class);

    /** 오류 본문을 통째로 로그에 붙이지 않는다. 앞부분만으로 원인은 충분히 보인다. */
    private static final int ERROR_BODY_LIMIT = 500;

    private final RestClient http;
    private final Json json;
    private final String model;
    private final double temperature;
    private final int maxTokens;

    public OpenAiChatApi(Json json,
                         @Value("${app.ai.base-url}") String baseUrl,
                         @Value("${app.ai.api-key:}") String apiKey,
                         @Value("${app.ai.model}") String model,
                         @Value("${app.ai.temperature:0.2}") double temperature,
                         @Value("${app.ai.max-tokens:4096}") int maxTokens,
                         @Value("${app.ai.timeout-ms:60000}") long timeoutMs) {

        // 기동할 때 막는다. 첫 사진 분석에서야 401 을 만나면 원인을 찾는 데 시간이 든다.
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "AI_PROVIDER=openai 인데 AI_API_KEY 가 비어 있습니다. backend/.env 를 확인하세요.");
        }
        if (model == null || model.isBlank() || "mock".equalsIgnoreCase(model)) {
            throw new IllegalStateException(
                    "AI_PROVIDER=openai 인데 AI_MODEL 이 '" + model + "' 입니다. 비전 모델 이름을 넣으세요.");
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.min(timeoutMs, 10_000)));
        // 사진 여러 장을 보는 호출은 느리다. 여기서 끊기면 작업이 FAILED 로 남는다.
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.json = json;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;

        log.info("OpenAI 연동을 켰습니다. model={} baseUrl={} timeoutMs={}", model, baseUrl, timeoutMs);
    }

    public String model() {
        return model;
    }

    /**
     * 사진과 함께 부르고 <b>스키마를 지킨 JSON</b> 을 돌려받는다.
     *
     * @param schemaName Structured Outputs 가 요구하는 이름. 영문·숫자·밑줄만 쓴다.
     * @param schema     {@code strict} 모드가 받는 형태여야 한다 ({@link BagCheckPrompt#outputSchema()}).
     */
    public JsonNode complete(String system, String user, List<VisionImage> images,
                             String schemaName, ObjectNode schema) {
        String body = json.write(buildRequest(system, user, images, schemaName, schema));

        String response;
        try {
            response = http.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw new OpenAiException(describe(e), isRetryable(e.getStatusCode().value()), e);
        } catch (RuntimeException e) {
            // 타임아웃·DNS·연결 거부. 네트워크 문제는 다시 걸어 볼 값이 있다.
            throw new OpenAiException("OpenAI 호출이 실패했습니다: " + e.getClass().getSimpleName(), true, e);
        }

        return extractContent(response);
    }

    private ObjectNode buildRequest(String system, String user, List<VisionImage> images,
                                    String schemaName, ObjectNode schema) {
        ObjectNode request = json.newObject();
        request.put("model", model);
        // gpt-4o 계열과 그 이후 모델이 함께 받는 이름이다. max_tokens 는 새 모델에서 거부된다.
        request.put("max_completion_tokens", maxTokens);
        // 07: 구조화된 JSON 출력이므로 낮게 둔다. 온도를 못 받는 모델이면 AI_TEMPERATURE 를
        // 음수로 두어 아예 보내지 않는다 — 그런 모델은 400 으로 거부한다.
        if (temperature >= 0) request.put("temperature", temperature);

        ObjectNode format = request.putObject("response_format");
        format.put("type", "json_schema");
        ObjectNode jsonSchema = format.putObject("json_schema");
        jsonSchema.put("name", schemaName);
        jsonSchema.put("strict", true);
        jsonSchema.set("schema", schema);

        ArrayNode messages = request.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);

        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        ArrayNode parts = userMessage.putArray("content");
        parts.addObject().put("type", "text").put("text", user);
        for (VisionImage image : images) {
            ObjectNode part = parts.addObject();
            part.put("type", "image_url");
            part.putObject("image_url").put("url", image.dataUrl());
        }
        return request;
    }

    /**
     * {@code choices[0].message.content} 를 꺼내 파싱한다.
     *
     * <p>세 가지를 먼저 확인한다. {@code refusal} 이 있으면 모델이 답하기를 거부한 것이고,
     * {@code finish_reason=length} 면 토큰이 모자라 JSON 이 <b>중간에 잘렸다</b> —
     * 그대로 파싱하면 엉뚱한 곳에서 실패하므로 여기서 사유를 밝힌다.
     */
    private JsonNode extractContent(String response) {
        JsonNode root = json.read(response);
        JsonNode choice = root.path("choices").path(0);
        if (choice.isMissingNode()) {
            throw new OpenAiException("OpenAI 응답에 choices 가 없습니다.", true);
        }

        JsonNode refusal = choice.path("message").path("refusal");
        if (refusal.isTextual() && !refusal.asText().isBlank()) {
            // 다시 불러도 같은 사진에는 같은 답을 한다.
            throw new OpenAiException("모델이 응답을 거부했습니다: " + refusal.asText(), false);
        }
        if ("length".equals(choice.path("finish_reason").asText(""))) {
            throw new OpenAiException("응답이 AI_MAX_TOKENS 에서 잘렸습니다. 값을 올리거나 사진 장수를 줄이세요.", false);
        }

        String content = choice.path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new OpenAiException("OpenAI 응답 본문이 비어 있습니다.", true);
        }
        return json.read(content);
    }

    /** 429 와 5xx 만 다시 건다. 401·400 은 설정이 틀린 것이라 다시 불러도 같다. */
    private static boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    private static String describe(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body.length() > ERROR_BODY_LIMIT) body = body.substring(0, ERROR_BODY_LIMIT) + "…";
        return "OpenAI 가 " + e.getStatusCode().value() + " 를 냈습니다: " + body;
    }
}

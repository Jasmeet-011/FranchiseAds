package com.franchiseads.claude;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Calls the Claude Messages API ({@code POST /v1/messages}) on Spring's {@link RestClient} (ADR-008).
 */
public class ClaudeClient {

	static final String API_VERSION = "2026-10-21";

	static final String MESSAGES_PATH = "/v1/messages";

	private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);

	private final ClaudeProperties properties;

	private final RestClient restClient;

	private final JsonMapper json = JsonMapper.shared();

	public ClaudeClient(ClaudeProperties properties) {
		this.properties = properties;
		this.restClient = restClient(properties);
	}

	/**
	 * Sends one single-turn request and returns the model's text. Throws a {@link ClaudeException} subtype when no
	 * usable response arrives; no API key means {@link ClaudeRequestException} before any HTTP call.
	 */
	public ClaudeResponse complete(String system, String user) {
		if (!properties.hasApiKey()) {
			throw new ClaudeRequestException("API key not configured");
		}
		if (log.isDebugEnabled()) {
			log.debug("Claude system prompt:\n{}\nClaude user prompt:\n{}", system, user);
		}
		byte[] body = requestBody(system, user);
		long start = System.nanoTime();
		RawResponse response;
		try {
			response = send(body);
		}
		catch (ResourceAccessException ex) {
			log.warn("Claude call failed after {} ms: {}", elapsedMillis(start), ex.getMessage());
			if (isTimeout(ex)) {
				throw new ClaudeTimeoutException("Claude call timed out: " + ex.getMessage(), ex);
			}
			throw new ClaudeUnavailableException("Claude could not be reached: " + ex.getMessage(), ex);
		}
		log.info("Claude call: HTTP {} in {} ms (request-id {})", response.status(), elapsedMillis(start),
				response.requestId());

		if (response.status() >= 200 && response.status() < 300) {
			try {
				ClaudeResponse result = parseEnvelope(response.body());
				log.info("Claude response: model={}, stop_reason={}, input_tokens={}, output_tokens={}, thinking_blocks={}",
						result.model(), result.stopReason(), result.inputTokens(), result.outputTokens(),
						result.thinkingBlocks());
				return result;
			}
			catch (InvalidEnvelopeException ex) {
				throw new ClaudeUnavailableException("Claude returned an invalid Messages response: " + ex.getMessage()
						+ requestIdSuffix(response.requestId()), ex);
			}
		}
		if (response.status() == 429 || response.status() >= 500) {
			throw new ClaudeUnavailableException("Claude unavailable: " + describeError(response), null);
		}
		throw new ClaudeRequestException("Claude rejected the request: " + describeError(response));
	}

	private static RestClient restClient(ClaudeProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(properties.connectTimeout())
			.version(HttpClient.Version.HTTP_1_1)
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		return RestClient.builder()
			.requestFactory(requestFactory)
			.baseUrl(properties.baseUrl())
			.defaultHeader("anthropic-version", API_VERSION)
			.build();
	}

	private byte[] requestBody(String system, String user) {
		ObjectNode root = json.createObjectNode();
		root.put("model", properties.model());
		root.put("max_tokens", properties.maxTokens());
		root.put("system", system);
		root.putArray("messages").addObject().put("role", "user").put("content", user);
		return json.writeValueAsString(root).getBytes(StandardCharsets.UTF_8);
	}

	private RawResponse send(byte[] body) {
		return restClient.post()
			.uri(MESSAGES_PATH)
			.header("x-api-key", properties.apiKey())
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.exchange((request, response) -> new RawResponse(response.getStatusCode().value(),
					response.getHeaders().getFirst("retry-after"), response.getHeaders().getFirst("request-id"),
					new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
	}

	/** Reads the envelope only. Whatever the model wrote inside it is returned as-is, valid ad copy or not. */
	private ClaudeResponse parseEnvelope(String body) throws InvalidEnvelopeException {
		JsonNode root;
		try {
			root = json.readTree(body);
		}
		catch (JacksonException ex) {
			throw new InvalidEnvelopeException("body is not JSON");
		}
		JsonNode content = root.path("content");
		if (!content.isArray()) {
			throw new InvalidEnvelopeException("no content array");
		}
		StringBuilder text = new StringBuilder();
		int thinkingBlocks = 0;
		for (JsonNode block : content) {
			String type = stringOrNull(block.path("type"));
			if ("text".equals(type)) {
				JsonNode blockText = block.path("text");
				if (!blockText.isString()) {
					throw new InvalidEnvelopeException("text block without a text string");
				}
				text.append(blockText.stringValue());
			}
			else if ("thinking".equals(type) || "redacted_thinking".equals(type)) {
				thinkingBlocks++;
			}
		}
		JsonNode usage = root.path("usage");
		return new ClaudeResponse(text.toString(), stringOrNull(root.path("stop_reason")),
				stringOrNull(root.path("model")), usage.path("input_tokens").asInt(0),
				usage.path("output_tokens").asInt(0), thinkingBlocks);
	}

	/** "HTTP 400 invalid_request_error: max_tokens: ... (request-id req_...)", from the documented error body. */
	private String describeError(RawResponse response) {
		StringBuilder description = new StringBuilder("HTTP ").append(response.status());
		String requestId = response.requestId();
		try {
			JsonNode root = json.readTree(response.body());
			String type = stringOrNull(root.path("error").path("type"));
			String message = stringOrNull(root.path("error").path("message"));
			if (type != null) {
				description.append(' ').append(type);
			}
			if (message != null) {
				description.append(": ").append(message);
			}
			if (requestId == null) {
				requestId = stringOrNull(root.path("request_id"));
			}
		}
		catch (JacksonException ex) {
			// Not the documented error shape (e.g. a proxy's HTML page): the status alone has to do.
		}
		return description.append(requestIdSuffix(requestId)).toString();
	}

	private static String requestIdSuffix(String requestId) {
		return (requestId != null) ? " (request-id " + requestId + ")" : "";
	}

	private static String stringOrNull(JsonNode node) {
		return node.isString() ? node.stringValue() : null;
	}

	/** {@link JdkClientHttpRequestFactory} reports both connect and read timeouts as {@link HttpTimeoutException}. */
	private static boolean isTimeout(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof HttpTimeoutException) {
				return true;
			}
		}
		return false;
	}

	private static long elapsedMillis(long startNanos) {
		return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
	}

	private record RawResponse(int status, String retryAfter, String requestId, String body) {
	}

	private static final class InvalidEnvelopeException extends Exception {

		InvalidEnvelopeException(String message) {
			super(message);
		}

	}

}

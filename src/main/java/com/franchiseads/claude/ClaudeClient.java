package com.franchiseads.claude;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.DoubleSupplier;
import java.util.regex.Pattern;
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
 * Calls the Claude Messages API ({@code POST /v1/messages}) on Spring's {@link RestClient} (ADR-008), with the retry
 * policy from docs/flow.md written out in {@link #complete} (ADR-009, ADR-022).
 * <p>
 * Retries cover transport-level failures only: 429, any 5xx, timeouts, connection errors, and a 200 whose body
 * isn't a Messages response. A valid response whose text isn't valid ad copy is returned normally; the parser and
 * the regeneration step deal with it.
 */
public class ClaudeClient {

	static final String API_VERSION = "2026-10-21";

	static final String MESSAGES_PATH = "/v1/messages";

	private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);

	private static final Pattern RETRY_AFTER_SECONDS = Pattern.compile("\\d{1,9}");

	private final ClaudeProperties properties;

	private final RestClient restClient;

	private final Sleeper sleeper;

	private final DoubleSupplier jitter;

	private final JsonMapper json = JsonMapper.shared();

	/**
	 * @param sleeper waits between attempts
	 * @param jitter returns values in [0, 1); scales the random part of the backoff
	 */
	public ClaudeClient(ClaudeProperties properties, Sleeper sleeper, DoubleSupplier jitter) {
		this.properties = properties;
		this.restClient = restClient(properties);
		this.sleeper = sleeper;
		this.jitter = jitter;
	}

	/**
	 * Sends one single-turn request and returns the model's text, retrying transient failures up to
	 * {@code maxAttempts} times in total. No API key means {@link ClaudeRequestException} before any HTTP call.
	 * @throws ClaudeRequestException non-retryable 4xx, or no API key
	 * @throws ClaudeUnavailableException 429, 5xx, connection error or invalid response on every attempt
	 * @throws ClaudeTimeoutException the last attempt timed out
	 */
	public ClaudeResponse complete(String system, String user) {
		if (!properties.hasApiKey()) {
			throw new ClaudeRequestException("API key not configured");
		}
		if (log.isDebugEnabled()) {
			log.debug("Claude system prompt:\n{}\nClaude user prompt:\n{}", system, user);
		}
		byte[] body = requestBody(system, user);
		int maxAttempts = properties.maxAttempts();
		for (int attempt = 1;; attempt++) {
			AttemptResult result = attempt(body, attempt, maxAttempts);
			if (result.response() != null) {
				return result.response();
			}
			if (attempt >= maxAttempts) {
				throw result.toException(attempt);
			}
			Duration delay = retryDelay(attempt, result.retryAfter());
			log.warn("Claude attempt {}/{} failed ({}); retrying in {} ms", attempt, maxAttempts,
					result.description(), delay.toMillis());
			try {
				sleeper.sleep(delay);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new ClaudeUnavailableException(
						"Interrupted while waiting to retry Claude after: " + result.description(), ex);
			}
		}
	}

	/** One HTTP exchange: a response, a retryable failure, or a thrown {@link ClaudeRequestException}. */
	private AttemptResult attempt(byte[] body, int attempt, int maxAttempts) {
		long start = System.nanoTime();
		RawResponse response;
		try {
			response = send(body);
		}
		catch (ResourceAccessException ex) {
			log.info("Claude attempt {}/{}: no response after {} ms: {}", attempt, maxAttempts, elapsedMillis(start),
					ex.getMessage());
			return isTimeout(ex) ? AttemptResult.timeout(ex.getMessage(), ex)
					: AttemptResult.unavailable(ex.getMessage(), ex, null);
		}
		log.info("Claude attempt {}/{}: HTTP {} in {} ms (request-id {})", attempt, maxAttempts, response.status(),
				elapsedMillis(start), response.requestId());

		int status = response.status();
		if (status >= 200 && status < 300) {
			try {
				ClaudeResponse result = parseEnvelope(response.body());
				log.info("Claude response: model={}, stop_reason={}, input_tokens={}, output_tokens={}, thinking_blocks={}",
						result.model(), result.stopReason(), result.inputTokens(), result.outputTokens(),
						result.thinkingBlocks());
				return AttemptResult.success(result);
			}
			catch (InvalidEnvelopeException ex) {
				return AttemptResult.unavailable("invalid Messages response: " + ex.getMessage()
						+ requestIdSuffix(response.requestId()), ex, null);
			}
		}
		if (status == 429 || status >= 500) {
			return AttemptResult.unavailable(describeError(response), null, response.retryAfter());
		}
		throw new ClaudeRequestException("Claude rejected the request: " + describeError(response));
	}

	/**
	 * A usable {@code retry-after} (whole seconds) wins, capped at {@code retryAfterCap}. Otherwise exponential
	 * backoff from {@code initialBackoff}, plus up to {@code maxJitter} of it at random.
	 */
	private Duration retryDelay(int failedAttempt, String retryAfterHeader) {
		Duration retryAfter = parseRetryAfter(retryAfterHeader);
		if (retryAfter != null) {
			return (retryAfter.compareTo(properties.retryAfterCap()) > 0) ? properties.retryAfterCap() : retryAfter;
		}
		Duration backoff = properties.initialBackoff().multipliedBy(1L << (failedAttempt - 1));
		long jitterMillis = (long) (backoff.toMillis() * properties.maxJitter() * jitter.getAsDouble());
		return backoff.plusMillis(jitterMillis);
	}

	/** Only the delta-seconds form. An HTTP date, a fraction or garbage falls back to the normal backoff. */
	private static Duration parseRetryAfter(String header) {
		if (header == null) {
			return null;
		}
		String value = header.strip();
		if (!RETRY_AFTER_SECONDS.matcher(value).matches()) {
			log.info("Ignoring retry-after header that isn't whole seconds: '{}'", header);
			return null;
		}
		return Duration.ofSeconds(Long.parseLong(value));
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

	/** Either {@code response} is set, or the attempt failed in a way worth retrying. */
	private record AttemptResult(ClaudeResponse response, boolean timedOut, String description, Throwable cause,
			String retryAfter) {

		static AttemptResult success(ClaudeResponse response) {
			return new AttemptResult(response, false, null, null, null);
		}

		static AttemptResult timeout(String description, Throwable cause) {
			return new AttemptResult(null, true, description, cause, null);
		}

		static AttemptResult unavailable(String description, Throwable cause, String retryAfter) {
			return new AttemptResult(null, false, description, cause, retryAfter);
		}

		/** The last attempt decides the type: a final timeout is a timeout, anything else is unavailable. */
		ClaudeException toException(int attempts) {
			String summary = attempts + " attempt" + ((attempts == 1) ? "" : "s") + ": " + description;
			return timedOut ? new ClaudeTimeoutException("Claude timed out after " + summary, cause)
					: new ClaudeUnavailableException("Claude unavailable after " + summary, cause);
		}

	}

	private static final class InvalidEnvelopeException extends Exception {

		InvalidEnvelopeException(String message) {
			super(message);
		}

	}

}

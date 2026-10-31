package com.franchiseads.claude;

import static com.franchiseads.claude.ClaudeFixtures.API_KEY;
import static com.franchiseads.claude.ClaudeFixtures.MODEL;
import static com.franchiseads.claude.ClaudeFixtures.error;
import static com.franchiseads.claude.ClaudeFixtures.message;
import static com.franchiseads.claude.ClaudeFixtures.properties;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.franchiseads.generation.AdCopyParser;
import com.franchiseads.generation.MalformedAdCopyException;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.ResourceAccessException;

class ClaudeClientTests {

	@RegisterExtension
	static WireMockExtension wireMock = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort())
		.build();

	private final RecordingSleeper sleeper = new RecordingSleeper();

	/** No random part unless a test sets one, so delays are exact. */
	private DoubleSupplier jitter = () -> 0.0;

	private ClaudeClient client() {
		return client(properties(wireMock.baseUrl()));
	}

	private ClaudeClient client(ClaudeProperties properties) {
		return new ClaudeClient(properties, sleeper, () -> jitter.getAsDouble());
	}

	// --- successful responses ---

	@Test
	void returnsTextStopReasonModelAndTokensFor200() {
		wireMock.stubFor(post("/v1/messages").willReturn(okJson(message("hello"))));

		ClaudeResponse response = client().complete("system prompt", "user prompt");

		assertThat(response).isEqualTo(new ClaudeResponse("hello", "end_turn", MODEL, 120, 45, 0));
		verifyRequests(1);
		assertThat(sleeper.sleeps()).isEmpty();
	}

	@Test
	void sendsModelMaxTokensSystemAndSingleUserMessage() {
		wireMock.stubFor(post("/v1/messages").willReturn(okJson(message("ok"))));

		client().complete("Be brief.", "Write ads.");

		wireMock.verify(postRequestedFor(urlEqualTo("/v1/messages")).withRequestBody(equalToJson("""
				{"model":"claude-haiku-5-5","max_tokens":1024,"system":"Be brief.",
				 "messages":[{"role":"user","content":"Write ads."}]}
				""")));
		verifyRequests(1);
	}

	@Test
	void sendsRequestBodyAsUtf8() {
		wireMock.stubFor(post("/v1/messages").willReturn(okJson(message("ok"))));

		client().complete("system", "Montréal café ☕");

		wireMock.verify(postRequestedFor(urlEqualTo("/v1/messages"))
			.withRequestBody(equalToJson("{\"messages\":[{\"content\":\"Montréal café ☕\"}]}", false, true)));
		verifyRequests(1);
	}

	@Test
	void concatenatesTextBlocksAndCountsThinkingBlocksWithoutReturningThem() {
		wireMock.stubFor(post("/v1/messages").willReturn(okJson(message("end_turn",
				"{\"type\":\"thinking\",\"thinking\":\"\",\"signature\":\"sig\"}",
				"{\"type\":\"text\",\"text\":\"{\\\"headlines\\\":\"}", "{\"type\":\"redacted_thinking\",\"data\":\"x\"}",
				"{\"type\":\"text\",\"text\":\" []}\"}"))));

		ClaudeResponse response = client().complete("system", "user");

		assertThat(response.text()).isEqualTo("{\"headlines\": []}");
		assertThat(response.thinkingBlocks()).isEqualTo(2);
		verifyRequests(1);
	}

	@Test
	void returnsModelTextThatIsNotAdCopyUnchangedAfterOneRequest() {
		wireMock.stubFor(post("/v1/messages").willReturn(okJson(message("Sure! Here are some ads: not json"))));

		ClaudeResponse response = client().complete("system", "user");

		assertThat(response.text()).isEqualTo("Sure! Here are some ads: not json");
		verifyRequests(1);
		assertThat(sleeper.sleeps()).isEmpty();
	}

	@Test
	void garbageModelTextInAValidEnvelopeIsOneRequestAndMalformedForTheParser() {
		wireMock.stubFor(post("/v1/messages").willReturn(okJson(message("Here are your ads: headline one, two"))));

		ClaudeResponse response = client().complete("system", "user");

		verifyRequests(1);
		assertThat(sleeper.sleeps()).isEmpty();
		assertThatThrownBy(() -> new AdCopyParser().parse(response)).isInstanceOf(MalformedAdCopyException.class);
	}

	// --- non-retryable 4xx ---

	@Test
	void badRequestFailsWithoutRetryAndReportsTheApiError() {
		wireMock.stubFor(post("/v1/messages").willReturn(aResponse().withStatus(400)
			.withHeader("content-type", "application/json")
			.withHeader("request-id", "req_header_01")
			.withBody(error("invalid_request_error", "max_tokens: must be positive"))));

		assertThatThrownBy(() -> client().complete("system", "user")).isInstanceOf(ClaudeRequestException.class)
			.hasMessage("Claude rejected the request: HTTP 400 invalid_request_error: max_tokens: must be positive "
					+ "(request-id req_header_01)");
		verifyRequests(1);
		assertThat(sleeper.sleeps()).isEmpty();
	}

	@Test
	void unauthorizedFailsWithoutRetry() {
		wireMock.stubFor(post("/v1/messages").willReturn(aResponse().withStatus(401)
			.withHeader("content-type", "application/json")
			.withBody(error("authentication_error", "invalid x-api-key"))));

		assertThatThrownBy(() -> client().complete("system", "user")).isInstanceOf(ClaudeRequestException.class)
			.hasMessage("Claude rejected the request: HTTP 401 authentication_error: invalid x-api-key "
					+ "(request-id req_body_01)")
			.hasMessageNotContaining(API_KEY);
		verifyRequests(1);
		assertThat(sleeper.sleeps()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(ints = { 402, 403, 404, 408, 409, 413 })
	void otherClientErrorsAreNeverRetried(int status) {
		wireMock.stubFor(post("/v1/messages").willReturn(aResponse().withStatus(status)));

		assertThatThrownBy(() -> client().complete("system", "user")).isInstanceOf(ClaudeRequestException.class)
			.hasMessage("Claude rejected the request: HTTP " + status);
		verifyRequests(1);
		assertThat(sleeper.sleeps()).isEmpty();
	}

	@Test
	void blankApiKeyFailsBeforeAnyHttpCall() {
		ClaudeClient client = client(properties(wireMock.baseUrl(), "  "));

		assertThatThrownBy(() -> client.complete("system", "user")).isInstanceOf(ClaudeRequestException.class)
			.hasMessage("API key not configured");
		wireMock.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void missingApiKeyFailsBeforeAnyHttpCall() {
		ClaudeClient client = client(properties(wireMock.baseUrl(), null));

		assertThatThrownBy(() -> client.complete("system", "user")).isInstanceOf(ClaudeRequestException.class)
			.hasMessage("API key not configured");
		wireMock.verify(0, anyRequestedFor(anyUrl()));
	}

	// --- retries ---

	@Test
	void tooManyRequestsThenSuccessRetriesOnceAfterBackoff() {
		stubSequence(status(429, error("rate_limit_error", "Number of requests has exceeded your rate limit")),
				okJson(message("hello")));

		assertThat(client().complete("system", "user").text()).isEqualTo("hello");
		verifyRequests(2);
		assertThat(sleeper.sleeps()).containsExactly(Duration.ofMillis(500));
	}

	@Test
	void honorsRetryAfterSecondsOn429() {
		stubSequence(status(429, error("rate_limit_error", "slow down")).withHeader("retry-after", "3"),
				okJson(message("hello")));

		client().complete("system", "user");

		verifyRequests(2);
		assertThat(sleeper.sleeps()).containsExactly(Duration.ofSeconds(3));
	}

	@Test
	void capsRetryAfterAtTenSeconds() {
		stubSequence(status(429, error("rate_limit_error", "slow down")).withHeader("retry-after", "60"),
				okJson(message("hello")));

		client().complete("system", "user");

		verifyRequests(2);
		assertThat(sleeper.sleeps()).containsExactly(Duration.ofSeconds(10));
	}

	@ParameterizedTest
	@ValueSource(strings = { "soon", "1.5", "-1", "Wed, 21 Oct 2026 07:28:00 GMT", "" })
	void unparseableRetryAfterFallsBackToNormalBackoff(String retryAfter) {
		stubSequence(status(429, error("rate_limit_error", "slow down")).withHeader("retry-after", retryAfter),
				okJson(message("hello")));

		client().complete("system", "user");

		verifyRequests(2);
		assertThat(sleeper.sleeps()).containsExactly(Duration.ofMillis(500));
	}

	@Test
	void honorsRetryAfterOnServerErrorsToo() {
		stubSequence(status(503, error("api_error", "unavailable")).withHeader("retry-after", "2"),
				okJson(message("hello")));

		client().complete("system", "user");

		verifyRequests(2);
		assertThat(sleeper.sleeps()).containsExactly(Duration.ofSeconds(2));
	}

	@Test
	void serverErrorThreeTimesGivesUpAfterThreeAttempts() {
		wireMock.stubFor(post("/v1/messages").willReturn(status(500, error("api_error", "Internal server error"))));

		assertThatThrownBy(() -> client().complete("system", "user")).isInstanceOf(ClaudeUnavailableException.class)
			.hasMessage("Claude unavailable after 3 attempts: HTTP 500 api_error: Internal server error "
					+ "(request-id req_body_01)");
		verifyRequests(3);
		assertThat(sleeper.sleeps()).containsExactly(Duration.ofMillis(500), Duration.ofSeconds(1));
	}

	@Test
	void overloadedThenSuccessRetriesOnce() {
		stubSequence(status(529, error("overloaded_error", "Overloaded")), okJson(message("hello")));

		assertThat(client().complete("system", "user").text()).isEqualTo("hello");
		verifyRequests(2);
		assertThat(sleeper.sleeps()).containsExactly(Duration.ofMillis(500));
	}

	@ParameterizedTest
	@ValueSource(ints = { 501, 502, 504, 505 })
	void everyServerErrorIsRetried(int status) {
		stubSequence(aResponse().withStatus(status), okJson(message("hello")));

		assertThat(client().complete("system", "user").text()).isEqualTo("hello");
		verifyRequests(2);
	}

	@Test
	void addsJitterOfUpToAQuarterOfTheBackoff() {
		jitter = () -> 0.5;
		wireMock.stubFor(post("/v1/messages").willReturn(aResponse().withStatus(500)));

		assertThatThrownBy(() -> client().complete("system", "user")).isInstanceOf(ClaudeUnavailableException.class);
		// 500ms + 500 * 0.25 * 0.5 (62.5, truncated); 1000ms + 1000 * 0.25 * 0.5
		assertThat(sleeper.sleeps()).containsExactly(Duration.ofMillis(562), Duration.ofMillis(1125));
	}

	@Test
	void respectsConfiguredMaxAttempts() {
		wireMock.stubFor(post("/v1/messages").willReturn(aResponse().withStatus(500)));
		ClaudeClient client = client(properties(wireMock.baseUrl(), API_KEY, Duration.ofSeconds(2), 1));

		assertThatThrownBy(() -> client.complete("system", "user")).isInstanceOf(ClaudeUnavailableException.class)
			.hasMessage("Claude unavailable after 1 attempt: HTTP 500");
		verifyRequests(1);
		assertThat(sleeper.sleeps()).isEmpty();
	}

	// --- envelope vs. model output ---

	@Test
	void retriesInvalidEnvelopeThenSucceeds() {
		stubSequence(aResponse().withStatus(200).withBody("<html>gateway</html>"),
				okJson("{\"type\":\"message\",\"stop_reason\":\"end_turn\"}"), okJson(message("hello")));

		assertThat(client().complete("system", "user").text()).isEqualTo("hello");
		verifyRequests(3);
		assertThat(sleeper.sleeps()).containsExactly(Duration.ofMillis(500), Duration.ofSeconds(1));
	}

	@Test
	void invalidEnvelopeOnEveryAttemptIsUnavailable() {
		wireMock.stubFor(post("/v1/messages")
			.willReturn(okJson("{\"content\":[{\"type\":\"text\",\"text\":42}]}").withHeader("request-id", "req_7")));

		assertThatThrownBy(() -> client().complete("system", "user")).isInstanceOf(ClaudeUnavailableException.class)
			.hasMessage("Claude unavailable after 3 attempts: invalid Messages response: "
					+ "text block without a text string (request-id req_7)");
		verifyRequests(3);
	}

	// --- timeouts and connection errors ---

	@Test
	void readTimeoutOnEveryAttemptIsClaudeTimeout() {
		wireMock.stubFor(post("/v1/messages").willReturn(okJson(message("late")).withFixedDelay(1000)));
		ClaudeClient client = client(properties(wireMock.baseUrl(), API_KEY, Duration.ofMillis(100), 3));

		assertThatThrownBy(() -> client.complete("system", "user")).isInstanceOf(ClaudeTimeoutException.class)
			.hasMessageStartingWith("Claude timed out after 3 attempts: ")
			.cause()
			.isInstanceOf(ResourceAccessException.class)
			.cause()
			.isInstanceOf(HttpTimeoutException.class);
		verifyRequests(3);
		assertThat(sleeper.sleeps()).containsExactly(Duration.ofMillis(500), Duration.ofSeconds(1));
	}

	@Test
	void readTimeoutThenSuccessRecovers() {
		stubSequence(okJson(message("late")).withFixedDelay(1000), okJson(message("hello")));
		ClaudeClient client = client(properties(wireMock.baseUrl(), API_KEY, Duration.ofMillis(100), 3));

		assertThat(client.complete("system", "user").text()).isEqualTo("hello");
		verifyRequests(2);
		assertThat(sleeper.sleeps()).containsExactly(Duration.ofMillis(500));
	}

	@Test
	void connectionRefusedIsRetriedThenUnavailable() throws IOException {
		ClaudeClient client = client(properties("http://localhost:" + unusedPort()));

		assertThatThrownBy(() -> client.complete("system", "user")).isInstanceOf(ClaudeUnavailableException.class)
			.hasMessageStartingWith("Claude unavailable after 3 attempts: ")
			.cause()
			.isInstanceOf(ResourceAccessException.class);
		assertThat(sleeper.sleeps()).containsExactly(Duration.ofMillis(500), Duration.ofSeconds(1));
	}

	@Test
	void interruptedWhileWaitingStopsAndKeepsTheInterruptFlag() {
		wireMock.stubFor(post("/v1/messages").willReturn(aResponse().withStatus(500)));
		ClaudeClient client = new ClaudeClient(properties(wireMock.baseUrl()), delay -> {
			throw new InterruptedException();
		}, () -> 0.0);

		try {
			assertThatThrownBy(() -> client.complete("system", "user"))
				.isInstanceOf(ClaudeUnavailableException.class)
				.hasMessage("Interrupted while waiting to retry Claude after: HTTP 500")
				.hasCauseInstanceOf(InterruptedException.class);
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
		}
		finally {
			Thread.interrupted();
		}
		verifyRequests(1);
	}

	// --- helpers ---

	private static ResponseDefinitionBuilder status(int status, String body) {
		return aResponse().withStatus(status).withHeader("content-type", "application/json").withBody(body);
	}

	/** The n-th request gets the n-th response; the last one repeats. */
	private static void stubSequence(ResponseDefinitionBuilder... responses) {
		for (int i = 0; i < responses.length; i++) {
			wireMock.stubFor(post("/v1/messages").inScenario("sequence")
				.whenScenarioStateIs((i == 0) ? STARTED : "response-" + i)
				.willReturn(responses[i])
				.willSetStateTo("response-" + Math.min(i + 1, responses.length - 1)));
		}
	}

	private static int unusedPort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	/** Exactly {@code count} requests reached WireMock, and every one carried the required headers. */
	private static void verifyRequests(int count) {
		wireMock.verify(count, anyRequestedFor(anyUrl()));
		wireMock.verify(count,
				postRequestedFor(urlEqualTo("/v1/messages")).withHeader("x-api-key", equalTo(API_KEY))
					.withHeader("anthropic-version", equalTo("2026-10-21"))
					.withHeader("content-type", containing("application/json")));
	}

}

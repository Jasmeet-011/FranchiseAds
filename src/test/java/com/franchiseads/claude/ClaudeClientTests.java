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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ClaudeClientTests {

	@RegisterExtension
	static WireMockExtension wireMock = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort())
		.build();

	private ClaudeClient client() {
		return new ClaudeClient(properties(wireMock.baseUrl()));
	}

	@Test
	void returnsTextStopReasonModelAndTokensFor200() {
		wireMock.stubFor(post("/v1/messages").willReturn(okJson(message("hello"))));

		ClaudeResponse response = client().complete("system prompt", "user prompt");

		assertThat(response).isEqualTo(new ClaudeResponse("hello", "end_turn", MODEL, 120, 45, 0));
		verifyRequests(1);
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
	}

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
	}

	@Test
	void blankApiKeyFailsBeforeAnyHttpCall() {
		ClaudeClient client = new ClaudeClient(properties(wireMock.baseUrl(), "  "));

		assertThatThrownBy(() -> client.complete("system", "user")).isInstanceOf(ClaudeRequestException.class)
			.hasMessage("API key not configured");
		wireMock.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void missingApiKeyFailsBeforeAnyHttpCall() {
		ClaudeClient client = new ClaudeClient(properties(wireMock.baseUrl(), null));

		assertThatThrownBy(() -> client.complete("system", "user")).isInstanceOf(ClaudeRequestException.class)
			.hasMessage("API key not configured");
		wireMock.verify(0, anyRequestedFor(anyUrl()));
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

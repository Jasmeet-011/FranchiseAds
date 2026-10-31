package com.franchiseads;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.franchiseads.brand.dto.BrandResponse;
import com.franchiseads.campaign.dto.CampaignResponse;
import com.franchiseads.location.dto.LocationResponse;
import com.franchiseads.variant.VariantStatus;
import com.franchiseads.variant.dto.VariantResponse;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * The whole system over HTTP: Postgres in a container, and WireMock standing in for the Claude API. Nothing is mocked
 * inside the application, so the real {@code ClaudeClient}, parser, validator, persistence and approval workflow run
 * together. Only the base URL and the API key point the client somewhere else.
 */
@Import(TestcontainersConfiguration.class)
@ExtendWith(TruncateTablesExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ApprovalWorkflowEndToEndTests {

	private static final String API_KEY = "e2e-test-key-not-real";

	private static final String MODEL = "claude-haiku-5-5";

	private static final String MESSAGES = "/v1/messages";

	private static final String REGENERATION_MARKER = "Your previous response for this brief could not be used.";

	private static final String VALID = """
			{"headlines": ["Acme Fitness Austin", "First Month Free", "Join Today", "Classes Every Morning",
			  "Friendly Coaches"],
			 "descriptions": ["Strength, cardio and yoga in downtown Austin.", "Your first month is on us."]}""";

	private static final String CHEAP = VALID.replace("Friendly Coaches", "Cheap Friendly Coaches");

	@RegisterExtension
	static WireMockExtension claudeApi = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort())
		.build();

	/** Every Claude property the assertions rely on is set here, so a local {@code .env} can't change the result. */
	@DynamicPropertySource
	static void pointClaudeClientAtWireMock(DynamicPropertyRegistry registry) {
		registry.add("franchiseads.claude.base-url", claudeApi::baseUrl);
		registry.add("franchiseads.claude.api-key", () -> API_KEY);
		registry.add("franchiseads.claude.model", () -> MODEL);
		registry.add("franchiseads.claude.effort", () -> "low");
		registry.add("franchiseads.claude.max-attempts", () -> 3);
	}

	@Autowired
	private RestTestClient client;

	@Test
	void generatedVariantIsSubmittedAndApproved() {
		Long brandId = createBrand("[]");
		Long locationId = createLocation(brandId);
		Long campaignId = createCampaign(brandId);
		claudeApiAnswers(VALID);

		VariantResponse generated = generate(campaignId, locationId);
		assertThat(generated.status()).isEqualTo(VariantStatus.DRAFT);
		assertThat(generated.validationPassed()).isTrue();
		assertThat(generated.violations()).isEmpty();
		assertThat(generated.headlines()).containsExactly("Acme Fitness Austin", "First Month Free", "Join Today",
				"Classes Every Morning", "Friendly Coaches");
		assertThat(generated.generationAttempts()).isEqualTo(1);
		assertThat(generated.model()).isEqualTo(MODEL);

		VariantResponse submitted = act(generated.id(), "submit");
		assertThat(submitted.status()).isEqualTo(VariantStatus.PENDING_REVIEW);

		VariantResponse approved = act(generated.id(), "approve");
		assertThat(approved.status()).isEqualTo(VariantStatus.APPROVED);
		assertThat(approved.rejectionReason()).isNull();
		assertThat(approved.updatedAt()).isAfter(approved.createdAt());

		assertThat(fetch(generated.id())).isEqualTo(approved);
		client.get()
			.uri("/campaigns/{id}/variants?status=APPROVED", campaignId)
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.length()").isEqualTo(1)
			.jsonPath("$[0].id").isEqualTo(generated.id());

		claudeApi.verify(1, postRequestedFor(urlEqualTo(MESSAGES)).withHeader("x-api-key", equalTo(API_KEY))
			.withHeader("anthropic-version", equalTo("2026-10-21"))
			.withRequestBody(containing("First month free")));
	}

	@Test
	void copyViolatingARuleTwiceIsSavedFlaggedAndCannotBeSubmitted() {
		Long brandId = createBrand("[\"cheap\"]");
		Long locationId = createLocation(brandId);
		Long campaignId = createCampaign(brandId);
		claudeApiAnswers(CHEAP);

		VariantResponse generated = generate(campaignId, locationId);
		assertThat(generated.status()).isEqualTo(VariantStatus.DRAFT);
		assertThat(generated.validationPassed()).isFalse();
		assertThat(generated.violations())
			.containsExactly("BANNED_WORD: headlines[4] contains banned word \"cheap\": \"Cheap Friendly Coaches\"");
		assertThat(generated.generationAttempts()).isEqualTo(2);

		List<LoggedRequest> requests = claudeApi.findAll(postRequestedFor(urlEqualTo(MESSAGES)));
		assertThat(requests).hasSize(2);
		assertThat(requests.get(0).getBodyAsString()).doesNotContain(REGENERATION_MARKER);
		assertThat(requests.get(1).getBodyAsString()).contains(REGENERATION_MARKER)
			.contains("BANNED_WORD: headlines[4] contains banned word");

		client.post()
			.uri("/variants/{id}/submit", generated.id())
			.exchange()
			.expectStatus().isEqualTo(409)
			.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody()
			.jsonPath("$.detail").isEqualTo("Can't submit a variant that failed validation: flagged variants can't "
					+ "enter review. Generate a new variant for this location instead.")
			.jsonPath("$.currentStatus").isEqualTo("DRAFT")
			.jsonPath("$.attemptedAction").isEqualTo("SUBMIT");

		assertThat(fetch(generated.id()).status()).isEqualTo(VariantStatus.DRAFT);
	}

	@Test
	void rejectedVariantCannotBeApproved() {
		Long brandId = createBrand("[]");
		Long locationId = createLocation(brandId);
		Long campaignId = createCampaign(brandId);
		claudeApiAnswers(VALID);
		Long variantId = generate(campaignId, locationId).id();
		act(variantId, "submit");

		VariantResponse rejected = client.post()
			.uri("/variants/{id}/reject", variantId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"reason\": \"  Off-brand tone  \"}")
			.exchange()
			.expectStatus().isOk()
			.returnResult(VariantResponse.class)
			.getResponseBody();
		assertThat(rejected.status()).isEqualTo(VariantStatus.REJECTED);
		assertThat(rejected.rejectionReason()).isEqualTo("Off-brand tone");

		client.post()
			.uri("/variants/{id}/approve", variantId)
			.exchange()
			.expectStatus().isEqualTo(409)
			.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody()
			.jsonPath("$.detail").isEqualTo("Can't approve a variant in status REJECTED: APPROVED and REJECTED are "
					+ "final. Generate a new variant for this location instead.")
			.jsonPath("$.currentStatus").isEqualTo("REJECTED")
			.jsonPath("$.attemptedAction").isEqualTo("APPROVE");

		VariantResponse stored = fetch(variantId);
		assertThat(stored.status()).isEqualTo(VariantStatus.REJECTED);
		assertThat(stored.rejectionReason()).isEqualTo("Off-brand tone");
	}

	/** Every call to the Messages API gets a successful response whose text is {@code adCopy}. */
	private static void claudeApiAnswers(String adCopy) {
		claudeApi.stubFor(post(urlEqualTo(MESSAGES)).willReturn(okJson(messagesResponse(adCopy))));
	}

	/** A Messages API response shaped like the documented one, with the ad copy JSON-escaped into its text block. */
	private static String messagesResponse(String text) {
		return JsonMapper.shared()
			.writeValueAsString(Map.of("id", "msg_e2e", "type", "message", "role", "assistant", "model", MODEL,
					"content", List.of(Map.of("type", "text", "text", text)), "stop_reason", "end_turn", "usage",
					Map.of("input_tokens", 600, "output_tokens", 130)));
	}

	private VariantResponse generate(Long campaignId, Long locationId) {
		return client.post()
			.uri("/campaigns/{id}/generate", campaignId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"locationId\": " + locationId + "}")
			.exchange()
			.expectStatus().isCreated()
			.returnResult(VariantResponse.class)
			.getResponseBody();
	}

	private VariantResponse act(Long variantId, String action) {
		return client.post()
			.uri("/variants/{id}/{action}", variantId, action)
			.exchange()
			.expectStatus().isOk()
			.returnResult(VariantResponse.class)
			.getResponseBody();
	}

	private VariantResponse fetch(Long variantId) {
		return client.get()
			.uri("/variants/{id}", variantId)
			.exchange()
			.expectStatus().isOk()
			.returnResult(VariantResponse.class)
			.getResponseBody();
	}

	private Long createBrand(String bannedWordsJson) {
		return client.post()
			.uri("/brands")
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"name\": \"Acme Fitness\", \"bannedWords\": " + bannedWordsJson + "}")
			.exchange()
			.expectStatus().isCreated()
			.returnResult(BrandResponse.class)
			.getResponseBody()
			.id();
	}

	private Long createLocation(Long brandId) {
		return client.post()
			.uri("/brands/{brandId}/locations", brandId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"name\": \"Downtown\", \"city\": \"Austin\", \"localOffer\": \"First month free\"}")
			.exchange()
			.expectStatus().isCreated()
			.returnResult(LocationResponse.class)
			.getResponseBody()
			.id();
	}

	private Long createCampaign(Long brandId) {
		return client.post()
			.uri("/brands/{brandId}/campaigns", brandId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"name\": \"Spring\", \"theme\": \"New year, new routine\"}")
			.exchange()
			.expectStatus().isCreated()
			.returnResult(CampaignResponse.class)
			.getResponseBody()
			.id();
	}

}

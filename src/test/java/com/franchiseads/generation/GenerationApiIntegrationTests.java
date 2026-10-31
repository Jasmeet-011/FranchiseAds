package com.franchiseads.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.franchiseads.TestcontainersConfiguration;
import com.franchiseads.brand.dto.BrandResponse;
import com.franchiseads.campaign.dto.CampaignResponse;
import com.franchiseads.claude.ClaudeClient;
import com.franchiseads.claude.ClaudeResponse;
import com.franchiseads.location.dto.LocationResponse;
import com.franchiseads.variant.VariantStatus;
import com.franchiseads.variant.dto.VariantResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/** The generate and variant endpoints over HTTP against Postgres, with only the Claude client mocked. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class GenerationApiIntegrationTests {

	private static final String VALID = """
			{"headlines": ["Acme Fitness Austin", "First Month Free", "Join Today", "Classes Every Morning",
			  "Friendly Coaches"],
			 "descriptions": ["Strength, cardio and yoga in downtown Austin.", "Your first month is on us."]}""";

	private static final String CHEAP = VALID.replace("Friendly Coaches", "Cheap Friendly Coaches");

	@Autowired
	private RestTestClient client;

	@MockitoBean
	private ClaudeClient claude;

	@Test
	void generatedVariantIsSavedAndReadableByIdAndByStatus() {
		Long brandId = createBrand("[]");
		Long locationId = createLocation(brandId);
		Long campaignId = createCampaign(brandId);
		answers(VALID);

		VariantResponse created = client.post()
			.uri("/campaigns/{id}/generate", campaignId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"locationId\": " + locationId + "}")
			.exchange()
			.expectStatus().isCreated()
			.expectHeader().valueMatches("Location", "/variants/\\d+")
			.returnResult(VariantResponse.class)
			.getResponseBody();

		assertThat(created.campaignId()).isEqualTo(campaignId);
		assertThat(created.locationId()).isEqualTo(locationId);
		assertThat(created.status()).isEqualTo(VariantStatus.DRAFT);
		assertThat(created.validationPassed()).isTrue();
		assertThat(created.violations()).isEmpty();
		assertThat(created.headlines()).hasSize(5).contains("Join Today");
		assertThat(created.generationAttempts()).isEqualTo(1);
		assertThat(created.model()).isEqualTo("claude-haiku-5-5");

		VariantResponse fetched = client.get()
			.uri("/variants/{id}", created.id())
			.exchange()
			.expectStatus().isOk()
			.returnResult(VariantResponse.class)
			.getResponseBody();
		assertThat(fetched).isEqualTo(created);

		client.get()
			.uri("/campaigns/{id}/variants?status=DRAFT", campaignId)
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.length()").isEqualTo(1)
			.jsonPath("$[0].id").isEqualTo(created.id());
		client.get()
			.uri("/campaigns/{id}/variants?status=APPROVED", campaignId)
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.length()").isEqualTo(0);
	}

	@Test
	void flaggedVariantIsStoredWithEveryViolation() {
		Long brandId = createBrand("[\"cheap\"]");
		Long locationId = createLocation(brandId);
		Long campaignId = createCampaign(brandId);
		answers(CHEAP, CHEAP);

		VariantResponse created = generate(campaignId, locationId);

		String violation = "BANNED_WORD: headlines[4] contains banned word \"cheap\": \"Cheap Friendly Coaches\"";
		assertThat(created.validationPassed()).isFalse();
		assertThat(created.violations()).containsExactly(violation);
		assertThat(created.generationAttempts()).isEqualTo(2);

		client.get()
			.uri("/campaigns/{id}/variants", campaignId)
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.length()").isEqualTo(1)
			.jsonPath("$[0].validationPassed").isEqualTo(false)
			.jsonPath("$[0].violations[0]").isEqualTo(violation)
			.jsonPath("$[0].generationAttempts").isEqualTo(2);
	}

	@Test
	void locationOfAnotherBrandIs422AndNothingIsSaved() {
		Long brandId = createBrand("[]");
		Long campaignId = createCampaign(brandId);
		Long otherLocationId = createLocation(createBrand("[]"));

		client.post()
			.uri("/campaigns/{id}/generate", campaignId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"locationId\": " + otherLocationId + "}")
			.exchange()
			.expectStatus().isEqualTo(422)
			.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody()
			.jsonPath("$.detail")
			.isEqualTo("Location " + otherLocationId + " does not belong to the brand of campaign " + campaignId);

		assertNoVariants(campaignId);
	}

	@Test
	void malformedTwiceIs502AndNothingIsSaved() {
		Long brandId = createBrand("[]");
		Long locationId = createLocation(brandId);
		Long campaignId = createCampaign(brandId);
		answers("not json", "still not json");

		client.post()
			.uri("/campaigns/{id}/generate", campaignId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"locationId\": " + locationId + "}")
			.exchange()
			.expectStatus().isEqualTo(502)
			.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody()
			.jsonPath("$.detail").isEqualTo("The AI model did not return usable ad copy. Try again.");

		assertNoVariants(campaignId);
	}

	@Test
	void variantEndpointsReturn404And400AsProblemDetails() {
		client.get().uri("/variants/{id}", Long.MAX_VALUE).exchange().expectStatus().isNotFound();
		client.get().uri("/campaigns/{id}/variants", Long.MAX_VALUE).exchange().expectStatus().isNotFound();
		client.get()
			.uri("/campaigns/{id}/variants?status=PUBLISHED", createCampaign(createBrand("[]")))
			.exchange()
			.expectStatus().isBadRequest()
			.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	private void answers(String first, String... rest) {
		ClaudeResponse[] more = new ClaudeResponse[rest.length];
		for (int i = 0; i < rest.length; i++) {
			more[i] = response(rest[i]);
		}
		when(claude.complete(anyString(), anyString(), any())).thenReturn(response(first), more);
	}

	private static ClaudeResponse response(String text) {
		return new ClaudeResponse(text, "end_turn", "claude-haiku-5-5", 600, 130, 0, 0);
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

	private void assertNoVariants(Long campaignId) {
		client.get()
			.uri("/campaigns/{id}/variants", campaignId)
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.length()").isEqualTo(0);
	}

	private Long createBrand(String bannedWordsJson) {
		return client.post()
			.uri("/brands")
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"name\": \"Generation Test Brand " + UUID.randomUUID() + "\", \"bannedWords\": " + bannedWordsJson
					+ "}")
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

package com.franchiseads.brand;

import static org.assertj.core.api.Assertions.assertThat;

import com.franchiseads.TestcontainersConfiguration;
import com.franchiseads.TruncateTablesExtension;
import com.franchiseads.brand.dto.BrandResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Full stack over HTTP against Testcontainers Postgres. The tables are truncated before each test
 * ({@link TruncateTablesExtension}), so no test sees another's rows.
 */
@Import(TestcontainersConfiguration.class)
@ExtendWith(TruncateTablesExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class BrandApiIntegrationTests {

	@Autowired
	private RestTestClient client;

	@Test
	void createdBrandIsReturnedByGetWithIdenticalBody() {
		BrandResponse created = client.post()
			.uri("/brands")
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"name": " Round Trip Gym ", "voiceGuidelines": " Friendly ", "bannedWords": ["Cheap", "best in town"]}
					""")
			.exchange()
			.expectStatus().isCreated()
			.expectHeader().valueMatches("Location", "/brands/\\d+")
			.returnResult(BrandResponse.class)
			.getResponseBody();

		assertThat(created.name()).isEqualTo("Round Trip Gym");
		assertThat(created.voiceGuidelines()).isEqualTo("Friendly");
		assertThat(created.createdAt()).isNotNull();

		BrandResponse fetched = client.get()
			.uri("/brands/{id}", created.id())
			.exchange()
			.expectStatus().isOk()
			.returnResult(BrandResponse.class)
			.getResponseBody();
		assertThat(fetched).isEqualTo(created);
	}

	@Test
	void bannedWordsDifferingOnlyInCaseAreStoredOnce() {
		BrandResponse created = client.post()
			.uri("/brands")
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"name": "Dedupe Gym", "bannedWords": ["Cheap", "cheap", " CHEAP ", "free"]}
					""")
			.exchange()
			.expectStatus().isCreated()
			.returnResult(BrandResponse.class)
			.getResponseBody();

		client.get()
			.uri("/brands/{id}", created.id())
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.bannedWords.length()").isEqualTo(2)
			.jsonPath("$.bannedWords[0]").isEqualTo("Cheap")
			.jsonPath("$.bannedWords[1]").isEqualTo("free");
	}

	@Test
	void brandNamesDifferingOnlyInCaseConflict() {
		client.post()
			.uri("/brands")
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"name\": \"Acme\"}")
			.exchange()
			.expectStatus().isCreated();

		client.post()
			.uri("/brands")
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"name\": \"acme\"}")
			.exchange()
			.expectStatus().isEqualTo(409)
			.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody()
			.jsonPath("$.status").isEqualTo(409)
			.jsonPath("$.detail").isEqualTo("A brand with this name already exists (names are compared ignoring case).")
			.jsonPath("$.instance").isEqualTo("/brands");
	}

	@Test
	void unknownBrandIs404ProblemDetail() {
		client.get()
			.uri("/brands/{id}", Long.MAX_VALUE)
			.exchange()
			.expectStatus().isNotFound()
			.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody()
			.jsonPath("$.detail").isEqualTo("Brand " + Long.MAX_VALUE + " not found");
	}

}

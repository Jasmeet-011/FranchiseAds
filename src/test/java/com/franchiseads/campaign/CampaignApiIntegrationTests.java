package com.franchiseads.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import com.franchiseads.TestcontainersConfiguration;
import com.franchiseads.TruncateTablesExtension;
import com.franchiseads.brand.dto.BrandResponse;
import com.franchiseads.campaign.dto.CampaignResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@Import(TestcontainersConfiguration.class)
@ExtendWith(TruncateTablesExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class CampaignApiIntegrationTests {

	@Autowired
	private RestTestClient client;

	@Test
	void createdCampaignIsReturnedByGetAndListedUnderItsBrandOnly() {
		Long brandId = createBrand();
		Long otherBrandId = createBrand();
		CampaignResponse spring = createCampaign(brandId, "Spring");
		CampaignResponse summer = createCampaign(brandId, "Summer");
		createCampaign(otherBrandId, "Elsewhere");

		CampaignResponse fetched = client.get()
			.uri("/campaigns/{id}", spring.id())
			.exchange()
			.expectStatus().isOk()
			.returnResult(CampaignResponse.class)
			.getResponseBody();
		assertThat(fetched).isEqualTo(spring);
		assertThat(fetched.brandId()).isEqualTo(brandId);

		client.get()
			.uri("/brands/{brandId}/campaigns", brandId)
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.length()").isEqualTo(2)
			.jsonPath("$[0].id").isEqualTo(spring.id())
			.jsonPath("$[1].id").isEqualTo(summer.id());
	}

	@Test
	void createUnderUnknownBrandIs404() {
		client.post()
			.uri("/brands/{brandId}/campaigns", Long.MAX_VALUE)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"name\": \"Spring\", \"theme\": \"New season\"}")
			.exchange()
			.expectStatus().isNotFound()
			.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody()
			.jsonPath("$.detail").isEqualTo("Brand " + Long.MAX_VALUE + " not found");
	}

	@Test
	void listUnderUnknownBrandIs404() {
		client.get()
			.uri("/brands/{brandId}/campaigns", Long.MAX_VALUE)
			.exchange()
			.expectStatus().isNotFound()
			.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	private Long createBrand() {
		return client.post()
			.uri("/brands")
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"name\": \"Campaign Test Brand " + UUID.randomUUID() + "\"}")
			.exchange()
			.expectStatus().isCreated()
			.returnResult(BrandResponse.class)
			.getResponseBody()
			.id();
	}

	private CampaignResponse createCampaign(Long brandId, String name) {
		return client.post()
			.uri("/brands/{brandId}/campaigns", brandId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"name\": \"" + name + "\", \"theme\": \"Get moving this season\"}")
			.exchange()
			.expectStatus().isCreated()
			.returnResult(CampaignResponse.class)
			.getResponseBody();
	}

}

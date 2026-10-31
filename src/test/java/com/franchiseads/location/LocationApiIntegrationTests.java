package com.franchiseads.location;

import static org.assertj.core.api.Assertions.assertThat;

import com.franchiseads.TestcontainersConfiguration;
import com.franchiseads.TruncateTablesExtension;
import com.franchiseads.brand.dto.BrandResponse;
import com.franchiseads.location.dto.LocationResponse;
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
class LocationApiIntegrationTests {

	@Autowired
	private RestTestClient client;

	@Test
	void createdLocationIsReturnedByGetAndListedUnderItsBrandOnly() {
		Long brandId = createBrand();
		Long otherBrandId = createBrand();
		LocationResponse downtown = createLocation(brandId, "Downtown");
		LocationResponse uptown = createLocation(brandId, "Uptown");
		createLocation(otherBrandId, "Elsewhere");

		LocationResponse fetched = client.get()
			.uri("/locations/{id}", downtown.id())
			.exchange()
			.expectStatus().isOk()
			.returnResult(LocationResponse.class)
			.getResponseBody();
		assertThat(fetched).isEqualTo(downtown);
		assertThat(fetched.brandId()).isEqualTo(brandId);

		client.get()
			.uri("/brands/{brandId}/locations", brandId)
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.length()").isEqualTo(2)
			.jsonPath("$[0].id").isEqualTo(downtown.id())
			.jsonPath("$[1].id").isEqualTo(uptown.id());
	}

	@Test
	void createUnderUnknownBrandIs404() {
		client.post()
			.uri("/brands/{brandId}/locations", Long.MAX_VALUE)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"name\": \"Downtown\", \"city\": \"Austin\"}")
			.exchange()
			.expectStatus().isNotFound()
			.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody()
			.jsonPath("$.detail").isEqualTo("Brand " + Long.MAX_VALUE + " not found");
	}

	@Test
	void listUnderUnknownBrandIs404() {
		client.get()
			.uri("/brands/{brandId}/locations", Long.MAX_VALUE)
			.exchange()
			.expectStatus().isNotFound()
			.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	private Long createBrand() {
		return client.post()
			.uri("/brands")
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"name\": \"Location Test Brand " + UUID.randomUUID() + "\"}")
			.exchange()
			.expectStatus().isCreated()
			.returnResult(BrandResponse.class)
			.getResponseBody()
			.id();
	}

	private LocationResponse createLocation(Long brandId, String name) {
		return client.post()
			.uri("/brands/{brandId}/locations", brandId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"name\": \"" + name + "\", \"city\": \"Austin\", \"region\": \"TX\"}")
			.exchange()
			.expectStatus().isCreated()
			.returnResult(LocationResponse.class)
			.getResponseBody();
	}

}

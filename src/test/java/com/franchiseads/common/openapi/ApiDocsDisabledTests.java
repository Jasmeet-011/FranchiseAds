package com.franchiseads.common.openapi;

import com.franchiseads.TestcontainersConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/** {@code API_DOCS_ENABLED=false} switches off both the OpenAPI document and Swagger UI (ADR-036). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "API_DOCS_ENABLED=false")
@AutoConfigureRestTestClient
class ApiDocsDisabledTests {

	@Autowired
	private RestTestClient client;

	@ParameterizedTest
	@ValueSource(strings = { "/v3/api-docs", "/swagger-ui.html", "/swagger-ui/index.html" })
	void apiDocsAndSwaggerUiAreNotFound(String path) {
		client.get()
			.uri(path)
			.exchange()
			.expectStatus().isNotFound()
			.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

}

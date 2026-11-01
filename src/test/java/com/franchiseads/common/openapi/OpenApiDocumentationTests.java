package com.franchiseads.common.openapi;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import com.franchiseads.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The OpenAPI document covers every endpoint the application actually maps, so a new endpoint can't go undocumented
 * silently, and each operation's error responses match docs/flow.md (ADR-036).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "API_DOCS_ENABLED=true")
@AutoConfigureRestTestClient
class OpenApiDocumentationTests {

	/**
	 * The ProblemDetail statuses each endpoint documents, from the error table in docs/flow.md. A new endpoint fails
	 * {@link #everyEndpointDocumentsExactlyItsErrorResponsesFromFlowMd} until it's listed here.
	 */
	private static final Map<Endpoint, Set<Integer>> EXPECTED_ERRORS = Map.ofEntries(
			entry(new Endpoint("post", "/brands"), Set.of(400, 409)),
			entry(new Endpoint("get", "/brands"), Set.of()),
			entry(new Endpoint("get", "/brands/{id}"), Set.of(400, 404)),
			entry(new Endpoint("post", "/brands/{brandId}/locations"), Set.of(400, 404)),
			entry(new Endpoint("get", "/brands/{brandId}/locations"), Set.of(400, 404)),
			entry(new Endpoint("get", "/locations/{id}"), Set.of(400, 404)),
			entry(new Endpoint("post", "/brands/{brandId}/campaigns"), Set.of(400, 404)),
			entry(new Endpoint("get", "/brands/{brandId}/campaigns"), Set.of(400, 404)),
			entry(new Endpoint("get", "/campaigns/{id}"), Set.of(400, 404)),
			entry(new Endpoint("post", "/campaigns/{campaignId}/generate"), Set.of(400, 404, 422, 502, 503, 504)),
			entry(new Endpoint("get", "/campaigns/{campaignId}/variants"), Set.of(400, 404)),
			entry(new Endpoint("get", "/variants/{id}"), Set.of(400, 404)),
			entry(new Endpoint("post", "/variants/{id}/submit"), Set.of(400, 404, 409)),
			entry(new Endpoint("post", "/variants/{id}/approve"), Set.of(400, 404, 409)),
			entry(new Endpoint("post", "/variants/{id}/reject"), Set.of(400, 404, 409)));

	/** Endpoints that answer 201 Created; every other one answers 200. */
	private static final Set<Endpoint> CREATES = Set.of(new Endpoint("post", "/brands"),
			new Endpoint("post", "/brands/{brandId}/locations"), new Endpoint("post", "/brands/{brandId}/campaigns"),
			new Endpoint("post", "/campaigns/{campaignId}/generate"));

	@Autowired
	private RestTestClient client;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Test
	void everyControllerEndpointIsDocumentedAndNothingElse() {
		List<Endpoint> mapped = mappedEndpoints();
		// Guards against a vacuous pass: the mapping lookup must find the known endpoints.
		assertThat(mapped).containsAll(EXPECTED_ERRORS.keySet());

		assertThat(documentedEndpoints(apiDocs())).containsExactlyInAnyOrderElementsOf(mapped);
	}

	@Test
	void everyOperationHasASummaryAndExactlyOneSuccessResponse() {
		JsonNode paths = apiDocs().path("paths");
		for (Endpoint endpoint : mappedEndpoints()) {
			JsonNode operation = paths.path(endpoint.path()).path(endpoint.method());
			assertThat(operation.path("summary").asString("")).as("summary of %s", endpoint).isNotBlank();

			List<String> successes = responseCodes(operation).stream().filter(code -> code.startsWith("2")).toList();
			String expected = CREATES.contains(endpoint) ? "201" : "200";
			assertThat(successes).as("success responses of %s", endpoint).containsExactly(expected);
			assertThat(operation.path("responses").path(expected).path("content").path("*/*").path("schema"))
				.as("success schema of %s", endpoint)
				.isNotEmpty();
		}
	}

	@Test
	void everyEndpointDocumentsExactlyItsErrorResponsesFromFlowMd() {
		JsonNode paths = apiDocs().path("paths");
		for (Endpoint endpoint : mappedEndpoints()) {
			assertThat(EXPECTED_ERRORS).as("expected error responses for %s (add it, per docs/flow.md)", endpoint)
				.containsKey(endpoint);
			Set<Integer> errors = new TreeSet<>();
			for (String code : responseCodes(paths.path(endpoint.path()).path(endpoint.method()))) {
				if (!code.startsWith("2")) {
					errors.add(Integer.valueOf(code));
				}
			}
			assertThat(errors).as("error responses of %s", endpoint)
				.containsExactlyInAnyOrderElementsOf(EXPECTED_ERRORS.get(endpoint));
		}
	}

	@Test
	void errorResponsesReferenceOneSharedProblemDetailDefinitionPerStatus() {
		JsonNode docs = apiDocs();
		for (Endpoint endpoint : mappedEndpoints()) {
			JsonNode responses = docs.path("paths").path(endpoint.path()).path(endpoint.method()).path("responses");
			for (Map.Entry<String, JsonNode> response : responses.properties()) {
				if (!response.getKey().startsWith("2")) {
					assertThat(response.getValue().path("$ref").asString(""))
						.as("%s response of %s", response.getKey(), endpoint)
						.isEqualTo("#/components/responses/Problem" + response.getKey());
				}
			}
		}

		JsonNode components = docs.path("components");
		assertThat(components.path("responses").propertyNames()).containsExactlyInAnyOrder("Problem400",
				"Problem404", "Problem409", "Problem422", "Problem502", "Problem503", "Problem504");
		for (JsonNode response : components.path("responses").values()) {
			assertThat(response.path("description").asString("")).isNotBlank();
			assertThat(response.path("content").path("application/problem+json").path("schema").path("$ref")
				.asString("")).isEqualTo("#/components/schemas/ProblemDetail");
		}
		assertThat(components.path("schemas").path("ProblemDetail").path("properties").propertyNames())
			.containsExactlyInAnyOrder("type", "title", "status", "detail", "instance", "errors", "currentStatus",
					"attemptedAction");
	}

	@Test
	void swaggerUiIsServed() {
		client.get().uri("/swagger-ui/index.html").exchange().expectStatus().isOk();
		client.get().uri("/swagger-ui.html").exchange().expectStatus().is3xxRedirection();
	}

	private JsonNode apiDocs() {
		String body = client.get()
			.uri("/v3/api-docs")
			.exchange()
			.expectStatus().isOk()
			.returnResult(String.class)
			.getResponseBody();
		return JsonMapper.shared().readTree(body);
	}

	/** Every (method, path) the application's own controllers map, read from Spring MVC itself. */
	private List<Endpoint> mappedEndpoints() {
		List<Endpoint> endpoints = new ArrayList<>();
		handlerMapping.getHandlerMethods().forEach((info, handler) -> {
			if (!handler.getBeanType().getPackageName().startsWith("com.franchiseads")) {
				return;
			}
			for (String path : info.getPatternValues()) {
				for (RequestMethod method : info.getMethodsCondition().getMethods()) {
					endpoints.add(new Endpoint(method.name().toLowerCase(), path));
				}
			}
		});
		endpoints.sort(Comparator.comparing(Endpoint::path).thenComparing(Endpoint::method));
		return endpoints;
	}

	private static List<Endpoint> documentedEndpoints(JsonNode docs) {
		List<Endpoint> endpoints = new ArrayList<>();
		for (Map.Entry<String, JsonNode> path : docs.path("paths").properties()) {
			for (String method : path.getValue().propertyNames()) {
				endpoints.add(new Endpoint(method, path.getKey()));
			}
		}
		return endpoints;
	}

	private static List<String> responseCodes(JsonNode operation) {
		return List.copyOf(operation.path("responses").propertyNames());
	}

	record Endpoint(String method, String path) {
	}

}

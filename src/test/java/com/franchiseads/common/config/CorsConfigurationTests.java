package com.franchiseads.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CORS as a browser sees it, through a stub controller shaped like the real ones (a create with Location, a validated
 * body). The origins are set here, not taken from the defaults or .env; {@link CorsPropertiesTests} covers the defaults.
 */
@WebMvcTest(controllers = CorsConfigurationTests.StubController.class,
		properties = "franchiseads.cors.allowed-origins=http://localhost:5173,http://ui.test:3000")
@Import({ WebCorsConfiguration.class, CorsConfigurationTests.StubController.class })
class CorsConfigurationTests {

	private static final String ALLOWED = "http://localhost:5173";

	private static final String OTHER = "http://evil.example";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private StubController controller;

	@BeforeEach
	void resetCalls() {
		controller.calls.set(0);
	}

	@Test
	void preflightFromAllowedOriginGetsCorsHeaders() throws Exception {
		mvc.perform(options("/stub").header(HttpHeaders.ORIGIN, ALLOWED)
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,OPTIONS"))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "content-type"))
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
		assertThat(controller.calls).hasValue(0);
	}

	@Test
	void preflightFromSecondConfiguredOriginIsAllowed() throws Exception {
		mvc.perform(options("/stub").header(HttpHeaders.ORIGIN, "http://ui.test:3000")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://ui.test:3000"));
	}

	@Test
	void preflightFromOtherOriginIsRejected() throws Exception {
		mvc.perform(options("/stub").header(HttpHeaders.ORIGIN, OTHER)
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void requestFromOtherOriginIsRejectedBeforeTheController() throws Exception {
		mvc.perform(get("/stub").header(HttpHeaders.ORIGIN, OTHER))
			.andExpect(status().isForbidden())
			.andExpect(content().string("Invalid CORS request"))
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
		assertThat(controller.calls).hasValue(0);
	}

	@Test
	void preflightForDisallowedMethodIsRejected() throws Exception {
		mvc.perform(options("/stub/7").header(HttpHeaders.ORIGIN, ALLOWED)
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void preflightForOnlyDisallowedHeaderIsRejected() throws Exception {
		mvc.perform(options("/stub").header(HttpHeaders.ORIGIN, ALLOWED)
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	/** Spring answers a partly allowed preflight with the allowed subset; the browser then blocks the request. */
	@Test
	void preflightAllowsOnlyTheListedHeader() throws Exception {
		mvc.perform(options("/stub").header(HttpHeaders.ORIGIN, ALLOWED)
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type, authorization"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "content-type"));
	}

	@Test
	void createFromAllowedOriginExposesLocation() throws Exception {
		mvc.perform(post("/stub").header(HttpHeaders.ORIGIN, ALLOWED)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"Acme\"}"))
			.andExpect(status().isCreated())
			.andExpect(header().string(HttpHeaders.LOCATION, "/stub/7"))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Location"))
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
	}

	@Test
	void errorResponseFromAllowedOriginCarriesCorsHeaders() throws Exception {
		mvc.perform(post("/stub").header(HttpHeaders.ORIGIN, ALLOWED)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \" \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED));
	}

	@Test
	void requestWithoutOriginGetsNoCorsHeaders() throws Exception {
		mvc.perform(get("/stub"))
			.andExpect(status().isOk())
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
		assertThat(controller.calls).hasValue(1);
	}

	@RestController
	@RequestMapping("/stub")
	static class StubController {

		final AtomicInteger calls = new AtomicInteger();

		@GetMapping
		String list() {
			calls.incrementAndGet();
			return "[]";
		}

		@GetMapping("/{id}")
		String get(@PathVariable long id) {
			calls.incrementAndGet();
			return "{}";
		}

		@PostMapping
		ResponseEntity<Void> create(@Valid @RequestBody StubRequest request) {
			calls.incrementAndGet();
			return ResponseEntity.created(URI.create("/stub/7")).build();
		}

	}

	record StubRequest(@NotBlank String name) {
	}

}

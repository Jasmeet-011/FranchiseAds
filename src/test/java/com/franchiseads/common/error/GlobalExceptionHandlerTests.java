package com.franchiseads.common.error;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.franchiseads.claude.ClaudeRequestException;
import com.franchiseads.claude.ClaudeTimeoutException;
import com.franchiseads.claude.ClaudeUnavailableException;
import com.franchiseads.generation.LocationBrandMismatchException;
import com.franchiseads.generation.MalformedAdCopyException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.sql.SQLException;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTests.StubController.class)
@Import(GlobalExceptionHandlerTests.StubController.class)
class GlobalExceptionHandlerTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void validationFailureListsEveryFieldAndMessage() throws Exception {
		mvc.perform(post("/stub").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": " ", "tags": ["ok", "", "x", "y"]}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.title").value("Bad Request"))
			.andExpect(jsonPath("$.detail").value("Request validation failed."))
			.andExpect(jsonPath("$.instance").value("/stub"))
			.andExpect(jsonPath("$.errors.length()").value(3))
			.andExpect(jsonPath("$.errors[0].field").value("name"))
			.andExpect(jsonPath("$.errors[0].message").value("must not be blank"))
			.andExpect(jsonPath("$.errors[1].field").value("tags"))
			.andExpect(jsonPath("$.errors[1].message").value("size must be between 0 and 3"))
			.andExpect(jsonPath("$.errors[2].field").value("tags[1]"))
			.andExpect(jsonPath("$.errors[2].message").value("must not be blank"));
	}

	@Test
	void unreadableBodyIsProblemDetail400() throws Exception {
		mvc.perform(post("/stub").contentType(MediaType.APPLICATION_JSON).content("{not json"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.detail").value("Failed to read request"));
	}

	@Test
	void unsupportedMethodIsProblemDetail405() throws Exception {
		mvc.perform(delete("/stub"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(405));
	}

	@Test
	void unsupportedMediaTypeIsProblemDetail415() throws Exception {
		mvc.perform(post("/stub").contentType(MediaType.TEXT_PLAIN).content("name"))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(415));
	}

	@Test
	void missingRequestParameterIsProblemDetail400() throws Exception {
		mvc.perform(get("/stub/search"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Required parameter 'q' is not present."));
	}

	@Test
	void nonNumericIdIsProblemDetail400() throws Exception {
		mvc.perform(get("/stub/items/abc"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void unknownPathIsProblemDetail404() throws Exception {
		mvc.perform(get("/no-such-path"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void missingResourceIs404WithResourceAndId() throws Exception {
		mvc.perform(get("/stub/items/99"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("Not Found"))
			.andExpect(jsonPath("$.detail").value("Brand 99 not found"))
			.andExpect(jsonPath("$.instance").value("/stub/items/99"));
	}

	@Test
	void duplicateBrandNameIs409() throws Exception {
		mvc.perform(get("/stub/integrity").param("kind", "UNIQUE").param("constraint", "uq_brand_name_lower"))
			.andExpect(status().isConflict())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("Conflict"))
			.andExpect(jsonPath("$.detail")
				.value("A brand with this name already exists (names are compared ignoring case)."));
	}

	@Test
	void unmappedIntegrityViolationIsGeneric500() throws Exception {
		mvc.perform(get("/stub/integrity").param("kind", "FOREIGN_KEY").param("constraint", "fk_location_brand"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
			.andExpect(content().string(not(containsString("fk_location_brand"))));
	}

	@Test
	void unexpectedExceptionIsGeneric500WithoutInternalDetails() throws Exception {
		mvc.perform(get("/stub/boom"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
			.andExpect(content().string(not(containsString("secret"))));
	}

	@Test
	void locationOfAnotherBrandIs422WithDetail() throws Exception {
		mvc.perform(get("/stub/mismatch"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("Unprocessable Content"))
			.andExpect(jsonPath("$.detail").value("Location 42 does not belong to the brand of campaign 3"));
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource(delimiter = '|', textBlock = """
			malformed   | 502 | Bad Gateway         | The AI model did not return usable ad copy. Try again.
			rejected    | 502 | Bad Gateway         | The AI provider rejected the request.
			unavailable | 503 | Service Unavailable | The AI provider is unavailable. Try again later.
			timeout     | 504 | Gateway Timeout     | The AI provider did not respond in time. Try again later.
			""")
	void claudeFailuresMapToFixedDetailsWithoutUpstreamText(String kind, int status, String title, String detail)
			throws Exception {
		mvc.perform(get("/stub/claude/{kind}", kind))
			.andExpect(status().is(status))
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value(title))
			.andExpect(jsonPath("$.detail").value(detail))
			.andExpect(content().string(not(containsString("upstream-secret"))))
			.andExpect(content().string(not(containsString("req_"))));
	}

	record StubRequest(@NotBlank String name, @Size(max = 3) List<@NotBlank String> tags) {
	}

	@RestController
	@RequestMapping("/stub")
	static class StubController {

		@PostMapping
		StubRequest create(@Valid @RequestBody StubRequest request) {
			return request;
		}

		@GetMapping("/search")
		String search(@RequestParam String q) {
			return q;
		}

		@GetMapping("/items/{id}")
		String item(@PathVariable Long id) {
			throw new ResourceNotFoundException("Brand", id);
		}

		@GetMapping("/integrity")
		String integrity(@RequestParam ConstraintKind kind, @RequestParam String constraint) {
			SQLException sqlException = new SQLException("violation of " + constraint);
			throw new DataIntegrityViolationException("could not execute statement",
					new ConstraintViolationException("could not execute statement", sqlException, kind, constraint));
		}

		@GetMapping("/mismatch")
		String mismatch() {
			throw new LocationBrandMismatchException(42L, 3L);
		}

		@GetMapping("/claude/{kind}")
		String claude(@PathVariable String kind) {
			String upstream = "HTTP 400 invalid_request_error: upstream-secret (request-id req_123)";
			throw switch (kind) {
				case "malformed" -> new MalformedAdCopyException(upstream);
				case "rejected" -> new ClaudeRequestException(upstream);
				case "unavailable" -> new ClaudeUnavailableException(upstream, null);
				case "timeout" -> new ClaudeTimeoutException(upstream, null);
				default -> new IllegalArgumentException(kind);
			};
		}

		@GetMapping("/boom")
		String boom() {
			throw new IllegalStateException("secret internal detail");
		}

	}

}

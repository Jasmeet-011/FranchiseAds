package com.franchiseads.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.franchiseads.brand.dto.CreateBrandRequest;
import com.franchiseads.common.error.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BrandController.class)
class BrandControllerTests {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private BrandService brandService;

	@Test
	void createReturns201WithLocationAndBody() throws Exception {
		given(brandService.create(any())).willReturn(brand(7L, "Acme Fitness", List.of("Cheap")));

		mvc.perform(post("/brands").contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "Acme Fitness", "voiceGuidelines": "Upbeat", "bannedWords": ["Cheap"]}
				"""))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "/brands/7"))
			.andExpect(jsonPath("$.id").value(7))
			.andExpect(jsonPath("$.name").value("Acme Fitness"))
			.andExpect(jsonPath("$.bannedWords[0]").value("Cheap"))
			.andExpect(jsonPath("$.createdAt").value("2026-11-01T14:03:11Z"));
	}

	@Test
	void createPassesNormalizedRequestToService() throws Exception {
		given(brandService.create(any())).willReturn(brand(7L, "Acme", List.of()));

		mvc.perform(post("/brands").contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "  Acme ", "bannedWords": ["Cheap", "cheap", " CHEAP "]}
				"""))
			.andExpect(status().isCreated());

		ArgumentCaptor<CreateBrandRequest> captor = ArgumentCaptor.forClass(CreateBrandRequest.class);
		verify(brandService).create(captor.capture());
		assertThat(captor.getValue().name()).isEqualTo("Acme");
		assertThat(captor.getValue().bannedWords()).containsExactly("Cheap");
	}

	@Test
	void rejectsBlankNameAndOverlongVoiceGuidelines() throws Exception {
		mvc.perform(post("/brands").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"   \", \"voiceGuidelines\": \"" + "v".repeat(2001) + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors.length()").value(2))
			.andExpect(jsonPath("$.errors[0].field").value("name"))
			.andExpect(jsonPath("$.errors[0].message").value("must not be blank"))
			.andExpect(jsonPath("$.errors[1].field").value("voiceGuidelines"))
			.andExpect(jsonPath("$.errors[1].message").value("size must be between 0 and 2000"));
		verifyNoInteractions(brandService);
	}

	@Test
	void rejectsOverlongName() throws Exception {
		mvc.perform(post("/brands").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"" + "n".repeat(101) + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("name"))
			.andExpect(jsonPath("$.errors[0].message").value("size must be between 0 and 100"));
	}

	@Test
	void rejectsBlankAndOverlongBannedWords() throws Exception {
		mvc.perform(post("/brands").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"Acme\", \"bannedWords\": [\"ok\", \"  \", \"" + "w".repeat(51) + "\"]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.length()").value(2))
			.andExpect(jsonPath("$.errors[0].field").value("bannedWords[1]"))
			.andExpect(jsonPath("$.errors[0].message").value("must not be blank"))
			.andExpect(jsonPath("$.errors[1].field").value("bannedWords[2]"))
			.andExpect(jsonPath("$.errors[1].message").value("size must be between 0 and 50"));
	}

	@Test
	void rejectsMoreThan50BannedWords() throws Exception {
		mvc.perform(post("/brands").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"Acme\", \"bannedWords\": " + jsonWords(51) + "}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("bannedWords"))
			.andExpect(jsonPath("$.errors[0].message").value("size must be between 0 and 50"));
	}

	@Test
	void countsBannedWordsAfterDeduplication() throws Exception {
		given(brandService.create(any())).willReturn(brand(7L, "Acme", List.of()));
		String fiftyWordsPlusOneDuplicate = jsonWords(50).replace("]", ", \"WORD0\"]");

		mvc.perform(post("/brands").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"Acme\", \"bannedWords\": " + fiftyWordsPlusOneDuplicate + "}"))
			.andExpect(status().isCreated());
	}

	@Test
	void getReturnsBrand() throws Exception {
		given(brandService.get(7L)).willReturn(brand(7L, "Acme", List.of()));

		mvc.perform(get("/brands/7"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(7))
			.andExpect(jsonPath("$.name").value("Acme"));
	}

	@Test
	void getUnknownBrandIs404() throws Exception {
		given(brandService.get(99L)).willThrow(new ResourceNotFoundException("Brand", 99L));

		mvc.perform(get("/brands/99"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Brand 99 not found"));
	}

	@Test
	void listReturnsAllBrands() throws Exception {
		given(brandService.list()).willReturn(List.of(brand(1L, "Acme", List.of()), brand(2L, "Bolt", List.of())));

		mvc.perform(get("/brands"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[1].name").value("Bolt"));
	}

	private static Brand brand(Long id, String name, List<String> bannedWords) {
		Brand brand = new Brand(name, null, bannedWords);
		ReflectionTestUtils.setField(brand, "id", id);
		ReflectionTestUtils.setField(brand, "createdAt", Instant.parse("2026-11-01T14:03:11Z"));
		return brand;
	}

	private static String jsonWords(int count) {
		return IntStream.range(0, count)
			.mapToObj(i -> "\"word" + i + "\"")
			.collect(Collectors.joining(", ", "[", "]"));
	}

}

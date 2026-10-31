package com.franchiseads.brand.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreateBrandRequestTests {

	@Test
	void stripsNameAndTurnsBlankVoiceGuidelinesIntoNull() {
		CreateBrandRequest request = new CreateBrandRequest("  Acme Fitness ", "   ", null);

		assertThat(request.name()).isEqualTo("Acme Fitness");
		assertThat(request.voiceGuidelines()).isNull();
	}

	@Test
	void missingBannedWordsBecomeEmptyList() {
		assertThat(new CreateBrandRequest("Acme", null, null).bannedWords()).isNotNull().isEmpty();
	}

	@Test
	void dedupesBannedWordsIgnoringCaseAfterStrippingAndKeepsFirstSpelling() {
		CreateBrandRequest request = new CreateBrandRequest("Acme", null,
				List.of("Cheap", "cheap", " CHEAP ", "best in town", "Best In Town"));

		assertThat(request.bannedWords()).containsExactly("Cheap", "best in town");
	}

	@Test
	void keepsBlankAndNullBannedWordsOnceSoValidationReportsThem() {
		CreateBrandRequest request = new CreateBrandRequest("Acme", null, Arrays.asList("ok", " ", "", null, null));

		assertThat(request.bannedWords()).containsExactly("ok", "", null);
	}

}

package com.franchiseads.brand.dto;

import com.franchiseads.common.Strings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Body of {@code POST /brands}. Values are normalized on construction, before validation: strings are stripped,
 * a blank {@code voiceGuidelines} becomes null, and {@code bannedWords} defaults to empty and is de-duplicated
 * ignoring case, keeping the first spelling.
 */
public record CreateBrandRequest(
		@NotBlank @Size(max = 100) String name,
		@Size(max = 2000) String voiceGuidelines,
		@Size(max = 50) List<@NotBlank @Size(max = 50) String> bannedWords) {

	public CreateBrandRequest {
		name = Strings.strip(name);
		voiceGuidelines = Strings.stripToNull(voiceGuidelines);
		bannedWords = normalizeBannedWords(bannedWords);
	}

	private static List<String> normalizeBannedWords(List<String> words) {
		if (words == null) {
			return List.of();
		}
		Set<String> seen = new HashSet<>();
		List<String> result = new ArrayList<>();
		for (String word : words) {
			String stripped = Strings.strip(word);
			// Null and blank entries are kept (once) so validation reports them.
			if (seen.add((stripped != null) ? stripped.toLowerCase(Locale.ROOT) : null)) {
				result.add(stripped);
			}
		}
		return Collections.unmodifiableList(result);
	}

}

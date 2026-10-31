package com.franchiseads.validation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * What a valid set of ad copy looks like. The single source for these numbers: the prompt states them and the
 * validator enforces them (ADR-024). Bounds are Google's responsive search ad limits.
 *
 * @param headlineCount exactly this many headlines (Google allows 3 to 15)
 * @param descriptionCount exactly this many descriptions (Google allows 2 to 4)
 * @param headlineMaxLength max code points per headline (Google: 30)
 * @param descriptionMaxLength max code points per description (Google: 90)
 */
@Validated
@ConfigurationProperties("franchiseads.ad-copy")
public record AdCopyRules(@DefaultValue("5") @Min(3) @Max(15) int headlineCount,
		@DefaultValue("2") @Min(2) @Max(4) int descriptionCount,
		@DefaultValue("30") @Min(1) @Max(30) int headlineMaxLength,
		@DefaultValue("90") @Min(1) @Max(90) int descriptionMaxLength) {

	public static final AdCopyRules DEFAULTS = new AdCopyRules(5, 2, 30, 90);

}

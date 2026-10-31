package com.franchiseads.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Binding only: the runner loads no application.yml and no .env, so every property is set here. */
class AdCopyRulesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(AdCopyRulesConfiguration.class);

	@Test
	void bindsFiveHeadlinesTwoDescriptionsAndGoogleLengthLimitsByDefault() {
		contextRunner.run(context -> assertThat(context.getBean(AdCopyRules.class)).isEqualTo(AdCopyRules.DEFAULTS)
			.isEqualTo(new AdCopyRules(5, 2, 30, 90)));
	}

	@Test
	void bindsCountsWithinGoogleLimits() {
		contextRunner
			.withPropertyValues("franchiseads.ad-copy.headline-count=15", "franchiseads.ad-copy.description-count=4")
			.run(context -> assertThat(context.getBean(AdCopyRules.class)).isEqualTo(new AdCopyRules(15, 4, 30, 90)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "headline-count=2", "headline-count=16", "description-count=1", "description-count=5",
			"headline-max-length=31", "description-max-length=91", "headline-max-length=0" })
	void rejectsValuesOutsideGoogleLimits(String property) {
		contextRunner.withPropertyValues("franchiseads.ad-copy." + property)
			.run(context -> assertThat(context).hasFailed());
	}

}

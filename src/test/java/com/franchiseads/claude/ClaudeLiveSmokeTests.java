package com.franchiseads.claude;

import static org.assertj.core.api.Assertions.assertThat;

import com.franchiseads.generation.AdBrief;
import com.franchiseads.generation.AdCopy;
import com.franchiseads.generation.AdCopyParser;
import com.franchiseads.generation.Prompt;
import com.franchiseads.generation.PromptBuilder;
import com.franchiseads.validation.AdCopyRules;
import com.franchiseads.validation.AdCopyRulesConfiguration;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Calls the real Claude API once with the real configuration (application.yml, .env, environment). Tagged "live" and
 * excluded from {@code verify} and CI; run it by hand with {@code ./mvnw test -Plive} (see README). Costs one small
 * request. Only the Claude beans are loaded, so no database is needed.
 */
@Tag("live")
@SpringBootTest(classes = { ClaudeConfiguration.class, AdCopyRulesConfiguration.class })
class ClaudeLiveSmokeTests {

	private static final Logger log = LoggerFactory.getLogger(ClaudeLiveSmokeTests.class);

	@Autowired
	private ClaudeClient client;

	@Autowired
	private ClaudeProperties properties;

	@Autowired
	private AdCopyRules rules;

	@Test
	void realApiReturnsParseableAdCopy() {
		assertThat(properties.hasApiKey())
			.as("API key not configured: set ANTHROPIC_API_KEY in .env or the environment to run this test")
			.isTrue();
		AdBrief brief = new AdBrief("Acme Fitness", "Upbeat and plain-spoken. Short words, no jargon.",
				List.of("cheap", "best in town"), "Acme Fitness Downtown", "Austin", "TX",
				"First month free for new members", "New year, new routine");
		Prompt prompt = new PromptBuilder(rules).build(brief);

		long start = System.nanoTime();
		ClaudeResponse response = client.complete(prompt.system(), prompt.user());
		long latencyMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

		log.info("LIVE SMOKE: model={}, latency={} ms (including any retries), stop_reason={}, input_tokens={}, "
				+ "output_tokens={}, thinking_blocks={} ({})", response.model(), latencyMillis, response.stopReason(),
				response.inputTokens(), response.outputTokens(), response.thinkingBlocks(),
				(response.thinkingBlocks() > 0) ? "thinking blocks came back" : "no thinking blocks");
		log.info("LIVE SMOKE: raw text:\n{}", response.text());

		AdCopy copy = new AdCopyParser().parse(response);

		log.info("LIVE SMOKE: headlines={}, descriptions={}", copy.headlines(), copy.descriptions());
		assertThat(copy.headlines()).isNotEmpty();
		assertThat(copy.descriptions()).isNotEmpty();
	}

}

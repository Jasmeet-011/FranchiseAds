package com.franchiseads.claude;

import java.time.Clock;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClaudeProperties.class)
public class ClaudeConfiguration {

	/** Uses the application's {@link Clock} bean, or the system clock where there is none (slice and live tests). */
	@Bean
	ClaudeClient claudeClient(ClaudeProperties properties, ObjectProvider<Clock> clock) {
		return new ClaudeClient(properties, Thread::sleep, () -> ThreadLocalRandom.current().nextDouble(),
				clock.getIfAvailable(Clock::systemUTC));
	}

}

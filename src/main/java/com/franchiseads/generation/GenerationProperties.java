package com.franchiseads.generation;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for the generate flow.
 *
 * @param deadline how long one generate request may spend calling Claude, both calls and all retries included,
 * counted from the start of the request (ADR-027)
 */
@Validated
@ConfigurationProperties("franchiseads.generation")
public record GenerationProperties(@DefaultValue("50s") @NotNull Duration deadline) {
}

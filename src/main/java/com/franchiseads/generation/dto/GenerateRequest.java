package com.franchiseads.generation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Body of {@code POST /campaigns/{campaignId}/generate}. */
public record GenerateRequest(@NotNull @Positive Long locationId) {
}

package com.franchiseads.generation;

/** What the generate flow needs from the database, as plain values that are safe to use outside a transaction. */
public record GenerationInputs(Long campaignId, Long locationId, AdBrief brief) {
}

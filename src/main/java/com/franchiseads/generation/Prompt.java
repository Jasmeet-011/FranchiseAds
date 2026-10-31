package com.franchiseads.generation;

/** The two prompt parts of one Messages API call: the {@code system} field and the single user message. */
public record Prompt(String system, String user) {
}

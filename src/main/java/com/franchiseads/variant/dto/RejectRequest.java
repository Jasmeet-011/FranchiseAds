package com.franchiseads.variant.dto;

import com.franchiseads.common.Strings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /variants/{id}/reject}. The reason is stripped on construction, so the limit applies to the
 * stored value; 500 matches the {@code rejection_reason} column.
 */
public record RejectRequest(@NotBlank @Size(max = 500) String reason) {

	public RejectRequest {
		reason = Strings.strip(reason);
	}

}

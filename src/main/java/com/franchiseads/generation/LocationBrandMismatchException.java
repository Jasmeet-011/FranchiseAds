package com.franchiseads.generation;

/** The location belongs to a different brand than the campaign. Maps to 422 (docs/flow.md). */
public class LocationBrandMismatchException extends RuntimeException {

	public LocationBrandMismatchException(Long locationId, Long campaignId) {
		super("Location " + locationId + " does not belong to the brand of campaign " + campaignId);
	}

}

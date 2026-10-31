package com.franchiseads.common.error;

/**
 * A requested resource (brand, location, campaign, variant) does not exist. Mapped to 404.
 */
public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String resource, Object id) {
		super(resource + " " + id + " not found");
	}

}

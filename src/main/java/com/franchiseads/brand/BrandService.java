package com.franchiseads.brand;

import com.franchiseads.brand.dto.CreateBrandRequest;
import com.franchiseads.common.error.ResourceNotFoundException;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BrandService {

	private final BrandRepository brands;

	BrandService(BrandRepository brands) {
		this.brands = brands;
	}

	/**
	 * Name uniqueness is enforced only by the {@code uq_brand_name_lower} index (ADR-015): an exists-then-insert
	 * check would race. {@code saveAndFlush} makes a duplicate fail here, inside the request.
	 */
	@Transactional
	public Brand create(CreateBrandRequest request) {
		return brands.saveAndFlush(new Brand(request.name(), request.voiceGuidelines(), request.bannedWords()));
	}

	@Transactional(readOnly = true)
	public List<Brand> list() {
		return brands.findAll(Sort.by("id"));
	}

	/** Returns the brand or throws {@link ResourceNotFoundException}. Also used by locations and campaigns. */
	@Transactional(readOnly = true)
	public Brand get(Long id) {
		return brands.findById(id).orElseThrow(() -> new ResourceNotFoundException("Brand", id));
	}

}

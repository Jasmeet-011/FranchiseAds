package com.franchiseads.campaign;

import com.franchiseads.brand.Brand;
import com.franchiseads.brand.BrandService;
import com.franchiseads.campaign.dto.CreateCampaignRequest;
import com.franchiseads.common.error.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignService {

	private final CampaignRepository campaigns;

	private final BrandService brandService;

	CampaignService(CampaignRepository campaigns, BrandService brandService) {
		this.campaigns = campaigns;
		this.brandService = brandService;
	}

	@Transactional
	public Campaign create(Long brandId, CreateCampaignRequest request) {
		Brand brand = brandService.get(brandId);
		return campaigns.save(new Campaign(brand, request.name(), request.theme()));
	}

	/** Campaigns of an existing brand, oldest first. An unknown brand is 404, not an empty list. */
	@Transactional(readOnly = true)
	public List<Campaign> listForBrand(Long brandId) {
		brandService.get(brandId);
		return campaigns.findAllByBrandIdOrderByIdAsc(brandId);
	}

	@Transactional(readOnly = true)
	public Campaign get(Long id) {
		return campaigns.findById(id).orElseThrow(() -> new ResourceNotFoundException("Campaign", id));
	}

}

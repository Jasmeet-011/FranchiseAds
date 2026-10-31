package com.franchiseads.campaign;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

	List<Campaign> findAllByBrandIdOrderByIdAsc(Long brandId);

}

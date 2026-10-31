package com.franchiseads.variant;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdVariantRepository extends JpaRepository<AdVariant, Long> {

	List<AdVariant> findAllByCampaignIdOrderByIdAsc(Long campaignId);

	List<AdVariant> findAllByCampaignIdAndStatusOrderByIdAsc(Long campaignId, VariantStatus status);

}

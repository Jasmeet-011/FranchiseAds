package com.franchiseads.location;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, Long> {

	List<Location> findAllByBrandIdOrderByIdAsc(Long brandId);

}

package com.franchiseads.location;

import com.franchiseads.brand.Brand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "location")
public class Location {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "brand_id", nullable = false, updatable = false)
	private Brand brand;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, length = 100)
	private String city;

	@Column(length = 100)
	private String region;

	@Column(name = "local_offer", length = 200)
	private String localOffer;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Location() {
	}

	public Location(Brand brand, String name, String city, String region, String localOffer) {
		this.brand = brand;
		this.name = name;
		this.city = city;
		this.region = region;
		this.localOffer = localOffer;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	public Long getId() {
		return id;
	}

	public Brand getBrand() {
		return brand;
	}

	public String getName() {
		return name;
	}

	public String getCity() {
		return city;
	}

	public String getRegion() {
		return region;
	}

	public String getLocalOffer() {
		return localOffer;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}

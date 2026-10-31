package com.franchiseads.campaign;

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
@Table(name = "campaign")
public class Campaign {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "brand_id", nullable = false, updatable = false)
	private Brand brand;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, length = 500)
	private String theme;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Campaign() {
	}

	public Campaign(Brand brand, String name, String theme) {
		this.brand = brand;
		this.name = name;
		this.theme = theme;
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

	public String getTheme() {
		return theme;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}

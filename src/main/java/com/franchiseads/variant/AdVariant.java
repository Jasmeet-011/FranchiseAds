package com.franchiseads.variant;

import com.franchiseads.campaign.Campaign;
import com.franchiseads.location.Location;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One generated set of headlines and descriptions for a campaign at a location. The copy never changes after
 * generation; only the review status (and the rejection reason) does, through {@link #submit}, {@link #approve} and
 * {@link #reject}.
 */
@Entity
@Table(name = "ad_variant")
public class AdVariant {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "campaign_id", nullable = false, updatable = false)
	private Campaign campaign;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "location_id", nullable = false, updatable = false)
	private Location location;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false)
	private List<String> headlines = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false)
	private List<String> descriptions = new ArrayList<>();

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private VariantStatus status;

	@Column(name = "validation_passed", nullable = false)
	private boolean validationPassed;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false)
	private List<String> violations = new ArrayList<>();

	@Column(name = "generation_attempts", nullable = false)
	private int generationAttempts;

	@Column(name = "rejection_reason", length = 500)
	private String rejectionReason;

	@Column(nullable = false, length = 100)
	private String model;

	@Version
	private Long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected AdVariant() {
	}

	public AdVariant(Campaign campaign, Location location, List<String> headlines, List<String> descriptions,
			boolean validationPassed, List<String> violations, int generationAttempts, String model) {
		this.campaign = campaign;
		this.location = location;
		this.headlines = new ArrayList<>(headlines);
		this.descriptions = new ArrayList<>(descriptions);
		this.status = VariantStatus.DRAFT;
		this.validationPassed = validationPassed;
		this.violations = new ArrayList<>(violations);
		this.generationAttempts = generationAttempts;
		this.model = model;
	}

	/**
	 * {@code DRAFT} to {@code PENDING_REVIEW}. The rules for this and the other actions, including the validation
	 * guard, are in {@link VariantStatus#next}.
	 */
	public void submit() {
		status = status.next(VariantAction.SUBMIT, validationPassed);
	}

	public void approve() {
		status = status.next(VariantAction.APPROVE, validationPassed);
	}

	/** Changes nothing if the transition isn't allowed. */
	public void reject(String reason) {
		VariantStatus next = status.next(VariantAction.REJECT, validationPassed);
		rejectionReason = Objects.requireNonNull(reason, "reason");
		status = next;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
		this.updatedAt = this.createdAt;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	public Long getId() {
		return id;
	}

	public Campaign getCampaign() {
		return campaign;
	}

	public Location getLocation() {
		return location;
	}

	public List<String> getHeadlines() {
		return List.copyOf(headlines);
	}

	public List<String> getDescriptions() {
		return List.copyOf(descriptions);
	}

	public VariantStatus getStatus() {
		return status;
	}

	public boolean isValidationPassed() {
		return validationPassed;
	}

	public List<String> getViolations() {
		return List.copyOf(violations);
	}

	public int getGenerationAttempts() {
		return generationAttempts;
	}

	public String getRejectionReason() {
		return rejectionReason;
	}

	public String getModel() {
		return model;
	}

	public Long getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}

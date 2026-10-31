package com.franchiseads.brand;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "brand")
public class Brand {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "voice_guidelines", length = 2000)
	private String voiceGuidelines;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "banned_words", nullable = false)
	private List<String> bannedWords = new ArrayList<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Brand() {
	}

	public Brand(String name, String voiceGuidelines, List<String> bannedWords) {
		this.name = name;
		this.voiceGuidelines = voiceGuidelines;
		this.bannedWords = new ArrayList<>(bannedWords);
	}

	@PrePersist
	void onCreate() {
		// timestamptz stores microseconds; truncate so the saved value equals what is read back.
		this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getVoiceGuidelines() {
		return voiceGuidelines;
	}

	public List<String> getBannedWords() {
		return List.copyOf(bannedWords);
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}

package com.schwab.shortener.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "links", uniqueConstraints = {@UniqueConstraint(name = "uk_links_code", columnNames = "code"), @UniqueConstraint(name = "uk_links_idempotency", columnNames = "idempotency_key")})
public class Link {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(nullable = false, length = 12) private String code;
  @Column(nullable = false, length = 2048) private String originalUrl;
  @Column(nullable = false) private Instant createdAt;
  private Instant expiresAt;
  @Column(name = "idempotency_key", length = 128) private String idempotencyKey;
  @Column(nullable = false) private boolean deleted;
  protected Link() { }
  public Link(String code, String originalUrl, Instant expiresAt, String idempotencyKey) { this.code = code; this.originalUrl = originalUrl; this.expiresAt = expiresAt; this.idempotencyKey = idempotencyKey; this.createdAt = Instant.now(); }
  public Long id() { return id; } public String code() { return code; } public String originalUrl() { return originalUrl; } public Instant createdAt() { return createdAt; } public Instant expiresAt() { return expiresAt; } public boolean deleted() { return deleted; }
  public boolean expired(Instant now) { return expiresAt != null && !expiresAt.isAfter(now); }
  public void delete() { deleted = true; }
}

package com.schwab.shortener.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "click_events")
public class ClickEvent {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(nullable = false) private Long linkId;
  @Column(nullable = false) private Instant clickedAt;
  @Column(length = 512) private String referrer;
  protected ClickEvent() { }
  public ClickEvent(Long linkId, String referrer) { this.linkId = linkId; this.referrer = referrer; this.clickedAt = Instant.now(); }
  public Instant clickedAt() { return clickedAt; } public String referrer() { return referrer; }
}

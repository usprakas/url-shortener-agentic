package com.schwab.shortener.application;

import com.schwab.shortener.domain.ClickEvent;
import com.schwab.shortener.infrastructure.ClickEventRepository;
import java.util.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service public class ClickRecorder {
  private final ClickEventRepository events; public ClickRecorder(ClickEventRepository events) { this.events = events; }
  @Async public void record(Long linkId, String referrer) { try { events.save(new ClickEvent(linkId, referrer)); } catch (RuntimeException ignored) { /* Redirects must remain available if analytics fails. */ } }
  public List<ClickEvent> events(Long linkId) { return events.findByLinkId(linkId); }
}

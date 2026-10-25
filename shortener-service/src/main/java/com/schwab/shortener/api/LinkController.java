package com.schwab.shortener.api;

import com.schwab.shortener.application.LinkService;
import com.schwab.shortener.domain.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class LinkController {
  private final LinkService service;
  public LinkController(LinkService service) { this.service = service; }
  @PostMapping(value = "/api/v1/links", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public LinkResponse create(@Valid @RequestBody CreateLinkRequest request, @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) { return LinkResponse.from(service.create(request.url(), request.alias(), request.expiresAt(), idempotencyKey)); }
  @GetMapping("/api/v1/links/{code}") public LinkResponse metadata(@PathVariable String code) { return LinkResponse.from(service.metadata(code)); }
  @GetMapping("/api/v1/links/{code}/stats") public StatsResponse stats(@PathVariable String code) { List<ClickEvent> events = service.events(code); Map<String, Long> referrers = new TreeMap<>(); for (ClickEvent e : events) referrers.merge(e.referrer() == null || e.referrer().isBlank() ? "direct" : e.referrer(), 1L, Long::sum); return new StatsResponse(events.size(), events.stream().map(ClickEvent::clickedAt).toList(), referrers); }
  @DeleteMapping("/api/v1/links/{code}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable String code) { service.delete(code); }
  @GetMapping("/{code}") public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) { return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(service.redirect(code, request.getHeader("Referer")).originalUrl())).build(); }
  public record CreateLinkRequest(@NotBlank @Size(max = 2048) String url, @Size(max = 12) String alias, Instant expiresAt) { }
  public record LinkResponse(String code, String originalUrl, Instant createdAt, Instant expiresAt) { static LinkResponse from(Link link) { return new LinkResponse(link.code(), link.originalUrl(), link.createdAt(), link.expiresAt()); } }
  public record StatsResponse(long clickCount, List<Instant> timestamps, Map<String, Long> referrers) { }
}

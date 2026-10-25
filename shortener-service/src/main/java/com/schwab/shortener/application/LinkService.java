package com.schwab.shortener.application;

import com.schwab.shortener.domain.*;
import com.schwab.shortener.infrastructure.*;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinkService {
  private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private final LinkRepository links; private final ClickRecorder clickRecorder;
  public LinkService(LinkRepository links, ClickRecorder clickRecorder) { this.links = links; this.clickRecorder = clickRecorder; }
  @Transactional public Link create(String url, String alias, Instant expiresAt, String idempotencyKey) {
    validateUrl(url); if (expiresAt != null && !expiresAt.isAfter(Instant.now())) throw new InvalidRequest("expiresAt must be in the future");
    if (idempotencyKey != null && !idempotencyKey.isBlank()) { var prior = links.findByIdempotencyKey(idempotencyKey); if (prior.isPresent()) return prior.get(); }
    String candidate = alias == null || alias.isBlank() ? nextCode() : validateAlias(alias);
    for (int attempt = 0; attempt < 5; attempt++) {
      if (!links.existsByCode(candidate)) try { return links.saveAndFlush(new Link(candidate, url, expiresAt, blankToNull(idempotencyKey))); } catch (DataIntegrityViolationException collision) { if (alias != null && !alias.isBlank()) throw new Conflict("Alias is already in use"); }
      if (alias != null && !alias.isBlank()) throw new Conflict("Alias is already in use"); candidate = nextCode();
    }
    throw new ServiceUnavailable("Could not allocate a unique short code");
  }
  @Transactional(readOnly = true) public Link metadata(String code) { return active(code); }
  @Transactional(readOnly = true) public Link redirect(String code, String referrer) { Link link = active(code); clickRecorder.record(link.id(), referrer); return link; }
  @Transactional public void delete(String code) { active(code).delete(); }
  @Transactional(readOnly = true) public List<ClickEvent> events(String code) { return clickRecorder.events(active(code).id()); }
  private Link active(String code) { Link link = links.findByCode(code).orElseThrow(() -> new NotFound("Link not found")); if (link.deleted() || link.expired(Instant.now())) throw new NotFound("Link not found"); return link; }
  static void validateUrl(String raw) { try { URI uri = new URI(raw); if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || raw.length() > 2048) throw new InvalidRequest("URL must be an HTTP(S) URL no longer than 2048 characters"); String host = uri.getHost().toLowerCase(Locale.ROOT); if (host.equals("localhost") || host.endsWith(".localhost")) throw new InvalidRequest("Local destinations are not allowed"); for (InetAddress address : InetAddress.getAllByName(host)) if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isMulticastAddress()) throw new InvalidRequest("Private network destinations are not allowed"); } catch (URISyntaxException | UnknownHostException e) { throw new InvalidRequest("URL is invalid or cannot be resolved"); } }
  private String validateAlias(String alias) { if (!alias.matches("[A-Za-z0-9]{4,12}")) throw new InvalidRequest("Alias must be 4-12 base62 characters"); return alias; }
  private String nextCode() { StringBuilder result = new StringBuilder(8); for (int i = 0; i < 8; i++) result.append(ALPHABET.charAt(ThreadLocalRandom.current().nextInt(ALPHABET.length()))); return result.toString(); }
  private String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
}

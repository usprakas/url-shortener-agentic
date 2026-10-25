package com.schwab.shortener.infrastructure;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestControls extends OncePerRequestFilter {
  private final Map<String, Window> windows = new ConcurrentHashMap<>();
  @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
    String correlationId = Optional.ofNullable(request.getHeader("X-Correlation-Id")).filter(value -> value.length() <= 128).orElseGet(() -> UUID.randomUUID().toString());
    MDC.put("correlationId", correlationId); response.setHeader("X-Correlation-Id", correlationId);
    try { if (request.getRequestURI().equals("/api/v1/links") && request.getMethod().equals("POST") && !allow(request.getRemoteAddr())) { response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Rate limit exceeded"); return; } chain.doFilter(request, response); } finally { MDC.remove("correlationId"); }
  }
  private boolean allow(String client) { Window window = windows.compute(client, (key, prior) -> prior == null || prior.started.plusSeconds(60).isBefore(Instant.now()) ? new Window(Instant.now(), 1) : new Window(prior.started, prior.requests + 1)); return window.requests <= 60; }
  private record Window(Instant started, int requests) { }
}

package com.schwab.shortener.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc
class LinkControllerTest {
  @Autowired MockMvc mvc;
  @Test void createsMetadataRedirectsAndDeletesALink() throws Exception {
    String created = mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://example.com/docs\",\"alias\":\"docs2026\"}").header("Idempotency-Key", "create-docs"))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("docs2026")).andExpect(header().exists("X-Correlation-Id")).andReturn().getResponse().getContentAsString();
    mvc.perform(get("/api/v1/links/docs2026")).andExpect(status().isOk()).andExpect(jsonPath("$.originalUrl").value("https://example.com/docs"));
    mvc.perform(get("/docs2026").header("Referer", "https://referrer.example")).andExpect(status().isFound()).andExpect(redirectedUrl("https://example.com/docs"));
    mvc.perform(delete("/api/v1/links/docs2026")).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/links/docs2026")).andExpect(status().isNotFound()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }
  @Test void isIdempotentAndBlocksPrivateDestinations() throws Exception {
    String payload = "{\"url\":\"https://example.com/idempotent\"}";
    String one = mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(payload).header("Idempotency-Key", "same-request")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String two = mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(payload).header("Idempotency-Key", "same-request")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    org.junit.jupiter.api.Assertions.assertEquals(one, two);
    mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"http://localhost:8080/admin\"}")).andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }
}

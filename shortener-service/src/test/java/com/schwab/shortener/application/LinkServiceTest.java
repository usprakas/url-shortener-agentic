package com.schwab.shortener.application;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LinkServiceTest {
  @Test void rejectsNonWebAndLocalUrls() {
    assertThrows(InvalidRequest.class, () -> LinkService.validateUrl("ftp://example.com/file"));
    assertThrows(InvalidRequest.class, () -> LinkService.validateUrl("https://localhost/private"));
  }
}

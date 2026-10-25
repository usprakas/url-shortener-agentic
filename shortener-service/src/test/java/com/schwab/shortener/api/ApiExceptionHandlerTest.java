package com.schwab.shortener.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.schwab.shortener.application.Conflict;
import com.schwab.shortener.application.NotFound;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class ApiExceptionHandlerTest {

  private final ApiExceptionHandler handler = new ApiExceptionHandler();

  @Test
  void mapsNotFoundToATypedFourOhFour() {
    ProblemDetail problem = handler.handleNotFound(new NotFound("Link not found"));

    assertEquals(HttpStatus.NOT_FOUND.value(), problem.getStatus());
    assertEquals("Link not found", problem.getDetail());
  }

  @Test
  void mapsConflictToFourZeroNine() {
    ProblemDetail problem = handler.handleConflict(new Conflict("Alias is already in use"));

    assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
  }

  @Test
  void mapsMalformedRequestBodyToBadRequestInsteadOfServerError() {
    ProblemDetail problem = handler.handleMalformedBody(
        new org.springframework.http.converter.HttpMessageNotReadableException("not json", (org.springframework.http.HttpInputMessage) null));

    assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
  }

  @Test
  void mapsUnexpectedExceptionsToFiveHundredWithoutLeakingInternalDetail() {
    ProblemDetail problem = handler.handleUnexpected(new RuntimeException("jdbc url user=root password=supersecret"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem.getStatus());
    assertEquals("An unexpected error occurred", problem.getDetail());
    assertFalse(problem.getDetail().contains("supersecret"));
  }
}

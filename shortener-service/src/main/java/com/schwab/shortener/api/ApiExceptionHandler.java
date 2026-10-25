package com.schwab.shortener.api;

import com.schwab.shortener.application.Conflict;
import com.schwab.shortener.application.InvalidRequest;
import com.schwab.shortener.application.NotFound;
import com.schwab.shortener.application.ServiceUnavailable;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(NotFound.class)
  ProblemDetail handleNotFound(NotFound exception) {
    return problem(HttpStatus.NOT_FOUND, exception.getMessage());
  }

  @ExceptionHandler(Conflict.class)
  ProblemDetail handleConflict(Conflict exception) {
    return problem(HttpStatus.CONFLICT, exception.getMessage());
  }

  @ExceptionHandler(InvalidRequest.class)
  ProblemDetail handleInvalidRequest(InvalidRequest exception) {
    return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
  }

  @ExceptionHandler(ServiceUnavailable.class)
  ProblemDetail handleServiceUnavailable(ServiceUnavailable exception) {
    return problem(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail handleValidationFailure(MethodArgumentNotValidException exception) {
    return problem(HttpStatus.BAD_REQUEST, "Request failed validation");
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail handleMalformedBody(HttpMessageNotReadableException exception) {
    return problem(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpected(Exception exception) {
    log.error("Unhandled exception while serving a request", exception);
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
  }

  private ProblemDetail problem(HttpStatus status, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create("urn:shortener:" + status.value()));
    return problem;
  }
}

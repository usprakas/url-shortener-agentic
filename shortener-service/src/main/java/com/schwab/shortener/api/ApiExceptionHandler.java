package com.schwab.shortener.api;

import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
class ApiExceptionHandler {
  @ExceptionHandler(RuntimeException.class) ProblemDetail handle(RuntimeException exception) {
    HttpStatus status = exception.getClass().getSimpleName().equals("NotFound") ? HttpStatus.NOT_FOUND : exception.getClass().getSimpleName().equals("Conflict") ? HttpStatus.CONFLICT : exception.getClass().getSimpleName().equals("ServiceUnavailable") ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_REQUEST;
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage()); problem.setType(URI.create("urn:shortener:" + status.value())); return problem;
  }
}

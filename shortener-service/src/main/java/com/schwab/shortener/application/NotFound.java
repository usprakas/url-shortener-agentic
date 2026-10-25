package com.schwab.shortener.application;

public class NotFound extends RuntimeException {
  public NotFound(String message) {
    super(message);
  }
}

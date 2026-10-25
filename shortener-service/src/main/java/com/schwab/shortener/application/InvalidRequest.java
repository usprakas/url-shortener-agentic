package com.schwab.shortener.application;

public class InvalidRequest extends RuntimeException {
  public InvalidRequest(String message) {
    super(message);
  }
}

package com.schwab.shortener.application;

public class ServiceUnavailable extends RuntimeException {
  public ServiceUnavailable(String message) {
    super(message);
  }
}

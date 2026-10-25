package com.schwab.shortener.application;

public class Conflict extends RuntimeException {
  public Conflict(String message) {
    super(message);
  }
}

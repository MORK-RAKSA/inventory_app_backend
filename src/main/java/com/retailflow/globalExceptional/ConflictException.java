package com.retailflow.globalExceptional;

public class ConflictException extends RuntimeException {
  public ConflictException(String message) {
    super(message);
  }
}

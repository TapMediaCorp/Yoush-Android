package com.tapmedia.yoush.transport;

import java.io.IOException;

public class RetryLaterException extends Exception {
  public RetryLaterException() {}

  public RetryLaterException(Exception e) {
    super(e);
  }
}

package com.tapmedia.yoush.video;

public final class VideoSourceException extends Exception {

  VideoSourceException(String message) {
    super(message);
  }

  VideoSourceException(String message, Exception inner) {
    super(message, inner);
  }
}

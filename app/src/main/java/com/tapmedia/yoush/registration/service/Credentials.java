package com.tapmedia.yoush.registration.service;

import androidx.annotation.NonNull;

public final class Credentials {

  private final String e164number;
  private final String password;

  public Credentials(@NonNull String e164number, @NonNull String password) {
    this.e164number = e164number;
    this.password   = password;
  }

  public @NonNull String getE164number() {
    return e164number;
  }

  public @NonNull String getPassword() {
    return password;
  }
}

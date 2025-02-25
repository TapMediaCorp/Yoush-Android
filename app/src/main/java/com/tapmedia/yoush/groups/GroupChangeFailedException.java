package com.tapmedia.yoush.groups;

import androidx.annotation.NonNull;

public final class GroupChangeFailedException extends Exception {

  GroupChangeFailedException() {
  }

  GroupChangeFailedException(@NonNull Throwable throwable) {
    super(throwable);
  }

  GroupChangeFailedException(@NonNull String message) {
    super(message);
  }
}

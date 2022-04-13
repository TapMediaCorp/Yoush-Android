package com.tapmedia.yoush.groups.ui;

import androidx.annotation.NonNull;

import com.tapmedia.yoush.recipients.Recipient;

public interface RecipientLongClickListener {
  boolean onLongClick(@NonNull Recipient recipient);
}

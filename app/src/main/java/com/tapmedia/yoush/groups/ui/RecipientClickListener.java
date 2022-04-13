package com.tapmedia.yoush.groups.ui;

import androidx.annotation.NonNull;

import com.tapmedia.yoush.recipients.Recipient;

public interface RecipientClickListener {
  void onClick(@NonNull Recipient recipient);
}

package com.tapmedia.yoush.util;

import android.content.Context;
import android.text.style.ClickableSpan;
import android.view.View;

import androidx.annotation.NonNull;

import com.tapmedia.yoush.VerifyIdentityActivity;
import com.tapmedia.yoush.database.documents.IdentityKeyMismatch;
import com.tapmedia.yoush.recipients.RecipientId;
import org.whispersystems.libsignal.IdentityKey;

public class VerifySpan extends ClickableSpan {

  private final Context     context;
  private final RecipientId recipientId;
  private final IdentityKey identityKey;

  public VerifySpan(@NonNull Context context, @NonNull IdentityKeyMismatch mismatch) {
    this.context     = context;
    this.recipientId = mismatch.getRecipientId(context);
    this.identityKey = mismatch.getIdentityKey();
  }

  public VerifySpan(@NonNull Context context, @NonNull RecipientId recipientId, @NonNull IdentityKey identityKey) {
    this.context     = context;
    this.recipientId = recipientId;
    this.identityKey = identityKey;
  }

  @Override
  public void onClick(@NonNull View widget) {
    context.startActivity(VerifyIdentityActivity.newIntent(context, recipientId, identityKey, false));
  }
}

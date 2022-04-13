package com.tapmedia.yoush.groups.ui.addmembers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import com.tapmedia.yoush.contacts.SelectedContact;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.util.concurrent.SignalExecutors;

class AddMembersRepository {

  private final Context context;

  AddMembersRepository() {
    this.context = ApplicationDependencies.getApplication();
  }

  void getOrCreateRecipientId(@NonNull SelectedContact selectedContact, @NonNull Consumer<RecipientId> consumer) {
    SignalExecutors.BOUNDED.execute(() -> consumer.accept(selectedContact.getOrCreateRecipientId(context)));
  }
}

package com.tapmedia.yoush.groups.ui.addtogroup;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.groups.ui.GroupErrors;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.util.SingleLiveEvent;
import com.tapmedia.yoush.util.concurrent.SignalExecutors;

import java.util.List;
import java.util.Objects;

public final class AddToGroupViewModel extends ViewModel {

  private final Application            context;
  private final AddToGroupRepository   repository;
  private final RecipientId            recipientId;
  private final SingleLiveEvent<Event> events      = new SingleLiveEvent<>();

  private AddToGroupViewModel(@NonNull RecipientId recipientId) {
    this.context     = ApplicationDependencies.getApplication();
    this.recipientId = recipientId;
    this.repository  = new AddToGroupRepository();
  }

  public SingleLiveEvent<Event> getEvents() {
    return events;
  }

  void onContinueWithSelection(@NonNull List<RecipientId> groupRecipientIds) {
    if (groupRecipientIds.isEmpty()) {
      events.postValue(new Event.CloseEvent());
    } else if (groupRecipientIds.size() == 1) {
      SignalExecutors.BOUNDED.execute(() -> {
        Recipient groupRecipient = Recipient.resolved(groupRecipientIds.get(0));
        String    recipientName  = Recipient.resolved(recipientId).getDisplayName(context);
        String    groupName      = groupRecipient.getDisplayName(context);

        events.postValue(new Event.AddToSingleGroupConfirmationEvent(context.getResources().getString(R.string.AddToGroupActivity_add_member),
                                                                     context.getResources().getString(R.string.AddToGroupActivity_add_s_to_s, recipientName, groupName),
                                                                     groupRecipient, recipientName, groupName));
      });
    } else {
      throw new AssertionError("Does not support multi-select");
    }
  }

  void onAddToGroupsConfirmed(@NonNull Event.AddToSingleGroupConfirmationEvent event) {
    repository.add(recipientId,
                   event.groupRecipient,
                   error -> events.postValue(new Event.ToastEvent(context.getResources().getString(GroupErrors.getUserDisplayMessage(error)))),
                   () -> {
                     events.postValue(new Event.ToastEvent(context.getResources().getString(R.string.AddToGroupActivity_s_added_to_s, event.recipientName, event.groupName)));
                     events.postValue(new Event.CloseEvent());
                   });
  }

  static abstract class Event {

    static class CloseEvent extends Event {
    }

    static class ToastEvent extends Event {
      private final String message;

      ToastEvent(@NonNull String message) {
        this.message = message;
      }

      public String getMessage() {
        return message;
      }
    }

    static class AddToSingleGroupConfirmationEvent extends Event {
      private final String    title;
      private final String    message;
      private final Recipient groupRecipient;
      private final String    recipientName;
      private final String    groupName;

      AddToSingleGroupConfirmationEvent(@NonNull String title,
                                        @NonNull String message,
                                        @NonNull Recipient groupRecipient,
                                        @NonNull String recipientName,
                                        @NonNull String groupName)
      {
        this.title          = title;
        this.message        = message;
        this.groupRecipient = groupRecipient;
        this.recipientName  = recipientName;
        this.groupName      = groupName;
      }

      String getTitle() {
        return title;
      }

      String getMessage() {
        return message;
      }
    }
  }

  public static class Factory implements ViewModelProvider.Factory {

    private final RecipientId recipientId;

    public Factory(@NonNull RecipientId recipientId) {
      this.recipientId = recipientId;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return Objects.requireNonNull(modelClass.cast(new AddToGroupViewModel(recipientId)));
    }
  }
}

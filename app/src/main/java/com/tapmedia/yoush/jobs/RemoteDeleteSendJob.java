package com.tapmedia.yoush.jobs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import com.tapmedia.yoush.crypto.UnidentifiedAccessUtil;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.MessagingDatabase;
import com.tapmedia.yoush.database.NoSuchMessageException;
import com.tapmedia.yoush.database.model.MessageRecord;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobmanager.Data;
import com.tapmedia.yoush.jobmanager.Job;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.recipients.RecipientUtil;
import com.tapmedia.yoush.transport.RetryLaterException;
import com.tapmedia.yoush.util.GroupUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RemoteDeleteSendJob extends BaseJob {

  public static final String KEY = "RemoteDeleteSendJob";

  private static final String TAG = Log.tag(RemoteDeleteSendJob.class);

  private static final String KEY_MESSAGE_ID              = "message_id";
  private static final String KEY_IS_MMS                  = "is_mms";
  private static final String KEY_RECIPIENTS              = "recipients";
  private static final String KEY_INITIAL_RECIPIENT_COUNT = "initial_recipient_count";

  private final long              messageId;
  private final boolean           isMms;
  private final List<RecipientId> recipients;
  private final int               initialRecipientCount;


  @WorkerThread
  public static @NonNull RemoteDeleteSendJob create(@NonNull Context context,
                                                    long messageId,
                                                    boolean isMms)
      throws NoSuchMessageException
  {
    MessageRecord message = isMms ? DatabaseFactory.getMmsDatabase(context).getMessageRecord(messageId)
                                  : DatabaseFactory.getSmsDatabase(context).getMessage(messageId);

    Recipient conversationRecipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(message.getThreadId());

    if (conversationRecipient == null) {
      throw new AssertionError("We have a message, but couldn't find the thread!");
    }

    List<RecipientId> recipients = conversationRecipient.isGroup() ? Stream.of(conversationRecipient.getParticipants()).map(Recipient::getId).toList()
                                                                   : Stream.of(conversationRecipient.getId()).toList();

    recipients.remove(Recipient.self().getId());

    return new RemoteDeleteSendJob(messageId,
                               isMms,
                               recipients,
                               recipients.size(),
                               new Parameters.Builder()
                                             .setQueue(conversationRecipient.getId().toQueueKey())
                                             .setLifespan(TimeUnit.DAYS.toMillis(1))
                                             .setMaxAttempts(Parameters.UNLIMITED)
                                             .build());
  }

  private RemoteDeleteSendJob(long messageId,
                              boolean isMms,
                              @NonNull List<RecipientId> recipients,
                              int initialRecipientCount,
                              @NonNull Parameters parameters)
  {
    super(parameters);

    this.messageId             = messageId;
    this.isMms                 = isMms;
    this.recipients            = recipients;
    this.initialRecipientCount = initialRecipientCount;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_MESSAGE_ID, messageId)
                             .putBoolean(KEY_IS_MMS, isMms)
                             .putString(KEY_RECIPIENTS, RecipientId.toSerializedList(recipients))
                             .putInt(KEY_INITIAL_RECIPIENT_COUNT, initialRecipientCount)
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    MessagingDatabase db;
    MessageRecord     message;

    if (isMms) {
      db      = DatabaseFactory.getMmsDatabase(context);
      message = DatabaseFactory.getMmsDatabase(context).getMessageRecord(messageId);
    } else {
      db      = DatabaseFactory.getSmsDatabase(context);
      message = DatabaseFactory.getSmsDatabase(context).getMessage(messageId);
    }

    long       targetSentTimestamp  = message.getDateSent();
    Recipient conversationRecipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(message.getThreadId());

    if (conversationRecipient == null) {
      throw new AssertionError("We have a message, but couldn't find the thread!");
    }

    if (!message.isOutgoing()) {
      throw new IllegalStateException("Cannot delete a message that isn't yours!");
    }

    List<Recipient> destinations = Stream.of(recipients).map(Recipient::resolved).toList();
    List<Recipient> completions  = deliver(conversationRecipient, destinations, targetSentTimestamp, true);

    for (Recipient completion : completions) {
      recipients.remove(completion.getId());
    }

    Log.i(TAG, "Completed now: " + completions.size() + ", Remaining: " + recipients.size());

    if (recipients.isEmpty()) {
      db.markAsSent(messageId, true);
    } else {
      Log.w(TAG, "Still need to send to " + recipients.size() + " recipients. Retrying.");
      throw new RetryLaterException();
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof IOException ||
           e instanceof RetryLaterException;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Failed to send remote delete to all recipients! (" + (initialRecipientCount - recipients.size() + "/" + initialRecipientCount + ")") );
  }

  private @NonNull List<Recipient> deliver(@NonNull Recipient conversationRecipient, @NonNull List<Recipient> destinations, long targetSentTimestamp, boolean isSilent)
      throws IOException, UntrustedIdentityException
  {
    SignalServiceMessageSender             messageSender      = ApplicationDependencies.getSignalServiceMessageSender();
    List<SignalServiceAddress>             addresses          = Stream.of(destinations).map(t -> RecipientUtil.toSignalServiceAddress(context, t)).toList();
    List<Optional<UnidentifiedAccessPair>> unidentifiedAccess = Stream.of(destinations).map(recipient -> UnidentifiedAccessUtil.getAccessFor(context, recipient)).toList();
    SignalServiceDataMessage.Builder       dataMessage        = SignalServiceDataMessage.newBuilder()
                                                                                        .withTimestamp(System.currentTimeMillis())
                                                                                        .withRemoteDelete(new SignalServiceDataMessage.RemoteDelete(targetSentTimestamp));

    if (conversationRecipient.isGroup()) {
      GroupUtil.setDataMessageGroupContext(context, dataMessage, conversationRecipient.requireGroupId().requirePush());
    }

    List<SendMessageResult> results = messageSender.sendMessage(addresses, unidentifiedAccess, false, dataMessage.build(), isSilent);

    Stream.of(results)
          .filter(r -> r.getIdentityFailure() != null)
          .map(SendMessageResult::getAddress)
          .map(a -> Recipient.externalPush(context, a))
          .forEach(r -> Log.w(TAG, "Identity failure for " + r.getId()));

    Stream.of(results)
          .filter(SendMessageResult::isUnregisteredFailure)
          .map(SendMessageResult::getAddress)
          .map(a -> Recipient.externalPush(context, a))
          .forEach(r -> Log.w(TAG, "Unregistered failure for " + r.getId()));

    return Stream.of(results)
                 .filter(r -> r.getSuccess() != null || r.getIdentityFailure() != null || r.isUnregisteredFailure())
                 .map(SendMessageResult::getAddress)
                 .map(a -> Recipient.externalPush(context, a))
                 .toList();
  }

  public static class Factory implements Job.Factory<RemoteDeleteSendJob> {

    @Override
    public @NonNull RemoteDeleteSendJob create(@NonNull Parameters parameters, @NonNull Data data) {
      long              messageId             = data.getLong(KEY_MESSAGE_ID);
      boolean           isMms                 = data.getBoolean(KEY_IS_MMS);
      List<RecipientId> recipients            = RecipientId.fromSerializedList(data.getString(KEY_RECIPIENTS));
      int               initialRecipientCount = data.getInt(KEY_INITIAL_RECIPIENT_COUNT);

      return new RemoteDeleteSendJob(messageId, isMms, recipients, initialRecipientCount, parameters);
    }
  }
}

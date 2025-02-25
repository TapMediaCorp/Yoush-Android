package com.tapmedia.yoush.jobs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.tapmedia.yoush.ApplicationContext;
import com.tapmedia.yoush.attachments.Attachment;
import com.tapmedia.yoush.conversation.background.BackgroundJobSend;
import com.tapmedia.yoush.crypto.UnidentifiedAccessUtil;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.GroupDatabase;
import com.tapmedia.yoush.database.GroupReceiptDatabase.GroupReceiptInfo;
import com.tapmedia.yoush.database.MmsDatabase;
import com.tapmedia.yoush.database.NoSuchMessageException;
import com.tapmedia.yoush.database.documents.IdentityKeyMismatch;
import com.tapmedia.yoush.database.documents.NetworkFailure;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.groups.GroupId;
import com.tapmedia.yoush.jobmanager.Data;
import com.tapmedia.yoush.jobmanager.Job;
import com.tapmedia.yoush.jobmanager.JobLogger;
import com.tapmedia.yoush.jobmanager.JobManager;
import com.tapmedia.yoush.jobmanager.impl.NetworkConstraint;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.mms.MessageGroupContext;
import com.tapmedia.yoush.mms.MmsException;
import com.tapmedia.yoush.mms.OutgoingGroupUpdateMessage;
import com.tapmedia.yoush.mms.OutgoingMediaMessage;
import com.tapmedia.yoush.mms.SlideDeck;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.recipients.RecipientUtil;
import com.tapmedia.yoush.transport.RetryLaterException;
import com.tapmedia.yoush.transport.UndeliverableMessageException;
import com.tapmedia.yoush.util.DevLogger;
import com.tapmedia.yoush.util.GroupUtil;
import com.google.protobuf.ByteString;

import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage.Preview;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage.Quote;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContextV2;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PushGroupSendJob extends PushSendJob {

  public static final String KEY = "PushGroupSendJob";

  private static final String TAG = PushGroupSendJob.class.getSimpleName();

  private static final String KEY_MESSAGE_ID       = "message_id";
  private static final String KEY_FILTER_RECIPIENT = "filter_recipient";
  private static final String CALL_OBJECT       = "call_object";

  private long        messageId;
  private RecipientId filterRecipient;
  private String      callObject;

  public PushGroupSendJob(long messageId, @NonNull RecipientId destination, @Nullable RecipientId filterRecipient, boolean hasMedia) {
    this(new Job.Parameters.Builder()
                           .setQueue(destination.toQueueKey(hasMedia))
                           .addConstraint(NetworkConstraint.KEY)
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build(),
         messageId, filterRecipient);

  }

  public PushGroupSendJob(long messageId, @NonNull RecipientId destination, @Nullable RecipientId filterRecipient, boolean hasMedia, String callObject) {
    this(new Job.Parameters.Builder()
                    .setQueue(destination.toQueueKey(hasMedia))
                    .addConstraint(NetworkConstraint.KEY)
                    .setLifespan(TimeUnit.DAYS.toMillis(1))
                    .setMaxAttempts(Parameters.UNLIMITED)
                    .build(),
            messageId, filterRecipient, callObject);

  }

  public PushGroupSendJob(@NonNull Job.Parameters parameters, long messageId, @Nullable RecipientId filterRecipient) {
    super(parameters);


    if (this.callObject != null) {
      new PushGroupSendJob(parameters, messageId, filterRecipient, this.callObject);
    } else {
      this.messageId       = messageId;
      this.filterRecipient = filterRecipient;
    }
  }

  private PushGroupSendJob(@NonNull Job.Parameters parameters, long messageId, @Nullable RecipientId filterRecipient, String callObject) {
    super(parameters);

    this.messageId       = messageId;
    this.filterRecipient = filterRecipient;
    this.callObject = callObject;
  }

  @WorkerThread
  public static void enqueue(@NonNull Context context,
                             @NonNull JobManager jobManager,
                             long messageId,
                             @NonNull RecipientId destination,
                             @Nullable RecipientId filterAddress)
  {
    try {
      Recipient group = Recipient.resolved(destination);
      if (!group.isPushGroup()) {
        throw new AssertionError("Not a group!");
      }

      if (!DatabaseFactory.getGroupDatabase(context).isActive(group.requireGroupId())) {
        throw new MmsException("Inactive group!");
      }

      MmsDatabase            database                    = DatabaseFactory.getMmsDatabase(context);
      OutgoingMediaMessage   message                     = database.getOutgoingMessage(messageId);
      Set<String>            attachmentUploadIds         = enqueueCompressingAndUploadAttachmentsChains(jobManager, message);

      jobManager.add(new PushGroupSendJob(messageId, destination, filterAddress, !attachmentUploadIds.isEmpty()), attachmentUploadIds, attachmentUploadIds.isEmpty() ? null : destination.toQueueKey());

    } catch (NoSuchMessageException | MmsException e) {
      Log.w(TAG, "Failed to enqueue message.", e);
      DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @WorkerThread
  public static void enqueueCallGroup(@NonNull Context context,
                             @NonNull JobManager jobManager,
                             long messageId,
                             @NonNull RecipientId destination,
                             @Nullable RecipientId filterAddress,
                                      String callObject)
  {
    try {
      Recipient group = Recipient.resolved(destination);
      if (!group.isPushGroup()) {
        throw new AssertionError("Not a group!");
      }

      if (!DatabaseFactory.getGroupDatabase(context).isActive(group.requireGroupId())) {
        throw new MmsException("Inactive group!");
      }

      MmsDatabase            database                    = DatabaseFactory.getMmsDatabase(context);
      OutgoingMediaMessage   message                     = database.getOutgoingMessage(messageId);
      Set<String>            attachmentUploadIds         = enqueueCompressingAndUploadAttachmentsChains(jobManager, message);

      jobManager.add(new PushGroupSendJob(messageId, destination, filterAddress, !attachmentUploadIds.isEmpty(), callObject), attachmentUploadIds, attachmentUploadIds.isEmpty() ? null : destination.toQueueKey());

    } catch (NoSuchMessageException | MmsException e) {
      Log.w(TAG, "Failed to enqueue message.", e);
      DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_MESSAGE_ID, messageId)
                             .putString(KEY_FILTER_RECIPIENT, filterRecipient != null ? filterRecipient.serialize() : null).putString(CALL_OBJECT, callObject)
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onAdded() {
    DatabaseFactory.getMmsDatabase(context).markAsSending(messageId);
  }

  @Override
  public void onPushSend()
      throws IOException, MmsException, NoSuchMessageException,  RetryLaterException
  {
    MmsDatabase               database                   = DatabaseFactory.getMmsDatabase(context);

    OutgoingMediaMessage      messageOut                    = database.getOutgoingMessage(messageId);
    OutgoingMediaMessage      message       = null;

    if (callObject != null) {
      message = new OutgoingMediaMessage(messageOut.getRecipient(), new SlideDeck(), callObject, messageOut.getSentTimeMillis(), messageOut.getSubscriptionId(), messageOut.getExpiresIn(), messageOut.isViewOnce(), messageOut.getDistributionType(), messageOut.getOutgoingQuote(), messageOut.getSharedContacts(), messageOut.getLinkPreviews(), "");
    } else {
      message = messageOut;
    }




    List<NetworkFailure>      existingNetworkFailures    = message.getNetworkFailures();
    List<IdentityKeyMismatch> existingIdentityMismatches = message.getIdentityKeyMismatches();

    long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(message.getRecipient());
    ApplicationDependencies.getJobManager().cancelAllInQueue(TypingSendJob.getQueue(threadId));

    if (database.isSent(messageId)) {
      log(TAG, "Message " + messageId + " was already sent. Ignoring.");
      return;
    }

    Recipient groupRecipient = message.getRecipient().fresh();

    if (!groupRecipient.isPushGroup()) {
      throw new MmsException("Message recipient isn't a group!");
    }

    try {
      log(TAG, "Sending message: " + messageId);

      if (!groupRecipient.resolve().isProfileSharing() && !database.isGroupQuitMessage(messageId)) {
        RecipientUtil.shareProfileIfFirstSecureMessage(context, groupRecipient);
      }

      List<Recipient> target;

      if      (filterRecipient != null)            target = Collections.singletonList(Recipient.resolved(filterRecipient));
      else if (!existingNetworkFailures.isEmpty()) target = Stream.of(existingNetworkFailures).map(nf -> Recipient.resolved(nf.getRecipientId(context))).toList();
      else                                         target = getGroupMessageRecipients(groupRecipient.requireGroupId(), messageId);

      Map<String, Recipient> idByE164 = Stream.of(target).filter(Recipient::hasE164).collect(Collectors.toMap(Recipient::requireE164, r -> r));
      Map<UUID, Recipient>   idByUuid = Stream.of(target).filter(Recipient::hasUuid).collect(Collectors.toMap(Recipient::requireUuid, r -> r));

      List<SendMessageResult>   results = deliver(message, groupRecipient, target);
      Log.i(TAG, JobLogger.format(this, "Finished send."));

      List<NetworkFailure>             networkFailures           = Stream.of(results).filter(SendMessageResult::isNetworkFailure).map(result -> new NetworkFailure(findId(result.getAddress(), idByE164, idByUuid))).toList();
      List<IdentityKeyMismatch>        identityMismatches        = Stream.of(results).filter(result -> result.getIdentityFailure() != null).map(result -> new IdentityKeyMismatch(findId(result.getAddress(), idByE164, idByUuid), result.getIdentityFailure().getIdentityKey())).toList();
      List<SendMessageResult>          successes                 = Stream.of(results).filter(result -> result.getSuccess() != null).toList();
      List<Pair<RecipientId, Boolean>> successUnidentifiedStatus = Stream.of(successes).map(result -> new Pair<>(findId(result.getAddress(), idByE164, idByUuid), result.getSuccess().isUnidentified())).toList();
      Set<RecipientId>                 successIds                = Stream.of(successUnidentifiedStatus).map(Pair::first).collect(Collectors.toSet());
      List<NetworkFailure>             resolvedNetworkFailures   = Stream.of(existingNetworkFailures).filter(failure -> successIds.contains(failure.getRecipientId(context))).toList();
      List<IdentityKeyMismatch>        resolvedIdentityFailures = Stream.of(existingIdentityMismatches).filter(failure -> successIds.contains(failure.getRecipientId(context))).toList();

      for (NetworkFailure resolvedFailure : resolvedNetworkFailures) {
        database.removeFailure(messageId, resolvedFailure);
        existingNetworkFailures.remove(resolvedFailure);
      }

      for (IdentityKeyMismatch resolvedIdentity : resolvedIdentityFailures) {
        database.removeMismatchedIdentity(messageId, resolvedIdentity.getRecipientId(context), resolvedIdentity.getIdentityKey());
        existingIdentityMismatches.remove(resolvedIdentity);
      }

      if (!networkFailures.isEmpty()) {
        database.addFailures(messageId, networkFailures);
      }

      for (IdentityKeyMismatch mismatch : identityMismatches) {
        database.addMismatchedIdentity(messageId, mismatch.getRecipientId(context), mismatch.getIdentityKey());
      }

      DatabaseFactory.getGroupReceiptDatabase(context).setUnidentified(successUnidentifiedStatus, messageId);

      if (existingNetworkFailures.isEmpty() && networkFailures.isEmpty() && identityMismatches.isEmpty() && existingIdentityMismatches.isEmpty()) {
        database.markAsSent(messageId, true);

        markAttachmentsUploaded(messageId, message.getAttachments());

        if (message.getExpiresIn() > 0 && !message.isExpirationUpdate()) {
          database.markExpireStarted(messageId);
          ApplicationContext.getInstance(context)
                  .getExpiringMessageManager()
                  .scheduleDeletion(messageId, true, message.getExpiresIn());
        }

        if (message.isViewOnce()) {
          DatabaseFactory.getAttachmentDatabase(context).deleteAttachmentFilesForViewOnceMessage(messageId);
        }
        BackgroundJobSend.updateMessage(messageId);
        BackgroundJobSend.sendBackgroundForNewMember(message, threadId);

      } else if (!networkFailures.isEmpty()) {
        throw new RetryLaterException();
      } else if (!identityMismatches.isEmpty()) {
        database.markAsSentFailed(messageId);
        notifyMediaMessageDeliveryFailed(context, messageId);

        List<RecipientId>  mismatchRecipientIds = Stream.of(identityMismatches).map(mismatch -> mismatch.getRecipientId(context)).toList();
        RetrieveProfileJob.enqueue(mismatchRecipientIds);
      }
    } catch (UntrustedIdentityException | UndeliverableMessageException e) {
      warn(TAG, e);
      database.markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof IOException)         return true;
    if (exception instanceof RetryLaterException) return true;
    return false;
  }

  @Override
  public void onFailure() {
    DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
  }

  private static @NonNull RecipientId findId(@NonNull SignalServiceAddress address,
                                             @NonNull Map<String, Recipient> byE164,
                                             @NonNull Map<UUID, Recipient> byUuid)
  {
    if (address.getNumber().isPresent() && byE164.containsKey(address.getNumber().get())) {
      return Objects.requireNonNull(byE164.get(address.getNumber().get())).getId();
    } else if (address.getUuid().isPresent() && byUuid.containsKey(address.getUuid().get())) {
      return Objects.requireNonNull(byUuid.get(address.getUuid().get())).getId();
    } else {
      throw new IllegalStateException("Found an address that was never provided!");
    }
  }

  private List<SendMessageResult> deliver(OutgoingMediaMessage message, @NonNull Recipient groupRecipient, @NonNull List<Recipient> destinations)
      throws IOException, UntrustedIdentityException, UndeliverableMessageException {
    rotateSenderCertificateIfNecessary();

    SignalServiceMessageSender                 messageSender      = ApplicationDependencies.getSignalServiceMessageSender();
    GroupId.Push                               groupId            = groupRecipient.requireGroupId().requirePush();
    Optional<byte[]>                           profileKey         = getProfileKey(groupRecipient);
    Optional<Quote>                            quote              = getQuoteFor(message);
    Optional<SignalServiceDataMessage.Sticker> sticker            = getStickerFor(message);
    List<SharedContact>                        sharedContacts     = getSharedContactsFor(message);
    List<Preview>                              previews           = getPreviewsFor(message);
    List<SignalServiceAddress>                 addresses          = Stream.of(destinations).map(this::getPushAddress).toList();
    List<Attachment>                           attachments        = Stream.of(message.getAttachments()).filterNot(Attachment::isSticker).toList();
    List<SignalServiceAttachment>              attachmentPointers = getAttachmentPointersFor(attachments);
    boolean                                    isRecipientUpdate  = destinations.size() != DatabaseFactory.getGroupReceiptDatabase(context).getGroupReceiptInfo(messageId).size();

    List<Optional<UnidentifiedAccessPair>> unidentifiedAccess = Stream.of(destinations)
                                                                      .map(recipient -> UnidentifiedAccessUtil.getAccessFor(context, recipient))
                                                                      .toList();

    if (message.isGroup()) {
      OutgoingGroupUpdateMessage groupMessage = (OutgoingGroupUpdateMessage) message;

      if (groupMessage.isV2Group()) {
        MessageGroupContext.GroupV2Properties properties   = groupMessage.requireGroupV2Properties();
        GroupContextV2                        groupContext = properties.getGroupContext();
        SignalServiceGroupV2.Builder          builder      = SignalServiceGroupV2.newBuilder(properties.getGroupMasterKey())
                                                                                 .withRevision(groupContext.getRevision());

        ByteString groupChange = groupContext.getGroupChange();
        if (groupChange != null) {
          builder.withSignedGroupChange(groupChange.toByteArray());
        }

        SignalServiceGroupV2     group            = builder.build();
        SignalServiceDataMessage groupDataMessage = SignalServiceDataMessage.newBuilder()
                                                                            .withTimestamp(message.getSentTimeMillis())
                                                                            .withExpiration(groupRecipient.getExpireMessages())
                                                                            .asGroupMessage(group)
                                                                            .build();

        List<SendMessageResult>  result = messageSender.sendMessage(addresses, unidentifiedAccess, isRecipientUpdate, groupDataMessage,false);
        return result;

      } else {
        MessageGroupContext.GroupV1Properties properties = groupMessage.requireGroupV1Properties();

        GroupContext               groupContext     = properties.getGroupContext();
        SignalServiceAttachment    avatar           = attachmentPointers.isEmpty() ? null : attachmentPointers.get(0);
        SignalServiceGroup.Type    type             = properties.isQuit() ? SignalServiceGroup.Type.QUIT : SignalServiceGroup.Type.UPDATE;
        List<SignalServiceAddress> members          = Stream.of(groupContext.getMembersList())
                                                            .map(m -> new SignalServiceAddress(UuidUtil.parseOrNull(m.getUuid()), m.getE164()))
                                                            .toList();
        SignalServiceGroup         group            = new SignalServiceGroup(type, groupId.getDecodedId(), groupContext.getName(), members, avatar);
        SignalServiceDataMessage   groupDataMessage = SignalServiceDataMessage.newBuilder()
                                                                              .withTimestamp(message.getSentTimeMillis())
                                                                              .withExpiration(message.getRecipient().getExpireMessages())
                                                                              .asGroupMessage(group)
                                                                              .build();

        Log.i(TAG, JobLogger.format(this, "Beginning update send."));

        List<SendMessageResult>  result = messageSender.sendMessage(addresses, unidentifiedAccess, isRecipientUpdate, groupDataMessage,false);
        return result;

      }
    } else {
      SignalServiceDataMessage.Builder builder = SignalServiceDataMessage.newBuilder()
                                                                         .withTimestamp(message.getSentTimeMillis());

      GroupUtil.setDataMessageGroupContext(context, builder, groupId);

      SignalServiceDataMessage groupMessage = builder.withAttachments(attachmentPointers)
                                                     .withBody(message.getBody())
                                                     .withExpiration((int)(message.getExpiresIn() / 1000))
                                                     .withViewOnce(message.isViewOnce())
                                                     .asExpirationUpdate(message.isExpirationUpdate())
                                                     .withProfileKey(profileKey.orNull())
                                                     .withQuote(quote.orNull())
                                                     .withSticker(sticker.orNull())
                                                     .withSharedContacts(sharedContacts)
                                                     .withPreviews(previews)
                                                     .build();

      Log.i(TAG, JobLogger.format(this, "Beginning message send."));
      return messageSender.sendMessage(addresses, unidentifiedAccess, isRecipientUpdate, groupMessage, false);
    }
  }

  private @NonNull List<Recipient> getGroupMessageRecipients(@NonNull GroupId groupId, long messageId) {
    List<GroupReceiptInfo> destinations = DatabaseFactory.getGroupReceiptDatabase(context).getGroupReceiptInfo(messageId);

    if (!destinations.isEmpty()) {
      return Stream.of(destinations).map(GroupReceiptInfo::getRecipientId).map(Recipient::resolved).toList();
    }

    List<Recipient> members = Stream.of(DatabaseFactory.getGroupDatabase(context)
                                                       .getGroupMembers(groupId, GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF))
                                    .map(Recipient::resolve)
                                    .toList();

    if (members.size() > 0) {
      Log.w(TAG, "No destinations found for group message " + groupId + " using current group membership");
    }
    
    return members;
  }

  public static class Factory implements Job.Factory<PushGroupSendJob> {
    @Override
    public @NonNull PushGroupSendJob create(@NonNull Parameters parameters, @NonNull com.tapmedia.yoush.jobmanager.Data data) {
      String      raw    = data.getString(KEY_FILTER_RECIPIENT);
      RecipientId filter = raw != null ? RecipientId.from(raw) : null;

      return new PushGroupSendJob(parameters, data.getLong(KEY_MESSAGE_ID), filter, data.getString(CALL_OBJECT));
    }
  }
}

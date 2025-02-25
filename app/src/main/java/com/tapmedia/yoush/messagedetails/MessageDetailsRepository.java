package com.tapmedia.yoush.messagedetails;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.GroupDatabase;
import com.tapmedia.yoush.database.GroupReceiptDatabase;
import com.tapmedia.yoush.database.documents.IdentityKeyMismatch;
import com.tapmedia.yoush.database.documents.NetworkFailure;
import com.tapmedia.yoush.database.model.MessageRecord;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.util.concurrent.SignalExecutors;

import java.util.LinkedList;
import java.util.List;

final class MessageDetailsRepository {

  private final Context context = ApplicationDependencies.getApplication();

  @NonNull LiveData<MessageRecord> getMessageRecord(String type, Long messageId) {
    return new MessageRecordLiveData(context, type, messageId);
  }

  @NonNull LiveData<MessageDetails> getMessageDetails(@Nullable MessageRecord messageRecord) {
    final MutableLiveData<MessageDetails> liveData = new MutableLiveData<>();

    if (messageRecord != null) {
      SignalExecutors.BOUNDED.execute(() -> liveData.postValue(getRecipientDeliveryStatusesInternal(messageRecord)));
    } else {
      liveData.setValue(null);
    }

    return liveData;
  }

  @WorkerThread
  private @NonNull MessageDetails getRecipientDeliveryStatusesInternal(@NonNull MessageRecord messageRecord) {
    List<RecipientDeliveryStatus> recipients = new LinkedList<>();

    if (!messageRecord.getRecipient().isGroup()) {
      recipients.add(new RecipientDeliveryStatus(messageRecord,
                                                 messageRecord.getRecipient(),
                                                 getStatusFor(messageRecord),
                                                 messageRecord.isUnidentified(),
                                                 -1,
                                                 getNetworkFailure(messageRecord, messageRecord.getRecipient()),
                                                 getKeyMismatchFailure(messageRecord, messageRecord.getRecipient())));
    } else {
      List<GroupReceiptDatabase.GroupReceiptInfo> receiptInfoList = DatabaseFactory.getGroupReceiptDatabase(context).getGroupReceiptInfo(messageRecord.getId());

      if (receiptInfoList.isEmpty()) {
        List<Recipient> group = DatabaseFactory.getGroupDatabase(context).getGroupMembers(messageRecord.getRecipient().requireGroupId(), GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);

        for (Recipient recipient : group) {
          recipients.add(new RecipientDeliveryStatus(messageRecord,
                                                     recipient,
                                                     RecipientDeliveryStatus.Status.UNKNOWN,
                                                     false,
                                                     -1,
                                                     getNetworkFailure(messageRecord, recipient),
                                                     getKeyMismatchFailure(messageRecord, recipient)));
        }
      } else {
        for (GroupReceiptDatabase.GroupReceiptInfo info : receiptInfoList) {
          Recipient           recipient        = Recipient.resolved(info.getRecipientId());
          NetworkFailure      failure          = getNetworkFailure(messageRecord, recipient);
          IdentityKeyMismatch mismatch         = getKeyMismatchFailure(messageRecord, recipient);
          boolean             recipientFailure = failure != null || mismatch != null;

          recipients.add(new RecipientDeliveryStatus(messageRecord,
                                                     recipient,
                                                     getStatusFor(info.getStatus(), messageRecord.isPending(), recipientFailure),
                                                     info.isUnidentified(),
                                                     info.getTimestamp(),
                                                     failure,
                                                     mismatch));
        }
      }
    }

    return new MessageDetails(messageRecord, recipients);
  }

  private @Nullable NetworkFailure getNetworkFailure(MessageRecord messageRecord, Recipient recipient) {
    if (messageRecord.hasNetworkFailures()) {
      for (final NetworkFailure failure : messageRecord.getNetworkFailures()) {
        if (failure.getRecipientId(context).equals(recipient.getId())) {
          return failure;
        }
      }
    }
    return null;
  }

  private @Nullable IdentityKeyMismatch getKeyMismatchFailure(MessageRecord messageRecord, Recipient recipient) {
    if (messageRecord.isIdentityMismatchFailure()) {
      for (final IdentityKeyMismatch mismatch : messageRecord.getIdentityKeyMismatches()) {
        if (mismatch.getRecipientId(context).equals(recipient.getId())) {
          return mismatch;
        }
      }
    }
    return null;
  }

  private @NonNull RecipientDeliveryStatus.Status getStatusFor(MessageRecord messageRecord) {
    if (messageRecord.isRemoteRead()) return RecipientDeliveryStatus.Status.READ;
    if (messageRecord.isDelivered())  return RecipientDeliveryStatus.Status.DELIVERED;
    if (messageRecord.isSent())       return RecipientDeliveryStatus.Status.SENT;
    if (messageRecord.isPending())    return RecipientDeliveryStatus.Status.PENDING;

    return RecipientDeliveryStatus.Status.UNKNOWN;
  }

  private @NonNull RecipientDeliveryStatus.Status getStatusFor(int groupStatus, boolean pending, boolean failed) {
    if      (groupStatus == GroupReceiptDatabase.STATUS_READ)                    return RecipientDeliveryStatus.Status.READ;
    else if (groupStatus == GroupReceiptDatabase.STATUS_DELIVERED)               return RecipientDeliveryStatus.Status.DELIVERED;
    else if (groupStatus == GroupReceiptDatabase.STATUS_UNDELIVERED && failed)   return RecipientDeliveryStatus.Status.UNKNOWN;
    else if (groupStatus == GroupReceiptDatabase.STATUS_UNDELIVERED && !pending) return RecipientDeliveryStatus.Status.SENT;
    else if (groupStatus == GroupReceiptDatabase.STATUS_UNDELIVERED)             return RecipientDeliveryStatus.Status.PENDING;
    else if (groupStatus == GroupReceiptDatabase.STATUS_UNKNOWN)                 return RecipientDeliveryStatus.Status.UNKNOWN;
    throw new AssertionError();
  }
}

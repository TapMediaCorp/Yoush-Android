package com.tapmedia.yoush.conversation.ui.error;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.annimon.stream.Stream;

import com.tapmedia.yoush.crypto.storage.TextSecureIdentityKeyStore;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.IdentityDatabase;
import com.tapmedia.yoush.database.IdentityDatabase.IdentityRecord;
import com.tapmedia.yoush.database.MmsDatabase;
import com.tapmedia.yoush.database.SmsDatabase;
import com.tapmedia.yoush.database.model.MessageRecord;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.sms.MessageSender;
import com.tapmedia.yoush.util.concurrent.SignalExecutors;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.SignalProtocolAddress;

import java.util.List;

import static org.whispersystems.libsignal.SessionCipher.SESSION_LOCK;

final class SafetyNumberChangeRepository {

  private final Context context;

  SafetyNumberChangeRepository(Context context) {
    this.context = context.getApplicationContext();
  }

  @NonNull LiveData<SafetyNumberChangeState> getSafetyNumberChangeState(@NonNull List<RecipientId> recipientIds, @Nullable Long messageId) {
    MutableLiveData<SafetyNumberChangeState> liveData = new MutableLiveData<>();
    SignalExecutors.BOUNDED.execute(() -> liveData.postValue(getSafetyNumberChangeStateInternal(recipientIds, messageId)));
    return liveData;
  }

  @NonNull LiveData<TrustAndVerifyResult> trustOrVerifyChangedRecipients(@NonNull List<ChangedRecipient> changedRecipients) {
    MutableLiveData<TrustAndVerifyResult> liveData = new MutableLiveData<>();
    SignalExecutors.BOUNDED.execute(() -> liveData.postValue(trustOrVerifyChangedRecipientsInternal(changedRecipients)));
    return liveData;
  }

  @NonNull LiveData<TrustAndVerifyResult> trustOrVerifyChangedRecipientsAndResend(@NonNull List<ChangedRecipient> changedRecipients, @NonNull MessageRecord messageRecord) {
    MutableLiveData<TrustAndVerifyResult> liveData = new MutableLiveData<>();
    SignalExecutors.BOUNDED.execute(() -> liveData.postValue(trustOrVerifyChangedRecipientsAndResendInternal(changedRecipients, messageRecord)));
    return liveData;
  }

  @WorkerThread
  private @NonNull SafetyNumberChangeState getSafetyNumberChangeStateInternal(@NonNull List<RecipientId> recipientIds, @Nullable Long messageId) {
    MessageRecord messageRecord = null;
    if (messageId != null) {
      messageRecord = DatabaseFactory.getMmsSmsDatabase(context).getMessageRecord(messageId);
    }

    List<Recipient> recipients = Stream.of(recipientIds).map(Recipient::resolved).toList();

    List<ChangedRecipient> changedRecipients = Stream.of(DatabaseFactory.getIdentityDatabase(context).getIdentities(recipients).getIdentityRecords())
                                                     .map(record -> new ChangedRecipient(Recipient.resolved(record.getRecipientId()), record))
                                                     .toList();

    return new SafetyNumberChangeState(changedRecipients, messageRecord);
  }

  @WorkerThread
  private TrustAndVerifyResult trustOrVerifyChangedRecipientsInternal(@NonNull List<ChangedRecipient> changedRecipients) {
    IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(context);

    synchronized (SESSION_LOCK) {
      for (ChangedRecipient changedRecipient : changedRecipients) {
        IdentityRecord identityRecord = changedRecipient.getIdentityRecord();

        if (changedRecipient.isUnverified()) {
          identityDatabase.setVerified(identityRecord.getRecipientId(),
                                       identityRecord.getIdentityKey(),
                                       IdentityDatabase.VerifiedStatus.DEFAULT);
        } else {
          identityDatabase.setApproval(identityRecord.getRecipientId(), true);
        }
      }
    }

    return TrustAndVerifyResult.TRUST_AND_VERIFY;
  }

  @WorkerThread
  private TrustAndVerifyResult trustOrVerifyChangedRecipientsAndResendInternal(@NonNull List<ChangedRecipient> changedRecipients,
                                                                               @NonNull MessageRecord messageRecord) {
    synchronized (SESSION_LOCK) {
      for (ChangedRecipient changedRecipient : changedRecipients) {
        SignalProtocolAddress      mismatchAddress  = new SignalProtocolAddress(changedRecipient.getRecipient().requireServiceId(), 1);
        TextSecureIdentityKeyStore identityKeyStore = new TextSecureIdentityKeyStore(context);
        identityKeyStore.saveIdentity(mismatchAddress, changedRecipient.getIdentityRecord().getIdentityKey(), true);
      }
    }

    if (messageRecord.isOutgoing()) {
      processOutgoingMessageRecord(changedRecipients, messageRecord);
    }

    return TrustAndVerifyResult.TRUST_VERIFY_AND_RESEND;
  }

  @WorkerThread
  private void processOutgoingMessageRecord(@NonNull List<ChangedRecipient> changedRecipients, @NonNull MessageRecord messageRecord) {
    SmsDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);
    MmsDatabase mmsDatabase = DatabaseFactory.getMmsDatabase(context);

    for (ChangedRecipient changedRecipient : changedRecipients) {
      RecipientId id          = changedRecipient.getRecipient().getId();
      IdentityKey identityKey = changedRecipient.getIdentityRecord().getIdentityKey();

      if (messageRecord.isMms()) {
        mmsDatabase.removeMismatchedIdentity(messageRecord.getId(), id, identityKey);

        if (messageRecord.getRecipient().isPushGroup()) {
          MessageSender.resendGroupMessage(context, messageRecord, id);
        } else {
          MessageSender.resend(context, messageRecord);
        }
      } else {
        smsDatabase.removeMismatchedIdentity(messageRecord.getId(), id, identityKey);

        MessageSender.resend(context, messageRecord);
      }
    }
  }

  static final class SafetyNumberChangeState {

    private final List<ChangedRecipient> changedRecipients;
    private final MessageRecord          messageRecord;

    SafetyNumberChangeState(List<ChangedRecipient> changedRecipients, @Nullable MessageRecord messageRecord) {
      this.changedRecipients = changedRecipients;
      this.messageRecord     = messageRecord;
    }

    @NonNull List<ChangedRecipient> getChangedRecipients() {
      return changedRecipients;
    }

    @Nullable MessageRecord getMessageRecord() {
      return messageRecord;
    }
  }
}

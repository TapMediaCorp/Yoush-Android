package com.tapmedia.yoush.jobs;


import androidx.annotation.NonNull;

import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobmanager.Data;
import com.tapmedia.yoush.jobmanager.Job;
import com.tapmedia.yoush.jobmanager.impl.NetworkConstraint;
import com.tapmedia.yoush.logging.Log;

import com.tapmedia.yoush.crypto.UnidentifiedAccessUtil;
import com.tapmedia.yoush.database.IdentityDatabase.VerifiedStatus;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.recipients.RecipientUtil;
import com.tapmedia.yoush.util.Base64;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.util.TextSecurePreferences;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class MultiDeviceVerifiedUpdateJob extends BaseJob {

  public static final String KEY = "MultiDeviceVerifiedUpdateJob";

  private static final String TAG = MultiDeviceVerifiedUpdateJob.class.getSimpleName();

  private static final String KEY_DESTINATION     = "destination";
  private static final String KEY_IDENTITY_KEY    = "identity_key";
  private static final String KEY_VERIFIED_STATUS = "verified_status";
  private static final String KEY_TIMESTAMP       = "timestamp";

  private RecipientId    destination;
  private byte[]         identityKey;
  private VerifiedStatus verifiedStatus;
  private long           timestamp;

  public MultiDeviceVerifiedUpdateJob(@NonNull RecipientId destination, IdentityKey identityKey, VerifiedStatus verifiedStatus) {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setQueue("__MULTI_DEVICE_VERIFIED_UPDATE__")
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build(),
         destination,
         identityKey.serialize(),
         verifiedStatus,
         System.currentTimeMillis());
  }

  private MultiDeviceVerifiedUpdateJob(@NonNull Job.Parameters parameters,
                                       @NonNull RecipientId destination,
                                       @NonNull byte[] identityKey,
                                       @NonNull VerifiedStatus verifiedStatus,
                                       long timestamp)
  {
    super(parameters);

    this.destination    = destination;
    this.identityKey    = identityKey;
    this.verifiedStatus = verifiedStatus;
    this.timestamp      = timestamp;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putString(KEY_DESTINATION, destination.serialize())
                             .putString(KEY_IDENTITY_KEY, Base64.encodeBytes(identityKey))
                             .putInt(KEY_VERIFIED_STATUS, verifiedStatus.toInt())
                             .putLong(KEY_TIMESTAMP, timestamp)
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException, UntrustedIdentityException {
    try {
      if (!TextSecurePreferences.isMultiDevice(context)) {
        Log.i(TAG, "Not multi device...");
        return;
      }

      if (destination == null) {
        Log.w(TAG, "No destination...");
        return;
      }

      SignalServiceMessageSender    messageSender        = ApplicationDependencies.getSignalServiceMessageSender();
      Recipient                     recipient            = Recipient.resolved(destination);
      VerifiedMessage.VerifiedState verifiedState        = getVerifiedState(verifiedStatus);
      SignalServiceAddress          verifiedAddress      = RecipientUtil.toSignalServiceAddress(context, recipient);
      VerifiedMessage               verifiedMessage      = new VerifiedMessage(verifiedAddress, new IdentityKey(identityKey, 0), verifiedState, timestamp);

      messageSender.sendMessage(SignalServiceSyncMessage.forVerified(verifiedMessage),
                                UnidentifiedAccessUtil.getAccessFor(context, recipient));
    } catch (InvalidKeyException e) {
      throw new IOException(e);
    }
  }

  private VerifiedMessage.VerifiedState getVerifiedState(VerifiedStatus status) {
    VerifiedMessage.VerifiedState verifiedState;

    switch (status) {
      case DEFAULT:    verifiedState = VerifiedMessage.VerifiedState.DEFAULT;    break;
      case VERIFIED:   verifiedState = VerifiedMessage.VerifiedState.VERIFIED;   break;
      case UNVERIFIED: verifiedState = VerifiedMessage.VerifiedState.UNVERIFIED; break;
      default: throw new AssertionError("Unknown status: " + verifiedStatus);
    }

    return verifiedState;
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    return exception instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {

  }

  public static final class Factory implements Job.Factory<MultiDeviceVerifiedUpdateJob> {
    @Override
    public @NonNull MultiDeviceVerifiedUpdateJob create(@NonNull Parameters parameters, @NonNull Data data) {
      try {
        RecipientId    destination    = RecipientId.from(data.getString(KEY_DESTINATION));
        VerifiedStatus verifiedStatus = VerifiedStatus.forState(data.getInt(KEY_VERIFIED_STATUS));
        long           timestamp      = data.getLong(KEY_TIMESTAMP);
        byte[]         identityKey    = Base64.decode(data.getString(KEY_IDENTITY_KEY));

        return new MultiDeviceVerifiedUpdateJob(parameters, destination, identityKey, verifiedStatus, timestamp);
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }
  }
}

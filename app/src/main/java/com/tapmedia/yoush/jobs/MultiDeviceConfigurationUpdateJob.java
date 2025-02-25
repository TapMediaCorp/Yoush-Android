package com.tapmedia.yoush.jobs;


import androidx.annotation.NonNull;

import com.tapmedia.yoush.crypto.UnidentifiedAccessUtil;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobmanager.Data;
import com.tapmedia.yoush.jobmanager.Job;
import com.tapmedia.yoush.jobmanager.impl.NetworkConstraint;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.multidevice.ConfigurationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

public class MultiDeviceConfigurationUpdateJob extends BaseJob {

  public static final String KEY = "MultiDeviceConfigurationUpdateJob";

  private static final String TAG = MultiDeviceConfigurationUpdateJob.class.getSimpleName();

  private static final String KEY_READ_RECEIPTS_ENABLED                    = "read_receipts_enabled";
  private static final String KEY_TYPING_INDICATORS_ENABLED                = "typing_indicators_enabled";
  private static final String KEY_UNIDENTIFIED_DELIVERY_INDICATORS_ENABLED = "unidentified_delivery_indicators_enabled";
  private static final String KEY_LINK_PREVIEWS_ENABLED                    = "link_previews_enabled";

  private boolean readReceiptsEnabled;
  private boolean typingIndicatorsEnabled;
  private boolean unidentifiedDeliveryIndicatorsEnabled;
  private boolean linkPreviewsEnabled;

  public MultiDeviceConfigurationUpdateJob(boolean readReceiptsEnabled,
                                           boolean typingIndicatorsEnabled,
                                           boolean unidentifiedDeliveryIndicatorsEnabled,
                                           boolean linkPreviewsEnabled)
  {
    this(new Job.Parameters.Builder()
                           .setQueue("__MULTI_DEVICE_CONFIGURATION_UPDATE_JOB__")
                           .addConstraint(NetworkConstraint.KEY)
                           .setMaxAttempts(10)
                           .build(),
         readReceiptsEnabled,
         typingIndicatorsEnabled,
         unidentifiedDeliveryIndicatorsEnabled,
         linkPreviewsEnabled);

  }

  private MultiDeviceConfigurationUpdateJob(@NonNull Job.Parameters parameters,
                                            boolean readReceiptsEnabled,
                                            boolean typingIndicatorsEnabled,
                                            boolean unidentifiedDeliveryIndicatorsEnabled,
                                            boolean linkPreviewsEnabled)
  {
    super(parameters);

    this.readReceiptsEnabled                   = readReceiptsEnabled;
    this.typingIndicatorsEnabled               = typingIndicatorsEnabled;
    this.unidentifiedDeliveryIndicatorsEnabled = unidentifiedDeliveryIndicatorsEnabled;
    this.linkPreviewsEnabled                   = linkPreviewsEnabled;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putBoolean(KEY_READ_RECEIPTS_ENABLED, readReceiptsEnabled)
                             .putBoolean(KEY_TYPING_INDICATORS_ENABLED, typingIndicatorsEnabled)
                             .putBoolean(KEY_UNIDENTIFIED_DELIVERY_INDICATORS_ENABLED, unidentifiedDeliveryIndicatorsEnabled)
                             .putBoolean(KEY_LINK_PREVIEWS_ENABLED, linkPreviewsEnabled)
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException, UntrustedIdentityException {
    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Not multi device, aborting...");
      return;
    }

    SignalServiceMessageSender messageSender = ApplicationDependencies.getSignalServiceMessageSender();
    messageSender.sendMessage(SignalServiceSyncMessage.forConfiguration(new ConfigurationMessage(Optional.of(readReceiptsEnabled),
                                                                                                 Optional.of(unidentifiedDeliveryIndicatorsEnabled),
                                                                                                 Optional.of(typingIndicatorsEnabled),
                                                                                                 Optional.of(linkPreviewsEnabled))),
                              UnidentifiedAccessUtil.getAccessForSync(context));
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "**** Failed to synchronize read receipts state!");
  }

  public static final class Factory implements Job.Factory<MultiDeviceConfigurationUpdateJob> {
    @Override
    public @NonNull MultiDeviceConfigurationUpdateJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new MultiDeviceConfigurationUpdateJob(parameters,
                                                   data.getBooleanOrDefault(KEY_READ_RECEIPTS_ENABLED, false),
                                                   data.getBooleanOrDefault(KEY_TYPING_INDICATORS_ENABLED, false),
                                                   data.getBooleanOrDefault(KEY_UNIDENTIFIED_DELIVERY_INDICATORS_ENABLED, false),
                                                   data.getBooleanOrDefault(KEY_LINK_PREVIEWS_ENABLED, false));
    }
  }
}

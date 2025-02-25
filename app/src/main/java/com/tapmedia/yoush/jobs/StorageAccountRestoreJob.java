package com.tapmedia.yoush.jobs;

import android.app.job.JobScheduler;

import androidx.annotation.NonNull;

import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobmanager.Data;
import com.tapmedia.yoush.jobmanager.Job;
import com.tapmedia.yoush.jobmanager.JobManager;
import com.tapmedia.yoush.jobmanager.JobTracker;
import com.tapmedia.yoush.jobmanager.impl.NetworkConstraint;
import com.tapmedia.yoush.keyvalue.SignalStore;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.storage.StorageSyncHelper;
import com.tapmedia.yoush.util.Base64;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.storage.SignalAccountRecord;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.api.storage.StorageKey;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Restored the AccountRecord present in the storage service, if any. This will overwrite any local
 * data that is stored in AccountRecord, so this should only be done immediately after registration.
 */
public class StorageAccountRestoreJob extends BaseJob {

  public static String KEY = "StorageAccountRestoreJob";

  public static long LIFESPAN = TimeUnit.SECONDS.toMillis(20);

  private static final String TAG = Log.tag(StorageAccountRestoreJob.class);

  public StorageAccountRestoreJob() {
    this(new Parameters.Builder()
                       .setQueue(StorageSyncJob.QUEUE_KEY)
                       .addConstraint(NetworkConstraint.KEY)
                       .setMaxInstances(1)
                       .setMaxAttempts(1)
                       .setLifespan(LIFESPAN)
                       .build());
  }

  private StorageAccountRestoreJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    SignalServiceAccountManager accountManager    = ApplicationDependencies.getSignalServiceAccountManager();
    StorageKey                  storageServiceKey = SignalStore.storageServiceValues().getOrCreateStorageKey();

    Log.i(TAG, "Retrieving manifest...");
    Optional<SignalStorageManifest> manifest = accountManager.getStorageManifest(storageServiceKey);

    if (!manifest.isPresent()) {
      Log.w(TAG, "Manifest did not exist or was undecryptable (bad key). Not restoring. Force-pushing.");
      ApplicationDependencies.getJobManager().add(new StorageForcePushJob());
      return;
    }

    Optional<StorageId> accountId = manifest.get().getAccountStorageId();

    if (!accountId.isPresent()) {
      Log.w(TAG, "Manifest had no account record! Not restoring.");
      return;
    }

    Log.i(TAG, "Retrieving account record...");
    List<SignalStorageRecord> records = accountManager.readStorageRecords(storageServiceKey, Collections.singletonList(accountId.get()));
    SignalStorageRecord       record  = records.size() > 0 ? records.get(0) : null;

    if (record == null) {
      Log.w(TAG, "Could not find account record, even though we had an ID! Not restoring.");
      return;
    }

    SignalAccountRecord accountRecord = record.getAccount().orNull();
    if (accountRecord == null) {
      Log.w(TAG, "The storage record didn't actually have an account on it! Not restoring.");
      return;
    }


    Log.i(TAG, "Applying changes locally...");
    StorageId selfStorageId = StorageId.forAccount(Recipient.self().getStorageServiceId());
    StorageSyncHelper.applyAccountStorageSyncUpdates(context, selfStorageId, accountRecord, false);

    JobManager jobManager = ApplicationDependencies.getJobManager();

    if (accountRecord.getAvatarUrlPath().isPresent()) {
      Log.i(TAG,  "Fetching avatar...");
      Optional<JobTracker.JobState> state = jobManager.runSynchronously(new RetrieveProfileAvatarJob(Recipient.self(), accountRecord.getAvatarUrlPath().get()), LIFESPAN/2);

      if (state.isPresent()) {
        Log.i(TAG, "Avatar retrieved successfully. " + state.get());
      } else {
        Log.w(TAG, "Avatar retrieval did not complete in time (or otherwise failed).");
      }
    } else {
      Log.i(TAG, "No avatar present. Not fetching.");
    }

    Log.i(TAG,  "Refreshing attributes...");
    Optional<JobTracker.JobState> state = jobManager.runSynchronously(new RefreshAttributesJob(), LIFESPAN/2);

    if (state.isPresent()) {
      Log.i(TAG, "Attributes refreshed successfully. " + state.get());
    } else {
      Log.w(TAG, "Attribute refresh did not complete in time (or otherwise failed).");
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {
  }

  public static class Factory implements Job.Factory<StorageAccountRestoreJob> {
    @Override
    public @NonNull
    StorageAccountRestoreJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new StorageAccountRestoreJob(parameters);
    }
  }
}

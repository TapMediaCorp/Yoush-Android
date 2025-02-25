package com.tapmedia.yoush.jobs;


import android.app.Application;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import com.tapmedia.yoush.crypto.ProfileKeyUtil;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.GroupDatabase;
import com.tapmedia.yoush.database.RecipientDatabase;
import com.tapmedia.yoush.database.RecipientDatabase.UnidentifiedAccessMode;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobmanager.Data;
import com.tapmedia.yoush.jobmanager.Job;
import com.tapmedia.yoush.jobmanager.JobManager;
import com.tapmedia.yoush.jobmanager.impl.NetworkConstraint;
import com.tapmedia.yoush.keyvalue.SignalStore;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.profiles.ProfileName;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.transport.RetryLaterException;
import com.tapmedia.yoush.util.Base64;
import com.tapmedia.yoush.util.FeatureFlags;
import com.tapmedia.yoush.util.IdentityUtil;
import com.tapmedia.yoush.util.ProfileUtil;
import com.tapmedia.yoush.util.SetUtil;
import com.tapmedia.yoush.util.Stopwatch;
import com.tapmedia.yoush.util.Util;
import com.tapmedia.yoush.util.concurrent.SignalExecutors;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.internal.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Retrieves a users profile and sets the appropriate local fields.
 */
public class RetrieveProfileJob extends BaseJob {

  public static final String KEY = "RetrieveProfileJob";

  private static final String TAG = RetrieveProfileJob.class.getSimpleName();

  private static final String KEY_RECIPIENTS = "recipients";

  private final List<RecipientId> recipientIds;

  /**
   * Identical to {@link #enqueue(Collection)})}, but run on a background thread for convenience.
   */
  public static void enqueueAsync(@NonNull RecipientId recipientId) {
    SignalExecutors.BOUNDED.execute(() -> {
      ApplicationDependencies.getJobManager().add(forRecipient(recipientId));
    });
  }

  /**
   * Submits the necessary job to refresh the profile of the requested recipient. Works for any
   * RecipientId, including individuals, groups, or yourself.
   *
   * Identical to {@link #enqueue(Collection)})}
   */
  @WorkerThread
  public static void enqueue(@NonNull RecipientId recipientId) {
    ApplicationDependencies.getJobManager().add(forRecipient(recipientId));
  }

  /**
   * Submits the necessary jobs to refresh the profiles of the requested recipients. Works for any
   * RecipientIds, including individuals, groups, or yourself.
   */
  @WorkerThread
  public static void enqueue(@NonNull Collection<RecipientId> recipientIds) {
    JobManager jobManager = ApplicationDependencies.getJobManager();

    for (Job job : forRecipients(recipientIds)) {
      jobManager.add(job);
    }
  }

  /**
   * Works for any RecipientId, whether it's an individual, group, or yourself.
   */
  @WorkerThread
  public static @NonNull Job forRecipient(@NonNull RecipientId recipientId) {
    Recipient recipient = Recipient.resolved(recipientId);

    if (recipient.isLocalNumber()) {
      return new RefreshOwnProfileJob();
    } else if (recipient.isGroup()) {
      Context         context    = ApplicationDependencies.getApplication();
      List<Recipient> recipients = DatabaseFactory.getGroupDatabase(context).getGroupMembers(recipient.requireGroupId(), GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);

      return new RetrieveProfileJob(Stream.of(recipients).map(Recipient::getId).toList());
    } else {
      return new RetrieveProfileJob(Collections.singletonList(recipientId));
    }
  }

  /**
   * Works for any RecipientId, whether it's an individual, group, or yourself.
   */
  @WorkerThread
  public static @NonNull List<Job> forRecipients(@NonNull Collection<RecipientId> recipientIds) {
    Context           context    = ApplicationDependencies.getApplication();
    List<RecipientId> combined   = new LinkedList<>();
    List<Job>         jobs       = new LinkedList<>();

    for (RecipientId recipientId : recipientIds) {
      Recipient recipient = Recipient.resolved(recipientId);

      if (recipient.isLocalNumber()) {
        jobs.add(new RefreshOwnProfileJob());
      } else if (recipient.isGroup()) {
        List<Recipient> recipients = DatabaseFactory.getGroupDatabase(context).getGroupMembers(recipient.requireGroupId(), GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);
        combined.addAll(Stream.of(recipients).map(Recipient::getId).toList());
      } else {
        combined.add(recipientId);
      }
    }

    jobs.add(new RetrieveProfileJob(combined));

    return jobs;
  }

  /**
   * Will fetch some profiles to ensure we're decently up-to-date if we haven't done so within a
   * certain time period.
   */
  public static void enqueueRoutineFetchIfNeccessary(Application application) {
    long timeSinceRefresh = System.currentTimeMillis() - SignalStore.misc().getLastProfileRefreshTime();
    if (timeSinceRefresh < TimeUnit.HOURS.toMillis(12)) {
      Log.i(TAG, "Too soon to refresh. Did the last refresh " + timeSinceRefresh + " ms ago.");
      return;
    }

    SignalExecutors.BOUNDED.execute(() -> {
      RecipientDatabase db      = DatabaseFactory.getRecipientDatabase(application);
      long              current = System.currentTimeMillis();

      List<RecipientId> ids = db.getRecipientsForRoutineProfileFetch(current - TimeUnit.DAYS.toMillis(30),
                                                                     current - TimeUnit.DAYS.toMillis(1),
                                                                     50);

      if (ids.size() > 0) {
        Log.i(TAG, "Optimistically refreshing " + ids.size() + " eligible recipient(s).");
        enqueue(ids);
      } else {
        Log.i(TAG, "No recipients to refresh.");
      }

      SignalStore.misc().setLastProfileRefreshTime(System.currentTimeMillis());
    });
  }

  private RetrieveProfileJob(@NonNull List<RecipientId> recipientIds) {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setMaxAttempts(3)
                           .build(),
         recipientIds);
  }

  private RetrieveProfileJob(@NonNull Job.Parameters parameters, @NonNull List<RecipientId> recipientIds) {
    super(parameters);
    this.recipientIds = recipientIds;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder()
                   .putStringListAsArray(KEY_RECIPIENTS, Stream.of(recipientIds)
                                                               .map(RecipientId::serialize)
                                                               .toList())
                   .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException, RetryLaterException {
    Stopwatch        stopwatch = new Stopwatch("RetrieveProfile");
    Set<RecipientId> retries   = new HashSet<>();

    List<Recipient> recipients = Stream.of(recipientIds).map(Recipient::resolved).toList();
    stopwatch.split("resolve");

    List<Pair<Recipient, ListenableFuture<ProfileAndCredential>>> futures = Stream.of(recipients)
                                                                                  .filter(Recipient::hasServiceIdentifier)
                                                                                  .map(r -> new Pair<>(r, ProfileUtil.retrieveProfile(context, r, getRequestType(r))))
                                                                                  .toList();
    stopwatch.split("futures");

    List<Pair<Recipient, ProfileAndCredential>> profiles = Stream.of(futures)
                                                                 .map(pair -> {
                                                                   Recipient recipient = pair.first();

                                                                   try {
                                                                     ProfileAndCredential profile = pair.second().get(5, TimeUnit.SECONDS);
                                                                     return new Pair<>(recipient, profile);
                                                                   } catch (InterruptedException | TimeoutException e) {
                                                                     retries.add(recipient.getId());
                                                                   } catch (ExecutionException e) {
                                                                     if (e.getCause() instanceof PushNetworkException) {
                                                                       retries.add(recipient.getId());
                                                                     } else if (e.getCause() instanceof NotFoundException) {
                                                                       Log.w(TAG, "Failed to find a profile for " + recipient.getId());
                                                                     } else {
                                                                       Log.w(TAG, "Failed to retrieve profile for " + recipient.getId());
                                                                     }
                                                                   }
                                                                   return null;
                                                                 })
                                                                 .withoutNulls()
                                                                 .toList();
    stopwatch.split("network");

    for (Pair<Recipient, ProfileAndCredential> profile : profiles) {
      process(profile.first(), profile.second());
    }

    Set<RecipientId> success = SetUtil.difference(recipientIds, retries);
    DatabaseFactory.getRecipientDatabase(context).markProfilesFetched(success, System.currentTimeMillis());

    stopwatch.split("process");

    long keyCount = Stream.of(profiles).map(Pair::first).map(Recipient::getProfileKey).withoutNulls().count();
    Log.d(TAG, String.format(Locale.US, "Started with %d recipient(s). Found %d profile(s), and had keys for %d of them. Will retry %d.", recipients.size(), profiles.size(), keyCount, retries.size()));

    stopwatch.stop(TAG);

    recipientIds.clear();
    recipientIds.addAll(retries);

    if (recipientIds.size() > 0) {
      throw new RetryLaterException();
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof RetryLaterException;
  }

  @Override
  public void onFailure() {}

  private void process(Recipient recipient, ProfileAndCredential profileAndCredential) throws IOException {
    SignalServiceProfile profile              = profileAndCredential.getProfile();
    ProfileKey           recipientProfileKey  = ProfileKeyUtil.profileKeyOrNull(recipient.getProfileKey());

    setProfileName(recipient, profile.getName());
    setProfileAvatar(recipient, profile.getAvatar());
    if (FeatureFlags.usernames()) setUsername(recipient, profile.getUsername());
    setProfileCapabilities(recipient, profile.getCapabilities());
    setIdentityKey(recipient, profile.getIdentityKey());
    setUnidentifiedAccessMode(recipient, profile.getUnidentifiedAccess(), profile.isUnrestrictedUnidentifiedAccess());

    if (recipientProfileKey != null) {
      Optional<ProfileKeyCredential> profileKeyCredential = profileAndCredential.getProfileKeyCredential();
      if (profileKeyCredential.isPresent()) {
        setProfileKeyCredential(recipient, recipientProfileKey, profileKeyCredential.get());
      }
    }
  }

  private void setProfileKeyCredential(@NonNull Recipient recipient,
                                       @NonNull ProfileKey recipientProfileKey,
                                       @NonNull ProfileKeyCredential credential)
  {
    RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    recipientDatabase.setProfileKeyCredential(recipient.getId(), recipientProfileKey, credential);
  }

  private static SignalServiceProfile.RequestType getRequestType(@NonNull Recipient recipient) {
    return !recipient.hasProfileKeyCredential()
           ? SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL
           : SignalServiceProfile.RequestType.PROFILE;
  }

  private void setIdentityKey(Recipient recipient, String identityKeyValue) {
    try {
      if (TextUtils.isEmpty(identityKeyValue)) {
        Log.w(TAG, "Identity key is missing on profile!");
        return;
      }

      IdentityKey identityKey = new IdentityKey(Base64.decode(identityKeyValue), 0);

      if (!DatabaseFactory.getIdentityDatabase(context)
                          .getIdentity(recipient.getId())
                          .isPresent())
      {
        Log.w(TAG, "Still first use...");
        return;
      }

      IdentityUtil.saveIdentity(context, recipient.requireServiceId(), identityKey);
    } catch (InvalidKeyException | IOException e) {
      Log.w(TAG, e);
    }
  }

  private void setUnidentifiedAccessMode(Recipient recipient, String unidentifiedAccessVerifier, boolean unrestrictedUnidentifiedAccess) {
    RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    ProfileKey        profileKey        = ProfileKeyUtil.profileKeyOrNull(recipient.getProfileKey());

    if (unrestrictedUnidentifiedAccess && unidentifiedAccessVerifier != null) {
      if (recipient.getUnidentifiedAccessMode() != UnidentifiedAccessMode.UNRESTRICTED) {
        Log.i(TAG, "Marking recipient UD status as unrestricted.");
        recipientDatabase.setUnidentifiedAccessMode(recipient.getId(), UnidentifiedAccessMode.UNRESTRICTED);
      }
    } else if (profileKey == null || unidentifiedAccessVerifier == null) {
      if (recipient.getUnidentifiedAccessMode() != UnidentifiedAccessMode.DISABLED) {
        Log.i(TAG, "Marking recipient UD status as disabled.");
        recipientDatabase.setUnidentifiedAccessMode(recipient.getId(), UnidentifiedAccessMode.DISABLED);
      }
    } else {
      ProfileCipher profileCipher = new ProfileCipher(profileKey);
      boolean verifiedUnidentifiedAccess;

      try {
        verifiedUnidentifiedAccess = profileCipher.verifyUnidentifiedAccess(Base64.decode(unidentifiedAccessVerifier));
      } catch (IOException e) {
        Log.w(TAG, e);
        verifiedUnidentifiedAccess = false;
      }

      UnidentifiedAccessMode mode = verifiedUnidentifiedAccess ? UnidentifiedAccessMode.ENABLED : UnidentifiedAccessMode.DISABLED;

      if (recipient.getUnidentifiedAccessMode() != mode) {
        Log.i(TAG, "Marking recipient UD status as " + mode.name() + " after verification.");
        recipientDatabase.setUnidentifiedAccessMode(recipient.getId(), mode);
      }
    }
  }

  private void setProfileName(Recipient recipient, String profileName) {
    try {
      ProfileKey profileKey = ProfileKeyUtil.profileKeyOrNull(recipient.getProfileKey());
      if (profileKey == null) return;

      String plaintextProfileName = ProfileUtil.decryptName(profileKey, profileName);

      if (!Objects.equals(plaintextProfileName, recipient.getProfileName().serialize())) {
        Log.i(TAG, "Profile name updated. Writing new value.");
        DatabaseFactory.getRecipientDatabase(context).setProfileName(recipient.getId(), ProfileName.fromSerialized(plaintextProfileName));
      }

      if (TextUtils.isEmpty(plaintextProfileName)) {
        Log.i(TAG, "No profile name set.");
      }
    } catch (InvalidCiphertextException e) {
      Log.w(TAG, "Bad profile key for " + recipient.getId());
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  private void setProfileAvatar(Recipient recipient, String profileAvatar) {
    if (recipient.getProfileKey() == null) return;

    if (!Util.equals(profileAvatar, recipient.getProfileAvatar())) {
      ApplicationDependencies.getJobManager().add(new RetrieveProfileAvatarJob(recipient, profileAvatar));
    }
  }

  private void setUsername(Recipient recipient, @Nullable String username) {
    DatabaseFactory.getRecipientDatabase(context).setUsername(recipient.getId(), username);
  }

  private void setProfileCapabilities(@NonNull Recipient recipient, @Nullable SignalServiceProfile.Capabilities capabilities) {
    if (capabilities == null) {
      return;
    }

    DatabaseFactory.getRecipientDatabase(context).setCapabilities(recipient.getId(), capabilities);
  }

  public static final class Factory implements Job.Factory<RetrieveProfileJob> {

    @Override
    public @NonNull RetrieveProfileJob create(@NonNull Parameters parameters, @NonNull Data data) {
      String[]          ids          = data.getStringArray(KEY_RECIPIENTS);
      List<RecipientId> recipientIds = Stream.of(ids).map(RecipientId::from).toList();

      return new RetrieveProfileJob(parameters, recipientIds);
    }
  }
}

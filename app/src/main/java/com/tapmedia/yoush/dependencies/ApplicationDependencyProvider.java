package com.tapmedia.yoush.dependencies;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;
import com.tapmedia.yoush.BuildConfig;
import com.tapmedia.yoush.jobmanager.impl.FactoryJobPredicate;
import com.tapmedia.yoush.jobs.MarkerJob;
import com.tapmedia.yoush.jobs.PushDecryptMessageJob;
import com.tapmedia.yoush.jobs.PushGroupSendJob;
import com.tapmedia.yoush.jobs.PushMediaSendJob;
import com.tapmedia.yoush.jobs.PushProcessMessageJob;
import com.tapmedia.yoush.jobs.PushTextSendJob;
import com.tapmedia.yoush.jobs.ReactionSendJob;
import com.tapmedia.yoush.jobs.TypingSendJob;
import com.tapmedia.yoush.messages.IncomingMessageProcessor;
import com.tapmedia.yoush.crypto.storage.SignalProtocolStoreImpl;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.events.ReminderUpdateEvent;
import com.tapmedia.yoush.messages.BackgroundMessageRetriever;
import com.tapmedia.yoush.jobmanager.JobManager;
import com.tapmedia.yoush.jobmanager.JobMigrator;
import com.tapmedia.yoush.jobmanager.impl.JsonDataSerializer;
import com.tapmedia.yoush.jobs.FastJobStorage;
import com.tapmedia.yoush.jobs.JobManagerFactories;
import com.tapmedia.yoush.keyvalue.KeyValueStore;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.megaphone.MegaphoneRepository;
import com.tapmedia.yoush.messages.InitialMessageRetriever;
import com.tapmedia.yoush.notifications.DefaultMessageNotifier;
import com.tapmedia.yoush.notifications.MessageNotifier;
import com.tapmedia.yoush.notifications.OptimizedMessageNotifier;
import com.tapmedia.yoush.push.SecurityEventListener;
import com.tapmedia.yoush.push.SignalServiceNetworkAccess;
import com.tapmedia.yoush.recipients.LiveRecipientCache;
import com.tapmedia.yoush.messages.IncomingMessageObserver;
import com.tapmedia.yoush.util.AlarmSleepTimer;
import com.tapmedia.yoush.util.EarlyMessageCache;
import com.tapmedia.yoush.util.FeatureFlags;
import com.tapmedia.yoush.util.FrameRateTracker;
import com.tapmedia.yoush.util.TextSecurePreferences;
import com.tapmedia.yoush.util.concurrent.SignalExecutors;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.util.UptimeSleepTimer;
import org.whispersystems.signalservice.api.websocket.ConnectivityListener;

import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Implementation of {@link ApplicationDependencies.Provider} that provides real app dependencies.
 */
public class ApplicationDependencyProvider implements ApplicationDependencies.Provider {

  private static final String TAG = Log.tag(ApplicationDependencyProvider.class);

  private final Application                context;
  private final SignalServiceNetworkAccess networkAccess;

  public ApplicationDependencyProvider(@NonNull Application context, @NonNull SignalServiceNetworkAccess networkAccess) {
    this.context       = context;
    this.networkAccess = networkAccess;
  }

  private @NonNull ClientZkOperations provideClientZkOperations() {
    return ClientZkOperations.create(networkAccess.getConfiguration(context));
  }

  @Override
  public @NonNull GroupsV2Operations provideGroupsV2Operations() {
    return new GroupsV2Operations(provideClientZkOperations());
  }

  @Override
  public @NonNull SignalServiceAccountManager provideSignalServiceAccountManager() {
    return new SignalServiceAccountManager(networkAccess.getConfiguration(context),
                                           new DynamicCredentialsProvider(context),
                                           BuildConfig.SIGNAL_AGENT,
                                           provideGroupsV2Operations());
  }

  @Override
  public @NonNull SignalServiceMessageSender provideSignalServiceMessageSender() {
      return new SignalServiceMessageSender(networkAccess.getConfiguration(context),
                                            new DynamicCredentialsProvider(context),
                                            new SignalProtocolStoreImpl(context),
                                            BuildConfig.SIGNAL_AGENT,
                                            TextSecurePreferences.isMultiDevice(context),
                                            FeatureFlags.attachmentsV3(),
                                            Optional.fromNullable(IncomingMessageObserver.getPipe()),
                                            Optional.fromNullable(IncomingMessageObserver.getUnidentifiedPipe()),
                                            Optional.of(new SecurityEventListener(context)),
                                            provideClientZkOperations().getProfileOperations(),
                                            SignalExecutors.newCachedBoundedExecutor("signal-messages", 1, 16));
  }

  @Override
  public @NonNull SignalServiceMessageReceiver provideSignalServiceMessageReceiver() {
    SleepTimer sleepTimer = TextSecurePreferences.isFcmDisabled(context) ? new AlarmSleepTimer(context)
                                                                         : new UptimeSleepTimer();
    return new SignalServiceMessageReceiver(networkAccess.getConfiguration(context),
                                            new DynamicCredentialsProvider(context),
                                            BuildConfig.SIGNAL_AGENT,
                                            new PipeConnectivityListener(),
                                            sleepTimer,
                                            provideClientZkOperations().getProfileOperations());
  }

  @Override
  public @NonNull SignalServiceNetworkAccess provideSignalServiceNetworkAccess() {
    return networkAccess;
  }

  @Override
  public @NonNull IncomingMessageProcessor provideIncomingMessageProcessor() {
    return new IncomingMessageProcessor(context);
  }

  @Override
  public @NonNull BackgroundMessageRetriever provideBackgroundMessageRetriever() {
    return new BackgroundMessageRetriever();
  }

  @Override
  public @NonNull LiveRecipientCache provideRecipientCache() {
    return new LiveRecipientCache(context);
  }

  @Override
  public @NonNull JobManager provideJobManager() {
    return new JobManager(context, new JobManager.Configuration.Builder()
                                                               .setDataSerializer(new JsonDataSerializer())
                                                               .setJobFactories(JobManagerFactories.getJobFactories(context))
                                                               .setConstraintFactories(JobManagerFactories.getConstraintFactories(context))
                                                               .setConstraintObservers(JobManagerFactories.getConstraintObservers(context))
                                                               .setJobStorage(new FastJobStorage(DatabaseFactory.getJobDatabase(context), SignalExecutors.newCachedSingleThreadExecutor("signal-fast-job-storage")))
                                                               .setJobMigrator(new JobMigrator(TextSecurePreferences.getJobManagerVersion(context), JobManager.CURRENT_VERSION, JobManagerFactories.getJobMigrations(context)))
                                                               .addReservedJobRunner(new FactoryJobPredicate(PushDecryptMessageJob.KEY, PushProcessMessageJob.KEY, MarkerJob.KEY))
                                                               .addReservedJobRunner(new FactoryJobPredicate(PushTextSendJob.KEY, PushMediaSendJob.KEY, PushGroupSendJob.KEY, ReactionSendJob.KEY, TypingSendJob.KEY))
                                                               .build());
  }

  @Override
  public @NonNull FrameRateTracker provideFrameRateTracker() {
    return new FrameRateTracker(context);
  }

  @Override
  public @NonNull KeyValueStore provideKeyValueStore() {
    return new KeyValueStore(context);
  }

  @Override
  public @NonNull MegaphoneRepository provideMegaphoneRepository() {
    return new MegaphoneRepository(context);
  }

  @Override
  public @NonNull EarlyMessageCache provideEarlyMessageCache() {
    return new EarlyMessageCache();
  }

  @Override
  public @NonNull InitialMessageRetriever provideInitialMessageRetriever() {
    return new InitialMessageRetriever();
  }

  @Override
  public @NonNull MessageNotifier provideMessageNotifier() {
    return new OptimizedMessageNotifier(new DefaultMessageNotifier());
  }

  private static class DynamicCredentialsProvider implements CredentialsProvider {

    private final Context context;

    private DynamicCredentialsProvider(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public UUID getUuid() {
      return TextSecurePreferences.getLocalUuid(context);
    }

    @Override
    public String getE164() {
      return TextSecurePreferences.getLocalNumber(context);
    }

    @Override
    public String getPassword() {
      return TextSecurePreferences.getPushServerPassword(context);
    }

    @Override
    public String getSignalingKey() {
      return TextSecurePreferences.getSignalingKey(context);
    }
  }

  private class PipeConnectivityListener implements ConnectivityListener {

    @Override
    public void onConnected() {
      Log.i(TAG, "onConnected()");
      TextSecurePreferences.setUnauthorizedReceived(context, false);
    }

    @Override
    public void onConnecting() {
      Log.i(TAG, "onConnecting()");
    }

    @Override
    public void onDisconnected() {
      Log.w(TAG, "onDisconnected()");
    }

    @Override
    public void onAuthenticationFailure() {
      Log.w(TAG, "onAuthenticationFailure()");
      TextSecurePreferences.setUnauthorizedReceived(context, true);
      EventBus.getDefault().post(new ReminderUpdateEvent());
    }
  }
}

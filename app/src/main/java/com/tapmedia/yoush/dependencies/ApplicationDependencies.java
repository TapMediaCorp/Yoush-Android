package com.tapmedia.yoush.dependencies;

import android.app.Application;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.tapmedia.yoush.BuildConfig;
import com.tapmedia.yoush.messages.IncomingMessageProcessor;
import com.tapmedia.yoush.messages.BackgroundMessageRetriever;
import com.tapmedia.yoush.groups.GroupsV2AuthorizationMemoryValueCache;
import com.tapmedia.yoush.groups.v2.processing.GroupsV2StateProcessor;
import com.tapmedia.yoush.jobmanager.JobManager;
import com.tapmedia.yoush.keyvalue.KeyValueStore;
import com.tapmedia.yoush.keyvalue.SignalStore;
import com.tapmedia.yoush.megaphone.MegaphoneRepository;
import com.tapmedia.yoush.messages.InitialMessageRetriever;
import com.tapmedia.yoush.notifications.DefaultMessageNotifier;
import com.tapmedia.yoush.notifications.MessageNotifier;
import com.tapmedia.yoush.push.SignalServiceNetworkAccess;
import com.tapmedia.yoush.recipients.LiveRecipientCache;
import com.tapmedia.yoush.messages.IncomingMessageObserver;
import com.tapmedia.yoush.util.EarlyMessageCache;
import com.tapmedia.yoush.util.FeatureFlags;
import com.tapmedia.yoush.util.FrameRateTracker;
import com.tapmedia.yoush.util.IasKeyStore;
import com.tapmedia.yoush.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.KeyBackupService;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import com.tapmedia.yoush.groups.GroupsV2Authorization;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;

/**
 * Location for storing and retrieving application-scoped singletons. Users must call
 * {@link #init(Application, Provider)} before using any of the methods, preferably early on in
 * {@link Application#onCreate()}.
 *
 * All future application-scoped singletons should be written as normal objects, then placed here
 * to manage their singleton-ness.
 */
public class ApplicationDependencies {

  private static Application application;
  private static Provider    provider;

  private static SignalServiceAccountManager  accountManager;
  private static SignalServiceMessageSender   messageSender;
  private static SignalServiceMessageReceiver messageReceiver;
  private static IncomingMessageProcessor     incomingMessageProcessor;
  private static BackgroundMessageRetriever   backgroundMessageRetriever;
  private static LiveRecipientCache           recipientCache;
  private static JobManager                   jobManager;
  private static FrameRateTracker             frameRateTracker;
  private static KeyValueStore                keyValueStore;
  private static MegaphoneRepository          megaphoneRepository;
  private static GroupsV2Authorization        groupsV2Authorization;
  private static GroupsV2StateProcessor       groupsV2StateProcessor;
  private static GroupsV2Operations           groupsV2Operations;
  private static EarlyMessageCache            earlyMessageCache;
  private static InitialMessageRetriever      initialMessageRetriever;
  private static MessageNotifier              messageNotifier;

  @MainThread
  public static synchronized void init(@NonNull Application application, @NonNull Provider provider) {
    if (ApplicationDependencies.application != null || ApplicationDependencies.provider != null) {
      throw new IllegalStateException("Already initialized!");
    }

    ApplicationDependencies.application     = application;
    ApplicationDependencies.provider        = provider;
    ApplicationDependencies.messageNotifier = provider.provideMessageNotifier();
  }

  public static @NonNull Application getApplication() {
    assertInitialization();
    return application;
  }

  public static synchronized @NonNull SignalServiceAccountManager getSignalServiceAccountManager() {
    assertInitialization();

    if (accountManager == null) {
      accountManager = provider.provideSignalServiceAccountManager();
    }

    return accountManager;
  }

  public static synchronized @NonNull GroupsV2Authorization getGroupsV2Authorization() {
    assertInitialization();

    if (groupsV2Authorization == null) {
      GroupsV2Authorization.ValueCache authCache = new GroupsV2AuthorizationMemoryValueCache(SignalStore.groupsV2AuthorizationCache());
      groupsV2Authorization = new GroupsV2Authorization(getSignalServiceAccountManager().getGroupsV2Api(), authCache);
    }

    return groupsV2Authorization;
  }

  public static synchronized @NonNull GroupsV2Operations getGroupsV2Operations() {
    assertInitialization();

    if (groupsV2Operations == null) {
      groupsV2Operations = provider.provideGroupsV2Operations();
    }

    return groupsV2Operations;
  }

  public static synchronized @NonNull KeyBackupService getKeyBackupService() {
    return getSignalServiceAccountManager().getKeyBackupService(IasKeyStore.getIasKeyStore(application),
                                                                BuildConfig.KBS_ENCLAVE_NAME,
                                                                BuildConfig.KBS_MRENCLAVE,
                                                                10);
  }

  public static synchronized @NonNull GroupsV2StateProcessor getGroupsV2StateProcessor() {
    assertInitialization();

    if (groupsV2StateProcessor == null) {
      groupsV2StateProcessor = new GroupsV2StateProcessor(application);
    }

    return groupsV2StateProcessor;
  }

  public static synchronized @NonNull SignalServiceMessageSender getSignalServiceMessageSender() {
    assertInitialization();

    if (messageSender == null) {
      messageSender = provider.provideSignalServiceMessageSender();
    } else {
      messageSender.update(
              IncomingMessageObserver.getPipe(),
              IncomingMessageObserver.getUnidentifiedPipe(),
              TextSecurePreferences.isMultiDevice(application),
              FeatureFlags.attachmentsV3());
    }

    return messageSender;
  }

  public static synchronized @NonNull SignalServiceMessageReceiver getSignalServiceMessageReceiver() {
    assertInitialization();

    if (messageReceiver == null) {
      messageReceiver = provider.provideSignalServiceMessageReceiver();
    }

    return messageReceiver;
  }

  public static synchronized void resetSignalServiceMessageReceiver() {
    assertInitialization();
    messageReceiver = null;
  }

  public static synchronized @NonNull SignalServiceNetworkAccess getSignalServiceNetworkAccess() {
    assertInitialization();
    return provider.provideSignalServiceNetworkAccess();
  }

  public static synchronized @NonNull IncomingMessageProcessor getIncomingMessageProcessor() {
    assertInitialization();

    if (incomingMessageProcessor == null) {
      incomingMessageProcessor = provider.provideIncomingMessageProcessor();
    }

    return incomingMessageProcessor;
  }

  public static synchronized @NonNull BackgroundMessageRetriever getBackgroundMessageRetriever() {
    assertInitialization();

    if (backgroundMessageRetriever == null) {
      backgroundMessageRetriever = provider.provideBackgroundMessageRetriever();
    }

    return backgroundMessageRetriever;
  }

  public static synchronized @NonNull LiveRecipientCache getRecipientCache() {
    assertInitialization();

    if (recipientCache == null) {
      recipientCache = provider.provideRecipientCache();
    }

    return recipientCache;
  }

  public static synchronized @NonNull JobManager getJobManager() {
    assertInitialization();

    if (jobManager == null) {
      jobManager = provider.provideJobManager();
    }

    return jobManager;
  }

  public static synchronized @NonNull FrameRateTracker getFrameRateTracker() {
    assertInitialization();

    if (frameRateTracker == null) {
      frameRateTracker = provider.provideFrameRateTracker();
    }

    return frameRateTracker;
  }

  public static synchronized @NonNull KeyValueStore getKeyValueStore() {
    assertInitialization();

    if (keyValueStore == null) {
      keyValueStore = provider.provideKeyValueStore();
    }

    return keyValueStore;
  }

  public static synchronized @NonNull MegaphoneRepository getMegaphoneRepository() {
    assertInitialization();

    if (megaphoneRepository == null) {
      megaphoneRepository = provider.provideMegaphoneRepository();
    }

    return megaphoneRepository;
  }

  public static synchronized @NonNull EarlyMessageCache getEarlyMessageCache() {
    assertInitialization();

    if (earlyMessageCache == null) {
      earlyMessageCache = provider.provideEarlyMessageCache();
    }

    return earlyMessageCache;
  }

  public static synchronized @NonNull InitialMessageRetriever getInitialMessageRetriever() {
    assertInitialization();

    if (initialMessageRetriever == null) {
      initialMessageRetriever = provider.provideInitialMessageRetriever();
    }

    return initialMessageRetriever;
  }

  public static synchronized @NonNull MessageNotifier getMessageNotifier() {
    assertInitialization();
    return messageNotifier;
  }

  private static void assertInitialization() {
    if (application == null || provider == null) {
      throw new UninitializedException();
    }
  }

  public interface Provider {
    @NonNull GroupsV2Operations provideGroupsV2Operations();
    @NonNull SignalServiceAccountManager provideSignalServiceAccountManager();
    @NonNull SignalServiceMessageSender provideSignalServiceMessageSender();
    @NonNull SignalServiceMessageReceiver provideSignalServiceMessageReceiver();
    @NonNull SignalServiceNetworkAccess provideSignalServiceNetworkAccess();
    @NonNull IncomingMessageProcessor provideIncomingMessageProcessor();
    @NonNull BackgroundMessageRetriever provideBackgroundMessageRetriever();
    @NonNull LiveRecipientCache provideRecipientCache();
    @NonNull JobManager provideJobManager();
    @NonNull FrameRateTracker provideFrameRateTracker();
    @NonNull KeyValueStore provideKeyValueStore();
    @NonNull MegaphoneRepository provideMegaphoneRepository();
    @NonNull EarlyMessageCache provideEarlyMessageCache();
    @NonNull InitialMessageRetriever provideInitialMessageRetriever();
    @NonNull MessageNotifier provideMessageNotifier();
  }

  private static class UninitializedException extends IllegalStateException {
    private UninitializedException() {
      super("You must call init() first!");
    }
  }
}

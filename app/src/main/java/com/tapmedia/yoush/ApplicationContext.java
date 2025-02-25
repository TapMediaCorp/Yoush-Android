/*
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tapmedia.yoush;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.multidex.MultiDexApplication;

import com.google.android.gms.security.ProviderInstaller;

import org.conscrypt.Conscrypt;
import org.signal.aesgcmprovider.AesGcmProvider;
// import org.signal.ringrtc.CallManager;
import com.tapmedia.yoush.components.TypingStatusRepository;
import com.tapmedia.yoush.components.TypingStatusSender;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.helpers.SQLCipherOpenHelper;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.dependencies.ApplicationDependencyProvider;
import com.tapmedia.yoush.gcm.FcmJobService;
import com.tapmedia.yoush.jobs.CreateSignedPreKeyJob;
import com.tapmedia.yoush.jobs.FcmRefreshJob;
import com.tapmedia.yoush.jobs.MultiDeviceContactUpdateJob;
import com.tapmedia.yoush.jobs.PushNotificationReceiveJob;
import com.tapmedia.yoush.jobs.RefreshPreKeysJob;
import com.tapmedia.yoush.jobs.RetrieveProfileJob;
import com.tapmedia.yoush.logging.AndroidLogger;
import com.tapmedia.yoush.logging.CustomSignalProtocolLogger;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.logging.PersistentLogger;
import com.tapmedia.yoush.logging.SignalUncaughtExceptionHandler;
import com.tapmedia.yoush.messages.IncomingMessageObserver;
import com.tapmedia.yoush.messages.InitialMessageRetriever;
import com.tapmedia.yoush.migrations.ApplicationMigrations;
import com.tapmedia.yoush.notifications.NotificationChannels;
import com.tapmedia.yoush.providers.BlobProvider;
import com.tapmedia.yoush.push.SignalServiceNetworkAccess;
import com.tapmedia.yoush.registration.RegistrationUtil;
import com.tapmedia.yoush.revealable.ViewOnceMessageManager;
import com.tapmedia.yoush.ringrtc.RingRtcLogger;
import com.tapmedia.yoush.service.DirectoryRefreshListener;
import com.tapmedia.yoush.service.ExpiringMessageManager;
import com.tapmedia.yoush.service.KeyCachingService;
import com.tapmedia.yoush.service.LocalBackupListener;
import com.tapmedia.yoush.service.RotateSenderCertificateListener;
import com.tapmedia.yoush.service.RotateSignedPreKeyListener;
import com.tapmedia.yoush.service.UpdateApkRefreshListener;
import com.tapmedia.yoush.storage.StorageSyncHelper;
import com.tapmedia.yoush.util.FeatureFlags;
import com.tapmedia.yoush.util.TextSecurePreferences;
import com.tapmedia.yoush.util.concurrent.SignalExecutors;
import com.tapmedia.yoush.util.dynamiclanguage.DynamicLanguageContextWrapper;
import org.webrtc.audio.JavaAudioDeviceModule;
// import org.webrtc.voiceengine.WebRtcAudioManager;
//  import org.webrtc.voiceengine.WebRtcAudioUtils;
import org.whispersystems.libsignal.logging.SignalProtocolLoggerProvider;

import java.security.Security;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Will be called once when the TextSecure process is created.
 *
 * We're using this as an insertion point to patch up the Android PRNG disaster,
 * to initialize the job manager, and to check for GCM registration freshness.
 *
 * @author Moxie Marlinspike
 */
public class ApplicationContext extends MultiDexApplication implements DefaultLifecycleObserver {

  private static final String TAG = ApplicationContext.class.getSimpleName();

  private ExpiringMessageManager   expiringMessageManager;
  private ViewOnceMessageManager   viewOnceMessageManager;
  private TypingStatusRepository   typingStatusRepository;
  private TypingStatusSender       typingStatusSender;
  private IncomingMessageObserver  incomingMessageObserver;
  private PersistentLogger         persistentLogger;

  private volatile boolean isAppVisible;

  public static ApplicationContext getInstance(Context context) {
    return (ApplicationContext)context.getApplicationContext();
  }

  private static ApplicationContext mInstance;

  public static ApplicationContext getInstance() {
    return mInstance;
  }

  @Override
  public void onCreate() {
    mInstance = this;
    long startTime = System.currentTimeMillis();
    super.onCreate();
    initializeSecurityProvider();
    initializeLogging();
    Log.i(TAG, "onCreate()");
    initializeCrashHandling();
    initializeAppDependencies();
    initializeFirstEverAppLaunch();
    initializeApplicationMigrations();
    initializeMessageRetrieval();
    initializeExpiringMessageManager();
    initializeRevealableMessageManager();
    initializeTypingStatusRepository();
    initializeTypingStatusSender();
    initializeGcmCheck();
    initializeSignedPreKeyCheck();
    initializePeriodicTasks();
    initializeCircumvention();
    initializeRingRtc();
    initializePendingMessages();
    initializeBlobProvider();
    initializeCleanup();

    FeatureFlags.init();
    NotificationChannels.create(this);
    RefreshPreKeysJob.scheduleIfNecessary();
    StorageSyncHelper.scheduleRoutineSync();
    RetrieveProfileJob.enqueueRoutineFetchIfNeccessary(this);
    RegistrationUtil.markRegistrationPossiblyComplete();
    ProcessLifecycleOwner.get().getLifecycle().addObserver(this);

    if (Build.VERSION.SDK_INT < 21) {
      AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    ApplicationDependencies.getJobManager().beginJobLoop();
    Log.d(TAG, "onCreate() took " + (System.currentTimeMillis() - startTime) + " ms");
  }

  @Override
  public void onStart(@NonNull LifecycleOwner owner) {
    isAppVisible = true;
    Log.i(TAG, "App is now visible.");
    FeatureFlags.refreshIfNecessary();
    ApplicationDependencies.getRecipientCache().warmUp();
    executePendingContactSync();
    KeyCachingService.onAppForegrounded(this);
    ApplicationDependencies.getFrameRateTracker().begin();
    ApplicationDependencies.getMegaphoneRepository().onAppForegrounded();
    catchUpOnMessages();
  }

  @Override
  public void onStop(@NonNull LifecycleOwner owner) {
    isAppVisible = false;
    Log.i(TAG, "App is no longer visible.");
    KeyCachingService.onAppBackgrounded(this);
    ApplicationDependencies.getMessageNotifier().clearVisibleThread();
    ApplicationDependencies.getFrameRateTracker().end();
  }

  public ExpiringMessageManager getExpiringMessageManager() {
    return expiringMessageManager;
  }

  public ViewOnceMessageManager getViewOnceMessageManager() {
    return viewOnceMessageManager;
  }

  public TypingStatusRepository getTypingStatusRepository() {
    return typingStatusRepository;
  }

  public TypingStatusSender getTypingStatusSender() {
    return typingStatusSender;
  }

  public boolean isAppVisible() {
    return isAppVisible;
  }

  public PersistentLogger getPersistentLogger() {
    return persistentLogger;
  }

  private void initializeSecurityProvider() {
    try {
      Class.forName("org.signal.aesgcmprovider.AesGcmCipher");
    } catch (ClassNotFoundException e) {
      Log.e(TAG, "Failed to find AesGcmCipher class");
      throw new ProviderInitializationException();
    }

    int aesPosition = Security.insertProviderAt(new AesGcmProvider(), 1);
    Log.i(TAG, "Installed AesGcmProvider: " + aesPosition);

    if (aesPosition < 0) {
      Log.e(TAG, "Failed to install AesGcmProvider()");
      throw new ProviderInitializationException();
    }

    int conscryptPosition = Security.insertProviderAt(Conscrypt.newProvider(), 2);
    Log.i(TAG, "Installed Conscrypt provider: " + conscryptPosition);

    if (conscryptPosition < 0) {
      Log.w(TAG, "Did not install Conscrypt provider. May already be present.");
    }
  }

  private void initializeLogging() {
    persistentLogger = new PersistentLogger(this);
    com.tapmedia.yoush.logging.Log.initialize(new AndroidLogger(), persistentLogger);

    SignalProtocolLoggerProvider.setProvider(new CustomSignalProtocolLogger());
  }

  private void initializeCrashHandling() {
    final Thread.UncaughtExceptionHandler originalHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(new SignalUncaughtExceptionHandler(originalHandler));
  }

  private void initializeApplicationMigrations() {
    ApplicationMigrations.onApplicationCreate(this, ApplicationDependencies.getJobManager());
  }

  public void initializeMessageRetrieval() {
    this.incomingMessageObserver = new IncomingMessageObserver(this);
  }

  private void initializeAppDependencies() {
    ApplicationDependencies.init(this, new ApplicationDependencyProvider(this, new SignalServiceNetworkAccess(this)));
  }

  private void initializeFirstEverAppLaunch() {
    if (TextSecurePreferences.getFirstInstallVersion(this) == -1) {
      if (!SQLCipherOpenHelper.databaseFileExists(this)) {
        Log.i(TAG, "First ever app launch!");
        AppInitialization.onFirstEverAppLaunch(this);
      }

      Log.i(TAG, "Setting first install version to " + BuildConfig.CANONICAL_VERSION_CODE);
      TextSecurePreferences.setFirstInstallVersion(this, BuildConfig.CANONICAL_VERSION_CODE);
    }
  }

  private void initializeGcmCheck() {
    if (TextSecurePreferences.isPushRegistered(this)) {
      long nextSetTime = TextSecurePreferences.getFcmTokenLastSetTime(this) + TimeUnit.HOURS.toMillis(6);

      if (TextSecurePreferences.getFcmToken(this) == null || nextSetTime <= System.currentTimeMillis()) {
        ApplicationDependencies.getJobManager().add(new FcmRefreshJob());
      }
    }
  }

  private void initializeSignedPreKeyCheck() {
    if (!TextSecurePreferences.isSignedPreKeyRegistered(this)) {
      ApplicationDependencies.getJobManager().add(new CreateSignedPreKeyJob(this));
    }
  }

  private void initializeExpiringMessageManager() {
    this.expiringMessageManager = new ExpiringMessageManager(this);
  }

  private void initializeRevealableMessageManager() {
    this.viewOnceMessageManager = new ViewOnceMessageManager(this);
  }

  private void initializeTypingStatusRepository() {
    this.typingStatusRepository = new TypingStatusRepository();
  }

  private void initializeTypingStatusSender() {
    this.typingStatusSender = new TypingStatusSender(this);
  }

  private void initializePeriodicTasks() {
    RotateSignedPreKeyListener.schedule(this);
    DirectoryRefreshListener.schedule(this);
    LocalBackupListener.schedule(this);
    RotateSenderCertificateListener.schedule(this);

    if (BuildConfig.PLAY_STORE_DISABLED) {
      UpdateApkRefreshListener.schedule(this);
    }
  }

  private void initializeRingRtc() {
    try {
      Set<String> HARDWARE_AEC_BLACKLIST = new HashSet<String>() {{
        add("Pixel");
        add("Pixel XL");
        add("Moto G5");
        add("Moto G (5S) Plus");
        add("Moto G4");
        add("TA-1053");
        add("Mi A1");
        add("Mi A2");
        add("E5823"); // Sony z5 compact
        add("Redmi Note 5");
        add("FP2"); // Fairphone FP2
        add("MI 5");
      }};

      Set<String> OPEN_SL_ES_WHITELIST = new HashSet<String>() {{
        add("Pixel");
        add("Pixel XL");
      }};

//      if (HARDWARE_AEC_BLACKLIST.contains(Build.MODEL)) {
//        WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
//      }
//
//      if (!OPEN_SL_ES_WHITELIST.contains(Build.MODEL)) {
//       WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true);
//      }

      //  JavaAudioDeviceModule.builder ( this )
      //          .setUseHardwareAcousticEchoCanceler(false)
      //          .setUseHardwareNoiseSuppressor(false)
      //          .createAudioDeviceModule ();
      // WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
      // WebRtcAudioUtils.setWebRtcBasedAutomaticGainControl(true);
      // WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(true);


//      CallManager.initialize(this, new RingRtcLogger());
    } catch (UnsatisfiedLinkError e) {
      throw new AssertionError("Unable to load ringrtc library", e);
    }
  }

  @SuppressLint("StaticFieldLeak")
  private void initializeCircumvention() {
    AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        if (new SignalServiceNetworkAccess(ApplicationContext.this).isCensored(ApplicationContext.this)) {
          try {
            ProviderInstaller.installIfNeeded(ApplicationContext.this);
          } catch (Throwable t) {
            Log.w(TAG, t);
          }
        }
        return null;
      }
    };

    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private void executePendingContactSync() {
    if (TextSecurePreferences.needsFullContactSync(this)) {
      ApplicationDependencies.getJobManager().add(new MultiDeviceContactUpdateJob(true));
    }
  }

  private void initializePendingMessages() {
    if (TextSecurePreferences.getNeedsMessagePull(this)) {
      Log.i(TAG, "Scheduling a message fetch.");
      if (Build.VERSION.SDK_INT >= 26) {
        FcmJobService.schedule(this);
      } else {
        ApplicationDependencies.getJobManager().add(new PushNotificationReceiveJob(this));
      }
      TextSecurePreferences.setNeedsMessagePull(this, false);
    }
  }

  private void initializeBlobProvider() {
    SignalExecutors.BOUNDED.execute(() -> {
      BlobProvider.getInstance().onSessionStart(this);
    });
  }

  private void initializeCleanup() {
    SignalExecutors.BOUNDED.execute(() -> {
      int deleted = DatabaseFactory.getAttachmentDatabase(this).deleteAbandonedPreuploadedAttachments();
      Log.i(TAG, "Deleted " + deleted + " abandoned attachments.");
    });
  }

  private void catchUpOnMessages() {
    InitialMessageRetriever retriever = ApplicationDependencies.getInitialMessageRetriever();

    if (retriever.isCaughtUp()) {
      return;
    }

    SignalExecutors.UNBOUNDED.execute(() -> {
      long startTime = System.currentTimeMillis();

      switch (retriever.begin(TimeUnit.SECONDS.toMillis(60))) {
        case SUCCESS:
          Log.i(TAG, "Successfully caught up on messages. " + (System.currentTimeMillis() - startTime) + " ms");
          break;
        case FAILURE_TIMEOUT:
          Log.w(TAG, "Did not finish catching up due to a timeout. " + (System.currentTimeMillis() - startTime) + " ms");
          break;
        case FAILURE_ERROR:
          Log.w(TAG, "Did not finish catching up due to an error. " + (System.currentTimeMillis() - startTime) + " ms");
          break;
        case SKIPPED_ALREADY_CAUGHT_UP:
          Log.i(TAG, "Already caught up. " + (System.currentTimeMillis() - startTime) + " ms");
          break;
        case SKIPPED_ALREADY_RUNNING:
          Log.i(TAG, "Already in the process of catching up. " + (System.currentTimeMillis() - startTime) + " ms");
          break;
      }
    });
  }

  @Override
  protected void attachBaseContext(Context base) {
    super.attachBaseContext(DynamicLanguageContextWrapper.updateContext(base, TextSecurePreferences.getLanguage(base)));
  }

  private static class ProviderInitializationException extends RuntimeException {
  }
}

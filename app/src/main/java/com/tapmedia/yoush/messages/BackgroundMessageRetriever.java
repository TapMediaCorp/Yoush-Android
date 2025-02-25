package com.tapmedia.yoush.messages;

import android.content.Context;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.tapmedia.yoush.ApplicationContext;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobmanager.impl.NetworkConstraint;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.util.PowerManagerCompat;
import com.tapmedia.yoush.util.ServiceUtil;
import com.tapmedia.yoush.util.TextSecurePreferences;
import com.tapmedia.yoush.util.WakeLockUtil;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Retrieves messages while the app is in the background via provided {@link MessageRetrievalStrategy}'s.
 */
public class BackgroundMessageRetriever {

  private static final String TAG = Log.tag(BackgroundMessageRetriever.class);

  private static final String WAKE_LOCK_TAG  = "MessageRetriever";

  private static final Semaphore ACTIVE_LOCK = new Semaphore(2);

  private static final long CATCHUP_TIMEOUT = TimeUnit.SECONDS.toMillis(60);
  private static final long NORMAL_TIMEOUT  = TimeUnit.SECONDS.toMillis(10);

  /**
   * @return False if the retrieval failed and should be rescheduled, otherwise true.
   */
  @WorkerThread
  public boolean retrieveMessages(@NonNull Context context, MessageRetrievalStrategy... strategies) {
    if (shouldIgnoreFetch(context)) {
      Log.i(TAG, "Skipping retrieval -- app is in the foreground.");
      return true;
    }

    if (!ACTIVE_LOCK.tryAcquire()) {
      Log.i(TAG, "Skipping retrieval -- there's already one enqueued.");
      return true;
    }

    synchronized (this) {
      PowerManager.WakeLock wakeLock = null;

      try {
        wakeLock = WakeLockUtil.acquire(context, PowerManager.PARTIAL_WAKE_LOCK, TimeUnit.SECONDS.toMillis(60), WAKE_LOCK_TAG);

        TextSecurePreferences.setNeedsMessagePull(context, true);

        long         startTime    = System.currentTimeMillis();
        PowerManager powerManager = ServiceUtil.getPowerManager(context);
        boolean      doze         = PowerManagerCompat.isDeviceIdleMode(powerManager);
        boolean      network      = new NetworkConstraint.Factory(ApplicationContext.getInstance(context)).create().isMet();

        if (doze || !network) {
          Log.w(TAG, "We may be operating in a constrained environment. Doze: " + doze + " Network: " + network);
        }

        if (ApplicationDependencies.getInitialMessageRetriever().isCaughtUp()) {
          Log.i(TAG, "Performing normal message fetch.");
          return executeBackgroundRetrieval(context, startTime, strategies);
        } else {
          Log.i(TAG, "Performing initial message fetch.");
          InitialMessageRetriever.Result result = ApplicationDependencies.getInitialMessageRetriever().begin(CATCHUP_TIMEOUT);
          if (result == InitialMessageRetriever.Result.SUCCESS) {
            Log.i(TAG, "Initial message request was completed successfully. " + logSuffix(startTime));
            TextSecurePreferences.setNeedsMessagePull(context, false);
            return true;
          } else {
            Log.w(TAG, "Initial message fetch returned result " + result + ", so doing a normal message fetch.");
            return executeBackgroundRetrieval(context, System.currentTimeMillis(), strategies);
          }
        }
      } finally {
        WakeLockUtil.release(wakeLock, WAKE_LOCK_TAG);
        ACTIVE_LOCK.release();
      }
    }
  }

  private boolean executeBackgroundRetrieval(@NonNull Context context, long startTime, @NonNull MessageRetrievalStrategy[] strategies) {
    boolean success = false;

    for (MessageRetrievalStrategy strategy : strategies) {
      if (shouldIgnoreFetch(context)) {
        Log.i(TAG, "Stopping further strategy attempts -- app is in the foreground." + logSuffix(startTime));
        success = true;
        break;
      }

      Log.i(TAG, "Attempting strategy: " + strategy.toString() + logSuffix(startTime));

      if (strategy.execute(NORMAL_TIMEOUT)) {
        Log.i(TAG, "Strategy succeeded: " + strategy.toString() + logSuffix(startTime));
        success = true;
        break;
      } else {
        Log.w(TAG, "Strategy failed: " + strategy.toString() + logSuffix(startTime));
      }
    }

    if (success) {
      TextSecurePreferences.setNeedsMessagePull(context, false);
    } else {
      Log.w(TAG, "All strategies failed!" + logSuffix(startTime));
    }

    return success;
  }

  /**
   * @return True if there is no need to execute a message fetch, because the websocket will take
   *         care of it.
   */
  public static boolean shouldIgnoreFetch(@NonNull Context context) {
    return ApplicationContext.getInstance(context).isAppVisible() &&
           !ApplicationDependencies.getSignalServiceNetworkAccess().isCensored(context);
  }

  private static String logSuffix(long startTime) {
    return " (" + (System.currentTimeMillis() - startTime) + " ms elapsed)";
  }
}

package com.tapmedia.yoush.gcm;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.messages.BackgroundMessageRetriever;
import com.tapmedia.yoush.messages.RestStrategy;
import com.tapmedia.yoush.util.ServiceUtil;
import com.tapmedia.yoush.util.TextSecurePreferences;
import com.tapmedia.yoush.util.concurrent.SignalExecutors;

/**
 * Pulls down messages. Used when we fail to pull down messages in {@link FcmReceiveService}.
 */
@RequiresApi(26)
public class FcmJobService extends JobService {

  private static final String TAG = FcmJobService.class.getSimpleName();

  private static final int ID = 1337;

  @RequiresApi(26)
  public static void schedule(@NonNull Context context) {
    JobInfo.Builder jobInfoBuilder = new JobInfo.Builder(ID, new ComponentName(context, FcmJobService.class))
                                                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                                                .setBackoffCriteria(0, JobInfo.BACKOFF_POLICY_LINEAR)
                                                .setPersisted(true);

    ServiceUtil.getJobScheduler(context).schedule(jobInfoBuilder.build());
  }

  @Override
  public boolean onStartJob(JobParameters params) {
    Log.d(TAG, "onStartJob()");

    if (BackgroundMessageRetriever.shouldIgnoreFetch(this)) {
      Log.i(TAG, "App is foregrounded. No need to run.");
      return false;
    }

    SignalExecutors.UNBOUNDED.execute(() -> {
      Context                    context   = getApplicationContext();
      BackgroundMessageRetriever retriever = ApplicationDependencies.getBackgroundMessageRetriever();
      boolean                    success   = retriever.retrieveMessages(context, new RestStrategy(), new RestStrategy());

      if (success) {
        Log.i(TAG, "Successfully retrieved messages.");
        jobFinished(params, false);
      } else {
        Log.w(TAG, "Failed to retrieve messages. Scheduling a retry.");
        jobFinished(params, true);
      }
    });

    return true;
  }

  @Override
  public boolean onStopJob(JobParameters params) {
    Log.d(TAG, "onStopJob()");
    return TextSecurePreferences.getNeedsMessagePull(getApplicationContext());
  }
}

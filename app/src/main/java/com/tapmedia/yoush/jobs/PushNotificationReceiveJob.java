package com.tapmedia.yoush.jobs;

import android.content.Context;
import androidx.annotation.NonNull;

import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.messages.BackgroundMessageRetriever;
import com.tapmedia.yoush.messages.RestStrategy;
import com.tapmedia.yoush.jobmanager.Data;
import com.tapmedia.yoush.jobmanager.Job;
import com.tapmedia.yoush.jobmanager.impl.NetworkConstraint;
import com.tapmedia.yoush.logging.Log;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

public class PushNotificationReceiveJob extends BaseJob {

  public static final String KEY = "PushNotificationReceiveJob";

  private static final String TAG = PushNotificationReceiveJob.class.getSimpleName();

  public PushNotificationReceiveJob(Context context) {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setQueue("__notification_received")
                           .setMaxAttempts(3)
                           .setMaxInstances(1)
                           .build());
    setContext(context);
  }

  private PushNotificationReceiveJob(@NonNull Job.Parameters parameters) {
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
  public void onRun() throws IOException {
    BackgroundMessageRetriever retriever = ApplicationDependencies.getBackgroundMessageRetriever();
    boolean                    result    = retriever.retrieveMessages(context, new RestStrategy());

    if (result) {
      Log.i(TAG, "Successfully pulled messages.");
    } else {
      throw new PushNetworkException("Failed to pull messages.");
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    Log.w(TAG, e);
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "***** Failed to download pending message!");
//    MessageNotifier.notifyMessagesPending(getContext());
  }

  private static String timeSuffix(long startTime) {
    return " (" + (System.currentTimeMillis() - startTime) + " ms elapsed)";
  }

  public static final class Factory implements Job.Factory<PushNotificationReceiveJob> {
    @Override
    public @NonNull PushNotificationReceiveJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new PushNotificationReceiveJob(parameters);
    }
  }
}

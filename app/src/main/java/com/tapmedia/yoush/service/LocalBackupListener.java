package com.tapmedia.yoush.service;


import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobs.LocalBackupJob;
import com.tapmedia.yoush.util.TextSecurePreferences;

import java.util.concurrent.TimeUnit;

public class LocalBackupListener extends PersistentAlarmManagerListener {

  private static final long INTERVAL = TimeUnit.DAYS.toMillis(1);

  @Override
  protected long getNextScheduledExecutionTime(Context context) {
    return TextSecurePreferences.getNextBackupTime(context);
  }

  @Override
  protected long onAlarm(Context context, long scheduledTime) {
    if (TextSecurePreferences.isBackupEnabled(context)) {
      ApplicationDependencies.getJobManager().add(new LocalBackupJob());
    }

    return setNextBackupTimeToIntervalFromNow(context);
  }

  public static void schedule(Context context) {
    if (TextSecurePreferences.isBackupEnabled(context)) {
      new LocalBackupListener().onReceive(context, new Intent());
    }
  }

  public static long setNextBackupTimeToIntervalFromNow(@NonNull Context context) {
    long nextTime = System.currentTimeMillis() + INTERVAL;
    TextSecurePreferences.setNextBackupTime(context, nextTime);

    return nextTime;
  }
}

package com.tapmedia.yoush.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.tapmedia.yoush.ApplicationContext;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobs.PushNotificationReceiveJob;

public class BootReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    ApplicationDependencies.getJobManager().add(new PushNotificationReceiveJob(context));
  }
}

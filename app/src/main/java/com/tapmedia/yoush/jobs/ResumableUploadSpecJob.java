package com.tapmedia.yoush.jobs;

import androidx.annotation.NonNull;

import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobmanager.Data;
import com.tapmedia.yoush.jobmanager.Job;
import com.tapmedia.yoush.jobmanager.impl.NetworkConstraint;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.util.FeatureFlags;
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ResumableUploadSpecJob extends BaseJob {

  private static final String TAG = Log.tag(ResumableUploadSpecJob.class);

  static final String KEY_RESUME_SPEC = "resume_spec";

  public static final String KEY = "ResumableUploadSpecJob";

  public ResumableUploadSpecJob() {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build());
  }

  private ResumableUploadSpecJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  protected void onRun() throws Exception {
    if (!FeatureFlags.attachmentsV3()) {
      Log.i(TAG, "Attachments V3 is not enabled so there is nothing to do!");
      return;
    }

    ResumableUploadSpec resumableUploadSpec = ApplicationDependencies.getSignalServiceMessageSender()
                                                                     .getResumableUploadSpec();

    setOutputData(new Data.Builder()
                          .putString(KEY_RESUME_SPEC, resumableUploadSpec.serialize())
                          .build());
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof IOException;
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
  public void onFailure() {

  }

  public static class Factory implements Job.Factory<ResumableUploadSpecJob> {

    @Override
    public @NonNull ResumableUploadSpecJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new ResumableUploadSpecJob(parameters);
    }
  }
}

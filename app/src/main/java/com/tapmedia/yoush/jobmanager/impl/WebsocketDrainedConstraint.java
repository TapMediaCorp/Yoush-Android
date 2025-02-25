package com.tapmedia.yoush.jobmanager.impl;

import android.app.job.JobInfo;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobmanager.Constraint;

/**
 * A constraint that is met once we have pulled down all messages from the websocket during initial
 * load. See {@link com.tapmedia.yoush.messages.InitialMessageRetriever}.
 */
public final class WebsocketDrainedConstraint implements Constraint {

  public static final String KEY = "WebsocketDrainedConstraint";

  private WebsocketDrainedConstraint() {
  }

  @Override
  public boolean isMet() {
    return ApplicationDependencies.getInitialMessageRetriever().isCaughtUp();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @RequiresApi(26)
  @Override
  public void applyToJobInfo(@NonNull JobInfo.Builder jobInfoBuilder) {
  }

  public static final class Factory implements Constraint.Factory<WebsocketDrainedConstraint> {

    @Override
    public WebsocketDrainedConstraint create() {
      return new WebsocketDrainedConstraint();
    }
  }
}

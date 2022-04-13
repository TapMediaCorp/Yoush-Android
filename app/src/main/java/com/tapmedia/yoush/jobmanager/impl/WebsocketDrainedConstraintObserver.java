package com.tapmedia.yoush.jobmanager.impl;

import androidx.annotation.NonNull;

import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobmanager.ConstraintObserver;

/**
 * An observer for {@link WebsocketDrainedConstraint}. Will fire when the
 * {@link com.tapmedia.yoush.messages.InitialMessageRetriever} is caught up.
 */
public class WebsocketDrainedConstraintObserver implements ConstraintObserver {

  private static final String REASON = WebsocketDrainedConstraintObserver.class.getSimpleName();

  private volatile Notifier notifier;

  public WebsocketDrainedConstraintObserver() {
    ApplicationDependencies.getInitialMessageRetriever().addListener(() -> {
      if (notifier != null) {
        notifier.onConstraintMet(REASON);
      }
    });
  }

  @Override
  public void register(@NonNull Notifier notifier) {
    this.notifier = notifier;
  }
}

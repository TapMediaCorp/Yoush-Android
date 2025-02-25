package com.tapmedia.yoush.jobmanager.impl;

import android.app.Application;
import android.app.job.JobInfo;
import androidx.annotation.NonNull;

import com.tapmedia.yoush.jobmanager.Constraint;
import com.tapmedia.yoush.util.TextSecurePreferences;

public class NetworkOrCellServiceConstraint implements Constraint {

  public static final String KEY        = "NetworkOrCellServiceConstraint";
  public static final String LEGACY_KEY = "CellServiceConstraint";

  private final Application       application;
  private final NetworkConstraint networkConstraint;

  private NetworkOrCellServiceConstraint(@NonNull Application application) {
    this.application       = application;
    this.networkConstraint = new NetworkConstraint.Factory(application).create();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public boolean isMet() {
    if (TextSecurePreferences.isWifiSmsEnabled(application)) {
      return networkConstraint.isMet() || hasCellService(application);
    } else {
      return hasCellService(application);
    }
  }

  @Override
  public void applyToJobInfo(@NonNull JobInfo.Builder jobInfoBuilder) {
  }

  private static boolean hasCellService(@NonNull Application application) {
    return CellServiceConstraintObserver.getInstance(application).hasService();
  }

  public static class Factory implements Constraint.Factory<NetworkOrCellServiceConstraint> {

    private final Application application;

    public Factory(@NonNull Application application) {
      this.application = application;
    }

    @Override
    public NetworkOrCellServiceConstraint create() {
      return new NetworkOrCellServiceConstraint(application);
    }
  }
}

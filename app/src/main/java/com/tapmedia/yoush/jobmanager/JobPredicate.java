package com.tapmedia.yoush.jobmanager;

import androidx.annotation.NonNull;

import com.tapmedia.yoush.jobmanager.persistence.JobSpec;

public interface JobPredicate {
  JobPredicate NONE = jobSpec -> true;

  boolean shouldRun(@NonNull JobSpec jobSpec);
}

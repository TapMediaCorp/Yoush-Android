package com.tapmedia.yoush.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import net.sqlcipher.database.SQLiteDatabase;

import com.tapmedia.yoush.database.helpers.SQLCipherOpenHelper;
import com.tapmedia.yoush.jobmanager.persistence.ConstraintSpec;
import com.tapmedia.yoush.jobmanager.persistence.DependencySpec;
import com.tapmedia.yoush.jobmanager.persistence.FullSpec;
import com.tapmedia.yoush.jobmanager.persistence.JobSpec;

import java.util.LinkedList;
import java.util.List;

public class JobDatabase extends Database {

  public static String JOBS_TABLE_NAME         = "job_spec";
  public static String CONSTRAINTS_TABLE_NAME  = "constraint_spec";
  public static String DEPENDENCIES_TABLE_NAME = "dependency_spec";

  public static final String[] CREATE_TABLE = new String[] { Jobs.CREATE_TABLE,
                                                             Constraints.CREATE_TABLE,
                                                             Dependencies.CREATE_TABLE };

  private static final class Jobs {
    private static final String TABLE_NAME            = JOBS_TABLE_NAME;
    private static final String ID                    = "_id";
    private static final String JOB_SPEC_ID           = "job_spec_id";
    private static final String FACTORY_KEY           = "factory_key";
    private static final String QUEUE_KEY             = "queue_key";
    private static final String CREATE_TIME           = "create_time";
    private static final String NEXT_RUN_ATTEMPT_TIME = "next_run_attempt_time";
    private static final String RUN_ATTEMPT           = "run_attempt";
    private static final String MAX_ATTEMPTS          = "max_attempts";
    private static final String MAX_BACKOFF           = "max_backoff";
    private static final String MAX_INSTANCES         = "max_instances";
    private static final String LIFESPAN              = "lifespan";
    private static final String SERIALIZED_DATA       = "serialized_data";
    private static final String SERIALIZED_INPUT_DATA = "serialized_input_data";
    private static final String IS_RUNNING            = "is_running";

    private static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID                    + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                    JOB_SPEC_ID           + " TEXT UNIQUE, " +
                                                                                    FACTORY_KEY           + " TEXT, " +
                                                                                    QUEUE_KEY             + " TEXT, " +
                                                                                    CREATE_TIME           + " INTEGER, " +
                                                                                    NEXT_RUN_ATTEMPT_TIME + " INTEGER, " +
                                                                                    RUN_ATTEMPT           + " INTEGER, " +
                                                                                    MAX_ATTEMPTS          + " INTEGER, " +
                                                                                    MAX_BACKOFF           + " INTEGER, " +
                                                                                    MAX_INSTANCES         + " INTEGER, " +
                                                                                    LIFESPAN              + " INTEGER, " +
                                                                                    SERIALIZED_DATA       + " TEXT, " +
                                                                                    SERIALIZED_INPUT_DATA + " TEXT DEFAULT NULL, " +
                                                                                    IS_RUNNING            + " INTEGER)";
  }

  private static final class Constraints {
    private static final String TABLE_NAME  = CONSTRAINTS_TABLE_NAME;
    private static final String ID          = "_id";
    private static final String JOB_SPEC_ID = "job_spec_id";
    private static final String FACTORY_KEY = "factory_key";

    private static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID          + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                    JOB_SPEC_ID + " TEXT, " +
                                                                                    FACTORY_KEY + " TEXT, " +
                                                                                    "UNIQUE(" + JOB_SPEC_ID + ", " + FACTORY_KEY + "))";
  }

  private static final class Dependencies {
    private static final String TABLE_NAME             = DEPENDENCIES_TABLE_NAME;
    private static final String ID                     = "_id";
    private static final String JOB_SPEC_ID            = "job_spec_id";
    private static final String DEPENDS_ON_JOB_SPEC_ID = "depends_on_job_spec_id";

    private static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID                     + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                    JOB_SPEC_ID            + " TEXT, " +
                                                                                    DEPENDS_ON_JOB_SPEC_ID + " TEXT, " +
                                                                                    "UNIQUE(" + JOB_SPEC_ID + ", " + DEPENDS_ON_JOB_SPEC_ID + "))";
  }


  public JobDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public synchronized void insertJobs(@NonNull List<FullSpec> fullSpecs) {
    if (Stream.of(fullSpecs).map(FullSpec::getJobSpec).allMatch(JobSpec::isMemoryOnly)) {
      return;
    }

    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();

    try {
      for (FullSpec fullSpec : fullSpecs) {
        insertJobSpec(db, fullSpec.getJobSpec());
        insertConstraintSpecs(db, fullSpec.getConstraintSpecs());
        insertDependencySpecs(db, fullSpec.getDependencySpecs());
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public synchronized @NonNull List<JobSpec> getAllJobSpecs() {
    List<JobSpec> jobs = new LinkedList<>();

    try (Cursor cursor = databaseHelper.getReadableDatabase().query(Jobs.TABLE_NAME, null, null, null, null, null, Jobs.CREATE_TIME + ", " + Jobs.ID + " ASC")) {
      while (cursor != null && cursor.moveToNext()) {
        jobs.add(jobSpecFromCursor(cursor));
      }
    }

    return jobs;
  }

  public synchronized void updateJobRunningState(@NonNull String id, boolean isRunning) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(Jobs.IS_RUNNING, isRunning ? 1 : 0);

    String   query = Jobs.JOB_SPEC_ID + " = ?";
    String[] args  = new String[]{ id };

    databaseHelper.getWritableDatabase().update(Jobs.TABLE_NAME, contentValues, query, args);
  }

  public synchronized void updateJobAfterRetry(@NonNull String id, boolean isRunning, int runAttempt, long nextRunAttemptTime, @NonNull String serializedData) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(Jobs.IS_RUNNING, isRunning ? 1 : 0);
    contentValues.put(Jobs.RUN_ATTEMPT, runAttempt);
    contentValues.put(Jobs.NEXT_RUN_ATTEMPT_TIME, nextRunAttemptTime);
    contentValues.put(Jobs.SERIALIZED_DATA, serializedData);

    String   query = Jobs.JOB_SPEC_ID + " = ?";
    String[] args  = new String[]{ id };

    databaseHelper.getWritableDatabase().update(Jobs.TABLE_NAME, contentValues, query, args);
  }

  public synchronized void updateAllJobsToBePending() {
    ContentValues contentValues = new ContentValues();
    contentValues.put(Jobs.IS_RUNNING, 0);

    databaseHelper.getWritableDatabase().update(Jobs.TABLE_NAME, contentValues, null, null);
  }

  public synchronized void updateJobs(@NonNull List<JobSpec> jobs) {
    if (Stream.of(jobs).allMatch(JobSpec::isMemoryOnly)) {
      return;
    }

    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();

    try {
      Stream.of(jobs)
            .filterNot(JobSpec::isMemoryOnly)
            .forEach(job -> {
              ContentValues values = new ContentValues();
              values.put(Jobs.JOB_SPEC_ID, job.getId());
              values.put(Jobs.FACTORY_KEY, job.getFactoryKey());
              values.put(Jobs.QUEUE_KEY, job.getQueueKey());
              values.put(Jobs.CREATE_TIME, job.getCreateTime());
              values.put(Jobs.NEXT_RUN_ATTEMPT_TIME, job.getNextRunAttemptTime());
              values.put(Jobs.RUN_ATTEMPT, job.getRunAttempt());
              values.put(Jobs.MAX_ATTEMPTS, job.getMaxAttempts());
              values.put(Jobs.MAX_BACKOFF, job.getMaxBackoff());
              values.put(Jobs.MAX_INSTANCES, job.getMaxInstances());
              values.put(Jobs.LIFESPAN, job.getLifespan());
              values.put(Jobs.SERIALIZED_DATA, job.getSerializedData());
              values.put(Jobs.SERIALIZED_INPUT_DATA, job.getSerializedInputData());
              values.put(Jobs.IS_RUNNING, job.isRunning() ? 1 : 0);

              String   query = Jobs.JOB_SPEC_ID + " = ?";
              String[] args  = new String[]{ job.getId() };

              db.update(Jobs.TABLE_NAME, values, query, args);
            });

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public synchronized void deleteJobs(@NonNull List<String> jobIds) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();

    try {
      for (String jobId : jobIds) {
        String[] arg = new String[]{jobId};

        db.delete(Jobs.TABLE_NAME, Jobs.JOB_SPEC_ID + " = ?", arg);
        db.delete(Constraints.TABLE_NAME, Constraints.JOB_SPEC_ID + " = ?", arg);
        db.delete(Dependencies.TABLE_NAME, Dependencies.JOB_SPEC_ID + " = ?", arg);
        db.delete(Dependencies.TABLE_NAME, Dependencies.DEPENDS_ON_JOB_SPEC_ID + " = ?", arg);
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public synchronized @NonNull List<ConstraintSpec> getAllConstraintSpecs() {
    List<ConstraintSpec> constraints = new LinkedList<>();

    try (Cursor cursor = databaseHelper.getReadableDatabase().query(Constraints.TABLE_NAME, null, null, null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        constraints.add(constraintSpecFromCursor(cursor));
      }
    }

    return constraints;
  }

  public synchronized @NonNull List<DependencySpec> getAllDependencySpecs() {
    List<DependencySpec> dependencies = new LinkedList<>();

    try (Cursor cursor = databaseHelper.getReadableDatabase().query(Dependencies.TABLE_NAME, null, null, null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        dependencies.add(dependencySpecFromCursor(cursor));
      }
    }

    return dependencies;
  }

  private void insertJobSpec(@NonNull SQLiteDatabase db, @NonNull JobSpec job) {
    if (job.isMemoryOnly()) {
      return;
    }

    ContentValues contentValues = new ContentValues();
    contentValues.put(Jobs.JOB_SPEC_ID, job.getId());
    contentValues.put(Jobs.FACTORY_KEY, job.getFactoryKey());
    contentValues.put(Jobs.QUEUE_KEY, job.getQueueKey());
    contentValues.put(Jobs.CREATE_TIME, job.getCreateTime());
    contentValues.put(Jobs.NEXT_RUN_ATTEMPT_TIME, job.getNextRunAttemptTime());
    contentValues.put(Jobs.RUN_ATTEMPT, job.getRunAttempt());
    contentValues.put(Jobs.MAX_ATTEMPTS, job.getMaxAttempts());
    contentValues.put(Jobs.MAX_BACKOFF, job.getMaxBackoff());
    contentValues.put(Jobs.MAX_INSTANCES, job.getMaxInstances());
    contentValues.put(Jobs.LIFESPAN, job.getLifespan());
    contentValues.put(Jobs.SERIALIZED_DATA, job.getSerializedData());
    contentValues.put(Jobs.SERIALIZED_INPUT_DATA, job.getSerializedInputData());
    contentValues.put(Jobs.IS_RUNNING, job.isRunning() ? 1 : 0);

    db.insertWithOnConflict(Jobs.TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_IGNORE);
  }

  private void insertConstraintSpecs(@NonNull SQLiteDatabase db, @NonNull List<ConstraintSpec> constraints) {
    Stream.of(constraints)
          .filterNot(ConstraintSpec::isMemoryOnly)
          .forEach(constraintSpec -> {
            ContentValues contentValues = new ContentValues();
            contentValues.put(Constraints.JOB_SPEC_ID, constraintSpec.getJobSpecId());
            contentValues.put(Constraints.FACTORY_KEY, constraintSpec.getFactoryKey());
            db.insertWithOnConflict(Constraints.TABLE_NAME, null ,contentValues, SQLiteDatabase.CONFLICT_IGNORE);
          });
  }

  private void insertDependencySpecs(@NonNull SQLiteDatabase db, @NonNull List<DependencySpec> dependencies) {
    Stream.of(dependencies)
          .filterNot(DependencySpec::isMemoryOnly)
          .forEach(dependencySpec -> {
            ContentValues contentValues = new ContentValues();
            contentValues.put(Dependencies.JOB_SPEC_ID, dependencySpec.getJobId());
            contentValues.put(Dependencies.DEPENDS_ON_JOB_SPEC_ID, dependencySpec.getDependsOnJobId());
            db.insertWithOnConflict(Dependencies.TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_IGNORE);
          });
  }

  private @NonNull JobSpec jobSpecFromCursor(@NonNull Cursor cursor) {
    return new JobSpec(cursor.getString(cursor.getColumnIndexOrThrow(Jobs.JOB_SPEC_ID)),
                       cursor.getString(cursor.getColumnIndexOrThrow(Jobs.FACTORY_KEY)),
                       cursor.getString(cursor.getColumnIndexOrThrow(Jobs.QUEUE_KEY)),
                       cursor.getLong(cursor.getColumnIndexOrThrow(Jobs.CREATE_TIME)),
                       cursor.getLong(cursor.getColumnIndexOrThrow(Jobs.NEXT_RUN_ATTEMPT_TIME)),
                       cursor.getInt(cursor.getColumnIndexOrThrow(Jobs.RUN_ATTEMPT)),
                       cursor.getInt(cursor.getColumnIndexOrThrow(Jobs.MAX_ATTEMPTS)),
                       cursor.getLong(cursor.getColumnIndexOrThrow(Jobs.MAX_BACKOFF)),
                       cursor.getLong(cursor.getColumnIndexOrThrow(Jobs.LIFESPAN)),
                       cursor.getInt(cursor.getColumnIndexOrThrow(Jobs.MAX_INSTANCES)),
                       cursor.getString(cursor.getColumnIndexOrThrow(Jobs.SERIALIZED_DATA)),
                       cursor.getString(cursor.getColumnIndexOrThrow(Jobs.SERIALIZED_INPUT_DATA)),
                       cursor.getInt(cursor.getColumnIndexOrThrow(Jobs.IS_RUNNING)) == 1,
                       false);
  }

  private @NonNull ConstraintSpec constraintSpecFromCursor(@NonNull Cursor cursor) {
    return new ConstraintSpec(cursor.getString(cursor.getColumnIndexOrThrow(Constraints.JOB_SPEC_ID)),
                              cursor.getString(cursor.getColumnIndexOrThrow(Constraints.FACTORY_KEY)),
                              false);
  }

  private @NonNull DependencySpec dependencySpecFromCursor(@NonNull Cursor cursor) {
    return new DependencySpec(cursor.getString(cursor.getColumnIndexOrThrow(Dependencies.JOB_SPEC_ID)),
                              cursor.getString(cursor.getColumnIndexOrThrow(Dependencies.DEPENDS_ON_JOB_SPEC_ID)),
                              false);
  }
}

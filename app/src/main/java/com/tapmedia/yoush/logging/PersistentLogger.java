package com.tapmedia.yoush.logging;

import android.annotation.SuppressLint;
import android.content.Context;
import androidx.annotation.AnyThread;
import androidx.annotation.WorkerThread;

import com.tapmedia.yoush.database.NoExternalStorageException;
import com.tapmedia.yoush.util.concurrent.ListenableFuture;
import com.tapmedia.yoush.util.concurrent.SettableFuture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@SuppressLint("LogNotSignal")
public final class PersistentLogger extends Log.Logger {

  private static final String TAG     = PersistentLogger.class.getSimpleName();

  private static final String LOG_V   = "V";
  private static final String LOG_D   = "D";
  private static final String LOG_I   = "I";
  private static final String LOG_W   = "W";
  private static final String LOG_E   = "E";
  private static final String LOG_WTF = "A";

  private static final String           LOG_DIRECTORY   = "log";
  private static final String           FILENAME_PREFIX = "log-";
  private static final int              MAX_LOG_FILES   = 7;
  private static final int              MAX_LOG_SIZE    = 300 * 1024;
  private static final SimpleDateFormat DATE_FORMAT     = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS zzz");

  private final Context  context;
  private final Executor executor;
  private final byte[]   secret;

  private LogFile.Writer writer;

  public PersistentLogger(Context context) {
    this.context  = context.getApplicationContext();
    this.secret   = LogSecretProvider.getOrCreateAttachmentSecret(context);
    this.executor = Executors.newSingleThreadExecutor(r -> {
      Thread thread = new Thread(r, "signal-PersistentLogger");
      thread.setPriority(Thread.MIN_PRIORITY);
      return thread;
    });

    executor.execute(this::initializeWriter);
  }

  @Override
  public void v(String tag, String message, Throwable t) {
    write(LOG_V, tag, message, t);
  }

  @Override
  public void d(String tag, String message, Throwable t) {
    write(LOG_D, tag, message, t);
  }

  @Override
  public void i(String tag, String message, Throwable t) {
    write(LOG_I, tag, message, t);
  }

  @Override
  public void w(String tag, String message, Throwable t) {
    write(LOG_W, tag, message, t);
  }

  @Override
  public void e(String tag, String message, Throwable t) {
    write(LOG_E, tag, message, t);
  }

  @Override
  public void wtf(String tag, String message, Throwable t) {
    write(LOG_WTF, tag, message, t);
  }

  @Override
  public void blockUntilAllWritesFinished() {
    CountDownLatch latch = new CountDownLatch(1);

    executor.execute(latch::countDown);

    try {
      latch.await();
    } catch (InterruptedException e) {
      android.util.Log.w(TAG, "Failed to wait for all writes.");
    }
  }

  @WorkerThread
  public ListenableFuture<CharSequence> getLogs() {
    final SettableFuture<CharSequence> future = new SettableFuture<>();

    executor.execute(() -> {
      StringBuilder builder = new StringBuilder();

      try {
        File[] logs = getSortedLogFiles();
        for (int i = logs.length - 1; i >= 0; i--) {
          try {
            LogFile.Reader reader = new LogFile.Reader(secret, logs[i]);
            builder.append(reader.readAll());
          } catch (IOException e) {
            android.util.Log.w(TAG, "Failed to read log at index " + i + ". Removing reference.");
            logs[i].delete();
          }
        }

        future.set(builder);
      } catch (NoExternalStorageException e) {
        future.setException(e);
      }
    });

    return future;
  }

  @WorkerThread
  private void initializeWriter() {
    try {
      writer = new LogFile.Writer(secret, getOrCreateActiveLogFile());
    } catch (NoExternalStorageException | IOException e) {
      android.util.Log.e(TAG, "Failed to initialize writer.", e);
    }
  }

  @AnyThread
  private void write(String level, String tag, String message, Throwable t) {
    executor.execute(() -> {
      try {
        if (writer == null) {
          return;
        }

        if (writer.getLogSize() >= MAX_LOG_SIZE) {
          writer.close();
          writer = new LogFile.Writer(secret, createNewLogFile());
          trimLogFilesOverMax();
        }

        for (String entry : buildLogEntries(level, tag, message, t)) {
          writer.writeEntry(entry);
        }

      } catch (NoExternalStorageException e) {
        android.util.Log.w(TAG, "Cannot persist logs.", e);
      } catch (IOException e) {
        android.util.Log.w(TAG, "Failed to write line. Deleting all logs and starting over.");
        deleteAllLogs();
        initializeWriter();
      }
    });
  }

  private void trimLogFilesOverMax() throws NoExternalStorageException {
    File[] logs = getSortedLogFiles();
    if (logs.length > MAX_LOG_FILES) {
      for (int i = MAX_LOG_FILES; i < logs.length; i++) {
        logs[i].delete();
      }
    }
  }

  private void deleteAllLogs() {
    try {
      File[] logs = getSortedLogFiles();
      for (File log : logs) {
        log.delete();
      }
    } catch (NoExternalStorageException e) {
      android.util.Log.w(TAG, "Was unable to delete logs.", e);
    }
  }

  private File getOrCreateActiveLogFile() throws NoExternalStorageException {
    File[] logs = getSortedLogFiles();
    if (logs.length > 0) {
      return logs[0];
    }

    return createNewLogFile();
  }

  private File createNewLogFile() throws NoExternalStorageException {
    return new File(getOrCreateLogDirectory(), FILENAME_PREFIX + System.currentTimeMillis());
  }

  private File[] getSortedLogFiles() throws NoExternalStorageException {
    File[] logs = getOrCreateLogDirectory().listFiles();
    if (logs != null) {
      Arrays.sort(logs, (o1, o2) -> o2.getName().compareTo(o1.getName()));
      return logs;
    }
    return new File[0];
  }

  private File getOrCreateLogDirectory() throws NoExternalStorageException {
    File logDir = new File(context.getCacheDir(), LOG_DIRECTORY);
    if (!logDir.exists() && !logDir.mkdir()) {
      throw new NoExternalStorageException("Unable to create log directory.");
    }

    return logDir;
  }

  private List<String> buildLogEntries(String level, String tag, String message, Throwable t) {
    List<String> entries = new LinkedList<>();
    Date         date    = new Date();

    entries.add(buildEntry(level, tag, message, date));

    if (t != null) {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      t.printStackTrace(new PrintStream(outputStream));

      String   trace = new String(outputStream.toByteArray());
      String[] lines = trace.split("\\n");

      for (String line : lines) {
        entries.add(buildEntry(level, tag, line, date));
      }
    }

    return entries;
  }

  private String buildEntry(String level, String tag, String message, Date date) {
    return DATE_FORMAT.format(date) + ' ' + level + ' ' + tag + ": " + message;
  }
}

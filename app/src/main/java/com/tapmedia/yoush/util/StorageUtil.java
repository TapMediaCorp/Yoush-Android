package com.tapmedia.yoush.util;

import android.content.Context;
import android.os.Environment;
import androidx.annotation.Nullable;

import com.tapmedia.yoush.database.NoExternalStorageException;

import java.io.File;

public class StorageUtil {

  public static File getBackupDirectory() throws NoExternalStorageException {
    File storage = Environment.getExternalStorageDirectory();

    if (!storage.canWrite()) {
      throw new NoExternalStorageException();
    }

    File signal = new File(storage, "Signal");
    File backups = new File(signal, "Backups");

    if (!backups.exists()) {
      if (!backups.mkdirs()) {
        throw new NoExternalStorageException("Unable to create backup directory...");
      }
    }

    return backups;
  }

  public static File getBackupCacheDirectory(Context context) {
    return context.getExternalCacheDir();
  }

  private static File getSignalStorageDir() throws NoExternalStorageException {
    final File storage = Environment.getExternalStorageDirectory();

    if (!storage.canWrite()) {
      throw new NoExternalStorageException();
    }

    return storage;
  }

  public static boolean canWriteInSignalStorageDir() {
    File storage;

    try {
      storage = getSignalStorageDir();
    } catch (NoExternalStorageException e) {
      return false;
    }

    return storage.canWrite();
  }

  public static File getLegacyBackupDirectory() throws NoExternalStorageException {
    return getSignalStorageDir();
  }

  public static File getVideoDir() throws NoExternalStorageException {
    return new File(getSignalStorageDir(), Environment.DIRECTORY_MOVIES);
  }

  public static File getAudioDir() throws NoExternalStorageException {
    return new File(getSignalStorageDir(), Environment.DIRECTORY_MUSIC);
  }

  public static File getImageDir() throws NoExternalStorageException {
    return new File(getSignalStorageDir(), Environment.DIRECTORY_PICTURES);
  }

  public static File getDownloadDir() throws NoExternalStorageException {
    return new File(getSignalStorageDir(), Environment.DIRECTORY_DOWNLOADS);
  }

  public static @Nullable String getCleanFileName(@Nullable String fileName) {
    if (fileName == null) return null;

    fileName = fileName.replace('\u202D', '\uFFFD');
    fileName = fileName.replace('\u202E', '\uFFFD');

    return fileName;
  }
}

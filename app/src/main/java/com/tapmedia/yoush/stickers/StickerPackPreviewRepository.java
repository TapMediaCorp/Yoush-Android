package com.tapmedia.yoush.stickers;

import android.content.Context;
import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;
import com.google.android.gms.common.util.Hex;

import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.StickerDatabase;
import com.tapmedia.yoush.database.model.StickerPackRecord;
import com.tapmedia.yoush.database.model.StickerRecord;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.util.concurrent.SignalExecutors;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.messages.SignalServiceStickerManifest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class StickerPackPreviewRepository {

  private static final String TAG = Log.tag(StickerPackPreviewRepository.class);

  private final StickerDatabase              stickerDatabase;
  private final SignalServiceMessageReceiver receiver;

  public StickerPackPreviewRepository(@NonNull Context context) {
    this.receiver        = ApplicationDependencies.getSignalServiceMessageReceiver();
    this.stickerDatabase = DatabaseFactory.getStickerDatabase(context);
  }

  public void getStickerManifest(@NonNull String packId,
                                 @NonNull String packKey,
                                 @NonNull Callback<Optional<StickerManifestResult>> callback)
  {
    SignalExecutors.UNBOUNDED.execute(() -> {
      Optional<StickerManifestResult> localManifest = getManifestFromDatabase(packId);

      if (localManifest.isPresent()) {
        Log.d(TAG, "Found manifest locally.");
        callback.onComplete(localManifest);
      } else {
        Log.d(TAG, "Looking for manifest remotely.");
        callback.onComplete(getManifestRemote(packId, packKey));
      }
    });
  }

  @WorkerThread
  private Optional<StickerManifestResult> getManifestFromDatabase(@NonNull String packId) {
    StickerPackRecord record = stickerDatabase.getStickerPack(packId);

    if (record != null && record.isInstalled()) {
      StickerManifest.Sticker       cover    = toSticker(record.getCover());
      List<StickerManifest.Sticker> stickers = getStickersFromDatabase(packId);

      StickerManifest manifest = new StickerManifest(record.getPackId(),
                                                     record.getPackKey(),
                                                     record.getTitle(),
                                                     record.getAuthor(),
                                                     Optional.of(cover),
                                                     stickers);

      return Optional.of(new StickerManifestResult(manifest, record.isInstalled()));
    }

    return Optional.absent();
  }

  @WorkerThread
  private Optional<StickerManifestResult> getManifestRemote(@NonNull String packId, @NonNull String packKey) {
    try {
      byte[]                       packIdBytes    = Hex.stringToBytes(packId);
      byte[]                       packKeyBytes   = Hex.stringToBytes(packKey);
      SignalServiceStickerManifest remoteManifest = receiver.retrieveStickerManifest(packIdBytes, packKeyBytes);
      StickerManifest              localManifest  = new StickerManifest(packId,
                                                                        packKey,
                                                                        remoteManifest.getTitle(),
                                                                        remoteManifest.getAuthor(),
                                                                        toOptionalSticker(packId, packKey, remoteManifest.getCover()),
                                                                        Stream.of(remoteManifest.getStickers())
                                                                              .map(s -> toSticker(packId, packKey, s))
                                                                              .toList());

      return Optional.of(new StickerManifestResult(localManifest, false));
    } catch (IOException | InvalidMessageException e) {
      Log.w(TAG, "Failed to retrieve pack manifest.", e);
    }

    return Optional.absent();
  }

  @WorkerThread
  private List<StickerManifest.Sticker> getStickersFromDatabase(@NonNull String packId) {
    List<StickerManifest.Sticker> stickers = new ArrayList<>();

    try (Cursor cursor = stickerDatabase.getStickersForPack(packId)) {
      StickerDatabase.StickerRecordReader reader = new StickerDatabase.StickerRecordReader(cursor);

      StickerRecord record;
      while ((record = reader.getNext()) != null) {
        stickers.add(toSticker(record));
      }
    }

    return stickers;
  }


  private Optional<StickerManifest.Sticker> toOptionalSticker(@NonNull String packId,
                                                              @NonNull String packKey,
                                                              @NonNull Optional<SignalServiceStickerManifest.StickerInfo> remoteSticker)
  {
    return remoteSticker.isPresent() ? Optional.of(toSticker(packId, packKey, remoteSticker.get()))
                                     : Optional.absent();
  }

  private StickerManifest.Sticker toSticker(@NonNull String packId,
                                            @NonNull String packKey,
                                            @NonNull SignalServiceStickerManifest.StickerInfo remoteSticker)
  {
    return new StickerManifest.Sticker(packId, packKey, remoteSticker.getId(), remoteSticker.getEmoji());
  }

  private StickerManifest.Sticker toSticker(@NonNull StickerRecord record) {
    return new StickerManifest.Sticker(record.getPackId(), record.getPackKey(), record.getStickerId(), record.getEmoji(), record.getUri());
  }

  static class StickerManifestResult {
    private final StickerManifest manifest;
    private final boolean         isInstalled;

    StickerManifestResult(StickerManifest manifest, boolean isInstalled) {
      this.manifest    = manifest;
      this.isInstalled = isInstalled;
    }

    public StickerManifest getManifest() {
      return manifest;
    }

    public boolean isInstalled() {
      return isInstalled;
    }
  }

  interface Callback<T> {
    void onComplete(T result);
  }
}

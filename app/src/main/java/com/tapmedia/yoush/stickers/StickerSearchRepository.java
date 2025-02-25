package com.tapmedia.yoush.stickers;

import android.content.Context;
import android.database.Cursor;
import androidx.annotation.NonNull;

import com.tapmedia.yoush.components.emoji.EmojiUtil;
import com.tapmedia.yoush.database.AttachmentDatabase;
import com.tapmedia.yoush.database.CursorList;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.StickerDatabase;
import com.tapmedia.yoush.database.StickerDatabase.StickerRecordReader;
import com.tapmedia.yoush.database.model.StickerPackRecord;
import com.tapmedia.yoush.database.model.StickerRecord;
import com.tapmedia.yoush.util.concurrent.SignalExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class StickerSearchRepository {

  private final StickerDatabase    stickerDatabase;
  private final AttachmentDatabase attachmentDatabase;

  public StickerSearchRepository(@NonNull Context context) {
    this.stickerDatabase    = DatabaseFactory.getStickerDatabase(context);
    this.attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context);
  }

  public void searchByEmoji(@NonNull String emoji, @NonNull Callback<List<StickerRecord>> callback) {
    SignalExecutors.BOUNDED.execute(() -> {
      String              searchEmoji = EmojiUtil.getCanonicalRepresentation(emoji);
      List<StickerRecord> out         = new ArrayList<>();
      Set<String>         possible    = EmojiUtil.getAllRepresentations(searchEmoji);

      for (String candidate : possible) {
        try (StickerRecordReader reader = new StickerRecordReader(stickerDatabase.getStickersByEmoji(candidate))) {
          StickerRecord record = null;
          while ((record = reader.getNext()) != null) {
            out.add(record);
          }
        }
      }

      callback.onResult(out);
    });
  }

  public void getStickerFeatureAvailability(@NonNull Callback<Boolean> callback) {
    SignalExecutors.BOUNDED.execute(() -> {
      try (Cursor cursor = stickerDatabase.getAllStickerPacks("1")) {
        if (cursor != null && cursor.moveToFirst()) {
          callback.onResult(true);
        } else {
          callback.onResult(attachmentDatabase.hasStickerAttachments());
        }
      }
    });
  }

  private static class StickerModelBuilder implements CursorList.ModelBuilder<StickerRecord> {
    @Override
    public StickerRecord build(@NonNull Cursor cursor) {
      return new StickerRecordReader(cursor).getCurrent();
    }
  }

  public interface Callback<T> {
    void onResult(T result);
  }
}

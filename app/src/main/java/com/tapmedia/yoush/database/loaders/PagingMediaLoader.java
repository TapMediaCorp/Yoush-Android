package com.tapmedia.yoush.database.loaders;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.tapmedia.yoush.attachments.AttachmentId;
import com.tapmedia.yoush.database.AttachmentDatabase;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.MediaDatabase;
import com.tapmedia.yoush.database.MediaDatabase.Sorting;
import com.tapmedia.yoush.mms.PartAuthority;
import com.tapmedia.yoush.util.AsyncLoader;

public final class PagingMediaLoader extends AsyncLoader<Pair<Cursor, Integer>> {

  @SuppressWarnings("unused")
  private static final String TAG = PagingMediaLoader.class.getSimpleName();

  private final Uri     uri;
  private final boolean leftIsRecent;
  private final Sorting sorting;
  private final long    threadId;

  public PagingMediaLoader(@NonNull Context context, long threadId, @NonNull Uri uri, boolean leftIsRecent, @NonNull Sorting sorting) {
    super(context);
    this.threadId     = threadId;
    this.uri          = uri;
    this.leftIsRecent = leftIsRecent;
    this.sorting      = sorting;
  }

  @Override
  public @Nullable Pair<Cursor, Integer> loadInBackground() {
    Cursor cursor = DatabaseFactory.getMediaDatabase(getContext()).getGalleryMediaForThread(threadId, sorting, threadId == MediaDatabase.ALL_THREADS);

    while (cursor.moveToNext()) {
      AttachmentId attachmentId  = new AttachmentId(cursor.getLong(cursor.getColumnIndexOrThrow(AttachmentDatabase.ROW_ID)), cursor.getLong(cursor.getColumnIndexOrThrow(AttachmentDatabase.UNIQUE_ID)));
      Uri          attachmentUri = PartAuthority.getAttachmentDataUri(attachmentId);

      if (attachmentUri.equals(uri)) {
        return new Pair<>(cursor, leftIsRecent ? cursor.getPosition() : cursor.getCount() - 1 - cursor.getPosition());
      }
    }

    return null;
  }
}

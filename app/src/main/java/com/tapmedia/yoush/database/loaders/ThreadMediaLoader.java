package com.tapmedia.yoush.database.loaders;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.MediaDatabase;

public final class ThreadMediaLoader extends MediaLoader {

           private final long                  threadId;
  @NonNull private final MediaType             mediaType;
  @NonNull private final MediaDatabase.Sorting sorting;

  public ThreadMediaLoader(@NonNull Context context,
                           long threadId,
                           @NonNull MediaType mediaType,
                           @NonNull MediaDatabase.Sorting sorting)
  {
    super(context);
    this.threadId  = threadId;
    this.mediaType = mediaType;
    this.sorting   = sorting;
  }

  @Override
  public Cursor getCursor() {
    return createThreadMediaCursor(context, threadId, mediaType, sorting);
  }

  static Cursor createThreadMediaCursor(@NonNull Context context,
                                        long threadId,
                                        @NonNull MediaType mediaType,
                                        @NonNull MediaDatabase.Sorting sorting) {
    MediaDatabase mediaDatabase = DatabaseFactory.getMediaDatabase(context);

    switch (mediaType) {
      case GALLERY : return mediaDatabase.getGalleryMediaForThread(threadId, sorting);
      case DOCUMENT: return mediaDatabase.getDocumentMediaForThread(threadId, sorting);
      case AUDIO   : return mediaDatabase.getAudioMediaForThread(threadId, sorting);
      case ALL     : return mediaDatabase.getAllMediaForThread(threadId, sorting);
      default      : throw new AssertionError();
    }
  }

}

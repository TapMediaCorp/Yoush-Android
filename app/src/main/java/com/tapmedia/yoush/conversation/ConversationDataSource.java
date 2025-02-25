package com.tapmedia.yoush.conversation;

import android.content.Context;
import android.database.ContentObserver;

import androidx.annotation.NonNull;
import androidx.paging.DataSource;
import androidx.paging.PositionalDataSource;

import com.tapmedia.yoush.database.DatabaseContentProviders;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.MmsSmsDatabase;
import com.tapmedia.yoush.database.model.MessageRecord;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.util.Util;
import com.tapmedia.yoush.util.concurrent.SignalExecutors;
import com.tapmedia.yoush.util.paging.Invalidator;
import com.tapmedia.yoush.util.paging.SizeFixResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Core data source for loading an individual conversation.
 */
class ConversationDataSource extends PositionalDataSource<MessageRecord> {

  private static final String TAG = Log.tag(ConversationDataSource.class);

  public static final Executor EXECUTOR = SignalExecutors.newFixedLifoThreadExecutor("signal-conversation", 1, 1);

  private final Context             context;
  private final long                threadId;

  private ConversationDataSource(@NonNull Context context,
                                 long threadId,
                                 @NonNull Invalidator invalidator)
  {
    this.context            = context;
    this.threadId           = threadId;

    ContentObserver contentObserver = new ContentObserver(null) {
      @Override
      public void onChange(boolean selfChange) {
        invalidate();
        context.getContentResolver().unregisterContentObserver(this);
      }
    };

    invalidator.observe(() -> {
      invalidate();
      context.getContentResolver().unregisterContentObserver(contentObserver);
    });

    context.getContentResolver().registerContentObserver(DatabaseContentProviders.Conversation.getUriForThread(threadId), true, contentObserver);
  }

  @Override
  public void loadInitial(@NonNull LoadInitialParams params, @NonNull LoadInitialCallback<MessageRecord> callback) {
    long start = System.currentTimeMillis();

    MmsSmsDatabase      db             = DatabaseFactory.getMmsSmsDatabase(context);
    List<MessageRecord> records        = new ArrayList<>(params.requestedLoadSize);
    int                 totalCount     = db.getConversationCount(threadId);
    int                 effectiveCount = params.requestedStartPosition;

    try (MmsSmsDatabase.Reader reader = db.readerFor(db.getConversation(threadId, params.requestedStartPosition, params.requestedLoadSize))) {
      MessageRecord record;
      while ((record = reader.getNext()) != null && effectiveCount < totalCount && !isInvalid()) {
        records.add(record);
        effectiveCount++;
      }
    }

    if (!isInvalid()) {
      SizeFixResult<MessageRecord> result = SizeFixResult.ensureMultipleOfPageSize(records, params.requestedStartPosition, params.pageSize, totalCount);

      callback.onResult(result.getItems(), params.requestedStartPosition, result.getTotal());
    }

    Log.d(TAG, "[Initial Load] " + (System.currentTimeMillis() - start) + " ms | thread: " + threadId + ", start: " + params.requestedStartPosition + ", size: " + params.requestedLoadSize + (isInvalid() ? " -- invalidated" : ""));
  }

  @Override
  public void loadRange(@NonNull LoadRangeParams params, @NonNull LoadRangeCallback<MessageRecord> callback) {
    long start = System.currentTimeMillis();

    MmsSmsDatabase      db      = DatabaseFactory.getMmsSmsDatabase(context);
    List<MessageRecord> records = new ArrayList<>(params.loadSize);

    try (MmsSmsDatabase.Reader reader = db.readerFor(db.getConversation(threadId, params.startPosition, params.loadSize))) {
      MessageRecord record;
      while ((record = reader.getNext()) != null && !isInvalid()) {
        records.add(record);
      }
    }

    callback.onResult(records);

    Log.d(TAG, "[Update] " + (System.currentTimeMillis() - start) + " ms | thread: " + threadId + ", start: " + params.startPosition + ", size: " + params.loadSize + (isInvalid() ? " -- invalidated" : ""));
  }

  static class Factory extends DataSource.Factory<Integer, MessageRecord> {

    private final Context             context;
    private final long                threadId;
    private final Invalidator         invalidator;

    Factory(Context context, long threadId, @NonNull Invalidator invalidator) {
      this.context     = context;
      this.threadId    = threadId;
      this.invalidator = invalidator;
    }

    @Override
    public @NonNull DataSource<Integer, MessageRecord> create() {
      return new ConversationDataSource(context, threadId, invalidator);
    }
  }
}

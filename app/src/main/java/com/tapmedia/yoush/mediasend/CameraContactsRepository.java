package com.tapmedia.yoush.mediasend;

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.tapmedia.yoush.contacts.ContactRepository;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.GroupDatabase;
import com.tapmedia.yoush.database.RecipientDatabase;
import com.tapmedia.yoush.database.ThreadDatabase;
import com.tapmedia.yoush.database.model.ThreadRecord;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.util.concurrent.SignalExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Handles retrieving the data to be shown in {@link CameraContactSelectionFragment}.
 */
class CameraContactsRepository {

  private static final String TAG = Log.tag(CameraContactsRepository.class);

  private static final int RECENT_MAX = 25;

  private final Context           context;
  private final ThreadDatabase    threadDatabase;
  private final GroupDatabase     groupDatabase;
  private final RecipientDatabase recipientDatabase;
  private final ContactRepository contactRepository;
  private final Executor          serialExecutor;
  private final ExecutorService   parallelExecutor;

  CameraContactsRepository(@NonNull Context context) {
    this.context           = context.getApplicationContext();
    this.threadDatabase    = DatabaseFactory.getThreadDatabase(context);
    this.groupDatabase     = DatabaseFactory.getGroupDatabase(context);
    this.recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    this.contactRepository = new ContactRepository(context);
    this.serialExecutor    = SignalExecutors.SERIAL;
    this.parallelExecutor  = SignalExecutors.BOUNDED;
  }

  void getCameraContacts(@NonNull Callback<CameraContacts> callback) {
    getCameraContacts("", callback);
  }

  void getCameraContacts(@NonNull String query, @NonNull Callback<CameraContacts> callback) {
    serialExecutor.execute(() -> {
      Future<List<Recipient>> recents  = parallelExecutor.submit(() -> getRecents(query));
      Future<List<Recipient>> contacts = parallelExecutor.submit(() -> getContacts(query));
      Future<List<Recipient>> groups   = parallelExecutor.submit(() -> getGroups(query));

      try {
        long           startTime = System.currentTimeMillis();
        CameraContacts result    = new CameraContacts(recents.get(), contacts.get(), groups.get());

        Log.d(TAG, "Total time: " + (System.currentTimeMillis() - startTime) + " ms");

        callback.onComplete(result);
      } catch (InterruptedException | ExecutionException e) {
        Log.w(TAG, "Failed to perform queries.", e);
        callback.onComplete(new CameraContacts(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
      }
    });
  }


  @WorkerThread
  private @NonNull List<Recipient> getRecents(@NonNull String query) {
    if (!TextUtils.isEmpty(query)) {
      return Collections.emptyList();
    }

    List<Recipient> recipients = new ArrayList<>(RECENT_MAX);

    try (ThreadDatabase.Reader threadReader = threadDatabase.readerFor(threadDatabase.getRecentPushConversationList(RECENT_MAX, false))) {
      ThreadRecord threadRecord;
      while ((threadRecord = threadReader.getNext()) != null) {
        recipients.add(threadRecord.getRecipient().resolve());
      }
    }

    return recipients;
  }

  @WorkerThread
  private @NonNull List<Recipient> getContacts(@NonNull String query) {
    List<Recipient> recipients = new ArrayList<>();

    try (Cursor cursor = contactRepository.querySignalContacts(query)) {
      while (cursor.moveToNext()) {
        RecipientId id        = RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(ContactRepository.ID_COLUMN)));
        Recipient   recipient = Recipient.resolved(id);
        recipients.add(recipient);
      }
    }

    return recipients;
  }

  @WorkerThread
  private @NonNull List<Recipient> getGroups(@NonNull String query) {
    if (TextUtils.isEmpty(query)) {
      return Collections.emptyList();
    }

    List<Recipient> recipients = new ArrayList<>();

    try (GroupDatabase.Reader reader = groupDatabase.getGroupsFilteredByTitle(query, false)) {
      GroupDatabase.GroupRecord groupRecord;
      while ((groupRecord = reader.getNext()) != null) {
        RecipientId recipientId = recipientDatabase.getOrInsertFromGroupId(groupRecord.getId());
        recipients.add(Recipient.resolved(recipientId));
      }
    }

    return recipients;
  }

  interface Callback<E> {
    void onComplete(E result);
  }
}

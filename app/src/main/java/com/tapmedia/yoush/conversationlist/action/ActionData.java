package com.tapmedia.yoush.conversationlist.action;

import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.tapmedia.yoush.ApplicationContext;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.database.DatabaseContentProviders;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.MessagingDatabase;
import com.tapmedia.yoush.database.ThreadDatabase;
import com.tapmedia.yoush.database.model.ThreadRecord;
import com.tapmedia.yoush.notifications.MarkReadReceiver;
import com.tapmedia.yoush.notifications.NotificationItem;
import com.tapmedia.yoush.storage.StorageSyncHelper;
import com.tapmedia.yoush.util.Res;
import com.tapmedia.yoush.util.concurrent.SimpleTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ActionData {

    private static Context context = ApplicationContext.getInstance();

    public static ThreadDatabase threadDatabase() {
        return DatabaseFactory.getThreadDatabase(context);
    }

    public static void markAsRead(
            Fragment fragment,
            long threadId,
            SimpleTask.ForegroundTask<Boolean> onCompleted
    ) {
        SimpleTask.run(fragment.getViewLifecycleOwner().getLifecycle(), () -> {

            List<MessagingDatabase.MarkedMessageInfo> messageIds = threadDatabase()
                    .setRead(threadId, false);
            //ApplicationDependencies.getMessageNotifier().updateNotification(context);
            MarkReadReceiver.process(context, messageIds);
            return true;
        }, onCompleted);
    }

    public static void markUnAsRead(
            Fragment fragment,
            long threadId,
            SimpleTask.ForegroundTask<Boolean> onCompleted
    ) {
        Set<Long> set = new HashSet<>();
        set.add(threadId);
        SimpleTask.run(fragment.getViewLifecycleOwner().getLifecycle(), () -> {
            ThreadDatabase db = threadDatabase();
            db.setForcedUnread(set);
            StorageSyncHelper.scheduleSyncForDataChange();
            return true;
        }, onCompleted);
    }

    public static void archiveConversation(
            Fragment fragment,
            long threadId,
            SimpleTask.ForegroundTask<Boolean> onCompleted
    ) {
        SimpleTask.run(fragment.getViewLifecycleOwner().getLifecycle(), () -> {
            ThreadDatabase db = threadDatabase();
            db.archiveConversation(threadId);
            return true;
        }, onCompleted);
    }

    public static void unArchiveConversation(
            Fragment fragment,
            long threadId,
            SimpleTask.ForegroundTask<Boolean> onCompleted
    ) {
        SimpleTask.run(fragment.getViewLifecycleOwner().getLifecycle(), () -> {
            ThreadDatabase db = threadDatabase();
            db.unarchiveConversation(threadId);
            return true;
        }, onCompleted);
    }

    public static void hideConversation(
            Fragment fragment,
            long threadId,
            SimpleTask.ForegroundTask<Boolean> onCompleted
    ) {
        SimpleTask.run(fragment.getViewLifecycleOwner().getLifecycle(), () -> {
            ThreadDatabase db = threadDatabase();
            db.isHideConversation(threadId, true);
            return true;
        }, onCompleted);
    }

    public static void unHideConversation(
            Fragment fragment,
            long threadId,
            SimpleTask.ForegroundTask<Boolean> onCompleted
    ) {
        SimpleTask.run(fragment.getViewLifecycleOwner().getLifecycle(), () -> {
            ThreadDatabase db = threadDatabase();
            db.isHideConversation(threadId, false);
            return true;
        }, onCompleted);
    }

    public static void deleteConversation(
            Fragment fragment,
            long threadId,
            SimpleTask.ForegroundTask<Boolean> onCompleted) {

        DialogInterface.OnClickListener onConfirm = (dialog, which) -> {
            SimpleTask.run(fragment.getViewLifecycleOwner().getLifecycle(), () -> {
                ThreadDatabase db = threadDatabase();
                Set<Long> set = new HashSet<>();
                set.add(threadId);
                db.deleteConversations(set);
                return true;
            }, onCompleted);
            //ApplicationDependencies.getMessageNotifier().updateNotification(fragment.getActivity());
        };
        new AlertDialog.Builder(fragment.requireActivity())
                .setIconAttribute(R.attr.dialog_alert_icon)
                .setTitle(Res.quantityStr(R.plurals.ConversationListFragment_delete_selected_conversations, 1, 1))
                .setMessage(Res.quantityStr(R.plurals.ConversationListFragment_this_will_permanently_delete_all_n_selected_conversations, 1, 1))
                .setCancelable(true)
                .setPositiveButton(R.string.delete, onConfirm)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static void deleteHiddenConversation(
            Fragment fragment,
            SimpleTask.ForegroundTask<Boolean> onCompleted) {

        SimpleTask.run(fragment.getViewLifecycleOwner().getLifecycle(), () -> {
            Set<Long> set = new HashSet<>();
            ThreadDatabase db =threadDatabase();
            Cursor cursor = db.getConversationListByHidden(true);
            cursor.setNotificationUri(context.getContentResolver(), DatabaseContentProviders.ConversationList.CONTENT_URI);
            try (ThreadDatabase.Reader reader = db.readerFor(cursor)) {
                ThreadRecord record;
                while ((record = reader.getNext()) != null) {
                    set.add(record.getThreadId());
                }
                cursor.close();
            }
            if (set.isEmpty()) return true;
            db.deleteConversations(set);
            return true;
        }, onCompleted);
    }

    public static ThreadRecord getThreadRecord(long threadId) {
        if (threadId < 0) return null;
        ThreadDatabase db =threadDatabase();
        Cursor cursor = db.getConversationListByHidden(true); //db.getConversationList();
        cursor.setNotificationUri(context.getContentResolver(), DatabaseContentProviders.ConversationList.CONTENT_URI);
        try (ThreadDatabase.Reader reader = db.readerFor(cursor)) {
            ThreadRecord record;
            while ((record = reader.getNext()) != null) {
                if (record.getThreadId() == threadId) {
                    cursor.close();
                    return record;
                }
            }
        }
        cursor.close();
        return null;
    }

    public static List<ThreadRecord> getHiddenThreadRecords() {
        List<ThreadRecord> list= new ArrayList<>();
        ThreadDatabase db =threadDatabase();
        Cursor cursor = db.getConversationListByHidden(true);
        cursor.setNotificationUri(context.getContentResolver(), DatabaseContentProviders.ConversationList.CONTENT_URI);
        try (ThreadDatabase.Reader reader = db.readerFor(cursor)) {
            ThreadRecord record;
            while ((record = reader.getNext()) != null) {
                list.add(record);
            }
        }
        cursor.close();
        return list;
    }

    public static ThreadRecord getThreadRecord2(long threadId) {
        if (threadId < 0) return null;
        ThreadDatabase db = DatabaseFactory.getThreadDatabase(context);
        String query = db.createQuery("_id = ? ", 0, 0);
        Cursor cursor = db
                .databaseHelper
                .getReadableDatabase()
                .rawQuery(query, new String[]{String.valueOf(threadId)});
        cursor.setNotificationUri(context.getContentResolver(), DatabaseContentProviders.ConversationList.CONTENT_URI);
        try (ThreadDatabase.Reader reader = db.readerFor(cursor)) {
            ThreadRecord record;
            while ((record = reader.getNext()) != null) {
                if (record.getThreadId() == threadId) {
                    cursor.close();
                    return record;
                }
            }
        }
        cursor.close();
        return null;
    }

    public static boolean isHiddenConversation(NotificationItem item){
        if (item == null)  return false;
        ThreadRecord record = ActionData.getThreadRecord(item.getThreadId());
        if (record == null)  return false;
        return record.isHidden();
    }
}

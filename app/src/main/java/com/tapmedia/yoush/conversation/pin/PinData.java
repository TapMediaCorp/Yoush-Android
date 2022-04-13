package com.tapmedia.yoush.conversation.pin;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.lifecycle.MutableLiveData;

import com.google.android.mms.pdu_alt.PduHeaders;

import net.sqlcipher.database.SQLiteDatabase;

import com.tapmedia.yoush.ApplicationContext;
import com.tapmedia.yoush.conversation.ConversationFragment;
import com.tapmedia.yoush.conversation.ConversationUpdateItem;
import com.tapmedia.yoush.conversation.model.PinWrapper;
import com.tapmedia.yoush.conversation.model.UserAction;
import com.tapmedia.yoush.conversationlist.ConversationListItem;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.MmsDatabase;
import com.tapmedia.yoush.database.MmsSmsColumns;
import com.tapmedia.yoush.database.MmsSmsDatabase;
import com.tapmedia.yoush.database.SmsDatabase;
import com.tapmedia.yoush.database.model.MessageRecord;
import com.tapmedia.yoush.database.model.ThreadRecord;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.util.JsObject;
import com.tapmedia.yoush.util.SimpleSingleObserver;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;


public class PinData {

    public static long pinType = 1234567891;

    public static MutableLiveData<List<PinWrapper>> recordLiveData = new MutableLiveData<>();

    private static Context context() {
        return ApplicationContext.getInstance();
    }

    /**
     * {@link ConversationListItem}.getThreadDisplayBody
     */
    public static boolean isValidRecord(ThreadRecord record) {
        return record.getType() == pinType || isValidRecordBody(record.getBody());
    }

    /**
     * {@link ConversationUpdateItem}.getItemViewType
     * if (PinData.isPinMessage(messageRecord)) return MESSAGE_TYPE_UPDATE;
     */
    public static boolean isValidRecord(MessageRecord record) {
        return record.getType() == pinType || isValidRecordBody(record.getBody());
    }

    public static boolean isValidRecordBody(String body) {
        return body.indexOf("\"messageType\"") != -1 &&
                body.indexOf("\"pinMessage_action\"") != -1 &&
                body.indexOf("\"pinMessage_messageTimestamp\"") != -1 &&
                body.indexOf("\"pinMessage_sequence\"") != -1;
    }

    /**
     * {@link ConversationFragment}.onCreateView
     */
    public static void onDataChange(long threadId){
        onDataChange(threadId,() -> { });
    }

    public static void onDataChange(long threadId, Runnable runnable) {
        Single
                .fromCallable(() -> {
                    runnable.run();
                    return PinData.getCurrentPinWrapper(threadId);
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SimpleSingleObserver<List<PinWrapper>>() {
                    @Override
                    public void onSuccess(List<PinWrapper> messageId) {
                        PinData.recordLiveData.postValue(messageId);
                    }
                });
    }

    public static void removeRecord(MessageRecord record) {
        DatabaseFactory.getMmsDatabase(ApplicationContext.getInstance()).delete(record.getId());
    }

    public static List<PinWrapper> getCurrentPinWrapper(long threadId) {
        List<PinWrapper> wrapperList = getPinAndUnpinWrapper(threadId);
        List<PinWrapper> pinWrapperList = new ArrayList<>();
        List<PinWrapper> unpinWrapperList = new ArrayList<>();
        for (PinWrapper record : wrapperList) {
            if (pinWrapperList.size() > 3) {
                break;
            }
            String action = record.getAction();
            if (action.equals("unpin") && pinWrapperList.indexOf(record) < 0) {
                unpinWrapperList.add(record);
                continue;
            }
            if (action.equals("pin") && unpinWrapperList.indexOf(record) < 0 && pinWrapperList.indexOf(record) < 0) {
                pinWrapperList.add(record);
                continue;
            }
        }
        pinWrapperList.sort(PinWrapper.getSequenceComparator());
        return pinWrapperList;
    }

    private static List<PinWrapper> getPinAndUnpinWrapper(long threadId) {
        MmsSmsDatabase db = DatabaseFactory.getMmsSmsDatabase(context());
        String order = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " DESC";
        String selection = String.format("thread_id = %s AND (msg_box = %s OR m_type = %s)",
                threadId, pinType, pinType);
        Cursor cursor = db.queryTables(MmsSmsDatabase.PROJECTION, selection, order, null);
        db.setNotifyConversationListeners(cursor, threadId);
        List<PinWrapper> wrapperList = new ArrayList<>();
        try (MmsSmsDatabase.Reader reader = db.readerFor(cursor)) {
            MessageRecord record;
            while ((record = reader.getNext()) != null) {

                PinWrapper wrapper = JsObject.parse(record.getBody(), PinWrapper.class);
                if (wrapper == null) continue;

                String action = wrapper.getAction();
                if (!action.equals("pin") && !action.equals("unpin")) continue;

                MessageRecord refRecord = getMessageRecord(threadId, wrapper.getTimestamp());
                if (refRecord == null) continue;
                wrapper.record = record;
                wrapper.actionRecipient = UserAction.recipient(wrapper.getUserAction());
                wrapper.authorRecipient = UserAction.recipient(wrapper.getAuthor());
                wrapper.refRecord = refRecord;
                wrapperList.add(wrapper);
            }
        }
        cursor.close();
        return wrapperList;
    }

    public static PinWrapper getWrapper(MessageRecord record) {
        PinWrapper wrapper = JsObject.parse(record.getBody(), PinWrapper.class);
        wrapper.authorRecipient = UserAction.recipient(wrapper.getAuthor());
        wrapper.actionRecipient = UserAction.recipient(wrapper.getUserAction());
        wrapper.record = record;
        if (null != wrapper.getTimestamp()) {
            wrapper.refRecord = PinData.getMessageRecord(record.getThreadId(), wrapper.getTimestamp());
        }
        return wrapper;
    }

    public static MessageRecord getMessageRecord(long threadId, long timestamp) {
        MmsSmsDatabase db = DatabaseFactory.getMmsSmsDatabase(context());
        String order = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " DESC";
        String selection = String.format("thread_id = %s AND date_sent = %s", threadId, timestamp);
        Cursor cursor = db.queryTables(MmsSmsDatabase.PROJECTION, selection, order, null);
        db.setNotifyConversationListeners(cursor, threadId);
        try (MmsSmsDatabase.Reader reader = db.readerFor(cursor)) {
            MessageRecord record;
            while ((record = reader.getNext()) != null) {
                cursor.close();
                return record;
            }
        }
        cursor.close();
        return null;
    }

    public static void reorderPinMessage(long threadId, JsObject body) {
        List<JsObject> objList = body.list("pinMessage_reorderMessages");
        if (objList.isEmpty()) return;
        List<PinWrapper> recordWrapperList = PinData.getCurrentPinWrapper(threadId);
        for (JsObject obj : objList) {
            long messageTimestamp = obj.getLong("pinMessage_messageTimestamp");
            if (messageTimestamp <= 0) continue;
            long newSequence = obj.getLong("pinMessage_sequence");
            if (newSequence <= 0) continue;
            updatePinRecord(recordWrapperList, messageTimestamp, newSequence);
        }
    }

    private static void updatePinRecord(
            List<PinWrapper> wrapperList,
            long messageTimestamp,
            long newSequence
    ) {
        for (PinWrapper recordWrapper : wrapperList) {
            if (recordWrapper.getTimestamp() != messageTimestamp) continue;
            JsObject sequencedObj = JsObject.create()
                    .put("pinMessage_userAction", JsObject.create()
                            .put("uuid", recordWrapper.getUserAction().getUuid())
                            .put("phoneNumber", recordWrapper.getUserAction().getPhoneNumber()))
                    .put("pinMessage_author", JsObject.create()
                            .put("uuid", recordWrapper.getAuthor().getUuid())
                            .put("phoneNumber", recordWrapper.getAuthor().getPhoneNumber()))
                    .put("pinMessage_messageId", recordWrapper.getMessageId())
                    .put("pinMessage_messageTimestamp", recordWrapper.getTimestamp())
                    .put("pinMessage_action", recordWrapper.getAction())
                    .put("pinMessage_sequence", newSequence)
                    .put("pinMessage_groupId", recordWrapper.getGroupId())
                    .put("messageType", recordWrapper.getMessageType());
            MessageRecord pinRecord = recordWrapper.record;
            SmsDatabase smsDatabase = DatabaseFactory.getSmsDatabase(ApplicationContext.getInstance());
            SQLiteDatabase db = smsDatabase.databaseHelper.getWritableDatabase();
            db.execSQL("UPDATE mms SET body = ? WHERE _id = ? AND thread_id = ?",
                    new String[]{
                            sequencedObj.toString(),
                            pinRecord.getId() + "",
                            pinRecord.getThreadId() + ""
                    });
            break;
        }
    }

    public static void insertMessage(
            JsObject body,
            Recipient recipient,
            long threadId,
            long sentTime,
            long serverTime
    ) {
        MmsDatabase database = DatabaseFactory.getMmsDatabase(context());
        ContentValues values = new ContentValues();
        values.put("date", sentTime);
        values.put("date_server", serverTime);
        values.put("address", recipient.getId().serialize());
        values.put("msg_box", PinData.pinType);
        values.put("m_type", PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF);
        values.put("thread_id", threadId);
        values.put("ct_l", "");
        values.put("st", MmsDatabase.Status.DOWNLOAD_INITIALIZED);
        values.put("date_received", System.currentTimeMillis());
        values.put("part_count", 0);
        values.put("subscription_id", -1);
        values.put("expires_in", 0);
        values.put("reveal_duration", 0);
        values.put("body", body.build().toString());
        values.put("read", 1);
        values.put("unidentified", true);
        long messageId = database
                .databaseHelper
                .getWritableDatabase()
                .insert("mms", null, values);
    }


}

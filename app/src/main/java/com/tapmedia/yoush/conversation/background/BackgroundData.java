package com.tapmedia.yoush.conversation.background;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import androidx.lifecycle.MutableLiveData;

import com.tapmedia.yoush.ApplicationContext;
import com.tapmedia.yoush.attachments.Attachment;
import com.tapmedia.yoush.attachments.DatabaseAttachment;
import com.tapmedia.yoush.attachments.PointerAttachment;
import com.tapmedia.yoush.conversation.model.BackgroundWrapper;
import com.tapmedia.yoush.conversation.model.UserAction;
import com.tapmedia.yoush.database.AttachmentDatabase;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.MmsDatabase;
import com.tapmedia.yoush.database.MmsSmsColumns;
import com.tapmedia.yoush.database.MmsSmsDatabase;
import com.tapmedia.yoush.database.model.MessageRecord;
import com.tapmedia.yoush.database.model.MmsMessageRecord;
import com.tapmedia.yoush.database.model.ThreadRecord;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobs.AttachmentDownloadJob;
import com.tapmedia.yoush.mediasend.MediaSendActivityResult;
import com.tapmedia.yoush.mms.MmsException;
import com.tapmedia.yoush.mms.Slide;
import com.tapmedia.yoush.mms.SlideDeck;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.util.JsObject;
import com.tapmedia.yoush.util.SimpleSingleObserver;
import com.tapmedia.yoush.util.SingleLiveEvent;
import com.google.android.mms.pdu_alt.PduHeaders;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class BackgroundData {

    public static String folderPath = "conversation_wallpaper";

    public static long  messageId = -1;

    public static long backgroundType = 1234567892;

    public static MutableLiveData<MediaSendActivityResult> uploadResultLiveData = new SingleLiveEvent();

    public static MutableLiveData<BackgroundWrapper> backgroundLiveData = new MutableLiveData<>();

    private static Context context(){
        return ApplicationContext.getInstance();
    }

    public static BackgroundWrapper getBackgroundMessage(long threadId) {
        MmsSmsDatabase db = DatabaseFactory.getMmsSmsDatabase(context());
        String order = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " DESC";
        String selection = String.format("thread_id = %s AND (msg_box = %s OR m_type = %s)",
                threadId, backgroundType, backgroundType);
        Cursor cursor = db.queryTables(MmsSmsDatabase.PROJECTION, selection, order, null);
        db.setNotifyConversationListeners(cursor, threadId);
        MessageRecord record;
        BackgroundWrapper wrapper = null;
        try (MmsSmsDatabase.Reader reader = db.readerFor(cursor)) {
            while ((record = reader.getNext()) != null) {
                wrapper = JsObject.parse(record.getBody(), BackgroundWrapper.class);
                if (wrapper == null) continue;
                wrapper.record = record;
                wrapper.actionRecipient = UserAction.recipient(wrapper.getUserAction());
                break;
            }
        }
        cursor.close();
        return wrapper;
    }

    public static BackgroundWrapper getWrapper(MessageRecord record) {
        BackgroundWrapper wrapper = JsObject.parse(record.getBody(), BackgroundWrapper.class);
        wrapper.actionRecipient = UserAction.recipient(wrapper.getUserAction());
        wrapper.record = record;
        return wrapper;
    }

    public static Slide getSlide(MessageRecord record) {
        if (!record.isMms()) return null;
        MmsMessageRecord mmsRecord = (MmsMessageRecord) record;
        if (record == null) return null;
        SlideDeck slideDeck = mmsRecord.getSlideDeck();
        List<Slide> thumbnailSlides = slideDeck.getThumbnailSlides();
        if (thumbnailSlides == null || thumbnailSlides.size() < 1) return null;
        Slide slide = thumbnailSlides.get(0);
        if (!slide.hasImage() && !slide.isInProgress()) return null;
        return slide;
    }

    public static MessageRecord getMessageRecord(long threadId, long dateSent) {
        MmsSmsDatabase db = DatabaseFactory.getMmsSmsDatabase(context());
        String order = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " DESC";
        String selection = String.format("thread_id = %s AND date_sent = %s",threadId, dateSent);
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

    public static MessageRecord getMessageRecord(long dateSent) {
        MmsSmsDatabase db = DatabaseFactory.getMmsSmsDatabase(context());
        String order = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " DESC";
        String selection = String.format("date_sent = %s", dateSent);
        Cursor cursor = db.queryTables(MmsSmsDatabase.PROJECTION, selection, order, null);
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

    public static boolean isValidRecord(MessageRecord record) {
        return record.getType() == backgroundType || isValidRecordBody(record.getBody());
    }

    public static boolean isValidRecord(ThreadRecord record) {
        return record.getType() == backgroundType || isValidRecordBody(record.getBody());
    }

    public static boolean isTempRecord(MessageRecord record) {
        return record.getType() != backgroundType && isValidRecordBody(record.getBody());
    }

    public static boolean isValidRecordBody(String body) {
        return body.indexOf("\"messageType\"") != -1 &&
                body.indexOf("\"updateWallPaper\"") != -1 &&
                body.indexOf("\"action\"") != -1 &&
                body.indexOf("\"userAction\"") != -1;
    }

    public static void removeRecord(MessageRecord record) {
        Single.fromCallable(() -> {
            DatabaseFactory.getMmsDatabase(ApplicationContext.getInstance()).delete(record.getId());
            return true;
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SimpleSingleObserver<Boolean>() {
                });
    }

    public static void onDataChange(long threadId){
        onDataChange(threadId,() -> { });
    }

    public static void onDataChange(long threadId, Runnable runnable) {
        Single
                .fromCallable(() -> {
                    runnable.run();
                    return getCurrentBackgroundWrapper(threadId);
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SimpleSingleObserver<BackgroundWrapper>() {
                    @Override
                    public void onSuccess(BackgroundWrapper wrapper) {
                        backgroundLiveData.postValue(wrapper);
                    }
                });
    }

    public static BackgroundWrapper getCurrentBackgroundWrapper(long threadId) {
        BackgroundWrapper wrapper = getBackgroundMessage(threadId);
        if (wrapper == null) return null;
        wrapper.threadId = threadId;
        if (wrapper.getAction().equals("remove")) return wrapper;
        if (!TextUtils.isEmpty(wrapper.getImageUrl())) return wrapper;
        if (getSlide(wrapper.record) != null) return wrapper;
        return null;
    }

    public static void removeRecord(long recordId) {
        Single.fromCallable(() -> {
            DatabaseFactory.getMmsDatabase(ApplicationContext.getInstance()).delete(recordId);
            return true;
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SimpleSingleObserver<Boolean>() {
                });
    }

    public static long insertMessage(
            SignalServiceContent content,
            SignalServiceDataMessage message,
            JsObject body,
            Recipient recipient,
            long threadId
    ) {
        List<Attachment> attachments = Collections.emptyList();
        if (message.getAttachments().isPresent()) {
            attachments = PointerAttachment.forPointers(Optional.of(message.getAttachments().get()));
        }
        return insertMessage(
                attachments,
                body,
                recipient,
                threadId,
                message.getTimestamp(),
                content.getServerReceivedTimestamp()
        );
    }

    public static long insertMessage(
            List<Attachment> attachments,
            JsObject body,
            Recipient recipient,
            long threadId,
            long sentTime,
            long serverTime
    ) {
        try {
            ContentValues values = new ContentValues();
            values.put("date", sentTime);
            values.put("date_server", serverTime);
            values.put("address", recipient.getId().serialize());
            values.put("msg_box", backgroundType);
            values.put("m_type", PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF);
            values.put("thread_id", threadId);
            values.put("ct_l", "");
            values.put("st", MmsDatabase.Status.DOWNLOAD_INITIALIZED);
            values.put("date_received", System.currentTimeMillis());
            values.put("part_count", attachments.size());
            values.put("subscription_id", -1);
            values.put("expires_in", 0);
            values.put("reveal_duration", 0);
            values.put("body", body.build().toString());
            values.put("read", 1);
            values.put("unidentified", true);
            long messageId = DatabaseFactory
                    .getMmsDatabase(context())
                    .databaseHelper
                    .getWritableDatabase()
                    .insert("mms", null, values);
            if (attachments.size() > 0) {
                AttachmentDatabase partsDatabase = DatabaseFactory.getAttachmentDatabase(context());
                List<Attachment> linkedAttachments = new LinkedList<>();
                linkedAttachments.addAll(attachments);
                partsDatabase.insertAttachmentsForMessage(messageId, linkedAttachments, Collections.emptyList());

                List<DatabaseAttachment> allAttachments = DatabaseFactory
                        .getAttachmentDatabase(context())
                        .getAttachmentsForMessage(messageId);

                for (DatabaseAttachment attachment : allAttachments) {
                    ApplicationDependencies.getJobManager()
                            .add(new AttachmentDownloadJob(messageId, attachment.getAttachmentId(), false));
                }
            }
            return messageId;
        } catch (MmsException e) {
            return -1;
        }

    }
}

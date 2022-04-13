package com.tapmedia.yoush.conversation.pin;

import android.content.ContentValues;
import android.content.Intent;

import androidx.fragment.app.FragmentActivity;

import com.google.android.mms.pdu_alt.PduHeaders;

import com.tapmedia.yoush.ApplicationContext;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.conversation.ConversationActivity;
import com.tapmedia.yoush.conversation.job.MessageJob;
import com.tapmedia.yoush.conversation.model.PinWrapper;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.model.MessageRecord;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.dialog.AlertBottomDialog;
import com.tapmedia.yoush.jobmanager.Job;
import com.tapmedia.yoush.jobmanager.impl.NetworkConstraint;
import com.tapmedia.yoush.jobs.PushGroupSendJob;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.sms.MessageSender;
import com.tapmedia.yoush.util.JsArray;
import com.tapmedia.yoush.util.JsObject;


import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PinJobSend {

    /**
     * {@link ConversationActivity}.onMenuItemClick
     */
    public static void pin(FragmentActivity activity, MessageRecord record) {

        if (null == PinData.recordLiveData.getValue()) return;
        if (record.hasNetworkFailures()) return;
        if (record.getType() == PinData.pinType) return;
        if (record.isMediaPending()) return;

        if (isPinned(record)) {
            alertUnpin(activity, record);
            return;
        }

        if (PinData.getCurrentPinWrapper(MessageJob.threadId).size() > 3) {
            alertMaxPin(activity);
            return;
        }
        PinData.onDataChange(MessageJob.threadId, () -> {
            // pin json
            JsObject body = pinBody(record, "pin");
            // insert new sequence in local db
            /*PinData.insertMessage(
                    body,
                    MessageJob.recipient,
                    MessageJob.threadId,
                    System.currentTimeMillis(),
                    System.currentTimeMillis()
            );*/
            // send pin/unpin message
            sendMessage(body);
        });
    }

    public static void alertUnpin(FragmentActivity activity, MessageRecord record) {
        AlertBottomDialog d = new AlertBottomDialog();
        d.title = activity.getString(R.string.pin_unpin_title);
        d.subTitle = activity.getString(R.string.pin_unpin_sub_title);
        d.confirmLabel = activity.getString(R.string.pin_unpin_btn);
        d.cancelLabel = activity.getString(R.string.pin_unpin_cancel);
        d.confirmColor = activity.getColor(R.color.red_600);
        d.runnable = () -> unpin(record);
        d.show(activity.getSupportFragmentManager(), null);
    }

    public static void alertMaxPin(FragmentActivity activity) {
        AlertBottomDialog d = new AlertBottomDialog();
        d.title = activity.getString(R.string.pin_max_title);
        d.subTitle = activity.getString(R.string.pin_max_sub_title);
        d.confirmLabel = activity.getString(R.string.pin_max_btn);
        d.cancelLabel = activity.getString(R.string.pin_max_cancel);
        d.runnable = () -> activity.startActivity(new Intent(activity, ConversationPinManagementActivity.class));
        d.show(activity.getSupportFragmentManager(), null);
    }

    public static void unpin(MessageRecord record) {
        PinData.onDataChange(MessageJob.threadId, () -> {
            // unpin json
            JsObject body = pinBody(record, "unpin");
            // insert new sequence in local db
            /*PinData.insertMessage(body, MessageJob.recipient, MessageJob.threadId,
                    System.currentTimeMillis(), System.currentTimeMillis());*/
            // send pin/unpin message
            sendMessage(body);
        });
    }

    /**
     * {@link ConversationPinManagementActivity}.onSaveButtonClick
     */
    public static void reorder(List<PinWrapper> wrapperList) {
        PinData.onDataChange(MessageJob.threadId, () -> {
            // update new sequence for reorderList
            sequenceList(wrapperList);
            // reorder json
            JsObject body = PinJobSend.reorderBody(wrapperList);
            // insert new sequence in local db
            PinData.insertMessage(body, MessageJob.recipient, MessageJob.threadId,
                    System.currentTimeMillis(), System.currentTimeMillis());
            // update new sequence in local db
            PinData.reorderPinMessage(MessageJob.threadId, body);
            // send reorder message
            sendMessage(body);
        });
    }

    /**
     * @param wrapperList
     */
    private static void sequenceList(List<PinWrapper> wrapperList) {
        List<PinWrapper> list = PinData.getCurrentPinWrapper(MessageJob.threadId);
        if (list == null || list.size() != wrapperList.size()) return;
        for (int i = 0; i < wrapperList.size(); i++) {
            long newSequence = list.get(i).getSequence();
            wrapperList.get(i).setSequence(newSequence);
        }
    }

    private static JsObject reorderBody(List<PinWrapper> wrapperList) {
        JsArray reorderMessages = JsArray.create();
        for (PinWrapper wrapper : wrapperList) {
            reorderMessages.put(JsObject.create()
                    .put("messageType", wrapper.getMessageType())
                    .put("pinMessage_sequence", wrapper.getSequence())
                    .put("pinMessage_messageTimestamp", wrapper.getTimestamp())
                    .put("pinMessage_messageId", wrapper.getMessageId())
                    .put("pinMessage_author", JsObject.create()
                            .put("uuid", wrapper.getAuthor().getUuid())
                            .put("phoneNumber", wrapper.getAuthor().getPhoneNumber())
                    )
            );
        }
        return JsObject.create()
                .put("messageType", "pinMessage")
                .put("pinMessage_action", "reorder")
                .put("pinMessage_userAction", JsObject.create()
                        .put("uuid", Recipient.self().getUuid().get().toString())
                        .put("phoneNumber", Recipient.self().getE164().get())
                )
                .put("pinMessage_reorderMessages", reorderMessages);
    }

    private static JsObject pinBody(MessageRecord record, String action) {

        Recipient author;
        Recipient individualRecipient = record.getIndividualRecipient();
        if (record.isOutgoing()) {
            author = Recipient.self();
        } else {
            author = individualRecipient;
        }
        String strGroupId = "";
        if (individualRecipient.getGroupId().isPresent()) {
            strGroupId = individualRecipient.getGroupId().get().toString();
        }
        return JsObject.create()
                .put("messageType", "pinMessage")
                .put("pinMessage_action", action)
                .put("pinMessage_groupId", strGroupId)
                .put("pinMessage_messageTimestamp", record.getDateSent())
                .put("pinMessage_sequence", System.currentTimeMillis())
                .put("pinMessage_messageId", "FF7DED81-00A4-4738-8ADB-F0327B01DD77")
                .put("pinMessage_userAction", JsObject.create()
                        .put("uuid", Recipient.self().getUuid().get().toString())
                        .put("phoneNumber", Recipient.self().getE164().get())
                )
                .put("pinMessage_author", JsObject.create()
                        .put("uuid", author.getUuid().get().toString())
                        .put("phoneNumber", author.getE164().get())
                );

    }

    private static boolean isPinned(MessageRecord record) {
        List<PinWrapper> pinRecordList = PinData.getCurrentPinWrapper(record.getThreadId());
        if (null == pinRecordList) return true;
        for (PinWrapper pinRecord : pinRecordList) {
            if (null == pinRecord.refRecord) continue;
            if (record.getDateSent() == pinRecord.refRecord.getDateSent()) {
                return true;
            }
        }
        return false;
    }

    private static void sendMessage(JsObject body) {
        long messageId = insertSendRecord(body);
        MessageJob.onMessageSent(messageId, MessageJob.recipient);
    }

    private static long insertSendRecord(JsObject body) {
        ContentValues values = new ContentValues();
        values.put("date", System.currentTimeMillis());
        values.put("m_type", PinData.pinType);
        values.put("msg_box", 22);
        values.put("thread_id", MessageJob.threadId);
        values.put("read", 1);
        values.put("date_received", System.currentTimeMillis());
        values.put("subscription_id", -1);
        values.put("expires_in", 0);
        values.put("reveal_duration", true);
        values.put("address", MessageJob.recipient.getId().serialize());
        values.put("delivery_receipt_count", 0);
        values.put("body", body.toString());
        values.put("part_count", 0);
        return DatabaseFactory
                .getMmsDatabase(ApplicationContext.getInstance())
                .databaseHelper
                .getWritableDatabase()
                .insert("mms", null, values);
    }

}
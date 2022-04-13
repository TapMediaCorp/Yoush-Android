package com.tapmedia.yoush.conversation.pin;

import android.content.Context;

import com.tapmedia.yoush.ApplicationContext;
import com.tapmedia.yoush.conversation.job.MessageJob;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.groups.GroupId;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.util.JsObject;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

public class PinJobReceiver {

    private static Context context = ApplicationContext.getInstance();

    /**
     * {@link com.tapmedia.yoush.jobs.PushProcessMessageJob}.handleDataMessage(Optional<Long> smsMessageId)
     * 351: if (ConversationPinJob.isPinMessage(content, message, groupId)) return;
     * }
     */
    public static boolean handleReceivedMessage(
            SignalServiceContent content,
            SignalServiceDataMessage message,
            Optional<GroupId> groupId
    ) {

        if (!groupId.isPresent()) return false;
        if (!message.getBody().isPresent()) return false;

        String strBody = message.getBody().get();
        JsObject body = JsObject.create(strBody);

        String messageType = body.str("messageType");
        if (!messageType.equals("pinMessage")) return false;

        Recipient recipient = Recipient.externalGroup(context, groupId.get());
        Long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);

        String action = body.str("pinMessage_action");
        if (action.equals("pin") || action.equals("unpin")) {
            MessageJob.onIo(() -> {
                PinData.insertMessage(body, recipient, threadId,
                        message.getTimestamp(),
                        content.getServerReceivedTimestamp());
                MessageJob.notifyDataChanged(threadId);
            });
            return true;
        }
        if (action.equals("reorder")) {
            MessageJob.onIo(() -> {
                PinData.insertMessage(body, recipient, threadId,
                        message.getTimestamp(),
                        content.getServerReceivedTimestamp());
                PinData.reorderPinMessage(threadId, body);
                MessageJob.notifyDataChanged(threadId);
            });
            return true;
        }

        return false;
    }

}

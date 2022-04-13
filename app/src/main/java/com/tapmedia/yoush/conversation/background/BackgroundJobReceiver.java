package com.tapmedia.yoush.conversation.background;

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
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class BackgroundJobReceiver {

    private static Context context() {
        return ApplicationContext.getInstance();
    }

    public static boolean handleReceivedMessage(
            SignalServiceContent content,
            SignalServiceDataMessage message,
            Optional<GroupId> groupId
    ) {

        if (!message.getBody().isPresent()) return false;

        String strBody = message.getBody().get();
        JsObject body = JsObject.create(strBody);
        String messageType = body.str("messageType");
        String action = body.str("action");
        if (!messageType.equals("updateWallPaper")) return false;
        if (!action.equals("set") && !action.equals("remove")) return false;
        if (groupId.isPresent()) {
            Recipient recipient = Recipient.externalGroup(context(), groupId.get());
            long threadId = DatabaseFactory.getThreadDatabase(context()).getThreadIdFor(recipient);
            BackgroundData.onDataChange(threadId, () -> {
                BackgroundData.insertMessage(
                        content,
                        message,
                        body,
                        recipient,
                        threadId
                );
                MessageJob.notifyDataChanged(threadId);
            });
            return true;
        }

        SignalServiceAddress address = content.getSender();
        Recipient recipient = Recipient.externalPush(context(), address);
        long threadId = DatabaseFactory.getThreadDatabase(context()).getThreadIdFor(recipient);
        BackgroundData.onDataChange(threadId, () -> {
            BackgroundData.insertMessage(
                    content,
                    message,
                    body,
                    recipient,
                    threadId
            );
            MessageJob.notifyDataChanged(threadId);
        });
        return true;
    }




}

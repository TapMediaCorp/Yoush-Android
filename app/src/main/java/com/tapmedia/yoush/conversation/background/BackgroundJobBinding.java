package com.tapmedia.yoush.conversation.background;

import android.content.Context;
import android.view.View;

import androidx.annotation.StringRes;

import com.tapmedia.yoush.ApplicationContext;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.conversation.ConversationUpdateItem;
import com.tapmedia.yoush.conversation.model.BackgroundWrapper;
import com.tapmedia.yoush.conversation.model.UserAction;
import com.tapmedia.yoush.conversationlist.ConversationListItem;
import com.tapmedia.yoush.database.model.MessageRecord;
import com.tapmedia.yoush.database.model.ThreadRecord;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.util.JsObject;

import java.util.Formatter;


public class BackgroundJobBinding {

    private static String str(@StringRes int res, Object... args) {
        String s = ApplicationContext.getInstance().getString(res);
        return new Formatter().format(s, args).toString();
    }

    /**
     * {@link ConversationUpdateItem}.present
     * 131: if (ConversationPinJob.isBindPinMessage(this, messageRecord)) return;
     * }
     */
    public static boolean isBindMessage(ConversationUpdateItem item, MessageRecord record) {
        if (!BackgroundData.isValidRecord(record)) {
            return false;
        }
        goneUnusedView(item);
        BackgroundWrapper pinWrapper = BackgroundData.getWrapper(record);
        item.body.setVisibility(View.VISIBLE);
        if (pinWrapper.getSilent() != null && pinWrapper.getSilent() == true) {
            item.setVisibility(View.GONE);
        } else {
            item.setVisibility(View.VISIBLE);
            item.body.setText(itemText(pinWrapper));
        }
        return true;
    }

    private static void goneUnusedView(ConversationUpdateItem item) {
        item.icon.setVisibility(View.GONE);
        item.title.setVisibility(View.GONE);
        item.date.setVisibility(View.GONE);
    }

    /**
     * {@link ConversationListItem}@getThreadDisplayBody
     */
    public static String threadRecordText(ThreadRecord record) {
        int index = record.getBody().indexOf("{");
        String jsonBody = record.getBody().substring(index);
        BackgroundWrapper wrapper = JsObject.parse(jsonBody, BackgroundWrapper.class);
        if (wrapper==null) return "";
        wrapper.actionRecipient = UserAction.recipient(wrapper.getUserAction());
        return itemText(wrapper);
    }

    private static String itemText(BackgroundWrapper wrapper) {
        if (wrapper.getSilent() != null && wrapper.getSilent() == true) {
            return str(R.string.MessageRecord_s_updated_group, userText(wrapper));
        }
        StringBuilder sb = new StringBuilder();
        sb.append(userText(wrapper));
        sb.append(" ");
        sb.append(actionText(wrapper));
        return sb.toString();
    }

    private static String userText(BackgroundWrapper wrapper) {
        Context context = ApplicationContext.getInstance();
        Recipient recipient = wrapper.actionRecipient;
        if (!recipient.getE164().isPresent()) {
            return "";
        }
        if (Recipient.self().getE164().get().equals(recipient.getE164().get())) {
            return str(R.string.you);
        }
        return recipient.getDisplayName(context);
    }

    private static String actionText(BackgroundWrapper wrapper) {

        switch (wrapper.getAction()) {
            case "set":
                return str(R.string.set_conversation_background);
            case "remove":
                return str(R.string.remove_conversation_background);
            default:
                return "";
        }
    }

}

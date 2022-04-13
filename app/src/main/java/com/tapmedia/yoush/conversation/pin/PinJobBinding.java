package com.tapmedia.yoush.conversation.pin;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.StringRes;

import com.tapmedia.yoush.ApplicationContext;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.attachments.Attachment;
import com.tapmedia.yoush.conversation.ConversationUpdateItem;
import com.tapmedia.yoush.conversation.model.PinWrapper;
import com.tapmedia.yoush.conversation.model.UserAction;
import com.tapmedia.yoush.conversationlist.ConversationListItem;
import com.tapmedia.yoush.database.model.MessageRecord;
import com.tapmedia.yoush.database.model.MmsMessageRecord;
import com.tapmedia.yoush.database.model.ThreadRecord;
import com.tapmedia.yoush.mms.AudioSlide;
import com.tapmedia.yoush.mms.Slide;
import com.tapmedia.yoush.mms.SlideDeck;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.util.JsObject;

import java.util.Formatter;
import java.util.List;

public class PinJobBinding {

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
        if (PinData.isValidRecord(record)) {
            goneUnusedView(item);
            PinWrapper pinWrapper = PinData.getWrapper(record);
            item.body.setVisibility(View.VISIBLE);
            item.body.setText(itemText(pinWrapper));
            return true;
        }
        return false;
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
        PinWrapper wrapper = JsObject.parse(jsonBody, PinWrapper.class);
        if (wrapper==null) return "";
        wrapper.actionRecipient = UserAction.recipient(wrapper.getUserAction());
        if (null != wrapper.getTimestamp()) {
            wrapper.refRecord = PinData.getMessageRecord(record.getThreadId(), wrapper.getTimestamp());
        }
        return itemText(wrapper);
    }

    /**
     *
     */
    public static String itemText(PinWrapper wrapper) {
        StringBuilder sb = new StringBuilder();
        sb.append(userText(wrapper));
        sb.append(" ");
        sb.append(actionText(wrapper));
        sb.append(" ");
        sb.append(messageText(wrapper));
        return sb.toString();
    }

    private static String userText(PinWrapper wrapper) {
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

    private static String actionText(PinWrapper wrapper) {
        switch (wrapper.getAction()) {
            case "pin":
                return str(R.string.pin_pinned);
            case "unpin":
                return str(R.string.pin_unpin);
            case "reorder":
                return str(R.string.pin_list_updated);
            default:
                return "";
        }
    }

    private static String messageText(PinWrapper wrapper) {
        if (null == wrapper.refRecord) return "";
        if (!TextUtils.isEmpty(wrapper.refRecord.getBody())) {
            return str(R.string.pin_message_text, bodyText(wrapper.refRecord));
        }
        if (!wrapper.refRecord.isMms()) return "";
        MmsMessageRecord record = (MmsMessageRecord) wrapper.refRecord;
        SlideDeck slideDeck = record.getSlideDeck();
        AudioSlide audioSlide = slideDeck.getAudioSlide();
        if (null != audioSlide) {
            return String.format("%s", str(R.string.ThreadRecord_voice_message).toLowerCase()); //🎤
        }
        List<Slide> thumbnailSlides = slideDeck.getThumbnailSlides();
        if (null != thumbnailSlides && thumbnailSlides.size() > 0) {
            Attachment attachment = thumbnailSlides.get(0).asAttachment();
            String contentType = attachment.getContentType();
            if (contentType.indexOf("image") != -1) {
                return String.format("%s", str(R.string.ThreadRecord_photo).toLowerCase());
            }
            if (contentType.indexOf("video") != -1) {
                return String.format("%s", str(R.string.ThreadRecord_video).toLowerCase());
            }
            return String.format("%s", str(R.string.ThreadRecord_file).toLowerCase());
        }
        return str(R.string.pin_message_text, bodyText(wrapper.refRecord));
    }

    public static String bodyText(MessageRecord record) {
        String body = record.getBody();
        if (body.length() >= 26) {
            return body.substring(0, 26);
        } else {
            return body;
        }
    }

}

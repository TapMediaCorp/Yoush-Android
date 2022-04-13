package com.tapmedia.yoush.conversation.pin;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.tapmedia.yoush.ApplicationContext;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.attachments.Attachment;
import com.tapmedia.yoush.components.ThumbnailView;
import com.tapmedia.yoush.conversation.job.MessageJob;
import com.tapmedia.yoush.conversation.model.PinWrapper;
import com.tapmedia.yoush.database.model.MessageRecord;
import com.tapmedia.yoush.database.model.MmsMessageRecord;
import com.tapmedia.yoush.mms.AudioSlide;
import com.tapmedia.yoush.mms.Slide;
import com.tapmedia.yoush.mms.SlideDeck;

import java.util.Formatter;
import java.util.List;

public class ConversationPinItemView extends ConstraintLayout {

    public ConversationPinItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    TextView textViewMessage;
    TextView textViewAuthor;
    ImageView imageView;
    ThumbnailView thumbnailView;

    private static String str(@StringRes int res, Object... args) {
        String s = ApplicationContext.getInstance().getString(res);
        return new Formatter().format(s, args).toString();
    }

    public void bindItem(PinWrapper pinWrapper) {

        textViewMessage = this.findViewById(R.id.conversationPinTextMessage);
        textViewAuthor = this.findViewById(R.id.conversationPinTextAuthor);
        imageView = this.findViewById(R.id.conversationPinImageView);
        thumbnailView = this.findViewById(R.id.conversationPinThumbnailView);

        String authorName = pinWrapper.authorRecipient.getDisplayName(getContext());
        String authorText = getContext().getString(R.string.pin_message_of, authorName);
        textViewAuthor.setText(authorText);


        MessageRecord refRecord = pinWrapper.refRecord;
        if (!refRecord.isMms()) {
            bindText(pinWrapper);
            return;
        }
        MmsMessageRecord record = (MmsMessageRecord) refRecord;
        SlideDeck slideDeck = record.getSlideDeck();
        AudioSlide audioSlide = slideDeck.getAudioSlide();
        if (null != audioSlide) {
            bindAudio();
            return;
        }
        List<Slide> thumbnailSlides = slideDeck.getThumbnailSlides();
        if (null != thumbnailSlides && thumbnailSlides.size() > 0) {
            bindThumbnail(record, thumbnailSlides);
            return;
        }
        bindText(pinWrapper);
    }

    private void bindText(PinWrapper record) {
        imageView.setImageResource(0);
        thumbnailView.setVisibility(INVISIBLE);
        textViewMessage.setText(PinJobBinding.bodyText(record.refRecord));
    }

    private void bindAudio() {
        imageView.setImageResource(R.drawable.ic_file);
        thumbnailView.setVisibility(INVISIBLE);
        textViewMessage.setText(String.format("[%s]", str(R.string.ThreadRecord_voice_message)));
    }

    private void bindThumbnail(MmsMessageRecord record, List<Slide> thumbnailSlides) {
        Attachment attachment = thumbnailSlides.get(0).asAttachment();
        imageView.setImageResource(0);
        thumbnailView.setVisibility(VISIBLE);
        thumbnailView.setImageResource(
                MessageJob.glideRequests,
                thumbnailSlides.get(0), true, false,
                attachment.getWidth(),
                attachment.getHeight()
        );
        if (!TextUtils.isEmpty(record.getBody())) {
            textViewMessage.setText(PinJobBinding.bodyText(record));
            return;
        }
        String contentType = attachment.getContentType();
        if (contentType.indexOf("image") != -1) {
            textViewMessage.setText(String.format("[%s]", str(R.string.ThreadRecord_photo)));
            return;
        }
        if (contentType.indexOf("video") != -1) {
            textViewMessage.setText(String.format("[%s]", str(R.string.ThreadRecord_video)));
            return;
        }
        textViewMessage.setText(String.format("[%s]", str(R.string.ThreadRecord_file)));
    }

    public void visibleSeparator(int visible) {
        this.findViewById(R.id.conversationPinSeparator).setVisibility(visible);
    }

}

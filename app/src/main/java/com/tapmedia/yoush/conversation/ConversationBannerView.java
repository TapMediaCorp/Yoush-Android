package com.tapmedia.yoush.conversation;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.components.AvatarImageView;
import com.tapmedia.yoush.contacts.avatars.FallbackContactPhoto;
import com.tapmedia.yoush.contacts.avatars.ResourceContactPhoto;
import com.tapmedia.yoush.mms.GlideRequests;
import com.tapmedia.yoush.recipients.Recipient;


public class ConversationBannerView extends ConstraintLayout {

  private AvatarImageView contactAvatar;
  private TextView        contactTitle;
  private TextView        contactSubtitle;
  private TextView        contactDescription;

  public ConversationBannerView(Context context) {
    this(context, null);
  }

  public ConversationBannerView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ConversationBannerView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    inflate(getContext(), R.layout.conversation_banner_view, this);

    contactAvatar      = findViewById(R.id.message_request_avatar);
    contactTitle       = findViewById(R.id.message_request_title);
    contactSubtitle    = findViewById(R.id.message_request_subtitle);
    contactDescription = findViewById(R.id.message_request_description);

    contactAvatar.setFallbackPhotoProvider(new FallbackPhotoProvider());
  }

  public void setAvatar(@NonNull GlideRequests requests, @Nullable Recipient recipient) {
    contactAvatar.setAvatar(requests, recipient, false);
  }

  public void setTitle(@Nullable CharSequence title) {
    contactTitle.setText(title);
  }

  public void setSubtitle(@Nullable CharSequence subtitle) {
    contactSubtitle.setText(subtitle);
  }

  public void setDescription(@Nullable CharSequence description) {
    contactDescription.setText(description);
  }

  public void hideSubtitle() {
    contactSubtitle.setVisibility(View.GONE);
  }

  public void showDescription() {
    contactDescription.setVisibility(View.VISIBLE);
  }

  public void hideDescription() {
    contactDescription.setVisibility(View.GONE);
  }

  private static final class FallbackPhotoProvider extends Recipient.FallbackPhotoProvider {
    @Override
    public @NonNull FallbackContactPhoto getPhotoForRecipientWithoutName() {
      return new ResourceContactPhoto(R.drawable.ic_profile_80_new);
    }

    @Override
    public @NonNull FallbackContactPhoto getPhotoForGroup() {
      return new ResourceContactPhoto(R.drawable.ic_group_80_new);
    }

    @Override
    public @NonNull FallbackContactPhoto getPhotoForLocalNumber() {
      return new ResourceContactPhoto(R.drawable.ic_note_80_new);
    }
  }
}

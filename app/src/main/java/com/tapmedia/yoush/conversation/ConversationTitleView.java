package com.tapmedia.yoush.conversation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.components.AvatarImageView;
import com.tapmedia.yoush.mms.GlideRequests;
import com.tapmedia.yoush.recipients.LiveRecipient;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.util.ExpirationUtil;
import com.tapmedia.yoush.util.FeatureFlags;
import com.tapmedia.yoush.util.Util;
import com.tapmedia.yoush.util.ViewUtil;

import java.util.UUID;

public class ConversationTitleView extends RelativeLayout {

  @SuppressWarnings("unused")
  private static final String TAG = ConversationTitleView.class.getSimpleName();

  private View            content;
  private AvatarImageView avatar;
  private TextView        title;
  private TextView        subtitle;
  private ImageView       verified;
  private View            subtitleContainer;
  private View            verifiedSubtitle;
  private View            expirationBadgeContainer;
  private TextView        expirationBadgeTime;

  public ConversationTitleView(Context context) {
    this(context, null);
  }

  public ConversationTitleView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();

    this.content                  = ViewUtil.findById(this, R.id.content);
    this.title                    = ViewUtil.findById(this, R.id.title);
    this.subtitle                 = ViewUtil.findById(this, R.id.subtitle);
    this.verified                 = ViewUtil.findById(this, R.id.verified_indicator);
    this.subtitleContainer        = ViewUtil.findById(this, R.id.subtitle_container);
    this.verifiedSubtitle         = ViewUtil.findById(this, R.id.verified_subtitle);
    this.avatar                   = ViewUtil.findById(this, R.id.contact_photo_image);
    this.expirationBadgeContainer = ViewUtil.findById(this, R.id.expiration_badge_container);
    this.expirationBadgeTime      = ViewUtil.findById(this, R.id.expiration_badge);

    ViewUtil.setTextViewGravityStart(this.title, getContext());
    ViewUtil.setTextViewGravityStart(this.subtitle, getContext());
  }

  public void showExpiring(@NonNull LiveRecipient recipient) {
    expirationBadgeTime.setText(ExpirationUtil.getExpirationAbbreviatedDisplayValue(getContext(), recipient.get().getExpireMessages()));
    expirationBadgeContainer.setVisibility(View.VISIBLE);
    updateSubtitleVisibility();
  }

  public void clearExpiring() {
    expirationBadgeContainer.setVisibility(View.GONE);
    updateSubtitleVisibility();
  }

  public void setTitle(@NonNull GlideRequests glideRequests, @Nullable Recipient recipient) {
    this.subtitleContainer.setVisibility(View.VISIBLE);

    if      (recipient == null) setComposeTitle();
    else                        setRecipientTitle(recipient);

    if (recipient != null && recipient.isBlocked()) {
      title.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_block_white_18dp, 0, 0, 0);
    } else if (recipient != null && recipient.isMuted()) {
      title.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_volume_off_white_18dp, 0, 0, 0);
    } else {
      title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
    }

    if (recipient != null) {
      this.avatar.setAvatar(glideRequests, recipient, false);
    }

    updateVerifiedSubtitleVisibility();
  }

  public void setVerified(boolean verified) {
    this.verified.setVisibility(verified ? View.VISIBLE : View.GONE);

    updateVerifiedSubtitleVisibility();
  }

  private void setComposeTitle() {
    this.title.setText(R.string.ConversationActivity_compose_message);
    this.subtitle.setText(null);
    updateSubtitleVisibility();
  }

  private void setRecipientTitle(Recipient recipient) {
    if      (recipient.isGroup())       setGroupRecipientTitle(recipient);
    else if (recipient.isLocalNumber()) setSelfTitle();
    else                                setIndividualRecipientTitle(recipient);
  }

  @SuppressLint("SetTextI18n")
  private void setNonContactRecipientTitle(Recipient recipient) {
    this.title.setText(Util.getFirstNonEmpty(recipient.getE164().orNull(), recipient.getUuid().transform(UUID::toString).orNull()));

    if (recipient.getProfileName().isEmpty()) {
      this.subtitle.setText(null);
    } else {
      this.subtitle.setText("~" + recipient.getProfileName().toString());
    }

    updateSubtitleVisibility();
  }

  private void setGroupRecipientTitle(Recipient recipient) {
    this.title.setText(recipient.getDisplayName(getContext()));
    this.subtitle.setText(Stream.of(recipient.getParticipants())
                                .sorted((a, b) -> Boolean.compare(a.isLocalNumber(), b.isLocalNumber()))
                                .map(r -> r.isLocalNumber() ? getResources().getString(R.string.ConversationTitleView_you)
                                                            : r.getDisplayName(getContext()))
                                .collect(Collectors.joining(", ")));

    updateSubtitleVisibility();
  }

  private void setSelfTitle() {
    this.title.setText(R.string.note_to_self);
    this.subtitleContainer.setVisibility(View.GONE);
  }

  private void setIndividualRecipientTitle(Recipient recipient) {
    final String displayName = recipient.getDisplayName(getContext());
    this.title.setText(displayName);
    this.subtitle.setText(null);
    updateVerifiedSubtitleVisibility();
  }

  private void updateVerifiedSubtitleVisibility() {
    verifiedSubtitle.setVisibility(subtitle.getVisibility() != VISIBLE && verified.getVisibility() == VISIBLE ? VISIBLE : GONE);
  }

  private void updateSubtitleVisibility() {
    subtitle.setVisibility(expirationBadgeContainer.getVisibility() != VISIBLE && !TextUtils.isEmpty(subtitle.getText()) ? VISIBLE : GONE);
    updateVerifiedSubtitleVisibility();
  }
}

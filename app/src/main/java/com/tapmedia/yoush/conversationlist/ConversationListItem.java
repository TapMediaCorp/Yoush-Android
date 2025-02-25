/*
 * Copyright (C) 2014-2017 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tapmedia.yoush.conversationlist;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.tapmedia.yoush.BindableConversationListItem;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.Unbindable;
import com.tapmedia.yoush.components.AlertView;
import com.tapmedia.yoush.components.AvatarImageView;
import com.tapmedia.yoush.components.DeliveryStatusView;
import com.tapmedia.yoush.components.FromTextView;
import com.tapmedia.yoush.components.ThumbnailView;
import com.tapmedia.yoush.components.TypingIndicatorView;
import com.tapmedia.yoush.conversation.background.BackgroundData;
import com.tapmedia.yoush.conversation.background.BackgroundJobBinding;
import com.tapmedia.yoush.conversation.pin.PinData;
import com.tapmedia.yoush.conversation.pin.PinJobBinding;
import com.tapmedia.yoush.conversationlist.model.MessageResult;
import com.tapmedia.yoush.database.MmsSmsColumns;
import com.tapmedia.yoush.database.SmsDatabase;
import com.tapmedia.yoush.database.ThreadDatabase;
import com.tapmedia.yoush.database.model.ThreadRecord;
import com.tapmedia.yoush.mms.GlideRequests;
import com.tapmedia.yoush.recipients.LiveRecipient;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientForeverObserver;
import com.tapmedia.yoush.util.DateUtils;
import com.tapmedia.yoush.util.ExpirationUtil;
import com.tapmedia.yoush.util.MediaUtil;
import com.tapmedia.yoush.util.SearchUtil;
import com.tapmedia.yoush.util.ThemeUtil;
import com.tapmedia.yoush.util.Util;
import com.tapmedia.yoush.util.ViewUtil;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

public class ConversationListItem extends RelativeLayout
                                  implements RecipientForeverObserver,
    BindableConversationListItem, Unbindable
{
  @SuppressWarnings("unused")
  private final static String TAG = ConversationListItem.class.getSimpleName();

  private final static Typeface  BOLD_TYPEFACE  = Typeface.create("sans-serif-medium", Typeface.NORMAL);
  private final static Typeface  LIGHT_TYPEFACE = Typeface.create("sans-serif", Typeface.NORMAL);

  private static final int MAX_SNIPPET_LENGTH = 500;

  private Set<Long>           selectedThreads;
  private LiveRecipient       recipient;
  private LiveRecipient       groupAddedBy;
  private long                threadId;
  private GlideRequests       glideRequests;
  private View                subjectContainer;
  private TextView            subjectView;
  private TypingIndicatorView typingView;
  private FromTextView        fromView;
  private TextView            dateView;
  private TextView            archivedView;
  private DeliveryStatusView  deliveryStatusIndicator;
  private AlertView           alertView;
  private TextView            unreadIndicator;
  private long                lastSeen;
  private ThreadRecord        thread;
  private boolean             batchMode;

  private int             unreadCount;
  private AvatarImageView contactPhotoImage;
  private ThumbnailView   thumbnailView;

  private int distributionType;

  private final RecipientForeverObserver groupAddedByObserver = adder -> {
    if (isAttachedToWindow() && subjectView != null && thread != null) {
      subjectView.setText(getThreadDisplayBody(getContext(), thread));
    }
  };

  public ConversationListItem(Context context) {
    this(context, null);
  }

  public ConversationListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.subjectContainer        = findViewById(R.id.subject_container);
    this.subjectView             = findViewById(R.id.subject);
    this.typingView              = findViewById(R.id.typing_indicator);
    this.fromView                = findViewById(R.id.from);
    this.dateView                = findViewById(R.id.date);
    this.deliveryStatusIndicator = findViewById(R.id.delivery_status);
    this.alertView               = findViewById(R.id.indicators_parent);
    this.contactPhotoImage       = findViewById(R.id.contact_photo_image);
    this.thumbnailView           = findViewById(R.id.thumbnail);
    this.archivedView            = findViewById(R.id.archived);
    this.unreadIndicator         = findViewById(R.id.unread_indicator);
    thumbnailView.setClickable(false);

    ViewUtil.setTextViewGravityStart(this.fromView, getContext());
    ViewUtil.setTextViewGravityStart(this.subjectView, getContext());
  }

  @Override
  public void bind(@NonNull ThreadRecord thread,
                   @NonNull GlideRequests glideRequests,
                   @NonNull Locale locale,
                   @NonNull Set<Long> typingThreads,
                   @NonNull Set<Long> selectedThreads,
                   boolean batchMode)
  {
    bind(thread, glideRequests, locale, typingThreads, selectedThreads, batchMode, null);
  }

  public void bind(@NonNull ThreadRecord thread,
                   @NonNull GlideRequests glideRequests,
                   @NonNull Locale locale,
                   @NonNull Set<Long> typingThreads,
                   @NonNull Set<Long> selectedThreads,
                   boolean batchMode,
                   @Nullable String highlightSubstring)
  {
    if (this.recipient != null) this.recipient.removeForeverObserver(this);
    if (this.groupAddedBy != null) this.groupAddedBy.removeForeverObserver(groupAddedByObserver);

    this.selectedThreads  = selectedThreads;
    this.recipient        = thread.getRecipient().live();
    this.threadId         = thread.getThreadId();
    this.glideRequests    = glideRequests;
    this.unreadCount      = thread.getUnreadCount();
    this.distributionType = thread.getDistributionType();
    this.lastSeen         = thread.getLastSeen();
    this.thread           = thread;

    this.recipient.observeForever(this);
    if (highlightSubstring != null) {
      String name = recipient.get().isLocalNumber() ? getContext().getString(R.string.note_to_self) : recipient.get().getDisplayName(getContext());

      this.fromView.setText(SearchUtil.getHighlightedSpan(locale, () -> new StyleSpan(Typeface.BOLD), name, highlightSubstring));
    } else {
      this.fromView.setText(recipient.get(), thread.isRead());
    }

    updateTypingIndicator(typingThreads);

    this.subjectView.setText(getTrimmedSnippet(getThreadDisplayBody(getContext(), thread)));

    if (thread.getGroupAddedBy() != null) {
      groupAddedBy = Recipient.live(thread.getGroupAddedBy());
      groupAddedBy.observeForever(groupAddedByObserver);
    }

    this.subjectView.setTypeface(thread.isRead() ? LIGHT_TYPEFACE : BOLD_TYPEFACE);
    this.subjectView.setTextColor(thread.isRead() ? ThemeUtil.getThemedColor(getContext(), R.attr.conversation_list_item_subject_color)
                                                  : ThemeUtil.getThemedColor(getContext(), R.attr.conversation_list_item_unread_color));

    if (thread.getDate() > 0) {
      CharSequence date = DateUtils.getBriefRelativeTimeSpanString(getContext(), locale, thread.getDate());
      dateView.setText(date);
      dateView.setTypeface(thread.isRead() ? LIGHT_TYPEFACE : BOLD_TYPEFACE);
      dateView.setTextColor(thread.isRead() ? ThemeUtil.getThemedColor(getContext(), R.attr.conversation_list_item_date_color)
                                            : ThemeUtil.getThemedColor(getContext(), R.attr.conversation_list_item_unread_color));
    }

    if (thread.isArchived()) {
      this.archivedView.setVisibility(View.VISIBLE);
    } else {
      this.archivedView.setVisibility(View.GONE);
    }

    setStatusIcons(thread);
    setThumbnailSnippet(thread);
    setBatchMode(batchMode);
    setRippleColor(recipient.get());
      setUnreadIndicator(thread);
      contactPhotoImage.setAvatar(glideRequests, recipient.get(), !batchMode);
  }

  public void bind(@NonNull  Recipient     contact,
                   @NonNull  GlideRequests glideRequests,
                   @NonNull  Locale        locale,
                   @Nullable String        highlightSubstring)
  {
    if (this.recipient != null) this.recipient.removeForeverObserver(this);
    if (this.groupAddedBy != null) this.groupAddedBy.removeForeverObserver(groupAddedByObserver);

    this.selectedThreads = Collections.emptySet();
    this.recipient       = contact.live();
    this.glideRequests   = glideRequests;

    this.recipient.observeForever(this);

    fromView.setText(contact);
    fromView.setText(SearchUtil.getHighlightedSpan(locale, () -> new StyleSpan(Typeface.BOLD), new SpannableString(fromView.getText()), highlightSubstring));
    subjectView.setText(SearchUtil.getHighlightedSpan(locale, () -> new StyleSpan(Typeface.BOLD), contact.getE164().or(""), highlightSubstring));
    dateView.setText("");
    archivedView.setVisibility(GONE);
    unreadIndicator.setVisibility(GONE);
    deliveryStatusIndicator.setNone();
    alertView.setNone();
    thumbnailView.setVisibility(GONE);

    setBatchMode(false);
    setRippleColor(contact);
    contactPhotoImage.setAvatar(glideRequests, recipient.get(), !batchMode);
  }

  public void bind(@NonNull  MessageResult messageResult,
                   @NonNull  GlideRequests glideRequests,
                   @NonNull  Locale        locale,
                   @Nullable String        highlightSubstring)
  {
    if (this.recipient != null) this.recipient.removeForeverObserver(this);
    if (this.groupAddedBy != null) this.groupAddedBy.removeForeverObserver(groupAddedByObserver);

    this.selectedThreads = Collections.emptySet();
    this.recipient       = messageResult.conversationRecipient.live();
    this.glideRequests   = glideRequests;

    this.recipient.observeForever(this);

    fromView.setText(recipient.get(), true);
    subjectView.setText(SearchUtil.getHighlightedSpan(locale, () -> new StyleSpan(Typeface.BOLD), messageResult.bodySnippet, highlightSubstring));
    dateView.setText(DateUtils.getBriefRelativeTimeSpanString(getContext(), locale, messageResult.receivedTimestampMs));
    archivedView.setVisibility(GONE);
    unreadIndicator.setVisibility(GONE);
    deliveryStatusIndicator.setNone();
    alertView.setNone();
    thumbnailView.setVisibility(GONE);

    setBatchMode(false);
    setRippleColor(recipient.get());
    contactPhotoImage.setAvatar(glideRequests, recipient.get(), !batchMode);
  }

  @Override
  public void unbind() {
    if (this.recipient != null) {
      this.recipient.removeForeverObserver(this);
      this.recipient = null;

      setBatchMode(false);
      contactPhotoImage.setAvatar(glideRequests, null, !batchMode);
    }

    if (this.groupAddedBy != null) {
      this.groupAddedBy.removeForeverObserver(groupAddedByObserver);
      this.groupAddedBy = null;
    }
  }

  @Override
  public void setBatchMode(boolean batchMode) {
    this.batchMode = batchMode;
    setSelected(batchMode && selectedThreads.contains(thread.getThreadId()));
  }

  @Override
  public void updateTypingIndicator(@NonNull Set<Long> typingThreads) {
    if (typingThreads.contains(threadId)) {
      this.subjectView.setVisibility(INVISIBLE);

      this.typingView.setVisibility(VISIBLE);
      this.typingView.startAnimation();
    } else {
      this.typingView.setVisibility(GONE);
      this.typingView.stopAnimation();

      this.subjectView.setVisibility(VISIBLE);
    }
  }

  public Recipient getRecipient() {
    return recipient.get();
  }

  public long getThreadId() {
    return threadId;
  }

  public @NonNull ThreadRecord getThread() {
    return thread;
  }

  public int getUnreadCount() {
    return unreadCount;
  }

  public int getDistributionType() {
    return distributionType;
  }

  public long getLastSeen() {
    return lastSeen;
  }

  private static @NonNull CharSequence getTrimmedSnippet(@NonNull CharSequence snippet) {
    return snippet.length() <= MAX_SNIPPET_LENGTH ? snippet
                                                  : snippet.subSequence(0, MAX_SNIPPET_LENGTH);
  }

  private void setThumbnailSnippet(ThreadRecord thread) {
    if (thread.getSnippetUri() != null) {
      this.thumbnailView.setVisibility(View.VISIBLE);
      this.thumbnailView.setImageResource(glideRequests, thread.getSnippetUri());

      LayoutParams subjectParams = (RelativeLayout.LayoutParams)this.subjectContainer .getLayoutParams();
      subjectParams.addRule(RelativeLayout.LEFT_OF, R.id.thumbnail);
      subjectParams.addRule(RelativeLayout.START_OF, R.id.thumbnail);
      this.subjectContainer.setLayoutParams(subjectParams);
      this.post(new ThumbnailPositioner(thumbnailView, archivedView, deliveryStatusIndicator, dateView));
    } else {
      this.thumbnailView.setVisibility(View.GONE);

      LayoutParams subjectParams = (RelativeLayout.LayoutParams)this.subjectContainer.getLayoutParams();
      subjectParams.addRule(RelativeLayout.LEFT_OF, R.id.status);
      subjectParams.addRule(RelativeLayout.START_OF, R.id.status);
      this.subjectContainer.setLayoutParams(subjectParams);
    }
  }

  private void setStatusIcons(ThreadRecord thread) {
    if (!thread.isOutgoing() || thread.isOutgoingCall() || thread.isVerificationStatusChange()) {
      deliveryStatusIndicator.setNone();
      alertView.setNone();
    } else if (thread.isFailed()) {
      deliveryStatusIndicator.setNone();
      alertView.setFailed();
    } else if (thread.isPendingInsecureSmsFallback()) {
      deliveryStatusIndicator.setNone();
      alertView.setPendingApproval();
    } else {
      alertView.setNone();

      if      (thread.isPending())    deliveryStatusIndicator.setPending();
      else if (thread.isRemoteRead()) deliveryStatusIndicator.setRead();
      else if (thread.isDelivered())  deliveryStatusIndicator.setDelivered();
      else                            deliveryStatusIndicator.setSent();
    }
  }

  private void setRippleColor(Recipient recipient) {
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      ((RippleDrawable)(getBackground()).mutate())
          .setColor(ColorStateList.valueOf(recipient.getColor().toConversationColor(getContext())));
    }
  }

  private void setUnreadIndicator(ThreadRecord thread) {
    if ((thread.isOutgoing() && !thread.isForcedUnread()) || thread.isRead()) {
      unreadIndicator.setVisibility(View.GONE);
      return;
    }

    unreadIndicator.setText(unreadCount > 0 ? String.valueOf(unreadCount) : " ");
    unreadIndicator.setVisibility(View.VISIBLE);
  }

  @Override
  public void onRecipientChanged(@NonNull Recipient recipient) {
    fromView.setText(recipient, unreadCount == 0);
    contactPhotoImage.setAvatar(glideRequests, recipient, !batchMode);
    setRippleColor(recipient);
  }


  private static SpannableString getThreadDisplayBody(@NonNull Context context, @NonNull ThreadRecord thread) {
    if (PinData.isValidRecord(thread)) {
      return new SpannableString(PinJobBinding.threadRecordText(thread));
    }
    if (BackgroundData.isValidRecord(thread)) {
      return new SpannableString(BackgroundJobBinding.threadRecordText(thread));
    }
    if (thread.getGroupAddedBy() != null) {
      return emphasisAdded(context.getString(thread.isGv2Invite() ? R.string.ThreadRecord_s_invited_you_to_the_group
                                                                  : R.string.ThreadRecord_s_added_you_to_the_group,
                                             Recipient.live(thread.getGroupAddedBy()).get().getDisplayName(context)));
    } else if (!thread.isMessageRequestAccepted()) {
      return emphasisAdded(context.getString(R.string.ThreadRecord_message_request));
    } else if (SmsDatabase.Types.isGroupUpdate(thread.getType())) {
      return emphasisAdded(context.getString(R.string.ThreadRecord_group_updated));
    } else if (SmsDatabase.Types.isGroupQuit(thread.getType())) {
      return emphasisAdded(context.getString(R.string.ThreadRecord_left_the_group));
    } else if (SmsDatabase.Types.isKeyExchangeType(thread.getType())) {
      return emphasisAdded(context.getString(R.string.ConversationListItem_key_exchange_message));
    } else if (SmsDatabase.Types.isFailedDecryptType(thread.getType())) {
      return emphasisAdded(context.getString(R.string.MessageDisplayHelper_bad_encrypted_message));
    } else if (SmsDatabase.Types.isNoRemoteSessionType(thread.getType())) {
      return emphasisAdded(context.getString(R.string.MessageDisplayHelper_message_encrypted_for_non_existing_session));
    } else if (SmsDatabase.Types.isEndSessionType(thread.getType())) {
      return emphasisAdded(context.getString(R.string.ThreadRecord_secure_session_reset));
    } else if (MmsSmsColumns.Types.isLegacyType(thread.getType())) {
      return emphasisAdded(context.getString(R.string.MessageRecord_message_encrypted_with_a_legacy_protocol_version_that_is_no_longer_supported));
    } else if (MmsSmsColumns.Types.isDraftMessageType(thread.getType())) {
      String draftText = context.getString(R.string.ThreadRecord_draft);
      return emphasisAdded(draftText + " " + thread.getBody(), 0, draftText.length());
    } else if (SmsDatabase.Types.isOutgoingCall(thread.getType())) {
      return emphasisAdded(context.getString(com.tapmedia.yoush.R.string.ThreadRecord_called));
    } else if (SmsDatabase.Types.isIncomingCall(thread.getType())) {
      return emphasisAdded(context.getString(com.tapmedia.yoush.R.string.ThreadRecord_called_you));
    } else if (SmsDatabase.Types.isMissedCall(thread.getType())) {
      return emphasisAdded(context.getString(com.tapmedia.yoush.R.string.ThreadRecord_missed_call));
    } else if (SmsDatabase.Types.isJoinedType(thread.getType())) {
      return emphasisAdded(context.getString(R.string.ThreadRecord_s_is_on_signal, thread.getRecipient().getDisplayName(context)));
    } else if (SmsDatabase.Types.isExpirationTimerUpdate(thread.getType())) {
      int seconds = (int)(thread.getExpiresIn() / 1000);
      if (seconds <= 0) {
        return emphasisAdded(context.getString(R.string.ThreadRecord_disappearing_messages_disabled));
      }
      String time = ExpirationUtil.getExpirationDisplayValue(context, seconds);
      return emphasisAdded(context.getString(R.string.ThreadRecord_disappearing_message_time_updated_to_s, time));
    } else if (SmsDatabase.Types.isIdentityUpdate(thread.getType())) {
      if (thread.getRecipient().isGroup()) {
        return emphasisAdded(context.getString(R.string.ThreadRecord_safety_number_changed));
      } else {
        return emphasisAdded(context.getString(R.string.ThreadRecord_your_safety_number_with_s_has_changed, thread.getRecipient().getDisplayName(context)));
      }
    } else if (SmsDatabase.Types.isIdentityVerified(thread.getType())) {
      return emphasisAdded(context.getString(R.string.ThreadRecord_you_marked_verified));
    } else if (SmsDatabase.Types.isIdentityDefault(thread.getType())) {
      return emphasisAdded(context.getString(R.string.ThreadRecord_you_marked_unverified));
    } else if (SmsDatabase.Types.isUnsupportedMessageType(thread.getType())) {
      return emphasisAdded(context.getString(R.string.ThreadRecord_message_could_not_be_processed));
    } else {
      ThreadDatabase.Extra extra = thread.getExtra();
      if (extra != null && extra.isViewOnce()) {
        return new SpannableString(emphasisAdded(getViewOnceDescription(context, thread.getContentType())));
      } else if (extra != null && extra.isRemoteDelete()) {
        return new SpannableString(emphasisAdded(context.getString(thread.isOutgoing() ? R.string.ThreadRecord_you_deleted_this_message : R.string.ThreadRecord_this_message_was_deleted)));
      } else {
        String body = thread.getBody();

        if (isJSONValid(body)) {
          try {
            JSONObject json = new JSONObject(body);

            if (json.getString("messageType") != null) {
              String messageType = json.getString("messageType");

              if (messageType.equals("groupCall") || messageType.equals("call")) {
                String groupMessageBody = json.getString("message");
                return new SpannableString(Util.emptyIfNull(groupMessageBody));
              } else {
                return new SpannableString(Util.emptyIfNull(thread.getBody()));
              }
            } else {
              return new SpannableString(Util.emptyIfNull(thread.getBody()));
            }



          } catch (JSONException e) {
            return new SpannableString(Util.emptyIfNull(thread.getBody()));
            //throw new IllegalStateException("JSON VALID: " + e.getMessage());
          }
        } else {
          return new SpannableString(Util.emptyIfNull(thread.getBody()));
        }
      }
    }
  }

  private static @NonNull SpannableString emphasisAdded(String sequence) {
    return emphasisAdded(sequence, 0, sequence.length());
  }

  private static @NonNull SpannableString emphasisAdded(String sequence, int start, int end) {
    SpannableString spannable = new SpannableString(sequence);
    spannable.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC),
                      start,
                      end,
                      Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return spannable;
  }

  private static String getViewOnceDescription(@NonNull Context context, @Nullable String contentType) {
    if (MediaUtil.isViewOnceType(contentType)) {
      return context.getString(R.string.ThreadRecord_view_once_media);
    } else if (MediaUtil.isVideoType(contentType)) {
      return context.getString(R.string.ThreadRecord_view_once_video);
    } else {
      return context.getString(R.string.ThreadRecord_view_once_photo);
    }
  }

  private static class ThumbnailPositioner implements Runnable {

    private final View thumbnailView;
    private final View archivedView;
    private final View deliveryStatusView;
    private final View dateView;

    ThumbnailPositioner(View thumbnailView, View archivedView, View deliveryStatusView, View dateView) {
      this.thumbnailView      = thumbnailView;
      this.archivedView       = archivedView;
      this.deliveryStatusView = deliveryStatusView;
      this.dateView           = dateView;
    }

    @Override
    public void run() {
      LayoutParams thumbnailParams = (RelativeLayout.LayoutParams)thumbnailView.getLayoutParams();

      if (archivedView.getVisibility() == View.VISIBLE &&
          (archivedView.getWidth() + deliveryStatusView.getWidth()) > dateView.getWidth())
      {
        thumbnailParams.addRule(RelativeLayout.LEFT_OF, R.id.status);
        thumbnailParams.addRule(RelativeLayout.START_OF, R.id.status);
      } else {
        thumbnailParams.addRule(RelativeLayout.LEFT_OF, R.id.date);
        thumbnailParams.addRule(RelativeLayout.START_OF, R.id.date);
      }

      thumbnailView.setLayoutParams(thumbnailParams);
    }
  }

  public static boolean isJSONValid(String test) {
    try {
      new JSONObject(test);
    } catch (JSONException ex) {
      // edited, to include @Arthur's comment
      // e.g. in case JSONArray is valid as well...
      try {
        new JSONArray(test);
      } catch (JSONException ex1) {
        return false;
      }
    }
    return true;
  }

}

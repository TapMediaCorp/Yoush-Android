/*
 * Copyright (C) 2012 Moxie Marlinpsike
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
package com.tapmedia.yoush.database.model;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.database.MmsSmsColumns;
import com.tapmedia.yoush.database.SmsDatabase;
import com.tapmedia.yoush.database.documents.IdentityKeyMismatch;
import com.tapmedia.yoush.database.documents.NetworkFailure;
import com.tapmedia.yoush.database.model.databaseprotos.DecryptedGroupV2Context;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.util.Base64;
import com.tapmedia.yoush.util.ExpirationUtil;
import com.tapmedia.yoush.util.GroupUtil;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * The base class for message record models that are displayed in
 * conversations, as opposed to models that are displayed in a thread list.
 * Encapsulates the shared data between both SMS and MMS messages.
 *
 * @author Moxie Marlinspike
 *
 */
public abstract class MessageRecord extends DisplayRecord {

  private static final String TAG = Log.tag(MessageRecord.class);

  private final Recipient                 individualRecipient;
  private final int                       recipientDeviceId;
  private final long                      id;
  private final List<IdentityKeyMismatch> mismatches;
  private final List<NetworkFailure>      networkFailures;
  private final int                       subscriptionId;
  private final long                      expiresIn;
  private final long                      expireStarted;
  private final boolean                   unidentified;
  private final List<ReactionRecord>      reactions;
  private final long                      serverTimestamp;
  private final boolean                   remoteDelete;

  MessageRecord(long id, String body, Recipient conversationRecipient,
                Recipient individualRecipient, int recipientDeviceId,
                long dateSent, long dateReceived, long dateServer, long threadId,
                int deliveryStatus, int deliveryReceiptCount, long type,
                List<IdentityKeyMismatch> mismatches,
                List<NetworkFailure> networkFailures,
                int subscriptionId, long expiresIn, long expireStarted,
                int readReceiptCount, boolean unidentified,
                @NonNull List<ReactionRecord> reactions, boolean remoteDelete)
  {
    super(body, conversationRecipient, dateSent, dateReceived,
          threadId, deliveryStatus, deliveryReceiptCount, type, readReceiptCount);
    this.id                  = id;
    this.individualRecipient = individualRecipient;
    this.recipientDeviceId   = recipientDeviceId;
    this.mismatches          = mismatches;
    this.networkFailures     = networkFailures;
    this.subscriptionId      = subscriptionId;
    this.expiresIn           = expiresIn;
    this.expireStarted       = expireStarted;
    this.unidentified        = unidentified;
    this.reactions           = reactions;
    this.serverTimestamp     = dateServer;
    this.remoteDelete        = remoteDelete;
  }

  public abstract boolean isMms();
  public abstract boolean isMmsNotification();

  public boolean isSecure() {
    return MmsSmsColumns.Types.isSecureType(type);
  }

  public boolean isLegacyMessage() {
    return MmsSmsColumns.Types.isLegacyType(type);
  }

  @Override
  public SpannableString getDisplayBody(@NonNull Context context) {
    if (isGroupUpdate() && isGroupV2()) {
      return new SpannableString(getGv2Description(context));
    } else if (isGroupUpdate() && isOutgoing()) {
      return new SpannableString(context.getString(R.string.MessageRecord_you_updated_group));
    } else if (isGroupUpdate()) {
      return new SpannableString(GroupUtil.getDescription(context, getBody(), false).toString(getIndividualRecipient()));
    } else if (isGroupQuit() && isOutgoing()) {
      return new SpannableString(context.getString(R.string.MessageRecord_left_group));
    } else if (isGroupQuit()) {
      return new SpannableString(context.getString(R.string.ConversationItem_group_action_left, getIndividualRecipient().getDisplayName(context)));
    } else if (isIncomingCall()) {
      return new SpannableString(context.getString(R.string.MessageRecord_s_called_you, getIndividualRecipient().getDisplayName(context)));
    } else if (isOutgoingCall()) {
      return new SpannableString(context.getString(R.string.MessageRecord_you_called));
    } else if (isMissedCall()) {
      return new SpannableString(context.getString(R.string.MessageRecord_missed_call));
    } else if (isJoined()) {
      return new SpannableString(context.getString(R.string.MessageRecord_s_joined_signal, getIndividualRecipient().getDisplayName(context)));
    } else if (isExpirationTimerUpdate()) {
      int seconds = (int)(getExpiresIn() / 1000);
      if (seconds <= 0) {
        return isOutgoing() ? new SpannableString(context.getString(R.string.MessageRecord_you_disabled_disappearing_messages))
                            : new SpannableString(context.getString(R.string.MessageRecord_s_disabled_disappearing_messages, getIndividualRecipient().getDisplayName(context)));
      }
      String time = ExpirationUtil.getExpirationDisplayValue(context, seconds);
      return isOutgoing() ? new SpannableString(context.getString(R.string.MessageRecord_you_set_disappearing_message_time_to_s, time))
                          : new SpannableString(context.getString(R.string.MessageRecord_s_set_disappearing_message_time_to_s, getIndividualRecipient().getDisplayName(context), time));
    } else if (isIdentityUpdate()) {
      return new SpannableString(context.getString(R.string.MessageRecord_your_safety_number_with_s_has_changed, getIndividualRecipient().getDisplayName(context)));
    } else if (isIdentityVerified()) {
      if (isOutgoing()) return new SpannableString(context.getString(R.string.MessageRecord_you_marked_your_safety_number_with_s_verified, getIndividualRecipient().getDisplayName(context)));
      else              return new SpannableString(context.getString(R.string.MessageRecord_you_marked_your_safety_number_with_s_verified_from_another_device, getIndividualRecipient().getDisplayName(context)));
    } else if (isIdentityDefault()) {
      if (isOutgoing()) return new SpannableString(context.getString(R.string.MessageRecord_you_marked_your_safety_number_with_s_unverified, getIndividualRecipient().getDisplayName(context)));
      else              return new SpannableString(context.getString(R.string.MessageRecord_you_marked_your_safety_number_with_s_unverified_from_another_device, getIndividualRecipient().getDisplayName(context)));
    }
      if (isJSONValid(getBody())) {
          try {
              JSONObject json = new JSONObject(getBody());

              if (json.getString("messageType") != null) {
                  String messageType = json.getString("messageType");

                  if (messageType.equals("groupCall") || messageType.equals("call")) {
                      String groupMessageBody = "đã gọi đến trong group";
                      return new SpannableString(groupMessageBody);
                  } else {
                      return new SpannableString(getBody());
                  }
              } else {
                  return new SpannableString(getBody());
              }



          } catch (JSONException e) {
              return new SpannableString(getBody());
          }
      } else {
          return new SpannableString(getBody());
      }

  }

  private @NonNull String getGv2Description(@NonNull Context context) {
    if (!isGroupUpdate() || !isGroupV2()) {
      throw new AssertionError();
    }
    try {
      ShortStringDescriptionStrategy descriptionStrategy     = new ShortStringDescriptionStrategy(context);
      byte[]                         decoded                 = Base64.decode(getBody());
      DecryptedGroupV2Context        decryptedGroupV2Context = DecryptedGroupV2Context.parseFrom(decoded);
      GroupsV2UpdateMessageProducer  updateMessageProducer   = new GroupsV2UpdateMessageProducer(context, descriptionStrategy, Recipient.self().getUuid().get());

      if (decryptedGroupV2Context.hasChange() && decryptedGroupV2Context.getGroupState().getRevision() > 0) {
        DecryptedGroupChange change  = decryptedGroupV2Context.getChange();
        List<String>         strings = updateMessageProducer.describeChange(change);
        StringBuilder        result  = new StringBuilder();

        for (int i = 0; i < strings.size(); i++) {
          if (i > 0) result.append('\n');
          result.append(strings.get(i));
        }

        return result.toString();
      } else {
        return updateMessageProducer.describeNewGroup(decryptedGroupV2Context.getGroupState());
      }
    } catch (IOException e) {
      Log.w(TAG, "GV2 Message update detail could not be read", e);
      return context.getString(R.string.MessageRecord_group_updated);
    }
  }

  /**
   * Describes a UUID by it's corresponding recipient's {@link Recipient#getDisplayName(Context)}.
   */
  private static class ShortStringDescriptionStrategy implements GroupsV2UpdateMessageProducer.DescribeMemberStrategy {

    private final Context context;

   ShortStringDescriptionStrategy(@NonNull Context context) {
      this.context = context;
   }

   @Override
   public @NonNull String describe(@NonNull UUID uuid) {
     if (UuidUtil.UNKNOWN_UUID.equals(uuid)) {
       return context.getString(R.string.MessageRecord_unknown);
     }
     return Recipient.resolved(RecipientId.from(uuid, null)).getDisplayName(context);
   }
 }

  public long getId() {
    return id;
  }

  public boolean isPush() {
    return SmsDatabase.Types.isPushType(type) && !SmsDatabase.Types.isForcedSms(type);
  }

  public long getTimestamp() {
    if (isPush() && getDateSent() < getDateReceived()) {
      return getDateSent();
    }
    return getDateReceived();
  }

  public long getServerTimestamp() {
    return serverTimestamp;
  }

  public boolean isForcedSms() {
    return SmsDatabase.Types.isForcedSms(type);
  }

  public boolean isIdentityVerified() {
    return SmsDatabase.Types.isIdentityVerified(type);
  }

  public boolean isIdentityDefault() {
    return SmsDatabase.Types.isIdentityDefault(type);
  }

  public boolean isIdentityMismatchFailure() {
    return mismatches != null && !mismatches.isEmpty();
  }

  public boolean isBundleKeyExchange() {
    return SmsDatabase.Types.isBundleKeyExchange(type);
  }

  public boolean isContentBundleKeyExchange() {
    return SmsDatabase.Types.isContentBundleKeyExchange(type);
  }

  public boolean isIdentityUpdate() {
    return SmsDatabase.Types.isIdentityUpdate(type);
  }

  public boolean isCorruptedKeyExchange() {
    return SmsDatabase.Types.isCorruptedKeyExchange(type);
  }

  public boolean isInvalidVersionKeyExchange() {
    return SmsDatabase.Types.isInvalidVersionKeyExchange(type);
  }

  public boolean isUpdate() {
    return isGroupAction() || isJoined() || isExpirationTimerUpdate() || isCallLog() ||
           isEndSession()  || isIdentityUpdate() || isIdentityVerified() || isIdentityDefault();
  }

  public boolean isMediaPending() {
    return false;
  }

  public Recipient getIndividualRecipient() {
    return individualRecipient.live().get();
  }

  public int getRecipientDeviceId() {
    return recipientDeviceId;
  }

  public long getType() {
    return type;
  }

  public List<IdentityKeyMismatch> getIdentityKeyMismatches() {
    return mismatches;
  }

  public List<NetworkFailure> getNetworkFailures() {
    return networkFailures;
  }

  public boolean hasNetworkFailures() {
    return networkFailures != null && !networkFailures.isEmpty();
  }

  public boolean hasFailedWithNetworkFailures() {
    return isFailed() && ((getRecipient().isPushGroup() && hasNetworkFailures()) || !isIdentityMismatchFailure());
  }

  protected SpannableString emphasisAdded(String sequence) {
    SpannableString spannable = new SpannableString(sequence);
    spannable.setSpan(new RelativeSizeSpan(0.9f), 0, sequence.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    spannable.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), 0, sequence.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

    return spannable;
  }

  public boolean equals(Object other) {
    return other != null                              &&
           other instanceof MessageRecord             &&
           ((MessageRecord) other).getId() == getId() &&
           ((MessageRecord) other).isMms() == isMms();
  }

  public int hashCode() {
    return (int)getId();
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }

  public long getExpiresIn() {
    return expiresIn;
  }

  public long getExpireStarted() {
    return expireStarted;
  }

  public boolean isUnidentified() {
    return unidentified;
  }

  public boolean isViewOnce() {
    return false;
  }

  public boolean isRemoteDelete() {
    return remoteDelete;
  }

  public @NonNull List<ReactionRecord> getReactions() {
    return reactions;
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

    public  HashSet<MessageRecord> toHashSet() {
        int capacity =  4 / 3 + 1;
        HashSet<MessageRecord> set = new HashSet(capacity);
        Collections.addAll(set, this);
        return set;
    }
}

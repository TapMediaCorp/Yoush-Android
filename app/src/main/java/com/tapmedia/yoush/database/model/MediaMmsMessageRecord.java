/**
 * Copyright (C) 2012 Moxie Marlinspike
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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.SpannableString;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.contactshare.Contact;
import com.tapmedia.yoush.database.MmsDatabase;
import com.tapmedia.yoush.database.SmsDatabase.Status;
import com.tapmedia.yoush.database.documents.IdentityKeyMismatch;
import com.tapmedia.yoush.database.documents.NetworkFailure;
import com.tapmedia.yoush.linkpreview.LinkPreview;
import com.tapmedia.yoush.mms.SlideDeck;
import com.tapmedia.yoush.recipients.Recipient;

import java.util.List;

/**
 * Represents the message record model for MMS messages that contain
 * media (ie: they've been downloaded).
 *
 * @author Moxie Marlinspike
 *
 */

public class MediaMmsMessageRecord extends MmsMessageRecord {
  private final static String TAG = MediaMmsMessageRecord.class.getSimpleName();

  private final int     partCount;

  public MediaMmsMessageRecord(long id,
                               Recipient conversationRecipient,
                               Recipient individualRecipient,
                               int recipientDeviceId,
                               long dateSent,
                               long dateReceived,
                               long dateServer,
                               int deliveryReceiptCount,
                               long threadId,
                               String body,
                               @NonNull SlideDeck slideDeck,
                               int partCount,
                               long mailbox,
                               List<IdentityKeyMismatch> mismatches,
                               List<NetworkFailure> failures,
                               int subscriptionId,
                               long expiresIn,
                               long expireStarted,
                               boolean viewOnce,
                               int readReceiptCount,
                               @Nullable Quote quote,
                               @NonNull List<Contact> contacts,
                               @NonNull List<LinkPreview> linkPreviews,
                               boolean unidentified,
                               @NonNull List<ReactionRecord> reactions,
                               boolean remoteDelete)
  {
    super(id, body, conversationRecipient, individualRecipient, recipientDeviceId, dateSent,
          dateReceived, dateServer, threadId, Status.STATUS_NONE, deliveryReceiptCount, mailbox, mismatches, failures,
          subscriptionId, expiresIn, expireStarted, viewOnce, slideDeck,
          readReceiptCount, quote, contacts, linkPreviews, unidentified, reactions, remoteDelete);
    this.partCount = partCount;
  }

  public int getPartCount() {
    return partCount;
  }

  @Override
  public boolean isMmsNotification() {
    return false;
  }

  @Override
  public SpannableString getDisplayBody(@NonNull Context context) {
    if (MmsDatabase.Types.isFailedDecryptType(type)) {
      return emphasisAdded(context.getString(R.string.MmsMessageRecord_bad_encrypted_mms_message));
    } else if (MmsDatabase.Types.isDuplicateMessageType(type)) {
      return emphasisAdded(context.getString(R.string.SmsMessageRecord_duplicate_message));
    } else if (MmsDatabase.Types.isNoRemoteSessionType(type)) {
      return emphasisAdded(context.getString(R.string.MmsMessageRecord_mms_message_encrypted_for_non_existing_session));
    } else if (isLegacyMessage()) {
      return emphasisAdded(context.getString(R.string.MessageRecord_message_encrypted_with_a_legacy_protocol_version_that_is_no_longer_supported));
    }

    return super.getDisplayBody(context);
  }
}

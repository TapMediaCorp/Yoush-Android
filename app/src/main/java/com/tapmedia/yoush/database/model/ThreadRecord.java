/*
 * Copyright (C) 2012 Moxie Marlinspike
 * Copyright (C) 2013-2017 Open Whisper Systems
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

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tapmedia.yoush.database.MmsSmsColumns;
import com.tapmedia.yoush.database.SmsDatabase;
import com.tapmedia.yoush.database.ThreadDatabase;
import com.tapmedia.yoush.database.ThreadDatabase.Extra;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import org.whispersystems.libsignal.util.guava.Preconditions;

import java.util.Objects;

/**
 * Represents an entry in the {@link com.tapmedia.yoush.database.ThreadDatabase}.
 */
public final class ThreadRecord {

  private final long      threadId;
  private final String    body;
  private final Recipient recipient;
  private final long      type;
  private final long      date;
  private final long      deliveryStatus;
  private final int       deliveryReceiptCount;
  private final int       readReceiptCount;
  private final Uri       snippetUri;
  private final String    contentType;
  private final Extra     extra;
  private final long      count;
  private final int       unreadCount;
  private final boolean forcedUnread;
  private final int distributionType;
  private final boolean   archived;
  private final long      expiresIn;
  private final long      lastSeen;
  private final boolean isHidden;

  private ThreadRecord(@NonNull Builder builder) {
    this.threadId             = builder.threadId;
    this.body                 = builder.body;
    this.recipient            = builder.recipient;
    this.date                 = builder.date;
    this.type                 = builder.type;
    this.deliveryStatus       = builder.deliveryStatus;
    this.deliveryReceiptCount = builder.deliveryReceiptCount;
    this.readReceiptCount     = builder.readReceiptCount;
    this.snippetUri           = builder.snippetUri;
    this.contentType          = builder.contentType;
    this.extra                = builder.extra;
    this.count                = builder.count;
    this.unreadCount          = builder.unreadCount;
    this.forcedUnread         = builder.forcedUnread;
    this.distributionType     = builder.distributionType;
    this.archived             = builder.archived;
    this.expiresIn            = builder.expiresIn;
    this.lastSeen = builder.lastSeen;
    this.isHidden = builder.isHidden;
  }

  public long getThreadId() {
    return threadId;
  }

  public @NonNull Recipient getRecipient() {
    return recipient;
  }

  public @Nullable Uri getSnippetUri() {
    return snippetUri;
  }

  public @NonNull String getBody() {
    return body;
  }

  public @Nullable Extra getExtra() {
    return extra;
  }

  public @Nullable String getContentType() {
    return contentType;
  }

  public long getCount() {
    return count;
  }

  public int getUnreadCount() {
    return unreadCount;
  }

  public boolean isForcedUnread() {
    return forcedUnread;
  }

  public boolean isRead() {
    return unreadCount == 0 && !forcedUnread;
  }

  public long getDate() {
    return date;
  }

  public boolean isArchived() {
    return archived;
  }

  public long getType() {
    return type;
  }

  public int getDistributionType() {
    return distributionType;
  }

  public long getExpiresIn() {
    return expiresIn;
  }

  public long getLastSeen() {
    return lastSeen;
  }

  public boolean isOutgoing() {
    return MmsSmsColumns.Types.isOutgoingMessageType(type);
  }

  public boolean isOutgoingCall() {
    return SmsDatabase.Types.isOutgoingCall(type);
  }

  public boolean isVerificationStatusChange() {
    return StatusUtil.isVerificationStatusChange(type);
  }

  public boolean isPending() {
    return StatusUtil.isPending(type);
  }

  public boolean isFailed() {
    return StatusUtil.isFailed(type, deliveryStatus);
  }

  public boolean isRemoteRead() {
    return readReceiptCount > 0;
  }

  public boolean isPendingInsecureSmsFallback() {
    return SmsDatabase.Types.isPendingInsecureSmsFallbackType(type);
  }

  public boolean isDelivered() {
    return StatusUtil.isDelivered(deliveryStatus, deliveryReceiptCount);
  }

  public @Nullable RecipientId getGroupAddedBy() {
    if (extra != null && extra.getGroupAddedBy() != null) return RecipientId.from(extra.getGroupAddedBy());
    else                                                  return null;
  }

  public boolean isGv2Invite() {
    return extra != null && extra.isGv2Invite();
  }

  public boolean isMessageRequestAccepted() {
    if (extra != null) return extra.isMessageRequestAccepted();
    else               return true;
  }

  public boolean isHidden() {
    return isHidden;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ThreadRecord that = (ThreadRecord) o;
    return threadId == that.threadId                         &&
           type == that.type                                 &&
           date == that.date                                 &&
           deliveryStatus == that.deliveryStatus             &&
           deliveryReceiptCount == that.deliveryReceiptCount &&
           readReceiptCount == that.readReceiptCount         &&
           count == that.count                               &&
           unreadCount == that.unreadCount                   &&
           forcedUnread == that.forcedUnread                 &&
           distributionType == that.distributionType         &&
           archived == that.archived                         &&
           expiresIn == that.expiresIn                       &&
           lastSeen == that.lastSeen                         &&
           body.equals(that.body)                            &&
           recipient.equals(that.recipient)                  &&
           Objects.equals(snippetUri, that.snippetUri)       &&
           Objects.equals(contentType, that.contentType)     &&
           Objects.equals(extra, that.extra);
  }

  @Override
  public int hashCode() {
    return Objects.hash(threadId,
                        body,
                        recipient,
                        type,
                        date,
                        deliveryStatus,
                        deliveryReceiptCount,
                        readReceiptCount,
                        snippetUri,
                        contentType,
                        extra,
                        count,
                        unreadCount,
                        forcedUnread,
                        distributionType,
                        archived,
                        expiresIn,
                        lastSeen);
  }

  public static class Builder {
    private long      threadId;
    private String    body;
    private Recipient recipient;
    private long      type;
    private long      date;
    private long      deliveryStatus;
    private int       deliveryReceiptCount;
    private int       readReceiptCount;
    private Uri snippetUri;
    private String contentType;
    private Extra extra;
    private long count;
    private int unreadCount;
    private boolean forcedUnread;
    private int distributionType;
    private boolean archived;
    private long expiresIn;
    private long lastSeen;
    private boolean isHidden;

    public Builder(long threadId) {
      this.threadId = threadId;
    }

    public Builder setBody(@NonNull String body) {
      this.body = body;
      return this;
    }

    public Builder setRecipient(@NonNull Recipient recipient) {
      this.recipient = recipient;
      return this;
    }

    public Builder setType(long type) {
      this.type = type;
      return this;
    }

    public Builder setThreadId(long threadId) {
      this.threadId = threadId;
      return this;
    }

    public Builder setDate(long date) {
      this.date = date;
      return this;
    }

    public Builder setDeliveryStatus(long deliveryStatus) {
      this.deliveryStatus = deliveryStatus;
      return this;
    }

    public Builder setDeliveryReceiptCount(int deliveryReceiptCount) {
      this.deliveryReceiptCount = deliveryReceiptCount;
      return this;
    }

    public Builder setReadReceiptCount(int readReceiptCount) {
      this.readReceiptCount = readReceiptCount;
      return this;
    }

    public Builder setSnippetUri(@Nullable Uri snippetUri) {
      this.snippetUri = snippetUri;
      return this;
    }

    public Builder setContentType(@Nullable String contentType) {
      this.contentType = contentType;
      return this;
    }

    public Builder setExtra(@Nullable Extra extra) {
      this.extra = extra;
      return this;
    }

    public Builder setCount(long count) {
      this.count = count;
      return this;
    }

    public Builder setUnreadCount(int unreadCount) {
      this.unreadCount = unreadCount;
      return this;
    }

    public Builder setForcedUnread(boolean forcedUnread) {
      this.forcedUnread = forcedUnread;
      return this;
    }

    public Builder setDistributionType(int distributionType) {
      this.distributionType = distributionType;
      return this;
    }

    public Builder setArchived(boolean archived) {
      this.archived = archived;
      return this;
    }

    public Builder setExpiresIn(long expiresIn) {
      this.expiresIn = expiresIn;
      return this;
    }

    public Builder setLastSeen(long lastSeen) {
      this.lastSeen = lastSeen;
      return this;
    }

    public Builder setIsHidden(boolean isHidden) {
      this.isHidden = isHidden;
      return this;
    }

    public ThreadRecord build() {
      if (distributionType == ThreadDatabase.DistributionTypes.CONVERSATION) {
        Preconditions.checkArgument(threadId > 0);
        Preconditions.checkArgument(date > 0);
        Preconditions.checkNotNull(body);
        Preconditions.checkNotNull(recipient);
      }
      return new ThreadRecord(this);
    }
  }
}

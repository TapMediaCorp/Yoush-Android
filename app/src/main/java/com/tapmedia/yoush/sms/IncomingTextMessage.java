package com.tapmedia.yoush.sms;

import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.SmsMessage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tapmedia.yoush.groups.GroupId;
import com.tapmedia.yoush.recipients.RecipientId;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.List;

public class IncomingTextMessage implements Parcelable {

  public static final Parcelable.Creator<IncomingTextMessage> CREATOR = new Parcelable.Creator<IncomingTextMessage>() {
    @Override
    public IncomingTextMessage createFromParcel(Parcel in) {
      return new IncomingTextMessage(in);
    }

    @Override
    public IncomingTextMessage[] newArray(int size) {
      return new IncomingTextMessage[size];
    }
  };
  private static final String TAG = IncomingTextMessage.class.getSimpleName();

            private final String      message;
            private final RecipientId sender;
            private final int         senderDeviceId;
            private final int         protocol;
            private final String      serviceCenterAddress;
            private final boolean     replyPathPresent;
            private final String      pseudoSubject;
            private final long        sentTimestampMillis;
            private final long        serverTimestampMillis;
  @Nullable private final GroupId     groupId;
            private final boolean     push;
            private final int         subscriptionId;
            private final long        expiresInMillis;
            private final boolean     unidentified;

  public IncomingTextMessage(@NonNull RecipientId sender, @NonNull SmsMessage message, int subscriptionId) {
    this.message               = message.getDisplayMessageBody();
    this.sender                = sender;
    this.senderDeviceId        = SignalServiceAddress.DEFAULT_DEVICE_ID;
    this.protocol              = message.getProtocolIdentifier();
    this.serviceCenterAddress  = message.getServiceCenterAddress();
    this.replyPathPresent      = message.isReplyPathPresent();
    this.pseudoSubject         = message.getPseudoSubject();
    this.sentTimestampMillis   = message.getTimestampMillis();
    this.serverTimestampMillis = -1;
    this.subscriptionId        = subscriptionId;
    this.expiresInMillis       = 0;
    this.groupId               = null;
    this.push                  = false;
    this.unidentified          = false;
  }

  public IncomingTextMessage(@NonNull RecipientId sender,
                             int senderDeviceId,
                             long sentTimestampMillis,
                             long serverTimestampMillis,
                             String encodedBody,
                             Optional<GroupId> groupId,
                             long expiresInMillis,
                             boolean unidentified)
  {
    this.message               = encodedBody;
    this.sender                = sender;
    this.senderDeviceId        = senderDeviceId;
    this.protocol              = 31337;
    this.serviceCenterAddress  = "GCM";
    this.replyPathPresent      = true;
    this.pseudoSubject         = "";
    this.sentTimestampMillis   = sentTimestampMillis;
    this.serverTimestampMillis = serverTimestampMillis;
    this.push                  = true;
    this.subscriptionId        =    -1;
    this.expiresInMillis       = expiresInMillis;
    this.unidentified          = unidentified;
    this.groupId               = groupId.orNull();
  }

  public IncomingTextMessage(Parcel in) {
    this.message               = in.readString();
    this.sender                = in.readParcelable(IncomingTextMessage.class.getClassLoader());
    this.senderDeviceId        = in.readInt();
    this.protocol              = in.readInt();
    this.serviceCenterAddress  = in.readString();
    this.replyPathPresent      = (in.readInt() == 1);
    this.pseudoSubject         = in.readString();
    this.sentTimestampMillis   = in.readLong();
    this.serverTimestampMillis = in.readLong();
    this.groupId               = GroupId.parseNullableOrThrow(in.readString());
    this.push                  = (in.readInt() == 1);
    this.subscriptionId        = in.readInt();
    this.expiresInMillis       = in.readLong();
    this.unidentified          = in.readInt() == 1;
  }

  public IncomingTextMessage(IncomingTextMessage base, String newBody) {
    this.message               = newBody;
    this.sender                = base.getSender();
    this.senderDeviceId        = base.getSenderDeviceId();
    this.protocol              = base.getProtocol();
    this.serviceCenterAddress  = base.getServiceCenterAddress();
    this.replyPathPresent      = base.isReplyPathPresent();
    this.pseudoSubject         = base.getPseudoSubject();
    this.sentTimestampMillis   = base.getSentTimestampMillis();
    this.serverTimestampMillis = base.getServerTimestampMillis();
    this.groupId               = base.getGroupId();
    this.push                  = base.isPush();
    this.subscriptionId        = base.getSubscriptionId();
    this.expiresInMillis       = base.getExpiresIn();
    this.unidentified          = base.isUnidentified();
  }

  public IncomingTextMessage(List<IncomingTextMessage> fragments) {
    StringBuilder body = new StringBuilder();

    for (IncomingTextMessage message : fragments) {
      body.append(message.getMessageBody());
    }

    this.message               = body.toString();
    this.sender                = fragments.get(0).getSender();
    this.senderDeviceId        = fragments.get(0).getSenderDeviceId();
    this.protocol              = fragments.get(0).getProtocol();
    this.serviceCenterAddress  = fragments.get(0).getServiceCenterAddress();
    this.replyPathPresent      = fragments.get(0).isReplyPathPresent();
    this.pseudoSubject         = fragments.get(0).getPseudoSubject();
    this.sentTimestampMillis   = fragments.get(0).getSentTimestampMillis();
    this.serverTimestampMillis = fragments.get(0).getServerTimestampMillis();
    this.groupId               = fragments.get(0).getGroupId();
    this.push                  = fragments.get(0).isPush();
    this.subscriptionId        = fragments.get(0).getSubscriptionId();
    this.expiresInMillis       = fragments.get(0).getExpiresIn();
    this.unidentified          = fragments.get(0).isUnidentified();
  }

  protected IncomingTextMessage(@NonNull RecipientId sender, @Nullable GroupId groupId)
  {
    this.message               = "";
    this.sender                = sender;
    this.senderDeviceId        = SignalServiceAddress.DEFAULT_DEVICE_ID;
    this.protocol              = 31338;
    this.serviceCenterAddress  = "Outgoing";
    this.replyPathPresent      = true;
    this.pseudoSubject         = "";
    this.sentTimestampMillis   = System.currentTimeMillis();
    this.serverTimestampMillis = sentTimestampMillis;
    this.groupId               = groupId;
    this.push                  = true;
    this.subscriptionId        = -1;
    this.expiresInMillis       = 0;
    this.unidentified          = false;
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }

  public long getExpiresIn() {
    return expiresInMillis;
  }

  public long getSentTimestampMillis() {
    return sentTimestampMillis;
  }

  public long getServerTimestampMillis() {
    return serverTimestampMillis;
  }

  public String getPseudoSubject() {
    return pseudoSubject;
  }

  public String getMessageBody() {
    return message;
  }

  public RecipientId getSender() {
    return sender;
  }

  public int getSenderDeviceId() {
    return senderDeviceId;
  }

  public int getProtocol() {
    return protocol;
  }

  public String getServiceCenterAddress() {
    return serviceCenterAddress;
  }

  public boolean isReplyPathPresent() {
    return replyPathPresent;
  }

  public boolean isSecureMessage() {
    return false;
  }

  public boolean isPreKeyBundle() {
    return isLegacyPreKeyBundle() || isContentPreKeyBundle();
  }

  public boolean isLegacyPreKeyBundle() {
    return false;
  }

  public boolean isContentPreKeyBundle() {
    return false;
  }

  public boolean isEndSession() {
    return false;
  }

  public boolean isPush() {
    return push;
  }

  public @Nullable GroupId getGroupId() {
    return groupId;
  }

  public boolean isGroup() {
    return false;
  }

  public boolean isJoined() {
    return false;
  }

  public boolean isIdentityUpdate() {
    return false;
  }

  public boolean isIdentityVerified() {
    return false;
  }

  public boolean isIdentityDefault() {
    return false;
  }

  public boolean isUnidentified() {
    return unidentified;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel out, int flags) {
    out.writeString(message);
    out.writeParcelable(sender, flags);
    out.writeInt(senderDeviceId);
    out.writeInt(protocol);
    out.writeString(serviceCenterAddress);
    out.writeInt(replyPathPresent ? 1 : 0);
    out.writeString(pseudoSubject);
    out.writeLong(sentTimestampMillis);
    out.writeString(groupId == null ? null : groupId.toString());
    out.writeInt(push ? 1 : 0);
    out.writeInt(subscriptionId);
    out.writeLong(expiresInMillis);
    out.writeInt(unidentified ? 1 : 0);
  }
}

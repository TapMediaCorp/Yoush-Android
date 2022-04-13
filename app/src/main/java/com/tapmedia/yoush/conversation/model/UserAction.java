package com.tapmedia.yoush.conversation.model;

import com.google.gson.annotations.SerializedName;

import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;

import java.util.UUID;

public class UserAction {

    @SerializedName("uuid")
    private String uuid;

    @SerializedName("phoneNumber")
    private String phoneNumber;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public static Recipient recipient(UserAction userAction) {
        if (null == userAction) return null;
        return recipient(userAction.getUuid(), userAction.getPhoneNumber());
    }

    private static Recipient recipient(String uuid, String phoneNumber) {
        RecipientId recipientId = RecipientId.from(UUID.fromString(uuid), phoneNumber);
        Recipient recipient = Recipient.resolved(recipientId);
        return recipient;
    }


}
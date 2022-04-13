package com.tapmedia.yoush.conversation.model;

import androidx.annotation.Nullable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import com.tapmedia.yoush.database.model.MessageRecord;
import com.tapmedia.yoush.recipients.Recipient;

import java.util.Comparator;
import java.util.Objects;

public class PinWrapper {

    @SerializedName("pinMessage_action")
    private String action;

    @SerializedName("pinMessage_messageTimestamp")
    private Long timestamp;

    @SerializedName("pinMessage_userAction")
    private UserAction userAction;

    @SerializedName("pinMessage_groupId")
    private String groupId;

    @SerializedName("pinMessage_author")
    private UserAction author;

    @SerializedName("messageType")
    private String messageType;

    @SerializedName("pinMessage_sequence")
    private Long sequence;

    @SerializedName("pinMessage_messageId")
    private String messageId;

    @Nullable
    @Expose
    public Recipient authorRecipient;

    @Nullable
    @Expose
    public Recipient actionRecipient;

    @Nullable
    @Expose
    public MessageRecord refRecord;

    @Nullable
    @Expose
    public MessageRecord record;

    public static Comparator<PinWrapper> getSequenceComparator() {
        return (o1, o2) -> {
            if (o1.getSequence() > o2.getSequence()) return -1;
            if (o1.getSequence() < o2.getSequence()) return 1;
            return 0;
        };
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public UserAction getUserAction() {
        return userAction;
    }

    public void setUserAction(UserAction userAction) {
        this.userAction = userAction;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public UserAction getAuthor() {
        return author;
    }

    public void setAuthor(UserAction author) {
        this.author = author;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public Long getSequence() {
        return sequence;
    }

    public void setSequence(Long sequence) {
        this.sequence = sequence;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PinWrapper wrapper = (PinWrapper) o;
        return timestamp.equals(wrapper.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp);
    }


}

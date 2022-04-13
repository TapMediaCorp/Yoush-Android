package com.tapmedia.yoush.conversation.model;


import androidx.annotation.Nullable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import com.tapmedia.yoush.database.model.MessageRecord;
import com.tapmedia.yoush.database.model.MmsMessageRecord;
import com.tapmedia.yoush.recipients.Recipient;

public class BackgroundWrapper {

    @SerializedName("action")
    private String action;

    @SerializedName("imageUrl")
    private String imageUrl;

    @SerializedName("userAction")
    private UserAction userAction;

    @SerializedName("messageType")
    private String messageType;

    @Nullable
    @Expose
    @SerializedName("silent")
    private Boolean silent;

    @Expose
    public long threadId;

    @Nullable
    @Expose
    public MessageRecord record;

    @Nullable
    @Expose
    public Recipient actionRecipient;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public UserAction getUserAction() {
        return userAction;
    }

    public void setUserAction(UserAction userAction) {
        this.userAction = userAction;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    @Nullable
    public Boolean getSilent() {
        return silent;
    }

    public void setSilent(@Nullable Boolean silent) {
        this.silent = silent;
    }
}

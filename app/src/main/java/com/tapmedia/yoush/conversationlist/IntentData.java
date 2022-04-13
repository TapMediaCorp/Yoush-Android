package com.tapmedia.yoush.conversationlist;

import androidx.annotation.NonNull;

import com.tapmedia.yoush.recipients.RecipientId;

import java.io.Serializable;

public class IntentData {

    private int startingPosition;
    private RecipientId recipientId;
    private long threadId;
    private int distributionType;
    private String key;

    public IntentData() {
    }

    public IntentData(RecipientId recipientId, long threadId, int distributionType, int startingPosition) {
        this.recipientId = recipientId;
        this.threadId = threadId;
        this.distributionType = distributionType;
        this.startingPosition = startingPosition;
    }

    public RecipientId getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(RecipientId recipientId) {
        this.recipientId = recipientId;
    }

    public long getThreadId() {
        return threadId;
    }

    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    public int getDistributionType() {
        return distributionType;
    }

    public void setDistributionType(int distributionType) {
        this.distributionType = distributionType;
    }

    public int getStartingPosition() {
        return startingPosition;
    }

    public void setStartingPosition(int startingPosition) {
        this.startingPosition = startingPosition;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    ////    public class Place implements Serializable{
//        private int id;
//        private String name;
//
//        public void setId(int id) {
//            this.id = id;
//        }
//        public int getId() {
//            return id;
//        }
//        public String getName() {
//            return name;
//        }
//
//        public void setName(String name) {
//            this.name = name;
//        }
////    }
}

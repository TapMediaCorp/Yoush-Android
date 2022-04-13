package com.tapmedia.yoush.conversationlist.adapter;

import com.tapmedia.yoush.conversationlist.model.MessageResult;
import com.tapmedia.yoush.database.model.ThreadRecord;
import com.tapmedia.yoush.recipients.Recipient;

public interface ConversationListItemEventListener {

    void onItemEventMaskAsRead(ThreadRecord record, int position);

    void onItemEventMaskAsUnRead(ThreadRecord record, int position);

    void onItemEventArchive(ThreadRecord record, int position);

    void onItemEventUnArchive(ThreadRecord record, int position);

    void onItemEventHide(ThreadRecord record, int position);

    void onItemEventUnHide(ThreadRecord record, int position);

    void onItemEventDelete(ThreadRecord record, int position);

    void onItemEventClick(ThreadRecord record, int position);

    void onContactClicked(Recipient recipient);

    void onMessageClicked(MessageResult message);

    void onItemEventShowArchiveList();

    boolean onItemEventLongClick(ThreadRecord conversation, int position);

}


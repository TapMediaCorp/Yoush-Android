package com.tapmedia.yoush.conversationlist.model;

import androidx.annotation.NonNull;

import com.tapmedia.yoush.database.model.ThreadRecord;
import com.tapmedia.yoush.recipients.Recipient;

import java.util.Collections;
import java.util.List;

/**
 * Represents an all-encompassing search result that can contain various result for different
 * subcategories.
 */
public class SearchResult {

  public static final SearchResult EMPTY = new SearchResult("", Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

  private final String              query;
  private final List<Recipient>     contacts;
  private final List<ThreadRecord>  conversations;
  private final List<ThreadRecord>  hiddenConversations;
  private final List<MessageResult> messages;

  public SearchResult(@NonNull String query,
                      @NonNull List<Recipient> contacts,
                      @NonNull List<ThreadRecord> conversations,
                      @NonNull List<ThreadRecord> hiddenConversations,
                      @NonNull List<MessageResult> messages)
  {
    this.query                = query;
    this.contacts             = contacts;
    this.conversations        = conversations;
    this.hiddenConversations  = hiddenConversations;
    this.messages             = messages;
  }

  public List<Recipient> getContacts() {
    return contacts;
  }

  public List<ThreadRecord> getConversations() {
    return conversations;
  }

  public List<ThreadRecord> getHiddenConversations() {
    return hiddenConversations;
  }

  public List<MessageResult> getMessages() {
    return messages;
  }

  public String getQuery() {
    return query;
  }

  public int size() {
    return contacts.size() + conversations.size() + messages.size() + hiddenConversations.size();
  }

  public boolean isEmpty() {
    return size() == 0;
  }
}

package com.tapmedia.yoush.conversationlist.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.conversationlist.ConversationListItem;
import com.tapmedia.yoush.conversationlist.action.ActionBindJob;
import com.tapmedia.yoush.conversationlist.model.MessageResult;
import com.tapmedia.yoush.conversationlist.model.SearchResult;
import com.tapmedia.yoush.database.model.ThreadRecord;
import com.tapmedia.yoush.mms.GlideRequests;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.util.StickyHeaderDecoration;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ConversationListSearchAdapter
        extends RecyclerView.Adapter<ConversationListSearchAdapter.ItemVH> implements
        StickyHeaderDecoration.StickyHeaderAdapter<ConversationListSearchAdapter.HeaderViewHolder> {

  private static final int TYPE_CONVERSATIONS = 1;
  private static final int TYPE_CONTACTS = 2;
  private static final int TYPE_MESSAGES = 3;
  private static final int TYPE_HIDDEN_CONVERSATIONS = 4;

  private final GlideRequests glideRequests;
  private final ConversationListItemEventListener itemEventListener;
  private final Locale locale = Locale.getDefault();


  @NonNull
  private SearchResult searchResult = SearchResult.EMPTY;

  public ConversationListSearchAdapter(@NonNull GlideRequests glideRequests,
                                       @NonNull ConversationListItemEventListener listener) {
    this.glideRequests = glideRequests;
    this.itemEventListener = listener;
  }

  @Override
  public int getItemCount() {
    return searchResult.size();
  }

  @Override
  public int getItemViewType(int position) {
    if (getHiddenConversationResult(position) != null) {
      return R.layout.conversation_list_item_hidden;
    }
    ThreadRecord record = getConversationResult(position);
    if (record != null && record.isHidden()) {
      return R.layout.conversation_list_item_hidden;
    }
    return R.layout.conversation_list_item_content;
  }

  @Override
  public ConversationListSearchAdapter.ItemVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View v = LayoutInflater.from(parent.getContext())
            .inflate(viewType, parent, false);
    return new ConversationListSearchAdapter.ItemVH(v);
  }

  @Override
  public void onBindViewHolder(@NonNull ConversationListSearchAdapter.ItemVH holder, int position) {
    ThreadRecord hiddenRecord = getHiddenConversationResult(position);
    if (hiddenRecord != null) {
      holder.conversationListItem.bind(hiddenRecord,
              glideRequests,
              locale,
              Collections.emptySet(),
              Collections.emptySet(),
              false,
              searchResult.getQuery());
      ActionBindJob.bindHiddenConversationListItemGesture(this,
              holder.itemView,
              hiddenRecord,
              position,
              itemEventListener);
      return;
    }

    ThreadRecord record = getConversationResult(position);
    if (record != null) {
      holder.conversationListItem.bind(record,
              glideRequests,
              locale,
              Collections.emptySet(),
              Collections.emptySet(),
              false,
              searchResult.getQuery());
      if (record.isHidden()) ActionBindJob.bindHiddenConversationListItemGesture(this,
              holder.itemView,
              record,
              position,
              itemEventListener);
      else ActionBindJob.bindDefaultItemGestureNoGesture(this,
              holder.itemView,
              record,
              position,
              itemEventListener);
      return;
    }


    Recipient contactRecipient = getContactResult(position);
    if (contactRecipient != null) {
      holder.bind(contactRecipient, position, glideRequests, itemEventListener, locale, searchResult.getQuery());
      return;
    }

    MessageResult messageResult = getMessageResult(position);
    if (messageResult != null) {
      holder.bind(messageResult, position, glideRequests, itemEventListener, locale, searchResult.getQuery());
    }
  }

  @Override
  public void onViewRecycled(@NonNull ConversationListSearchAdapter.ItemVH holder) {
    holder.recycle();
  }



  @Override
  public long getHeaderId(int position) {
    if (getConversationResult(position) != null) {
      return TYPE_CONVERSATIONS;
    } else if (getContactResult(position) != null) {
      return TYPE_CONTACTS;
    } else if (getHiddenConversationResult(position) != null) {
      return TYPE_HIDDEN_CONVERSATIONS;
    } else {
      return TYPE_MESSAGES;
    }
  }

  @Override
  public ConversationListSearchAdapter.HeaderViewHolder onCreateHeaderViewHolder(ViewGroup parent, int position) {
    return new ConversationListSearchAdapter.HeaderViewHolder(LayoutInflater.from(parent.getContext())
            .inflate(R.layout.search_result_list_divider, parent, false));
  }

  @Override
  public void onBindHeaderViewHolder(ConversationListSearchAdapter.HeaderViewHolder viewHolder, int position) {
    viewHolder.bind((int) getHeaderId(position));
  }

  public void updateResults(@NonNull SearchResult result) {
    this.searchResult = result;
    notifyDataSetChanged();
  }

  @Nullable
  private ThreadRecord getConversationResult(int position) {
    List<ThreadRecord> list = searchResult.getConversations();
    int size = list.size();
    if (size != 0 && position < size) {
      return list.get(position);
    }
    return null;
  }

  @Nullable
  private ThreadRecord getHiddenConversationResult(int position) {
    if (position >= getFirstHideConversationIndex() && position < getFirstContactIndex()) {
      return searchResult.getHiddenConversations().get(position - getFirstHideConversationIndex());
    }
    return null;
  }

  @Nullable
  private Recipient getContactResult(int position) {
    if (position >= getFirstContactIndex() && position < getFirstMessageIndex()) {
      return searchResult.getContacts().get(position - getFirstContactIndex());
    }
    return null;
  }

  @Nullable
  private MessageResult getMessageResult(int position) {
    if (position >= getFirstMessageIndex() && position < searchResult.size()) {
      return searchResult.getMessages().get(position - getFirstMessageIndex());
    }
    return null;
  }

  private int getFirstHideConversationIndex() {
    return searchResult.getConversations().size();
  }

  private int getFirstContactIndex() {
    return getFirstHideConversationIndex() + searchResult.getHiddenConversations().size();
  }

  private int getFirstMessageIndex() {
    return getFirstContactIndex() + searchResult.getContacts().size();
  }


  static class ItemVH extends RecyclerView.ViewHolder {

    public ConversationListItem conversationListItem;

    public MotionLayout layoutMotion;

    ItemVH(View itemView) {
      super(itemView);
      layoutMotion = itemView.findViewById(R.id.layoutMotion);
      conversationListItem = itemView.findViewById(R.id.layoutItem);
    }

    void bind(Recipient contactResult,
              int position,
              GlideRequests glideRequests,
              ConversationListItemEventListener listener,
              Locale locale,
              @Nullable String query) {
      conversationListItem.bind(contactResult, glideRequests, locale, query);
      conversationListItem.setOnClickListener(view -> listener.onContactClicked(contactResult));
    }

    void bind(MessageResult messageResult,
              int position,
              GlideRequests glideRequests,
              ConversationListItemEventListener listener,
              Locale locale,
              @Nullable String query) {
      conversationListItem.bind(messageResult, glideRequests, locale, query);
      conversationListItem.setOnClickListener(view -> listener.onMessageClicked(messageResult));
    }

    void recycle() {
      conversationListItem.unbind();
      conversationListItem.setOnClickListener(null);
    }
  }

  public static class HeaderViewHolder extends RecyclerView.ViewHolder {

    private TextView titleView;

    public HeaderViewHolder(View itemView) {
      super(itemView);
      titleView = itemView.findViewById(R.id.label);
    }

    public void bind(int headerType) {
      switch (headerType) {
        case TYPE_CONVERSATIONS:
          titleView.setText(R.string.SearchFragment_header_conversations);
          break;
        case TYPE_CONTACTS:
          titleView.setText(R.string.SearchFragment_header_contacts);
          break;
        case TYPE_MESSAGES:
          titleView.setText(R.string.SearchFragment_header_messages);
          break;
        case TYPE_HIDDEN_CONVERSATIONS:
          titleView.setText(R.string.SearchFragment_header_hidden_conversations);
          break;
      }
    }
  }
}

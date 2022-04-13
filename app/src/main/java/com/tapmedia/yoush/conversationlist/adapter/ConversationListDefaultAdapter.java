package com.tapmedia.yoush.conversationlist.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.tapmedia.yoush.BindableConversationListItem;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.conversationlist.ConversationListItemAction;
import com.tapmedia.yoush.conversationlist.action.ActionBindJob;
import com.tapmedia.yoush.conversationlist.model.Conversation;
import com.tapmedia.yoush.database.model.ThreadRecord;
import com.tapmedia.yoush.mms.GlideRequests;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.util.CachedInflater;
import com.tapmedia.yoush.util.ViewUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ConversationListDefaultAdapter extends PagedListAdapter<Conversation, RecyclerView.ViewHolder> {

    private static final int TYPE_THREAD = R.layout.conversation_list_item_default;
    private static final int TYPE_ACTION = R.layout.conversation_list_item_action;
    private static final int TYPE_PLACEHOLDER = 0;
    private final GlideRequests glideRequests;
    private final Map<Long, Conversation> batchSet = Collections.synchronizedMap(new HashMap<>());
    private final Set<Long> typingSet = new HashSet<>();
    public ConversationListItemEventListener itemEventListener;
    private boolean batchMode = false;
    private int archived;
    public ConversationListDefaultAdapter(@NonNull GlideRequests glideRequests, @NonNull ConversationListItemEventListener listener) {
        super(new DiffCallback());
        this.glideRequests = glideRequests;
        this.itemEventListener = listener;
    }

    @Override
    public int getItemCount() {
        return (archived > 0 ? 1 : 0) + super.getItemCount();
    }

    @Override
    public int getItemViewType(int position) {
        if (archived > 0 && position == getItemCount() - 1) {
            return TYPE_ACTION;
        }
        Conversation conversation = getItem(position);
        if (conversation == null) {
            return TYPE_PLACEHOLDER;
        }

        return TYPE_THREAD;

    }

    @Override
    public @NonNull
    RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        if (viewType == TYPE_ACTION) {
            ItemVH holder = new ItemVH(LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false));
            holder.itemView.setOnClickListener(v -> {
                int position = holder.getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    itemEventListener.onItemEventShowArchiveList();
                }
            });
            return holder;
    }

    if (viewType == TYPE_THREAD) {
        ItemVH holder = new ItemVH(CachedInflater.from(parent.getContext()).inflate(viewType, parent, false));
      return holder;
    }

    if (viewType == TYPE_PLACEHOLDER) {
      View v = new FrameLayout(parent.getContext());
      v.setLayoutParams(new FrameLayout.LayoutParams(1, ViewUtil.dpToPx(100)));
      return new PlaceholderVH(v);
    }

    throw new IllegalStateException("Unknown type! " + viewType);
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {

      if (holder.getItemViewType() == TYPE_ACTION) {
          ItemVH casted = (ItemVH) holder;
          ThreadRecord record = new ThreadRecord.Builder(100)
                  .setBody("")
                  .setDate(100)
                  .setRecipient(Recipient.UNKNOWN)
                  .setCount(archived)
                  .build();
          ConversationListItemAction vh = (ConversationListItemAction) casted.itemView;
          vh.bind(record, glideRequests, Locale.getDefault(), typingSet, getBatchSelectionIds(), batchMode);
          return;
      }

      if (holder.getItemViewType() == TYPE_THREAD) {
          ItemVH casted = (ItemVH) holder;
          Conversation conversation = Objects.requireNonNull(getItem(position));
          casted.conversationListItem.bind(
                  conversation.getThreadRecord(),
                  glideRequests,
                  Locale.getDefault(),
                  typingSet,
                  getBatchSelectionIds(),
                  batchMode
          );
          ActionBindJob.bindDefaultItemGesture(this,
                  casted.itemView,
                  conversation.getThreadRecord(),
                  position,
                  itemEventListener);
      }
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
    if (payloads.isEmpty()) {
      onBindViewHolder(holder, position);
    } else {
      for (Object payloadObject : payloads) {
        if (payloadObject instanceof Payload) {
          Payload payload = (Payload) payloadObject;
          ItemVH vh = (ItemVH) holder;
          if (payload == Payload.SELECTION) {
            vh.conversationListItem.setBatchMode(batchMode);
          } else {
            vh.conversationListItem.updateTypingIndicator(typingSet);
          }
        }
      }
    }
  }

    public void setTypingThreads(@NonNull Set<Long> typingThreadSet) {
        this.typingSet.clear();
        this.typingSet.addAll(typingThreadSet);

        notifyItemRangeChanged(0, getItemCount(), Payload.TYPING_INDICATOR);
    }

  @Override
  public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
    if (holder instanceof ItemVH) {
      ItemVH vh = (ItemVH) holder;
      vh.conversationListItem.unbind();
    }
  }

    public void updateArchived(int archived) {
        int oldArchived = this.archived;

        this.archived = archived;

        if (oldArchived != archived) {
            if (archived == 0) {
                notifyItemRemoved(getItemCount());
            } else if (oldArchived == 0) {
                notifyItemInserted(getItemCount() - 1);
            } else {
        notifyItemChanged(getItemCount() - 1);
      }
    }
  }

    public void selectAllThreads() {
        for (int i = 0; i < super.getItemCount(); i++) {
            Conversation conversation = getItem(i);
            if (conversation != null && conversation.getThreadRecord().getThreadId() != -1) {
                batchSet.put(conversation.getThreadRecord().getThreadId(), conversation);
            }
        }

        notifyItemRangeChanged(0, getItemCount(), Payload.SELECTION);
    }

  @NonNull Set<Long> getBatchSelectionIds() {
    return batchSet.keySet();
  }

    public void initializeBatchMode(boolean toggle) {
        this.batchMode = toggle;
        unSelectAllThreads();
    }

    public void toggleConversationInBatchSet(@NonNull Conversation conversation) {
        if (batchSet.containsKey(conversation.getThreadRecord().getThreadId())) {
            batchSet.remove(conversation.getThreadRecord().getThreadId());
        } else if (conversation.getThreadRecord().getThreadId() != -1) {
            batchSet.put(conversation.getThreadRecord().getThreadId(), conversation);
        }

        notifyItemRangeChanged(0, getItemCount(), Payload.SELECTION);
    }

    public void toggleConversationInBatchSetByThread(@NonNull ThreadRecord threadRecord) {
        if (batchSet.containsKey(threadRecord.getThreadId())) {
            batchSet.remove(threadRecord.getThreadId());
        } else if (threadRecord.getThreadId() != -1) {
            batchSet.put(threadRecord.getThreadId(), new Conversation(threadRecord));
        }

        notifyItemRangeChanged(0, getItemCount(), Payload.SELECTION);
    }

    public Collection<Conversation> getBatchSelection() {
        return batchSet.values();
    }

    public void unSelectAllThreads() {
        batchSet.clear();
        notifyItemRangeChanged(0, getItemCount(), Payload.SELECTION);
    }

    private enum Payload {
        TYPING_INDICATOR,
        SELECTION
    }

    private static final class ItemVH extends RecyclerView.ViewHolder {

        public BindableConversationListItem conversationListItem;

        public MotionLayout layoutMotion;

        ItemVH(View itemView) {
            super(itemView);
            layoutMotion = itemView.findViewById(R.id.layoutMotion);
            conversationListItem = itemView.findViewById(R.id.layoutItem);
        }

  }

  private static final class DiffCallback extends DiffUtil.ItemCallback<Conversation> {

    @Override
    public boolean areItemsTheSame(@NonNull Conversation oldItem, @NonNull Conversation newItem) {
      return oldItem.getThreadRecord().getThreadId() == newItem.getThreadRecord().getThreadId();
    }

    @Override
    public boolean areContentsTheSame(@NonNull Conversation oldItem, @NonNull Conversation newItem) {
      return oldItem.equals(newItem);
    }
  }

  private static class PlaceholderVH extends RecyclerView.ViewHolder {
    PlaceholderVH(@NonNull View itemView) {
      super(itemView);
    }
  }

}

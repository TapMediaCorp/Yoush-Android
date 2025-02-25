package com.tapmedia.yoush.conversation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.conversation.ConversationAdapter.StickyHeaderViewHolder;
import com.tapmedia.yoush.util.StickyHeaderDecoration;

class LastSeenHeader extends StickyHeaderDecoration {

  private final ConversationAdapter adapter;
  private final long                 lastSeenTimestamp;

  LastSeenHeader(ConversationAdapter adapter, long lastSeenTimestamp) {
    super(adapter, false, false);
    this.adapter           = adapter;
    this.lastSeenTimestamp = lastSeenTimestamp;
  }

  @Override
  protected boolean hasHeader(RecyclerView parent, StickyHeaderAdapter stickyAdapter, int position) {
    if (lastSeenTimestamp <= 0) {
      return false;
    }

    long currentRecordTimestamp  = adapter.getReceivedTimestamp(position);
    long previousRecordTimestamp = adapter.getReceivedTimestamp(position + 1);

    return currentRecordTimestamp > lastSeenTimestamp && previousRecordTimestamp < lastSeenTimestamp;
  }

  @Override
  protected int getHeaderTop(RecyclerView parent, View child, View header, int adapterPos, int layoutPos) {
    return parent.getLayoutManager().getDecoratedTop(child);
  }

  @Override
  protected @NonNull RecyclerView.ViewHolder getHeader(RecyclerView parent, StickyHeaderAdapter stickyAdapter, int position) {
    StickyHeaderViewHolder viewHolder = new StickyHeaderViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.conversation_item_last_seen, parent, false));
    adapter.onBindLastSeenViewHolder(viewHolder, position);

    int widthSpec  = View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.EXACTLY);
    int heightSpec = View.MeasureSpec.makeMeasureSpec(parent.getHeight(), View.MeasureSpec.UNSPECIFIED);

    int childWidth  = ViewGroup.getChildMeasureSpec(widthSpec, parent.getPaddingLeft() + parent.getPaddingRight(), viewHolder.itemView.getLayoutParams().width);
    int childHeight = ViewGroup.getChildMeasureSpec(heightSpec, parent.getPaddingTop() + parent.getPaddingBottom(), viewHolder.itemView.getLayoutParams().height);

    viewHolder.itemView.measure(childWidth, childHeight);
    viewHolder.itemView.layout(0, 0, viewHolder.itemView.getMeasuredWidth(), viewHolder.itemView.getMeasuredHeight());

    return viewHolder;
  }
}

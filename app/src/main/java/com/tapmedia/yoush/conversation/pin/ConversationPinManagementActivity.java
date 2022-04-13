package com.tapmedia.yoush.conversation.pin;

import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.core.view.MotionEventCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.conversation.job.MessageJob;
import com.tapmedia.yoush.conversation.model.PinWrapper;
import com.tapmedia.yoush.ui.base.BaseActivity;
import com.tapmedia.yoush.ui.base.BaseRecyclerAdapter;
import com.tapmedia.yoush.ui.base.BaseViewHolder;
import com.tapmedia.yoush.ui.base.ViewClickListener;

import java.util.Collections;
import java.util.List;

public class ConversationPinManagementActivity extends BaseActivity {

    private RecyclerView recyclerView;

    private TextView conversationPinViewClose;

    private TextView conversationPinViewSave;

    private ConversationPinAdapter adapter = new ConversationPinAdapter();

    @Override
    public int layoutResource() {
        return R.layout.conversation_activity_pin_mng;
    }

    @Override
    public void onFindView() {
        conversationPinViewSave = findViewById(R.id.viewSave);
        conversationPinViewClose = findViewById(R.id.viewClose);
        recyclerView = findViewById(R.id.conversationPinRecyclerView);
    }

    @Override
    public void onViewCreated() {

        addViewClicks(conversationPinViewSave, conversationPinViewClose);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ConversationPinItemTouchHelper());
        itemTouchHelper.attachToRecyclerView(recyclerView);
        adapter.listChangedListener = new OnListChangedListener() {
            @Override
            public void onListChanged(List<PinWrapper> list) {
                conversationPinViewSave.setEnabled(true);
            }
        };
        adapter.dragStartListener = viewHolder -> itemTouchHelper.startDrag(viewHolder);



    }

    @Override
    public void onLiveDataObservers() {
        PinData.recordLiveData.observe(this, list -> {
            if (null != list) {
                adapter.setListItem(list);
            }
        });
    }

    @Override
    protected void onViewClick(View v) {
        switch (v.getId()) {
            case R.id.viewSave:
                MessageJob.onSmsPermissionGranted(this, () -> {
                    PinJobSend.reorder(adapter.getListItem());
                    finish();
                });
                break;
            case R.id.viewClose:
                finish();
                break;
        }
    }

    public interface OnStartDragListener {
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }

    public interface OnListChangedListener {
        void onListChanged(List<PinWrapper> list);
    }

    private class ConversationPinAdapter extends BaseRecyclerAdapter<PinWrapper> {

        public OnStartDragListener dragStartListener;

        public OnListChangedListener listChangedListener;

        @Override
        protected int getLayoutResource() {
            return R.layout.conversation_activity_pin_item_editable;
        }

        @Override
        protected BaseViewHolder getViewHolder(View v) {
            return new BaseViewHolder(v);
        }

        @Override
        public void onBindHolder(BaseViewHolder holder, PinWrapper item, int position) {

            ConversationPinItemView pinItemView = holder.findViewById(R.id.pinItemView);
            pinItemView.bindItem(item);
            pinItemView.visibleSeparator(View.VISIBLE);

            holder.view(R.id.conversationImageDelete).setOnClickListener(new ViewClickListener() {
                @Override
                public void onClicks(View v) {
                    MessageJob.onSmsPermissionGranted(ConversationPinManagementActivity.this, () -> {
                        PinJobSend.alertUnpin(ConversationPinManagementActivity.this, item.refRecord);
                    });

                }
            });
            holder.view(R.id.conversationImageDrag1)
                    .setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_DOWN) {
                                dragStartListener.onStartDrag(holder);
                            }
                            return false;
                        }
                    });
        }

        public void onItemMove(int fromPosition, int toPosition) {
            Collections.swap(getListItem(), fromPosition, toPosition);
            listChangedListener.onListChanged(getListItem());
            notifyItemMoved(fromPosition, toPosition);
        }

        public void onItemDismiss(int position) {
            removeItem(position);
        }
    }

    private class ConversationPinItemTouchHelper extends ItemTouchHelper.Callback {

        @Override
        public boolean isLongPressDragEnabled() {
            return true;
        }

        @Override
        public boolean isItemViewSwipeEnabled() {
            return false;
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            final int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
            final int swipeFlags = ItemTouchHelper.START | ItemTouchHelper.END;
            return makeMovementFlags(dragFlags, swipeFlags);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView,
                              RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
            int sourcePosition = source.getAdapterPosition();
            int targetPosition = target.getAdapterPosition();
            adapter.onItemMove(sourcePosition, targetPosition);
            return true;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int i) {
            adapter.onItemDismiss(viewHolder.getAdapterPosition());
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
            }
            super.onSelectedChanged(viewHolder, actionState);
        }

        @Override
        public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
        }
    }

}

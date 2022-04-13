package com.tapmedia.yoush.conversationlist.action;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.tapmedia.yoush.ApplicationContext;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.conversationlist.adapter.ConversationListItemEventListener;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.model.ThreadRecord;
import com.tapmedia.yoush.notifications.NotificationItem;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.ui.base.ViewClickListener;
import com.tapmedia.yoush.ui.main.MainFragment;
import com.tapmedia.yoush.ui.pin.PinAuthFragment;
import com.tapmedia.yoush.ui.widget.AppMotionLayout;
import com.tapmedia.yoush.util.simple.SimpleTransitionListener;

import org.whispersystems.libsignal.util.guava.Optional;


public class ActionBindJob {

    private static int selectedPosition = -1;
    private static int clickedViewId = -1;

    private static View.OnClickListener bindItemGesture(
            RecyclerView.Adapter adapter,
            View itemView,
            ThreadRecord record,
            int position,
            ConversationListItemEventListener listener
    ) {

        AppMotionLayout motionLayout = itemView.findViewById(R.id.layoutMotion);
        motionLayout.addTransitionListener(new SimpleTransitionListener() {
            @Override
            public void onTransitionStarted(MotionLayout layout, int startId, int endId) {
                itemView.getParent().requestDisallowInterceptTouchEvent(true);
            }

            @Override
            public void onTransitionCompleted(MotionLayout layout, int currentId) {
                itemView.getParent().requestDisallowInterceptTouchEvent(false);
                if (currentId != R.id.stateDefault) {
                    int temp = selectedPosition;
                    selectedPosition = position;
                    adapter.notifyItemChanged(temp);
                    return;
                }
            }
        });
        if (selectedPosition != position) {
            motionLayout.safeTransitionTo(R.id.stateDefault);
        }

        View.OnClickListener clickListener = new ViewClickListener() {
            @Override
            public void onClicks(View v) {
                clickedViewId = v.getId();
                selectedPosition = position;
                if (motionLayout.getProgress() != 0f && motionLayout.getProgress() != 1f) {
                    return;
                }
                motionLayout.transitionToState(R.id.stateDefault);
                onActionEvent(record, position, listener);
            }
        };

        motionLayout.viewClickListener = clickListener;
        motionLayout.addViewClickListener(motionLayout.findViewById(R.id.layoutItem));
        return clickListener;
    }

    public static void bindDefaultItemGesture(
            RecyclerView.Adapter adapter,
            View itemView,
            ThreadRecord record,
            int position,
            ConversationListItemEventListener listener
    ) {
        View.OnClickListener clickListener = bindItemGesture(
                adapter,
                itemView,
                record,
                position,
                listener
        );
        // bind read button
        View viewRead = itemView.findViewById(R.id.viewRead);
        viewRead.setOnClickListener(clickListener);
        TextView textViewRead = itemView.findViewById(R.id.textViewRead);
        if (record.isForcedUnread() || record.getUnreadCount() > 0) {
            textViewRead.setText(R.string.mask_as_read);
        } else {
            textViewRead.setText(R.string.mask_as_unread);
        }

        // bind archive button
        View viewArchive = itemView.findViewById(R.id.viewArchive);
        viewArchive.setOnClickListener(clickListener);
        ImageView imageViewArchive = itemView.findViewById(R.id.imageViewArchive);
        TextView textViewArchive = itemView.findViewById(R.id.textViewArchive);
        if (record.isArchived()) {
            imageViewArchive.setImageResource(R.drawable.ic_unarchive);
            viewArchive.setBackgroundResource(R.color.colorUnArchived);
            textViewArchive.setText(R.string.un_archive);
        } else {
            imageViewArchive.setImageResource(R.drawable.ic_archive);
            viewArchive.setBackgroundResource(R.color.colorArchived);
            textViewArchive.setText(R.string.archive);
        }

        // bind hide button
        View viewHide = itemView.findViewById(R.id.viewHide);
        viewHide.setOnClickListener(clickListener);

        // bind delete button
        View viewDelete = itemView.findViewById(R.id.viewDelete);
        viewDelete.setOnClickListener(clickListener);
    }


    public static void bindHiddenConversationListItemGesture(
            RecyclerView.Adapter adapter,
            View itemView,
            ThreadRecord record,
            int position,
            ConversationListItemEventListener listener
    ) {
        View.OnClickListener clickListener = bindItemGesture(
                adapter,
                itemView,
                record,
                position,
                listener
        );
        // bind hide button
        View viewHide = itemView.findViewById(R.id.viewUnHide);
        viewHide.setOnClickListener(clickListener);
    }

    public static void bindDefaultItemGestureNoGesture(
            RecyclerView.Adapter adapter,
            View itemView,
            ThreadRecord record,
            int position,
            ConversationListItemEventListener listener
    ) {
        View layout = itemView.findViewById(R.id.layoutItem);
        layout.setOnClickListener(v -> {
            clickedViewId = v.getId();
            int temp = selectedPosition;
            selectedPosition = position;
            adapter.notifyItemChanged(temp);
            onActionEvent(record, position, listener);
        });
    }

    private static void onActionEvent(
            ThreadRecord record,
            int position,
            ConversationListItemEventListener listener
    ) {
        if (null == listener) return;
        switch (clickedViewId) {
            case R.id.layoutItem:
                listener.onItemEventClick(record, position);
                break;
            case R.id.viewRead:
                if (record.isForcedUnread()) {
                    listener.onItemEventMaskAsRead(record, position);
                } else {
                    listener.onItemEventMaskAsUnRead(record, position);
                }
                break;
            case R.id.viewArchive:
                if (record.isArchived()) {
                    listener.onItemEventUnArchive(record, position);
                } else {
                    listener.onItemEventArchive(record, position);
                }
                break;
            case R.id.viewHide:
                listener.onItemEventHide(record, position);
                break;
            case R.id.viewUnHide:
                listener.onItemEventUnHide(record, position);
                break;
            case R.id.viewDelete:
                listener.onItemEventDelete(record, position);
                break;
        }
        selectedPosition = -1;
        clickedViewId = -1;
    }

    public static void onContactItemClick(
            MainFragment fragment,
            Optional<RecipientId> recipientId,
            String number
    ) {
        Recipient recipient;
        if (!recipientId.isPresent()) {
            recipient = Recipient.external(ApplicationContext.getInstance(), number);
        } else {
            recipient = Recipient.resolved(recipientId.get());
        }
        onContactItemClick(fragment, recipient);
    }

    public static void onContactItemClick(
            MainFragment fragment,
            Recipient recipient
    ) {
        long threadId = DatabaseFactory
                .getThreadDatabase(ApplicationContext.getInstance())
                .getThreadIdIfExistsFor(recipient);
        ThreadRecord threadRecord = ActionData.getThreadRecord(threadId);
        if (threadRecord == null) {
            fragment.goToConversation(recipient.getId(), threadId);
            return;
        }
        if (threadRecord.isHidden()) {
            PinAuthFragment f = new PinAuthFragment();
            f.onSuccess = () -> fragment.goToConversation(threadRecord);
            fragment.addFragment(f);
            return;
        }
        fragment.goToConversation(threadRecord);

    }


}
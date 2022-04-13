package com.tapmedia.yoush.ui.main;

import android.content.Intent;

import androidx.annotation.NonNull;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.conversation.ConversationActivity;
import com.tapmedia.yoush.database.ThreadDatabase;
import com.tapmedia.yoush.database.model.ThreadRecord;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.ui.base.BaseDialogFragment;

public abstract class MainDialogFragment extends BaseDialogFragment {

    public void goToConversation(@NonNull RecipientId recipientId, long threadId) {
        goToConversation(recipientId, threadId, ThreadDatabase.DistributionTypes.DEFAULT, -1);
    }

    public void goToConversation(@NonNull RecipientId recipientId, long threadId, int distributionType, int startingPosition) {
        Intent intent = ConversationActivity.buildIntent(requireActivity(), recipientId, threadId, distributionType, startingPosition);
        requireActivity().startActivity(intent);
        requireActivity().overridePendingTransition(R.anim.slide_from_end, R.anim.fade_scale_out);
    }

    public void goToConversation(ThreadRecord record) {
        goToConversation(record.getRecipient().getId(), record.getThreadId(), record.getDistributionType(), -1);
    }
}

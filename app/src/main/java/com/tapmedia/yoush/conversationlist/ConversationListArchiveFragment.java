package com.tapmedia.yoush.conversationlist;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;
import com.tapmedia.yoush.R;


public class ConversationListArchiveFragment extends ConversationListFragment {


    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setHasOptionsMenu(false);
    }

    @Override
    public void onViewCreated() {
        super.onViewCreated();
        imageViewViewBack.setVisibility(View.VISIBLE);
        imageViewCreateConversation.setVisibility(View.GONE);
        textViewAppBar.setText(R.string.AndroidManifest_archived_conversations);

    }

    @Override
    public void onLiveDataObservers() {

    }

    @Override
    public void onSubmitList(@NonNull @NotNull ConversationListViewModel.ConversationList conversationList) {
        super.onSubmitList(conversationList);
        recyclerView.setVisibility(View.VISIBLE);
    }

    @Override
    protected boolean isArchived() {
        return true;
    }


}



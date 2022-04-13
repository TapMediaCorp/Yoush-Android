package com.tapmedia.yoush.conversation.background;

import android.content.Intent;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.conversation.job.MessageJob;
import com.tapmedia.yoush.groups.GroupId;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.ui.main.MainFragment;
import com.tapmedia.yoush.util.JsArray;

import java.util.ArrayList;

public class ConversationBackgroundFragment extends MainFragment {

    public static final int CONVERSATION_BACKGROUND = 13;
    public RecipientId recipientId = null;
    public GroupId groupId = null;
    private View viewBack;
    private RecyclerView recyclerView;
    private ConversationBackgroundAdapter adapter = new ConversationBackgroundAdapter();
    private View textViewGallery;

    @Override
    public int layoutResource() {
        return R.layout.conversation_background;
    }

    @Override
    public void onFindView() {
        viewBack = find(R.id.viewBack);
        recyclerView = find(R.id.recyclerView);
        textViewGallery = find(R.id.textViewGallery);
    }

    @Override
    public void onViewCreated() {
        setStatusBarColor(R.color.colorPrimary);
        addViewClicks(viewBack, textViewGallery);
        adapter.bind(recyclerView, 3);
        adapter.setOnItemClickListener((item, position) -> {
            MessageJob.onSmsPermissionGranted(requireActivity(), () -> {
                if (position != 0) {
                    BackgroundJobSend.setBackground(null, item);
                } else {
                    BackgroundJobSend.removeBackground();
                }
                requireActivity().finish();
            });
        });
        ArrayList<String> list = JsArray.readAssets("wallpaper/wallpaper.json");
        list.add(0, null);
        adapter.setListItem(list);
    }

    @Override
    public void onLiveDataObservers() {
    }

    @Override
    protected void onViewClick(View v) {
        switch (v.getId()) {
            case R.id.viewBack:
                requireActivity().onBackPressed();
                break;
            case R.id.textViewGallery:
                Intent getIntent = new Intent(Intent.ACTION_GET_CONTENT);
                getIntent.setType("image/*");

                Intent pickIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                pickIntent.setType("image/*");

                Intent chooserIntent = Intent.createChooser(getIntent, "Select Image");
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] {pickIntent});

                startActivityForResult(chooserIntent, ConversationBackgroundFragment.CONVERSATION_BACKGROUND);
                break;
        }
    }


}

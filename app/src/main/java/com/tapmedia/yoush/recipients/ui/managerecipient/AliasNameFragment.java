package com.tapmedia.yoush.recipients.ui.managerecipient;


import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProviders;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.ui.main.MainFragment;

public class AliasNameFragment extends MainFragment {

    private EditText editTextName;
    private TextView textViewHint;
    private View viewBack;
    private View viewClose;
    private View viewSave;
    private ManageRecipientViewModel viewModel;
    public RecipientId recipientId;
    private Recipient recipient;

    @Override
    public int layoutResource() {
        return R.layout.alias_name;
    }

    @Override
    public void onFindView() {
        editTextName = find(R.id.editTextName);
        textViewHint = find(R.id.textViewHint);
        viewBack = find(R.id.viewBack);
        viewClose = find(R.id.viewClose);
        viewSave = find(R.id.viewSave);
    }

    @Override
    public void onViewCreated() {
        recipient = Recipient.resolved(recipientId);
        ManageRecipientViewModel.Factory factory = new ManageRecipientViewModel.Factory(recipientId);
        viewModel = ViewModelProviders.of(requireActivity(), factory).get(ManageRecipientViewModel.class);
        addViewClicks(viewBack, viewClose, viewSave);
    }

    @Override
    public void onLiveDataObservers() {
        String profileName = recipient.getProfileName().toString();
        textViewHint.setText(str(R.string.change_alias_name_hint, profileName));
        editTextName.setHint(profileName);
        editTextName.setText(recipient.getDisplayName(requireContext()));
    }

    @Override
    protected void onViewClick(View v) {
        switch (v.getId()) {
            case R.id.viewBack:
            case R.id.viewClose:
                onBackPress();
                break;
            case R.id.viewSave:
                hideKeyboard();
                editTextName.clearFocus();
                viewModel.saveSettings(editTextName.getText().toString());
                onBackPress();
                break;
        }
    }

}

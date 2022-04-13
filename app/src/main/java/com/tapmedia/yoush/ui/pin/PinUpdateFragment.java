package com.tapmedia.yoush.ui.pin;


import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.keyvalue.SignalStore;

public class PinUpdateFragment extends BasePinFragment {

    private boolean hasAuth = false;

    @Override
    public void onViewCreated() {
        super.onViewCreated();
        textViewTitle.setText(R.string.pin_enter_old);
        viewBack.setVisibility(View.GONE);
    }

    @Override
    public void onCodeComplete(@NonNull @NotNull String code) {
        textViewMessage.setText(null);
        if (!hasAuth) {
            String pinCode = SignalStore.pinCodeValues().getPinCode();
            if (code.equals(pinCode)) {
                getView().postDelayed(() -> {
                    hasAuth = true;
                    viewPinCode.clear();
                    viewKeyboard.displayKeyboard();
                    textViewTitle.setText(R.string.pin_enter_new);
                    textViewHint.setText(R.string.conversation_list_hide_activity_notice_bottom);
                }, 300);
                return;
            }
            onFailure(() -> {
                textViewMessage.setText(R.string.KbsReminderDialog__incorrect_pin_try_again);
            });
            return;
        }

        if (TextUtils.isEmpty(pinTemp)) {
            getView().postDelayed(() -> {
                pinTemp = code;
                viewPinCode.clear();
                viewKeyboard.displayKeyboard();
                textViewTitle.setText(R.string.pin_confirm_new);
                viewModel.setPinCode(code);
            }, 300);
            return;
        }

        if (pinTemp.equals(code)) {
            SignalStore.pinCodeValues().setPinCode(code);
            pinTemp = null;
            onSuccessfully();
            return;
        }
        onFailure(() -> {
            textViewMessage.setText(R.string.ConfirmKbsPinFragment__pins_dont_match);
        });
    }
}

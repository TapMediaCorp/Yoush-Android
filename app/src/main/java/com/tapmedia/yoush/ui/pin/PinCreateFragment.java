package com.tapmedia.yoush.ui.pin;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.keyvalue.SignalStore;

public class PinCreateFragment extends BasePinFragment {

  @Override
  public void onViewCreated() {
    super.onViewCreated();
    textViewTitle.setText(R.string.pin_enter_new);
    textViewHint.setText(R.string.conversation_list_hide_activity_notice_bottom);
  }

  @Override
  public void onCodeComplete(@NonNull @NotNull String code) {
    textViewMessage.setText(null);
    if (TextUtils.isEmpty(pinTemp)) {
      getView().postDelayed(() -> {
        pinTemp = code;
        viewPinCode.clear();
        viewKeyboard.displayKeyboard();
        textViewTitle.setText(R.string.pin_confirm_new);
        viewModel.setPinCode(code);
      },300);
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


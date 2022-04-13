package com.tapmedia.yoush.ui.pin;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.keyvalue.SignalStore;

public final class PinAuthFragment extends BasePinFragment {

  @Override
  public void onViewCreated() {
    super.onViewCreated();
    textViewTitle.setText(R.string.conversation_list_unhide_activity_notice_header);
  }

  @Override
  public void onCodeComplete(@NonNull @NotNull String code) {
    textViewMessage.setText(null);
    String pinCode = SignalStore.pinCodeValues().getPinCode();
    if (code.equals(pinCode)) {
      onSuccessfully();
      return;
    }
    onFailure(() -> {
      textViewMessage.setText(R.string.KbsReminderDialog__incorrect_pin_try_again);
    });

  }

}


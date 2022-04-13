package com.tapmedia.yoush.ui.pin;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.lifecycle.ViewModelProviders;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.components.registration.VerificationCodeView;
import com.tapmedia.yoush.components.registration.VerificationPinKeyboard;
import com.tapmedia.yoush.conversationlist.ConversationListViewModel;
import com.tapmedia.yoush.registration.ReceivedSmsEvent;
import com.tapmedia.yoush.ui.main.MainFragment;
import com.tapmedia.yoush.util.concurrent.AssertedSuccessListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


abstract class BasePinFragment extends MainFragment implements
        VerificationCodeView.OnCodeEnteredListener {

    public TextView textViewTitle;
    public TextView textViewMessage;
    public TextView textViewHint;
    public VerificationCodeView viewPinCode;
    public VerificationPinKeyboard viewKeyboard;
    public boolean autoCompleting;
    public TextView textViewSubTitle;
    public ConversationListViewModel viewModel;
    public String pinTemp = null;
    public View viewBack;
    public Runnable onSuccess;

    private static List<Integer> convertVerificationCodeToDigits(@Nullable String code) {
        if (code == null || code.length() != 6) {
            return Collections.emptyList();
        }
        List<Integer> result = new ArrayList<>(code.length());
        try {
            for (int i = 0; i < code.length(); i++) {
                result.add(Integer.parseInt(Character.toString(code.charAt(i))));
            }
        } catch (NumberFormatException e) {
            return Collections.emptyList();
        }

        return result;
    }

    @Override
    public int layoutResource() {
        return R.layout.pin;
    }

    @Override
    public void onFindView() {
        textViewTitle = find(R.id.textViewTitle);
        textViewMessage = find(R.id.textViewMessage);
        textViewHint = find(R.id.textViewHint);
        viewPinCode = find(R.id.viewPinCode);
        viewKeyboard = find(R.id.viewKeyboard);
        textViewSubTitle = find(R.id.textViewSubTitle);
        viewBack = find(R.id.viewBack);
    }

    @Override
    public void onViewCreated() {
        hideKeyboard();
        viewBack.setOnClickListener(v -> onBackPress());
        initializeViewModel();
        connectKeyboard(viewPinCode, viewKeyboard);
        viewPinCode.setOnCompleteListener(this);
        setStatusBarColor(R.color.colorWhite);
    }

    @Override
    public void onLiveDataObservers() {

    }

    @Override
    public void onResume() {
        super.onResume();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        setStatusBarColor(R.color.colorPrimary);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    public void onSuccessfully() {
        viewKeyboard.displaySuccess().addListener(new AssertedSuccessListener<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                onBackPress();
                if (null != onSuccess) {
                    onSuccess.run();
                }
            }
        });
    }

    public void onFailure(Runnable runnable) {
        viewPinCode.clear();
        viewKeyboard.displayKeyboard();
        runnable.run();
    }

    private void initializeViewModel() {
        viewModel = ViewModelProviders.of(this, new ConversationListViewModel.Factory(false)).get(ConversationListViewModel.class);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(@NonNull LifecycleOwner owner) {
                viewModel.onVisible();
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onVerificationCodeReceived(@NonNull ReceivedSmsEvent event) {
        viewPinCode.clear();
        List<Integer> parsedCode = convertVerificationCodeToDigits(event.getCode());
        autoCompleting = true;
        final int size = parsedCode.size();
        for (int i = 0; i < size; i++) {
            final int index = i;
            viewPinCode.postDelayed(() -> {
                viewPinCode.append(parsedCode.get(index));
                if (index == size - 1) {
                    autoCompleting = false;
                }
            }, i * 200);
        }
    }

    private void connectKeyboard(VerificationCodeView verificationCodeView, VerificationPinKeyboard keyboard) {
        keyboard.setOnKeyPressListener(key -> {
            if (!autoCompleting) {
                if (key >= 0) {
                    verificationCodeView.append(key);
                } else {
                    verificationCodeView.delete();
                }
            }
        });
    }


}

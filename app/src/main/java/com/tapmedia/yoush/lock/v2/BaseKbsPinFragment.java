package com.tapmedia.yoush.lock.v2;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.airbnb.lottie.LottieAnimationView;

import com.tapmedia.yoush.LoggingFragment;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.keyvalue.SignalStore;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.pin.PinOptOutDialog;
import com.tapmedia.yoush.pin.PinState;
import com.tapmedia.yoush.util.CommunicationActions;
import com.tapmedia.yoush.util.concurrent.SimpleTask;
import com.tapmedia.yoush.util.text.AfterTextChanged;
import com.tapmedia.yoush.util.views.LearnMoreTextView;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;

import java.io.IOException;

abstract class BaseKbsPinFragment<ViewModel extends BaseKbsPinViewModel> extends LoggingFragment {

  private TextView            title;
  private LearnMoreTextView   description;
  private EditText            input;
  private TextView            label;
  private TextView            keyboardToggle;
  private TextView            confirm;
  private LottieAnimationView lottieProgress;
  private LottieAnimationView lottieEnd;
  private ViewModel           viewModel;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
    StrictMode.setThreadPolicy(policy);
    setHasOptionsMenu(true);
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater,
                                     @Nullable ViewGroup container,
                                     @Nullable Bundle savedInstanceState)
  {
    SimpleTask.run(() -> {
      try {
        PinState.onPinCreationSkipped(requireContext());
        return true;
      } catch (IOException | UnauthenticatedResponseException e) {
//        Log.w(TAG, e);
        return false;
      }
    }, success -> {
      if (success) {
        closeNavGraphBranch();
      } else {
        PinState.onPinCreateFailure();
        closeNavGraphBranch();
      }
    });
    return inflater.inflate(R.layout.base_kbs_pin_fragment, container, false);
//    return null;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
//    initializeViews(view);
//
//    viewModel = initializeViewModel();
//    viewModel.getUserEntry().observe(getViewLifecycleOwner(), kbsPin -> {
//      boolean isEntryValid = kbsPin.length() >= KbsConstants.MINIMUM_PIN_LENGTH;
//
//      confirm.setEnabled(isEntryValid);
//      confirm.setAlpha(isEntryValid ? 1f : 0.5f);
//    });
//
//    viewModel.getKeyboard().observe(getViewLifecycleOwner(), keyboardType -> {
//      updateKeyboard(keyboardType);
//      keyboardToggle.setText(resolveKeyboardToggleText(keyboardType));
//    });
//
//    description.setOnLinkClickListener(v -> {
//      CommunicationActions.openBrowserLink(requireContext(), getString(R.string.BaseKbsPinFragment__learn_more_url));
//    });
//
//    Toolbar toolbar = view.findViewById(R.id.kbs_pin_toolbar);
//    ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);
//    ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle(null);
//
//    initializeListeners();
//

  }

  @Override
  public void onResume() {
    super.onResume();

//    input.requestFocus();
  }

  @Override
  public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
    inflater.inflate(R.menu.pin_skip, menu);


  }

  @Override
  public void onPrepareOptionsMenu(@NonNull Menu menu) {
    if (RegistrationLockUtil.userHasRegistrationLock(requireContext()) ||
        SignalStore.kbsValues().hasPin())
    {
      menu.clear();
    }
//    onPinSkipped();

//    SimpleTask.run(() -> {
//      try {
//        PinState.onPinCreationSkipped(requireContext());
//        return true;
//      } catch (IOException | UnauthenticatedResponseException e) {
////        Log.w(TAG, e);
//        return false;
//      }
//    }, success -> {
//      if (success) {
//        closeNavGraphBranch();
//      } else {
//        PinState.onPinCreateFailure();
//        closeNavGraphBranch();
//      }
//    });
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    switch (item.getItemId()) {
      case R.id.menu_pin_learn_more:
        onLearnMore();
        return true;
      case R.id.menu_pin_skip:
        onPinSkipped();
        return true;
    }

    return false;
  }

  protected abstract ViewModel initializeViewModel();

  protected abstract void initializeViewStates();

  protected TextView getTitle() {
    return title;
  }

  protected LearnMoreTextView getDescription() {
    return description;
  }

  protected EditText getInput() {
    return input;
  }

  protected LottieAnimationView getLottieProgress() {
    return lottieProgress;
  }

  protected LottieAnimationView getLottieEnd() {
    return lottieEnd;
  }

  protected TextView getLabel() {
    return label;
  }

  protected TextView getKeyboardToggle() {
    return keyboardToggle;
  }

  protected TextView getConfirm() {
    return confirm;
  }

  protected void closeNavGraphBranch() {
    Intent activityIntent = requireActivity().getIntent();
    if (activityIntent != null && activityIntent.hasExtra("next_intent")) {
      startActivity(activityIntent.getParcelableExtra("next_intent"));
    }

    requireActivity().finish();
  }

  private void initializeViews(@NonNull View view) {
//    title          = view.findViewById(R.id.edit_kbs_pin_title);
//    description    = view.findViewById(R.id.edit_kbs_pin_description);
//    input          = view.findViewById(R.id.edit_kbs_pin_input);
//    label          = view.findViewById(R.id.edit_kbs_pin_input_label);
//    keyboardToggle = view.findViewById(R.id.edit_kbs_pin_keyboard_toggle);
//    confirm        = view.findViewById(R.id.edit_kbs_pin_confirm);
//    lottieProgress = view.findViewById(R.id.edit_kbs_pin_lottie_progress);
//    lottieEnd      = view.findViewById(R.id.edit_kbs_pin_lottie_end);

    initializeViewStates();
  }

  private void initializeListeners() {
    input.addTextChangedListener(new AfterTextChanged(s -> viewModel.setUserEntry(s.toString())));
    input.setImeOptions(EditorInfo.IME_ACTION_NEXT);
    input.setOnEditorActionListener(this::handleEditorAction);
    keyboardToggle.setOnClickListener(v -> viewModel.toggleAlphaNumeric());
    confirm.setOnClickListener(v -> viewModel.confirm());
  }

  private boolean handleEditorAction(@NonNull View view, int actionId, @NonNull KeyEvent event) {
    if (actionId == EditorInfo.IME_ACTION_NEXT && confirm.isEnabled()) {
      viewModel.confirm();
    }

    return true;
  }

  private void updateKeyboard(@NonNull PinKeyboardType keyboard) {
    boolean isAlphaNumeric = keyboard == PinKeyboardType.ALPHA_NUMERIC;

    input.setInputType(isAlphaNumeric ? InputType.TYPE_CLASS_TEXT   | InputType.TYPE_TEXT_VARIATION_PASSWORD
                                      : InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
  }

  private @StringRes int resolveKeyboardToggleText(@NonNull PinKeyboardType keyboard) {
    if (keyboard == PinKeyboardType.ALPHA_NUMERIC) {
      return R.string.BaseKbsPinFragment__create_numeric_pin;
    } else {
      return R.string.BaseKbsPinFragment__create_alphanumeric_pin;
    }
  }

  private void onLearnMore() {
    CommunicationActions.openBrowserLink(requireContext(), getString(R.string.KbsSplashFragment__learn_more_link));
  }

  private void onPinSkipped() {
    PinOptOutDialog.showForSkip(requireContext(),
                                this::closeNavGraphBranch,
                                () -> {
                                  PinState.onPinCreateFailure();
                                  closeNavGraphBranch();
                                });
  }
}

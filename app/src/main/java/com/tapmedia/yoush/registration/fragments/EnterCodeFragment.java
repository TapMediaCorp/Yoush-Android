package com.tapmedia.yoush.registration.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.navigation.Navigation;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.components.registration.CallMeCountDownView;
import com.tapmedia.yoush.components.registration.VerificationCodeView;
import com.tapmedia.yoush.components.registration.VerificationPinKeyboard;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.registration.ReceivedSmsEvent;
import com.tapmedia.yoush.registration.service.CodeVerificationRequest;
import com.tapmedia.yoush.registration.service.RegistrationCodeRequest;
import com.tapmedia.yoush.registration.service.RegistrationService;
import com.tapmedia.yoush.registration.viewmodel.RegistrationViewModel;
import com.tapmedia.yoush.util.concurrent.AssertedSuccessListener;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EnterCodeFragment extends BaseRegistrationFragment {

  private static final String TAG = Log.tag(EnterCodeFragment.class);

  private ScrollView              scrollView;
  private TextView                header;
  private TextView                title;
  private VerificationCodeView    verificationCodeView;
  private VerificationPinKeyboard keyboard;
  private CallMeCountDownView     callMeCountDown;
  private View                    wrongNumber;
  private View                    noCodeReceivedHelp;
//  private Button                  btnAccept;
  private boolean                 autoCompleting;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_enter_code, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    setDebugLogSubmitMultiTapView(view.findViewById(R.id.verify_header));

    scrollView           = view.findViewById(R.id.scroll_view);
    header               = view.findViewById(R.id.verify_header);
    title                = view.findViewById(R.id.verify_title);
    verificationCodeView = view.findViewById(R.id.viewPinCode);
//    btnAccept            = view.findViewById(R.id.verify_btn_accept);
    keyboard             = view.findViewById(R.id.viewKeyboard);
//    callMeCountDown      = view.findViewById(R.id.call_me_count_down);
//    wrongNumber          = view.findViewById(R.id.wrong_number)
//    noCodeReceivedHelp   = view.findViewById(R.id.no_code);

    connectKeyboard(verificationCodeView, keyboard);

    setOnCodeFullyEnteredListener(verificationCodeView);

//    wrongNumber.setOnClickListener(v -> Navigation.findNavController(view).navigate(EnterCodeFragmentDirections.actionWrongNumber()));

//    callMeCountDown.setOnClickListener(v -> handlePhoneCallRequest());

//    callMeCountDown.setListener((v, remaining) -> {
//      if (remaining <= 30) {
//        scrollView.smoothScrollTo(0, v.getBottom());
//        callMeCountDown.setListener(null);
//      }
//    });

//    noCodeReceivedHelp.setOnClickListener(v -> sendEmailToSupport());
//
//    getModel().getSuccessfulCodeRequestAttempts().observe(this, (attempts) -> {
//      if (attempts >= 3) {
//        noCodeReceivedHelp.setVisibility(View.VISIBLE);
//        scrollView.postDelayed(() -> scrollView.smoothScrollTo(0, noCodeReceivedHelp.getBottom()), 15000);
//      }
//    });
  }

  private void setOnCodeFullyEnteredListener(VerificationCodeView verificationCodeView) {
    verificationCodeView.setOnCompleteListener(code -> {
      RegistrationViewModel model = getModel();

      model.onVerificationCodeEntered(code);
//      callMeCountDown.setVisibility(View.INVISIBLE);
//      wrongNumber.setVisibility(View.INVISIBLE);
      keyboard.displayProgress();

      RegistrationService registrationService = RegistrationService.getInstance(model.getNumber().getE164Number(), model.getRegistrationSecret());

      registrationService.verifyAccount(requireActivity(), model.getFcmToken(), code, null, null, null,
              new CodeVerificationRequest.VerifyCallback() {

                @Override
                public void onSuccessfulRegistration() {
                  keyboard.displaySuccess().addListener(new AssertedSuccessListener<Boolean>() {
                    @Override
                    public void onSuccess(Boolean result) {
                      handleSuccessfulRegistration();
                    }
                  });
                }

                @Override
                public void onV1RegistrationLockPinRequiredOrIncorrect(long timeRemaining) {
                  model.setTimeRemaining(timeRemaining);
                  keyboard.displayLocked().addListener(new AssertedSuccessListener<Boolean>() {
                    @Override
                    public void onSuccess(Boolean r) {
                      Navigation.findNavController(requireView())
                              .navigate(EnterCodeFragmentDirections.actionRequireKbsLockPin(timeRemaining, true));
                    }
                  });
                }

                @Override
                public void onKbsRegistrationLockPinRequired(long timeRemaining, @NonNull TokenResponse tokenResponse, @NonNull String kbsStorageCredentials) {
                  model.setTimeRemaining(timeRemaining);
                  model.setStorageCredentials(kbsStorageCredentials);
                  model.setKeyBackupCurrentToken(tokenResponse);
                  keyboard.displayLocked().addListener(new AssertedSuccessListener<Boolean>() {
                    @Override
                    public void onSuccess(Boolean r) {
                      Navigation.findNavController(requireView())
                              .navigate(EnterCodeFragmentDirections.actionRequireKbsLockPin(timeRemaining, false));
                    }
                  });
                }

                @Override
                public void onIncorrectKbsRegistrationLockPin(@NonNull TokenResponse tokenResponse) {
                  throw new AssertionError("Unexpected, user has made no pin guesses");
                }

                @Override
                public void onRateLimited() {
                    keyboard.displayFailure().addListener(new AssertedSuccessListener<Boolean>() {
                      @Override
                      public void onSuccess(Boolean r) {
                        new AlertDialog.Builder(requireContext())
                                       .setTitle("Mã Pin không trùng khớp")
//                                       .setMessage(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later)
                                        .setMessage("Vui lòng nhập lại mã pin")
                                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
        //                                 callMeCountDown.setVisibility(View.VISIBLE);
        //                                 wrongNumber.setVisibility(View.VISIBLE);
                                         verificationCodeView.clear();
                                         keyboard.displayKeyboard();
                                       })
                                       .show();
                      }
                    });
                }

                @Override
                public void onKbsAccountLocked(@Nullable Long timeRemaining) {
                  if (timeRemaining != null) {
                    model.setTimeRemaining(timeRemaining);
                  }
                  Navigation.findNavController(requireView()).navigate(RegistrationLockFragmentDirections.actionAccountLocked());
                }

                @Override
                public void onError() {
//                    Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show();
                    Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show();
                    keyboard.displayFailure().addListener(new AssertedSuccessListener<Boolean>() {
                      @Override
                      public void onSuccess(Boolean result) {
//                        callMeCountDown.setVisibility(View.VISIBLE);
//                        wrongNumber.setVisibility(View.VISIBLE);
                        verificationCodeView.clear();
                        keyboard.displayKeyboard();
                      }
                    });
                }
              });
    });
  }

  private void handleSuccessfulRegistration() {
    Navigation.findNavController(requireView()).navigate(EnterCodeFragmentDirections.actionSuccessfulRegistration());
  }

  @Override
  public void onStart() {
    super.onStart();
    EventBus.getDefault().register(this);
  }

  @Override
  public void onStop() {
    super.onStop();
    EventBus.getDefault().unregister(this);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onVerificationCodeReceived(@NonNull ReceivedSmsEvent event) {
    verificationCodeView.clear();

    List<Integer> parsedCode = convertVerificationCodeToDigits(event.getCode());

    autoCompleting = true;
    final int size = parsedCode.size();

    for (int i = 0; i < size; i++) {
      final int index = i;
      verificationCodeView.postDelayed(() -> {
        verificationCodeView.append(parsedCode.get(index));
        if (index == size - 1) {
          autoCompleting = false;
        }
      }, i * 200);
    }
  }

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
      Log.w(TAG, "Failed to convert code into digits.", e);
      return Collections.emptyList();
    }

    return result;
  }

  private void handlePhoneCallRequest() {
//    callMeCountDown.startCountDown(RegistrationConstants.SUBSEQUENT_CALL_AVAILABLE_AFTER);

    RegistrationViewModel model   = getModel();
    String                captcha = model.getCaptchaToken();
    model.clearCaptchaResponse();

//    NavController navController = Navigation.findNavController(callMeCountDown);

    RegistrationService registrationService = RegistrationService.getInstance(model.getNumber().getE164Number(), model.getRegistrationSecret());

    registrationService.requestVerificationCode(requireActivity(), RegistrationCodeRequest.Mode.PHONE_CALL, captcha,
            new RegistrationCodeRequest.SmsVerificationCodeCallback() {

              @Override
              public void onNeedCaptcha() {
//          navController.navigate(EnterCodeFragmentDirections.actionRequestCaptcha());
              }

              @Override
              public void requestSent(@Nullable String fcmToken) {
                model.setFcmToken(fcmToken);
                model.markASuccessfulAttempt();
              }

              @Override
              public void onRateLimited() {
                Toast.makeText(requireContext(), R.string.RegistrationActivity_rate_limited_to_service, Toast.LENGTH_LONG).show();
              }

              @Override
              public void onError() {
                Toast.makeText(requireContext(), R.string.RegistrationActivity_unable_to_connect_to_service, Toast.LENGTH_LONG).show();
              }
            });
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

  @Override
  public void onResume() {
    super.onResume();

    getModel().getLiveNumber().observe(this, (s) -> header.setText(requireContext().getString(R.string.RegistrationActivity_enter_the_code)));
//    getModel().getLiveNumber().observe(this, (s) -> btnAccept.setText(requireContext().getString(R.string.RegistrationActivity_btn_accept)));
    getModel().getLiveNumber().observe(this, (s) -> {
        title.setText(requireContext().getString(R.string.RegistrationActivity_title, s.getFullFormattedNumber()));
    });
  }

}


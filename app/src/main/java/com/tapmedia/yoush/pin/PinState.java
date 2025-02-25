package com.tapmedia.yoush.pin;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobmanager.JobTracker;
import com.tapmedia.yoush.jobs.RefreshAttributesJob;
import com.tapmedia.yoush.jobs.StorageForcePushJob;
import com.tapmedia.yoush.keyvalue.KbsValues;
import com.tapmedia.yoush.keyvalue.SignalStore;
import com.tapmedia.yoush.lock.PinHashing;
import com.tapmedia.yoush.lock.RegistrationLockReminders;
import com.tapmedia.yoush.lock.v2.PinKeyboardType;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.megaphone.Megaphones;
import com.tapmedia.yoush.registration.service.KeyBackupSystemWrongPinException;
import com.tapmedia.yoush.util.Hex;
import com.tapmedia.yoush.util.TextSecurePreferences;
import com.tapmedia.yoush.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.KbsPinData;
import org.whispersystems.signalservice.api.KeyBackupService;
import org.whispersystems.signalservice.api.KeyBackupServicePinException;
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException;
import org.whispersystems.signalservice.api.kbs.HashedPin;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class PinState {

  private static final String TAG = Log.tag(PinState.class);

  /**
   * Invoked during registration to restore the master key based on the server response during
   * verification.
   *
   * Does not affect {@link PinState}.
   */
  public static synchronized @Nullable KbsPinData restoreMasterKey(@Nullable String pin,
                                                                   @Nullable String basicStorageCredentials,
                                                                   @NonNull TokenResponse tokenResponse)
    throws IOException, KeyBackupSystemWrongPinException, KeyBackupSystemNoDataException
  {
    Log.i(TAG, "restoreMasterKey()");

    if (pin == null) return null;

    if (basicStorageCredentials == null) {
      throw new AssertionError("Cannot restore KBS key, no storage credentials supplied");
    }

    KeyBackupService keyBackupService = ApplicationDependencies.getKeyBackupService();

    Log.i(TAG, "Opening key backup service session");
    KeyBackupService.RestoreSession session = keyBackupService.newRegistrationSession(basicStorageCredentials, tokenResponse);

    try {
      Log.i(TAG, "Restoring pin from KBS");
      HashedPin  hashedPin = PinHashing.hashPin(pin, session);
      KbsPinData kbsData   = session.restorePin(hashedPin);
      if (kbsData != null) {
        Log.i(TAG, "Found registration lock token on KBS.");
      } else {
        throw new AssertionError("Null not expected");
      }
      return kbsData;
    } catch (UnauthenticatedResponseException e) {
      Log.w(TAG, "Failed to restore key", e);
      throw new IOException(e);
    } catch (KeyBackupServicePinException e) {
      Log.w(TAG, "Incorrect pin", e);
      throw new KeyBackupSystemWrongPinException(e.getToken());
    }
  }

  /**
   * Invoked after a user has successfully registered. Ensures all the necessary state is updated.
   */
  public static synchronized void onRegistration(@NonNull Context context,
                                                 @Nullable KbsPinData kbsData,
                                                 @Nullable String pin,
                                                 boolean hasPinToRestore)
  {
    Log.i(TAG, "onNewRegistration()");

    TextSecurePreferences.setV1RegistrationLockPin(context, pin);

    if (kbsData == null && pin != null) {
      Log.i(TAG, "Registration Lock V1");
      SignalStore.kbsValues().clearRegistrationLockAndPin();
      TextSecurePreferences.setV1RegistrationLockEnabled(context, true);
      TextSecurePreferences.setRegistrationLockLastReminderTime(context, System.currentTimeMillis());
      TextSecurePreferences.setRegistrationLockNextReminderInterval(context, RegistrationLockReminders.INITIAL_INTERVAL);
    } else if (kbsData != null && pin != null) {
      Log.i(TAG, "Registration Lock V2");
      TextSecurePreferences.setV1RegistrationLockEnabled(context, false);
      SignalStore.kbsValues().setV2RegistrationLockEnabled(true);
      SignalStore.kbsValues().setKbsMasterKey(kbsData, pin);
      SignalStore.pinValues().resetPinReminders();
      resetPinRetryCount(context, pin, kbsData);
    } else if (hasPinToRestore) {
      Log.i(TAG, "Has a PIN to restore.");
      SignalStore.kbsValues().clearRegistrationLockAndPin();
      TextSecurePreferences.setV1RegistrationLockEnabled(context, false);
      SignalStore.storageServiceValues().setNeedsAccountRestore(true);
    } else {
      Log.i(TAG, "No registration lock or PIN at all.");
      SignalStore.kbsValues().clearRegistrationLockAndPin();
      TextSecurePreferences.setV1RegistrationLockEnabled(context, false);
    }

    updateState(buildInferredStateFromOtherFields());
  }

  /**
   * Invoked when the user is going through the PIN restoration flow (which is separate from reglock).
   */
  public static synchronized void onSignalPinRestore(@NonNull Context context, @NonNull KbsPinData kbsData, @NonNull String pin) {
    Log.i(TAG, "onSignalPinRestore()");

    SignalStore.kbsValues().setKbsMasterKey(kbsData, pin);
    SignalStore.kbsValues().setV2RegistrationLockEnabled(false);
    SignalStore.pinValues().resetPinReminders();
    SignalStore.storageServiceValues().setNeedsAccountRestore(false);
    resetPinRetryCount(context, pin, kbsData);

    updateState(buildInferredStateFromOtherFields());
  }

  /**
   * Invoked when the user skips out on PIN restoration or otherwise fails to remember their PIN.
   */
  public static synchronized void onPinRestoreForgottenOrSkipped() {
    SignalStore.kbsValues().clearRegistrationLockAndPin();
    SignalStore.storageServiceValues().setNeedsAccountRestore(false);

    updateState(buildInferredStateFromOtherFields());
  }

  /**
   * Invoked whenever the Signal PIN is changed or created.
   */
  @WorkerThread
  public static synchronized void onPinChangedOrCreated(@NonNull Context context, @NonNull String pin, @NonNull PinKeyboardType keyboard)
      throws IOException, UnauthenticatedResponseException
  {
    Log.i(TAG, "onPinChangedOrCreated()");

    boolean isFirstPin = !SignalStore.kbsValues().hasPin() || SignalStore.kbsValues().hasOptedOut();

    setPin(context, pin, keyboard);
    SignalStore.kbsValues().optIn();

    if (isFirstPin) {
      Log.i(TAG, "First time setting a PIN. Refreshing attributes to set the 'storage' capability.");
      bestEffortRefreshAttributes();
    } else {
      Log.i(TAG, "Not the first time setting a PIN.");
    }

    updateState(buildInferredStateFromOtherFields());
  }

  /**
   * Invoked when PIN creation fails.
   */
  public static synchronized void onPinCreateFailure() {
    Log.i(TAG, "onPinCreateFailure()");
    if (getState() == State.NO_REGISTRATION_LOCK) {
      SignalStore.kbsValues().onPinCreateFailure();
    }
  }

  /**
   * Invoked when the user has enabled the "PIN opt out" setting.
   */
  @WorkerThread
  public static synchronized void onPinOptOut(@NonNull Context context)
    throws IOException, UnauthenticatedResponseException
  {
    Log.i(TAG, "onPinOptOutEnabled()");
    assertState(State.PIN_WITH_REGISTRATION_LOCK_DISABLED, State.NO_REGISTRATION_LOCK);

    optOutOfPin(context);

    updateState(buildInferredStateFromOtherFields());
  }

  /**
   * Invoked when the user has chosen to skip PIN creation.
   */
  @WorkerThread
  public static synchronized void onPinCreationSkipped(@NonNull Context context)
    throws IOException, UnauthenticatedResponseException
  {
    Log.i(TAG, "onPinCreationSkipped()");
    assertState(State.NO_REGISTRATION_LOCK);

    optOutOfPin(context);

    updateState(buildInferredStateFromOtherFields());
  }

  /**
   * Invoked whenever a Signal PIN user enables registration lock.
   */
  @WorkerThread
  public static synchronized void onEnableRegistrationLockForUserWithPin() throws IOException {
    Log.i(TAG, "onEnableRegistrationLockForUserWithPin()");

    if (getState() == State.PIN_WITH_REGISTRATION_LOCK_ENABLED) {
      Log.i(TAG, "Registration lock already enabled. Skipping.");
      return;
    }

    assertState(State.PIN_WITH_REGISTRATION_LOCK_DISABLED);

    SignalStore.kbsValues().setV2RegistrationLockEnabled(false);
    ApplicationDependencies.getKeyBackupService()
                           .newPinChangeSession(SignalStore.kbsValues().getRegistrationLockTokenResponse())
                           .enableRegistrationLock(SignalStore.kbsValues().getOrCreateMasterKey());
    SignalStore.kbsValues().setV2RegistrationLockEnabled(true);

    updateState(State.PIN_WITH_REGISTRATION_LOCK_ENABLED);
  }

  /**
   * Invoked whenever a Signal PIN user disables registration lock.
   */
  @WorkerThread
  public static synchronized void onDisableRegistrationLockForUserWithPin() throws IOException {
    Log.i(TAG, "onDisableRegistrationLockForUserWithPin()");

    if (getState() == State.PIN_WITH_REGISTRATION_LOCK_DISABLED) {
      Log.i(TAG, "Registration lock already disabled. Skipping.");
      return;
    }

    assertState(State.PIN_WITH_REGISTRATION_LOCK_ENABLED);

    SignalStore.kbsValues().setV2RegistrationLockEnabled(true);
    ApplicationDependencies.getKeyBackupService()
                           .newPinChangeSession(SignalStore.kbsValues().getRegistrationLockTokenResponse())
                           .disableRegistrationLock();
    SignalStore.kbsValues().setV2RegistrationLockEnabled(false);

    updateState(State.PIN_WITH_REGISTRATION_LOCK_DISABLED);
  }

  /**
   * Should only be called by {@link com.tapmedia.yoush.migrations.RegistrationPinV2MigrationJob}.
   */
  @WorkerThread
  public static synchronized void onMigrateToRegistrationLockV2(@NonNull Context context, @NonNull String pin)
      throws IOException, UnauthenticatedResponseException
  {
    Log.i(TAG, "onMigrateToRegistrationLockV2()");

    KbsValues                         kbsValues        = SignalStore.kbsValues();
    MasterKey                         masterKey        = kbsValues.getOrCreateMasterKey();
    KeyBackupService                  keyBackupService = ApplicationDependencies.getKeyBackupService();
    KeyBackupService.PinChangeSession pinChangeSession = keyBackupService.newPinChangeSession();
    HashedPin                         hashedPin        = PinHashing.hashPin(pin, pinChangeSession);
    KbsPinData                        kbsData          = pinChangeSession.setPin(hashedPin, masterKey);

    pinChangeSession.enableRegistrationLock(masterKey);

    kbsValues.setKbsMasterKey(kbsData, pin);
    TextSecurePreferences.clearRegistrationLockV1(context);

    updateState(buildInferredStateFromOtherFields());
  }

  @WorkerThread
  private static void bestEffortRefreshAttributes() {
    Optional<JobTracker.JobState> result = ApplicationDependencies.getJobManager().runSynchronously(new RefreshAttributesJob(), TimeUnit.SECONDS.toMillis(10));

    if (result.isPresent() && result.get() == JobTracker.JobState.SUCCESS) {
      Log.i(TAG, "Attributes were refreshed successfully.");
    } else if (result.isPresent()) {
      Log.w(TAG, "Attribute refresh finished, but was not successful. Enqueuing one for later. (Result: " + result.get() + ")");
      ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
    } else {
      Log.w(TAG, "Job did not finish in the allotted time. It'll finish later.");
    }
  }

  @WorkerThread
  private static void resetPinRetryCount(@NonNull Context context, @Nullable String pin, @NonNull KbsPinData kbsData) {
    if (pin == null) {
      return;
    }

    KeyBackupService keyBackupService = ApplicationDependencies.getKeyBackupService();

    try {
      KbsValues                         kbsValues        = SignalStore.kbsValues();
      MasterKey                         masterKey        = kbsValues.getOrCreateMasterKey();
      KeyBackupService.PinChangeSession pinChangeSession = keyBackupService.newPinChangeSession(kbsData.getTokenResponse());
      HashedPin                         hashedPin        = PinHashing.hashPin(pin, pinChangeSession);
      KbsPinData                        newData          = pinChangeSession.setPin(hashedPin, masterKey);

      kbsValues.setKbsMasterKey(newData, pin);
      TextSecurePreferences.clearRegistrationLockV1(context);

      Log.i(TAG, "Pin set/attempts reset on KBS");
    } catch (IOException e) {
      Log.w(TAG, "May have failed to reset pin attempts!", e);
    } catch (UnauthenticatedResponseException e) {
      Log.w(TAG, "Failed to reset pin attempts", e);
    }
  }

  @WorkerThread
  private static void setPin(@NonNull Context context, @NonNull String pin, @NonNull PinKeyboardType keyboard)
      throws IOException, UnauthenticatedResponseException
  {
    KbsValues                         kbsValues        = SignalStore.kbsValues();
    MasterKey                         masterKey        = kbsValues.getOrCreateMasterKey();
    KeyBackupService                  keyBackupService = ApplicationDependencies.getKeyBackupService();
    KeyBackupService.PinChangeSession pinChangeSession = keyBackupService.newPinChangeSession();
    HashedPin                         hashedPin        = PinHashing.hashPin(pin, pinChangeSession);
    KbsPinData                        kbsData          = pinChangeSession.setPin(hashedPin, masterKey);

    kbsValues.setKbsMasterKey(kbsData, pin);
    TextSecurePreferences.clearRegistrationLockV1(context);
    SignalStore.pinValues().setKeyboardType(keyboard);
    SignalStore.pinValues().resetPinReminders();
    ApplicationDependencies.getMegaphoneRepository().markFinished(Megaphones.Event.PINS_FOR_ALL);
  }

  @WorkerThread
  private static void optOutOfPin(@NonNull Context context)
      throws IOException, UnauthenticatedResponseException
  {
    SignalStore.kbsValues().resetMasterKey();

    setPin(context, Hex.toStringCondensed(Util.getSecretBytes(32)), PinKeyboardType.ALPHA_NUMERIC);
    SignalStore.kbsValues().optOut();

    ApplicationDependencies.getJobManager().add(new StorageForcePushJob());
    bestEffortRefreshAttributes();
  }

  private static @NonNull State assertState(State... allowed) {
    State currentState = getState();

    for (State state : allowed) {
      if (currentState == state) {
        return currentState;
      }
    }

    switch (currentState) {
      case NO_REGISTRATION_LOCK:                throw new InvalidState_NoRegistrationLock();
      case REGISTRATION_LOCK_V1:                throw new InvalidState_RegistrationLockV1();
      case PIN_WITH_REGISTRATION_LOCK_ENABLED:  throw new InvalidState_PinWithRegistrationLockEnabled();
      case PIN_WITH_REGISTRATION_LOCK_DISABLED: throw new InvalidState_PinWithRegistrationLockDisabled();
      case PIN_OPT_OUT:                         throw new InvalidState_PinOptOut();
      default:                                  throw new IllegalStateException("Expected: " + Arrays.toString(allowed) + ", Actual: " + currentState);
    }
  }

  private static @NonNull State getState() {
    String serialized = SignalStore.pinValues().getPinState();

    if (serialized != null) {
      return State.deserialize(serialized);
    } else {
      State state = buildInferredStateFromOtherFields();
      SignalStore.pinValues().setPinState(state.serialize());
      return state;
    }
  }

  private static void updateState(@NonNull State state) {
    Log.i(TAG, "Updating state to: " + state);
    SignalStore.pinValues().setPinState(state.serialize());
  }

  private static @NonNull State buildInferredStateFromOtherFields() {
    Context   context   = ApplicationDependencies.getApplication();
    KbsValues kbsValues = SignalStore.kbsValues();

    boolean v1Enabled = TextSecurePreferences.isV1RegistrationLockEnabled(context);
    boolean v2Enabled = kbsValues.isV2RegistrationLockEnabled();
    boolean hasPin    = kbsValues.hasPin();
    boolean optedOut  = kbsValues.hasOptedOut();

    if (optedOut && !v2Enabled && !v1Enabled) {
      return State.PIN_OPT_OUT;
    }

    if (!v1Enabled && !v2Enabled && !hasPin) {
      return State.NO_REGISTRATION_LOCK;
    }

    if (v1Enabled && !v2Enabled && !hasPin) {
      return State.REGISTRATION_LOCK_V1;
    }

    if (v2Enabled && hasPin) {
      TextSecurePreferences.setV1RegistrationLockEnabled(context, false);
      return State.PIN_WITH_REGISTRATION_LOCK_ENABLED;
    }

    if (!v2Enabled && hasPin) {
      TextSecurePreferences.setV1RegistrationLockEnabled(context, false);
      return State.PIN_WITH_REGISTRATION_LOCK_DISABLED;
    }

    throw new InvalidInferredStateError(String.format(Locale.ENGLISH, "Invalid state! v1: %b, v2: %b, pin: %b", v1Enabled, v2Enabled, hasPin));
  }

  private enum State {
    /**
     * User has nothing -- either in the process of registration, or pre-PIN-migration
     */
    NO_REGISTRATION_LOCK("no_registration_lock"),

    /**
     * User has a V1 registration lock set
     */
    REGISTRATION_LOCK_V1("registration_lock_v1"),

    /**
     * User has a PIN, and registration lock is enabled.
     */
    PIN_WITH_REGISTRATION_LOCK_ENABLED("pin_with_registration_lock_enabled"),

    /**
     * User has a PIN, but registration lock is disabled.
     */
    PIN_WITH_REGISTRATION_LOCK_DISABLED("pin_with_registration_lock_disabled"),

    /**
     * The user has opted out of creating a PIN. In this case, we will generate a high-entropy PIN
     * on their behalf.
     */
    PIN_OPT_OUT("pin_opt_out");

    /**
     * Using a string key so that people can rename/reorder values in the future without breaking
     * serialization.
     */
    private final String key;

    State(String key) {
      this.key = key;
    }

    public @NonNull String serialize() {
      return key;
    }

    public static @NonNull State deserialize(@NonNull String serialized) {
      for (State state : values()) {
        if (state.key.equals(serialized)) {
          return state;
        }
      }
      throw new IllegalArgumentException("No state for value: " + serialized);
    }
  }

  private static class InvalidInferredStateError extends Error {
    InvalidInferredStateError(String message) {
      super(message);
    }
  }

  private static class InvalidState_NoRegistrationLock extends IllegalStateException {}
  private static class InvalidState_RegistrationLockV1 extends IllegalStateException {}
  private static class InvalidState_PinWithRegistrationLockEnabled extends IllegalStateException {}
  private static class InvalidState_PinWithRegistrationLockDisabled extends IllegalStateException {}
  private static class InvalidState_PinOptOut extends IllegalStateException {}
}

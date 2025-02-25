package com.tapmedia.yoush.contacts.sync;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import com.tapmedia.yoush.BuildConfig;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.contacts.ContactAccessor;
import com.tapmedia.yoush.contacts.ContactsDatabase;
import com.tapmedia.yoush.crypto.SessionUtil;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.MessagingDatabase.InsertResult;
import com.tapmedia.yoush.database.RecipientDatabase;
import com.tapmedia.yoush.database.RecipientDatabase.BulkOperationsHandle;
import com.tapmedia.yoush.database.RecipientDatabase.RegisteredState;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobs.MultiDeviceContactUpdateJob;
import com.tapmedia.yoush.jobs.StorageSyncJob;
import com.tapmedia.yoush.keyvalue.SignalStore;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.notifications.NotificationChannels;
import com.tapmedia.yoush.permissions.Permissions;
import com.tapmedia.yoush.phonenumbers.PhoneNumberFormatter;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.storage.StorageSyncHelper;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.sms.IncomingJoinedMessage;
import com.tapmedia.yoush.util.FeatureFlags;
import com.tapmedia.yoush.util.ProfileUtil;
import com.tapmedia.yoush.util.SetUtil;
import com.tapmedia.yoush.util.TextSecurePreferences;
import com.tapmedia.yoush.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Manages all the stuff around determining if a user is registered or not.
 */
public class DirectoryHelper {

  private static final String TAG = Log.tag(DirectoryHelper.class);

  @WorkerThread
  public static void refreshDirectory(@NonNull Context context, boolean notifyOfNewUsers) throws IOException {
    if (TextUtils.isEmpty(TextSecurePreferences.getLocalNumber(context))) {
      Log.i(TAG, "Have not yet set our own local number. Skipping.");
      return;
    }

    if (!Permissions.hasAll(context, Manifest.permission.WRITE_CONTACTS)) {
      Log.i(TAG, "No contact permissions. Skipping.");
      return;
    }

    RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    Set<String>       databaseNumbers   = sanitizeNumbers(recipientDatabase.getAllPhoneNumbers());
    Set<String>       systemNumbers     = sanitizeNumbers(ContactAccessor.getInstance().getAllContactsWithNumbers(context));
    Set<String>       allNumbers        = SetUtil.union(databaseNumbers, systemNumbers);

    DirectoryResult result;

    if (FeatureFlags.cds()) {
      result = ContactDiscoveryV2.getDirectoryResult(context, databaseNumbers, systemNumbers);
    } else {
      result = ContactDiscoveryV1.getDirectoryResult(databaseNumbers, systemNumbers);
    }

    if (result.getNumberRewrites().size() > 0) {
      Log.i(TAG, "[getDirectoryResult] Need to rewrite some numbers.");
      recipientDatabase.updatePhoneNumbers(result.getNumberRewrites());
    }

    HashMap<RecipientId, String> uuidMap = new HashMap<>();

    // TODO [greyson] [cds] Probably want to do this in a DB transaction to prevent concurrent operations
    for (Map.Entry<String, UUID> entry : result.getRegisteredNumbers().entrySet()) {
      // TODO [greyson] [cds] This is where we'll have to do record merging
      String                e164      = entry.getKey();
      UUID                  uuid      = entry.getValue();
      Optional<RecipientId> uuidEntry = uuid != null ? recipientDatabase.getByUuid(uuid) : Optional.absent();

      // TODO [greyson] [cds] Handle phone numbers changing, possibly conflicting
      if (uuidEntry.isPresent()) {
        recipientDatabase.setPhoneNumber(uuidEntry.get(), e164);
      }

      RecipientId id = uuidEntry.isPresent() ? uuidEntry.get() : recipientDatabase.getOrInsertFromE164(e164);

      uuidMap.put(id, uuid != null ? uuid.toString() : null);
    }

    Set<String>      activeNumbers = result.getRegisteredNumbers().keySet();
    Set<RecipientId> activeIds     = uuidMap.keySet();
    Set<RecipientId> inactiveIds   = Stream.of(allNumbers)
                                           .filterNot(activeNumbers::contains)
                                           .filterNot(n -> result.getNumberRewrites().containsKey(n))
                                           .map(recipientDatabase::getOrInsertFromE164)
                                           .collect(Collectors.toSet());

    recipientDatabase.bulkUpdatedRegisteredStatus(uuidMap, inactiveIds);

    updateContactsDatabase(context, activeIds, true, result.getNumberRewrites());

    if (TextSecurePreferences.isMultiDevice(context)) {
      ApplicationDependencies.getJobManager().add(new MultiDeviceContactUpdateJob());
    }

    if (TextSecurePreferences.hasSuccessfullyRetrievedDirectory(context) && notifyOfNewUsers) {
      Set<RecipientId>  existingSignalIds = new HashSet<>(recipientDatabase.getRegistered());
      Set<RecipientId>  existingSystemIds = new HashSet<>(recipientDatabase.getSystemContacts());
      Set<RecipientId>  newlyActiveIds    = new HashSet<>(activeIds);

      newlyActiveIds.removeAll(existingSignalIds);
      newlyActiveIds.retainAll(existingSystemIds);

      notifyNewUsers(context, newlyActiveIds);
    } else {
      TextSecurePreferences.setHasSuccessfullyRetrievedDirectory(context, true);
    }

    StorageSyncHelper.scheduleSyncForDataChange();
  }

  @WorkerThread
  public static RegisteredState refreshDirectoryFor(@NonNull Context context, @NonNull Recipient recipient, boolean notifyOfNewUsers) throws IOException {
    RecipientDatabase recipientDatabase       = DatabaseFactory.getRecipientDatabase(context);
    RegisteredState   originalRegisteredState = recipient.resolve().getRegistered();
    RegisteredState   newRegisteredState      = null;

    if (recipient.getUuid().isPresent()) {
      boolean isRegistered = isUuidRegistered(context, recipient);
      if (isRegistered) {
        recipientDatabase.markRegistered(recipient.getId(), recipient.getUuid().get());
      } else {
        recipientDatabase.markUnregistered(recipient.getId());
      }

      return isRegistered ? RegisteredState.REGISTERED : RegisteredState.NOT_REGISTERED;
    }

    if (!recipient.getE164().isPresent()) {
      Log.w(TAG, "No UUID or E164?");
      return RegisteredState.NOT_REGISTERED;
    }

    DirectoryResult result;

    if (FeatureFlags.cds()) {
      result = ContactDiscoveryV2.getDirectoryResult(context, recipient.getE164().get());
    } else {
      result = ContactDiscoveryV1.getDirectoryResult(recipient.getE164().get());
    }

    if (result.getNumberRewrites().size() > 0) {
      Log.i(TAG, "[getDirectoryResult] Need to rewrite some numbers.");
      recipientDatabase.updatePhoneNumbers(result.getNumberRewrites());
    }

    if (result.getRegisteredNumbers().size() > 0) {
      recipientDatabase.markRegistered(recipient.getId(), result.getRegisteredNumbers().values().iterator().next());
    } else {
      recipientDatabase.markUnregistered(recipient.getId());
    }

    if (Permissions.hasAll(context, Manifest.permission.WRITE_CONTACTS)) {
      updateContactsDatabase(context, Collections.singletonList(recipient.getId()), false, result.getNumberRewrites());
    }

    newRegisteredState = result.getRegisteredNumbers().size() > 0 ? RegisteredState.REGISTERED : RegisteredState.NOT_REGISTERED;

    if (newRegisteredState != originalRegisteredState) {
      ApplicationDependencies.getJobManager().add(new MultiDeviceContactUpdateJob());
      ApplicationDependencies.getJobManager().add(new StorageSyncJob());

      if (notifyOfNewUsers && newRegisteredState == RegisteredState.REGISTERED && recipient.resolve().isSystemContact()) {
        notifyNewUsers(context, Collections.singletonList(recipient.getId()));
      }

      StorageSyncHelper.scheduleSyncForDataChange();
    }

    return newRegisteredState;
  }

  private static boolean isUuidRegistered(@NonNull Context context, @NonNull Recipient recipient) throws IOException {
    try {
      ProfileUtil.retrieveProfile(context, recipient, SignalServiceProfile.RequestType.PROFILE).get(10, TimeUnit.SECONDS);
      return true;
    } catch (ExecutionException e) {
      if (e.getCause() instanceof NotFoundException) {
        return false;
      } else {
        throw new IOException(e);
      }
    } catch (InterruptedException | TimeoutException e) {
      throw new IOException(e);
    }
  }

  private static void updateContactsDatabase(@NonNull Context context,
                                             @NonNull Collection<RecipientId> activeIds,
                                             boolean removeMissing,
                                             @NonNull Map<String, String> rewrites)
  {
    AccountHolder account = getOrCreateSystemAccount(context);

    if (account == null) {
      Log.w(TAG, "Failed to create an account!");
      return;
    }

    try {
      RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
      ContactsDatabase  contactsDatabase  = DatabaseFactory.getContactsDatabase(context);
      List<String>      activeAddresses   = Stream.of(activeIds)
                                                  .map(Recipient::resolved)
                                                  .filter(Recipient::hasE164)
                                                  .map(Recipient::requireE164)
                                                  .toList();

      contactsDatabase.removeDeletedRawContacts(account.getAccount());
      contactsDatabase.setRegisteredUsers(account.getAccount(), activeAddresses, removeMissing);

      Cursor               cursor = ContactAccessor.getInstance().getAllSystemContacts(context);
      BulkOperationsHandle handle = recipientDatabase.beginBulkSystemContactUpdate();

      try {
        while (cursor != null && cursor.moveToNext()) {
          String number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));

          if (isValidContactNumber(number)) {
            String      formattedNumber = PhoneNumberFormatter.get(context).format(number);
            String      realNumber      = Util.getFirstNonEmpty(rewrites.get(formattedNumber), formattedNumber);
            RecipientId recipientId     = Recipient.externalContact(context, realNumber).getId();
            String      displayName     = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
            String      contactPhotoUri = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI));
            String      contactLabel    = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL));
            int         phoneType       = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE));
            Uri         contactUri      = ContactsContract.Contacts.getLookupUri(cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone._ID)),
                                                                                 cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)));

            handle.setSystemContactInfo(recipientId, displayName, contactPhotoUri, contactLabel, phoneType, contactUri.toString());
          }
        }
      } finally {
        handle.finish();
      }

      if (NotificationChannels.supported()) {
        try (RecipientDatabase.RecipientReader recipients = DatabaseFactory.getRecipientDatabase(context).getRecipientsWithNotificationChannels()) {
          Recipient recipient;
          while ((recipient = recipients.getNext()) != null) {
            NotificationChannels.updateContactChannelName(context, recipient);
          }
        }
      }
    } catch (RemoteException | OperationApplicationException e) {
      Log.w(TAG, "Failed to update contacts.", e);
    }
  }

  private static boolean isValidContactNumber(@Nullable String number) {
    return !TextUtils.isEmpty(number) && !UuidUtil.isUuid(number);
  }

  private static @Nullable AccountHolder getOrCreateSystemAccount(Context context) {
    AccountManager accountManager = AccountManager.get(context);
    Account[]      accounts       = accountManager.getAccountsByType(BuildConfig.APPLICATION_ID);

    AccountHolder account;

    if (accounts.length == 0) {
      account = createAccount(context);
    } else {
      account = new AccountHolder(accounts[0], false);
    }

    if (account != null && !ContentResolver.getSyncAutomatically(account.getAccount(), ContactsContract.AUTHORITY)) {
      ContentResolver.setSyncAutomatically(account.getAccount(), ContactsContract.AUTHORITY, true);
    }

    return account;
  }

  private static @Nullable AccountHolder createAccount(Context context) {
    AccountManager accountManager = AccountManager.get(context);
    Account        account        = new Account(context.getString(R.string.app_name), BuildConfig.APPLICATION_ID);

    if (accountManager.addAccountExplicitly(account, null, null)) {
      Log.i(TAG, "Created new account...");
      ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
      return new AccountHolder(account, true);
    } else {
      Log.w(TAG, "Failed to create account!");
      return null;
    }
  }

  private static void notifyNewUsers(@NonNull  Context context,
                                     @NonNull  Collection<RecipientId> newUsers)
  {
    if (!TextSecurePreferences.isNewContactsNotificationEnabled(context)) return;

    for (RecipientId newUser: newUsers) {
      Recipient recipient = Recipient.resolved(newUser);
      if (!SessionUtil.hasSession(context, recipient.getId()) && !recipient.isLocalNumber()) {
        IncomingJoinedMessage  message      = new IncomingJoinedMessage(newUser);
        Optional<InsertResult> insertResult = DatabaseFactory.getSmsDatabase(context).insertMessageInbox(message);

        if (insertResult.isPresent()) {
          int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
          if (hour >= 9 && hour < 23) {
            ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId(), true);
          } else {
            Log.i(TAG, "Not notifying of a new user due to the time of day. (Hour: " + hour + ")");
          }
        }
      }
    }
  }

  private static Set<String> sanitizeNumbers(@NonNull Set<String> numbers) {
    return Stream.of(numbers).filter(number -> {
      try {
        return number.startsWith("+") && number.length() > 1 && number.charAt(1) != '0' && Long.parseLong(number.substring(1)) > 0;
      } catch (NumberFormatException e) {
        return false;
      }
    }).collect(Collectors.toSet());
  }

  static class DirectoryResult {
    private final Map<String, UUID>   registeredNumbers;
    private final Map<String, String> numberRewrites;

    DirectoryResult(@NonNull Map<String, UUID> registeredNumbers,
                    @NonNull Map<String, String> numberRewrites)
    {
      this.registeredNumbers = registeredNumbers;
      this.numberRewrites    = numberRewrites;
    }


    @NonNull Map<String, UUID> getRegisteredNumbers() {
      return registeredNumbers;
    }

    @NonNull Map<String, String> getNumberRewrites() {
      return numberRewrites;
    }
  }

  private static class AccountHolder {
    private final boolean fresh;
    private final Account account;

    private AccountHolder(Account account, boolean fresh) {
      this.fresh   = fresh;
      this.account = account;
    }

    @SuppressWarnings("unused")
    public boolean isFresh() {
      return fresh;
    }

    public Account getAccount() {
      return account;
    }
  }
}

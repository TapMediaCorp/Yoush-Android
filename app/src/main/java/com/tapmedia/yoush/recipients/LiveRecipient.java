package com.tapmedia.yoush.recipients;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.annimon.stream.Stream;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.GroupDatabase;
import com.tapmedia.yoush.database.GroupDatabase.GroupRecord;
import com.tapmedia.yoush.database.RecipientDatabase;
import com.tapmedia.yoush.database.RecipientDatabase.RecipientSettings;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.util.TextSecurePreferences;
import com.tapmedia.yoush.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;

public final class LiveRecipient implements Serializable {

  private static final String TAG = Log.tag(LiveRecipient.class);

  private final Context                       context;
  private final MutableLiveData<Recipient>    liveData;
  private final Set<RecipientForeverObserver> observers;
  private final Observer<Recipient>           foreverObserver;
  private final AtomicReference<Recipient>    recipient;
  private final RecipientDatabase             recipientDatabase;
  private final GroupDatabase                 groupDatabase;

  LiveRecipient(@NonNull Context context, @NonNull MutableLiveData<Recipient> liveData, @NonNull Recipient defaultRecipient) {
    this.context           = context.getApplicationContext();
    this.liveData          = liveData;
    this.recipient         = new AtomicReference<>(defaultRecipient);
    this.recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    this.groupDatabase     = DatabaseFactory.getGroupDatabase(context);
    this.observers         = new CopyOnWriteArraySet<>();
    this.foreverObserver   = recipient -> {
      for (RecipientForeverObserver o : observers) {
        o.onRecipientChanged(recipient);
      }
    };
  }

  public @NonNull RecipientId getId() {
    return recipient.get().getId();
  }

  /**
   * @return A recipient that may or may not be fully-resolved.
   */
  public @NonNull Recipient get() {
    return recipient.get();
  }

  /**
   * Watch the recipient for changes. The callback will only be invoked if the provided lifecycle is
   * in a valid state. No need to remove the observer. If you do wish to remove the observer (if,
   * for instance, you wish to remove the listener before the end of the owner's lifecycle), you can
   * use {@link #removeObservers(LifecycleOwner)}.
   */
  public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<Recipient> observer) {
    Util.postToMain(() -> liveData.observe(owner, observer));
  }

  /**
   * Removes all observers of this data registered for the given LifecycleOwner.
   */
  public void removeObservers(@NonNull LifecycleOwner owner) {
    Util.runOnMain(() -> liveData.removeObservers(owner));
  }

  /**
   * Watch the recipient for changes. The callback could be invoked at any time. You MUST call
   * {@link #removeForeverObserver(RecipientForeverObserver)} when finished. You should use
   * {@link #observe(LifecycleOwner, Observer<Recipient>)} if possible, as it is lifecycle-safe.
   */
  public void observeForever(@NonNull RecipientForeverObserver observer) {
    Util.postToMain(() -> {
      observers.add(observer);

      if (observers.size() == 1) {
        liveData.observeForever(foreverObserver);
      }
    });
  }

  /**
   * Unsubscribes the provided {@link RecipientForeverObserver} from future changes.
   */
  public void removeForeverObserver(@NonNull RecipientForeverObserver observer) {
    Util.postToMain(() -> {
      observers.remove(observer);

      if (observers.isEmpty()) {
        liveData.removeObserver(foreverObserver);
      }
    });
  }

  /**
   * @return A fully-resolved version of the recipient. May require reading from disk.
   */
  @WorkerThread
  public @NonNull Recipient resolve() {
    Recipient current = recipient.get();

    if (!current.isResolving() || current.getId().isUnknown()) {
      return current;
    }

    if (Util.isMainThread()) {
      Log.w(TAG, "[Resolve][MAIN] " + getId(), new Throwable());
    }

    Recipient       updated      = fetchAndCacheRecipientFromDisk(getId());
    List<Recipient> participants = Stream.of(updated.getParticipants())
                                         .filter(Recipient::isResolving)
                                         .map(Recipient::getId)
                                         .map(this::fetchAndCacheRecipientFromDisk)
                                         .toList();

    for (Recipient participant : participants) {
      participant.live().set(participant);
    }

    set(updated);

    return updated;
  }

  /**
   * Forces a reload of the underlying recipient.
   */
  @WorkerThread
  public void refresh() {
    if (getId().isUnknown()) return;

    if (Util.isMainThread()) {
      Log.w(TAG, "[Refresh][MAIN] " + getId(), new Throwable());
    }

    Recipient       recipient    = fetchAndCacheRecipientFromDisk(getId());
    List<Recipient> participants = Stream.of(recipient.getParticipants())
                                         .map(Recipient::getId)
                                         .map(this::fetchAndCacheRecipientFromDisk)
                                         .toList();

    for (Recipient participant : participants) {
      participant.live().set(participant);
    }

    set(recipient);
  }

  public @NonNull LiveData<Recipient> getLiveData() {
    return liveData;
  }

  private @NonNull Recipient fetchAndCacheRecipientFromDisk(@NonNull RecipientId id) {
    RecipientSettings settings = recipientDatabase.getRecipientSettings(id);
    RecipientDetails  details  = settings.getGroupId() != null ? getGroupRecipientDetails(settings)
                                                               : RecipientDetails.forIndividual(context, settings);

    Recipient recipient = new Recipient(id, details, true);
    RecipientIdCache.INSTANCE.put(recipient);
    return recipient;
  }

  @WorkerThread
  private @NonNull RecipientDetails getGroupRecipientDetails(@NonNull RecipientSettings settings) {
    Optional<GroupRecord> groupRecord = groupDatabase.getGroup(settings.getId());

    if (groupRecord.isPresent()) {
      String          title    = groupRecord.get().getTitle();
      List<Recipient> members  = Stream.of(groupRecord.get().getMembers()).filterNot(RecipientId::isUnknown).map(this::fetchAndCacheRecipientFromDisk).toList();
      Optional<Long>  avatarId = Optional.absent();

      if (groupRecord.get().hasAvatar()) {
        avatarId = Optional.of(groupRecord.get().getAvatarId());
      }

      return new RecipientDetails(title, avatarId, false, false, settings, members);
    }

    return new RecipientDetails(null, Optional.absent(), false, false, settings, null);
  }

  synchronized void set(@NonNull Recipient recipient) {
    this.recipient.set(recipient);
    this.liveData.postValue(recipient);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LiveRecipient that = (LiveRecipient) o;
    return recipient.equals(that.recipient);
  }

  @Override
  public int hashCode() {
    return Objects.hash(recipient);
  }
}

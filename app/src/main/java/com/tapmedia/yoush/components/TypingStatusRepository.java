package com.tapmedia.yoush.components;

import android.annotation.SuppressLint;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import android.content.Context;
import androidx.annotation.NonNull;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@SuppressLint("UseSparseArrays")
public class TypingStatusRepository {

  private static final String TAG = TypingStatusRepository.class.getSimpleName();

  private static final long RECIPIENT_TYPING_TIMEOUT = TimeUnit.SECONDS.toMillis(15);

  private final Map<Long, Set<Typist>>                  typistMap;
  private final Map<Typist, Runnable>                   timers;
  private final Map<Long, MutableLiveData<TypingState>> notifiers;
  private final MutableLiveData<Set<Long>>              threadsNotifier;

  public TypingStatusRepository() {
    this.typistMap       = new HashMap<>();
    this.timers          = new HashMap<>();
    this.notifiers       = new HashMap<>();
    this.threadsNotifier = new MutableLiveData<>();
  }

  public synchronized void onTypingStarted(@NonNull Context context, long threadId, @NonNull Recipient author, int device) {
    if (author.isLocalNumber()) {
      return;
    }

    Set<Typist> typists = Util.getOrDefault(typistMap, threadId, new LinkedHashSet<>());
    Typist      typist  = new Typist(author, device, threadId);

    if (!typists.contains(typist)) {
      typists.add(typist);
      typistMap.put(threadId, typists);
      notifyThread(threadId, typists, false);
    }

    Runnable timer = timers.get(typist);
    if (timer != null) {
      Util.cancelRunnableOnMain(timer);
    }

    timer = () -> onTypingStopped(context, threadId, author, device, false);
    Util.runOnMainDelayed(timer, RECIPIENT_TYPING_TIMEOUT);
    timers.put(typist, timer);
  }

  public synchronized void onTypingStopped(@NonNull Context context, long threadId, @NonNull Recipient author, int device, boolean isReplacedByIncomingMessage) {
    if (author.isLocalNumber()) {
      return;
    }

    Set<Typist> typists = Util.getOrDefault(typistMap, threadId, new LinkedHashSet<>());
    Typist      typist  = new Typist(author, device, threadId);

    if (typists.contains(typist)) {
      typists.remove(typist);
      notifyThread(threadId, typists, isReplacedByIncomingMessage);
    }

    if (typists.isEmpty()) {
      typistMap.remove(threadId);
    }

    Runnable timer = timers.get(typist);
    if (timer != null) {
      Util.cancelRunnableOnMain(timer);
      timers.remove(typist);
    }
  }

  public synchronized LiveData<TypingState> getTypists(long threadId) {
    MutableLiveData<TypingState> notifier = Util.getOrDefault(notifiers, threadId, new MutableLiveData<>());
    notifiers.put(threadId, notifier);
    return notifier;
  }

  public synchronized LiveData<Set<Long>> getTypingThreads() {
    return threadsNotifier;
  }

  public synchronized void clear() {
    TypingState empty = new TypingState(Collections.emptyList(), false);
    for (MutableLiveData<TypingState> notifier : notifiers.values()) {
      notifier.postValue(empty);
    }
    
    notifiers.clear();
    typistMap.clear();
    timers.clear();

    threadsNotifier.postValue(Collections.emptySet());
  }

  private void notifyThread(long threadId, @NonNull Set<Typist> typists, boolean isReplacedByIncomingMessage) {
    Log.d(TAG, "notifyThread() threadId: " + threadId + "  typists: " + typists.size() + "  isReplaced: " + isReplacedByIncomingMessage);

    MutableLiveData<TypingState> notifier = Util.getOrDefault(notifiers, threadId, new MutableLiveData<>());
    notifiers.put(threadId, notifier);

    Set<Recipient> uniqueTypists = new LinkedHashSet<>();
    for (Typist typist : typists) {
      uniqueTypists.add(typist.getAuthor());
    }

    notifier.postValue(new TypingState(new ArrayList<>(uniqueTypists), isReplacedByIncomingMessage));

    Set<Long> activeThreads = Stream.of(typistMap.keySet()).filter(t -> !typistMap.get(t).isEmpty()).collect(Collectors.toSet());
    threadsNotifier.postValue(activeThreads);
  }

  public static class TypingState {
    private final List<Recipient> typists;
    private final boolean         replacedByIncomingMessage;

    public TypingState(List<Recipient> typists, boolean replacedByIncomingMessage) {
      this.typists                   = typists;
      this.replacedByIncomingMessage = replacedByIncomingMessage;
    }

    public List<Recipient> getTypists() {
      return typists;
    }

    public boolean isReplacedByIncomingMessage() {
      return replacedByIncomingMessage;
    }
  }

  private static class Typist {
    private final Recipient author;
    private final int       device;
    private final long      threadId;

    private Typist(@NonNull Recipient author, int device, long threadId) {
      this.author   = author;
      this.device   = device;
      this.threadId = threadId;
    }

    public Recipient getAuthor() {
      return author;
    }

    public int getDevice() {
      return device;
    }

    public long getThreadId() {
      return threadId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Typist typist = (Typist) o;

      if (device != typist.device) return false;
      if (threadId != typist.threadId) return false;
      return author.equals(typist.author);
    }

    @Override
    public int hashCode() {
      int result = author.hashCode();
      result = 31 * result + device;
      result = 31 * result + (int) (threadId ^ (threadId >>> 32));
      return result;
    }
  }
}

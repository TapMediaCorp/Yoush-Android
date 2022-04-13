package com.tapmedia.yoush.conversationlist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.snackbar.Snackbar;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;
import com.tapmedia.yoush.ApplicationContext;
import com.tapmedia.yoush.ApplicationPreferencesActivity;
import com.tapmedia.yoush.ContactSelectionActivity;
import com.tapmedia.yoush.MainNavigator;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.components.recyclerview.DeleteItemAnimator;
import com.tapmedia.yoush.components.reminder.DefaultSmsReminder;
import com.tapmedia.yoush.components.reminder.DozeReminder;
import com.tapmedia.yoush.components.reminder.ExpiredBuildReminder;
import com.tapmedia.yoush.components.reminder.OutdatedBuildReminder;
import com.tapmedia.yoush.components.reminder.PushRegistrationReminder;
import com.tapmedia.yoush.components.reminder.Reminder;
import com.tapmedia.yoush.components.reminder.ReminderView;
import com.tapmedia.yoush.components.reminder.ServiceOutageReminder;
import com.tapmedia.yoush.components.reminder.ShareReminder;
import com.tapmedia.yoush.components.reminder.SystemSmsImportReminder;
import com.tapmedia.yoush.components.reminder.UnauthorizedReminder;
import com.tapmedia.yoush.conversation.ConversationFragment;
import com.tapmedia.yoush.conversationlist.action.ActionBindJob;
import com.tapmedia.yoush.conversationlist.action.ActionData;
import com.tapmedia.yoush.conversationlist.adapter.ConversationListDefaultAdapter;
import com.tapmedia.yoush.conversationlist.adapter.ConversationListItemEventListener;
import com.tapmedia.yoush.conversationlist.adapter.ConversationListSearchAdapter;
import com.tapmedia.yoush.conversationlist.model.MessageResult;
import com.tapmedia.yoush.conversationlist.model.SearchResult;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.ThreadDatabase;
import com.tapmedia.yoush.database.model.ThreadRecord;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.events.ReminderUpdateEvent;
import com.tapmedia.yoush.insights.InsightsLauncher;
import com.tapmedia.yoush.jobs.ServiceOutageDetectionJob;
import com.tapmedia.yoush.keyvalue.SignalStore;
import com.tapmedia.yoush.lock.v2.CreateKbsPinActivity;
import com.tapmedia.yoush.megaphone.Megaphone;
import com.tapmedia.yoush.megaphone.MegaphoneActionController;
import com.tapmedia.yoush.megaphone.MegaphoneViewBuilder;
import com.tapmedia.yoush.megaphone.Megaphones;
import com.tapmedia.yoush.mms.GlideApp;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.sms.MessageSender;
import com.tapmedia.yoush.ui.main.MainFragment;
import com.tapmedia.yoush.ui.pin.PinAuthFragment;
import com.tapmedia.yoush.ui.pin.PinCreateFragment;
import com.tapmedia.yoush.util.AvatarUtil;
import com.tapmedia.yoush.util.SnapToTopDataObserver;
import com.tapmedia.yoush.util.StickyHeaderDecoration;
import com.tapmedia.yoush.util.TextSecurePreferences;
import com.tapmedia.yoush.util.Util;
import com.tapmedia.yoush.util.ViewUtil;
import com.tapmedia.yoush.util.WidgetUtil;
import com.tapmedia.yoush.util.concurrent.SimpleTask;
import com.tapmedia.yoush.util.simple.SimpleRecyclerViewScrollListener;
import com.tapmedia.yoush.util.task.SnackbarAsyncTask;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collections;

import static android.app.Activity.RESULT_OK;

public class ConversationListFragment extends MainFragment implements
        MainNavigator.BackHandler,
        WidgetUtil.SearchTextChangeListener,
        ConversationListItemEventListener,
        MegaphoneActionController {

  public static final short MESSAGE_REQUESTS_REQUEST_CODE_CREATE_NAME = 32562;
  private static final int ENTRANCE_INTO_CONVERSATION_CODE = 1001;

  public RecyclerView recyclerView;
  private ReminderView reminderView;
  private TextView textViewSearchEmpty;
  private ConversationListViewModel viewModel;
  private RecyclerView.Adapter activeAdapter;
  public ImageView imageViewCreateConversation;
  public ImageView imageViewViewBack;
  public View viewCloseSearch;
  private StickyHeaderDecoration searchAdapterDecoration;
  private ViewGroup megaphoneContainer;
  private SnapToTopDataObserver snapToTopDataObserver;
  private IntentData data;
  private EditText editTextSearch;
  public TextView textViewAppBar;
  private ConversationListDefaultAdapter defaultAdapter;
  private ConversationListSearchAdapter searchAdapter;
  private ImageView imageViewAvatar;

  public IntentData getData() {
    return data;
  }


  public void setData(IntentData data) {
    this.data = data;
  }

  @Override
  public int layoutResource() {
    return R.layout.conversation_list;
  }

  @Override
  public void onFindView() {
    reminderView = find(R.id.reminder);
    recyclerView = find(R.id.list);
    textViewAppBar = find(R.id.textViewAppBar);
    imageViewViewBack = find(R.id.viewBack);
    textViewSearchEmpty = find(R.id.textViewSearchEmpty);
    megaphoneContainer = find(R.id.megaphone_container);
    imageViewCreateConversation = find(R.id.viewCreateConversation);
    editTextSearch = find(R.id.editTextSearch);
    viewCloseSearch = find(R.id.viewCloseSearch);
    imageViewAvatar = find(R.id.imageViewAvatar);
  }

  @Override
  public void onViewCreated() {
    onFindView();
    reminderView.setOnDismissListener(this::updateReminders);
    recyclerView.setHasFixedSize(true);
    recyclerView.setLayoutManager(new LinearLayoutManager(requireActivity()));
    recyclerView.setItemAnimator(new DeleteItemAnimator());
    recyclerView.addOnScrollListener(new SimpleRecyclerViewScrollListener() {
      @Override
      public void onScrollStateChanged(@NonNull @NotNull RecyclerView recyclerView, int newState) {
        hideKeyboard();
      }
    });

    snapToTopDataObserver = new SnapToTopDataObserver(recyclerView, null);
    addViewClicks(imageViewCreateConversation, imageViewViewBack,viewCloseSearch);
    initializeListAdapters();
    initializeViewModel();
    initializeTypingObserver();
    WidgetUtil.onSearchTextChange(editTextSearch, this);
    setStatusBarColor(R.color.colorPrimary);
    //RatingManager.showRatingDialogIfNecessary(requireContext());
  }

  @Override
  public void onLiveDataObservers() {

  }

  @Override
  public void onStart() {
    super.onStart();
    ConversationFragment.prepare(requireContext());
  }

  @Override
  public void onResume() {
    super.onResume();
    updateReminders();
    if (TextSecurePreferences.isSmsEnabled(requireContext())) {
      InsightsLauncher.showInsightsModal(requireContext(), getParentFragmentManager());
    }
    SimpleTask.run(getLifecycle(), Recipient::self, this::initializeProfileIcon);
  }

  @Override
  protected void onViewClick(View v) {
    switch (v.getId()) {
      case R.id.viewCreateConversation: {
        start(ContactSelectionActivity.class);
        break;
      }
      case R.id.viewBack: {
        requireActivity().onBackPressed();
        break;
      }
      case R.id.viewCloseSearch: {
        editTextSearch.setText(null);
        break;
      }

    }
  }

  @Override
  public boolean onBackPressed() {
    return cancelSearchIfOpen();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (resultCode != RESULT_OK) {
      return;
    }

    if (requestCode == CreateKbsPinActivity.REQUEST_NEW_PIN) {
      ViewUtil.toast(getString(R.string.ConfirmKbsPinFragment__pin_created));
      viewModel.onMegaphoneCompleted(Megaphones.Event.PINS_FOR_ALL);
    } else if (requestCode == ENTRANCE_INTO_CONVERSATION_CODE) {
      hideKeyboard();
      IntentData intentData = getData();
      getNavigator().goToConversation(
              intentData.getRecipientId(),
              intentData.getThreadId(),
              intentData.getDistributionType(),
              -1
      );
    }
  }

  @Override
  public void onMegaphoneNavigationRequested(@NonNull Intent intent) {
    startActivity(intent);
  }

  @Override
  public void onMegaphoneNavigationRequested(@NonNull Intent intent, int requestCode) {
    startActivityForResult(intent, requestCode);
  }

  @Override
  public void onMegaphoneToastRequested(@NonNull String string) {

  }

  @Override
  public @NonNull Activity getMegaphoneActivity() {
    return requireActivity();
  }

  @Override
  public void onMegaphoneSnooze(@NonNull Megaphones.Event event) {
    viewModel.onMegaphoneSnoozed(event);
  }

  @Override
  public void onMegaphoneCompleted(@NonNull Megaphones.Event event) {
    viewModel.onMegaphoneCompleted(event);
  }

  private void initializeProfileIcon(@NonNull Recipient recipient) {
//    AvatarUtil.loadIconIntoImageView(recipient, imageViewAvatar);
//    imageViewAvatar.setOnClickListener(v ->
//            start(ApplicationPreferencesActivity.class)
//    );
  }

  private void initializeListAdapters() {
    defaultAdapter = new ConversationListDefaultAdapter(GlideApp.with(this), this);
    searchAdapter = new ConversationListSearchAdapter(GlideApp.with(this), this);
    searchAdapterDecoration = new StickyHeaderDecoration(searchAdapter, false, false);
    setAdapter(defaultAdapter);
  }

  private void setAdapter(@NonNull RecyclerView.Adapter adapter) {
    RecyclerView.Adapter oldAdapter = activeAdapter;
    activeAdapter = adapter;
    if (oldAdapter == activeAdapter) {
      return;
    }
    recyclerView.setAdapter(adapter);
    if (adapter == defaultAdapter) {
      defaultAdapter.registerAdapterDataObserver(snapToTopDataObserver);
    } else {
      defaultAdapter.unregisterAdapterDataObserver(snapToTopDataObserver);
    }
  }

  private void initializeTypingObserver() {
    ApplicationContext.getInstance(requireContext()).getTypingStatusRepository().getTypingThreads().observe(getViewLifecycleOwner(), threadIds -> {
      if (threadIds == null) {
        threadIds = Collections.emptySet();
      }

      defaultAdapter.setTypingThreads(threadIds);
    });
  }

  protected boolean isArchived() {
    return false;
  }

  private void initializeViewModel() {
    viewModel = ViewModelProviders.of(this, new ConversationListViewModel.Factory(isArchived())).get(ConversationListViewModel.class);
    viewModel.getSearchResult().observe(getViewLifecycleOwner(), this::onSearchResultChanged);
    viewModel.getConversationList().observe(getViewLifecycleOwner(), this::onSubmitList);
    viewModel.getMegaphone().observe(getViewLifecycleOwner(), this::onMegaphoneChanged);
    ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
      @Override
      public void onStart(@NonNull LifecycleOwner owner) {
        viewModel.onVisible();
      }
    });
  }

  private void onSearchResultChanged(@Nullable SearchResult result) {
    result = result != null ? result : SearchResult.EMPTY;
    searchAdapter.updateResults(result);
    if (result.isEmpty() && activeAdapter == searchAdapter) {
      textViewSearchEmpty.setText(getString(R.string.SearchFragment_no_results, editTextSearch.getText().toString().trim()));
    } else {
      textViewSearchEmpty.setText(null);
    }
  }

  private void onMegaphoneChanged(@Nullable Megaphone megaphone) {
    if (megaphone == null) {
      megaphoneContainer.setVisibility(View.GONE);
      megaphoneContainer.removeAllViews();
      return;
    }

    View view = MegaphoneViewBuilder.build(requireContext(), megaphone, this);

    megaphoneContainer.removeAllViews();

    if (view != null) {
      megaphoneContainer.addView(view);
      megaphoneContainer.setVisibility(View.VISIBLE);
    } else {
      megaphoneContainer.setVisibility(View.GONE);

      if (megaphone.getOnVisibleListener() != null) {
        megaphone.getOnVisibleListener().onEvent(megaphone, this);
      }
    }

    viewModel.onMegaphoneVisible(megaphone);
  }

  private void updateReminders() {
    Context context = requireContext();
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      if (UnauthorizedReminder.isEligible(context)) {
        return Optional.of(new UnauthorizedReminder(context));
      } else if (ExpiredBuildReminder.isEligible()) {
        return Optional.of(new ExpiredBuildReminder(context));
      } else if (ServiceOutageReminder.isEligible(context)) {
        ApplicationDependencies.getJobManager().add(new ServiceOutageDetectionJob());
        return Optional.of(new ServiceOutageReminder(context));
      } else if (OutdatedBuildReminder.isEligible()) {
        return Optional.of(new OutdatedBuildReminder(context));
      } else if (DefaultSmsReminder.isEligible(context)) {
        return Optional.of(new DefaultSmsReminder(context));
      } else if (Util.isDefaultSmsProvider(context) && SystemSmsImportReminder.isEligible(context)) {
        return Optional.of((new SystemSmsImportReminder(context)));
      } else if (PushRegistrationReminder.isEligible(context)) {
        return Optional.of((new PushRegistrationReminder(context)));
      } else if (ShareReminder.isEligible(context)) {
        return Optional.of(new ShareReminder(context));
      } else if (DozeReminder.isEligible(context)) {
        return Optional.of(new DozeReminder(context));
      } else {
        return Optional.<Reminder>absent();
      }
    }, reminder -> {
      reminderView.hide();
//      if (reminder.isPresent() && getActivity() != null && !isRemoving()) {
//        reminderView.showReminder(reminder.get());
//      } else if (!reminder.isPresent()) {
//        reminderView.hide();
//      }
    });
  }

  public void onSubmitList(@NonNull ConversationListViewModel.ConversationList conversationList) {
    defaultAdapter.submitList(conversationList.getConversations());
    defaultAdapter.updateArchived(conversationList.getArchivedCount());
  }

  /**
   * {@link ConversationListItemEventListener}  implements
   */
  @Override
  public void onItemEventMaskAsRead(ThreadRecord record, int position) {
    ActionData.markAsRead(ConversationListFragment.this, record.getThreadId(), none -> {
      activeAdapter.notifyItemChanged(position);
    });
  }

  @Override
  public void onItemEventMaskAsUnRead(ThreadRecord record, int position) {
    ActionData.markUnAsRead(ConversationListFragment.this, record.getThreadId(), none -> {
      activeAdapter.notifyItemChanged(position);
    });
  }

  @Override
  public void onItemEventArchive(ThreadRecord record, int position) {
    new SnackbarAsyncTask<Void>(getView(),
            quantityStr(R.plurals.ConversationListFragment_conversations_archived, 1, 1),
            str(R.string.ConversationListFragment_undo),
            color(R.color.amber_500),
            Snackbar.LENGTH_LONG, true) {

      @Override
      protected void onPostExecute(Void result) {
        super.onPostExecute(result);
      }

      @Override
      protected void executeAction(@Nullable Void parameter) {
        ActionData.archiveConversation(
                ConversationListFragment.this,
                record.getThreadId(),
                none -> {
                }
        );
      }

      @Override
      protected void reverseAction(@Nullable Void parameter) {
        onItemEventUnArchive(record, position);
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  @Override
  public void onItemEventUnArchive(ThreadRecord record, int position) {
    ActionData.unArchiveConversation(this, record.getThreadId(), none -> {
      activeAdapter.notifyDataSetChanged();
    });
  }

  @Override
  public void onItemEventHide(ThreadRecord record, int position) {
    Runnable runnable = () -> ActionData.hideConversation(
            ConversationListFragment.this,
            record.getThreadId(),
            none -> onResearch());

    String currentPinCode = SignalStore.pinCodeValues().getPinCode();
    if (TextUtils.isEmpty(currentPinCode)) {
      PinCreateFragment f = new PinCreateFragment();
      f.onSuccess = runnable;
      addFragment(f);
      return;
    }

    PinAuthFragment f = new PinAuthFragment();
    f.onSuccess = runnable;
    addFragment(f);
  }

  @Override
  public void onItemEventUnHide(ThreadRecord record, int position) {
    PinAuthFragment f = new PinAuthFragment();
    f.onSuccess = () -> {
      ActionData.unHideConversation(
              ConversationListFragment.this,
              record.getThreadId(),
              none -> onResearch());
    };
    addFragment(f);
  }

  @Override
  public void onItemEventDelete(ThreadRecord record, int position) {
    ActionData.deleteConversation(this, record.getThreadId(), none -> {
      activeAdapter.notifyDataSetChanged();
    });
  }

  @Override
  public void onItemEventClick(ThreadRecord record, int position) {
    hideKeyboard();
    String pinCode = SignalStore.pinCodeValues().getPinCode();
    if (record.isHidden() && !viewModel.lastQuery.equals(pinCode)) {
      PinAuthFragment f = new PinAuthFragment();
      f.onSuccess = () -> {
        getNavigator().goToConversation(record);
      };
      addFragment(f);
      return;
    }
    getNavigator().goToConversation(record);
  }

  @Override
  public void onItemEventShowArchiveList() {
    addFragment(new ConversationListArchiveFragment());
  }

  @Override
  public void onContactClicked(Recipient recipient) {
    hideKeyboard();
    long threadId = DatabaseFactory
            .getThreadDatabase(ApplicationContext.getInstance())
            .getThreadIdIfExistsFor(recipient);
    ThreadRecord threadRecord = ActionData.getThreadRecord(threadId);
    if (threadRecord == null) {
      goToConversation(recipient.getId(), threadId);
      return;
    }
    String pinCode = SignalStore.pinCodeValues().getPinCode();
    if (threadRecord.isHidden() && !viewModel.lastQuery.equals(pinCode)) {
      PinAuthFragment f = new PinAuthFragment();
      f.onSuccess = () -> goToConversation(threadRecord);
      addFragment(f);
      return;
    }
    goToConversation(threadRecord);
  }

  @Override
  public void onMessageClicked(MessageResult message) {
    hideKeyboard();
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      int startingPosition = DatabaseFactory.getMmsSmsDatabase(getContext()).getMessagePositionInConversation(message.threadId, message.receivedTimestampMs);
      return Math.max(0, startingPosition);
    }, startingPosition -> {
      Runnable runnable = () -> goToConversation(
              message.conversationRecipient.getId(),
              message.threadId,
              ThreadDatabase.DistributionTypes.DEFAULT,
              startingPosition
      );
      ThreadRecord threadRecord = ActionData.getThreadRecord(message.threadId);
      String pinCode = SignalStore.pinCodeValues().getPinCode();
      if (threadRecord.isHidden() && !viewModel.lastQuery.equals(pinCode)) {
        PinAuthFragment fragment = new PinAuthFragment();
        fragment.onSuccess = runnable;
        addFragment(fragment);
      } else {
        runnable.run();
      }
    });
  }

  @Override
  public boolean onItemEventLongClick(ThreadRecord threadRecord, int position) {
    return true;
  }

  /**
   *
   */
  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onEvent(ReminderUpdateEvent event) {
    updateReminders();
  }

  @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
  public void onEvent(MessageSender.MessageSentEvent event) {
    EventBus.getDefault().removeStickyEvent(event);
    cancelSearchIfOpen();
  }


  private void onResearch() {
    if (activeAdapter != searchAdapter) {
      defaultAdapter.notifyDataSetChanged();
    }
    String s = editTextSearch.getText().toString();
    if (TextUtils.isEmpty(s)) {
      cancelSearchIfOpen();
    } else {
      onStartSearch(s);
    }
  }

  /**
   * {@link WidgetUtil.SearchTextChangeListener} implements
   */
  @Override
  public void onStartSearch(String s) {
    viewModel.updateQuery(s);
    if (activeAdapter != searchAdapter) {
      viewCloseSearch.setVisibility(View.VISIBLE);
      recyclerView.removeItemDecoration(searchAdapterDecoration);
      recyclerView.addItemDecoration(searchAdapterDecoration);
      setAdapter(searchAdapter);
    }
  }

  @Override
  public void onSearchCancel() {
    cancelSearchIfOpen();
  }

  private boolean cancelSearchIfOpen() {
    if (activeAdapter != searchAdapter) return false;
    recyclerView.removeItemDecoration(searchAdapterDecoration);
    viewCloseSearch.setVisibility(View.INVISIBLE);
    textViewSearchEmpty.setText(null);
    setAdapter(defaultAdapter);
    return true;
  }

}



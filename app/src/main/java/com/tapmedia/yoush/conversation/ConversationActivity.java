/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tapmedia.yoush.conversation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.RestrictionEntry;
import android.content.RestrictionsManager;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Browser;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.PreferenceManager;

import com.annimon.stream.Stream;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.tapmedia.yoush.ApplicationContext;
import com.tapmedia.yoush.BlockUnblockDialog;
import com.tapmedia.yoush.ExpirationDialog;
import com.tapmedia.yoush.MainActivity;
import com.tapmedia.yoush.PassphraseRequiredActivity;
import com.tapmedia.yoush.PromptMmsActivity;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.ShortcutLauncherActivity;
import com.tapmedia.yoush.TransportOption;
import com.tapmedia.yoush.VerifyIdentityActivity;
import com.tapmedia.yoush.attachments.Attachment;
import com.tapmedia.yoush.attachments.TombstoneAttachment;
import com.tapmedia.yoush.audio.AudioRecorder;
import com.tapmedia.yoush.audio.AudioSlidePlayer;
import com.tapmedia.yoush.color.MaterialColor;
import com.tapmedia.yoush.components.AnimatingToggle;
import com.tapmedia.yoush.components.ComposeText;
import com.tapmedia.yoush.components.ConversationSearchBottomBar;
import com.tapmedia.yoush.components.HidingLinearLayout;
import com.tapmedia.yoush.components.InputAwareLayout;
import com.tapmedia.yoush.components.InputPanel;
import com.tapmedia.yoush.components.KeyboardAwareLinearLayout.OnKeyboardShownListener;
import com.tapmedia.yoush.components.SendButton;
import com.tapmedia.yoush.components.ThumbnailView;
import com.tapmedia.yoush.components.TooltipPopup;
import com.tapmedia.yoush.components.emoji.EmojiKeyboardProvider;
import com.tapmedia.yoush.components.emoji.EmojiStrings;
import com.tapmedia.yoush.components.emoji.MediaKeyboard;
import com.tapmedia.yoush.components.identity.UnverifiedBannerView;
import com.tapmedia.yoush.components.location.SignalPlace;
import com.tapmedia.yoush.components.reminder.ExpiredBuildReminder;
import com.tapmedia.yoush.components.reminder.Reminder;
import com.tapmedia.yoush.components.reminder.ReminderView;
import com.tapmedia.yoush.components.reminder.ServiceOutageReminder;
import com.tapmedia.yoush.components.reminder.UnauthorizedReminder;
import com.tapmedia.yoush.contacts.sync.DirectoryHelper;
import com.tapmedia.yoush.contactshare.Contact;
import com.tapmedia.yoush.contactshare.ContactShareEditActivity;
import com.tapmedia.yoush.contactshare.ContactUtil;
import com.tapmedia.yoush.contactshare.SimpleTextWatcher;
import com.tapmedia.yoush.conversation.ConversationGroupViewModel.GroupActiveState;
import com.tapmedia.yoush.conversation.background.BackgroundData;
import com.tapmedia.yoush.conversation.job.MessageJob;
import com.tapmedia.yoush.conversation.pin.ConversationPinLayout;
import com.tapmedia.yoush.conversation.ui.error.SafetyNumberChangeDialog;
import com.tapmedia.yoush.conversationlist.model.MessageResult;
import com.tapmedia.yoush.crypto.SecurityEvent;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.DraftDatabase;
import com.tapmedia.yoush.database.DraftDatabase.Draft;
import com.tapmedia.yoush.database.DraftDatabase.Drafts;
import com.tapmedia.yoush.database.GroupDatabase;
import com.tapmedia.yoush.database.IdentityDatabase;
import com.tapmedia.yoush.database.IdentityDatabase.IdentityRecord;
import com.tapmedia.yoush.database.IdentityDatabase.VerifiedStatus;
import com.tapmedia.yoush.database.MessagingDatabase.MarkedMessageInfo;
import com.tapmedia.yoush.database.MmsSmsColumns.Types;
import com.tapmedia.yoush.database.RecipientDatabase;
import com.tapmedia.yoush.database.RecipientDatabase.RegisteredState;
import com.tapmedia.yoush.database.ThreadDatabase;
import com.tapmedia.yoush.database.identity.IdentityRecordList;
import com.tapmedia.yoush.database.model.MessageRecord;
import com.tapmedia.yoush.database.model.MmsMessageRecord;
import com.tapmedia.yoush.database.model.ReactionRecord;
import com.tapmedia.yoush.database.model.StickerRecord;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.events.ReminderUpdateEvent;
import com.tapmedia.yoush.giph.ui.GiphyActivity;
import com.tapmedia.yoush.groups.GroupCallBeginService;
import com.tapmedia.yoush.groups.GroupChangeBusyException;
import com.tapmedia.yoush.groups.GroupChangeFailedException;
import com.tapmedia.yoush.groups.GroupInsufficientRightsException;
import com.tapmedia.yoush.groups.GroupManager;
import com.tapmedia.yoush.groups.GroupNotAMemberException;
import com.tapmedia.yoush.groups.JitsiService;
import com.tapmedia.yoush.groups.ui.GroupChangeFailureReason;
import com.tapmedia.yoush.groups.ui.GroupErrors;
import com.tapmedia.yoush.groups.ui.managegroup.ManageGroupActivity;
import com.tapmedia.yoush.insights.InsightsLauncher;
import com.tapmedia.yoush.invites.InviteReminderModel;
import com.tapmedia.yoush.invites.InviteReminderRepository;
import com.tapmedia.yoush.jobs.RequestGroupV2InfoJob;
import com.tapmedia.yoush.jobs.RetrieveProfileJob;
import com.tapmedia.yoush.jobs.ServiceOutageDetectionJob;
import com.tapmedia.yoush.linkpreview.LinkPreview;
import com.tapmedia.yoush.linkpreview.LinkPreviewRepository;
import com.tapmedia.yoush.linkpreview.LinkPreviewViewModel;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.maps.PlacePickerActivity;
import com.tapmedia.yoush.mediasend.Media;
import com.tapmedia.yoush.mediasend.MediaSendActivity;
import com.tapmedia.yoush.mediasend.MediaSendActivityResult;
import com.tapmedia.yoush.messagedetails.MessageDetailsActivity;
import com.tapmedia.yoush.messagerequests.MessageRequestViewModel;
import com.tapmedia.yoush.messagerequests.MessageRequestsBottomView;
import com.tapmedia.yoush.mms.AttachmentManager;
import com.tapmedia.yoush.mms.AttachmentManager.MediaType;
import com.tapmedia.yoush.mms.AudioSlide;
import com.tapmedia.yoush.mms.GifSlide;
import com.tapmedia.yoush.mms.GlideApp;
import com.tapmedia.yoush.mms.GlideRequests;
import com.tapmedia.yoush.mms.ImageSlide;
import com.tapmedia.yoush.mms.LocationSlide;
import com.tapmedia.yoush.mms.MediaConstraints;
import com.tapmedia.yoush.mms.OutgoingExpirationUpdateMessage;
import com.tapmedia.yoush.mms.OutgoingMediaMessage;
import com.tapmedia.yoush.mms.OutgoingSecureMediaMessage;
import com.tapmedia.yoush.mms.QuoteId;
import com.tapmedia.yoush.mms.QuoteModel;
import com.tapmedia.yoush.mms.Slide;
import com.tapmedia.yoush.mms.SlideDeck;
import com.tapmedia.yoush.mms.StickerSlide;
import com.tapmedia.yoush.mms.VideoSlide;
import com.tapmedia.yoush.notifications.MarkReadReceiver;
import com.tapmedia.yoush.notifications.NotificationChannels;
import com.tapmedia.yoush.permissions.Permissions;
import com.tapmedia.yoush.profiles.GroupShareProfileView;
import com.tapmedia.yoush.providers.BlobProvider;
import com.tapmedia.yoush.reactions.any.ReactWithAnyEmojiBottomSheetDialogFragment;
import com.tapmedia.yoush.recipients.LiveRecipient;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientExporter;
import com.tapmedia.yoush.recipients.RecipientFormattingException;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.recipients.RecipientUtil;
import com.tapmedia.yoush.recipients.ui.managerecipient.ManageRecipientActivity;
import com.tapmedia.yoush.registration.RegistrationNavigationActivity;
import com.tapmedia.yoush.ringrtc.RemotePeer;
import com.tapmedia.yoush.service.KeyCachingService;
import com.tapmedia.yoush.sms.MessageSender;
import com.tapmedia.yoush.sms.OutgoingEncryptedMessage;
import com.tapmedia.yoush.sms.OutgoingEndSessionMessage;
import com.tapmedia.yoush.sms.OutgoingTextMessage;
import com.tapmedia.yoush.stickers.StickerKeyboardProvider;
import com.tapmedia.yoush.stickers.StickerLocator;
import com.tapmedia.yoush.stickers.StickerManagementActivity;
import com.tapmedia.yoush.stickers.StickerPackInstallEvent;
import com.tapmedia.yoush.stickers.StickerSearchRepository;
import com.tapmedia.yoush.util.CharacterCalculator.CharacterState;
import com.tapmedia.yoush.util.DynamicDarkToolbarTheme;
import com.tapmedia.yoush.util.DynamicLanguage;
import com.tapmedia.yoush.util.DynamicTheme;
import com.tapmedia.yoush.util.IdentityUtil;
import com.tapmedia.yoush.util.MediaUtil;
import com.tapmedia.yoush.util.MessageUtil;
import com.tapmedia.yoush.util.ServiceUtil;
import com.tapmedia.yoush.util.TextSecurePreferences;
import com.tapmedia.yoush.util.TextSecurePreferences.MediaKeyboardMode;
import com.tapmedia.yoush.util.Util;
import com.tapmedia.yoush.util.ViewUtil;
import com.tapmedia.yoush.util.concurrent.AssertedSuccessListener;
import com.tapmedia.yoush.util.concurrent.ListenableFuture;
import com.tapmedia.yoush.util.concurrent.SettableFuture;
import com.tapmedia.yoush.util.concurrent.SignalExecutors;
import com.tapmedia.yoush.util.concurrent.SimpleTask;
import com.tapmedia.yoush.util.views.Stub;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jitsi.meet.sdk.JitsiMeetActivity;
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions;
import org.jitsi.meet.sdk.JitsiMeetUserInfo;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.tapmedia.yoush.TransportOption.Type;
import static com.tapmedia.yoush.database.GroupDatabase.GroupRecord;
import static org.whispersystems.libsignal.SessionCipher.SESSION_LOCK;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Encoder;
import io.jsonwebtoken.io.EncodingException;
import io.jsonwebtoken.security.Keys;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.security.Key;

import javax.net.ssl.HttpsURLConnection;

/**
 * Activity for displaying a message thread, as well as
 * composing/sending a new message into that thread.
 *
 * @author Moxie Marlinspike
 *
 */
@SuppressLint("StaticFieldLeak")
public class ConversationActivity extends PassphraseRequiredActivity
    implements ConversationFragment.ConversationFragmentListener,
               AttachmentManager.AttachmentListener,
               OnKeyboardShownListener,
               InputPanel.Listener,
               InputPanel.MediaListener,
               ComposeText.CursorPositionChangedListener,
               ConversationSearchBottomBar.EventListener,
               StickerKeyboardProvider.StickerEventListener,
               AttachmentKeyboard.Callback,
               ConversationReactionOverlay.OnReactionSelectedListener,
               ReactWithAnyEmojiBottomSheetDialogFragment.Callback,
               SafetyNumberChangeDialog.Callback
{

  private static final int SHORTCUT_ICON_SIZE = Build.VERSION.SDK_INT >= 26 ? ViewUtil.dpToPx(72) : ViewUtil.dpToPx(48 + 16 * 2);

  private static final String TAG = ConversationActivity.class.getSimpleName();

  public static final String SAFETY_NUMBER_DIALOG = "SAFETY_NUMBER";

  public static final String RECIPIENT_EXTRA                   = "recipient_id";
  public static final String THREAD_ID_EXTRA                   = "thread_id";
  public static final String TEXT_EXTRA                        = "draft_text";
  public static final String MEDIA_EXTRA                       = "media_list";
  public static final String STICKER_EXTRA                     = "sticker_extra";
  public static final String DISTRIBUTION_TYPE_EXTRA           = "distribution_type";
  public static final String STARTING_POSITION_EXTRA           = "starting_position";
  public static final String ACTIVITY = "activity";

  private static final int PICK_GALLERY        = 1;
  private static final int PICK_DOCUMENT       = 2;
  private static final int PICK_AUDIO          = 3;
  private static final int PICK_CONTACT        = 4;
  private static final int GET_CONTACT_DETAILS = 5;
  private static final int GROUP_EDIT = 6;
  private static final int TAKE_PHOTO = 7;
  private static final int ADD_CONTACT = 8;
  private static final int PICK_LOCATION = 9;
  private static final int PICK_GIF = 10;
  private static final int SMS_DEFAULT = 11;
  public static final int MEDIA_SENDER = 12;
  private GlideRequests glideRequests;
  protected ComposeText composeText;
  private AnimatingToggle buttonToggle;
  private SendButton sendButton;
  private ImageButton attachButton;
  protected ConversationTitleView titleView;
  private TextView charactersLeft;
  public ConversationFragment fragment;
  private Button unblockButton;
  private Button makeDefaultSmsButton;
  private Button registerButton;
  private InputAwareLayout container;
  protected Stub<ReminderView> reminderView;
  private Stub<UnverifiedBannerView> unverifiedBannerView;
  private Stub<GroupShareProfileView> groupShareProfileView;
  private TypingStatusTextWatcher typingTextWatcher;
  private ConversationSearchBottomBar searchNav;
  private MessageRequestsBottomView messageRequestBottomView;
  private ConversationReactionOverlay reactionOverlay;

  private AttachmentManager attachmentManager;
  private AudioRecorder audioRecorder;
  private BroadcastReceiver securityUpdateReceiver;
  private Stub<MediaKeyboard> emojiDrawerStub;
  private Stub<AttachmentKeyboard> attachmentKeyboardStub;
  protected HidingLinearLayout quickAttachmentToggle;
  protected HidingLinearLayout inlineAttachmentToggle;
  private InputPanel inputPanel;
  private View panelParent;
  private ThumbnailView thumbnailViewBackground;

  private LinkPreviewViewModel linkPreviewViewModel;
  private ConversationSearchViewModel searchViewModel;
  private ConversationStickerViewModel stickerViewModel;
  private ConversationViewModel viewModel;
  private InviteReminderModel inviteReminderModel;
  private ConversationGroupViewModel groupViewModel;

  private LiveRecipient recipient;
  public long threadId;
  private int distributionType;
  private boolean isSecureText;
  private boolean isDefaultSms = true;
  private boolean isMmsEnabled = true;
  private boolean isSecurityInitialized = false;
  private final IdentityRecordList identityRecords = new IdentityRecordList();
  private final DynamicTheme dynamicTheme = new DynamicDarkToolbarTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private String activity;

  private boolean configurationByRestrictions = false;

  public static final String RESTRICTION_SERVER_URL = "SERVER_URL";

  public static boolean checkDenyStartCall = false;

  public static Intent buildIntent(@NonNull Context context,
                     @NonNull RecipientId recipientId,
                     long threadId,
                     int distributionType,
                     int startingPosition) {
    Intent intent = new Intent(context, ConversationActivity.class);
    intent.putExtra(ConversationActivity.RECIPIENT_EXTRA, recipientId);
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, distributionType);
    intent.putExtra(ConversationActivity.STARTING_POSITION_EXTRA, startingPosition);
    return intent;
  }

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle state, boolean ready) {
    setContentView(R.layout.conversation_activity);
    RecipientId recipientId = getIntent().getParcelableExtra(RECIPIENT_EXTRA);
    activity = getIntent().getStringExtra(ACTIVITY);
    getIntent().removeExtra(ACTIVITY);
    if (recipientId == null) {
      Log.w(TAG, "[onCreate] Missing recipientId!");
      startActivity(new Intent(this, MainActivity.class));
      finish();
      return;
    }

    TypedArray typedArray = obtainStyledAttributes(new int[] {R.attr.conversation_background});
    int color = typedArray.getColor(0, Color.WHITE);
    typedArray.recycle();
    getWindow().getDecorView().setBackgroundColor(color);
    fragment = initFragment(R.id.fragment_content, new ConversationFragment(), dynamicLanguage.getCurrentLocale());
    initializeReceivers();
    initializeActionBar();
    initializeViews();
    initializeResources();
    initializeLinkPreviewObserver();
    initializeSearchObserver();
    initializeStickerObserver();
    initializeViewModel();
    initializeGroupViewModel();
    initializeEnabledCheck();
    initializeSecurity(recipient.get().isRegistered(), isDefaultSms).addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        initializeProfiles();
        initializeDraft().addListener(new AssertedSuccessListener<Boolean>() {
          @Override
          public void onSuccess(Boolean loadedDraft) {
            if (loadedDraft != null && loadedDraft) {
              Log.i(TAG, "Finished loading draft");
              Util.runOnMain(() -> {
                if (fragment != null && fragment.isResumed()) {
                  fragment.moveToLastSeen();
                } else {
                  Log.w(TAG, "Wanted to move to the last seen position, but the fragment was in an invalid state");
                }
              });
            }

            if (TextSecurePreferences.isTypingIndicatorsEnabled(ConversationActivity.this)) {
              composeText.addTextChangedListener(typingTextWatcher);
            }
            composeText.setSelection(composeText.length(), composeText.length());
          }
        });
      }
    });
    initializeInsightObserver();
    resolveRestrictions();

    MessageJob.threadId = threadId;
    MessageJob.recipient = getRecipient();
    thumbnailViewBackground = findViewById(R.id.thumbnailViewBackground);
    ConversationPinLayout pinLayout = findViewById(R.id.conversationPinLayout);
    pinLayout.initializePinView(this);
    pinLayout.pinMessageClickListener = record -> fragment.jumpToMessage(
            record.getRecipient().getId(),
            record.getDateReceived(),
            () -> ViewUtil.toast("message not found"));


    BackgroundData.uploadResultLiveData.observe(this, result -> sendMessage(result));
    BackgroundData.backgroundLiveData.observe(this, wrapper -> {
      if (wrapper == null || wrapper.threadId != threadId) {
        return;
      }
      if (wrapper.getAction().equals("remove")) {
        thumbnailViewBackground.setImageDrawable(null);
        return;
      }
      thumbnailViewBackground.post(() -> thumbnailViewBackground.setLayoutParams(new FrameLayout.LayoutParams(
              FrameLayout.LayoutParams.MATCH_PARENT, ViewUtil.screenHeight(this))
      ));
      String imageUrl = wrapper.getImageUrl();
      if (!TextUtils.isEmpty(imageUrl)) {
        thumbnailViewBackground.setImageUrl(
                MessageJob.glideRequests,
                imageUrl
        );
        return;
      }
      Slide slide = BackgroundData.getSlide(wrapper.record);
      if (slide != null) {
        thumbnailViewBackground.setImageResource(
                MessageJob.glideRequests,
                slide,
                false, false,
                slide.asAttachment().getWidth(),
                slide.asAttachment().getHeight()
        );
        return;
      }
    });

  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    resolveRestrictions();
    Log.i(TAG, "onNewIntent()");
    
    if (isFinishing()) {
      Log.w(TAG, "Activity is finishing...");
      return;
    }

    if (!Util.isEmpty(composeText) || attachmentManager.isAttachmentPresent() || inputPanel.getQuote().isPresent()) {
      saveDraft();
      attachmentManager.clear(glideRequests, false);
      inputPanel.clearQuote();
      silentlySetComposeText("");
    }

    RecipientId recipientId = intent.getParcelableExtra(RECIPIENT_EXTRA);
    activity = intent.getStringExtra(ACTIVITY);
    intent.removeExtra(ACTIVITY);

    if (recipientId == null) {
      Log.w(TAG, "[onNewIntent] Missing recipientId!");
      // TODO [greyson] Navigation
      startActivity(new Intent(this, MainActivity.class));
      finish();
      return;
    }

    setIntent(intent);
    initializeResources();
    initializeSecurity(recipient.get().isRegistered(), isDefaultSms).addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        initializeDraft();
      }
    });

    if (fragment != null) {
      fragment.onNewIntent();
    }

    searchNav.setVisibility(View.GONE);
  }

  @Override
  protected void onResume() {
    super.onResume();
    resolveRestrictions();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);

    EventBus.getDefault().register(this);
    initializeMmsEnabledCheck();
    initializeIdentityRecords();
    composeText.setTransport(sendButton.getSelectedTransport());

    Recipient recipientSnapshot = recipient.get();

    titleView.setTitle(glideRequests, recipientSnapshot);
    setActionBarColor(recipientSnapshot.getColor());
    setBlockedUserState(recipientSnapshot, isSecureText, isDefaultSms);
    calculateCharactersRemaining();

    if (recipientSnapshot.getGroupId().isPresent() && recipientSnapshot.getGroupId().get().isV2()) {
      ApplicationDependencies.getJobManager().add(new RequestGroupV2InfoJob(recipientSnapshot.getGroupId().get().requireV2()));
    }

    ApplicationDependencies.getMessageNotifier().setVisibleThread(threadId);
    markThreadAsRead();

    ConversationActivity obj2 = new ConversationActivity();
    if (obj2.checkDenyStartCall) {
      obj2.checkDenyStartCall = false;

      sendMessageCallGroup(true, false, false);
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    ApplicationDependencies.getMessageNotifier().clearVisibleThread();
    if (isFinishing()) overridePendingTransition(R.anim.fade_scale_in, R.anim.slide_to_end);
    inputPanel.onPause();
    fragment.setLastSeen(System.currentTimeMillis());
    markLastSeen();
    AudioSlidePlayer.stopAll();
    EventBus.getDefault().unregister(this);
  }

  @Override
  protected void onStop() {
    super.onStop();
    BackgroundData.backgroundLiveData.setValue(null);
    EventBus.getDefault().unregister(this);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    Log.i(TAG, "onConfigurationChanged(" + newConfig.orientation + ")");
    super.onConfigurationChanged(newConfig);
    composeText.setTransport(sendButton.getSelectedTransport());
    if (emojiDrawerStub.resolved() && container.getCurrentInput() == emojiDrawerStub.get()) {
      container.hideAttachedInput(true);
    }
    if (reactionOverlay != null && reactionOverlay.isShowing()) {
      reactionOverlay.hide();
    }
  }

  @Override
  protected void onDestroy() {
    saveDraft();
    if (securityUpdateReceiver != null)  unregisterReceiver(securityUpdateReceiver);
    super.onDestroy();
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent ev) {
    return reactionOverlay.applyTouchEvent(ev) || super.dispatchTouchEvent(ev);
  }

  @Override
  public void onActivityResult(final int reqCode, int resultCode, Intent data) {
    Log.i(TAG, "onActivityResult called: " + reqCode + ", " + resultCode + " , " + data);
    super.onActivityResult(reqCode, resultCode, data);

    if ((data == null && reqCode != TAKE_PHOTO && reqCode != SMS_DEFAULT) ||
        (resultCode != RESULT_OK && reqCode != SMS_DEFAULT))
    {
      updateLinkPreviewState();
      return;
    }

    switch (reqCode) {
    case PICK_DOCUMENT:
      setMedia(data.getData(), MediaType.DOCUMENT);
      break;
    case PICK_AUDIO:
      setMedia(data.getData(), MediaType.AUDIO);
      break;
//    case PICK_CONTACT:
//      if (isSecureText && !isSmsForced()) {
//        openContactShareEditor(data.getData());
//      } else {
//        addAttachmentContactInfo(data.getData());
//      }
//      break;
//    case GET_CONTACT_DETAILS:
//      sendSharedContact(data.getParcelableArrayListExtra(ContactShareEditActivity.KEY_CONTACTS));
//      break;
    case GROUP_EDIT:
      Recipient recipientSnapshot = recipient.get();

      onRecipientChanged(recipientSnapshot);
      titleView.setTitle(glideRequests, recipientSnapshot);
      NotificationChannels.updateContactChannelName(this, recipientSnapshot);
      setBlockedUserState(recipientSnapshot, isSecureText, isDefaultSms);
      supportInvalidateOptionsMenu();
      break;
    case TAKE_PHOTO:
      handleImageFromDeviceCameraApp();
      break;
    case ADD_CONTACT:
      SimpleTask.run(() -> {
        try {
          DirectoryHelper.refreshDirectoryFor(this, recipient.get(), false);
        } catch (IOException e) {
          Log.w(TAG, "Failed to refresh user after adding to contacts.");
        }
        return null;
      }, nothing -> onRecipientChanged(recipient.get()));
      break;
    case PICK_LOCATION:
      SignalPlace place = new SignalPlace(PlacePickerActivity.addressFromData(data));
      attachmentManager.setLocation(place, getCurrentMediaConstraints());
      break;
    case PICK_GIF:
      setMedia(data.getData(),
              MediaType.GIF,
              data.getIntExtra(GiphyActivity.EXTRA_WIDTH, 0),
              data.getIntExtra(GiphyActivity.EXTRA_HEIGHT, 0),
              data.getBooleanExtra(GiphyActivity.EXTRA_BORDERLESS, false));
      break;
      case SMS_DEFAULT:
        initializeSecurity(isSecureText, isDefaultSms);
        break;
      case MEDIA_SENDER:
        MediaSendActivityResult result = data.getParcelableExtra(MediaSendActivity.EXTRA_RESULT);
        sendButton.setTransport(result.getTransport());
        sendMessage(result);
        break;
    }
  }

  public void sendMessage(MediaSendActivityResult result) {
    if (result == null) return;
    if (result.isPushPreUpload()) {
      sendMediaMessage(result);
      return;
    }
    long expiresIn = recipient.get().getExpireMessages() * 1000L;
    int subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
    boolean initiating = threadId == -1;
    QuoteModel quote = result.isViewOnce() ? null : inputPanel.getQuote().orNull();
    SlideDeck slideDeck = new SlideDeck();

    for (Media mediaItem : result.getNonUploadedMedia()) {
      if (MediaUtil.isVideoType(mediaItem.getMimeType())) {
        slideDeck.addSlide(new VideoSlide(this, mediaItem.getUri(), 0, mediaItem.getCaption().orNull(), mediaItem.getTransformProperties().orNull()));
      } else if (MediaUtil.isGif(mediaItem.getMimeType())) {
        slideDeck.addSlide(new GifSlide(this, mediaItem.getUri(), 0, mediaItem.getWidth(), mediaItem.getHeight(), mediaItem.isBorderless(), mediaItem.getCaption().orNull()));
      } else if (MediaUtil.isImageType(mediaItem.getMimeType())) {
        slideDeck.addSlide(new ImageSlide(this, mediaItem.getUri(), mediaItem.getMimeType(), 0, mediaItem.getWidth(), mediaItem.getHeight(), mediaItem.isBorderless(), mediaItem.getCaption().orNull(), null));
      } else {
        Log.w(TAG, "Asked to send an unexpected mimeType: '" + mediaItem.getMimeType() + "'. Skipping.");
      }
    }

    final Context context = ConversationActivity.this.getApplicationContext();

    sendMediaMessage(result.getTransport().isSms(),
            result.getBody(),
            slideDeck,
            quote,
            Collections.emptyList(),
            Collections.emptyList(),
            expiresIn,
            result.isViewOnce(),
            subscriptionId,
            initiating,
            true).addListener(new AssertedSuccessListener<Void>() {
      @Override
      public void onSuccess(Void v) {
        AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
          Stream.of(slideDeck.getSlides())
                  .map(Slide::getUri)
                  .withoutNulls()
                  .filter(BlobProvider::isAuthority)
                  .forEach(uri -> BlobProvider.getInstance().delete(context, uri));
        });
      }
    });
  }

  private void handleImageFromDeviceCameraApp() {
    if (attachmentManager.getCaptureUri() == null) {
      Log.w(TAG, "No image available.");
      return;
    }

    try {
      Uri mediaUri = BlobProvider.getInstance()
              .forData(getContentResolver().openInputStream(attachmentManager.getCaptureUri()), 0L)
              .withMimeType(MediaUtil.IMAGE_JPEG)
                                 .createForSingleSessionOnDisk(this);

      getContentResolver().delete(attachmentManager.getCaptureUri(), null, null);

      setMedia(mediaUri, MediaType.IMAGE);
    } catch (IOException ioe) {
      Log.w(TAG, "Could not handle public image", ioe);
    }
  }

  @Override
  public void startActivity(Intent intent) {
    if (intent.getStringExtra(Browser.EXTRA_APPLICATION_ID) != null) {
      intent.removeExtra(Browser.EXTRA_APPLICATION_ID);
    }

    try {
      getIntent().removeExtra(ACTIVITY);
      intent.removeExtra(ACTIVITY);
      activity = null;
      super.startActivity(intent);
    } catch (ActivityNotFoundException e) {
      Log.w(TAG, e);
      Toast.makeText(this, R.string.ConversationActivity_there_is_no_app_available_to_handle_this_link_on_your_device, Toast.LENGTH_LONG).show();
    }
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    GroupActiveState groupActiveState = groupViewModel.getGroupActiveState().getValue();
    boolean isActiveGroup             = groupActiveState != null && groupActiveState.isActiveGroup();
    boolean isActiveV2Group           = groupActiveState != null && groupActiveState.isActiveV2Group();
    boolean isInActiveGroup           = groupActiveState != null && !groupActiveState.isActiveGroup();

    if (isInMessageRequest()) {

      super.onPrepareOptionsMenu(menu);
      return true;
    }

    if (isSecureText) {
      if (recipient.get().getExpireMessages() > 0) {
        titleView.showExpiring(recipient);
      } else {
        titleView.clearExpiring();
      }
    }

    final String[] buttonCallType = {"null"};

    if (isSingleConversation()) {
      if (isSecureText) inflater.inflate(R.menu.conversation_callable_secure, menu);
      else              inflater.inflate(R.menu.conversation_callable_insecure, menu);
    } else if (isGroupConversation()) {

      if (isSecureText) inflater.inflate(R.menu.conversation_callable_secure, menu);
      else              inflater.inflate(R.menu.conversation_callable_insecure, menu);

      if (!isPushGroupConversation()) {
        inflater.inflate(R.menu.conversation_mms_group_options, menu);
        if (distributionType == ThreadDatabase.DistributionTypes.BROADCAST) {
          menu.findItem(R.id.menu_distribution_broadcast).setChecked(true);
        } else {
          menu.findItem(R.id.menu_distribution_conversation).setChecked(true);
        }
      } else if (!isActiveGroup) {
//        buttonCallType[0] = "hide";
      }
    }


    if (recipient != null && recipient.get().isLocalNumber()) {
      if (isSecureText) {
        buttonCallType[0] = "hide";
      } else {
        hideMenuItem(menu, R.id.menu_call_insecure);
      }

    }

    if (recipient != null && recipient.get().isBlocked()) {
      if (isSecureText) {
        buttonCallType[0] = "hide";
      } else {
        hideMenuItem(menu, R.id.menu_call_insecure);
      }
    }

    if (isSingleConversation() && recipient != null && recipient.get().getUnidentifiedAccessMode().toString().equals("DISABLED")) {
      buttonCallType[0] = "hide";
    }

    if (isSingleConversation()) {
      hideMenuItem(menu, R.id.menu_call_join_secure);
    }

    if (isGroupConversation()) {
      OkHttpClient client = new OkHttpClient();
      Recipient recipientSnapshot = recipient.get();
      String roomName = "yoush_" + recipientSnapshot.getGroupId().get();
      roomName = roomName.replaceAll("!","");
      Request request = new Request.Builder().url("/room-size?room=" + roomName).build();

      client.newCall(request).enqueue(new Callback() {
        @Override
        public void onFailure(Call call, IOException e) {

        }

        @Override
        public void onResponse(Call call, Response response) {
          if (response.code() == 404) {
            ConversationActivity.this.runOnUiThread(new Runnable() {

              @Override
              public void run() {

                switch(buttonCallType[0]) {
                  case "call":
                    // display call button
                    hideMenuItem(menu, R.id.menu_call_join_secure);
                    break;
                  case "join":
                    hideMenuItem(menu, R.id.menu_call_secure);
                    hideMenuItem(menu, R.id.menu_video_secure);
                    break;
                  case "hide":
                    hideMenuItem(menu, R.id.menu_call_secure);
                    hideMenuItem(menu, R.id.menu_video_secure);
                    hideMenuItem(menu, R.id.menu_call_join_secure);
                    break;
                  default:
                    hideMenuItem(menu, R.id.menu_call_join_secure);
                    break;
                  // code block
                }

              }
            });

          } else {

            ConversationActivity.this.runOnUiThread(new Runnable() {

              @Override
              public void run() {
                hideMenuItem(menu, R.id.menu_call_secure);
                hideMenuItem(menu, R.id.menu_video_secure);
              }
            });


          }
        }
      });
    }

    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
      case R.id.menu_call_secure:
        handleDial(getRecipient(), true);
        return true;
      case R.id.menu_video_secure:
        handleVideo(getRecipient());
        return true;
      case R.id.menu_call_insecure:
        handleDial(getRecipient(), false);
        return true;
      case R.id.menu_search:
        handleSearch();
        return true;
      case R.id.menu_distribution_broadcast:
        handleDistributionBroadcastEnabled(item);
        return true;
      case R.id.menu_distribution_conversation:
        handleDistributionConversationEnabled(item);
        return true;
      case R.id.menu_invite:
        handleInviteLink();
        return true;
      case android.R.id.home:
        onNavigateUpHome();
        return true;
      case R.id.menu_call_join_secure:
        try {

          Recipient recipientSnapshot = recipient.get();
          Recipient recipient1 = Recipient.self();

          String roomName = "yoush_" + recipientSnapshot.getGroupId().get();
          String name = recipient1.getProfileName().toString();

          JitsiMeetUserInfo userInfo = new JitsiMeetUserInfo();
          if (name != null && !name.isEmpty()) {
            userInfo.setDisplayName(name);
          } else {
            userInfo.setDisplayName(recipient1.getE164().get());
          }

          userInfo.setEmail(recipient1.getE164().get() + "@tapofthink.com");

          String nameTo = recipientSnapshot.getName(this);
          boolean isVideo = false;

          String jws = Util.getJitsiToken(name, recipient1.getE164().get() + "@tapofthink.com");

          String configOverride = "#config.disableAEC=false&config.p2p.enabled=false&config.disableNS=false";

          roomName = roomName.replaceAll("!","");
          JitsiMeetConferenceOptions options = new JitsiMeetConferenceOptions.Builder()
                  .setRoom(roomName)
                  .setServerURL(new URL("" + configOverride))
                  .setToken(jws)
                  .setUserInfo(userInfo)
                  .setVideoMuted(!isVideo)

                  .setFeatureFlag("chat.enabled", false)
                  .setFeatureFlag("add-people.enabled", false)
                  .setFeatureFlag("invite.enabled", false)
                  .setFeatureFlag("meeting-password.enabled", false)

                  .setFeatureFlag("live-streaming.enabled", false)
                  .setFeatureFlag("video-share.enabled", false)
                  .setFeatureFlag("recording.enabled", false)
                  .setFeatureFlag("call-integration.enabled", false)
                  .setFeatureFlag("name.to", nameTo)
//                  .setConfigOverride("disableAEC", false)
//                  .setConfigOverride("p2p.enabled", false)

  //                  .setWelcomePageEnabled(false)
                  .build();

          JitsiService.launch(this,options, false, getWindow());
        } catch (MalformedURLException | UnsupportedEncodingException e) {
          e.printStackTrace();
        }
        return true;
    }

    return false;
  }

  private void onNavigateUpHome() {
   finish();

  }


  @Override
  public boolean onMenuOpened(int featureId, Menu menu) {
    if (menu == null) {
      return super.onMenuOpened(featureId, null);
    }
    return super.onMenuOpened(featureId, menu);
  }

  @Override
  public void onBackPressed() {
    if (reactionOverlay.isShowing()) reactionOverlay.hide();
    else if (container.isInputOpen()) container.hideCurrentInput(composeText);
    else super.onBackPressed();
  }

  @Override
  public void onKeyboardShown() {
    inputPanel.onKeyboardShown();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onEvent(ReminderUpdateEvent event) {
    updateReminders();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  public void onAttachmentMediaClicked(@NonNull Media media) {
    linkPreviewViewModel.onUserCancel();
    startActivityForResult(MediaSendActivity.buildEditorIntent(ConversationActivity.this, Collections.singletonList(media), recipient.get(), composeText.getTextTrimmed(), sendButton.getSelectedTransport()), MEDIA_SENDER);
    container.hideCurrentInput(composeText);
  }

  @Override
  public void onAttachmentSelectorClicked(@NonNull AttachmentKeyboardButton button) {
    switch (button) {
      case GALLERY:
        AttachmentManager.selectGallery(this, MEDIA_SENDER, recipient.get(), composeText.getTextTrimmed(), sendButton.getSelectedTransport());
        break;
      case FILE:
        AttachmentManager.selectDocument(this, PICK_DOCUMENT);
        break;
      case GIF:
        AttachmentManager.selectGif(this, PICK_GIF, !isSecureText, recipient.get().getColor().toConversationColor(this));
        break;
//      case CONTACT:
//        AttachmentManager.selectContactInfo(this, PICK_CONTACT);
//        break;
      case LOCATION:
        AttachmentManager.selectLocation(this, PICK_LOCATION);
        break;
    }

    container.hideCurrentInput(composeText);
  }

  @Override
  public void onAttachmentPermissionsRequested() {
    Permissions.with(this)
               .request(Manifest.permission.READ_EXTERNAL_STORAGE)
               .onAllGranted(() -> viewModel.onAttachmentKeyboardOpen())
               .execute();
  }

  /**
   * Event Handlers
   */
  private void handleSelectMessageExpiration() {
    boolean activeGroup = isActiveGroup();

    if (isPushGroupConversation() && !activeGroup) {
      return;
    }

    ExpirationDialog.show(this, recipient.get().getExpireMessages(),
      expirationTime ->
        SimpleTask.run(
          getLifecycle(),
          () -> {
            if (activeGroup) {
              try {
                GroupManager.updateGroupTimer(ConversationActivity.this, getRecipient().requireGroupId().requirePush(), expirationTime);
              } catch (GroupInsufficientRightsException e) {
                Log.w(TAG, e);
                return ConversationActivity.this.getString(R.string.ManageGroupActivity_you_dont_have_the_rights_to_do_this);
              } catch (GroupNotAMemberException e) {
                Log.w(TAG, e);
                return ConversationActivity.this.getString(R.string.ManageGroupActivity_youre_not_a_member_of_the_group);
              } catch (GroupChangeFailedException | GroupChangeBusyException | IOException e) {
                Log.w(TAG, e);
                return ConversationActivity.this.getString(R.string.ManageGroupActivity_failed_to_update_the_group);
              }
            } else {
              DatabaseFactory.getRecipientDatabase(ConversationActivity.this).setExpireMessages(recipient.getId(), expirationTime);
              OutgoingExpirationUpdateMessage outgoingMessage = new OutgoingExpirationUpdateMessage(getRecipient(), System.currentTimeMillis(), expirationTime * 1000L);
              MessageSender.send(ConversationActivity.this, outgoingMessage, threadId, false, null);
            }
            return null;
          },
          (errorString) -> {
            if (errorString != null) {
              Toast.makeText(ConversationActivity.this, errorString, Toast.LENGTH_SHORT).show();
            } else {
              invalidateOptionsMenu();
              if (fragment != null) fragment.setLastSeen(0);
            }
          })
    );
  }

  private void handleConversationSettings() {
    if (isGroupConversation()) {
      handleManageGroup();
      return;
    }

    if (isInMessageRequest()) return;

    Intent intent = ManageRecipientActivity.newIntentFromConversation(this, recipient.getId());
    startActivitySceneTransition(intent, titleView.findViewById(R.id.contact_photo_image), "avatar");
  }

  private void handleUnblock() {
    BlockUnblockDialog.showUnblockFor(this, getLifecycle(), recipient.get(), () -> {
      SignalExecutors.BOUNDED.execute(() -> {
        RecipientUtil.unblock(ConversationActivity.this, recipient.get());
      });
    });
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  private void handleMakeDefaultSms() {
    Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
    intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
    startActivityForResult(intent, SMS_DEFAULT);
  }

  private void handleRegisterForSignal() {
    startActivity(RegistrationNavigationActivity.newIntentForReRegistration(this));
  }

  private void handleInviteLink() {
    String inviteText = getString(R.string.ConversationActivity_lets_switch_to_signal, getString(R.string.install_url));

    if (isDefaultSms) {
      composeText.appendInvite(inviteText);
    } else {
      Intent intent = new Intent(Intent.ACTION_SENDTO);
      intent.setData(Uri.parse("smsto:" + recipient.get().requireSmsAddress()));
      intent.putExtra("sms_body", inviteText);
      intent.putExtra(Intent.EXTRA_TEXT, inviteText);
      startActivity(intent);
    }
  }

  private void handleResetSecureSession() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.ConversationActivity_reset_secure_session_question);
    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setCancelable(true);
    builder.setMessage(R.string.ConversationActivity_this_may_help_if_youre_having_encryption_problems);
    builder.setPositiveButton(R.string.ConversationActivity_reset, (dialog, which) -> {
      if (isSingleConversation()) {
        final Context context = getApplicationContext();

        OutgoingEndSessionMessage endSessionMessage =
            new OutgoingEndSessionMessage(new OutgoingTextMessage(getRecipient(), "TERMINATE", 0, -1));

        new AsyncTask<OutgoingEndSessionMessage, Void, Long>() {
          @Override
          protected Long doInBackground(OutgoingEndSessionMessage... messages) {
            return MessageSender.send(context, messages[0], threadId, false, null);
          }

          @Override
          protected void onPostExecute(Long result) {
            sendComplete(result);
          }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, endSessionMessage);
      }
    });
    builder.setNegativeButton(android.R.string.cancel, null);
    builder.show();
  }

  private static void addIconToHomeScreen(@NonNull Context context,
                                          @NonNull Bitmap bitmap,
                                          @NonNull Recipient recipient)
  {
    IconCompat icon = IconCompat.createWithAdaptiveBitmap(bitmap);
    String     name = recipient.isLocalNumber() ? context.getString(R.string.note_to_self)
                                                  : recipient.getDisplayName(context);

    ShortcutInfoCompat shortcutInfoCompat = new ShortcutInfoCompat.Builder(context, recipient.getId().serialize() + '-' + System.currentTimeMillis())
                                                                  .setShortLabel(name)
                                                                  .setIcon(icon)
                                                                  .setIntent(ShortcutLauncherActivity.createIntent(context, recipient.getId()))
                                                                  .build();

    if (ShortcutManagerCompat.requestPinShortcut(context, shortcutInfoCompat, null)) {
      Toast.makeText(context, context.getString(R.string.ConversationActivity_added_to_home_screen), Toast.LENGTH_LONG).show();
    }

    bitmap.recycle();
  }

  private void handleSearch() {
    searchViewModel.onSearchOpened();
  }

  private void handleManageGroup() {
    startActivityForResult(ManageGroupActivity.newIntent(ConversationActivity.this, recipient.get().requireGroupId()),
                           GROUP_EDIT,
                           ManageGroupActivity.createTransitionBundle(this, titleView.findViewById(R.id.contact_photo_image)));
  }

  private void handleDistributionBroadcastEnabled(MenuItem item) {
    distributionType = ThreadDatabase.DistributionTypes.BROADCAST;
    item.setChecked(true);

    if (threadId != -1) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getThreadDatabase(ConversationActivity.this)
                         .setDistributionType(threadId, ThreadDatabase.DistributionTypes.BROADCAST);
          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  private void handleDistributionConversationEnabled(MenuItem item) {
    distributionType = ThreadDatabase.DistributionTypes.CONVERSATION;
    item.setChecked(true);

    if (threadId != -1) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getThreadDatabase(ConversationActivity.this)
                         .setDistributionType(threadId, ThreadDatabase.DistributionTypes.CONVERSATION);
          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  private void handleDial(final Recipient recipient, boolean isSecure) {
    if (recipient == null) return;

    if (isGroupConversation()) {
      sendMessageCallGroup(false, false, false);
    }

    if (isSingleConversation()) {
      sendMessageCallGroup(false, false, true);
    }
  }

  private void handleVideo(final Recipient recipient) {
    if (recipient == null) return;

    if (isGroupConversation()) {
      sendMessageCallGroup(false, true, false);
    }

    if (isSingleConversation()) {
      sendMessageCallGroup(false, true, true);
    }
  }

  private void handleAddToContacts() {
    if (recipient.get().isGroup()) return;

    try {
      startActivityForResult(RecipientExporter.export(recipient.get()).asAddContactIntent(), ADD_CONTACT);
    } catch (ActivityNotFoundException e) {
      Log.w(TAG, e);
    }
  }

  private boolean handleDisplayQuickContact() {
    if (isInMessageRequest() || recipient.get().isGroup()) return false;

    if (recipient.get().getContactUri() != null) {
      ContactsContract.QuickContact.showQuickContact(ConversationActivity.this, titleView, recipient.get().getContactUri(), ContactsContract.QuickContact.MODE_LARGE, null);
    } else {
      handleAddToContacts();
    }

    return true;
  }

  private void handleAddAttachment() {
    if (this.isMmsEnabled || isSecureText) {
      viewModel.getRecentMedia().removeObservers(this);

      if (attachmentKeyboardStub.resolved() && container.isInputOpen() && container.getCurrentInput() == attachmentKeyboardStub.get()) {
        container.showSoftkey(composeText);
      } else {
        viewModel.getRecentMedia().observe(this, media -> attachmentKeyboardStub.get().onMediaChanged(media));
        attachmentKeyboardStub.get().setCallback(this);
        container.show(composeText, attachmentKeyboardStub.get());

        viewModel.onAttachmentKeyboardOpen();
      }
    } else {
      handleManualMmsRequired();
    }
  }

  private void handleManualMmsRequired() {
    Toast.makeText(this, R.string.MmsDownloader_error_reading_mms_settings, Toast.LENGTH_LONG).show();

    Bundle extras = getIntent().getExtras();
    Intent intent = new Intent(this, PromptMmsActivity.class);
    if (extras != null) intent.putExtras(extras);
    startActivity(intent);
  }

  private void handleRecentSafetyNumberChange() {
    List<IdentityRecord> records = identityRecords.getUnverifiedRecords();
    records.addAll(identityRecords.getUntrustedRecords());
    SafetyNumberChangeDialog.create(records).show(getSupportFragmentManager(), SAFETY_NUMBER_DIALOG);
  }

  @Override
  public void onSendAnywayAfterSafetyNumberChange() {
    initializeIdentityRecords().addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        sendMessage();
      }
    });
  }

  @Override
  public void onMessageResentAfterSafetyNumberChange() {
    initializeIdentityRecords().addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) { }
    });
  }

  private void handleSecurityChange(boolean isSecureText, boolean isDefaultSms) {
    Log.i(TAG, "handleSecurityChange(" + isSecureText + ", " + isDefaultSms + ")");

    this.isSecureText          = isSecureText;
    this.isDefaultSms          = isDefaultSms;
    this.isSecurityInitialized = true;

    boolean isMediaMessage = recipient.get().isMmsGroup() || attachmentManager.isAttachmentPresent();

    sendButton.resetAvailableTransports(isMediaMessage);

    if (!isSecureText && !isPushGroupConversation()) sendButton.disableTransport(Type.TEXTSECURE);
    if (recipient.get().isPushGroup())            sendButton.disableTransport(Type.SMS);

    if (!recipient.get().isPushGroup() && recipient.get().isForceSmsSelection()) {
      sendButton.setDefaultTransport(Type.SMS);
    } else {
      if (isSecureText || isPushGroupConversation()) sendButton.setDefaultTransport(Type.TEXTSECURE);
      else                                           sendButton.setDefaultTransport(Type.SMS);
    }

    calculateCharactersRemaining();
    supportInvalidateOptionsMenu();
    setBlockedUserState(recipient.get(), isSecureText, isDefaultSms);
  }

  //Initializers
  private ListenableFuture<Boolean> initializeDraft() {
    final SettableFuture<Boolean> result = new SettableFuture<>();

    final String         draftText      = getIntent().getStringExtra(TEXT_EXTRA);
    final Uri            draftMedia     = getIntent().getData();
    final MediaType      draftMediaType = MediaType.from(getIntent().getType());
    final List<Media>    mediaList      = getIntent().getParcelableArrayListExtra(MEDIA_EXTRA);
    final StickerLocator stickerLocator = getIntent().getParcelableExtra(STICKER_EXTRA);

    if (stickerLocator != null && draftMedia != null) {
      Log.d(TAG, "Handling shared sticker.");
      sendSticker(stickerLocator, draftMedia, 0, true);
      return new SettableFuture<>(false);
    }

    if (!Util.isEmpty(mediaList)) {
      Log.d(TAG, "Handling shared Media.");
      Intent sendIntent = MediaSendActivity.buildEditorIntent(this, mediaList, recipient.get(), draftText, sendButton.getSelectedTransport());
      startActivityForResult(sendIntent, MEDIA_SENDER);
      return new SettableFuture<>(false);
    }

    if (draftText != null) {
      composeText.setText("");
      composeText.append(draftText);
      result.set(true);
    }

    if (draftMedia != null && draftMediaType != null) {
      Log.d(TAG, "Handling shared Data.");
      return setMedia(draftMedia, draftMediaType);
    }

    if (draftText == null && draftMedia == null && draftMediaType == null) {
      return initializeDraftFromDatabase();
    } else {
      updateToggleButtonState();
      result.set(false);
    }

    return result;
  }

  private void initializeEnabledCheck() {
    groupViewModel.getGroupActiveState().observe(this, state -> {
      boolean enabled = state == null || !(isPushGroupConversation() && !state.isActiveGroup());
      inputPanel.setEnabled(enabled);
      sendButton.setEnabled(enabled);
      attachButton.setEnabled(enabled);
    });
  }

  private ListenableFuture<Boolean> initializeDraftFromDatabase() {
    SettableFuture<Boolean> future = new SettableFuture<>();

    new AsyncTask<Void, Void, List<Draft>>() {
      @Override
      protected List<Draft> doInBackground(Void... params) {
        DraftDatabase draftDatabase = DatabaseFactory.getDraftDatabase(ConversationActivity.this);
        List<Draft> results         = draftDatabase.getDrafts(threadId);

        draftDatabase.clearDrafts(threadId);

        return results;
      }

      @Override
      protected void onPostExecute(List<Draft> drafts) {
        if (drafts.isEmpty()) {
          future.set(false);
          updateToggleButtonState();
          return;
        }

        AtomicInteger                      draftsRemaining = new AtomicInteger(drafts.size());
        AtomicBoolean                      success         = new AtomicBoolean(false);
        ListenableFuture.Listener<Boolean> listener        = new AssertedSuccessListener<Boolean>() {
          @Override
          public void onSuccess(Boolean result) {
            success.compareAndSet(false, result);

            if (draftsRemaining.decrementAndGet() <= 0) {
              future.set(success.get());
            }
          }
        };

        for (Draft draft : drafts) {
          try {
            switch (draft.getType()) {
              case Draft.TEXT:
                composeText.setText(draft.getValue());
                listener.onSuccess(true);
                break;
              case Draft.LOCATION:
                attachmentManager.setLocation(SignalPlace.deserialize(draft.getValue()), getCurrentMediaConstraints()).addListener(listener);
                break;
              case Draft.IMAGE:
                setMedia(Uri.parse(draft.getValue()), MediaType.IMAGE).addListener(listener);
                break;
              case Draft.AUDIO:
                setMedia(Uri.parse(draft.getValue()), MediaType.AUDIO).addListener(listener);
                break;
              case Draft.VIDEO:
                setMedia(Uri.parse(draft.getValue()), MediaType.VIDEO).addListener(listener);
                break;
              case Draft.QUOTE:
                SettableFuture<Boolean> quoteResult = new SettableFuture<>();
                new QuoteRestorationTask(draft.getValue(), quoteResult).execute();
                quoteResult.addListener(listener);
                break;
            }
          } catch (IOException e) {
            Log.w(TAG, e);
          }
        }

        updateToggleButtonState();
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

    return future;
  }

  private ListenableFuture<Boolean> initializeSecurity(
          final boolean currentSecureText,
                                                       final boolean currentIsDefaultSms)
  {
    final SettableFuture<Boolean> future = new SettableFuture<>();

    handleSecurityChange(currentSecureText || isPushGroupConversation(), currentIsDefaultSms);

    new AsyncTask<Recipient, Void, boolean[]>() {
      @Override
      protected boolean[] doInBackground(Recipient... params) {
        Context           context         = ConversationActivity.this;
        Recipient         recipient       = params[0].resolve();
        Log.i(TAG, "Resolving registered state...");
        RegisteredState registeredState;

        if (recipient.isPushGroup()) {
          Log.i(TAG, "Push group recipient...");
          registeredState = RegisteredState.REGISTERED;
        } else {
          Log.i(TAG, "Checking through resolved recipient");
          registeredState = recipient.resolve().getRegistered();
        }

        Log.i(TAG, "Resolved registered state: " + registeredState);
        boolean           signalEnabled   = TextSecurePreferences.isPushRegistered(context);

        if (registeredState == RegisteredState.UNKNOWN) {
          try {
            Log.i(TAG, "Refreshing directory for user: " + recipient.getId().serialize());
            registeredState = DirectoryHelper.refreshDirectoryFor(context, recipient, false);
          } catch (IOException e) {
            Log.w(TAG, e);
          }
        }

        Log.i(TAG, "Returning registered state...");
        return new boolean[] {registeredState == RegisteredState.REGISTERED && signalEnabled,
                              Util.isDefaultSmsProvider(context)};
      }

      @Override
      protected void onPostExecute(boolean[] result) {
        if (result[0] != currentSecureText || result[1] != currentIsDefaultSms) {
          Log.i(TAG, "onPostExecute() handleSecurityChange: " + result[0] + " , " + result[1]);
          handleSecurityChange(result[0], result[1]);
        }
        future.set(true);
        onSecurityUpdated();
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, recipient.get());

    return future;
  }

  private void onSecurityUpdated() {
    Log.i(TAG, "onSecurityUpdated()");
    updateReminders();
    updateDefaultSubscriptionId(recipient.get().getDefaultSubscriptionId());
  }

  private void initializeInsightObserver() {
    inviteReminderModel = new InviteReminderModel(this, new InviteReminderRepository(this));
    inviteReminderModel.loadReminder(recipient, this::updateReminders);
  }

  protected void updateReminders() {
    Optional<Reminder> inviteReminder = inviteReminderModel.getReminder();

    if (UnauthorizedReminder.isEligible(this)) {
      reminderView.get().showReminder(new UnauthorizedReminder(this));
    } else if (ExpiredBuildReminder.isEligible()) {
      reminderView.get().showReminder(new ExpiredBuildReminder(this));
    } else if (ServiceOutageReminder.isEligible(this)) {
      ApplicationDependencies.getJobManager().add(new ServiceOutageDetectionJob());
      reminderView.get().showReminder(new ServiceOutageReminder(this));
    } else if (TextSecurePreferences.isPushRegistered(this)      &&
               TextSecurePreferences.isShowInviteReminders(this) &&
               !isSecureText                                     &&
               inviteReminder.isPresent()                        &&
               !recipient.get().isGroup()) {
      reminderView.get().setOnActionClickListener(this::handleReminderAction);
      reminderView.get().setOnDismissListener(() -> inviteReminderModel.dismissReminder());
      reminderView.get().showReminder(inviteReminder.get());
    } else if (reminderView.resolved()) {
      reminderView.get().hide();
    }
  }

  private void handleReminderAction(@IdRes int reminderActionId) {
    switch (reminderActionId) {
      case R.id.reminder_action_invite:
        handleInviteLink();
        reminderView.get().requestDismiss();
        break;
      case R.id.reminder_action_view_insights:
        InsightsLauncher.showInsightsDashboard(getSupportFragmentManager());
        break;
      default:
        throw new IllegalArgumentException("Unknown ID: " + reminderActionId);
    }
  }

  private void updateDefaultSubscriptionId(Optional<Integer> defaultSubscriptionId) {
    Log.i(TAG, "updateDefaultSubscriptionId(" + defaultSubscriptionId.orNull() + ")");
    sendButton.setDefaultSubscriptionId(defaultSubscriptionId);
  }

  private void initializeMmsEnabledCheck() {
    new AsyncTask<Void, Void, Boolean>() {
      @Override
      protected Boolean doInBackground(Void... params) {
        return Util.isMmsCapable(ConversationActivity.this);
      }

      @Override
      protected void onPostExecute(Boolean isMmsEnabled) {
        ConversationActivity.this.isMmsEnabled = isMmsEnabled;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private ListenableFuture<Boolean> initializeIdentityRecords() {
    final SettableFuture<Boolean> future = new SettableFuture<>();

    new AsyncTask<Recipient, Void, Pair<IdentityRecordList, String>>() {
      @Override
      protected @NonNull Pair<IdentityRecordList, String> doInBackground(Recipient... params) {
        IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(ConversationActivity.this);
        List<Recipient>                     recipients;

        if (params[0].isGroup()) {
          recipients = DatabaseFactory.getGroupDatabase(ConversationActivity.this)
                                      .getGroupMembers(params[0].requireGroupId(), GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);
        } else {
          recipients = Collections.singletonList(params[0]);
        }

        long               startTime          =  System.currentTimeMillis();
        IdentityRecordList identityRecordList = identityDatabase.getIdentities(recipients);

        Log.i(TAG, String.format(Locale.US, "Loaded %d identities in %d ms", recipients.size(), System.currentTimeMillis() - startTime));

        String message = null;

        if (identityRecordList.isUnverified()) {
          message = IdentityUtil.getUnverifiedBannerDescription(ConversationActivity.this, identityRecordList.getUnverifiedRecipients());
        }

        return new Pair<>(identityRecordList, message);
      }

      @Override
      protected void onPostExecute(@NonNull Pair<IdentityRecordList, String> result) {
        Log.i(TAG, "Got identity records: " + result.first().isUnverified());
        identityRecords.replaceWith(result.first());

        if (result.second() != null) {
          Log.d(TAG, "Replacing banner...");
          unverifiedBannerView.get().display(result.second(), result.first().getUnverifiedRecords(),
                                             new UnverifiedClickedListener(),
                                             new UnverifiedDismissedListener());
        } else if (unverifiedBannerView.resolved()) {
          Log.d(TAG, "Clearing banner...");
          unverifiedBannerView.get().hide();
        }

        titleView.setVerified(isSecureText && identityRecords.isVerified());

        future.set(true);
      }

    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, recipient.get());

    return future;
  }

  private void initializeViews() {
    titleView                = findViewById(R.id.conversation_title_view);
    buttonToggle             = ViewUtil.findById(this, R.id.button_toggle);
    sendButton               = ViewUtil.findById(this, R.id.send_button);
    attachButton             = ViewUtil.findById(this, R.id.attach_button);
    composeText              = ViewUtil.findById(this, R.id.embedded_text_editor);
    charactersLeft           = ViewUtil.findById(this, R.id.space_left);
    emojiDrawerStub          = ViewUtil.findStubById(this, R.id.emoji_drawer_stub);
    attachmentKeyboardStub   = ViewUtil.findStubById(this, R.id.attachment_keyboard_stub);
    unblockButton            = ViewUtil.findById(this, R.id.unblock_button);
    makeDefaultSmsButton     = ViewUtil.findById(this, R.id.make_default_sms_button);
    registerButton           = ViewUtil.findById(this, R.id.register_button);
    container                = ViewUtil.findById(this, R.id.layout_container);
    reminderView             = ViewUtil.findStubById(this, R.id.reminder_stub);
    unverifiedBannerView     = ViewUtil.findStubById(this, R.id.unverified_banner_stub);
    groupShareProfileView    = ViewUtil.findStubById(this, R.id.group_share_profile_view_stub);
    quickAttachmentToggle    = ViewUtil.findById(this, R.id.quick_attachment_toggle);
    inlineAttachmentToggle   = ViewUtil.findById(this, R.id.inline_attachment_container);
    inputPanel               = ViewUtil.findById(this, R.id.bottom_panel);
    panelParent              = ViewUtil.findById(this, R.id.conversation_activity_panel_parent);
    searchNav                = ViewUtil.findById(this, R.id.conversation_search_nav);
    messageRequestBottomView = ViewUtil.findById(this, R.id.conversation_activity_message_request_bottom_bar);
    reactionOverlay          = ViewUtil.findById(this, R.id.conversation_reaction_scrubber);

    ImageButton quickCameraToggle      = ViewUtil.findById(this, R.id.quick_camera_toggle);
    ImageButton inlineAttachmentButton = ViewUtil.findById(this, R.id.inline_attachment_button);

    container.addOnKeyboardShownListener(this);
    inputPanel.setListener(this);
    inputPanel.setMediaListener(this);

    attachmentManager = new AttachmentManager(this, this);
    audioRecorder     = new AudioRecorder(this);
    typingTextWatcher = new TypingStatusTextWatcher();

    SendButtonListener        sendButtonListener        = new SendButtonListener();
    ComposeKeyPressedListener composeKeyPressedListener = new ComposeKeyPressedListener();

    composeText.setOnEditorActionListener(sendButtonListener);
    composeText.setCursorPositionChangedListener(this);
    attachButton.setOnClickListener(new AttachButtonListener());
    attachButton.setOnLongClickListener(new AttachButtonLongClickListener());
    sendButton.setOnClickListener(sendButtonListener);
    sendButton.setEnabled(true);
    sendButton.addOnTransportChangedListener((newTransport, manuallySelected) -> {
      calculateCharactersRemaining();
      updateLinkPreviewState();
      composeText.setTransport(newTransport);

      buttonToggle.getBackground().setColorFilter(newTransport.getBackgroundColor(), PorterDuff.Mode.MULTIPLY);
      buttonToggle.getBackground().invalidateSelf();

      if (manuallySelected) recordTransportPreference(newTransport);
    });

    titleView.setOnClickListener(v -> handleConversationSettings());
    titleView.setOnLongClickListener(v -> handleDisplayQuickContact());
    unblockButton.setOnClickListener(v -> handleUnblock());
    makeDefaultSmsButton.setOnClickListener(v -> handleMakeDefaultSms());
    registerButton.setOnClickListener(v -> handleRegisterForSignal());

    composeText.setOnKeyListener(composeKeyPressedListener);
    composeText.addTextChangedListener(composeKeyPressedListener);
    composeText.setOnEditorActionListener(sendButtonListener);
    composeText.setOnClickListener(composeKeyPressedListener);
    composeText.setOnFocusChangeListener(composeKeyPressedListener);

    if (Camera.getNumberOfCameras() > 0) {
      quickCameraToggle.setVisibility(View.VISIBLE);
      quickCameraToggle.setOnClickListener(new QuickCameraToggleListener());
    } else {
      quickCameraToggle.setVisibility(View.GONE);
    }

    searchNav.setEventListener(this);

    inlineAttachmentButton.setOnClickListener(v -> handleAddAttachment());

    reactionOverlay.setOnReactionSelectedListener(this);
  }

  protected void initializeActionBar() {
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    ActionBar supportActionBar = getSupportActionBar();
    if (supportActionBar == null) throw new AssertionError();

    supportActionBar.setDisplayHomeAsUpEnabled(true);
    supportActionBar.setDisplayShowTitleEnabled(false);
  }

  private void initializeResources() {
    if (recipient != null) {
      recipient.removeObservers(this);
    }

    recipient        = Recipient.live(getIntent().getParcelableExtra(RECIPIENT_EXTRA));
    threadId         = getIntent().getLongExtra(THREAD_ID_EXTRA, -1);
    distributionType = getIntent().getIntExtra(DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);
    glideRequests    = GlideApp.with(this);

    recipient.observe(this, this::onRecipientChanged);
  }


  private void initializeLinkPreviewObserver() {
    linkPreviewViewModel = ViewModelProviders.of(this, new LinkPreviewViewModel.Factory(new LinkPreviewRepository())).get(LinkPreviewViewModel.class);

    if (!TextSecurePreferences.isLinkPreviewsEnabled(this)) {
      linkPreviewViewModel.onUserCancel();
      return;
    }

    linkPreviewViewModel.getLinkPreviewState().observe(this, previewState -> {
      if (previewState == null) return;

      if (previewState.isLoading()) {
        Log.d(TAG, "Loading link preview.");
        inputPanel.setLinkPreviewLoading();
      } else {
        Log.d(TAG, "Setting link preview: " + previewState.getLinkPreview().isPresent());
        inputPanel.setLinkPreview(glideRequests, previewState.getLinkPreview());
      }

      updateToggleButtonState();
    });
  }

  private void initializeSearchObserver() {
    searchViewModel = ViewModelProviders.of(this).get(ConversationSearchViewModel.class);

    searchViewModel.getSearchResults().observe(this, result -> {
      if (result == null) return;

      if (!result.getResults().isEmpty()) {
        MessageResult messageResult = result.getResults().get(result.getPosition());
        fragment.jumpToMessage(messageResult.messageRecipient.getId(), messageResult.receivedTimestampMs, searchViewModel::onMissingResult);
      }

      searchNav.setData(result.getPosition(), result.getResults().size());
    });
  }

  private void initializeStickerObserver() {
    StickerSearchRepository repository = new StickerSearchRepository(this);

    stickerViewModel = ViewModelProviders.of(this, new ConversationStickerViewModel.Factory(getApplication(), repository))
                                         .get(ConversationStickerViewModel.class);

    stickerViewModel.getStickerResults().observe(this, stickers -> {
      if (stickers == null) return;

      inputPanel.setStickerSuggestions(stickers);
    });

    stickerViewModel.getStickersAvailability().observe(this, stickersAvailable -> {
      if (stickersAvailable == null) return;

      boolean           isSystemEmojiPreferred = TextSecurePreferences.isSystemEmojiPreferred(this);
      MediaKeyboardMode keyboardMode           = TextSecurePreferences.getMediaKeyboardMode(this);
      boolean           stickerIntro           = !TextSecurePreferences.hasSeenStickerIntroTooltip(this);

      if (stickersAvailable) {
        inputPanel.showMediaKeyboardToggle(true);
        inputPanel.setMediaKeyboardToggleMode(isSystemEmojiPreferred || keyboardMode == MediaKeyboardMode.STICKER);
        if (stickerIntro) showStickerIntroductionTooltip();
      }

      if (emojiDrawerStub.resolved()) {
        initializeMediaKeyboardProviders(emojiDrawerStub.get(), stickersAvailable);
      }
    });
  }

  private void initializeViewModel() {
    this.viewModel = ViewModelProviders.of(this, new ConversationViewModel.Factory()).get(ConversationViewModel.class);
  }

  private void initializeGroupViewModel() {
    groupViewModel = ViewModelProviders.of(this, new ConversationGroupViewModel.Factory()).get(ConversationGroupViewModel.class);
    recipient.observe(this, groupViewModel::onRecipientChange);
    groupViewModel.getGroupActiveState().observe(this, unused -> invalidateOptionsMenu());
  }

  private void showStickerIntroductionTooltip() {
    TextSecurePreferences.setMediaKeyboardMode(this, MediaKeyboardMode.STICKER);
    inputPanel.setMediaKeyboardToggleMode(true);

    TooltipPopup.forTarget(inputPanel.getMediaKeyboardToggleAnchorView())
                .setBackgroundTint(getResources().getColor(R.color.core_ultramarine))
                .setTextColor(getResources().getColor(R.color.core_white))
                .setText(R.string.ConversationActivity_new_say_it_with_stickers)
                .setOnDismissListener(() -> {
                  TextSecurePreferences.setHasSeenStickerIntroTooltip(this, true);
                  EventBus.getDefault().removeStickyEvent(StickerPackInstallEvent.class);
                })
                .show(TooltipPopup.POSITION_ABOVE);
  }

  @Override
  public void onReactionSelected(MessageRecord messageRecord, String emoji) {
    final Context context = getApplicationContext();

    reactionOverlay.hide();

    SignalExecutors.BOUNDED.execute(() -> {
      ReactionRecord oldRecord = Stream.of(messageRecord.getReactions())
                                       .filter(record -> record.getAuthor().equals(Recipient.self().getId()))
                                       .findFirst()
                                       .orElse(null);

      if (oldRecord != null && oldRecord.getEmoji().equals(emoji)) {
        MessageSender.sendReactionRemoval(context, messageRecord.getId(), messageRecord.isMms(), oldRecord);
      } else {
        MessageSender.sendNewReaction(context, messageRecord.getId(), messageRecord.isMms(), emoji);
      }
    });
  }

  @Override
  public void onCustomReactionSelected(@NonNull MessageRecord messageRecord, boolean hasAddedCustomEmoji) {
    ReactionRecord oldRecord = Stream.of(messageRecord.getReactions())
                                     .filter(record -> record.getAuthor().equals(Recipient.self().getId()))
                                     .findFirst()
                                     .orElse(null);

    if (oldRecord != null && hasAddedCustomEmoji) {
      final Context context = getApplicationContext();

      reactionOverlay.hide();

      SignalExecutors.BOUNDED.execute(() -> MessageSender.sendReactionRemoval(context,
                                                                              messageRecord.getId(),
                                                                              messageRecord.isMms(),
                                                                              oldRecord));
    } else {
      reactionOverlay.hideAllButMask();

      ReactWithAnyEmojiBottomSheetDialogFragment.createForMessageRecord(messageRecord)
                                                .show(getSupportFragmentManager(), "BOTTOM");
    }
  }

  @Override
  public void onReactWithAnyEmojiDialogDismissed() {
    reactionOverlay.hideMask();
  }

  @Override
  public void onSearchMoveUpPressed() {
    searchViewModel.onMoveUp();
  }

  @Override
  public void onSearchMoveDownPressed() {
    searchViewModel.onMoveDown();
  }

  private void initializeProfiles() {
    if (!isSecureText) {
      Log.i(TAG, "SMS contact, no profile fetch needed.");
      return;
    }

    RetrieveProfileJob.enqueueAsync(recipient.getId());
  }

  private void onRecipientChanged(@NonNull Recipient recipient) {
    Log.i(TAG, "onModified(" + recipient.getId() + ") " + recipient.getRegistered());
    titleView.setTitle(glideRequests, recipient);
    titleView.setVerified(identityRecords.isVerified());
    setBlockedUserState(recipient, isSecureText, isDefaultSms);
    setActionBarColor(recipient.getColor());
    updateReminders();
    updateDefaultSubscriptionId(recipient.getDefaultSubscriptionId());
    initializeSecurity(isSecureText, isDefaultSms);
    if (groupViewModel != null) {
      groupViewModel.onRecipientChange(recipient);
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onIdentityRecordUpdate(final IdentityRecord event) {
    initializeIdentityRecords();
  }

  @Subscribe(threadMode =  ThreadMode.MAIN, sticky = true)
  public void onStickerPackInstalled(final StickerPackInstallEvent event) {
    if (!TextSecurePreferences.hasSeenStickerIntroTooltip(this)) return;

    EventBus.getDefault().removeStickyEvent(event);

    if (!inputPanel.isStickerMode()) {
      TooltipPopup.forTarget(inputPanel.getMediaKeyboardToggleAnchorView())
                  .setText(R.string.ConversationActivity_sticker_pack_installed)
                  .setIconGlideModel(event.getIconGlideModel())
                  .show(TooltipPopup.POSITION_ABOVE);
    }
  }

  private void initializeReceivers() {
    securityUpdateReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
//        if (!intent.getAction().equals("CANCEL_JOIN_CALL_BROADCAST")) {
          initializeSecurity(isSecureText, isDefaultSms);
          calculateCharactersRemaining();
//        }
      }
    };

    registerReceiver(securityUpdateReceiver,
                     new IntentFilter(SecurityEvent.SECURITY_UPDATE_EVENT),
                     KeyCachingService.KEY_PERMISSION, null);

    registerReceiver(securityUpdateReceiver,
            new IntentFilter("CANCEL_JOIN_CALL_BROADCAST"),
            KeyCachingService.KEY_PERMISSION, null);
  }

  //////// Helper Methods

  private ListenableFuture<Boolean> setMedia(@Nullable Uri uri, @NonNull MediaType mediaType) {
    return setMedia(uri, mediaType, 0, 0, false);
  }

  private ListenableFuture<Boolean> setMedia(@Nullable Uri uri, @NonNull MediaType mediaType, int width, int height, boolean borderless) {
    if (uri == null) {
      return new SettableFuture<>(false);
    }

    if (MediaType.VCARD.equals(mediaType) && isSecureText) {
      openContactShareEditor(uri);
      return new SettableFuture<>(false);
    } else if (MediaType.IMAGE.equals(mediaType) || MediaType.GIF.equals(mediaType) || MediaType.VIDEO.equals(mediaType)) {
      String mimeType = MediaUtil.getMimeType(this, uri);
      if (mimeType == null) {
        mimeType = mediaType.toFallbackMimeType();
      }

      Media media = new Media(uri, mimeType, 0, width, height, 0, 0, borderless, Optional.absent(), Optional.absent(), Optional.absent());
      startActivityForResult(MediaSendActivity.buildEditorIntent(ConversationActivity.this, Collections.singletonList(media), recipient.get(), composeText.getTextTrimmed(), sendButton.getSelectedTransport()), MEDIA_SENDER);
      return new SettableFuture<>(false);
    } else {
      return attachmentManager.setMedia(glideRequests, uri, mediaType, getCurrentMediaConstraints(), width, height);
    }
  }

  private void openContactShareEditor(Uri contactUri) {
    Intent intent = ContactShareEditActivity.getIntent(this, Collections.singletonList(contactUri));
    startActivityForResult(intent, GET_CONTACT_DETAILS);
  }

//  private void addAttachmentContactInfo(Uri contactUri) {
//    ContactAccessor contactDataList = ContactAccessor.getInstance();
//    ContactData contactData = contactDataList.getContactData(this, contactUri);
//
//    if      (contactData.numbers.size() == 1) composeText.append(contactData.numbers.get(0).number);
//    else if (contactData.numbers.size() > 1)  selectContactInfo(contactData);
//  }

  private void sendSharedContact(List<Contact> contacts) {
    int        subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
    long       expiresIn      = recipient.get().getExpireMessages() * 1000L;
    boolean    initiating     = threadId == -1;

    sendMediaMessage(isSmsForced(), "", attachmentManager.buildSlideDeck(), null, contacts, Collections.emptyList(), expiresIn, false, subscriptionId, initiating, false);
  }

//  private void selectContactInfo(ContactData contactData) {
//    final CharSequence[] numbers     = new CharSequence[contactData.numbers.size()];
//    final CharSequence[] numberItems = new CharSequence[contactData.numbers.size()];
//
//    for (int i = 0; i < contactData.numbers.size(); i++) {
//      numbers[i]     = contactData.numbers.get(i).number;
//      numberItems[i] = contactData.numbers.get(i).type + ": " + contactData.numbers.get(i).number;
//    }
//
//    AlertDialog.Builder builder = new AlertDialog.Builder(this);
//    builder.setIconAttribute(R.attr.conversation_attach_contact_info);
//    builder.setTitle(R.string.ConversationActivity_select_contact_info);
//
//    builder.setItems(numberItems, (dialog, which) -> composeText.append(numbers[which]));
//    builder.show();
//  }

  private Drafts getDraftsForCurrentState() {
    Drafts drafts = new Drafts();

    if (!Util.isEmpty(composeText)) {
      drafts.add(new Draft(Draft.TEXT, composeText.getTextTrimmed()));
    }

    for (Slide slide : attachmentManager.buildSlideDeck().getSlides()) {
      if      (slide.hasAudio() && slide.getUri() != null)    drafts.add(new Draft(Draft.AUDIO, slide.getUri().toString()));
      else if (slide.hasVideo() && slide.getUri() != null)    drafts.add(new Draft(Draft.VIDEO, slide.getUri().toString()));
      else if (slide.hasLocation())                           drafts.add(new Draft(Draft.LOCATION, ((LocationSlide)slide).getPlace().serialize()));
      else if (slide.hasImage() && slide.getUri() != null)    drafts.add(new Draft(Draft.IMAGE, slide.getUri().toString()));
    }

    Optional<QuoteModel> quote = inputPanel.getQuote();

    if (quote.isPresent()) {
      drafts.add(new Draft(Draft.QUOTE, new QuoteId(quote.get().getId(), quote.get().getAuthor()).serialize()));
    }

    return drafts;
  }

  protected ListenableFuture<Long> saveDraft() {
    final SettableFuture<Long> future = new SettableFuture<>();

    if (this.recipient == null) {
      future.set(threadId);
      return future;
    }

    final Drafts       drafts               = getDraftsForCurrentState();
    final long         thisThreadId         = this.threadId;
    final int          thisDistributionType = this.distributionType;

    new AsyncTask<Long, Void, Long>() {
      @Override
      protected Long doInBackground(Long... params) {
        ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(ConversationActivity.this);
        DraftDatabase  draftDatabase  = DatabaseFactory.getDraftDatabase(ConversationActivity.this);
        long           threadId       = params[0];

        if (drafts.size() > 0) {
          if (threadId == -1) threadId = threadDatabase.getThreadIdFor(getRecipient(), thisDistributionType);

          draftDatabase.insertDrafts(threadId, drafts);
          threadDatabase.updateSnippet(threadId, drafts.getSnippet(ConversationActivity.this),
                                       drafts.getUriSnippet(),
                                       System.currentTimeMillis(), Types.BASE_DRAFT_TYPE, true);
        } else if (threadId > 0) {
          threadDatabase.update(threadId, false, false);
        }

        return threadId;
      }

      @Override
      protected void onPostExecute(Long result) {
        future.set(result);
      }

    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, thisThreadId);

    return future;
  }

  private void setActionBarColor(MaterialColor color) {
    ActionBar supportActionBar = getSupportActionBar();
    if (supportActionBar == null) throw new AssertionError();
    supportActionBar.setBackgroundDrawable(new ColorDrawable(color.toActionBarColor(this)));
    setStatusBarColor(color.toStatusBarColor(this));
  }

  private void setBlockedUserState(Recipient recipient, boolean isSecureText, boolean isDefaultSms) {
    if (!isSecureText && isPushGroupConversation()) {
      unblockButton.setVisibility(View.GONE);
      inputPanel.setVisibility(View.GONE);
      makeDefaultSmsButton.setVisibility(View.GONE);
      registerButton.setVisibility(View.VISIBLE);
    } else if (!isSecureText && !isDefaultSms) {
      unblockButton.setVisibility(View.GONE);
      inputPanel.setVisibility(View.GONE);
      makeDefaultSmsButton.setVisibility(View.VISIBLE);
      registerButton.setVisibility(View.GONE);
    } else {
      inputPanel.setVisibility(View.VISIBLE);
      unblockButton.setVisibility(View.GONE);
      makeDefaultSmsButton.setVisibility(View.GONE);
      registerButton.setVisibility(View.GONE);

      groupViewModel.getGroupActiveState().observe(this, state -> {
        boolean enabled = state == null || !(isPushGroupConversation() && !state.isActiveGroup());

        if (!enabled) {
          inputPanel.setVisibility(View.GONE);
        }
      });
    }
  }

  private void calculateCharactersRemaining() {
    String          messageBody     = composeText.getTextTrimmed();
    TransportOption transportOption = sendButton.getSelectedTransport();
    CharacterState  characterState  = transportOption.calculateCharacters(messageBody);

    if (characterState.charactersRemaining <= 15 || characterState.messagesSpent > 1) {
      charactersLeft.setText(String.format(dynamicLanguage.getCurrentLocale(),
                                           "%d/%d (%d)",
                                           characterState.charactersRemaining,
                                           characterState.maxTotalMessageSize,
                                           characterState.messagesSpent));
      charactersLeft.setVisibility(View.VISIBLE);
    } else {
      charactersLeft.setVisibility(View.GONE);
    }
  }

  private void initializeMediaKeyboardProviders(@NonNull MediaKeyboard mediaKeyboard, boolean stickersAvailable) {
    boolean isSystemEmojiPreferred   = TextSecurePreferences.isSystemEmojiPreferred(this);

    if (stickersAvailable) {
      if (isSystemEmojiPreferred) {
        mediaKeyboard.setProviders(0, new StickerKeyboardProvider(this, this));
      } else {
        MediaKeyboardMode keyboardMode = TextSecurePreferences.getMediaKeyboardMode(this);
        int               index        = keyboardMode == MediaKeyboardMode.STICKER ? 1 : 0;

        mediaKeyboard.setProviders(index,
                                   new EmojiKeyboardProvider(this, inputPanel),
                                   new StickerKeyboardProvider(this, this));
      }
    } else if (!isSystemEmojiPreferred) {
      mediaKeyboard.setProviders(0, new EmojiKeyboardProvider(this, inputPanel));
    }
  }

  private boolean isInMessageRequest() {
    return messageRequestBottomView.getVisibility() == View.VISIBLE;
  }

  private boolean isSingleConversation() {
    return getRecipient() != null && !getRecipient().isGroup();
  }

  private boolean isActiveGroup() {
    if (!isGroupConversation()) return false;

    Optional<GroupRecord> record = DatabaseFactory.getGroupDatabase(this).getGroup(getRecipient().getId());
    return record.isPresent() && record.get().isActive();
  }

  @SuppressWarnings("SimplifiableIfStatement")
  private boolean isSelfConversation() {
    if (!TextSecurePreferences.isPushRegistered(this)) return false;
    if (recipient.get().isGroup())                     return false;

    return recipient.get().isLocalNumber();
  }

  private boolean isGroupConversation() {
    return getRecipient() != null && getRecipient().isGroup();
  }

  private boolean isPushGroupConversation() {
    return getRecipient() != null && getRecipient().isPushGroup();
  }

  private boolean isPushGroupV1Conversation() {
    return getRecipient() != null && getRecipient().isPushV1Group();
  }

  private boolean isSmsForced() {
    return sendButton.isManualSelection() && sendButton.getSelectedTransport().isSms();
  }

  public Recipient getRecipient() {
    return this.recipient.get();
  }

  protected long getThreadId() {
    return this.threadId;
  }

  private String getMessage() throws InvalidMessageException {
    String rawText = composeText.getTextTrimmed();

    if (rawText.length() < 1 && !attachmentManager.isAttachmentPresent())
      throw new InvalidMessageException(getString(R.string.ConversationActivity_message_is_empty_exclamation));

    return rawText;
  }

  private MediaConstraints getCurrentMediaConstraints() {
    return sendButton.getSelectedTransport().getType() == Type.TEXTSECURE
           ? MediaConstraints.getPushMediaConstraints()
           : MediaConstraints.getMmsMediaConstraints(sendButton.getSelectedTransport().getSimSubscriptionId().or(-1));
  }

  private void markThreadAsRead() {
    new AsyncTask<Long, Void, Void>() {
      @Override
      protected Void doInBackground(Long... params) {
        Context                 context    = ConversationActivity.this;
        List<MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context).setRead(params[0], false);

        ApplicationDependencies.getMessageNotifier().updateNotification(context);
        MarkReadReceiver.process(context, messageIds);

        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, threadId);
  }

  private void markLastSeen() {
    new AsyncTask<Long, Void, Void>() {
      @Override
      protected Void doInBackground(Long... params) {
        DatabaseFactory.getThreadDatabase(ConversationActivity.this).setLastSeen(params[0]);
        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, threadId);
  }

  public void sendComplete(long threadId) {
    boolean refreshFragment = (threadId != this.threadId);
    this.threadId = threadId;

    if (fragment == null || !fragment.isVisible() || isFinishing()) {
      return;
    }

    fragment.setLastSeen(0);

    if (refreshFragment) {
      fragment.reload(recipient.get(), threadId);
      ApplicationDependencies.getMessageNotifier().setVisibleThread(threadId);
    }

    fragment.scrollToBottom();
    attachmentManager.cleanup();

    updateLinkPreviewState();


  }

  private void sendMessage() {
    if (inputPanel.isRecordingInLockedMode()) {
      inputPanel.releaseRecordingLock();
      return;
    }

    try {
      Recipient recipient = getRecipient();

      if (recipient == null) {
        throw new RecipientFormattingException("Badly formatted");
      }

      String          message        = getMessage();
      TransportOption transport      = sendButton.getSelectedTransport();
      boolean         forceSms       = (recipient.isForceSmsSelection() || sendButton.isManualSelection()) && transport.isSms();
      int             subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
      long            expiresIn      = recipient.getExpireMessages() * 1000L;
      boolean         initiating     = threadId == -1;
      boolean         needsSplit     = !transport.isSms() && message.length() > transport.calculateCharacters(message).maxPrimaryMessageSize;
      boolean         isMediaMessage = attachmentManager.isAttachmentPresent() ||
                                       recipient.isGroup()                     ||
                                       recipient.getEmail().isPresent()        ||
                                       inputPanel.getQuote().isPresent()       ||
                                       linkPreviewViewModel.hasLinkPreview()   ||
                                       needsSplit;

      Log.i(TAG, "isManual Selection: " + sendButton.isManualSelection());
      Log.i(TAG, "forceSms: " + forceSms);

      if ((recipient.isMmsGroup() || recipient.getEmail().isPresent()) && !isMmsEnabled) {
        handleManualMmsRequired();
      } else if (!forceSms && (identityRecords.isUnverified() || identityRecords.isUntrusted())) {
        handleRecentSafetyNumberChange();
      } else if (isMediaMessage) {
        sendMediaMessage(forceSms, expiresIn, false, subscriptionId, initiating);
      } else {
        sendTextMessage(forceSms, expiresIn, subscriptionId, initiating);
      }
    } catch (RecipientFormattingException ex) {
      Toast.makeText(ConversationActivity.this,
                     R.string.ConversationActivity_recipient_is_not_a_valid_sms_or_email_address_exclamation,
                     Toast.LENGTH_LONG).show();
      Log.w(TAG, ex);
    } catch (InvalidMessageException ex) {
      Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_message_is_empty_exclamation,
                     Toast.LENGTH_SHORT).show();
      Log.w(TAG, ex);
    }
  }

  private void sendMediaMessage(@NonNull MediaSendActivityResult result) {
    long                 expiresIn     = recipient.get().getExpireMessages() * 1000L;
    QuoteModel           quote         = result.isViewOnce() ? null : inputPanel.getQuote().orNull();
    boolean              initiating    = threadId == -1;
    OutgoingMediaMessage message       = new OutgoingMediaMessage(recipient.get(), new SlideDeck(), result.getBody(), System.currentTimeMillis(), -1, expiresIn, result.isViewOnce(), distributionType, quote, Collections.emptyList(), Collections.emptyList(), "");
    OutgoingMediaMessage secureMessage = new OutgoingSecureMediaMessage(message                                                                                                                                                                                      );

    ApplicationContext.getInstance(this).getTypingStatusSender().onTypingStopped(threadId);

    inputPanel.clearQuote();
    attachmentManager.clear(glideRequests, false);
    silentlySetComposeText("");

    long id = fragment.stageOutgoingMessage(message, true);

    SimpleTask.run(() -> {
      long resultId = MessageSender.sendPushWithPreUploadedMedia(this, secureMessage, result.getPreUploadResults(), threadId, () -> fragment.releaseOutgoingMessage(id));

      int deleted = DatabaseFactory.getAttachmentDatabase(this).deleteAbandonedPreuploadedAttachments();
      Log.i(TAG, "Deleted " + deleted + " abandoned attachments.");

      return resultId;
    }, this::sendComplete);
  }

  private void sendMediaMessage(final boolean forceSms, final long expiresIn, final boolean viewOnce, final int subscriptionId, final boolean initiating)
      throws InvalidMessageException
  {
    Log.i(TAG, "Sending media message...");
    sendMediaMessage(forceSms, getMessage(), attachmentManager.buildSlideDeck(), inputPanel.getQuote().orNull(), Collections.emptyList(), linkPreviewViewModel.getActiveLinkPreviews(), expiresIn, viewOnce, subscriptionId, initiating, true);
  }

  private ListenableFuture<Void> sendMediaMessage(final boolean forceSms,
                                                  @NonNull String body,
                                                  SlideDeck slideDeck,
                                                  QuoteModel quote,
                                                  List<Contact> contacts,
                                                  List<LinkPreview> previews,
                                                  final long expiresIn,
                                                  final boolean viewOnce,
                                                  final int subscriptionId,
                                                  final boolean initiating,
                                                  final boolean clearComposeBox)
  {
    if (!isDefaultSms && (!isSecureText || forceSms)) {
      showDefaultSmsPrompt();
      return new SettableFuture<>(null);
    }

    if (isSecureText && !forceSms) {
      MessageUtil.SplitResult splitMessage = MessageUtil.getSplitMessage(this, body, sendButton.getSelectedTransport().calculateCharacters(body).maxPrimaryMessageSize);
      body = splitMessage.getBody();

      if (splitMessage.getTextSlide().isPresent()) {
        slideDeck.addSlide(splitMessage.getTextSlide().get());
      }
    }

    OutgoingMediaMessage outgoingMessageCandidate = new OutgoingMediaMessage(recipient.get(), slideDeck, body, System.currentTimeMillis(), subscriptionId, expiresIn, viewOnce, distributionType, quote, contacts, previews, "");

    final SettableFuture<Void> future  = new SettableFuture<>();
    final Context              context = getApplicationContext();

    final OutgoingMediaMessage outgoingMessage;

    if (isSecureText && !forceSms) {
      outgoingMessage = new OutgoingSecureMediaMessage(outgoingMessageCandidate);
      ApplicationContext.getInstance(context).getTypingStatusSender().onTypingStopped(threadId);
    } else {
      outgoingMessage = outgoingMessageCandidate;
    }

    Permissions.with(this)
               .request(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS)
               .ifNecessary(!isSecureText || forceSms)
               .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_sms_permission_in_order_to_send_an_sms))
               .onAllGranted(() -> {
                 if (clearComposeBox) {
                   inputPanel.clearQuote();
                   attachmentManager.clear(glideRequests, false);
                   silentlySetComposeText("");
                 }

                 final long id = fragment.stageOutgoingMessage(outgoingMessage, true);


                 SimpleTask.run(() -> {
                   return MessageSender.send(context, outgoingMessage, threadId, forceSms, () -> fragment.releaseOutgoingMessage(id));
                 }, result -> {
                   sendComplete(result);
                   future.set(null);
                 });
               })
               .onAnyDenied(() -> future.set(null))
               .execute();

    return future;
  }

  private void sendTextMessage(final boolean forceSms, final long expiresIn, final int subscriptionId, final boolean initiating)
      throws InvalidMessageException
  {
    if (!isDefaultSms && (!isSecureText || forceSms)) {
      showDefaultSmsPrompt();
      return;
    }

    final Context context     = getApplicationContext();
    final String  messageBody = getMessage();

    OutgoingTextMessage message;

    if (isSecureText && !forceSms) {
      message = new OutgoingEncryptedMessage(recipient.get(), messageBody, expiresIn);
      ApplicationContext.getInstance(context).getTypingStatusSender().onTypingStopped(threadId);
    } else {
      message = new OutgoingTextMessage(recipient.get(), messageBody, expiresIn, subscriptionId);
    }

    Permissions.with(this)
               .request(Manifest.permission.SEND_SMS)
               .ifNecessary(forceSms || !isSecureText)
               .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_sms_permission_in_order_to_send_an_sms))
               .onAllGranted(() -> {
                 silentlySetComposeText("");
                 final long id = fragment.stageOutgoingMessage(message);

                 new AsyncTask<OutgoingTextMessage, Void, Long>() {
                   @Override
                   protected Long doInBackground(OutgoingTextMessage... messages) {
                     return MessageSender.send(context, messages[0], threadId, forceSms, () -> fragment.releaseOutgoingMessage(id));
                   }

                   @Override
                   protected void onPostExecute(Long result) {
                     sendComplete(result);
                   }
                 }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, message);

               })
               .execute();
  }

  private void showDefaultSmsPrompt() {
    new AlertDialog.Builder(this)
                   .setMessage(R.string.ConversationActivity_signal_cannot_sent_sms_mms_messages_because_it_is_not_your_default_sms_app)
                   .setNegativeButton(R.string.ConversationActivity_no, (dialog, which) -> dialog.dismiss())
                   .setPositiveButton(R.string.ConversationActivity_yes, (dialog, which) -> handleMakeDefaultSms())
                   .show();
  }

  private void updateToggleButtonState() {
    if (inputPanel.isRecordingInLockedMode()) {
      buttonToggle.display(sendButton);
      quickAttachmentToggle.show();
      inlineAttachmentToggle.hide();
      return;
    }

    if (composeText.getText().length() == 0 && !attachmentManager.isAttachmentPresent()) {
      buttonToggle.display(attachButton);
      quickAttachmentToggle.show();
      inlineAttachmentToggle.hide();
    } else {
      buttonToggle.display(sendButton);
      quickAttachmentToggle.hide();

      if (!attachmentManager.isAttachmentPresent() && !linkPreviewViewModel.hasLinkPreview()) {
        inlineAttachmentToggle.show();
      } else {
        inlineAttachmentToggle.hide();
      }
    }
  }

  private void updateLinkPreviewState() {
    if (TextSecurePreferences.isLinkPreviewsEnabled(this) && !sendButton.getSelectedTransport().isSms() && !attachmentManager.isAttachmentPresent()) {
      linkPreviewViewModel.onEnabled();
      linkPreviewViewModel.onTextChanged(this, composeText.getTextTrimmed(), composeText.getSelectionStart(), composeText.getSelectionEnd());
    } else {
      linkPreviewViewModel.onUserCancel();
    }
  }

  private void recordTransportPreference(TransportOption transportOption) {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(ConversationActivity.this);

        recipientDatabase.setDefaultSubscriptionId(recipient.getId(), transportOption.getSimSubscriptionId().or(-1));

        if (!recipient.resolve().isPushGroup()) {
          recipientDatabase.setForceSmsSelection(recipient.getId(), recipient.get().getRegistered() == RegisteredState.REGISTERED && transportOption.isSms());
        }

        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  @Override
  public void onRecorderPermissionRequired() {
    Permissions.with(this)
               .request(Manifest.permission.RECORD_AUDIO)
               .ifNecessary()
               .withRationaleDialog(getString(R.string.ConversationActivity_to_send_audio_messages_allow_signal_access_to_your_microphone), R.drawable.ic_mic_solid_24)
               .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_requires_the_microphone_permission_in_order_to_send_audio_messages))
               .execute();
  }

  @Override
  public void onRecorderStarted() {
    Vibrator vibrator = ServiceUtil.getVibrator(this);
    vibrator.vibrate(20);

    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    audioRecorder.startRecording();
  }

  @Override
  public void onRecorderLocked() {
    updateToggleButtonState();
  }

  @Override
  public void onRecorderFinished() {
    updateToggleButtonState();
    Vibrator vibrator = ServiceUtil.getVibrator(this);
    vibrator.vibrate(20);

    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    ListenableFuture<Pair<Uri, Long>> future = audioRecorder.stopRecording();
    future.addListener(new ListenableFuture.Listener<Pair<Uri, Long>>() {
      @Override
      public void onSuccess(final @NonNull Pair<Uri, Long> result) {
        boolean    forceSms       = sendButton.isManualSelection() && sendButton.getSelectedTransport().isSms();
        boolean    initiating     = threadId == -1;
        int        subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
        long       expiresIn      = recipient.get().getExpireMessages() * 1000L;
        AudioSlide audioSlide     = new AudioSlide(ConversationActivity.this, result.first(), result.second(), MediaUtil.AUDIO_AAC, true);
        SlideDeck  slideDeck      = new SlideDeck();
        slideDeck.addSlide(audioSlide);

        sendMediaMessage(forceSms, "", slideDeck, inputPanel.getQuote().orNull(), Collections.emptyList(), Collections.emptyList(), expiresIn, false, subscriptionId, initiating, true).addListener(new AssertedSuccessListener<Void>() {
          @Override
          public void onSuccess(Void nothing) {
            new AsyncTask<Void, Void, Void>() {
              @Override
              protected Void doInBackground(Void... params) {
                BlobProvider.getInstance().delete(ConversationActivity.this, result.first());
                return null;
              }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
          }
        });
      }

      @Override
      public void onFailure(ExecutionException e) {
        Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_unable_to_record_audio, Toast.LENGTH_LONG).show();
      }
    });
  }

  @Override
  public void onRecorderCanceled() {
    updateToggleButtonState();
    Vibrator vibrator = ServiceUtil.getVibrator(this);
    vibrator.vibrate(50);

    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    ListenableFuture<Pair<Uri, Long>> future = audioRecorder.stopRecording();
    future.addListener(new ListenableFuture.Listener<Pair<Uri, Long>>() {
      @Override
      public void onSuccess(final Pair<Uri, Long> result) {
        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... params) {
            BlobProvider.getInstance().delete(ConversationActivity.this, result.first());
            return null;
          }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
      }

      @Override
      public void onFailure(ExecutionException e) {}
    });
  }

  @Override
  public void onEmojiToggle() {
    if (!emojiDrawerStub.resolved()) {
      Boolean stickersAvailable = stickerViewModel.getStickersAvailability().getValue();

      initializeMediaKeyboardProviders(emojiDrawerStub.get(), stickersAvailable == null ? false : stickersAvailable);

      inputPanel.setMediaKeyboard(emojiDrawerStub.get());
    }

    if (container.getCurrentInput() == emojiDrawerStub.get()) {
      container.showSoftkey(composeText);
    } else {
      container.show(composeText, emojiDrawerStub.get());
    }
  }

  @Override
  public void onLinkPreviewCanceled() {
    linkPreviewViewModel.onUserCancel();
  }

  @Override
  public void onStickerSuggestionSelected(@NonNull StickerRecord sticker) {
    sendSticker(sticker, true);
  }

  @Override
  public void onMediaSelected(@NonNull Uri uri, String contentType) {
    if (MediaUtil.isGif(contentType) || MediaUtil.isImageType(contentType)) {
      SimpleTask.run(getLifecycle(),
                     () -> getKeyboardImageDetails(uri),
                     details -> sendKeyboardImage(uri, contentType, details));
    } else if (MediaUtil.isVideoType(contentType)) {
      setMedia(uri, MediaType.VIDEO);
    } else if (MediaUtil.isAudioType(contentType)) {
      setMedia(uri, MediaType.AUDIO);
    }
  }

  @Override
  public void onCursorPositionChanged(int start, int end) {
    linkPreviewViewModel.onTextChanged(this, composeText.getTextTrimmed(), start, end);
  }

  @Override
  public void onStickerSelected(@NonNull StickerRecord stickerRecord) {
    sendSticker(stickerRecord, false);
  }

  @Override
  public void onStickerManagementClicked() {
    startActivity(StickerManagementActivity.getIntent(this));
    container.hideAttachedInput(true);
  }

  private void sendSticker(@NonNull StickerRecord stickerRecord, boolean clearCompose) {
    sendSticker(new StickerLocator(stickerRecord.getPackId(), stickerRecord.getPackKey(), stickerRecord.getStickerId()), stickerRecord.getUri(), stickerRecord.getSize(), clearCompose);

    SignalExecutors.BOUNDED.execute(() ->
     DatabaseFactory.getStickerDatabase(getApplicationContext())
                    .updateStickerLastUsedTime(stickerRecord.getRowId(), System.currentTimeMillis())
    );
  }

  private void sendSticker(@NonNull StickerLocator stickerLocator, @NonNull Uri uri, long size, boolean clearCompose) {
    if (sendButton.getSelectedTransport().isSms()) {
      Media  media  = new Media(uri, MediaUtil.IMAGE_WEBP, System.currentTimeMillis(), StickerSlide.WIDTH, StickerSlide.HEIGHT, size, 0, false, Optional.absent(), Optional.absent(), Optional.absent());
      Intent intent = MediaSendActivity.buildEditorIntent(this, Collections.singletonList(media), recipient.get(), composeText.getTextTrimmed(), sendButton.getSelectedTransport());
      startActivityForResult(intent, MEDIA_SENDER);
      return;
    }

    long            expiresIn      = recipient.get().getExpireMessages() * 1000L;
    int             subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
    boolean         initiating     = threadId == -1;
    TransportOption transport      = sendButton.getSelectedTransport();
    SlideDeck       slideDeck      = new SlideDeck();
    Slide           stickerSlide   = new StickerSlide(this, uri, size, stickerLocator);

    slideDeck.addSlide(stickerSlide);

    sendMediaMessage(transport.isSms(), "", slideDeck, null, Collections.emptyList(), Collections.emptyList(), expiresIn, false, subscriptionId, initiating, clearCompose);
  }

  private void silentlySetComposeText(String text) {
    typingTextWatcher.setEnabled(false);
    composeText.setText(text);
    typingTextWatcher.setEnabled(true);
  }

  // Listeners

  private class QuickCameraToggleListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      Permissions.with(ConversationActivity.this)
                 .request(Manifest.permission.CAMERA)
                 .ifNecessary()
                 .withRationaleDialog(getString(R.string.ConversationActivity_to_capture_photos_and_video_allow_signal_access_to_the_camera), R.drawable.ic_camera_solid_24)
                 .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_the_camera_permission_to_take_photos_or_video))
                 .onAllGranted(() -> {
                   composeText.clearFocus();
                   startActivityForResult(MediaSendActivity.buildCameraIntent(ConversationActivity.this, recipient.get(), sendButton.getSelectedTransport()), MEDIA_SENDER);
                   overridePendingTransition(R.anim.camera_slide_from_bottom, R.anim.stationary);
                 })
                 .onAnyDenied(() -> Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_signal_needs_camera_permissions_to_take_photos_or_video, Toast.LENGTH_LONG).show())
                 .execute();
    }
  }

  private class SendButtonListener implements OnClickListener, TextView.OnEditorActionListener {
    @Override
    public void onClick(View v) {
      sendMessage();
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
      if (actionId == EditorInfo.IME_ACTION_SEND) {
        sendButton.performClick();
        return true;
      }
      return false;
    }
  }

  private class AttachButtonListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      handleAddAttachment();
    }
  }

  private class AttachButtonLongClickListener implements View.OnLongClickListener {
    @Override
    public boolean onLongClick(View v) {
      return sendButton.performLongClick();
    }
  }

  private class ComposeKeyPressedListener implements OnKeyListener, OnClickListener, TextWatcher, OnFocusChangeListener {

    int beforeLength;

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
          if (TextSecurePreferences.isEnterSendsEnabled(ConversationActivity.this)) {
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public void onClick(View v) {
      container.showSoftkey(composeText);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,int after) {
      beforeLength = composeText.getTextTrimmed().length();
    }

    @Override
    public void afterTextChanged(Editable s) {
      calculateCharactersRemaining();

      if (composeText.getTextTrimmed().length() == 0 || beforeLength == 0) {
        composeText.postDelayed(ConversationActivity.this::updateToggleButtonState, 50);
      }

      stickerViewModel.onInputTextUpdated(s.toString());
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before,int count) {}

    @Override
    public void onFocusChange(View v, boolean hasFocus) {}
  }

  private class TypingStatusTextWatcher extends SimpleTextWatcher {

    private boolean enabled = true;

    @Override
    public void onTextChanged(String text) {
      if (enabled && threadId > 0 && isSecureText && !isSmsForced() && !recipient.get().isBlocked()) {
        ApplicationContext.getInstance(ConversationActivity.this).getTypingStatusSender().onTypingStarted(threadId);
      }
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  @Override
  public void onMessageRequest(@NonNull MessageRequestViewModel viewModel) {
    messageRequestBottomView.setAcceptOnClickListener(v -> viewModel.onAccept(this::showGroupChangeErrorToast));
    messageRequestBottomView.setDeleteOnClickListener(v -> onMessageRequestDeleteClicked(viewModel));
    messageRequestBottomView.setBlockOnClickListener(v -> onMessageRequestBlockClicked(viewModel));
    messageRequestBottomView.setUnblockOnClickListener(v -> onMessageRequestUnblockClicked(viewModel));

    viewModel.getRecipient().observe(this, this::presentMessageRequestBottomViewTo);
    viewModel.getMessageRequestDisplayState().observe(this, this::presentMessageRequestDisplayState);
    viewModel.getMessageRequestStatus().observe(this, status -> {
      switch (status) {
        case ACCEPTED:
          messageRequestBottomView.setVisibility(View.GONE);
          return;
        case DELETED:
        case BLOCKED:
          finish();
      }
    });
  }

  private void showGroupChangeErrorToast(@NonNull GroupChangeFailureReason e) {
    Toast.makeText(this, GroupErrors.getUserDisplayMessage(e), Toast.LENGTH_LONG).show();
  }

  @Override
  public void handleReaction(@NonNull View maskTarget,
                             @NonNull MessageRecord messageRecord,
                             @NonNull Toolbar.OnMenuItemClickListener toolbarListener,
                             @NonNull ConversationReactionOverlay.OnHideListener onHideListener)
  {
    reactionOverlay.setOnToolbarItemClickedListener(toolbarListener);
    reactionOverlay.setOnHideListener(onHideListener);
    reactionOverlay.show(this, maskTarget, messageRecord, panelParent.getMeasuredHeight());
  }

  @Override
  public void onListVerticalTranslationChanged(float translationY) {
    reactionOverlay.setListVerticalTranslation(translationY);
  }

  @Override
  public void onMessageWithErrorClicked(@NonNull MessageRecord messageRecord) {
    if (messageRecord.hasFailedWithNetworkFailures()) {
      new AlertDialog.Builder(this)
                     .setMessage(R.string.conversation_activity__message_could_not_be_sent)
                     .setNegativeButton(android.R.string.cancel, null)
                     .setPositiveButton(R.string.conversation_activity__send, (dialog, which) -> MessageSender.resend(this, messageRecord))
                     .show();
    } else if (messageRecord.isIdentityMismatchFailure()) {
      SafetyNumberChangeDialog.create(this, messageRecord).show(getSupportFragmentManager(), SAFETY_NUMBER_DIALOG);
    } else {
      startActivity(MessageDetailsActivity.getIntentForMessageDetails(this, messageRecord, messageRecord.getRecipient().getId(), messageRecord.getThreadId()));
    }
  }

  @Override
  public void onCursorChanged() {
    if (!reactionOverlay.isShowing()) {
      return;
    }

    SimpleTask.run(() -> {
          //noinspection CodeBlock2Expr
          return DatabaseFactory.getMmsSmsDatabase(this)
                                .checkMessageExists(reactionOverlay.getMessageRecord());
        }, messageExists -> {
          if (!messageExists) {
            reactionOverlay.hide();
          }
        });
  }

  @Override
  public void setThreadId(long threadId) {
    this.threadId = threadId;
  }

  @Override
  public void handleReplyMessage(MessageRecord messageRecord) {
    Recipient author;
    if (messageRecord.isOutgoing()) {
      author = Recipient.self();
    } else {
      author = messageRecord.getIndividualRecipient();
    }
    if (messageRecord.isMms() && !((MmsMessageRecord) messageRecord).getSharedContacts().isEmpty()) {
      Contact   contact     = ((MmsMessageRecord) messageRecord).getSharedContacts().get(0);
      String    displayName = ContactUtil.getDisplayName(contact);
      String    body        = getString(R.string.ConversationActivity_quoted_contact_message, EmojiStrings.BUST_IN_SILHOUETTE, displayName);
      SlideDeck slideDeck   = new SlideDeck();

      if (contact.getAvatarAttachment() != null) {
        slideDeck.addSlide(MediaUtil.getSlideForAttachment(this, contact.getAvatarAttachment()));
      }

      inputPanel.setQuote(GlideApp.with(this),
                          messageRecord.getDateSent(),
                          author,
                          body,
                          slideDeck);

    } else if (messageRecord.isMms() && !((MmsMessageRecord) messageRecord).getLinkPreviews().isEmpty()) {
      LinkPreview linkPreview = ((MmsMessageRecord) messageRecord).getLinkPreviews().get(0);
      SlideDeck   slideDeck   = new SlideDeck();

      if (linkPreview.getThumbnail().isPresent()) {
        slideDeck.addSlide(MediaUtil.getSlideForAttachment(this, linkPreview.getThumbnail().get()));
      }

      inputPanel.setQuote(GlideApp.with(this),
                          messageRecord.getDateSent(),
                          author,
                          messageRecord.getBody(),
                          slideDeck);
    } else {
      SlideDeck slideDeck = messageRecord.isMms() ? ((MmsMessageRecord) messageRecord).getSlideDeck() : new SlideDeck();

      if (messageRecord.isMms() && ((MmsMessageRecord) messageRecord).isViewOnce()) {
        Attachment attachment = new TombstoneAttachment(MediaUtil.VIEW_ONCE, true);
        slideDeck = new SlideDeck();
        slideDeck.addSlide(MediaUtil.getSlideForAttachment(this, attachment));
      }

      inputPanel.setQuote(GlideApp.with(this),
                          messageRecord.getDateSent(),
                          author,
                          messageRecord.getBody(),
                          slideDeck);
    }

    inputPanel.clickOnComposeInput();
  }

  @Override
  public void onMessageActionToolbarOpened() {

  }

  @Override
  public void onForwardClicked()  {
    inputPanel.clearQuote();
  }

  @Override
  public void onAttachmentChanged() {
    handleSecurityChange(isSecureText, isDefaultSms);
    updateToggleButtonState();
    updateLinkPreviewState();
  }

  private void onMessageRequestDeleteClicked(@NonNull MessageRequestViewModel requestModel) {
    Recipient recipient = requestModel.getRecipient().getValue();
    if (recipient == null) {
      Log.w(TAG, "[onMessageRequestDeleteClicked] No recipient!");
      return;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(this)
                                                 .setNeutralButton(R.string.ConversationActivity_cancel, (d, w) -> d.dismiss());

    if (recipient.isGroup() && recipient.isBlocked()) {
      builder.setTitle(R.string.ConversationActivity_delete_conversation);
      builder.setMessage(R.string.ConversationActivity_this_conversation_will_be_deleted_from_all_of_your_devices);
      builder.setPositiveButton(R.string.ConversationActivity_delete, (d, w) -> requestModel.onDelete());
    } else if (recipient.isGroup()) {
      builder.setTitle(R.string.ConversationActivity_delete_and_leave_group);
      builder.setMessage(R.string.ConversationActivity_you_will_leave_this_group_and_it_will_be_deleted_from_all_of_your_devices);
      builder.setNegativeButton(R.string.ConversationActivity_delete_and_leave, (d, w) -> requestModel.onDelete());
    } else {
      builder.setTitle(R.string.ConversationActivity_delete_conversation);
      builder.setMessage(R.string.ConversationActivity_this_conversation_will_be_deleted_from_all_of_your_devices);
      builder.setNegativeButton(R.string.ConversationActivity_delete, (d, w) -> requestModel.onDelete());
    }

    builder.show();
  }

  private void onMessageRequestBlockClicked(@NonNull MessageRequestViewModel requestModel) {
    Recipient recipient = requestModel.getRecipient().getValue();
    if (recipient == null) {
      Log.w(TAG, "[onMessageRequestBlockClicked] No recipient!");
      return;
    }

    BlockUnblockDialog.showBlockAndDeleteFor(this, getLifecycle(), recipient, requestModel::onBlock, requestModel::onBlockAndDelete);
  }

  private void onMessageRequestUnblockClicked(@NonNull MessageRequestViewModel requestModel) {
    Recipient recipient = requestModel.getRecipient().getValue();
    if (recipient == null) {
      Log.w(TAG, "[onMessageRequestUnblockClicked] No recipient!");
      return;
    }

    BlockUnblockDialog.showUnblockFor(this, getLifecycle(), recipient, requestModel::onUnblock);
  }

  private void presentMessageRequestDisplayState(@NonNull MessageRequestViewModel.DisplayState displayState) {
    if (getIntent().hasExtra(TEXT_EXTRA) || getIntent().hasExtra(MEDIA_EXTRA) || getIntent().hasExtra(STICKER_EXTRA)) {
      Log.d(TAG, "[presentMessageRequestDisplayState] Have extra, so ignoring provided state.");
      messageRequestBottomView.setVisibility(View.GONE);
    } else if (isPushGroupV1Conversation() && !isActiveGroup()) {
      Log.d(TAG, "[presentMessageRequestDisplayState] Inactive push group V1, so ignoring provided state.");
      messageRequestBottomView.setVisibility(View.GONE);
    } else {
      Log.d(TAG, "[presentMessageRequestDisplayState] " + displayState);
      switch (displayState) {
        case DISPLAY_MESSAGE_REQUEST:
          messageRequestBottomView.setVisibility(View.VISIBLE);
          if (groupShareProfileView.resolved()) {
            groupShareProfileView.get().setVisibility(View.GONE);
          }
          break;
        case DISPLAY_LEGACY:
          if (recipient.get().isGroup()) {
            groupShareProfileView.get().setRecipient(recipient.get());
            groupShareProfileView.get().setVisibility(View.VISIBLE);
          }
          messageRequestBottomView.setVisibility(View.GONE);
          break;
        case DISPLAY_NONE:
          messageRequestBottomView.setVisibility(View.GONE);
          if (groupShareProfileView.resolved()) {
            groupShareProfileView.get().setVisibility(View.GONE);
          }
          break;
      }
    }

    invalidateOptionsMenu();
  }

  private static void hideMenuItem(@NonNull Menu menu, @IdRes int menuItem) {
    if (menu.findItem(menuItem) != null) {
      menu.findItem(menuItem).setVisible(false);
    }
  }

  @WorkerThread
  private @Nullable KeyboardImageDetails getKeyboardImageDetails(@NonNull Uri uri) {
    try {
      Bitmap bitmap = glideRequests.asBitmap()
                                   .load(uri)
                                   .skipMemoryCache(true)
                                   .diskCacheStrategy(DiskCacheStrategy.NONE)
                                   .submit()
                                   .get(1000, TimeUnit.MILLISECONDS);
      int topLeft = bitmap.getPixel(0, 0);
      return new KeyboardImageDetails(bitmap.getWidth(), bitmap.getHeight(), Color.alpha(topLeft) < 255);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      return null;
    }
  }

  private void sendKeyboardImage(@NonNull Uri uri, @NonNull String contentType, @Nullable KeyboardImageDetails details) {
    if (details == null || !details.hasTransparency) {
      setMedia(uri, Objects.requireNonNull(MediaType.from(contentType)));
      return;
    }

    long       expiresIn      = recipient.get().getExpireMessages() * 1000L;
    int        subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
    boolean    initiating     = threadId == -1;
    QuoteModel quote          = inputPanel.getQuote().orNull();
    SlideDeck  slideDeck      = new SlideDeck();

    if (MediaUtil.isGif(contentType)) {
      slideDeck.addSlide(new GifSlide(this, uri, 0, details.width, details.height, details.hasTransparency, null));
    } else if (MediaUtil.isImageType(contentType)) {
      slideDeck.addSlide(new ImageSlide(this, uri, contentType, 0, details.width, details.height, details.hasTransparency, null, null));
    } else {
      throw new AssertionError("Only images are supported!");
    }

    sendMediaMessage(isSmsForced(),
                     "",
                     slideDeck,
                     quote,
                     Collections.emptyList(),
                     Collections.emptyList(),
                     expiresIn,
                     false,
                     subscriptionId,
                     initiating,
                     true);
  }

  private class UnverifiedDismissedListener implements UnverifiedBannerView.DismissListener {
    @Override
    public void onDismissed(final List<IdentityRecord> unverifiedIdentities) {
      final IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(ConversationActivity.this);

      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          synchronized (SESSION_LOCK) {
            for (IdentityRecord identityRecord : unverifiedIdentities) {
              identityDatabase.setVerified(identityRecord.getRecipientId(),
                                           identityRecord.getIdentityKey(),
                                           VerifiedStatus.DEFAULT);
            }
          }

          return null;
        }

        @Override
        protected void onPostExecute(Void result) {
          initializeIdentityRecords();
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  private class UnverifiedClickedListener implements UnverifiedBannerView.ClickListener {
    @Override
    public void onClicked(final List<IdentityRecord> unverifiedIdentities) {
      Log.i(TAG, "onClicked: " + unverifiedIdentities.size());
      if (unverifiedIdentities.size() == 1) {
        startActivity(VerifyIdentityActivity.newIntent(ConversationActivity.this, unverifiedIdentities.get(0), false));
      } else {
        String[] unverifiedNames = new String[unverifiedIdentities.size()];

        for (int i=0;i<unverifiedIdentities.size();i++) {
          unverifiedNames[i] = Recipient.resolved(unverifiedIdentities.get(i).getRecipientId()).getDisplayName(ConversationActivity.this);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(ConversationActivity.this);
        builder.setIconAttribute(R.attr.dialog_alert_icon);
        builder.setTitle("No longer verified");
        builder.setItems(unverifiedNames, (dialog, which) -> {
          startActivity(VerifyIdentityActivity.newIntent(ConversationActivity.this, unverifiedIdentities.get(which), false));
        });
        builder.show();
      }
    }
  }

  private class QuoteRestorationTask extends AsyncTask<Void, Void, MessageRecord> {

    private final String                  serialized;
    private final SettableFuture<Boolean> future;

    QuoteRestorationTask(@NonNull String serialized, @NonNull SettableFuture<Boolean> future) {
      this.serialized = serialized;
      this.future     = future;
    }

    @Override
    protected MessageRecord doInBackground(Void... voids) {
      QuoteId quoteId = QuoteId.deserialize(ConversationActivity.this, serialized);

      if (quoteId != null) {
        return DatabaseFactory.getMmsSmsDatabase(getApplicationContext()).getMessageFor(quoteId.getId(), quoteId.getAuthor());
      }

      return null;
    }

    @Override
    protected void onPostExecute(MessageRecord messageRecord) {
      if (messageRecord != null) {
        handleReplyMessage(messageRecord);
        future.set(true);
      } else {
        Log.e(TAG, "Failed to restore a quote from a draft. No matching message record.");
        future.set(false);
      }
    }
  }

  private void presentMessageRequestBottomViewTo(@Nullable Recipient recipient) {
    if (recipient == null) return;

    messageRequestBottomView.setRecipient(recipient);
  }

  private static class KeyboardImageDetails {
    private final int     width;
    private final int     height;
    private final boolean hasTransparency;

    private KeyboardImageDetails(int width, int height, boolean hasTransparency) {
      this.width           = width;
      this.height          = height;
      this.hasTransparency = hasTransparency;
    }
  }

  /* CALL GROUP FEATURE */

  /* step 1: when click button call */
  private void sendMessageCallGroup(boolean isHostEndCall, boolean isVideo, boolean isOneOne) {

    try {
      Recipient recipientSnapshot = recipient.get();
      Recipient recipient = getRecipient();
      Recipient recipient1 = Recipient.self();

      String roomName = "yoush_";
      String subject = "";

      String messageType = "";

      String callerName = "";

      String nameTo = "";

      if (isOneOne || !recipientSnapshot.getGroupId().isPresent()) {

        String phoneNumber1 = recipient1.getE164().get();
        String phoneNumber2 = recipientSnapshot.getE164().get();

        if (phoneNumber1.compareTo(phoneNumber2) == 1) {
          roomName = roomName + phoneNumber1 + "_" + phoneNumber2;
        } else {
          roomName = roomName + phoneNumber2 + "_" + phoneNumber1;
        }


        subject = null;
        messageType = "call";

        if (recipient.getProfileName().isEmpty()) {
          nameTo = recipient.getE164().get();
        } else {
          nameTo = recipient.getProfileName().toString();
        }

        callerName = recipient1.getProfileName().toString();
      } else {
        roomName = roomName + recipientSnapshot.getGroupId().get();
        subject = recipientSnapshot.getName(this);
        messageType = "groupCall";
        callerName = null;
        nameTo = subject;
      }

      String callState = "dialing";
      if (isHostEndCall) {
        callState = "endCall";
      } else {
        DatabaseFactory.getSmsDatabase(this).insertOutgoingCall(recipientSnapshot.getId());
      }

      roomName = roomName.replaceAll("!","");
      String          callObject        = "{\"messageType\":\""+messageType+"\", \"room\": \""+roomName+"\", \"message\": \"Cuộc gọi\", \"state\":\""+callState+"\", \"audioOnly\":\""+!isVideo+"\", \"caller\":{\"uuid\":\""+recipient1.getUuid().get()+"\", \"phoneNumber\":\""+recipient1.getE164().get()+"\", \"callerName\": \""+callerName+"\"}, \"subject\": \""+subject+"\",\"callId\": \""+recipient1.getUuid().get()+"\"}";
      String          message     = "{\"messageType\":\""+messageType+"\", \"room\": \""+roomName+"\", \"message\": \"Cuộc gọi\", \"state\":\""+callState+"\", \"audioOnly\":\""+!isVideo+"\", \"caller\":{\"uuid\":\""+recipient1.getUuid().get()+"\", \"phoneNumber\":\""+recipient1.getE164().get()+"\", \"callerName\": \""+callerName+"\"}, \"subject\": \""+subject+"\",\"callId\": \""+recipient1.getUuid().get()+"\"}";

      TransportOption transport      = sendButton.getSelectedTransport();
      boolean         forceSms       = (recipient.isForceSmsSelection() || sendButton.isManualSelection()) && transport.isSms();
      int             subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
      long            expiresIn      = recipient.getExpireMessages() * 1000L;
      boolean         initiating     = threadId == -1;
      boolean         isMediaMessage = true;

      Log.i(TAG, "isManual Selection: " + sendButton.isManualSelection());
      Log.i(TAG, "forceSms: " + forceSms);

      if ((recipient.isMmsGroup() || recipient.getEmail().isPresent()) && !isMmsEnabled) {
        handleManualMmsRequired();
      } else if (!forceSms && (identityRecords.isUnverified() || identityRecords.isUntrusted())) {
        handleRecentSafetyNumberChange();
      } else if (isMediaMessage) {

        if (!isHostEndCall) {
          OkHttpClient client = new OkHttpClient();

          Request request = new Request.Builder().url("/room-size?room=" + roomName).build();

          client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
              try {
                if (response.code() == 404) {
                  sendMediaMessageCallGroup(forceSms, expiresIn, false, subscriptionId, initiating, message, callObject);
                }
              } catch (InvalidMessageException e) {
                e.printStackTrace();
              }
            }
          });
        }

        if (isHostEndCall) {
          sendMediaMessageCallGroup(forceSms, expiresIn, false, subscriptionId, initiating, message, callObject);
          Intent intent = new Intent(ConversationActivity.this, GroupCallBeginService.class);
          intent.setAction(GroupCallBeginService.ACTION_LOCAL_HANGUP)
                  .putExtra(GroupCallBeginService.EXTRA_REMOTE_PEER, new RemotePeer(recipient.getId()));
          startService(intent);
        }

        if (!isHostEndCall) {

          Intent intent = new Intent(ConversationActivity.this, GroupCallBeginService.class);
          intent.setAction(GroupCallBeginService.ACTION_OUTGOING_CALL)
                  .putExtra(GroupCallBeginService.EXTRA_REMOTE_PEER, new RemotePeer(recipient.getId()));

          startService(intent);

          JitsiMeetUserInfo userInfo = new JitsiMeetUserInfo();

          String name = recipient1.getProfileName().toString();

          if (name != null && !name.isEmpty()) {
            userInfo.setDisplayName(name);
          } else {
            userInfo.setDisplayName(recipient1.getE164().get());
          }

          userInfo.setEmail(recipient1.getE164().get() + "@tapofthink.com");

          String jws = Util.getJitsiToken(name, recipient1.getE164().get() + "@tapofthink.com");

          Boolean isScreenSecurityEnabled = TextSecurePreferences.isScreenSecurityEnabled(this);

          String configOverride = "#config.disableAEC=false&config.p2p.enabled=false&config.disableNS=false";

          JitsiMeetConferenceOptions options = new JitsiMeetConferenceOptions.Builder()
                  .setRoom(roomName)
                  .setServerURL(new URL("" + configOverride))
                  .setToken(jws)
                  .setUserInfo(userInfo)
                  .setVideoMuted(!isVideo)

                  .setFeatureFlag("chat.enabled", false)
                  .setFeatureFlag("add-people.enabled", false)
                  .setFeatureFlag("invite.enabled", false)
                  .setFeatureFlag("meeting-password.enabled", false)

                  .setFeatureFlag("live-streaming.enabled", false)
                  .setFeatureFlag("video-share.enabled", false)
                  .setFeatureFlag("recording.enabled", false)
                  .setFeatureFlag("call-integration.enabled", false)
                  .setFeatureFlag("name.to", nameTo)
                  // .setConfigOverride("disableAEC", false)
                  // .setConfigOverride("p2p.enabled", false)

//                  .setWelcomePageEnabled(false)
                  .build();
          JitsiService.launch(this,options, false, getWindow());
        }


      } else {
        sendTextMessage(forceSms, expiresIn, subscriptionId, initiating);
      }
    } catch (InvalidMessageException | MalformedURLException | UnsupportedEncodingException ex) {
      Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_message_is_empty_exclamation,
              Toast.LENGTH_SHORT).show();
      Log.w(TAG, ex);
    }
  }

  private void resolveRestrictions() {
    RestrictionsManager manager =
            (RestrictionsManager) getSystemService(Context.RESTRICTIONS_SERVICE);
    Bundle restrictions = manager.getApplicationRestrictions();
    Collection<RestrictionEntry> entries = manager.getManifestRestrictions(
            getApplicationContext().getPackageName());
    for (RestrictionEntry restrictionEntry : entries) {
      String key = restrictionEntry.getKey();
      if (RESTRICTION_SERVER_URL.equals(key)) {
        //If restrictions are passed to the application.
        if (restrictions != null &&
                restrictions.containsKey(RESTRICTION_SERVER_URL)) {
          //defaultURL = restrictions.getString(RESTRICTION_SERVER_URL);
          configurationByRestrictions = true;
          //Otherwise use default URL from app-restrictions.xml.
        } else {
          //defaultURL = restrictionEntry.getSelectedString();
          configurationByRestrictions = false;
        }
      }
    }
  }

  /* step 2:  */
  private void sendMediaMessageCallGroup(final boolean forceSms, final long expiresIn, final boolean viewOnce, final int subscriptionId, final boolean initiating, String message, String callObject)
          throws InvalidMessageException
  {
    Log.i(TAG, "Sending callGroup...");
    sendMediaMessageCallGroup(forceSms, message, callObject, attachmentManager.buildSlideDeck(), inputPanel.getQuote().orNull(), Collections.emptyList(), linkPreviewViewModel.getActiveLinkPreviews(), expiresIn, viewOnce, subscriptionId, initiating, true);
  }

  /* step 3: */
  private ListenableFuture<Void> sendMediaMessageCallGroup(final boolean forceSms,
                                                  @NonNull String body,
                                                           String callObject,
                                                  SlideDeck slideDeck,
                                                  QuoteModel quote,
                                                  List<Contact> contacts,
                                                  List<LinkPreview> previews,
                                                  final long expiresIn,
                                                  final boolean viewOnce,
                                                  final int subscriptionId,
                                                  final boolean initiating,
                                                  final boolean clearComposeBox)
  {
    if (!isDefaultSms && (!isSecureText || forceSms)) {
      showDefaultSmsPrompt();
      return new SettableFuture<>(null);
    }

    if (isSecureText && !forceSms) {
      MessageUtil.SplitResult splitMessage = MessageUtil.getSplitMessage(this, body, sendButton.getSelectedTransport().calculateCharacters(body).maxPrimaryMessageSize);
      body = splitMessage.getBody();

      if (splitMessage.getTextSlide().isPresent()) {
        slideDeck.addSlide(splitMessage.getTextSlide().get());
      }
    }

    OutgoingMediaMessage outgoingMessageCandidate = new OutgoingMediaMessage(recipient.get(), slideDeck, body, System.currentTimeMillis(), subscriptionId, expiresIn, viewOnce, distributionType, quote, contacts, previews, "callGroup");

    final SettableFuture<Void> future  = new SettableFuture<>();
    final Context              context = getApplicationContext();

    final OutgoingMediaMessage outgoingMessage;

    if (isSecureText && !forceSms) {
      outgoingMessage = new OutgoingSecureMediaMessage(outgoingMessageCandidate);
      ApplicationContext.getInstance(context).getTypingStatusSender().onTypingStopped(threadId);
    } else {
      outgoingMessage = outgoingMessageCandidate;
    }

    Permissions.with(this)
            .request(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS)
            .ifNecessary(!isSecureText || forceSms)
            .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_sms_permission_in_order_to_send_an_sms))
            .onAllGranted(() -> {
              if (clearComposeBox) {
//                inputPanel.clearQuote();
                attachmentManager.clear(glideRequests, false);
//                silentlySetComposeText("");
              }

              final long id = fragment.stageOutgoingMessage(outgoingMessage, false);

              SimpleTask.run(() -> {
                return MessageSender.sendCallGroup(context, outgoingMessage, callObject, threadId, forceSms, () -> fragment.releaseOutgoingMessage(id));
              }, result -> {
                sendComplete(result);
                future.set(null);
              });
            })
            .onAnyDenied(() -> future.set(null))
            .execute();

    return future;
  }

}

package com.tapmedia.yoush.recipients.ui.managerecipient;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProviders;

import com.takisoft.colorpicker.ColorPickerDialog;
import com.takisoft.colorpicker.ColorStateDrawable;

import com.tapmedia.yoush.AvatarPreviewActivity;
import com.tapmedia.yoush.MediaPreviewActivity;
import com.tapmedia.yoush.MuteDialog;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.color.MaterialColor;
import com.tapmedia.yoush.color.MaterialColors;
import com.tapmedia.yoush.components.AvatarImageView;
import com.tapmedia.yoush.components.ThreadPhotoRailView;
import com.tapmedia.yoush.contacts.avatars.FallbackContactPhoto;
import com.tapmedia.yoush.contacts.avatars.FallbackPhoto80dp;
import com.tapmedia.yoush.conversation.background.ConversationBackgroundFragment;
import com.tapmedia.yoush.groups.ui.GroupMemberListView;
import com.tapmedia.yoush.mediaoverview.MediaOverviewActivity;
import com.tapmedia.yoush.mms.GlideApp;
import com.tapmedia.yoush.notifications.NotificationChannels;
import com.tapmedia.yoush.profiles.edit.EditProfileActivity;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.recipients.ui.notifications.CustomNotificationsDialogFragment;
import com.tapmedia.yoush.ui.main.MainFragment;
import com.tapmedia.yoush.util.DateUtils;
import com.tapmedia.yoush.util.LifecycleCursorWrapper;
import com.tapmedia.yoush.util.ServiceUtil;
import com.tapmedia.yoush.util.Util;

import java.util.Locale;
import java.util.Objects;

public class ManageRecipientFragment extends MainFragment {
  private static final String RECIPIENT_ID = "RECIPIENT_ID";
  private static final String FROM_CONVERSATION = "FROM_CONVERSATION";

  private static final int RETURN_FROM_MEDIA = 405;

  private ManageRecipientViewModel viewModel;
  private GroupMemberListView sharedGroupList;
  private Toolbar toolbar;
  private AvatarImageView avatar;
  private ThreadPhotoRailView threadPhotoRailView;
  private View mediaCard;
  private ManageRecipientViewModel.CursorFactory cursorFactory;
  private View sharedMediaRow;
  private View disappearingMessagesRow;
  private TextView disappearingMessages;
  // private View colorRow;
  // private ImageView colorChip;
  private View blockUnblockCard;
  private TextView block;
  private View blockimg;
  private TextView unblock;
  private View unblockimg;
  private View groupMembershipCard;
  private TextView addToAGroup;
  private SwitchCompat muteNotificationsSwitch;
  private View muteNotificationsRow;
  private TextView muteNotificationsUntilLabel;
  private View notificationsCard;
  private TextView customNotificationsButton;
  private View customNotificationsRow;
  private View toggleAllGroups;
  private View viewSafetyNumber;
  private TextView groupsInCommonCount;
  // private View messageButton;
  // private View secureCallButton;
  // private View insecureCallButton;
  // private View secureVideoCallButton;
  private View textViewChangeAliasName;
  private View textViewChangeBackground;
  private TextView title;
  private TextView subtitle;

  static ManageRecipientFragment newInstance(@NonNull RecipientId recipientId, boolean fromConversation) {
    ManageRecipientFragment fragment = new ManageRecipientFragment();
    Bundle args = new Bundle();

    args.putParcelable(RECIPIENT_ID, recipientId);
    args.putBoolean(FROM_CONVERSATION, fromConversation);
    fragment.setArguments(args);

    return fragment;
  }

  @Override
  public int layoutResource() {
    return R.layout.recipient_manage_fragment;
  }

  @Override
  public void onFindView() {
    avatar = find(R.id.recipient_avatar);
    toolbar = find(R.id.toolbar);
    title = find(R.id.name);
    subtitle = find(R.id.username_number);
//    sharedGroupList = find(R.id.shared_group_list);
//    groupsInCommonCount = find(R.id.groups_in_common_count);
    threadPhotoRailView = find(R.id.recent_photos);
    mediaCard = find(R.id.recipient_media_card);
    sharedMediaRow = find(R.id.shared_media_row);
    disappearingMessagesRow = find(R.id.disappearing_messages_row);
    disappearingMessages = find(R.id.disappearing_messages);
    // colorRow = find(R.id.color_row);
    // colorChip = find(R.id.color_chip);
    blockUnblockCard = find(R.id.recipient_block_and_leave_card);
    block = find(R.id.block);
    blockimg = find(R.id.blockimg);
    unblock = find(R.id.unblock);
    unblockimg = find(R.id.unblockimg);
    viewSafetyNumber = find(R.id.view_safety_number);
//    groupMembershipCard = find(R.id.recipient_membership_card);
//    addToAGroup = find(R.id.add_to_a_group);
    muteNotificationsUntilLabel = find(R.id.recipient_mute_notifications_until);
    muteNotificationsSwitch = find(R.id.recipient_mute_notifications_switch);
    muteNotificationsRow = find(R.id.recipient_mute_notifications_row);
    notificationsCard = find(R.id.recipient_notifications_card);
    customNotificationsButton = find(R.id.recipient_custom_notifications_button);
    customNotificationsRow = find(R.id.recipient_custom_notifications_row);
//    toggleAllGroups = find(R.id.toggle_all_groups);
    // messageButton = find(R.id.recipient_message);
    // secureCallButton = find(R.id.recipient_voice_call);
    // insecureCallButton = find(R.id.recipient_insecure_voice_call);
    // secureVideoCallButton = find(R.id.recipient_video_call);
    textViewChangeAliasName = find(R.id.textViewChangeAliasName);
    textViewChangeBackground= find(R.id.textViewChangeBackground);
  }

  @Override
  public void onViewCreated() {
    RecipientId recipientId = Objects.requireNonNull(requireArguments().getParcelable(RECIPIENT_ID));
    boolean fromConversation = requireArguments().getBoolean(FROM_CONVERSATION, false);
    ManageRecipientViewModel.Factory factory = new ManageRecipientViewModel.Factory(recipientId);

    viewModel = ViewModelProviders.of(requireActivity(), factory).get(ManageRecipientViewModel.class);

    toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
    toolbar.setOnMenuItemClickListener(this::onMenuItemSelected);
    toolbar.inflateMenu(R.menu.manage_recipient_fragment);

    if (recipientId.equals(Recipient.self().getId())) {
      notificationsCard.setVisibility(View.GONE);
//      groupMembershipCard.setVisibility(View.GONE);
      blockUnblockCard.setVisibility(View.GONE);
    } else {
//      viewModel.getVisibleSharedGroups().observe(getViewLifecycleOwner(), members -> sharedGroupList.setMembers(members));
//      viewModel.getSharedGroupsCountSummary().observe(getViewLifecycleOwner(), members -> groupsInCommonCount.setText(members));
//      addToAGroup.setOnClickListener(v -> viewModel.onAddToGroupButton(requireActivity()));
//      sharedGroupList.setRecipientClickListener(recipient -> viewModel.onGroupClicked(requireActivity(), recipient));
//      sharedGroupList.setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

    disappearingMessagesRow.setOnClickListener(v -> viewModel.handleExpirationSelection(requireContext()));
    block.setOnClickListener(v -> viewModel.onBlockClicked(requireActivity()));
    blockimg.setOnClickListener(v -> viewModel.onBlockClicked(requireActivity()));
    unblock.setOnClickListener(v -> viewModel.onUnblockClicked(requireActivity()));
    unblockimg.setOnClickListener(v -> viewModel.onUnblockClicked(requireActivity()));

    muteNotificationsRow.setOnClickListener(v -> {
      if (muteNotificationsSwitch.isEnabled()) {
        muteNotificationsSwitch.toggle();
      }
    });

    customNotificationsRow.setVisibility(View.VISIBLE);
    customNotificationsRow.setOnClickListener(v -> CustomNotificationsDialogFragment.create(recipientId)
            .show(requireFragmentManager(), "CUSTOM_NOTIFICATIONS"));

    //noinspection CodeBlock2Expr
    if (NotificationChannels.supported()) {
      viewModel.hasCustomNotifications().observe(getViewLifecycleOwner(), hasCustomNotifications -> {
        customNotificationsButton.setText(hasCustomNotifications ? R.string.ManageRecipientActivity_on
                : R.string.ManageRecipientActivity_off);
      });
    }

    // messageButton.setOnClickListener(v -> {
    //   if (fromConversation) {
    //     requireActivity().onBackPressed();
    //   } else {
    //     viewModel.onMessage(requireActivity());
    //   }
    // });
    // secureCallButton.setOnClickListener(v -> viewModel.onSecureCall(requireActivity()));
    // insecureCallButton.setOnClickListener(v -> viewModel.onInsecureCall(requireActivity()));
    // secureVideoCallButton.setOnClickListener(v -> viewModel.onSecureVideoCall(requireActivity()));

    textViewChangeBackground.setOnClickListener(v -> {
      ConversationBackgroundFragment fragment = new ConversationBackgroundFragment();
      fragment.recipientId = recipientId;
      addFragment(fragment);
    });

    textViewChangeAliasName.setOnClickListener(v -> {
      AliasNameFragment fragment = new AliasNameFragment();
      fragment.recipientId = recipientId;
      addFragment(fragment);
    });
  }

  @Override
  public void onLiveDataObservers() {
    viewModel.getCanCollapseMemberList().observe(getViewLifecycleOwner(), canCollapseMemberList -> {
      if (canCollapseMemberList) {
//        toggleAllGroups.setVisibility(View.VISIBLE);
//        toggleAllGroups.setOnClickListener(v -> viewModel.revealCollapsedMembers());
      } else {
//        toggleAllGroups.setVisibility(View.GONE);
      }
    });
    viewModel.getIdentity().observe(getViewLifecycleOwner(), identityRecord -> {
      viewSafetyNumber.setVisibility(identityRecord != null ? View.VISIBLE : View.GONE);

      if (identityRecord != null) {
        viewSafetyNumber.setOnClickListener(view -> viewModel.onViewSafetyNumberClicked(requireActivity(), identityRecord));
      }
    });


    viewModel.getDisappearingMessageTimer().observe(getViewLifecycleOwner(), string -> disappearingMessages.setText(string));
    viewModel.getRecipient().observe(getViewLifecycleOwner(), this::presentRecipient);
    viewModel.getMediaCursor().observe(getViewLifecycleOwner(), this::presentMediaCursor);
    viewModel.getMuteState().observe(getViewLifecycleOwner(), this::presentMuteState);
    viewModel.getCanBlock().observe(getViewLifecycleOwner(), canBlock -> {
      block.setVisibility(canBlock ? View.VISIBLE : View.GONE);
      blockimg.setVisibility(canBlock ? View.VISIBLE : View.GONE);
      unblock.setVisibility(canBlock ? View.GONE : View.VISIBLE);
      unblockimg.setVisibility(canBlock ? View.GONE : View.VISIBLE);
    });


    viewModel.getTitle().observe(getViewLifecycleOwner(), text -> {
      title.setText(text);
    });
    viewModel.getSubtitle().observe(getViewLifecycleOwner(), text -> {
      subtitle.setText(text);
      subtitle.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
      subtitle.setOnLongClickListener(null);
      title.setOnLongClickListener(null);
      setCopyToClipboardOnLongPress(TextUtils.isEmpty(text) ? title : subtitle);
    });
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == RETURN_FROM_MEDIA) {
      applyMediaCursorFactory();
    }
  }

  private void presentRecipient(@NonNull Recipient recipient) {
    disappearingMessagesRow.setVisibility(recipient.isRegistered() ? View.VISIBLE : View.GONE);
//    addToAGroup.setVisibility(recipient.isRegistered() ? View.VISIBLE : View.GONE);

    MaterialColor recipientColor = recipient.getColor();
    avatar.setFallbackPhotoProvider(new Recipient.FallbackPhotoProvider() {
      @Override
      public @NonNull FallbackContactPhoto getPhotoForRecipientWithoutName() {
        return new FallbackPhoto80dp(R.drawable.ic_profile_80_new, recipientColor);
      }

      @Override
      public @NonNull FallbackContactPhoto getPhotoForLocalNumber() {
        return new FallbackPhoto80dp(R.drawable.ic_note_80_new, recipientColor);
      }
    });
    avatar.setAvatar(recipient);
    avatar.setOnClickListener(v -> {
      FragmentActivity activity = requireActivity();
      activity.startActivity(AvatarPreviewActivity.intentFromRecipientId(activity, recipient.getId()),
                             AvatarPreviewActivity.createTransitionBundle(activity, avatar));
    });

    @ColorInt int        color         = recipientColor.toActionBarColor(requireContext());
              Drawable[] colorDrawable = new Drawable[]{ContextCompat.getDrawable(requireContext(), R.drawable.colorpickerpreference_pref_swatch)};
    // colorChip.setImageDrawable(new ColorStateDrawable(colorDrawable, color));
    // colorRow.setOnClickListener(v -> handleColorSelection(color));

//    secureCallButton.setVisibility(recipient.isRegistered() && !recipient.isLocalNumber() ? View.VISIBLE : View.GONE);
//    insecureCallButton.setVisibility(!recipient.isRegistered() && !recipient.isLocalNumber() ? View.VISIBLE : View.GONE);
//    secureVideoCallButton.setVisibility(recipient.isRegistered() && !recipient.isLocalNumber() ? View.VISIBLE : View.GONE);
  }

  private void presentMediaCursor(ManageRecipientViewModel.MediaCursor mediaCursor) {
    if (mediaCursor == null) return;
    sharedMediaRow.setOnClickListener(v -> startActivity(MediaOverviewActivity.forThread(requireContext(), mediaCursor.getThreadId())));

    setMediaCursorFactory(mediaCursor.getMediaCursorFactory());

    threadPhotoRailView.setListener(mediaRecord ->
        startActivityForResult(MediaPreviewActivity.intentFromMediaRecord(requireContext(),
                                                                          mediaRecord,
                                                                          ViewCompat.getLayoutDirection(threadPhotoRailView) == ViewCompat.LAYOUT_DIRECTION_LTR),
                               RETURN_FROM_MEDIA));
  }

  private void presentMuteState(@NonNull ManageRecipientViewModel.MuteState muteState) {
    if (muteNotificationsSwitch.isChecked() != muteState.isMuted()) {
      muteNotificationsSwitch.setOnCheckedChangeListener(null);
      muteNotificationsSwitch.setChecked(muteState.isMuted());
    }

    muteNotificationsSwitch.setEnabled(true);
    muteNotificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
      if (isChecked) {
        MuteDialog.show(requireContext(), viewModel::setMuteUntil, () -> muteNotificationsSwitch.setChecked(false));
      } else {
        viewModel.clearMuteUntil();
      }
    });
    muteNotificationsUntilLabel.setVisibility(muteState.isMuted() ? View.VISIBLE : View.GONE);

    if (muteState.isMuted()) {
      muteNotificationsUntilLabel.setText(getString(R.string.ManageRecipientActivity_until_s,
                                                    DateUtils.getTimeString(requireContext(),
                                                                            Locale.getDefault(),
                                                                            muteState.getMutedUntil())));
    }
  }

  private void handleColorSelection(@ColorInt int currentColor) {
    @ColorInt int[] colors = MaterialColors.CONVERSATION_PALETTE.asConversationColorArray(requireContext());

    ColorPickerDialog.Params params = new ColorPickerDialog.Params.Builder(requireContext())
                                                                  .setSelectedColor(currentColor)
                                                                  .setColors(colors)
                                                                  .setSize(ColorPickerDialog.SIZE_SMALL)
                                                                  .setSortColors(false)
                                                                  .setColumns(3)
                                                                  .build();

    ColorPickerDialog dialog = new ColorPickerDialog(requireActivity(), color -> viewModel.onSelectColor(color), params);
    dialog.setTitle(R.string.ManageRecipientActivity_chat_color);
    dialog.show();
  }

  public boolean onMenuItemSelected(@NonNull MenuItem item) {
    if (item.getItemId() == R.id.action_edit) {
      startActivity(EditProfileActivity.getIntentForUserProfileEdit(requireActivity()));
      return true;
    }

    return false;
  }

  private void setMediaCursorFactory(@Nullable ManageRecipientViewModel.CursorFactory cursorFactory) {
    if (this.cursorFactory != cursorFactory) {
      this.cursorFactory = cursorFactory;
      applyMediaCursorFactory();
    }
  }

  private void applyMediaCursorFactory() {
    Context context = getContext();
    if (context == null) return;
    if (cursorFactory != null) {
      Cursor cursor = cursorFactory.create();
      getViewLifecycleOwner().getLifecycle().addObserver(new LifecycleCursorWrapper(cursor));

      threadPhotoRailView.setCursor(GlideApp.with(context), cursor);
      mediaCard.setVisibility(cursor.getCount() > 0 ? View.VISIBLE : View.GONE);
    } else {
      threadPhotoRailView.setCursor(GlideApp.with(context), null);
      mediaCard.setVisibility(View.GONE);
    }
  }

  private static void setCopyToClipboardOnLongPress(@NonNull TextView textView) {
    textView.setOnLongClickListener(v -> {
      Util.copyToClipboard(v.getContext(), textView.getText().toString());
      ServiceUtil.getVibrator(v.getContext()).vibrate(250);
      Toast.makeText(v.getContext(), R.string.RecipientBottomSheet_copied_to_clipboard, Toast.LENGTH_SHORT).show();
      return true;
    });
  }


}

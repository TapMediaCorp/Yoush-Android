/*
 * Copyright (C) 2015 Open Whisper Systems
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
package com.tapmedia.yoush;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.tapmedia.yoush.components.ContactFilterToolbar;
import com.tapmedia.yoush.contacts.ContactsCursorLoader.DisplayMode;
import com.tapmedia.yoush.conversation.ConversationActivity;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.ThreadDatabase;
import com.tapmedia.yoush.groups.ui.creategroup.CreateGroupActivity;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.util.DynamicNoActionBarTheme;
import com.tapmedia.yoush.util.DynamicTheme;
import com.tapmedia.yoush.util.ServiceUtil;
import com.tapmedia.yoush.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;

/**
 * Base activity container for selecting a list of contacts.
 *
 * @author Moxie Marlinspike
 */
public class ContactSelectionActivity extends PassphraseRequiredActivity implements
        ContactSelectionListFragment.OnContactSelectedListener,
        ContactSelectionListFragment.ScrollCallback {

  public static final String EXTRA_LAYOUT_RES_ID = "layout_res_id";

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  protected ContactSelectionListFragment contactsFragment;

  protected ContactFilterToolbar toolbar;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle icicle, boolean ready) {
    if (!getIntent().hasExtra(ContactSelectionListFragment.DISPLAY_MODE)) {
      int displayMode = TextSecurePreferences.isSmsEnabled(this)
              ? DisplayMode.FLAG_ALL
              : DisplayMode.FLAG_PUSH | DisplayMode.FLAG_ACTIVE_GROUPS | DisplayMode.FLAG_INACTIVE_GROUPS | DisplayMode.FLAG_SELF;
      getIntent().putExtra(ContactSelectionListFragment.DISPLAY_MODE, displayMode);
    }
    setContentView(getIntent().getIntExtra(EXTRA_LAYOUT_RES_ID, R.layout.contact_selection_activity));
    initializeToolbar();
    initializeResources();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  /**
   *
   */
  private void initializeToolbar() {
    this.toolbar = findViewById(R.id.toolbar);
    toolbar.setVisibility(View.GONE);
    setSupportActionBar(toolbar);
    getSupportActionBar().setDisplayHomeAsUpEnabled(false);
    getSupportActionBar().setDisplayShowTitleEnabled(false);
    getSupportActionBar().setIcon(null);
    getSupportActionBar().setLogo(null);
  }

  private void initializeResources() {
    contactsFragment = (ContactSelectionListFragment) getSupportFragmentManager().findFragmentById(R.id.contact_selection_list_fragment);
    contactsFragment.setRefreshing(false);
    contactsFragment.scrollCallback = this;
    contactsFragment.onContactSelectedListener = this;
    contactsFragment.inviteCallback = null;
    contactsFragment.newGroupCallback = this::onNewGroup;
  }

  /**
   * {@link ContactSelectionListFragment.OnContactSelectedListener} implement
   */
  @Override
  public void onContactSelected(Optional<RecipientId> recipientId, String number) {
    Recipient recipient;
    if (recipientId.isPresent()) {
      recipient = Recipient.resolved(recipientId.get());
    } else {
      recipient = Recipient.external(this, number);
    }
    Intent intent = new Intent(this, ConversationActivity.class);
    intent.putExtra(ConversationActivity.RECIPIENT_EXTRA, recipient.getId());
    intent.putExtra(ConversationActivity.TEXT_EXTRA, getIntent().getStringExtra(ConversationActivity.TEXT_EXTRA));
    intent.setDataAndType(getIntent().getData(), getIntent().getType());
    long existingThread = DatabaseFactory.getThreadDatabase(this).getThreadIdIfExistsFor(recipient);
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, existingThread);
    intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);
    startActivity(intent);
    finish();
  }

  @Override
  public void onContactDeselected(Optional<RecipientId> recipientId, String number) {
  }

  /**
   * {@link ContactSelectionListFragment.ScrollCallback} implement
   */
  @Override
  public void onBeginScroll() {
    hideKeyboard();
  }

  private void hideKeyboard() {
    ServiceUtil.getInputMethodManager(this)
            .hideSoftInputFromWindow(toolbar.getWindowToken(), 0);
    toolbar.clearFocus();
  }

  /**
   *
   */
  private void onInvite() {
    startActivity(new Intent(this, InviteActivity.class));
    finish();
  }

  private void onNewGroup(boolean forceV1) {
    startActivity(CreateGroupActivity.newIntent(this));
    finish();
  }

}

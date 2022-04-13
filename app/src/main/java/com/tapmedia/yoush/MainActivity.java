package com.tapmedia.yoush;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentPagerAdapter;

import com.aurelhubert.ahbottomnavigation.AHBottomNavigation;
import com.aurelhubert.ahbottomnavigation.AHBottomNavigationItem;
import com.aurelhubert.ahbottomnavigation.AHBottomNavigationViewPager;

import com.tapmedia.yoush.conversation.ConversationActivity;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.ThreadDatabase;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.ui.pin.PinAuthFragment;
import com.tapmedia.yoush.util.DynamicNoActionBarTheme;
import com.tapmedia.yoush.util.DynamicTheme;
import org.whispersystems.libsignal.util.guava.Optional;

public class MainActivity extends PassphraseRequiredActivity implements
        ContactSelectionListFragment.OnContactSelectedListener,
        MainView {

    private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();
    private final MainNavigator navigator = new MainNavigator(this);
    private AHBottomNavigation bottomNavigation;
    private AHBottomNavigationViewPager viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState, boolean ready) {
        super.onCreate(savedInstanceState, ready);
        setContentView(R.layout.main_activity);
        viewPager = findViewById(R.id.mainViewPager);
        bottomNavigation = findViewById(R.id.mainBottomNavigation);
        configBottomNavigation();
        configViewPager();

    }

    @Override
    protected void onPreCreate() {
        super.onPreCreate();
        dynamicTheme.onCreate(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        dynamicTheme.onResume(this);
    }

    private void configBottomNavigation() {
        bottomNavigation.setDefaultBackgroundColor(Color.WHITE);
        bottomNavigation.setBehaviorTranslationEnabled(false);
        bottomNavigation.setTitleState(AHBottomNavigation.TitleState.ALWAYS_HIDE);
        bottomNavigation.setInactiveColor(Color.parseColor("#A1A1A1"));
        bottomNavigation.setAccentColor(Color.parseColor("#DFA952"));
        bottomNavigation.setNotificationBackgroundColor(Color.parseColor("#F04541"));
        bottomNavigation.addItem(new AHBottomNavigationItem(0, R.drawable.ic_chat_bottom_tab, R.color.white));
        bottomNavigation.addItem(new AHBottomNavigationItem(0, R.drawable.ic_friend_bottom_tab, R.color.white));
        bottomNavigation.addItem(new AHBottomNavigationItem(0, R.drawable.ic_home_bottom_tab, R.color.white));
        bottomNavigation.setCurrentItem(0);
        bottomNavigation.setOnTabSelectedListener((position, wasSelected) -> {
           if (position == 2) {
               viewPager.setCurrentItem(viewPager.getCurrentItem(), false);
               Intent intent = new Intent(this, ApplicationPreferencesActivity.class);
               this.startActivity(intent);
               return false;
           } else {
               if (!wasSelected) {
                   viewPager.setCurrentItem(position, false);
               }
           }

            return true;
        });
    }

    private void configViewPager() {
        viewPager.setOffscreenPageLimit(5);
        viewPager.setCurrentItem(0, false);
        viewPager.setAdapter(new MainActivityPagerAdapter(getSupportFragmentManager(),
                FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT));
    }

    public void lightStatusBarWidgets() {
        Window window = this.getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.getInsetsController().setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            );
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = window.getDecorView().getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    public void darkStatusBarWidgets() {
        Window window = this.getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.getInsetsController().setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            );
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = window.getDecorView().getSystemUiVisibility();
            flags = flags ^ View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    /**
     * {@link MainView} implements
     */
    @Override
    public MainNavigator getNavigator() {
        return navigator;
    }

    @Override
    public FragmentActivity activity() {
        return this;
    }

    /**
     * {@link ContactSelectionListFragment.OnContactSelectedListener} implements
     */
    @Override
    public void onContactSelected(Optional<RecipientId> recipientId, String number) {
        Recipient recipient;
        if (recipientId.isPresent()) {
            recipient = Recipient.resolved(recipientId.get());
        } else {
            recipient = Recipient.external(this, number);
        }
        launch(recipient);
    }

    @Override
    public void onContactDeselected(Optional<RecipientId> recipientId, String number) {

    }

    /**
     *
     */
    private void launch(Recipient recipient) {
        Intent intent = new Intent(this, ConversationActivity.class);
        intent.putExtra(ConversationActivity.RECIPIENT_EXTRA, recipient.getId());
        intent.putExtra(ConversationActivity.TEXT_EXTRA, getIntent().getStringExtra(ConversationActivity.TEXT_EXTRA));
        intent.setDataAndType(getIntent().getData(), getIntent().getType());
        long existingThread = DatabaseFactory.getThreadDatabase(this).getThreadIdIfExistsFor(recipient);
        intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, existingThread);
        intent.putExtra(ConversationActivity.ACTIVITY, "MainActivity");
        intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);
        startActivity(intent);
        finish();
    }

}



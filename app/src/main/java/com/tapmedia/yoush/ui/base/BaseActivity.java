package com.tapmedia.yoush.ui.base;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;

import com.tapmedia.yoush.conversation.ConversationActivity;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.ThreadDatabase;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.util.TextSecurePreferences;
import com.tapmedia.yoush.util.dynamiclanguage.DynamicLanguageActivityHelper;
import com.tapmedia.yoush.util.dynamiclanguage.DynamicLanguageContextWrapper;

public abstract class BaseActivity extends AppCompatActivity
        implements BaseView {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layoutResource());
        onFindView();
        onViewCreated();
        onLiveDataObservers();
    }

    /**
     * {@link BaseView} implements
     */
    @Override
    final public BaseActivity getBaseActivity() {
        return this;
    }

    @Override
    public NavController getNavController() {
        return null;
    }

    protected final void addViewClicks(View... views) {
        ViewClickListener listener = new ViewClickListener(360L) {
            @Override
            public void onClicks(View v) {
                onViewClick(v);
            }
        };
        for (View v : views) {
            v.setClickable(true);
            v.setOnClickListener(listener);
        }
    }

    protected void onViewClick(View v) {

    }

    protected final <T extends ViewModel> T getViewModel(Class<T> cls) {
        return new ViewModelProvider(this).get(cls);
    }

    @Override
    protected void onResume() {
        super.onResume();
        initializeScreenshotSecurity();
        DynamicLanguageActivityHelper.recreateIfNotInCorrectLanguage(this, TextSecurePreferences.getLanguage(this));
    }

    private void initializeScreenshotSecurity() {
        if (TextSecurePreferences.isScreenSecurityEnabled(this)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    protected void startActivitySceneTransition(Intent intent, View sharedView, String transitionName) {
        Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(this, sharedView, transitionName)
                .toBundle();
        ActivityCompat.startActivity(this, intent, bundle);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    protected void setStatusBarColor(int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(color);
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(DynamicLanguageContextWrapper.updateContext(newBase, TextSecurePreferences.getLanguage(newBase)));
    }

    public void launch(Recipient recipient) {
        Intent intent = new Intent(this, ConversationActivity.class);
        intent.putExtra(ConversationActivity.RECIPIENT_EXTRA, recipient.getId());
        intent.putExtra(ConversationActivity.TEXT_EXTRA, getIntent().getStringExtra(ConversationActivity.TEXT_EXTRA));
        intent.setDataAndType(getIntent().getData(), getIntent().getType());
        long existingThread = DatabaseFactory.getThreadDatabase(this).getThreadIdIfExistsFor(recipient);
        intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, existingThread);
        intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);
        startActivity(intent);
    }
}

package com.tapmedia.yoush;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.transition.TransitionInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;

import com.tapmedia.yoush.contacts.avatars.ContactPhoto;
import com.tapmedia.yoush.contacts.avatars.FallbackContactPhoto;
import com.tapmedia.yoush.contacts.avatars.ProfileContactPhoto;
import com.tapmedia.yoush.contacts.avatars.ResourceContactPhoto;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.mms.GlideApp;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;

/**
 * Activity for displaying avatars full screen.
 */
public final class AvatarPreviewActivity extends PassphraseRequiredActivity {

  private static final String TAG = Log.tag(AvatarPreviewActivity.class);

  private static final String RECIPIENT_ID_EXTRA = "recipient_id";

  public static @NonNull Intent intentFromRecipientId(@NonNull Context context,
                                                      @NonNull RecipientId recipientId)
  {
    Intent intent = new Intent(context, AvatarPreviewActivity.class);
    intent.putExtra(RECIPIENT_ID_EXTRA, recipientId.serialize());
    return intent;
  }

  public static Bundle createTransitionBundle(@NonNull Activity activity, @NonNull View from) {
    return ActivityOptionsCompat.makeSceneTransitionAnimation(activity, from, "avatar").toBundle();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);

    setTheme(R.style.TextSecure_MediaPreview);
    setContentView(R.layout.contact_photo_preview_activity);

    if (Build.VERSION.SDK_INT >= 21) {
      postponeEnterTransition();
      TransitionInflater inflater = TransitionInflater.from(this);
      getWindow().setSharedElementEnterTransition(inflater.inflateTransition(R.transition.full_screen_avatar_image_enter_transition_set));
      getWindow().setSharedElementReturnTransition(inflater.inflateTransition(R.transition.full_screen_avatar_image_return_transition_set));
    }

   Toolbar toolbar = findViewById(R.id.toolbar);

    ImageView avatar = findViewById(R.id.avatar);

    setSupportActionBar(toolbar);

    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                         WindowManager.LayoutParams.FLAG_FULLSCREEN);

    showSystemUI();

    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    Context     context     = getApplicationContext();
    RecipientId recipientId = RecipientId.from(getIntent().getStringExtra(RECIPIENT_ID_EXTRA));

    Recipient.live(recipientId).observe(this, recipient -> {
      ContactPhoto contactPhoto  = recipient.isLocalNumber() ? new ProfileContactPhoto(recipient, recipient.getProfileAvatar())
                                                                   : recipient.getContactPhoto();
      FallbackContactPhoto fallbackPhoto = recipient.isLocalNumber() ? new ResourceContactPhoto(R.drawable.ic_profile_outline_40_new, R.drawable.ic_profile_outline_20_new, R.drawable.ic_person_large)
                                                                     : recipient.getFallbackContactPhoto();

      Resources resources = this.getResources();

      GlideApp.with(this)
              .asBitmap()
              .load(contactPhoto)
              .fallback(fallbackPhoto.asCallCard(this))
              .error(fallbackPhoto.asCallCard(this))
              .diskCacheStrategy(DiskCacheStrategy.ALL)
              .addListener(new RequestListener<Bitmap>() {
                @Override
                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                  Log.w(TAG, "Unable to load avatar, or avatar removed, closing");
                  finish();
                  return false;
                }

                @Override
                public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                  return false;
                }
              })
              .into(new CustomTarget<Bitmap>() {
                @Override
                public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                  avatar.setImageDrawable(RoundedBitmapDrawableFactory.create(resources, resource));
                  if (Build.VERSION.SDK_INT >= 21) {
                    startPostponedEnterTransition();
                  }
                }

                @Override
                public void onLoadCleared(@Nullable Drawable placeholder) {
                }
              });

      toolbar.setTitle(recipient.getDisplayName(context));
    });

    avatar.setOnClickListener(v -> toggleUiVisibility());

    showAndHideWithSystemUI(getWindow(), findViewById(R.id.toolbar_layout));
  }

  private static void showAndHideWithSystemUI(@NonNull Window window, @NonNull View... views) {
    window.getDecorView().setOnSystemUiVisibilityChangeListener(visibility -> {
      boolean hide = (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;

      for (View view : views) {
        view.animate()
            .alpha(hide ? 0 : 1)
            .start();
      }
    });
  }

  private void toggleUiVisibility() {
    int systemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
    if ((systemUiVisibility & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0) {
      showSystemUI();
    } else {
      hideSystemUI();
    }
  }

  private void hideSystemUI() {
    getWindow().getDecorView().setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_IMMERSIVE              |
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE          |
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN      |
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION        |
        View.SYSTEM_UI_FLAG_FULLSCREEN              );
  }

  private void showSystemUI() {
    getWindow().getDecorView().setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE          |
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN       );
  }

  @Override
  public boolean onSupportNavigateUp() {
    onBackPressed();
    return true;
  }
}

package com.tapmedia.yoush.revealable;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProviders;

import com.tapmedia.yoush.PassphraseRequiredActivity;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.mms.DecryptableStreamUriLoader.DecryptableUri;
import com.tapmedia.yoush.mms.GlideApp;
import com.tapmedia.yoush.mms.PartAuthority;
import com.tapmedia.yoush.mms.VideoSlide;
import com.tapmedia.yoush.providers.BlobProvider;
import com.tapmedia.yoush.util.MediaUtil;
import com.tapmedia.yoush.video.VideoPlayer;

import java.util.concurrent.TimeUnit;

public class ViewOnceMessageActivity extends PassphraseRequiredActivity implements VideoPlayer.PlayerStateCallback {

  private static final String TAG = Log.tag(ViewOnceMessageActivity.class);

  private static final String KEY_MESSAGE_ID = "message_id";
  private static final String KEY_URI        = "uri";

  private ImageView                image;
  private VideoPlayer              video;
  private View                     closeButton;
  private TextView                 duration;
  private ViewOnceMessageViewModel viewModel;
  private Uri                      uri;

  private final Handler  handler                = new Handler(Looper.getMainLooper());
  private final Runnable durationUpdateRunnable = () -> {
    long timeLeft = TimeUnit.MILLISECONDS.toSeconds(video.getDuration() - video.getPlaybackPosition());
    long minutes  = timeLeft / 60;
    long seconds  = timeLeft % 60;

    duration.setText(getString(R.string.ViewOnceMessageActivity_video_duration, minutes, seconds));
    scheduleDurationUpdate();
  };

  public static Intent getIntent(@NonNull Context context, long messageId, @NonNull Uri uri) {
    Intent intent = new Intent(context, ViewOnceMessageActivity.class);
    intent.putExtra(KEY_MESSAGE_ID, messageId);
    intent.putExtra(KEY_URI, uri);
    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);
    setContentView(R.layout.view_once_message_activity);

    this.image       = findViewById(R.id.view_once_image);
    this.video       = findViewById(R.id.view_once_video);
    this.duration    = findViewById(R.id.view_once_duration);
    this.closeButton = findViewById(R.id.view_once_close_button);
    this.uri         = getIntent().getParcelableExtra(KEY_URI);

    closeButton.setOnClickListener(v -> finish());

    initViewModel(getIntent().getLongExtra(KEY_MESSAGE_ID, -1), uri);
  }

  @Override
  protected void onStop() {
    super.onStop();
    cancelDurationUpdate();
    video.cleanup();
    BlobProvider.getInstance().delete(this, uri);
    finish();
  }

  @Override
  public void onPlayerReady() {
    handler.post(durationUpdateRunnable);
  }

  private void initViewModel(long messageId, @NonNull Uri uri) {
    ViewOnceMessageRepository repository = new ViewOnceMessageRepository(this);

    viewModel = ViewModelProviders.of(this, new ViewOnceMessageViewModel.Factory(getApplication(), messageId, repository))
                                  .get(ViewOnceMessageViewModel.class);

    viewModel.getMessage().observe(this, (message) -> {
      if (message == null) return;

      if (message.isPresent()) {
        displayMedia(uri);
      } else {
        image.setImageDrawable(null);
        finish();
      }
    });
  }

  private void displayMedia(@NonNull Uri uri) {
    if (MediaUtil.isVideoType(PartAuthority.getAttachmentContentType(this, uri))) {
      displayVideo(uri);
    } else {
      displayImage(uri);
    }
  }

  private void displayVideo(@NonNull Uri uri) {
    video.setVisibility(View.VISIBLE);
    image.setVisibility(View.GONE);
    duration.setVisibility(View.VISIBLE);

    VideoSlide videoSlide = new VideoSlide(this, uri, 0);

    video.setWindow(getWindow());
    video.setPlayerStateCallbacks(this);
    video.setVideoSource(videoSlide, true);

    video.hideControls();
    video.loopForever();
  }

  private void displayImage(@NonNull Uri uri) {
    video.setVisibility(View.GONE);
    image.setVisibility(View.VISIBLE);
    duration.setVisibility(View.GONE);

    GlideApp.with(this)
            .load(new DecryptableUri(uri))
            .into(image);
  }

  private void scheduleDurationUpdate() {
    handler.postDelayed(durationUpdateRunnable, 100);
  }

  private void cancelDurationUpdate() {
    handler.removeCallbacks(durationUpdateRunnable);
  }

  private class ViewOnceGestureListener extends GestureDetector.SimpleOnGestureListener {

    private final View view;

    private ViewOnceGestureListener(View view) {
      this.view = view;
    }

    @Override
    public boolean onDown(MotionEvent e) {
      return true;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
      view.performClick();
      return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
      finish();
      return true;
    }
  }
}

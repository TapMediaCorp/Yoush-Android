package com.tapmedia.yoush.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.SimpleColorFilter;
import com.airbnb.lottie.model.KeyPath;
import com.airbnb.lottie.value.LottieValueCallback;
import com.pnikosis.materialishprogress.ProgressWheel;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.audio.AudioSlidePlayer;
import com.tapmedia.yoush.audio.AudioWaveForm;
import com.tapmedia.yoush.database.AttachmentDatabase;
import com.tapmedia.yoush.events.PartProgressEvent;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.mms.AudioSlide;
import com.tapmedia.yoush.mms.SlideClickListener;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class AudioView extends FrameLayout implements AudioSlidePlayer.Listener {

  private static final String TAG = AudioView.class.getSimpleName();

  private static final int FORWARDS =  1;
  private static final int REVERSE  = -1;

  @NonNull private final AnimatingToggle     controlToggle;
  @NonNull private final View                progressAndPlay;
  @NonNull private final LottieAnimationView playPauseButton;
  @NonNull private final ImageView           downloadButton;
  @NonNull private final ProgressWheel       circleProgress;
  @NonNull private final SeekBar             seekBar;
           private final boolean             smallView;
           private final boolean             autoRewind;

  @Nullable private final TextView duration;

  @ColorInt private final int waveFormPlayedBarsColor;
  @ColorInt private final int waveFormUnplayedBarsColor;

  @Nullable private SlideClickListener downloadListener;
  @Nullable private AudioSlidePlayer   audioSlidePlayer;
            private int                backwardsCounter;
            private int                lottieDirection;
            private boolean            isPlaying;
            private long               durationMillis;

  public AudioView(Context context) {
    this(context, null);
  }

  public AudioView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public AudioView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    TypedArray typedArray = null;
    try {
      typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.AudioView, 0, 0);

      smallView  = typedArray.getBoolean(R.styleable.AudioView_small, false);
      autoRewind = typedArray.getBoolean(R.styleable.AudioView_autoRewind, false);

      inflate(context, smallView ? R.layout.audio_view_small : R.layout.audio_view, this);

      this.controlToggle   = findViewById(R.id.control_toggle);
      this.playPauseButton = findViewById(R.id.play);
      this.progressAndPlay = findViewById(R.id.progress_and_play);
      this.downloadButton  = findViewById(R.id.download);
      this.circleProgress  = findViewById(R.id.circle_progress);
      this.seekBar         = findViewById(R.id.seek);
      this.duration        = findViewById(R.id.duration);

      lottieDirection = REVERSE;
      this.playPauseButton.setOnClickListener(new PlayPauseClickedListener());
      this.seekBar.setOnSeekBarChangeListener(new SeekBarModifiedListener());

      setTint(typedArray.getColor(R.styleable.AudioView_foregroundTintColor, Color.WHITE));

      this.waveFormPlayedBarsColor   = typedArray.getColor(R.styleable.AudioView_waveformPlayedBarsColor, Color.WHITE);
      this.waveFormUnplayedBarsColor = typedArray.getColor(R.styleable.AudioView_waveformUnplayedBarsColor, Color.WHITE);
    } finally {
      if (typedArray != null) {
        typedArray.recycle();
      }
    }
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    EventBus.getDefault().unregister(this);
  }

  public void setAudio(final @NonNull AudioSlide audio,
                       final boolean showControls)
  {
    if (seekBar instanceof WaveFormSeekBarView) {
      if (audioSlidePlayer != null && !Objects.equals(audioSlidePlayer.getAudioSlide().getUri(), audio.getUri())) {
       WaveFormSeekBarView waveFormView = (WaveFormSeekBarView) seekBar;
       waveFormView.setWaveMode(false);
       seekBar.setProgress(0);
       durationMillis = 0;
      }
    }

    if (showControls && audio.isPendingDownload()) {
      controlToggle.displayQuick(downloadButton);
      seekBar.setEnabled(false);
      downloadButton.setOnClickListener(new DownloadClickedListener(audio));
      if (circleProgress.isSpinning()) circleProgress.stopSpinning();
    } else if (showControls && audio.getTransferState() == AttachmentDatabase.TRANSFER_PROGRESS_STARTED) {
      controlToggle.displayQuick(progressAndPlay);
      seekBar.setEnabled(false);
      circleProgress.spin();
    } else {
      seekBar.setEnabled(true);
      if (circleProgress.isSpinning()) circleProgress.stopSpinning();
      showPlayButton();
      lottieDirection = REVERSE;
      playPauseButton.cancelAnimation();
      playPauseButton.setFrame(0);
    }

    this.audioSlidePlayer = AudioSlidePlayer.createFor(getContext(), audio, this);

    if (seekBar instanceof WaveFormSeekBarView) {
      WaveFormSeekBarView waveFormView = (WaveFormSeekBarView) seekBar;
      waveFormView.setColors(waveFormPlayedBarsColor, waveFormUnplayedBarsColor);
      if (android.os.Build.VERSION.SDK_INT >= 23) {
        new AudioWaveForm(getContext(), audio).getWaveForm(
          data -> {
            if (duration != null) {
              durationMillis = data.getDuration(TimeUnit.MILLISECONDS);
              updateProgress(0, 0);
              duration.setVisibility(VISIBLE);
            }
            waveFormView.setWaveData(data.getWaveForm());
          },
          () -> waveFormView.setWaveMode(false));
      } else {
        waveFormView.setWaveMode(false);
        if (duration != null) {
          duration.setVisibility(GONE);
        }
      }
    }
  }

  public void cleanup() {
    if (this.audioSlidePlayer != null && isPlaying) {
      this.audioSlidePlayer.stop();
    }
  }

  public void setDownloadClickListener(@Nullable SlideClickListener listener) {
    this.downloadListener = listener;
  }

  @Override
  public void onStart() {
    isPlaying = true;
    togglePlayToPause();
  }

  @Override
  public void onStop() {
    isPlaying = false;
    togglePauseToPlay();

    if (autoRewind || seekBar.getProgress() + 5 >= seekBar.getMax()) {
      backwardsCounter = 4;
      rewind();
    }
  }

  @Override
  public void setFocusable(boolean focusable) {
    super.setFocusable(focusable);
    this.playPauseButton.setFocusable(focusable);
    this.seekBar.setFocusable(focusable);
    this.seekBar.setFocusableInTouchMode(focusable);
    this.downloadButton.setFocusable(focusable);
  }

  @Override
  public void setClickable(boolean clickable) {
    super.setClickable(clickable);
    this.playPauseButton.setClickable(clickable);
    this.seekBar.setClickable(clickable);
    this.seekBar.setOnTouchListener(clickable ? null : new TouchIgnoringListener());
    this.downloadButton.setClickable(clickable);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    this.playPauseButton.setEnabled(enabled);
    this.seekBar.setEnabled(enabled);
    this.downloadButton.setEnabled(enabled);
  }

  @Override
  public void onProgress(double progress, long millis) {
    int seekProgress = (int) Math.floor(progress * seekBar.getMax());

    if (seekProgress > seekBar.getProgress() || backwardsCounter > 3) {
      backwardsCounter = 0;
      seekBar.setProgress(seekProgress);
      updateProgress((float) progress, millis);
    } else {
      backwardsCounter++;
    }
  }

  private void updateProgress(float progress, long millis) {
    if (duration != null && durationMillis > 0) {
      long remainingSecs = TimeUnit.MILLISECONDS.toSeconds(durationMillis - millis);
      duration.setText(getResources().getString(R.string.AudioView_duration, remainingSecs / 60, remainingSecs % 60));
    }

    if (smallView) {
      circleProgress.setInstantProgress(seekBar.getProgress() == 0 ? 1 : progress);
    }
  }

  public void setTint(int foregroundTint) {
    post(()-> this.playPauseButton.addValueCallback(new KeyPath("**"),
                                                    LottieProperty.COLOR_FILTER,
                                                    new LottieValueCallback<>(new SimpleColorFilter(foregroundTint))));

    this.downloadButton.setColorFilter(foregroundTint, PorterDuff.Mode.SRC_IN);
    this.circleProgress.setBarColor(foregroundTint);

    if (this.duration != null) {
      this.duration.setTextColor(foregroundTint);
    }
    this.seekBar.getProgressDrawable().setColorFilter(foregroundTint, PorterDuff.Mode.SRC_IN);
    this.seekBar.getThumb().setColorFilter(foregroundTint, PorterDuff.Mode.SRC_IN);
  }

  public void getSeekBarGlobalVisibleRect(@NonNull Rect rect) {
    seekBar.getGlobalVisibleRect(rect);
  }

  private double getProgress() {
    if (this.seekBar.getProgress() <= 0 || this.seekBar.getMax() <= 0) {
      return 0;
    } else {
      return (double)this.seekBar.getProgress() / (double)this.seekBar.getMax();
    }
  }

  private void togglePlayToPause() {
    startLottieAnimation(FORWARDS);
  }

  private void togglePauseToPlay() {
    startLottieAnimation(REVERSE);
  }

  private void startLottieAnimation(int direction) {
    showPlayButton();

    if (lottieDirection == direction) {
      return;
    }
    lottieDirection = direction;

    playPauseButton.pauseAnimation();
    playPauseButton.setSpeed(direction * 2);
    playPauseButton.resumeAnimation();
  }

  private void showPlayButton() {
    if (!smallView || seekBar.getProgress() == 0) {
      circleProgress.setInstantProgress(1);
    }
    circleProgress.setVisibility(VISIBLE);
    playPauseButton.setVisibility(VISIBLE);
    controlToggle.displayQuick(progressAndPlay);
  }

  public void stopPlaybackAndReset() {
    if (this.audioSlidePlayer != null && isPlaying) {
      this.audioSlidePlayer.stop();
      togglePauseToPlay();
    }
    rewind();
  }

  private class PlayPauseClickedListener implements View.OnClickListener {

    @Override
    public void onClick(View v) {
      if (lottieDirection == REVERSE) {
        try {
          Log.d(TAG, "playbutton onClick");
          if (audioSlidePlayer != null) {
            togglePlayToPause();
            audioSlidePlayer.play(getProgress());
          }
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      } else {
        Log.d(TAG, "pausebutton onClick");
        if (audioSlidePlayer != null) {
          togglePauseToPlay();
          audioSlidePlayer.stop();
          if (autoRewind) {
            rewind();
          }
        }
      }
    }
  }

  private void rewind() {
    seekBar.setProgress(0);
    updateProgress(0, 0);
  }

  private class DownloadClickedListener implements View.OnClickListener {
    private final @NonNull AudioSlide slide;

    private DownloadClickedListener(@NonNull AudioSlide slide) {
      this.slide = slide;
    }

    @Override
    public void onClick(View v) {
      if (downloadListener != null) downloadListener.onClick(v, slide);
    }
  }

  private class SeekBarModifiedListener implements SeekBar.OnSeekBarChangeListener {

    private boolean wasPlaying;

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
      if (fromUser && durationMillis > 0) {
        float progressFloat = progress / (float) seekBar.getMax();
        updateProgress(progressFloat, (long) (durationMillis * progressFloat));
      }
    }

    @Override
    public synchronized void onStartTrackingTouch(SeekBar seekBar) {
      wasPlaying = isPlaying;
      if (audioSlidePlayer != null && isPlaying) {
        audioSlidePlayer.stop();
      }
    }

    @Override
    public synchronized void onStopTrackingTouch(SeekBar seekBar) {
      try {
        if (audioSlidePlayer != null && wasPlaying) {
          audioSlidePlayer.play(getProgress());
        }
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    }
  }

  private static class TouchIgnoringListener implements OnTouchListener {
    @Override
    public boolean onTouch(View v, MotionEvent event) {
      return true;
    }
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  public void onEventAsync(final PartProgressEvent event) {
    if (audioSlidePlayer != null && event.attachment.equals(audioSlidePlayer.getAudioSlide().asAttachment())) {
      circleProgress.setInstantProgress(((float) event.progress) / event.total);
    }
  }

}

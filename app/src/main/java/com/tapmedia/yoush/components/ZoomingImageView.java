package com.tapmedia.yoush.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import com.tapmedia.yoush.logging.Log;
import android.util.Pair;
import android.view.View;
import android.widget.FrameLayout;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.Target;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.davemorrissey.labs.subscaleview.decoder.DecoderFactory;
import com.github.chrisbanes.photoview.PhotoView;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.components.subsampling.AttachmentBitmapDecoder;
import com.tapmedia.yoush.components.subsampling.AttachmentRegionDecoder;
import com.tapmedia.yoush.mms.DecryptableStreamUriLoader.DecryptableUri;
import com.tapmedia.yoush.mms.GlideRequests;
import com.tapmedia.yoush.mms.PartAuthority;
import com.tapmedia.yoush.util.BitmapDecodingException;
import com.tapmedia.yoush.util.BitmapUtil;
import com.tapmedia.yoush.util.MediaUtil;

import java.io.IOException;
import java.io.InputStream;


public class ZoomingImageView extends FrameLayout {

  private static final String TAG = ZoomingImageView.class.getSimpleName();

  private final PhotoView                 photoView;
  private final SubsamplingScaleImageView subsamplingImageView;

  public ZoomingImageView(Context context) {
    this(context, null);
  }

  public ZoomingImageView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ZoomingImageView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    inflate(context, R.layout.zooming_image_view, this);

    this.photoView            = findViewById(R.id.image_view);
    this.subsamplingImageView = findViewById(R.id.subsampling_image_view);

    this.subsamplingImageView.setOrientation(SubsamplingScaleImageView.ORIENTATION_USE_EXIF);

    this.photoView.setOnClickListener(v -> ZoomingImageView.this.callOnClick());
    this.subsamplingImageView.setOnClickListener(v -> ZoomingImageView.this.callOnClick());
  }

  @SuppressLint("StaticFieldLeak")
  public void setImageUri(@NonNull GlideRequests glideRequests, @NonNull Uri uri, @NonNull String contentType)
  {
    final Context context        = getContext();
    final int     maxTextureSize = BitmapUtil.getMaxTextureSize();

    Log.i(TAG, "Max texture size: " + maxTextureSize);

    new AsyncTask<Void, Void, Pair<Integer, Integer>>() {
      @Override
      protected @Nullable Pair<Integer, Integer> doInBackground(Void... params) {
        if (MediaUtil.isGif(contentType)) return null;

        try {
          InputStream inputStream = PartAuthority.getAttachmentStream(context, uri);
          return BitmapUtil.getDimensions(inputStream);
        } catch (IOException | BitmapDecodingException e) {
          Log.w(TAG, e);
          return null;
        }
      }

      protected void onPostExecute(@Nullable Pair<Integer, Integer> dimensions) {
        Log.i(TAG, "Dimensions: " + (dimensions == null ? "(null)" : dimensions.first + ", " + dimensions.second));

        if (dimensions == null || (dimensions.first <= maxTextureSize && dimensions.second <= maxTextureSize)) {
          Log.i(TAG, "Loading in standard image view...");
          setImageViewUri(glideRequests, uri);
        } else {
          Log.i(TAG, "Loading in subsampling image view...");
          setSubsamplingImageViewUri(uri);
        }
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private void setImageViewUri(@NonNull GlideRequests glideRequests, @NonNull Uri uri) {
    photoView.setVisibility(View.VISIBLE);
    subsamplingImageView.setVisibility(View.GONE);

    glideRequests.load(new DecryptableUri(uri))
                 .diskCacheStrategy(DiskCacheStrategy.NONE)
                 .dontTransform()
                 .override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                 .into(photoView);
  }

  private void setSubsamplingImageViewUri(@NonNull Uri uri) {
    subsamplingImageView.setBitmapDecoderFactory(new AttachmentBitmapDecoderFactory());
    subsamplingImageView.setRegionDecoderFactory(new AttachmentRegionDecoderFactory());

    subsamplingImageView.setVisibility(View.VISIBLE);
    photoView.setVisibility(View.GONE);

    subsamplingImageView.setImage(ImageSource.uri(uri));
  }

  public void cleanup() {
    photoView.setImageDrawable(null);
    subsamplingImageView.recycle();
  }

  private static class AttachmentBitmapDecoderFactory implements DecoderFactory<AttachmentBitmapDecoder> {
    @Override
    public AttachmentBitmapDecoder make() throws IllegalAccessException, InstantiationException {
      return new AttachmentBitmapDecoder();
    }
  }

  private static class AttachmentRegionDecoderFactory implements DecoderFactory<AttachmentRegionDecoder> {
    @Override
    public AttachmentRegionDecoder make() throws IllegalAccessException, InstantiationException {
      return new AttachmentRegionDecoder();
    }
  }
}

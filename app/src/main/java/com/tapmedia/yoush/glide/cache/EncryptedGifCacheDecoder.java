package com.tapmedia.yoush.glide.cache;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.tapmedia.yoush.logging.Log;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.load.resource.gif.StreamGifDecoder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class EncryptedGifCacheDecoder extends EncryptedCoder implements ResourceDecoder<File, GifDrawable> {

  private static final String TAG = EncryptedGifCacheDecoder.class.getSimpleName();

  private final byte[]           secret;
  private final StreamGifDecoder gifDecoder;

  public EncryptedGifCacheDecoder(@NonNull byte[] secret, @NonNull StreamGifDecoder gifDecoder) {
    this.secret     = secret;
    this.gifDecoder = gifDecoder;
  }

  @Override
  public boolean handles(@NonNull File source, @NonNull Options options) {
    try (InputStream inputStream = createEncryptedInputStream(secret, source)) {
      return gifDecoder.handles(inputStream, options);
    } catch (IOException e) {
      Log.w(TAG, e);
      return false;
    }
  }

  @Override
  public @Nullable Resource<GifDrawable> decode(@NonNull File source, int width, int height, @NonNull Options options) throws IOException {
    Log.i(TAG, "Encrypted GIF cache decoder running...");
    try (InputStream inputStream = createEncryptedInputStream(secret, source)) {
      return gifDecoder.decode(inputStream, width, height, options);
    }
  }

}

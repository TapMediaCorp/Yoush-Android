package com.tapmedia.yoush.mms;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.cache.DiskCache;
import com.bumptech.glide.load.engine.cache.DiskCacheAdapter;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.UnitModelLoader;
import com.bumptech.glide.load.resource.bitmap.Downsampler;
import com.bumptech.glide.load.resource.bitmap.StreamBitmapDecoder;
import com.bumptech.glide.load.resource.gif.ByteBufferGifDecoder;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.load.resource.gif.StreamGifDecoder;
import com.bumptech.glide.module.AppGlideModule;

import com.tapmedia.yoush.blurhash.BlurHash;
import com.tapmedia.yoush.blurhash.BlurHashModelLoader;
import com.tapmedia.yoush.blurhash.BlurHashResourceDecoder;
import com.tapmedia.yoush.contacts.avatars.ContactPhoto;
import com.tapmedia.yoush.crypto.AttachmentSecret;
import com.tapmedia.yoush.crypto.AttachmentSecretProvider;
import com.tapmedia.yoush.giph.model.ChunkedImageUrl;
import com.tapmedia.yoush.glide.ChunkedImageUrlLoader;
import com.tapmedia.yoush.glide.ContactPhotoLoader;
import com.tapmedia.yoush.glide.OkHttpUrlLoader;
import com.tapmedia.yoush.glide.cache.EncryptedBitmapCacheDecoder;
import com.tapmedia.yoush.glide.cache.EncryptedBitmapResourceEncoder;
import com.tapmedia.yoush.glide.cache.EncryptedCacheEncoder;
import com.tapmedia.yoush.glide.cache.EncryptedGifCacheDecoder;
import com.tapmedia.yoush.glide.cache.EncryptedGifDrawableResourceEncoder;
import com.tapmedia.yoush.mms.AttachmentStreamUriLoader.AttachmentModel;
import com.tapmedia.yoush.mms.DecryptableStreamUriLoader.DecryptableUri;
import com.tapmedia.yoush.stickers.StickerRemoteUri;
import com.tapmedia.yoush.stickers.StickerRemoteUriLoader;

import java.io.File;
import java.io.InputStream;

@GlideModule
public class SignalGlideModule extends AppGlideModule {

  @Override
  public boolean isManifestParsingEnabled() {
    return false;
  }

  @Override
  public void applyOptions(Context context, GlideBuilder builder) {
    builder.setLogLevel(Log.ERROR);
//    builder.setDiskCache(new NoopDiskCacheFactory());
  }

  @Override
  public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
    AttachmentSecret attachmentSecret = AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret();
    byte[]           secret           = attachmentSecret.getModernKey();

    registry.prepend(File.class, File.class, UnitModelLoader.Factory.getInstance());
    registry.prepend(InputStream.class, new EncryptedCacheEncoder(secret, glide.getArrayPool()));
    registry.prepend(File.class, Bitmap.class, new EncryptedBitmapCacheDecoder(secret, new StreamBitmapDecoder(new Downsampler(registry.getImageHeaderParsers(), context.getResources().getDisplayMetrics(), glide.getBitmapPool(), glide.getArrayPool()), glide.getArrayPool())));
    registry.prepend(File.class, GifDrawable.class, new EncryptedGifCacheDecoder(secret, new StreamGifDecoder(registry.getImageHeaderParsers(), new ByteBufferGifDecoder(context, registry.getImageHeaderParsers(), glide.getBitmapPool(), glide.getArrayPool()), glide.getArrayPool())));

    registry.prepend(BlurHash.class, Bitmap.class, new BlurHashResourceDecoder());

    registry.prepend(Bitmap.class, new EncryptedBitmapResourceEncoder(secret));
    registry.prepend(GifDrawable.class, new EncryptedGifDrawableResourceEncoder(secret));

    registry.append(ContactPhoto.class, InputStream.class, new ContactPhotoLoader.Factory(context));
    registry.append(DecryptableUri.class, InputStream.class, new DecryptableStreamUriLoader.Factory(context));
    registry.append(AttachmentModel.class, InputStream.class, new AttachmentStreamUriLoader.Factory());
    registry.append(ChunkedImageUrl.class, InputStream.class, new ChunkedImageUrlLoader.Factory());
    registry.append(StickerRemoteUri.class, InputStream.class, new StickerRemoteUriLoader.Factory());
    registry.append(BlurHash.class, BlurHash.class, new BlurHashModelLoader.Factory());
    registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory());
  }

  public static class NoopDiskCacheFactory implements DiskCache.Factory {
    @Override
    public DiskCache build() {
      return new DiskCacheAdapter();
    }
  }
}

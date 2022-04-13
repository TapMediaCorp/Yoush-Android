package com.tapmedia.yoush.conversation.preview;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Pair;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import com.tapmedia.yoush.attachments.Attachment;
import com.tapmedia.yoush.attachments.UriAttachment;
import com.tapmedia.yoush.database.AttachmentDatabase;
import com.tapmedia.yoush.linkpreview.LinkPreview;
import com.tapmedia.yoush.linkpreview.LinkPreviewRepository;
import com.tapmedia.yoush.providers.BlobProvider;
import com.tapmedia.yoush.util.MediaUtil;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PreviewJob {

    public static boolean handleLinkPreview(
            LinkPreviewRepository.Callback<Optional<LinkPreview>> callback,
            String url
    ) {
        if (url.indexOf("https://") < 0 && url.indexOf("http://") < 0) {
            return false;
        }
        Single.fromCallable(() -> {
            try {
                Document document = Jsoup.connect(url).get();
                String title = document.title();
                return new Pair<>(title, getImageUrl(document));
            } catch (IOException e) {
                return null;
            }

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleObserver<Pair<String, String>>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {

                    }

                    @Override
                    public void onSuccess(@NonNull Pair<String, String> pair) {
                        downloadImage(callback, url, pair.first, pair.second);
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        callback.onComplete(Optional.absent());
                    }
                });

        return true;
    }

    private static void downloadImage(
            LinkPreviewRepository.Callback<Optional<LinkPreview>> callback,
            String url,
            String title,
            String imageUrl
    ) {
        if (TextUtils.isEmpty(imageUrl)){
            callback.onComplete(Optional.of(new LinkPreview(url, title, Optional.absent())));
            return;
        }
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(imageUrl)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    InputStream is = response.body().byteStream();
                    BufferedInputStream bufferedInputStream = new BufferedInputStream(is);
                    Bitmap bmp = BitmapFactory.decodeStream(bufferedInputStream);
                    Optional<Attachment> attachment = onBitmapDownloaded(bmp);
                    callback.onComplete(Optional.of(new LinkPreview(url, title, attachment)));
                }catch (Exception e){
                    callback.onComplete(Optional.absent());
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                callback.onComplete(Optional.absent());
            }
        });
    }

    private static Optional<Attachment> onBitmapDownloaded(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] bytes = baos.toByteArray();
        Uri uri = BlobProvider.getInstance().forData(bytes).createForSingleSessionInMemory();
        Optional<Attachment> thumbnail = Optional.of(new UriAttachment(uri,
                uri,
                MediaUtil.IMAGE_JPEG,
                AttachmentDatabase.TRANSFER_PROGRESS_STARTED,
                bytes.length,
                bitmap.getWidth(),
                bitmap.getHeight(),
                null,
                null,
                false,
                false,
                false,
                null,
                null,
                null,
                null,
                null));
        return thumbnail;
    }

    private static String getImageUrl(Document document) {
        Elements elements = document.getElementsByAttributeValue("rel", "image_src");
        for (Element e : elements) {
            return e.attr("href");
        }
        elements = document.getElementsByAttributeValue("property", "og:image");
        for (Element e : elements) {
            return e.attr("content");
        }
        elements = document.getElementsByAttributeValue("rel", "preload");
        for (Element e : elements) {
            return e.attr("href");
        }
        return null;
    }

}

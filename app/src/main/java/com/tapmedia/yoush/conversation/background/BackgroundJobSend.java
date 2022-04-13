package com.tapmedia.yoush.conversation.background;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;

import com.tapmedia.yoush.ApplicationContext;
import com.tapmedia.yoush.TransportOption;
import com.tapmedia.yoush.TransportOptions;
import com.tapmedia.yoush.conversation.job.MessageJob;
import com.tapmedia.yoush.conversation.model.BackgroundWrapper;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.jobs.PushGroupSendJob;
import com.tapmedia.yoush.mediasend.Media;
import com.tapmedia.yoush.mediasend.MediaSendActivityResult;
import com.tapmedia.yoush.mms.OutgoingGroupUpdateMessage;
import com.tapmedia.yoush.mms.OutgoingMediaMessage;
import com.tapmedia.yoush.mms.Slide;
import com.tapmedia.yoush.providers.BlobProvider;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.util.JsObject;
import com.tapmedia.yoush.util.MediaUtil;
import com.tapmedia.yoush.util.Util;

import net.sqlcipher.database.SQLiteDatabase;

import org.whispersystems.libsignal.util.guava.Optional;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class BackgroundJobSend {

    private static Context context() {
        return ApplicationContext.getInstance();
    }

    public static void setBackground(Uri uri, String url) {
        setBackground(uri, url, false);
    }

    public static void setBackground(Uri uri, String url, boolean isSilently) {
        if (!TextUtils.isEmpty(url)) {
            BackgroundData.onDataChange(MessageJob.threadId, () -> {
                JsObject body = backgroundBody("set", url, isSilently);
                long messageId = insertSendRecord(body);
                MessageJob.onMessageSent(messageId, MessageJob.recipient);
            });
            return;
        }
        if (uri != null) {
            JsObject body = backgroundBody("set", null, isSilently);
            Media media = getMediaFromFile(uri);
            if (media == null) return;
            List<Media> mediaList = new ArrayList<>();
            mediaList.add(media);
            TransportOption transport = TransportOptions.getPushTransportOption(context());
            MediaSendActivityResult result = MediaSendActivityResult.forTraditionalSend(
                    mediaList,
                    body.build().toString(),
                    transport,
                    false
            );
            BackgroundData.uploadResultLiveData.setValue(result);
            return;
        }
    }

    public static void removeBackground() {
        BackgroundData.onDataChange(MessageJob.threadId,()->{
            JsObject body = backgroundBody("remove", null, false);
            long messageId = insertSendRecord(body);
            MessageJob.onMessageSent(messageId, MessageJob.recipient);
        });
    }

    public static JsObject backgroundBody(String action, String imageUrl, boolean isSilently) {
        return JsObject.create()
                .put("messageType", "updateWallPaper")
                .put("imageUrl", imageUrl)
                .put("action", action)
                .put("silent", isSilently)
                .put("userAction", JsObject.create()
                        .put("uuid", Recipient.self().getUuid().get().toString())
                        .put("phoneNumber", Recipient.self().getE164().get())
                );

    }

    public static Media getMediaFromAssets(Uri uri) {
        Context context = ApplicationContext.getInstance();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            String assetPath = uri.toString();
            String filePath = assetPath.substring(assetPath.indexOf(BackgroundData.folderPath));
            InputStream inputStream = context.getAssets().open(filePath);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            Uri newUri = BlobProvider.getInstance()
                    .forData(outputStream.toByteArray())
                    .withMimeType(MediaUtil.IMAGE_JPEG)
                    .createForSingleSessionOnDisk(context);
            return new Media(
                    newUri,
                    MediaUtil.IMAGE_JPEG,
                    System.currentTimeMillis(),
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    0L,
                    0,
                    false,
                    Optional.absent(),
                    Optional.absent(),
                    Optional.absent()
            );
        } catch (IOException e) {
            return null;
        } finally {
            Util.close(outputStream);
        }
    }

    public static Media getMediaFromFile(Uri uri) {
        Context context = ApplicationContext.getInstance();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            String[] filePathColumn = {MediaStore.Images.Media.DATA};
            Cursor cursor = context.getContentResolver().query(uri,
                    filePathColumn, null, null, null);
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String picturePath = cursor.getString(columnIndex);
            cursor.close();
            File file = new File(TextUtils.isEmpty(picturePath)? uri.getPath() :picturePath);
            FileInputStream inputStream = new FileInputStream(file);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            Uri newUri = BlobProvider.getInstance()
                    .forData(outputStream.toByteArray())
                    .withMimeType(MediaUtil.IMAGE_JPEG)
                    .createForSingleSessionOnDisk(context);
            return new Media(
                    newUri,
                    MediaUtil.IMAGE_JPEG,
                    System.currentTimeMillis(),
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    0L,
                    0,
                    false,
                    Optional.absent(),
                    Optional.absent(),
                    Optional.absent()
            );
        } catch (IOException e) {
            return null;
        } finally {
            Util.close(outputStream);
        }
    }

    private static long insertSendRecord(JsObject body) {
        ContentValues values = new ContentValues();
        values.put("date", System.currentTimeMillis());
        values.put("m_type", BackgroundData.backgroundType);
        values.put("msg_box", 22);
        values.put("thread_id", MessageJob.threadId);
        values.put("read", 1);
        values.put("date_received", System.currentTimeMillis());
        values.put("subscription_id", -1);
        values.put("expires_in", 0);
        values.put("reveal_duration", true);
        values.put("address", MessageJob.recipient.getId().serialize());
        values.put("delivery_receipt_count", 0);
        values.put("body", body.toString());
        values.put("part_count", 0);
        return DatabaseFactory
                .getMmsDatabase(ApplicationContext.getInstance())
                .databaseHelper
                .getWritableDatabase()
                .insert("mms", null, values);
    }

    /**
     * .send(Media message)
     */
    public static void rememberMessage(String body, long messageId) {
        if (BackgroundData.isValidRecordBody(body)) {
            BackgroundData.messageId = messageId;
        }
    }

    /**
     * {@link PushGroupSendJob}
     * .onPushSend
     */
    public static void updateMessage(long messageId) {
        if (messageId != BackgroundData.messageId) {
            BackgroundData.messageId = -1;
            return;
        }
       BackgroundData.messageId = -1;
        try {
            OutgoingMediaMessage message = DatabaseFactory
                    .getMmsDatabase(context())
                    .getOutgoingMessage(messageId);
            if (message == null) return;
            if (!BackgroundData.isValidRecordBody(message.getBody())) return;
            SQLiteDatabase db = DatabaseFactory
                    .getMmsDatabase(ApplicationContext.getInstance())
                    .databaseHelper
                    .getWritableDatabase();
            db.execSQL("UPDATE mms SET msg_box =? WHERE _id = ?",
                    new String[]{BackgroundData.backgroundType + "", messageId + ""});

            long threadId = DatabaseFactory
                    .getThreadDatabase(context())
                    .getThreadIdFor(message.getRecipient());
            BackgroundData.onDataChange(threadId, () -> {
            });

        } catch (Exception e) {

        }
    }

    public static String getPathFromURI(final Uri uri) {

        Context context = ApplicationContext.getInstance();
        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public static void sendBackgroundForNewMember(OutgoingMediaMessage message, long threadId) {
        if (!(message instanceof OutgoingGroupUpdateMessage)) {
            return;
        }
        BackgroundWrapper wrapper = BackgroundData.getCurrentBackgroundWrapper(threadId);

        if (wrapper == null || wrapper.getAction().equals("remove")) return;
        Slide slide = BackgroundData.getSlide(wrapper.record);
        if (slide == null) {
            BackgroundJobSend.setBackground(null, wrapper.getImageUrl(), true);
            return;
        }
        Uri uri = slide.getUri();
        Media media = new Media(
                uri, MediaUtil.IMAGE_JPEG, System.currentTimeMillis(),
                slide.asAttachment().getWidth(), slide.asAttachment().getHeight(),
                0L, 0, false,
                Optional.absent(), Optional.absent(), Optional.absent()
        );
        if (media == null) return;
        List<Media> mediaList = new ArrayList<>();
        mediaList.add(media);
        TransportOption transport = TransportOptions.getPushTransportOption(ApplicationContext.getInstance());
        JsObject body = BackgroundJobSend.backgroundBody("set", null, true);
        MediaSendActivityResult result = MediaSendActivityResult.forTraditionalSend(
                mediaList,
                body.build().toString(),
                transport,
                false
        );
        BackgroundData.uploadResultLiveData.postValue(result);

        /*long messageId = wrapper.record.getId();
        try {
            DatabaseFactory.getAttachmentDatabase(ApplicationContext.getInstance())
                    .deleteAttachmentFilesForViewOnceMessage(messageId);
            new File(URI.create(uri.toString())).deleteOnExit();
        } catch (Exception ignore) {

        }*/
    }

}

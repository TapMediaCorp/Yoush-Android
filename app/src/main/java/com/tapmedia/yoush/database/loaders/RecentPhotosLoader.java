package com.tapmedia.yoush.database.loaders;


import android.Manifest;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.loader.content.CursorLoader;

import com.tapmedia.yoush.permissions.Permissions;

public class RecentPhotosLoader extends CursorLoader {

  public static Uri BASE_URL = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

  private static final String[] PROJECTION = new String[] {
      MediaStore.Images.ImageColumns.DATA,
      MediaStore.Images.ImageColumns.DATE_TAKEN,
      MediaStore.Images.ImageColumns.DATE_MODIFIED,
      MediaStore.Images.ImageColumns.ORIENTATION,
      MediaStore.Images.ImageColumns.MIME_TYPE,
      MediaStore.Images.ImageColumns.BUCKET_ID,
      MediaStore.Images.ImageColumns.SIZE,
      MediaStore.Images.ImageColumns.WIDTH,
      MediaStore.Images.ImageColumns.HEIGHT
  };

  private static final String SELECTION  = MediaStore.Images.Media.DATA + " NOT NULL";

  private final Context context;

  public RecentPhotosLoader(Context context) {
    super(context);
    this.context = context.getApplicationContext();
  }

  @Override
  public Cursor loadInBackground() {
    if (Permissions.hasAll(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
      return context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                                PROJECTION, SELECTION, null,
                                                MediaStore.Images.ImageColumns.DATE_MODIFIED + " DESC");
    } else {
      return null;
    }
  }


}

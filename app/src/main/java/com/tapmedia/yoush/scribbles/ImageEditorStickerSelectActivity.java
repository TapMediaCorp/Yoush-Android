package com.tapmedia.yoush.scribbles;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.components.emoji.MediaKeyboard;
import com.tapmedia.yoush.components.emoji.MediaKeyboardProvider;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.model.StickerRecord;
import com.tapmedia.yoush.stickers.StickerKeyboardProvider;
import com.tapmedia.yoush.stickers.StickerManagementActivity;
import com.tapmedia.yoush.util.concurrent.SignalExecutors;

public final class ImageEditorStickerSelectActivity extends FragmentActivity {

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                         WindowManager.LayoutParams.FLAG_FULLSCREEN);

    setContentView(R.layout.scribble_select_new_sticker_activity);

    MediaKeyboard mediaKeyboard = findViewById(R.id.emoji_drawer);

    mediaKeyboard.setProviders(0, new StickerKeyboardProvider(this, new StickerKeyboardProvider.StickerEventListener() {
      @Override
      public void onStickerSelected(@NonNull StickerRecord sticker) {
        Intent intent = new Intent();
        intent.setData(sticker.getUri());
        setResult(RESULT_OK, intent);

        SignalExecutors.BOUNDED.execute(() ->
         DatabaseFactory.getStickerDatabase(getApplicationContext())
                        .updateStickerLastUsedTime(sticker.getRowId(), System.currentTimeMillis())
        );

        finish();
      }

      @Override
      public void onStickerManagementClicked() {
        startActivity(StickerManagementActivity.getIntent(ImageEditorStickerSelectActivity.this));
      }
    }
    ));

    mediaKeyboard.setKeyboardListener(new MediaKeyboard.MediaKeyboardListener() {
      @Override
      public void onShown() {
      }

      @Override
      public void onHidden() {
        finish();
      }

      @Override
      public void onKeyboardProviderChanged(@NonNull MediaKeyboardProvider provider) {
      }
    });

    mediaKeyboard.show();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      onBackPressed();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }
}

package com.tapmedia.yoush;

import com.facebook.flipper.android.AndroidFlipperClient;
import com.facebook.flipper.core.FlipperClient;
import com.facebook.flipper.plugins.databases.DatabasesFlipperPlugin;
import com.facebook.flipper.plugins.inspector.DescriptorMapping;
import com.facebook.flipper.plugins.inspector.InspectorFlipperPlugin;
import com.facebook.flipper.plugins.sharedpreferences.SharedPreferencesFlipperPlugin;
import com.facebook.soloader.SoLoader;

import com.tapmedia.yoush.database.FlipperSqlCipherAdapter;

public class FlipperApplicationContext extends ApplicationContext {

  @Override
  public void onCreate() {
    super.onCreate();

    SoLoader.init(this, false);

    FlipperClient client = AndroidFlipperClient.getInstance(this);
    client.addPlugin(new InspectorFlipperPlugin(this, DescriptorMapping.withDefaults()));
    client.addPlugin(new DatabasesFlipperPlugin(new FlipperSqlCipherAdapter(this)));
    client.addPlugin(new SharedPreferencesFlipperPlugin(this));
    client.start();
  }
}

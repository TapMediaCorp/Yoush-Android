package com.tapmedia.yoush.util;

import androidx.annotation.StyleRes;

import com.tapmedia.yoush.R;

public class DynamicIntroTheme extends DynamicTheme {

  protected @StyleRes int getLightThemeStyle() {
    return R.style.TextSecure_LightIntroTheme;
  }

  protected @StyleRes int getDarkThemeStyle() {
    return R.style.TextSecure_DarkIntroTheme;
  }
}

package com.tapmedia.yoush.util;

import android.util.Log;

import com.tapmedia.yoush.BuildConfig;

public class DevLogger {

    private static final String TAG = "DevLogger";

    public static void d(String format, Object... args) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, String.format(format, args));
        }
    }

}

package com.tapmedia.yoush.logging;

import android.annotation.SuppressLint;

@SuppressLint("LogNotSignal")
public final class AndroidLogger extends Log.Logger {

  @Override
  public void v(String tag, String message, Throwable t) {
    android.util.Log.v(tag, message, t);
  }

  @Override
  public void d(String tag, String message, Throwable t) {
    android.util.Log.d(tag, message, t);
  }

  @Override
  public void i(String tag, String message, Throwable t) {
    android.util.Log.i(tag, message, t);
  }

  @Override
  public void w(String tag, String message, Throwable t) {
    android.util.Log.w(tag, message, t);
  }

  @Override
  public void e(String tag, String message, Throwable t) {
    android.util.Log.e(tag, message, t);
  }

  @Override
  public void wtf(String tag, String message, Throwable t) {
    android.util.Log.wtf(tag, message, t);
  }

  @Override
  public void blockUntilAllWritesFinished() {
  }
}

package com.tapmedia.yoush.util;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;

import com.jakewharton.rxbinding4.widget.RxTextView;

import com.tapmedia.yoush.ApplicationContext;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.observers.DisposableObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class WidgetUtil {

    public static Toast currentToast;

    public static void toast(Object s) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showToast(s);
        } else {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    showToast(s);
                }
            });
        }
    }

    private static void showToast(Object s) {
        if (null != currentToast) {
            currentToast.cancel();
        }
        currentToast = Toast.makeText(ApplicationContext.getInstance(), s.toString(), Toast.LENGTH_SHORT);
        currentToast.show();
    }

    public interface SearchTextChangeListener {

        void onStartSearch(String s);

        void onSearchCancel();
    }

    public static void onSearchTextChange(EditText editText, SearchTextChangeListener listener) {
        RxTextView.textChanges(editText)
                .subscribeOn(Schedulers.io())
                .debounce(500, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new DisposableObserver<CharSequence>() {
                    @Override
                    public void onNext(CharSequence charSequence) {
                        if (TextUtils.isEmpty(charSequence)) {
                            listener.onSearchCancel();
                            return;
                        }
                        String s = charSequence.toString().trim();
                        if (s.trim().length() < 1) {
                            listener.onSearchCancel();
                            return;
                        }
                        listener.onStartSearch(s);
                    }

                    @Override
                    public void onError(Throwable e) {
                        listener.onSearchCancel();
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

}

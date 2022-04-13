package com.tapmedia.yoush.util;

import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;

public abstract class SimpleSingleObserver<T> implements SingleObserver<T> {

    @Override
    public void onSubscribe(Disposable d) {

    }

    @Override
    public void onSuccess(T list) {

    }

    @Override
    public void onError(Throwable e) {

    }
}

package com.tapmedia.yoush.util.simple;


import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.observers.DisposableObserver;

abstract class SimpleDisposableObserver<T> extends DisposableObserver<T> {

    @Override
    public void onError(@NonNull Throwable e) {

    }

    @Override
    public void onComplete() {

    }
}

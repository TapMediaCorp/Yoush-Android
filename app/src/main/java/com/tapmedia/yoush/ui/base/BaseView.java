package com.tapmedia.yoush.ui.base;

import androidx.navigation.NavController;

interface BaseView {

    BaseActivity getBaseActivity();

    NavController getNavController();

    int layoutResource();

    void onFindView();

    void onViewCreated();

    void onLiveDataObservers();

}

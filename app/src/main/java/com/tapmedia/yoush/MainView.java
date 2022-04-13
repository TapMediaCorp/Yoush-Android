package com.tapmedia.yoush;


import androidx.fragment.app.FragmentActivity;

interface MainView {

    MainNavigator getNavigator();

    FragmentActivity activity();
}

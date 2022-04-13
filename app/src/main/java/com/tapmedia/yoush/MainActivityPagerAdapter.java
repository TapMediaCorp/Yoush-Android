package com.tapmedia.yoush;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import com.tapmedia.yoush.conversation.background.ConversationBackgroundFragment;
import com.tapmedia.yoush.conversationlist.ConversationListFragment;

import java.util.ArrayList;

public class MainActivityPagerAdapter extends FragmentPagerAdapter {

    private ArrayList<Fragment> fragments = new ArrayList<>();
    private Fragment currentFragment;


    public MainActivityPagerAdapter(@NonNull FragmentManager fm, int behavior) {
        super(fm, behavior);
        fragments.clear();
        fragments.add(new ConversationListFragment());
        fragments.add(new ContactSelectionListFragment());
        // fragments.add(new ApplicationPreferencesActivity.ApplicationPreferenceFragment());
    }

    @Override
    public Fragment getItem(int position) {
        return fragments.get(position);
    }

    @Override
    public int getCount() {
        return fragments.size();
    }

    @Override
    public void setPrimaryItem(ViewGroup container, int position, Object object) {
        if (currentFragment != object) {
            currentFragment = ((Fragment) object);
        }
        super.setPrimaryItem(container, position, object);
    }

    public Fragment getCurrentFragment() {
        return currentFragment;
    }

}
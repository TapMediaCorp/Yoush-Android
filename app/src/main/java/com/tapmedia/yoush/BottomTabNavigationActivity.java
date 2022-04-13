//package com.tapmedia.yoush;
//
//import android.os.Bundle;
//
//import com.google.android.material.bottomnavigation.BottomNavigationView;
//import com.google.android.material.floatingactionbutton.FloatingActionButton;
//import com.google.android.material.snackbar.Snackbar;
//
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.appcompat.widget.Toolbar;
//import androidx.fragment.app.Fragment;
//import androidx.fragment.app.FragmentTransaction;
//
//import android.view.MenuItem;
//import android.view.View;
//import android.widget.FrameLayout;
//
//public class BottomTabNavigationActivity extends AppCompatActivity {
//
//    BottomNavigationView navigation;
//    FrameLayout frameLayout;
//
//    private HomeFragment homeFragment;
//    private ChatFragment chatFragment;
//    private FriendsFragment friendsFragment;
//    private NotificationFragment notificationFragment;
//    private ThreeDotsFragment threeDotsFragment;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_bottom_tab_navigation);
//        Toolbar toolbar = findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);
//        FloatingActionButton fab = findViewById(R.id.fab);
//
//        navigation = findViewById(R.id.navigation_bottom);
//        frameLayout = findViewById(R.id.frameLayout);
//
//        homeFragment = new HomeFragment();
//        chatFragment = new ChatFragment();
//        friendsFragment = new FriendsFragment();
//        notificationFragment = new NotificationFragment();
//        threeDotsFragment = new ThreeDotsFragment();
//
//        navigation.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
//            @Override
//            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
//
//                switch (item.getItemId()){
//                    case R.id.ic_home:
//                        InitializeFragment(homeFragment);
//                        return true;
//                    case R.id.ic_chat:
//                        InitializeFragment(chatFragment);
//                        return true;
//                    case R.id.ic_friends :
//                        InitializeFragment(friendsFragment);
//                        return true;
//                    case R.id.ic_notification :
//                        InitializeFragment(notificationFragment);
//                        return true;
//                    case R.id.ic_three_dots :
//                        InitializeFragment(threeDotsFragment);
//                        return true;
//                }
//
//                return false;
//            }
//        });
//
//
//
//    }
//    private void InitializeFragment (Fragment fragment){
//        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
//        fragmentTransaction.replace(R.id.frameLayout, fragment);
//        fragmentTransaction.commit();
//    }
//}